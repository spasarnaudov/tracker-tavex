package io.github.spasarnaudov.trackertavex.navigation

import java.net.URLEncoder

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

/** Route constants for Navigation Compose. Screens taking arguments build their own route strings. */
object Destinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CONNECTION_SETTINGS = "connection_settings"

    const val PORTFOLIO_GRAPH = "portfolio_graph"
    const val PORTFOLIO = "portfolio"
    const val MANUAL_ITEM_EDIT = "manual_item_edit"
    const val MANUAL_ITEM_EDIT_ARG = "itemKey"
    fun manualItemEdit(clientKey: String?) = "manual_item_edit?$MANUAL_ITEM_EDIT_ARG=${clientKey?.let { encode(it) } ?: ""}"

    const val ASSET_PURCHASES = "asset_purchases"
    const val ASSET_PURCHASES_ID_ARG = "assetId"
    const val ASSET_PURCHASES_SYMBOL_ARG = "symbol"
    const val ASSET_PURCHASES_NAME_ARG = "name"
    fun assetPurchases(assetId: Long, symbol: String, name: String) =
        "asset_purchases/$assetId/${encode(symbol)}/${encode(name)}"

    const val ASSETS = "assets"
    const val ASSET_DETAIL = "asset_detail"
    const val ASSET_DETAIL_ID_ARG = "assetId"
    const val ASSET_DETAIL_SYMBOL_ARG = "symbol"
    const val ASSET_DETAIL_NAME_ARG = "name"
    fun assetDetail(assetId: Long, symbol: String, name: String) =
        "asset_detail/$assetId/${encode(symbol)}/${encode(name)}"

    const val CHARTS = "charts"

    const val ACCOUNT = "account"
    const val CHANGE_PASSWORD = "change_password"
    const val DELETE_ACCOUNT = "delete_account"

    const val ADMIN_USERS = "admin_users"
    const val ADMIN_LOGIN_STATS = "admin_login_stats"
    const val ADMIN_LOGIN_HISTORY = "admin_login_history"
    const val ADMIN_LOGS = "admin_logs"
    const val ADMIN_LOG_DETAIL = "admin_log_detail"
    const val ADMIN_LOG_DETAIL_ARG = "logName"
    fun adminLogDetail(name: String) = "admin_log_detail/${encode(name)}"
}
