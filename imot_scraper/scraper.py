#!/usr/bin/env python3
"""Scrape apartment listings from imot.bg for a configured search.

Search results on imot.bg are server-rendered HTML (confirmed by direct
browser inspection, see imot-bg-research.md), so this script only needs
requests + BeautifulSoup — no browser automation. All fields required for
this step (price, city, neighborhood, area, year built, room count, URL)
are already present on the results list pages, so listing detail pages are
never fetched, which keeps the request count (and the load on imot.bg) low.

This step only collects data and writes it to JSON/CSV. Database storage
(PostgreSQL, upsert logic, price-history tracking) is a later step.
"""

import argparse
import csv
import json
import logging
import random
import re
import time
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import urlencode

import requests
import yaml
from bs4 import BeautifulSoup

BASE_URL = "https://www.imot.bg"

DEFAULT_SCRAPING = {
    "delay_min_seconds": 2.0,
    "delay_max_seconds": 3.5,
    "timeout_seconds": 15,
    "max_retries": 3,
    "max_pages": 50,
    "user_agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
}

CSV_FIELDNAMES = [
    "source_id",
    "url",
    "title",
    "property_type",
    "rooms",
    "city",
    "neighborhood",
    "price",
    "currency",
    "area_sqm",
    "price_per_sqm",
    "year_built",
    "scraped_at",
]

SOURCE_ID_RE = re.compile(r"/obiava-([a-z0-9]+)-")
PRICE_RE = re.compile(r"([\d\s ]+(?:[.,]\d+)?)\s*(€|лв\.?)")
AREA_RE = re.compile(r"(\d+(?:[.,]\d+)?)\s*кв\.?\s*м", re.IGNORECASE)
YEAR_RE = re.compile(r"\b(19|20)\d{2}\b")


def normalize_url(href: str) -> str:
    href = href.strip()
    if href.startswith("//"):
        return "https:" + href
    if href.startswith("/"):
        return BASE_URL + href
    return href


def extract_source_id(url: str) -> str | None:
    match = SOURCE_ID_RE.search(url)
    return match.group(1) if match else None


def is_building_project(source_id: str) -> bool:
    # Promoted "Жилищна сграда" (building project) cards use a short, purely
    # numeric ID (e.g. "8545"); real individual listings use a letter mixed
    # into a long numeric ID (e.g. "1a178542044874881").
    return source_id.isdigit()


def parse_price(text: str) -> tuple[Decimal | None, str | None]:
    match = PRICE_RE.search(text)
    if not match:
        return None, None
    raw_number = match.group(1).replace(" ", "").replace(" ", "").replace(",", ".")
    try:
        price = Decimal(raw_number)
    except InvalidOperation:
        return None, None
    currency = "EUR" if match.group(2) == "€" else "BGN"
    return price, currency


def parse_area(text: str) -> Decimal | None:
    match = AREA_RE.search(text)
    if not match:
        return None
    try:
        return Decimal(match.group(1).replace(",", "."))
    except InvalidOperation:
        return None


def parse_year_built(text: str) -> int | None:
    match = YEAR_RE.search(text)
    return int(match.group(0)) if match else None


def parse_location(text: str) -> tuple[str | None, str | None]:
    parts = [part.strip() for part in text.split(",") if part.strip()]
    if not parts:
        return None, None
    if len(parts) == 1:
        return parts[0], None
    return parts[0], ", ".join(parts[1:])


def build_search_url(search_cfg: dict, page: int) -> str:
    path = "/obiavi/{}/{}/{}".format(
        search_cfg["transaction_type"],
        search_cfg["city_slug"],
        search_cfg["type_slug"],
    )
    if page > 1:
        path += f"/p-{page}"

    params = dict(search_cfg.get("extra_query_params") or {})
    if search_cfg.get("year_built_filter"):
        params["ybuild_type"] = search_cfg["year_built_filter"]

    query = urlencode(params)
    return f"{BASE_URL}{path}?{query}" if query else f"{BASE_URL}{path}"


def fetch_page(session: requests.Session, url: str, scraping_cfg: dict) -> str | None:
    max_retries = scraping_cfg.get("max_retries", DEFAULT_SCRAPING["max_retries"])
    timeout = scraping_cfg.get("timeout_seconds", DEFAULT_SCRAPING["timeout_seconds"])

    for attempt in range(1, max_retries + 1):
        try:
            response = session.get(url, timeout=timeout)
        except (requests.Timeout, requests.ConnectionError) as exc:
            logging.warning("Request error on attempt %d/%d for %s: %s", attempt, max_retries, url, exc)
        else:
            if response.status_code == 429:
                retry_after = response.headers.get("Retry-After")
                wait = float(retry_after) if retry_after and retry_after.isdigit() else (2**attempt) * 5
                logging.warning("Got 429 for %s; waiting %.1fs before retry.", url, wait)
                time.sleep(wait)
                continue
            if response.status_code >= 500:
                logging.warning(
                    "Server error %d on attempt %d/%d for %s", response.status_code, attempt, max_retries, url
                )
            elif response.status_code >= 400:
                logging.error("Client error %d for %s; not retrying.", response.status_code, url)
                return None
            else:
                return response.text

        if attempt < max_retries:
            backoff = (2 ** (attempt - 1)) + random.uniform(0, 1)
            time.sleep(backoff)

    logging.error("Giving up on %s after %d attempts.", url, max_retries)
    return None


def parse_listing_fields(item, title_link, url: str, source_id: str, search_cfg: dict) -> dict:
    location_tag = title_link.find("location")
    location_text = location_tag.get_text(" ", strip=True) if location_tag else ""
    if location_tag is not None:
        location_tag.extract()
    title_text = title_link.get_text(" ", strip=True)
    city, neighborhood = parse_location(location_text)

    price_tag = item.select_one("div.price")
    price, currency = parse_price(price_tag.get_text(" ", strip=True)) if price_tag else (None, None)

    info_tag = item.select_one("div.info")
    info_text = info_tag.get_text(" ", strip=True) if info_tag else ""
    area_sqm = parse_area(info_text)
    year_built = parse_year_built(info_text)

    price_per_sqm = None
    if price is not None and area_sqm:
        price_per_sqm = (price / area_sqm).quantize(Decimal("0.01"))

    return {
        "source_id": source_id,
        "url": url,
        "title": title_text,
        "property_type": search_cfg.get("property_type"),
        "rooms": search_cfg.get("rooms"),
        "city": city,
        "neighborhood": neighborhood,
        "price": str(price) if price is not None else None,
        "currency": currency,
        "area_sqm": str(area_sqm) if area_sqm is not None else None,
        "price_per_sqm": str(price_per_sqm) if price_per_sqm is not None else None,
        "year_built": year_built,
        "scraped_at": datetime.now(timezone.utc).isoformat(),
    }


def scrape(config: dict) -> tuple[list[dict], dict]:
    search_cfg = config["search"]
    scraping_cfg = {**DEFAULT_SCRAPING, **(config.get("scraping") or {})}

    session = requests.Session()
    session.headers.update({"User-Agent": scraping_cfg["user_agent"]})

    listings: list[dict] = []
    stats = {
        "pages_fetched": 0,
        "items_seen": 0,
        "building_projects_skipped": 0,
        "parse_errors": 0,
        "listings_parsed": 0,
    }

    page = 1
    max_pages = scraping_cfg["max_pages"]
    while page <= max_pages:
        url = build_search_url(search_cfg, page)
        if page > 1:
            delay = random.uniform(scraping_cfg["delay_min_seconds"], scraping_cfg["delay_max_seconds"])
            time.sleep(delay)

        logging.info("Fetching page %d: %s", page, url)
        html = fetch_page(session, url, scraping_cfg)
        if html is None:
            logging.error("Stopping pagination early because page %d could not be fetched.", page)
            break

        stats["pages_fetched"] += 1
        soup = BeautifulSoup(html, "lxml")
        items = soup.select("div.item[id^=ida]")
        if not items:
            logging.info("No listing cards found on page %d; assuming end of results.", page)
            break

        stats["items_seen"] += len(items)
        for item in items:
            try:
                title_link = item.select_one("div.zaglavie a.title")
                if title_link is None or not title_link.get("href"):
                    stats["parse_errors"] += 1
                    continue

                item_url = normalize_url(title_link["href"])
                source_id = extract_source_id(item_url)
                if source_id is None:
                    stats["parse_errors"] += 1
                    continue
                if is_building_project(source_id):
                    stats["building_projects_skipped"] += 1
                    continue

                listing = parse_listing_fields(item, title_link, item_url, source_id, search_cfg)
                listings.append(listing)
                stats["listings_parsed"] += 1
            except Exception:
                logging.exception("Failed to parse a listing card; skipping it.")
                stats["parse_errors"] += 1

        page += 1
    else:
        logging.warning("Reached max_pages=%d safety cap; stopping pagination.", max_pages)

    return listings, stats


def load_config(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as config_file:
        config = yaml.safe_load(config_file)

    for section in ("search", "scraping", "output"):
        if section not in config:
            raise ValueError(f"{path} is missing the '{section}' section")

    for key in ("transaction_type", "city_slug", "type_slug"):
        if not config["search"].get(key):
            raise ValueError(f"{path}: search.{key} is required")

    return config


def write_output(listings: list[dict], output_cfg: dict, override_path: str | None, override_format: str | None) -> Path:
    path = Path(override_path) if override_path else Path(output_cfg["path"])
    output_format = override_format or output_cfg.get("format", "json")
    path.parent.mkdir(parents=True, exist_ok=True)

    if output_format == "json":
        path.write_text(json.dumps(listings, ensure_ascii=False, indent=2), encoding="utf-8")
    elif output_format == "csv":
        with path.open("w", newline="", encoding="utf-8") as csv_file:
            writer = csv.DictWriter(csv_file, fieldnames=CSV_FIELDNAMES)
            writer.writeheader()
            writer.writerows(listings)
    else:
        raise ValueError(f"Unsupported output format: {output_format}")

    return path


def main() -> None:
    parser = argparse.ArgumentParser(description="Scrape apartment listings from imot.bg.")
    parser.add_argument("--config", default="config.yaml", help="Path to config.yaml (default: config.yaml)")
    parser.add_argument("--output", help="Override the output file path from config.yaml")
    parser.add_argument("--format", choices=["json", "csv"], help="Override the output format from config.yaml")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    config = load_config(Path(args.config))
    listings, stats = scrape(config)
    output_path = write_output(listings, config["output"], args.output, args.format)

    print("---")
    print(f"Pages fetched: {stats['pages_fetched']}")
    print(f"Listing cards seen: {stats['items_seen']}")
    print(f"Building-project cards skipped: {stats['building_projects_skipped']}")
    print(f"Parse errors: {stats['parse_errors']}")
    print(f"Listings collected: {stats['listings_parsed']}")
    print(f"Output written to: {output_path}")


if __name__ == "__main__":
    main()
