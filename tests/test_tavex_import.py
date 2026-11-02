import io
import os
import sys
import unittest
import urllib.error
from contextlib import redirect_stderr
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from unittest.mock import MagicMock, patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
FLASK_APP_DIRECTORY = PROJECT_ROOT / "apps" / "flask"
sys.path.insert(0, str(FLASK_APP_DIRECTORY))
os.environ.setdefault("APP_ENV", "test")

import tavex_import
import fetch_tavex_prices  # noqa: E402 - importable after tavex_import adds scripts/ to sys.path


class TavexImportTests(unittest.TestCase):
    def test_gold_buyback_prices_become_separate_chart_products(self):
        products = tavex_import.gold_buyback_prices_to_products([
            {
                "karat": 14,
                "fineness": 585,
                "price_per_gram": Decimal("52.25"),
            },
            {
                "karat": 18,
                "fineness": 750,
                "price_per_gram": Decimal("67.00"),
            },
        ])

        self.assertEqual(len(products), 2)
        self.assertEqual(products[0]["name"], "1 грам злато изкупува - 14K / проба 585")
        self.assertEqual(products[0]["category_name"], "Gold buyback")
        self.assertEqual(products[0]["buy_price_eur"], Decimal("52.25"))

    def test_hourly_import_stores_buyback_prices_with_other_products(self):
        product = {
            "name": "Tavex product",
            "category_name": "Gold",
            "buy_price_eur": Decimal("100"),
        }
        buyback_price = {
            "karat": 14,
            "fineness": 585,
            "price_per_gram": Decimal("52.25"),
        }
        price_time = datetime(2026, 7, 15, 8, 0)

        with patch.object(
            tavex_import,
            "fetch_products_from_urls",
            return_value=([product], ["products-source"]),
        ), patch.object(
            tavex_import,
            "get_tavex_gold_buyback_prices",
            return_value=[buyback_price],
        ), patch.object(
            tavex_import,
            "import_assets_from_products",
            return_value={"imported_count": 1, "skipped_count": 1},
        ) as import_assets, patch.object(
            tavex_import,
            "import_asset_prices_by_name",
            return_value={"imported_count": 2, "missing_products": []},
        ) as import_prices:
            result = tavex_import.import_tavex_prices(price_time=price_time)

        imported_products = import_assets.call_args.args[0]
        self.assertEqual(len(imported_products), 2)
        self.assertEqual(imported_products[1]["category_name"], "Gold buyback")
        import_prices.assert_called_once_with(imported_products, price_time)
        self.assertEqual(result["buyback_products_count"], 1)
        self.assertIn(tavex_import.TAVEX_BUYBACK_URL, result["sources"])


class FetchHtmlRetryTests(unittest.TestCase):
    def _response(self, body):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = body.encode("utf-8")
        return response

    def test_fetch_html_retries_after_timeout(self):
        with patch.object(fetch_tavex_prices.urllib.request, "urlopen", side_effect=[
                    TimeoutError("The read operation timed out"),
                    self._response("<html>ok</html>"),
                ]) as urlopen, \
                patch.object(fetch_tavex_prices.time, "sleep") as sleep, \
                redirect_stderr(io.StringIO()):
            page_html = fetch_tavex_prices.fetch_html("https://tavex.bg/zlato/", 30)

        self.assertEqual(page_html, "<html>ok</html>")
        self.assertEqual(urlopen.call_count, 2)
        sleep.assert_called_once_with(fetch_tavex_prices.FETCH_RETRY_DELAYS[0])

    def test_fetch_html_raises_after_last_attempt(self):
        attempts_count = len(fetch_tavex_prices.FETCH_RETRY_DELAYS) + 1

        with patch.object(
                    fetch_tavex_prices.urllib.request,
                    "urlopen",
                    side_effect=TimeoutError("The read operation timed out"),
                ) as urlopen, \
                patch.object(fetch_tavex_prices.time, "sleep"), \
                redirect_stderr(io.StringIO()):
            with self.assertRaises(TimeoutError):
                fetch_tavex_prices.fetch_html("https://tavex.bg/zlato/", 30)

        self.assertEqual(urlopen.call_count, attempts_count)

    def test_fetch_html_does_not_retry_client_errors(self):
        not_found = urllib.error.HTTPError("https://tavex.bg/missing/", 404, "Not Found", {}, None)

        with patch.object(fetch_tavex_prices.urllib.request, "urlopen", side_effect=not_found) as urlopen, \
                patch.object(fetch_tavex_prices.time, "sleep") as sleep:
            with self.assertRaises(urllib.error.HTTPError):
                fetch_tavex_prices.fetch_html("https://tavex.bg/missing/", 30)

        self.assertEqual(urlopen.call_count, 1)
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
