import sys
import re
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

from repository import import_assets_from_products, import_asset_prices_by_name

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPTS_PATH = PROJECT_ROOT / "scripts"

if str(SCRIPTS_PATH) not in sys.path:
    sys.path.append(str(SCRIPTS_PATH))

from fetch_tavex_prices import (
    DEFAULT_CATEGORY_URLS,
    fetch_html,
    fetch_products_from_urls,
    html_to_lines,
    parse_decimal,
)

TAVEX_BUYBACK_URL = "https://tavex.bg/izkupuvane-zlato-i-srebro/"
GOLD_BUYBACK_CATEGORY = "Gold buyback"


def current_timestamp():
    return datetime.now().replace(microsecond=0)


def current_hour_timestamp():
    return datetime.now().replace(minute=0, second=0, microsecond=0)


def import_tavex_prices(price_time=None, timeout=30):
    products, sources = fetch_products_from_urls(DEFAULT_CATEGORY_URLS, timeout=timeout)
    buyback_products = gold_buyback_prices_to_products(
        get_tavex_gold_buyback_prices(timeout=timeout),
    )
    products.extend(buyback_products)
    sources.append(TAVEX_BUYBACK_URL)
    assets_result = import_assets_from_products(products)
    prices_result = import_asset_prices_by_name(
        products,
        price_time or current_timestamp(),
    )

    return {
        "sources": sources,
        "products_count": len(products),
        "buyback_products_count": len(buyback_products),
        "imported_assets_count": assets_result["imported_count"],
        "skipped_assets_count": assets_result["skipped_count"],
        "imported_prices_count": prices_result["imported_count"],
        "missing_products": prices_result["missing_products"],
    }


def decimal_or_none(value):
    if value in {None, ""}:
        return None

    try:
        return Decimal(str(value))
    except InvalidOperation:
        return None


def parse_tavex_gold_buyback_prices(page_html):
    lines = html_to_lines(page_html)
    prices = []

    for index, line in enumerate(lines):
        match = re.search(
            r"1\s+грам\s+злато\s+проба\s+(?P<fineness>\d+)\s+\((?P<karat>\d+)\s+карата\)",
            line,
            flags=re.IGNORECASE,
        )

        if not match:
            continue

        for offset in range(1, 8):
            price_line = " ".join(lines[index + 1:index + offset + 1])
            price_match = re.search(
                r"(?P<price>\d+(?:\s*[,.]\s*\d+)?)\s*€",
                price_line,
            )

            if not price_match:
                continue

            raw_price = re.sub(r"\s+", "", price_match.group("price"))
            price_per_gram = decimal_or_none(parse_decimal(raw_price))

            if price_per_gram is None:
                break

            karat = int(match.group("karat"))
            fineness = int(match.group("fineness"))
            prices.append({
                "karat": karat,
                "fineness": fineness,
                "label": f"{karat}K / {fineness}",
                "price_per_gram": price_per_gram,
            })
            break

    return sorted(prices, key=lambda price: price["karat"])


def get_tavex_gold_buyback_prices(timeout=30):
    page_html = fetch_html(TAVEX_BUYBACK_URL, timeout)
    return parse_tavex_gold_buyback_prices(page_html)


def gold_buyback_prices_to_products(prices):
    return [
        {
            "name": (
                f"1 грам злато изкупува - {price['karat']}K / "
                f"проба {price['fineness']}"
            ),
            "category_name": GOLD_BUYBACK_CATEGORY,
            "sell_price_eur": None,
            "buy_price_eur": price["price_per_gram"],
        }
        for price in prices
    ]
