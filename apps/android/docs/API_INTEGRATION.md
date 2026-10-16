# API Integration

This document maps every network call the app makes back to `API.md`, flags what is
an assumption rather than a documented fact, and describes the authentication,
error-handling, and portfolio-update flows in detail. It complements — it does not
replace — `API.md`, which is documented as the source of truth for anything it
covers, but does not currently exist in this checkout (see the note below).

> **Why some endpoints are "ASSUMED":** `API.md` says the complete contract lives in
> `openapi.yaml`, but that file was not available while this app was built. Rather than
> block on it, endpoints without a documented shape were filled in using the same
> REST/versioning conventions as the documented ones (see the retrospective note in the
> project's own build log/PR description). Every such endpoint is marked **ASSUMED**
> below and in a code comment at its declaration in
> [`ApiService.kt`](../app/src/main/java/io/github/spasarnaudov/trackertavex/core/network/ApiService.kt).
> Re-verify each one against the real server (or a future `openapi.yaml`) before
> shipping.

## Retrofit interface → screen mapping

All calls go through the single `ApiService` interface
(`core/network/ApiService.kt`), reached from a `ViewModel` only via a
`core/data/*Repository` — no screen or ViewModel calls Retrofit directly.

| `ApiService` method | HTTP | Source | Repository | Screen(s) |
|---|---|---|---|---|
| `health()` | `GET /health` | Documented | `AuthRepository.checkHealth`, ad-hoc in `ConnectionSettingsViewModel` | Splash (implicitly, via session check), Connection Settings ("Test connection") |
| `login(...)` | `POST /auth/login` | Documented | `AuthRepository.login` | Login |
| `register(...)` | `POST /auth/register` | **ASSUMED** — modeled on the login contract | `AuthRepository.register` | Registration |
| `logout()` | `POST /auth/logout` | Documented | `AuthRepository.logout` | Account (Log out) |
| `getCurrentUser()` | `GET /account/me` | **ASSUMED path** — API.md separates "session validation" and "current user" conceptually but documents neither path; this app uses one endpoint for both, since API.md says authenticated calls simply extend the session — there's no separate refresh/validate call | `AuthRepository.restoreSession`, `AccountRepository.getCurrentUser` | Splash (session restore), Account |
| `changePassword(...)` | `PUT /account/password` | **ASSUMED** | `AccountRepository.changePassword` | Change password |
| `deactivateAccount()` | `POST /account/deactivate` | **ASSUMED** | `AccountRepository.deactivateAccount` | Delete account |
| `getPortfolio()` | `GET /portfolio` | Path documented; response shape **ASSUMED** (API.md shows the `PUT` request but not a response body) | `PortfolioRepository.getPortfolio` | Portfolio |
| `updatePortfolio(...)` | `PUT /portfolio` | **Documented exactly** (request shape) | `PortfolioRepository.updatePortfolio` | Portfolio (Save) |
| `getPortfolioHistory(...)` | `GET /portfolio/history` | Path + query params (`range`, `interval`) documented; response body **ASSUMED** as a bare array of `{timestamp, value}` | `PortfolioRepository.getPortfolioHistory` | Portfolio History |
| `getAssets()` | `GET /assets` | Path documented; the `gold_buyback_assets` key is documented (referenced by name in API.md for `price_asset_id`), the rest of the shape (an `assets` key, per-asset fields) is **ASSUMED** | `AssetsRepository.getAssets` | Assets, Manual item editor (price-source picker), Charts (data-source picker) |
| `getAssetPrices(...)` | `GET /assets/{id}/prices` | Path + query params (`range`, `interval`, `start_date`, `end_date`) documented; response body **ASSUMED** as a bare array of `{timestamp, price}` | `AssetsRepository.getAssetPrices` | Asset detail chart, Charts |
| `getChartConfiguration()` | `GET /charts/configuration` | Path documented; body **ASSUMED** | `ChartsRepository.getConfiguration` | Charts |
| `updateChartConfiguration(...)` | `PUT /charts/configuration` | Path documented; body **ASSUMED** | `ChartsRepository.updateConfiguration` | Charts (add/edit/remove chart) |
| `getAdminUsers()` | `GET /admin/users` | **ASSUMED** | `AdminRepository.getUsers` | Admin → Users |
| `getAdminLoginStats()` | `GET /admin/login-stats` | **ASSUMED**, schema unknown so decoded generically (`Map<String, JsonElement>`) | `AdminRepository.getLoginStats` | Admin → Login stats |
| `getAdminLoginHistory()` | `GET /admin/login-history` | **ASSUMED**, schema unknown so decoded generically (`List<Map<String, JsonElement>>`) | `AdminRepository.getLoginHistory` | Admin → Login history |
| `getAdminLogFiles()` | `GET /admin/logs` | **ASSUMED** | `AdminRepository.getLogFiles` | Admin Logs |
| `getAdminLogContent(name)` | `GET /admin/logs/{name}` | **ASSUMED**, response treated as plain text and split into lines | `AdminRepository.getLogContent` | Log details |

## Authentication flow

```
App start
  │
  ├─ no token stored ───────────────────────────────► Login screen
  │
  └─ token stored
       │
       ├─ GET /account/me succeeds ──────────────────► main app (Portfolio)
       ├─ GET /account/me → 401 ──────────────────────► clear token, Login screen
       └─ GET /account/me → network/IO error ─────────► "Retry" screen, token kept
```

- The token is attached to every request by `AuthInterceptor` reading
  `TokenStorage.getToken()` synchronously (it's a plain in-memory `StateFlow` backed by
  Keystore-encrypted disk storage, so no suspend call is needed on the OkHttp
  dispatcher thread).
- `UnauthorizedInterceptor` watches every response: if a request that *carried* an
  `Authorization` header comes back `401`, it clears the token, clears
  `SessionManager`'s current user, and emits on `SessionExpiryNotifier`, which
  `AppNavHost` collects to navigate back to Login from anywhere in the app. A `401` on
  a request with **no** token attached (e.g. bad login credentials) is left alone — the
  caller (Login screen) handles that error itself, and there's nothing to clear.
- Per API.md, "successful authenticated calls extend the session timeout" — there is no
  separate refresh call; every authenticated request implicitly refreshes the session
  server-side.

## Force-login flow (active session conflict)

```
POST /auth/login {username, password}
  │
  ├─ 200 ───────────────────────────────────► store token, navigate to Portfolio
  │
  └─ 409 {"error":{"code":"active_session"}} ─► show confirmation dialog
                                                  │
                                    Cancel ───────┤
                                                  │
                                    Continue ─────┴──► POST /auth/login {username, password, force: true}
```

`force: true` is **only** sent after the user taps **Continue** on the dialog shown for
a `409 active_session` response (`LoginViewModel.confirmForceLogin`) — it is never sent
speculatively or automatically.

## Error handling

Every `ApiService` call goes through `apiCall { ... }` (`core/network/ApiResult.kt`),
which turns HTTP responses and exceptions into a typed `ApiResult<T>`:

| Condition | `AppError` | UI behavior |
|---|---|---|
| `400` | `BadRequest` | Inline/snackbar message from the response body |
| `401` (protected endpoint) | `Unauthorized` | Token + session cleared, app navigates to Login (see above) |
| `401` (public endpoint, e.g. bad login) | `Unauthorized` | Shown as a normal form error; no session side effects |
| `403` | `Forbidden` | Message shown as-is; **no** retry-as-different-role or bypass attempted (e.g. Admin screens surface the server's message verbatim) |
| `404` | `NotFound` | Message shown as-is |
| `409` with `code == "active_session"` | `ActiveSessionConflict` | Force-login confirmation dialog (Login only) |
| `409` (other) | `Conflict` | Message shown as-is |
| `422` | `ValidationFailed` | `details[]` shown when present (e.g. under the Portfolio Save snackbar) |
| `500` | `ServerError` | Message shown as-is, with Retry where applicable |
| Timeout / no network (`IOException`) | `Network` | "Retry" affordance; **token is never cleared** on a network error |
| Anything else | `Unknown` | Generic message, no crash |

`AppError` never contains a raw stack trace or exception message that could leak
internals — messages come from the server's `error.message`/`details`, or a
locally-written fallback string.

## Portfolio update semantics

`PortfolioRepository.updatePortfolio` mirrors API.md's `PUT /portfolio` contract
exactly:

- **Every** editable holding is sent, not just changed ones.
- A holding with `quantity == 0` is sent as-is (the server interprets that as removal —
  the app does not filter it out).
- An existing manual item includes its numeric `id`; a new one sends `id: null`
  (kotlinx.serialization is configured with `explicitNulls = true` specifically so this
  field is never silently omitted — see `core/network/NetworkJson.kt`).
- Deleting an existing manual item sends `delete: true` with its `id`; a manual item
  that was added and removed again in the same editing session before ever being saved
  is simply dropped from the payload (nothing to delete server-side).
- `price_asset_id` is set to a gold-buyback asset's id, or `null` for a manually typed
  `unit_price`; the two are mutually exclusive in the UI (picking an asset clears the
  manual price field and vice versa).
- **Before any HTTP call**, `PortfolioValidator` rejects negative quantities, negative
  manual unit prices, and blank manual item names — see
  `core/data/PortfolioValidator.kt` and its unit tests. Nothing invalid is ever sent to
  the server.
- On success, the ViewModel reloads its state from the `PUT` response body directly
  (rather than issuing a follow-up `GET /portfolio`), per API.md item 7's "reload from
  the response or the API" — either is acceptable, and reusing the response avoids an
  extra request.

## Example request/response models

**Login (documented):**

```json
POST /api/v1/auth/login
{"username": "demo", "password": "strong-password"}

200 OK
{
  "token": "...",
  "token_type": "Bearer",
  "expires_at": "2026-07-19T18:30:00",
  "user": {"id": 2, "username": "demo", "role": "user"}
}
```

**Portfolio update (documented request; ASSUMED response shape):**

```json
PUT /api/v1/portfolio
{
  "holdings": [{"asset_id": 12, "quantity": 2.5, "include_in_chart": true}],
  "manual_items": [
    {"id": null, "name": "Gold ring", "quantity": 8.2, "unit_price": 70,
     "price_asset_id": null, "include_in_chart": true, "delete": false}
  ]
}

200 OK   // ASSUMED shape — see PortfolioResponseDto
{
  "holdings": [{"asset_id": 12, "asset_symbol": "BTC", "asset_name": "Bitcoin",
                "quantity": 2.5, "include_in_chart": true, "price": 61000, "value": 152500}],
  "manual_items": [{"id": 41, "name": "Gold ring", "quantity": 8.2, "unit_price": 70,
                     "price_asset_id": null, "include_in_chart": true, "value": 574}],
  "total_value": 153074
}
```

**Validation error (documented):**

```json
422 Unprocessable Entity
{
  "error": {
    "code": "validation_failed",
    "message": "The portfolio payload is invalid.",
    "details": ["holdings[0] has an invalid asset or quantity."]
  }
}
```

## Decimal precision and dates

- All money/quantity fields are `BigDecimal`, serialized/deserialized via a custom
  `BigDecimalSerializer` that reads/writes the JSON number as text — the value never
  passes through `Double`/`Float`, so precision is never lost either direction.
  Formatting to two decimals for display (`formatMoney()`) is purely a UI concern and
  never mutates the underlying value used for editing/saving.
- ISO date-times (e.g. `expires_at`, which API.md shows with no UTC offset) are parsed
  leniently: an offset/`Z` is accepted if present, and a bare local date-time otherwise
  (`LocalDateTimeSerializer.parseFlexible`).
- Unknown JSON fields are ignored (`ignoreUnknownKeys = true`) and missing optional
  fields fall back to sensible defaults (`coerceInputValues = true`), so a server-side
  addition to any response never crashes parsing.
