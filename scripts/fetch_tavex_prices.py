#!/usr/bin/env python3
"""Fetch Tavex product prices and print them as JSON.

The script intentionally uses only Python's standard library, so it can run in
minimal environments without installing extra packages.
"""

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.request
from decimal import Decimal, InvalidOperation
from typing import Any


DEFAULT_URL = "https://tavex.bg/"
# Tavex sometimes responds slowly. Wait this many seconds before each retry.
FETCH_RETRY_DELAYS = [10, 30]
DEFAULT_CATEGORY_URLS = [
    "https://tavex.bg/zlato/",
    "https://tavex.bg/srebro/",
]
CATEGORY_BY_URL_PART = {
    "zlato": "Gold",
    "srebro": "Silver",
}
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "TrackerTavex/1.0"
)

SKIPPED_LABELS = {
    "В наличност",
    "Изчерпан",
    "Сравнение",
    "Известие",
    "Добавете към количката",
}

def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def normalize_match_value(value: str) -> str:
    return normalize_text(value).casefold()


def product_matches_request(product_name: str, requested_product: str) -> bool:
    return normalize_match_value(product_name) == normalize_match_value(requested_product)



def parse_decimal(value: str | None) -> str | None:
    if not value:
        return None

    cleaned_value = value.replace(" ", "").replace(",", ".")

    try:
        return str(Decimal(cleaned_value))
    except InvalidOperation:
        return None


def price_after(label: str, text: str) -> str | None:
    pattern = rf"{label}[^0-9]*([0-9]+(?:[ .][0-9]{{3}})*(?:[,.][0-9]+)?)\s*€"
    match = re.search(pattern, text, re.IGNORECASE)

    if not match:
        return None

    return parse_decimal(match.group(1))


def html_to_lines(page_html: str) -> list[str]:
    page_html = re.sub(
        r"<(script|style)\b[^>]*>.*?</\1>",
        "",
        page_html,
        flags=re.IGNORECASE | re.DOTALL,
    )
    page_html = re.sub(
        r"</?(?:article|div|h[1-6]|li|p|section|span|strong|br)\b[^>]*>",
        "\n",
        page_html,
        flags=re.IGNORECASE,
    )
    page_html = re.sub(r"<[^>]+>", " ", page_html)
    text = html.unescape(page_html)

    return [
        normalize_text(line)
        for line in text.splitlines()
        if normalize_text(line)
    ]


def strip_tags(value: str) -> str:
    value = re.sub(r"<[^>]+>", " ", value)
    return normalize_text(html.unescape(value))


def price_from_pricelist(pricelist: dict[str, Any], price_type: str) -> str | None:
    prices = pricelist.get(price_type)

    if not prices:
        return None

    price = prices[0].get("price")
    if price is None:
        return None

    return parse_decimal(str(price))


def parse_structured_products(page_html: str) -> list[dict[str, Any]]:
    products_by_name: dict[str, dict[str, Any]] = {}
    product_blocks = re.findall(
        r'<div class="product__meta">(?P<block>.*?)(?=<div class="product__meta">|</body>)',
        page_html,
        flags=re.DOTALL,
    )

    for block in product_blocks:
        title_match = re.search(
            r'<span class="product__title-inner">(?P<title>.*?)</span>',
            block,
            flags=re.DOTALL,
        )
        pricelist_match = re.search(
            r"data-pricelist='(?P<pricelist>.*?)'",
            block,
            flags=re.DOTALL,
        )

        if not title_match or not pricelist_match:
            continue

        name = strip_tags(title_match.group("title"))
        if not name:
            continue

        try:
            pricelist = json.loads(html.unescape(pricelist_match.group("pricelist")))
        except json.JSONDecodeError:
            continue

        sell_price_eur = price_from_pricelist(pricelist, "sell")
        buy_price_eur = price_from_pricelist(pricelist, "buy")

        if not sell_price_eur and not buy_price_eur:
            continue

        products_by_name[name] = {
            "name": name,
            "sell_price_eur": sell_price_eur,
            "buy_price_eur": buy_price_eur,
        }

    return list(products_by_name.values())


def looks_like_product_name(line: str) -> bool:
    if line in SKIPPED_LABELS:
        return False

    if "€" in line or "лв" in line:
        return False

    if "Продаваме" in line or "We buy" in line:
        return False

    if len(line) < 5 or len(line) > 160:
        return False

    return bool(re.search(r"[A-Za-zА-Яа-я]", line))


def parse_products(page_html: str) -> list[dict[str, Any]]:
    structured_products = parse_structured_products(page_html)

    if structured_products:
        return structured_products

    products_by_name: dict[str, dict[str, Any]] = {}
    last_product_name: str | None = None

    for line in html_to_lines(page_html):
        if looks_like_product_name(line):
            last_product_name = line
            continue

        if not last_product_name:
            continue

        sell_price_eur = price_after("Продаваме", line)
        buy_price_eur = price_after("We buy", line)

        if not sell_price_eur and not buy_price_eur:
            continue

        product = products_by_name.setdefault(
            last_product_name,
            {
                "name": last_product_name,
                "sell_price_eur": None,
                "buy_price_eur": None,
            },
        )

        if sell_price_eur:
            product["sell_price_eur"] = sell_price_eur

        if buy_price_eur:
            product["buy_price_eur"] = buy_price_eur

    return list(products_by_name.values())


def is_retryable_fetch_error(error: Exception) -> bool:
    if isinstance(error, urllib.error.HTTPError):
        return error.code >= 500

    return isinstance(error, (TimeoutError, ConnectionError, urllib.error.URLError))


def fetch_html(url: str, timeout: int) -> str:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT},
    )

    for attempt, retry_delay in enumerate([*FETCH_RETRY_DELAYS, None], start=1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.read().decode("utf-8", errors="replace")
        except Exception as error:
            if retry_delay is None or not is_retryable_fetch_error(error):
                raise

            print(f"Fetch attempt {attempt} for {url} failed: {error!r}. Retrying in {retry_delay}s.", file=sys.stderr)
            time.sleep(retry_delay)


def read_html_file(path: str) -> tuple[str, str]:
    with open(path, "r", encoding="utf-8") as file:
        return file.read(), path


def selected_urls(args: argparse.Namespace) -> list[str]:
    if args.all:
        return DEFAULT_CATEGORY_URLS

    return args.url or [DEFAULT_URL]


def category_from_url(url: str) -> str | None:
    for url_part, category_name in CATEGORY_BY_URL_PART.items():
        if url_part in url:
            return category_name

    return None


def fetch_products_from_urls(urls: list[str], timeout: int) -> tuple[list[dict[str, Any]], list[str]]:
    products_by_name: dict[str, dict[str, Any]] = {}
    sources = []

    for url in urls:
        page_html = fetch_html(url, timeout)
        sources.append(url)
        category_name = category_from_url(url)

        for product in parse_products(page_html):
            product["category_name"] = category_name
            products_by_name[product["name"]] = product

    return list(products_by_name.values()), sources


def read_requested_products(args: argparse.Namespace) -> list[str]:
    requested_products = []

    for product in args.product or []:
        product = normalize_text(product)

        if product:
            requested_products.append(product)

    if args.products_file:
        with open(args.products_file, "r", encoding="utf-8") as file:
            for line in file:
                product = normalize_text(line)

                if product and not product.startswith("#"):
                    requested_products.append(product)

    return requested_products


def filter_products(
    products: list[dict[str, Any]],
    requested_products: list[str],
) -> tuple[list[dict[str, Any]], list[str]]:
    if not requested_products:
        return products, []

    matched_products_by_name = {}
    missing_products = []

    for requested_product in requested_products:
        matches = [
            product
            for product in products
            if product_matches_request(product["name"], requested_product)
        ]

        if not matches:
            missing_products.append(requested_product)
            continue

        for product in matches:
            matched_products_by_name[product["name"]] = product

    return list(matched_products_by_name.values()), missing_products


def read_html(args: argparse.Namespace) -> tuple[str, str]:
    if args.html_file:
        return read_html_file(args.html_file)

    url = selected_urls(args)[0]
    return fetch_html(url, args.timeout), url


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fetch Tavex product prices and print them as JSON."
    )
    parser.add_argument(
        "--url",
        action="append",
        help="Tavex page URL to fetch. Can be used more than once.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Fetch products from the default Tavex category pages.",
    )
    parser.add_argument(
        "--product",
        action="append",
        help="Product name to keep in the output. Can be used more than once.",
    )
    parser.add_argument(
        "--products-file",
        help="Text file with one product name per line. Lines starting with # are ignored.",
    )
    parser.add_argument("--html-file", help="Parse a local Tavex HTML file instead.")
    parser.add_argument("--limit", type=int, default=None, help="Limit returned products.")
    parser.add_argument("--timeout", type=int, default=15, help="HTTP timeout in seconds.")
    parser.add_argument("--pretty", action="store_true", help="Pretty-print JSON output.")

    args = parser.parse_args()
    if args.html_file:
        page_html, source = read_html(args)
        sources = [source]
        products = parse_products(page_html)
    else:
        products, sources = fetch_products_from_urls(selected_urls(args), args.timeout)

    requested_products = read_requested_products(args)
    products, missing_products = filter_products(products, requested_products)

    if args.limit is not None:
        products = products[: args.limit]

    output = {
        "sources": sources,
        "requested_count": len(requested_products),
        "count": len(products),
        "missing_products": missing_products,
        "products": products,
    }

    indent = 2 if args.pretty else None
    print(json.dumps(output, ensure_ascii=False, indent=indent))


if __name__ == "__main__":
    main()
