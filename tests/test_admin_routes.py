import os
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
FLASK_APP_DIRECTORY = PROJECT_ROOT / "apps" / "flask"
sys.path.insert(0, str(FLASK_APP_DIRECTORY))
os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("APP_ENV", "test")

import app as application


class AdminRouteTests(unittest.TestCase):
    def setUp(self):
        application.app.config.update(TESTING=True, SECRET_KEY="test-secret-key")
        self.client = application.app.test_client()

    def _set_session(self):
        with self.client.session_transaction() as session:
            session["user_id"] = 1
            session["session_token"] = "token"

    def _user(self, role):
        return {
            "id": 1,
            "username": application.ROLE_MANAGER_USERNAME if role == "admin" else "tester",
            "role": role,
            "is_deleted": False,
            "active_session_token": "token",
            "active_session_expires_at": datetime.now() + timedelta(minutes=5),
        }

    def _get_as(self, path, role, **request_kwargs):
        self._set_session()
        with patch.object(application, "get_user_by_id", return_value=self._user(role)), \
                patch.object(application, "update_user_session"):
            return self.client.get(path, **request_kwargs)

    def test_unauthenticated_user_is_redirected_from_admin_pages(self):
        for path in ("/admin/users", "/admin/logs"):
            response = self.client.get(path)
            self.assertEqual(response.status_code, 302)
            self.assertIn("/login", response.location)

    def test_non_admin_is_redirected_from_admin_pages(self):
        for path in ("/admin/users", "/admin/logs"):
            response = self._get_as(path, "user")
            self.assertEqual(response.status_code, 302)
            self.assertTrue(response.location.endswith("/portfolio"))

    def test_regular_user_home_redirects_to_portfolio(self):
        response = self._get_as("/", "user")

        self.assertEqual(response.status_code, 302)
        self.assertTrue(response.location.endswith("/portfolio"))

    def test_regular_user_can_view_charts(self):
        with patch.object(application, "get_chart_assets", return_value=[]), \
                patch.object(application, "load_chart_filters", return_value={"charts": []}), \
                patch.object(application, "save_chart_filters") as save_filters:
            response = self._get_as("/charts", "user")

        self.assertEqual(response.status_code, 200)
        self.assertIn(b">Charts<", response.data)
        self.assertIn(b"Add a chart", response.data)
        self.assertIn(b">Portfolio<", response.data)
        save_filters.assert_called_once()

    def test_chart_filter_ajax_returns_custom_date_controls_and_filtered_data(self):
        asset = {"id": 7, "symbol": "GOLD", "name": "Gold"}
        saved_filters = {
            "charts": [{
                "asset_id": 7,
                "range": "custom",
                "interval": "daily",
                "start_date": "2026-07-01",
                "end_date": "2026-07-15",
            }],
        }

        with patch.object(application, "get_chart_assets", return_value=[asset]), \
                patch.object(application, "load_chart_filters", return_value=saved_filters), \
                patch.object(application, "get_latest_price_date", return_value=None), \
                patch.object(application, "get_asset_prices", return_value=[]) as get_prices, \
                patch.object(application, "save_chart_filters"):
            response = self._get_as(
                "/charts",
                "user",
                headers={"X-Requested-With": "XMLHttpRequest"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertNotIn(b"<!DOCTYPE html>", response.data)
        self.assertIn(b'name="chart_start_date"', response.data)
        self.assertIn(b'value="2026-07-01"', response.data)
        self.assertIn(b'name="chart_end_date"', response.data)
        self.assertIn(b'value="2026-07-15"', response.data)
        get_prices.assert_called_once_with(
            7,
            "2026-07-01",
            "2026-07-15",
            "daily",
        )

    def test_chart_custom_dates_are_validated_and_ordered(self):
        chart_config = application.normalize_chart_config({
            "asset_id": 7,
            "range": "custom",
            "interval": "daily",
            "start_date": "2026-07-15",
            "end_date": "2026-07-01",
        }, [7])
        invalid_config = application.normalize_chart_config({
            "asset_id": 7,
            "range": "custom",
            "interval": "daily",
            "start_date": "not-a-date",
            "end_date": "2026-07-15",
        }, [7])

        self.assertEqual(chart_config["start_date"], "2026-07-01")
        self.assertEqual(chart_config["end_date"], "2026-07-15")
        self.assertIsNone(invalid_config["start_date"])

    def test_admin_can_view_users(self):
        listed_user = self._user("admin") | {
            "created_at": datetime(2026, 7, 14, 12, 34, 56, 789123),
            "last_login_at": datetime(2026, 7, 15, 8, 9, 10, 654321),
            "login_count": 12,
        }

        with patch.object(application, "get_users", return_value=[listed_user]):
            response = self._get_as("/admin/users", "admin")

        self.assertEqual(response.status_code, 200)
        self.assertIn(b"Users", response.data)
        self.assertNotIn(b">Portfolio<", response.data)
        self.assertNotIn(b">Charts<", response.data)
        self.assertNotIn(b'value="demo"', response.data)
        self.assertNotIn(b'name="role_', response.data)
        self.assertNotIn(b">Active<", response.data)
        self.assertIn(b"admin", response.data)
        self.assertIn(b"2026-07-14 12:34:56", response.data)
        self.assertNotIn(b"789123", response.data)
        self.assertIn(b"2026-07-15 08:09:10", response.data)
        self.assertNotIn(b"654321", response.data)
        self.assertIn(b">12<", response.data)
        self.assertNotIn(b"Deactivate this account", response.data)
        self.assertNotIn(b">Delete<", response.data)
        self.assertLess(
            response.data.index(b"2026-07-14 12:34:56"),
            response.data.index(b"2026-07-15 08:09:10"),
        )

    def test_start_user_session_records_successful_login(self):
        user = self._user("user")

        with application.app.test_request_context(), \
                patch.object(application, "update_user_session") as update_session, \
                patch.object(application, "record_user_login") as record_login:
            response = application.start_user_session(user, "/portfolio")

        self.assertEqual(response.status_code, 302)
        self.assertTrue(response.location.endswith("/portfolio"))
        update_session.assert_called_once()
        record_login.assert_called_once_with(1)

    def test_legacy_market_data_pages_are_removed(self):
        for path in ("/categories", "/assets", "/prices", "/prices/import-tavex"):
            response = self.client.get(path)
            self.assertEqual(response.status_code, 404)

    def test_regular_user_can_soft_delete_own_account(self):
        self._set_session()

        with patch.object(application, "get_user_by_id", return_value=self._user("user")), \
                patch.object(application, "update_user_session"), \
                patch.object(application, "deactivate_user_account", return_value=True) as deactivate_account:
            response = self.client.post("/delete-account")

        self.assertEqual(response.status_code, 302)
        self.assertTrue(response.location.endswith("/login"))
        deactivate_account.assert_called_once_with(1)
        with self.client.session_transaction() as current_session:
            self.assertNotIn("user_id", current_session)

    def test_role_manager_account_cannot_be_deleted(self):
        self._set_session()
        role_manager = self._user("admin") | {
            "username": application.ROLE_MANAGER_USERNAME,
        }

        with patch.object(application, "get_user_by_id", return_value=role_manager), \
                patch.object(application, "update_user_session"), \
                patch.object(application, "deactivate_user_account") as deactivate_account:
            response = self.client.post("/delete-account")

        self.assertEqual(response.status_code, 302)
        self.assertTrue(response.location.endswith("/users"))
        deactivate_account.assert_not_called()

    def test_admin_can_view_logs_and_content_is_html_escaped(self):
        login_time = datetime(2026, 7, 15, 9, 10, 11, 123456)
        login_history = [{
            "id": 14,
            "user_id": 2,
            "username": "spas",
            "logged_in_at": login_time,
        }]

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "logs").mkdir()
            (root / "logs" / "audit.log").write_text("<script>alert(1)</script>\n", encoding="utf-8")

            with patch.object(application, "PROJECT_ROOT", root), \
                    patch.object(application, "get_user_login_history", return_value=login_history), \
                    patch.object(
                        application,
                        "get_user_login_users",
                        return_value=["admin", "deleted_user", "spas"],
                    ):
                response = self._get_as("/admin/logs", "admin")

        self.assertEqual(response.status_code, 200)
        self.assertIn(b"audit.log", response.data)
        self.assertIn(b"&lt;script&gt;", response.data)
        self.assertNotIn(b"<script>alert(1)</script>", response.data)
        self.assertIn(b"Logins", response.data)
        self.assertIn(b"spas", response.data)
        self.assertIn(b"2026-07-15 09:10:11", response.data)
        self.assertNotIn(b"123456", response.data)
        self.assertNotIn(b">Event<", response.data)
        self.assertNotIn(b">User ID<", response.data)
        self.assertIn(b'<option value="">All</option>', response.data)
        self.assertIn(b'<option value="deleted_user">deleted_user</option>', response.data)

    def test_missing_and_empty_log_directories_have_messages(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            with patch.object(application, "PROJECT_ROOT", root), \
                    patch.object(application, "get_user_login_history", return_value=[]), \
                    patch.object(application, "get_user_login_users", return_value=["admin", "spas"]):
                missing_response = self._get_as("/admin/logs", "admin")

            (root / "logs").mkdir()
            with patch.object(application, "PROJECT_ROOT", root), \
                    patch.object(application, "get_user_login_history", return_value=[]), \
                    patch.object(application, "get_user_login_users", return_value=["admin", "spas"]):
                empty_response = self._get_as("/admin/logs", "admin")

        self.assertIn(b"Log directory not found.", missing_response.data)
        self.assertIn(b"No log files found.", empty_response.data)
        self.assertIn(b"No login sessions for the selected user.", empty_response.data)

    def test_portfolio_saves_chart_selection_for_tavex_and_manual_items(self):
        self._set_session()
        form_data = {
            "holding_include_in_chart": "7",
            "manual_item_id": "9",
            "manual_item_name": "Gold ring",
            "manual_item_quantity": "3.5",
            "manual_item_unit_price": "80",
            "manual_item_price_asset_id": "12",
            "manual_item_include_in_chart": "9",
        }
        holdings = [
            {"asset_id": 7, "quantity": 2.0},
            {"asset_id": 8, "quantity": 3.0},
        ]

        with patch.object(application, "get_user_by_id", return_value=self._user("user")), \
                patch.object(application, "update_user_session"), \
                patch.object(application, "get_gold_buyback_assets", return_value=[{"id": 12}]), \
                patch.object(application, "get_portfolio_holdings", return_value=holdings), \
                patch.object(application, "save_holdings_chart_flags") as save_holdings, \
                patch.object(application, "save_portfolio_manual_items") as save_manual_items:
            response = self.client.post("/portfolio", data=form_data)

        self.assertEqual(response.status_code, 302)
        save_holdings.assert_called_once_with(1, {7})
        save_manual_items.assert_called_once_with(1, [{
            "id": 9,
            "name": "Gold ring",
            "quantity": 3.5,
            "unit_price": 80.0,
            "price_asset_id": 12,
            "include_in_chart": True,
            "delete": False,
        }])

    def test_portfolio_excludes_zero_quantity_items_from_chart(self):
        self._set_session()
        form_data = {
            "holding_include_in_chart": "7",
            "manual_item_id": "9",
            "manual_item_name": "Gold ring",
            "manual_item_quantity": "0",
            "manual_item_unit_price": "80",
            "manual_item_include_in_chart": "9",
        }

        with patch.object(application, "get_user_by_id", return_value=self._user("user")), \
                patch.object(application, "update_user_session"), \
                patch.object(application, "get_gold_buyback_assets", return_value=[]), \
                patch.object(application, "get_portfolio_holdings", return_value=[{"asset_id": 7, "quantity": 0.0}]), \
                patch.object(application, "save_holdings_chart_flags") as save_holdings, \
                patch.object(application, "save_portfolio_manual_items") as save_manual_items:
            response = self.client.post("/portfolio", data=form_data)

        self.assertEqual(response.status_code, 302)
        save_holdings.assert_called_once_with(1, set())
        save_manual_items.assert_called_once_with(1, [{
            "id": 9,
            "name": "Gold ring",
            "quantity": 0.0,
            "unit_price": 80.0,
            "price_asset_id": None,
            "include_in_chart": False,
            "delete": False,
        }])

    def test_portfolio_ajax_save_returns_updated_chart_without_redirect(self):
        self._set_session()
        price_date = datetime(2026, 7, 13, 12, 0)

        with patch.object(application, "get_user_by_id", return_value=self._user("user")), \
                patch.object(application, "update_user_session"), \
                patch.object(application, "get_gold_buyback_assets", return_value=[]), \
                patch.object(application, "get_portfolio_holdings", return_value=[{"asset_id": 7, "quantity": 2.0}]), \
                patch.object(application, "save_holdings_chart_flags"), \
                patch.object(application, "save_portfolio_manual_items"), \
                patch.object(application, "get_latest_price_date", return_value=price_date), \
                patch.object(application, "get_portfolio_history", return_value=[{
                    "price_date": price_date,
                    "value": 123.45,
                    "profit": 50.0,
                    "profit_percent": 25.0,
                }]):
            response = self.client.post(
                "/portfolio?portfolio_range=1d&portfolio_interval=hourly",
                data={"holding_include_in_chart": "7"},
                headers={"X-Requested-With": "XMLHttpRequest"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["chart_values"], [123.45])
        self.assertEqual(response.json["chart_profits"], [50.0])
        self.assertEqual(response.json["chart_profit_percents"], [25.0])
        self.assertEqual(response.json["portfolio_interval"], "hourly")

    def test_portfolio_chart_profit_is_taken_per_point_from_history(self):
        history = [
            {"price_date": datetime(2026, 7, 13, 9, 0), "value": 100.0, "profit": 10.0, "profit_percent": 12.5},
            {"price_date": datetime(2026, 7, 13, 10, 0), "value": 140.0, "profit": 40.0, "profit_percent": 40.0},
        ]

        with patch.object(application, "get_latest_price_date", return_value=None), \
                patch.object(application, "get_portfolio_history", return_value=history):
            chart_data = application.get_portfolio_chart_data(7, "1d", "hourly")

        self.assertEqual(chart_data["chart_profits"], [10.0, 40.0])
        self.assertEqual(chart_data["chart_profit_percents"], [12.5, 40.0])

    def test_portfolio_chart_profit_percent_can_be_none_per_point(self):
        history = [{"price_date": datetime(2026, 7, 13, 9, 0), "value": 100.0, "profit": 0.0, "profit_percent": None}]

        with patch.object(application, "get_latest_price_date", return_value=None), \
                patch.object(application, "get_portfolio_history", return_value=history):
            chart_data = application.get_portfolio_chart_data(7, "1d", "hourly")

        self.assertEqual(chart_data["chart_profit_percents"], [None])

    def test_chart_gaps_mark_points_after_missing_data(self):
        price_dates = [
            datetime(2026, 7, 13, 9, 0),
            datetime(2026, 7, 13, 10, 0),
            datetime(2026, 7, 13, 15, 0),
            datetime(2026, 7, 13, 16, 0),
        ]

        self.assertEqual(
            application.get_chart_gaps(price_dates, "hourly"),
            [False, False, True, False],
        )
        self.assertEqual(
            application.get_chart_gaps(price_dates, "daily"),
            [False, False, False, False],
        )


if __name__ == "__main__":
    unittest.main()
