import secrets
from datetime import datetime, timedelta
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from flask import Flask, flash, g, jsonify, redirect, render_template, request, send_from_directory, session, url_for
from werkzeug.security import check_password_hash, generate_password_hash

from chart_settings import load_chart_filters, save_chart_filters
from config import (
    DEBUG,
    HOST,
    LOG_LEVEL,
    PORT,
    PROJECT_ROOT,
    ROLE_MANAGER_USERNAME,
    SECRET_KEY,
    SESSION_COOKIE_NAME,
    SESSION_TIMEOUT_MINUTES,
    UPLOADS_DIR,
)
from receipts import delete_receipt_file, save_receipt_file, validate_receipt_upload
from repository import (
    add_asset_purchase,
    clear_asset_purchase_receipt,
    create_user,
    deactivate_user_account,
    delete_asset_purchase,
    get_asset_prices,
    get_asset_purchase,
    get_asset_purchases,
    get_chart_assets,
    get_gold_buyback_assets,
    get_latest_price_date,
    get_portfolio_history,
    get_portfolio_holdings,
    get_portfolio_manual_items,
    get_user_by_id,
    get_user_by_username,
    get_user_with_password_by_id,
    get_users,
    get_user_login_history,
    get_user_login_users,
    record_user_login,
    clear_user_session,
    save_holdings_chart_flags,
    save_portfolio_manual_items,
    set_asset_purchase_receipt,
    update_asset_purchase,
    update_user_password,
    update_user_session,
)
from log_reader import LOG_MAX_LINES, get_log_files
from api import api

app = Flask(__name__)
app.secret_key = SECRET_KEY
app.config["SESSION_COOKIE_NAME"] = SESSION_COOKIE_NAME
app.config["TEMPLATES_AUTO_RELOAD"] = True
app.logger.setLevel(LOG_LEVEL)
app.register_blueprint(api)


@app.errorhandler(404)
def api_not_found(error):
    if request.path.startswith("/api/"):
        return jsonify({
            "error": {"code": "not_found", "message": "API endpoint not found."},
        }), 404
    return error


@app.errorhandler(405)
def api_method_not_allowed(error):
    if request.path.startswith("/api/"):
        return jsonify({
            "error": {
                "code": "method_not_allowed",
                "message": "HTTP method not allowed.",
            },
        }), 405
    return error

SESSION_TIMEOUT = timedelta(minutes=SESSION_TIMEOUT_MINUTES)
app.permanent_session_lifetime = SESSION_TIMEOUT

DEFAULT_CHART_RANGE = "1d"
DEFAULT_CHART_INTERVAL = "recorded"
VALID_CHART_RANGES = {"1d", "1w", "1m", "ytd", "1y", "all", "custom"}
VALID_CHART_INTERVALS = {"recorded", "hourly", "daily", "weekly", "monthly"}
DEFAULT_PORTFOLIO_RANGE = "1d"
VALID_PORTFOLIO_RANGES = {"1d", "1w", "1m", "ytd", "1y", "all"}
VALID_PORTFOLIO_INTERVALS = {"hourly", "daily", "weekly"}
PUBLIC_ENDPOINTS = {"login", "register", "static"}
ADMIN_ENDPOINTS = {"logs"}
USER_MANAGEMENT_ENDPOINTS = {"users"}
ROLE_MANAGER_ENDPOINTS = USER_MANAGEMENT_ENDPOINTS | {
    "logs",
    "change_password",
    "logout",
    "session_activity",
    "session_status",
    "static",
}
PASSIVE_SESSION_ENDPOINTS = {"session_status", "static"}


def is_admin(user):
    return user and user["role"] == "admin"


def is_role_manager(user):
    return is_admin(user) and user["username"].lower() == ROLE_MANAGER_USERNAME


@app.context_processor
def inject_configured_usernames():
    return {
        "role_manager_username": ROLE_MANAGER_USERNAME,
    }


def is_safe_next_url(next_url):
    if not next_url:
        return False

    parsed_url = urlsplit(next_url)
    return not parsed_url.netloc and parsed_url.path.startswith("/")


def normalize_next_url(next_url):
    if not is_safe_next_url(next_url):
        return url_for("home")

    internal_paths = {
        url_for("change_password"),
        url_for("login"),
        url_for("logout"),
        url_for("session_activity"),
        url_for("session_status"),
    }
    parsed_url = urlsplit(next_url)

    if parsed_url.path in internal_paths:
        return url_for("home")

    query_items = [
        (query_name, query_value)
        for query_name, query_value in parse_qsl(parsed_url.query, keep_blank_values=True)
        if query_name != "password_dialog"
    ]

    return urlunsplit((
        parsed_url.scheme,
        parsed_url.netloc,
        parsed_url.path,
        urlencode(query_items),
        parsed_url.fragment,
    ))


def add_query_parameter(next_url, name, value):
    parsed_url = urlsplit(next_url)
    query_items = [
        (query_name, query_value)
        for query_name, query_value in parse_qsl(parsed_url.query, keep_blank_values=True)
        if query_name != name
    ]
    query_items.append((name, value))

    return urlunsplit((
        parsed_url.scheme,
        parsed_url.netloc,
        parsed_url.path,
        urlencode(query_items),
        parsed_url.fragment,
    ))


def get_session_expiration_time():
    return datetime.now() + SESSION_TIMEOUT


def has_active_session(user):
    expires_at = user["active_session_expires_at"]

    return bool(
        user["active_session_token"]
        and expires_at
        and expires_at > datetime.now()
    )


def start_user_session(user, next_url):
    session_token = secrets.token_urlsafe(32)
    expires_at = get_session_expiration_time()

    session.clear()
    session.permanent = True
    session["user_id"] = user["id"]
    session["session_token"] = session_token

    update_user_session(user["id"], session_token, expires_at)
    record_user_login(user["id"])

    return redirect(next_url)


def clear_current_session():
    user_id = session.get("user_id")
    session_token = session.get("session_token")

    if user_id:
        clear_user_session(user_id, session_token)

    session.clear()


@app.before_request
def require_login():
    g.current_user = None

    if request.blueprint == "api":
        return None

    if request.endpoint in PUBLIC_ENDPOINTS:
        return None

    if request.endpoint is None:
        return None

    user_id = session.get("user_id")

    if user_id:
        user = get_user_by_id(user_id)

        if user and not user["is_deleted"]:
            session_token = session.get("session_token")
            active_token = user["active_session_token"]
            expires_at = user["active_session_expires_at"]

            if not session_token or session_token != active_token:
                session.clear()
                return redirect(url_for("login", next=request.full_path))

            if not expires_at or expires_at <= datetime.now():
                clear_current_session()
                return redirect(url_for("login", next=request.full_path))

            if request.endpoint not in PASSIVE_SESSION_ENDPOINTS:
                update_user_session(user["id"], session_token, get_session_expiration_time())

            g.current_user = user

            if is_role_manager(user) and request.endpoint not in ROLE_MANAGER_ENDPOINTS:
                return redirect(url_for("users"))

            if request.endpoint in USER_MANAGEMENT_ENDPOINTS and not is_admin(user):
                return redirect(url_for("portfolio"))

            if request.endpoint in ADMIN_ENDPOINTS and not is_admin(user):
                return redirect(url_for("portfolio"))

            return None

        clear_current_session()

    return redirect(url_for("login", next=request.full_path))


@app.context_processor
def inject_current_user():
    return {"current_user": getattr(g, "current_user", None)}


def format_chart_label(value, interval):
    if not value:
        return ""

    if interval == "recorded" and hasattr(value, "strftime"):
        return value.strftime("%Y-%m-%d %H:%M:%S")

    if interval == "hourly" and hasattr(value, "strftime"):
        return value.strftime("%Y-%m-%d %H:%M")

    if hasattr(value, "date"):
        return value.date().isoformat()

    if hasattr(value, "isoformat"):
        return value.isoformat()

    return value


# Expected time between two neighbouring chart points. Prices are imported
# hourly, so "recorded" points are expected one hour apart.
CHART_INTERVAL_STEPS = {
    "recorded": timedelta(hours=1),
    "hourly": timedelta(hours=1),
    "daily": timedelta(days=1),
    "weekly": timedelta(weeks=1),
    "monthly": timedelta(days=31),
}
CHART_GAP_FACTOR = 1.5


def get_chart_gaps(price_dates, interval):
    """Return one flag per point: True when data is missing before that point."""
    step = CHART_INTERVAL_STEPS.get(interval, timedelta(hours=1))
    max_distance = step * CHART_GAP_FACTOR
    gaps = []

    for index, price_date in enumerate(price_dates):
        if index == 0:
            gaps.append(False)
            continue

        gaps.append(price_date - price_dates[index - 1] > max_distance)

    return gaps


def get_chart_list_value(values, index, default=None):
    if index >= len(values):
        return default

    value = values[index]
    return default if value in {None, ""} else value


def normalize_chart_date(value):
    if not value:
        return None

    try:
        return datetime.strptime(value, "%Y-%m-%d").date().isoformat()
    except (TypeError, ValueError):
        return None


def normalize_chart_config(chart_config, valid_asset_ids):
    try:
        asset_id = int(chart_config.get("asset_id"))
    except (AttributeError, TypeError, ValueError):
        return None

    if asset_id not in valid_asset_ids:
        return None

    selected_range = chart_config.get("range") or DEFAULT_CHART_RANGE
    selected_interval = chart_config.get("interval") or DEFAULT_CHART_INTERVAL

    if selected_range not in VALID_CHART_RANGES:
        selected_range = DEFAULT_CHART_RANGE

    if selected_interval not in VALID_CHART_INTERVALS:
        selected_interval = DEFAULT_CHART_INTERVAL

    start_date = normalize_chart_date(chart_config.get("start_date"))
    end_date = normalize_chart_date(chart_config.get("end_date"))

    if start_date and end_date and start_date > end_date:
        start_date, end_date = end_date, start_date

    return {
        "asset_id": asset_id,
        "range": selected_range,
        "interval": selected_interval,
        "start_date": start_date,
        "end_date": end_date,
    }


def get_requested_chart_configs(saved_filters, valid_asset_ids):
    chart_configs = []
    selected_asset_ids = set()

    if request.args:
        raw_asset_ids = request.args.getlist("chart_asset_id")
        ranges = request.args.getlist("chart_range")
        intervals = request.args.getlist("chart_interval")
        start_dates = request.args.getlist("chart_start_date")
        end_dates = request.args.getlist("chart_end_date")

        for index, raw_asset_id in enumerate(raw_asset_ids):
            chart_config = normalize_chart_config({
                "asset_id": raw_asset_id,
                "range": get_chart_list_value(ranges, index, DEFAULT_CHART_RANGE),
                "interval": get_chart_list_value(intervals, index, DEFAULT_CHART_INTERVAL),
                "start_date": get_chart_list_value(start_dates, index),
                "end_date": get_chart_list_value(end_dates, index),
            }, valid_asset_ids)

            if not chart_config or chart_config["asset_id"] in selected_asset_ids:
                continue

            chart_configs.append(chart_config)
            selected_asset_ids.add(chart_config["asset_id"])
    else:
        for saved_chart in saved_filters.get("charts", []) or []:
            chart_config = normalize_chart_config(saved_chart, valid_asset_ids)

            if not chart_config or chart_config["asset_id"] in selected_asset_ids:
                continue

            chart_configs.append(chart_config)
            selected_asset_ids.add(chart_config["asset_id"])

    remove_chart_index = request.args.get("remove_chart", type=int)

    if remove_chart_index is not None and 0 <= remove_chart_index < len(chart_configs):
        removed_chart = chart_configs.pop(remove_chart_index)
        selected_asset_ids.discard(removed_chart["asset_id"])

    if "add_chart" in request.args:
        for asset_id in valid_asset_ids:
            if asset_id not in selected_asset_ids:
                chart_configs.append({
                    "asset_id": asset_id,
                    "range": DEFAULT_CHART_RANGE,
                    "interval": DEFAULT_CHART_INTERVAL,
                    "start_date": None,
                    "end_date": None,
                })
                break

    return chart_configs


def get_chart_date_range(selected_range, latest_price_date, custom_start_date, custom_end_date):
    if selected_range == "custom":
        return custom_start_date, custom_end_date

    start_date = None
    end_date = latest_price_date

    if selected_range == "1d" and latest_price_date:
        start_date = latest_price_date - timedelta(days=1)
    elif selected_range == "1w" and latest_price_date:
        start_date = latest_price_date - timedelta(days=7)
    elif selected_range == "1m" and latest_price_date:
        start_date = latest_price_date - timedelta(days=30)
    elif selected_range == "ytd" and latest_price_date:
        start_date = latest_price_date.replace(month=1, day=1)
    elif selected_range == "1y" and latest_price_date:
        start_date = latest_price_date - timedelta(days=365)
    elif selected_range == "all":
        end_date = None

    return start_date, end_date


def get_portfolio_date_range(selected_range, latest_price_date):
    start_date = None
    end_date = latest_price_date

    if selected_range == "1d" and latest_price_date:
        start_date = latest_price_date - timedelta(days=1)
    elif selected_range == "1w" and latest_price_date:
        start_date = latest_price_date - timedelta(days=7)
    elif selected_range == "1m" and latest_price_date:
        start_date = latest_price_date - timedelta(days=30)
    elif selected_range == "ytd" and latest_price_date:
        start_date = latest_price_date.replace(month=1, day=1)
    elif selected_range == "1y" and latest_price_date:
        start_date = latest_price_date - timedelta(days=365)
    elif selected_range == "all":
        start_date = None
        end_date = None

    return start_date, end_date


@app.route("/login", methods=["GET", "POST"])
def login():
    if session.get("user_id"):
        return redirect(url_for("home"))

    error = None
    active_session_user = None
    next_url = request.args.get("next") or request.form.get("next") or url_for("home")
    next_url = normalize_next_url(next_url)

    if request.method == "GET":
        session.pop("pending_login_user_id", None)
        session.pop("pending_login_next_url", None)

    if request.method == "POST":
        force_logout = request.form.get("force_logout") == "1"
        pending_user_id = session.get("pending_login_user_id")

        if force_logout and pending_user_id:
            user = get_user_with_password_by_id(pending_user_id)
            pending_next_url = normalize_next_url(session.get("pending_login_next_url") or next_url)

            if user and not user["is_deleted"]:
                clear_user_session(user["id"])
                return start_user_session(user, pending_next_url)

            session.pop("pending_login_user_id", None)
            session.pop("pending_login_next_url", None)
            error = "Invalid username or password."
            return render_template(
                "login.html",
                error=error,
                next_url=next_url,
                active_session_user=active_session_user,
            )

        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        user = get_user_by_username(username) if username else None

        if user and not user["is_deleted"] and check_password_hash(user["password_hash"], password):
            if has_active_session(user):
                session["pending_login_user_id"] = user["id"]
                session["pending_login_next_url"] = next_url
                active_session_user = user["username"]

                return render_template(
                    "login.html",
                    error=error,
                    next_url=next_url,
                    active_session_user=active_session_user,
                )

            return start_user_session(user, next_url)

        error = "Invalid username or password."
        session.pop("pending_login_user_id", None)
        session.pop("pending_login_next_url", None)

    return render_template(
        "login.html",
        error=error,
        next_url=next_url,
        active_session_user=active_session_user,
    )


@app.route("/register", methods=["GET", "POST"])
def register():
    if session.get("user_id"):
        return redirect(url_for("home"))

    error = None

    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        confirm_password = request.form.get("confirm_password", "")

        if len(username) < 3:
            error = "Username must be at least 3 characters."
        elif len(password) < 8:
            error = "Password must be at least 8 characters."
        elif password != confirm_password:
            error = "Passwords do not match."
        else:
            user = create_user(username, generate_password_hash(password))

            if user:
                return start_user_session(user, url_for("home"))

            error = "This username is already taken."

    return render_template("register.html", error=error)


@app.route("/logout", methods=["POST"])
def logout():
    clear_current_session()
    return redirect(url_for("login"))


@app.route("/delete-account", methods=["POST"])
def delete_account():
    user_id = session["user_id"]

    if not deactivate_user_account(user_id):
        return redirect(url_for("home"))

    session.clear()
    return redirect(url_for("login"))


@app.route("/session-status")
def session_status():
    return jsonify({"authenticated": True})


@app.route("/session-activity", methods=["POST"])
def session_activity():
    return jsonify({"active": True})


@app.route("/change-password", methods=["GET", "POST"])
def change_password():
    error = None
    next_url = request.form.get("next_url") or request.referrer or url_for("home")

    if not is_safe_next_url(next_url):
        next_url = url_for("home")

    if request.method == "GET":
        return redirect(next_url)

    current_password = request.form.get("current_password", "")
    new_password = request.form.get("new_password", "")
    confirm_password = request.form.get("confirm_password", "")
    user = get_user_with_password_by_id(session["user_id"])

    if not user or not check_password_hash(user["password_hash"], current_password):
        error = "Current password is not correct."
    elif len(new_password) < 4:
        error = "New password must be at least 4 characters."
    elif new_password != confirm_password:
        error = "New passwords do not match."
    else:
        update_user_password(session["user_id"], generate_password_hash(new_password))
        flash("Password changed successfully.", "password_success")
        return redirect(add_query_parameter(next_url, "password_dialog", "1"))

    flash(error, "password_error")
    return redirect(add_query_parameter(next_url, "password_dialog", "1"))


@app.route("/users")
@app.route("/admin/users")
def users():
    return render_template(
        "users.html",
        users=get_users(),
    )


@app.route("/admin/logs")
def logs():
    log_directory = PROJECT_ROOT / "logs"
    return render_template(
        "logs.html",
        log_directory_exists=log_directory.is_dir(),
        log_files=get_log_files(log_directory),
        login_history=get_user_login_history(),
        login_users=get_user_login_users(),
        log_max_lines=LOG_MAX_LINES,
    )


@app.route("/")
def home():
    return redirect(url_for("portfolio"))


def get_portfolio_chart_data(user_id, portfolio_range, portfolio_interval):
    if portfolio_range not in VALID_PORTFOLIO_RANGES:
        portfolio_range = DEFAULT_PORTFOLIO_RANGE

    if portfolio_interval not in VALID_PORTFOLIO_INTERVALS:
        portfolio_interval = "hourly"

    portfolio_start_date, portfolio_end_date = get_portfolio_date_range(
        portfolio_range,
        get_latest_price_date(),
    )
    portfolio_history = get_portfolio_history(
        user_id,
        portfolio_start_date,
        portfolio_end_date,
        portfolio_interval,
    )

    return {
        "portfolio_range": portfolio_range,
        "portfolio_interval": portfolio_interval,
        "chart_labels": [
            format_chart_label(price["price_date"], portfolio_interval)
            for price in portfolio_history
        ],
        "chart_values": [
            float(price["value"])
            for price in portfolio_history
        ],
        "chart_profits": [
            float(price["profit"])
            for price in portfolio_history
        ],
        "chart_profit_percents": [
            float(price["profit_percent"]) if price["profit_percent"] is not None else None
            for price in portfolio_history
        ],
        "chart_gaps": get_chart_gaps(
            [price["price_date"] for price in portfolio_history],
            portfolio_interval,
        ),
    }


@app.route("/portfolio", methods=["GET", "POST"])
def portfolio():
    user_id = session["user_id"]
    gold_buyback_assets = get_gold_buyback_assets()
    valid_gold_buyback_asset_ids = {
        asset["id"]
        for asset in gold_buyback_assets
    }

    if request.method == "POST":
        manual_items = []
        manual_item_ids = request.form.getlist("manual_item_id")
        manual_item_names = request.form.getlist("manual_item_name")
        manual_item_quantities = request.form.getlist("manual_item_quantity")
        manual_item_unit_prices = request.form.getlist("manual_item_unit_price")
        manual_item_price_asset_ids = request.form.getlist("manual_item_price_asset_id")
        deleted_manual_item_ids = set(request.form.getlist("manual_item_delete"))
        chart_asset_ids = {
            int(asset_id)
            for asset_id in request.form.getlist("holding_include_in_chart")
            if asset_id.isdigit()
        }
        chart_manual_item_ids = set(request.form.getlist("manual_item_include_in_chart"))

        quantities_by_asset_id = {
            holding["asset_id"]: holding["quantity"]
            for holding in get_portfolio_holdings(user_id)
        }
        chart_asset_ids = {
            asset_id
            for asset_id in chart_asset_ids
            if quantities_by_asset_id.get(asset_id, 0) > 0
        }

        for index, raw_item_id in enumerate(manual_item_ids):
            try:
                item_id = int(raw_item_id)
                quantity = float(manual_item_quantities[index] or 0)
                unit_price = float(manual_item_unit_prices[index] or 0)
            except (IndexError, ValueError):
                continue

            raw_price_asset_id = (
                manual_item_price_asset_ids[index]
                if index < len(manual_item_price_asset_ids)
                else ""
            )
            price_asset_id = (
                int(raw_price_asset_id)
                if raw_price_asset_id.isdigit()
                and int(raw_price_asset_id) in valid_gold_buyback_asset_ids
                else None
            )

            manual_items.append({
                "id": item_id,
                "name": manual_item_names[index] if index < len(manual_item_names) else "",
                "quantity": quantity,
                "unit_price": unit_price,
                "price_asset_id": price_asset_id,
                "include_in_chart": (
                    raw_item_id in chart_manual_item_ids
                    and quantity > 0
                ),
                "delete": raw_item_id in deleted_manual_item_ids,
            })

        new_manual_item_name = request.form.get("new_manual_item_name", "").strip()

        if new_manual_item_name:
            try:
                new_manual_item_quantity = float(request.form.get("new_manual_item_quantity") or 0)
                new_manual_item_unit_price = float(request.form.get("new_manual_item_unit_price") or 0)
            except ValueError:
                new_manual_item_quantity = 0
                new_manual_item_unit_price = 0

            raw_new_price_asset_id = request.form.get("new_manual_item_price_asset_id", "")
            new_price_asset_id = (
                int(raw_new_price_asset_id)
                if raw_new_price_asset_id.isdigit()
                and int(raw_new_price_asset_id) in valid_gold_buyback_asset_ids
                else None
            )

            manual_items.append({
                "id": None,
                "name": new_manual_item_name,
                "quantity": new_manual_item_quantity,
                "unit_price": new_manual_item_unit_price,
                "price_asset_id": new_price_asset_id,
                "include_in_chart": (
                    request.form.get("new_manual_item_include_in_chart") == "1"
                    and new_manual_item_quantity > 0
                ),
                "delete": False,
            })

        save_holdings_chart_flags(user_id, chart_asset_ids)
        save_portfolio_manual_items(user_id, manual_items)

        if request.headers.get("X-Requested-With") == "XMLHttpRequest":
            return jsonify(get_portfolio_chart_data(
                user_id,
                request.args.get("portfolio_range", DEFAULT_PORTFOLIO_RANGE),
                request.args.get("portfolio_interval", "hourly"),
            ))

        return redirect(url_for("portfolio"))

    holdings = get_portfolio_holdings(user_id)
    manual_items = get_portfolio_manual_items(user_id)
    chart_data = get_portfolio_chart_data(
        user_id,
        request.args.get("portfolio_range", DEFAULT_PORTFOLIO_RANGE),
        request.args.get("portfolio_interval", "hourly"),
    )
    portfolio_range = chart_data["portfolio_range"]
    portfolio_interval = chart_data["portfolio_interval"]
    return render_template(
        "portfolio.html",
        holdings=holdings,
        manual_items=manual_items,
        gold_buyback_assets=gold_buyback_assets,
        portfolio_range=portfolio_range,
        portfolio_interval=portfolio_interval,
        portfolio_ranges=[
            ("1d", "1 Day"),
            ("1w", "1 Week"),
            ("1m", "1 Month"),
            ("ytd", "YTD"),
            ("1y", "1 Year"),
            ("all", "All"),
        ],
        portfolio_intervals=[
            ("hourly", "Hourly"),
            ("daily", "Daily"),
            ("weekly", "Weekly"),
        ],
        chart_labels=chart_data["chart_labels"],
        chart_values=chart_data["chart_values"],
        chart_profits=chart_data["chart_profits"],
        chart_profit_percents=chart_data["chart_profit_percents"],
        chart_gaps=chart_data["chart_gaps"],
    )


def parse_purchase_date(value):
    try:
        parsed = datetime.strptime(value, "%Y-%m-%d").date()
    except (TypeError, ValueError):
        return None

    if parsed > datetime.now().date():
        return None

    return parsed.isoformat()


@app.route("/portfolio/holdings/<int:asset_id>", methods=["GET", "POST"])
def portfolio_purchases(asset_id):
    user_id = session["user_id"]
    holding = next(
        (item for item in get_portfolio_holdings(user_id) if item["asset_id"] == asset_id),
        None,
    )

    if holding is None:
        return redirect(url_for("portfolio"))

    if request.method == "POST":
        try:
            quantity = float(request.form.get("quantity") or 0)
            purchase_price = float(request.form.get("purchase_price") or -1)
        except ValueError:
            quantity, purchase_price = 0, -1

        purchase_date = parse_purchase_date(request.form.get("purchase_date"))

        if quantity > 0 and quantity.is_integer() and purchase_price >= 0 and purchase_date:
            add_asset_purchase(user_id, asset_id, quantity, purchase_price, purchase_date)

        return redirect(url_for("portfolio_purchases", asset_id=asset_id))

    return render_template(
        "portfolio_purchases.html",
        holding=holding,
        purchases=get_asset_purchases(user_id, asset_id),
        today=datetime.now().date().isoformat(),
    )


@app.route("/portfolio/purchases/<int:purchase_id>/update", methods=["POST"])
def portfolio_purchase_update(purchase_id):
    user_id = session["user_id"]
    asset_id = int(request.form.get("asset_id"))

    try:
        quantity = float(request.form.get("quantity") or 0)
        purchase_price = float(request.form.get("purchase_price") or -1)
    except ValueError:
        quantity, purchase_price = 0, -1

    purchase_date = parse_purchase_date(request.form.get("purchase_date"))

    if quantity > 0 and quantity.is_integer() and purchase_price >= 0 and purchase_date:
        update_asset_purchase(user_id, purchase_id, quantity, purchase_price, purchase_date)

    return redirect(url_for("portfolio_purchases", asset_id=asset_id))


@app.route("/portfolio/purchases/<int:purchase_id>/delete", methods=["POST"])
def portfolio_purchase_delete(purchase_id):
    user_id = session["user_id"]
    asset_id = int(request.form.get("asset_id"))
    deleted = delete_asset_purchase(user_id, purchase_id)
    if deleted:
        delete_receipt_file(deleted["receipt_filename"])
    return redirect(url_for("portfolio_purchases", asset_id=asset_id))


@app.route("/portfolio/purchases/<int:purchase_id>/receipt", methods=["GET", "POST"])
def portfolio_purchase_receipt(purchase_id):
    user_id = session["user_id"]
    purchase = get_asset_purchase(user_id, purchase_id)

    if purchase is None:
        return redirect(url_for("portfolio"))

    if request.method == "POST":
        asset_id = int(request.form.get("asset_id"))
        extension, error = validate_receipt_upload(request.files.get("receipt"), request.content_length)

        if not error:
            filename = save_receipt_file(purchase_id, request.files["receipt"], extension)
            set_asset_purchase_receipt(user_id, purchase_id, filename)
            delete_receipt_file(purchase["receipt_filename"])

        return redirect(url_for("portfolio_purchases", asset_id=asset_id))

    if not purchase["receipt_filename"]:
        return redirect(url_for("portfolio"))

    return send_from_directory(UPLOADS_DIR, purchase["receipt_filename"])


@app.route("/portfolio/purchases/<int:purchase_id>/receipt/delete", methods=["POST"])
def portfolio_purchase_receipt_delete(purchase_id):
    user_id = session["user_id"]
    asset_id = int(request.form.get("asset_id"))
    purchase = get_asset_purchase(user_id, purchase_id)

    if purchase:
        clear_asset_purchase_receipt(user_id, purchase_id)
        delete_receipt_file(purchase["receipt_filename"])

    return redirect(url_for("portfolio_purchases", asset_id=asset_id))


@app.route("/charts")
def charts():
    assets = get_chart_assets()
    saved_filters = load_chart_filters()
    asset_ids = [asset["id"] for asset in assets]
    chart_configs = get_requested_chart_configs(saved_filters, asset_ids)
    selected_asset_ids = [config["asset_id"] for config in chart_configs]
    selected_asset_id_set = set(selected_asset_ids)
    chart_panels = []

    for index, chart_config in enumerate(chart_configs):
        asset_id = chart_config["asset_id"]
        latest_price_date = get_latest_price_date(asset_id)
        start_date, end_date = get_chart_date_range(
            chart_config["range"],
            latest_price_date,
            chart_config["start_date"],
            chart_config["end_date"],
        )
        prices = get_asset_prices(
            asset_id,
            start_date,
            end_date,
            chart_config["interval"],
        )
        selectable_assets = [
            asset
            for asset in assets
            if asset["id"] == asset_id or asset["id"] not in selected_asset_id_set
        ]

        chart_panels.append({
            "index": index,
            "asset_id": asset_id,
            "selectable_assets": selectable_assets,
            "range": chart_config["range"],
            "interval": chart_config["interval"],
            "start_date": chart_config["start_date"] or "",
            "end_date": chart_config["end_date"] or "",
            "labels": [
                format_chart_label(price["price_date"], chart_config["interval"])
                for price in prices
            ],
            "values": [float(price["price"]) for price in prices],
            "gaps": get_chart_gaps(
                [price["price_date"] for price in prices],
                chart_config["interval"],
            ),
            "has_prices": bool(prices),
        })

    save_chart_filters({
        "charts": chart_configs,
    })

    template_name = (
        "_charts_content.html"
        if request.headers.get("X-Requested-With") == "XMLHttpRequest"
        else "charts.html"
    )

    return render_template(
        template_name,
        chart_panels=chart_panels,
        can_add_chart=len(selected_asset_ids) < len(assets),
        chart_ranges=[
            ("1d", "1 Day"),
            ("1w", "1 Week"),
            ("1m", "1 Month"),
            ("ytd", "YTD"),
            ("1y", "1 Year"),
            ("all", "All"),
            ("custom", "Custom"),
        ],
        chart_intervals=[
            ("recorded", "Each record"),
            ("hourly", "Hourly Avg"),
            ("daily", "Daily Avg"),
            ("weekly", "Weekly Avg"),
            ("monthly", "Monthly Avg"),
        ],
    )


if __name__ == "__main__":
    app.run(host=HOST, port=PORT, debug=DEBUG)
