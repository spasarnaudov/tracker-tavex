# Tracker Tavex — Android

A native Android client for the Tracker Tavex `/api/v1` REST API. `API.md` at
the repository root is documented as the source of truth for every endpoint this
app calls, but it does not currently exist in this checkout — see
[Known limitations](#known-limitations) and
[`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md) for how that gap is handled.
Built with Kotlin, Jetpack Compose and Material 3.

## Architecture

MVVM with a strict one-way data flow and a repository layer between the UI and the
network:

```
Composable screen  →  ViewModel (StateFlow<UiState>)  →  Repository  →  ApiService (Retrofit)
      ↑ observes state                    ↓ ApiResult<T>          ↓ Response<DTO>
```

- **`core/network`** — the `ApiService` Retrofit interface, request/response DTOs
  (`core/network/dto`), a shared `networkJson` (kotlinx.serialization) configuration,
  `ApiResult`/`AppError` for typed success/error handling, the `AuthInterceptor` /
  `UnauthorizedInterceptor` OkHttp interceptors, and `DynamicApiServiceHolder`, which
  rebuilds the Retrofit/OkHttp stack when the configured base URL changes.
- **`core/data`** — one repository per API area (`AuthRepository`, `AccountRepository`,
  `PortfolioRepository`, `AssetsRepository`, `ChartsRepository`, `AdminRepository`),
  plus pure, unit-tested validators (`PortfolioValidator`, `DateRangeValidator`,
  `ConnectionUrlValidator`). Repositories map network DTOs to domain models — the UI
  layer never sees a DTO.
- **`core/model`** — immutable domain models (`User`, `Holding`, `ManualItem`,
  `Portfolio`, `Asset`, `AppError`, …).
- **`core/auth`** — `TokenStorage` (interface) / `KeystoreTokenStorage` (the Android
  Keystore-backed implementation), `SessionManager` (in-memory current user),
  `SessionExpiryNotifier` (broadcasts a forced logout after a `401`).
- **`core/storage`** — `SettingsDataStore`, the Preferences DataStore-backed override
  for the API base URL.
- **`core/ui`** — shared Compose building blocks (`LineChart`, loading/empty/error
  states, formatters) and a small dependency-free Canvas line chart.
- **`core/di`** — Hilt modules (`AppModule`, `NetworkModule`, `StorageBindingsModule`).
- **`feature/*`** — one package per screen area (`login`, `register`, `portfolio`,
  `assets`, `charts`, `account`, `admin`, `connection`, `splash`), each with its own
  `ViewModel`(s) + `@Composable` screen(s). ViewModels expose an immutable
  `data class ...UiState` via `StateFlow`; screens never call Retrofit directly.
- **`navigation`** — `AppNavHost` (Navigation Compose graph + bottom navigation) and
  `Destinations` (route builders).

## Technologies

- Kotlin 2.0, Jetpack Compose (Material 3), Navigation Compose
- Hilt (dependency injection), Kotlin Coroutines + `StateFlow`
- Retrofit + OkHttp, kotlinx.serialization (JSON)
- Jetpack DataStore (Preferences) for the connection-settings override
- Android Keystore (`AndroidKeyStore` + AES/GCM) for the Bearer token — see
  [Token storage](#token-storage) below
- JUnit4 + MockWebServer (unit tests), Compose UI Test (instrumented tests)

## Requirements

- Android Studio with support for AGP 9.0.x / Kotlin 2.0 (a current release channel)
- JDK 21
- Android SDK Platform 36, Build-Tools 36.x
- `minSdk = 26`, `compileSdk = targetSdk = 36`

The Gradle wrapper (`./gradlew`) downloads Gradle 9.1.0 automatically; you do not need
Gradle installed separately.

## Configuring the API base URL

The base URL is **never hardcoded** into application logic — it comes from
`BuildConfig.API_BASE_URL`, generated per product flavor in `app/build.gradle.kts`.
The `environment` flavor dimension has three flavors — `alpha`, `beta`,
`production` — each combined with the `debug`/`release` build type (6 build
variants total: `alphaDebug`, `alphaRelease`, `betaDebug`, ...). Each flavor
points at its own port on the same Tailscale-only host — see
[`docs/BUILD_VARIANTS.md`](docs/BUILD_VARIANTS.md#api-environments) for the
full variant-to-URL table.

That hostname is a [Tailscale](https://tailscale.com) MagicDNS name — see
[Using the Tailscale backend](#using-the-tailscale-backend) below. No public
HTTPS backend is planned currently, so cleartext HTTP is allowed, but scoped
narrowly to that one host, in **every** build variant including release —
see `app/src/<flavor>Debug/res/xml/network_security_config_*_debug.xml` for
the debug network security configs, and
[`network_security_config_release.xml`](app/src/release/res/xml/network_security_config_release.xml)
for the one release shares across all three flavors.

To point at your own server without touching Kotlin code:

1. Edit the `buildConfigField("String", "API_BASE_URL", "\"...\"")` line for the
   relevant flavor in `app/build.gradle.kts`, **or**
2. Run the app, open **Login → Connection settings**, type a base URL (must start with
   `http://` or `https://` and end with `/`), and tap **Test connection** then **Save**.
   A DataStore-persisted override always takes priority over `BuildConfig.API_BASE_URL`
   and survives app restarts. **Reset to default** clears the override.

Changing the base URL at runtime rebuilds the Retrofit/OkHttp client
(`DynamicApiServiceHolder`) and, if you were signed in, clears the local session first
(a confirmation dialog explains why) — the app never sends a token from one server to
another.

## Running the app

1. Open the project root in Android Studio and let it sync.
2. Pick a build variant (e.g. `alphaDebug`) from the Build Variants panel.
3. Make sure the device/emulator can reach the API base URL — for the current
   default that means being connected to the Tailscale tailnet the backend is
   on (see [Using the Tailscale backend](#using-the-tailscale-backend)), or
   override it via Connection Settings after first launch.
4. Run the `app` configuration.

## Tests

```bash
./gradlew test                  # JVM unit tests (repositories, validators, interceptors, error parsing)
./gradlew connectedAndroidTest  # Compose UI tests — needs a connected device/emulator
```

Unit tests live under `app/src/test`; Compose UI tests live under `app/src/androidTest`.
Both use hand-built fakes/`MockWebServer` instead of hitting the real API, and
ViewModels/repositories are constructed directly in tests (no Hilt required there),
so no network access or backend is needed to run them.

## Debug build

```bash
./gradlew assembleAlphaDebug     # or assembleBetaDebug / assembleProductionDebug
```

Produces an APK per flavor under `app/build/outputs/apk/<flavor>/debug/`. Cleartext
HTTP is allowed only to the exact host in that flavor's `API_BASE_URL`, scoped by its
own debug network security config (`network_security_config_<flavor>_debug.xml`).

## Release build

```bash
./gradlew assembleAlphaRelease   # or assembleBetaRelease / assembleProductionRelease
```

Before shipping to a public audience:

- Point `API_BASE_URL` in `app/build.gradle.kts` at a real HTTPS endpoint, for
  whichever flavor(s) you're shipping.
- Remove the cleartext exception in `app/src/release/` (currently there because no
  public HTTPS backend exists yet — see
  [Using the Tailscale backend](#using-the-tailscale-backend)) once you no longer
  need it.
- Configure a real signing config (this project ships without one — Android Studio's
  default debug signing is used for local `assembleRelease` runs).

## Using the Tailscale backend

No public HTTPS backend is planned right now. The API server runs on a machine
reachable only over [Tailscale](https://tailscale.com) (MagicDNS name
`piglet.tailf5e9c9.ts.net`), so:

- the device (physical phone or emulator host) needs the Tailscale app installed and
  connected to the same tailnet as the server;
- cleartext HTTP is allowed **only** for that exact hostname — every other host is
  still blocked, in both debug and release builds.

If your device can't resolve the MagicDNS name, open **Connection settings** and
enter the Tailscale IP directly (`http://100.x.y.z:<port>/api/v1/`, using your
flavor's port — see
[`docs/BUILD_VARIANTS.md`](docs/BUILD_VARIANTS.md#api-environments)), then **Test
connection** before **Save** — note a raw IP isn't in the network security config's
domain allowlist, so cleartext to it would still be blocked unless you add it there
too.

## Switching to a public HTTPS backend later

Set (or override via Connection Settings) an `https://` base URL, and remove the
cleartext exception in `app/src/release/` (and the per-flavor debug ones, if no
longer needed) — no other change is required. The OS's standard TLS/certificate
validation is used as-is; this app never disables SSL/TLS verification.

## Token storage

The Bearer token is encrypted with an AES-256/GCM key generated inside the Android
Keystore (`core/auth/KeystoreTokenStorage`) — the key material never leaves secure
hardware, so the ciphertext persisted in SharedPreferences is useless without the
originating device. The token is:

- attached automatically to every request via `AuthInterceptor`;
- cleared automatically when a protected request comes back `401`
  (`UnauthorizedInterceptor` + `SessionExpiryNotifier` route the app back to Login);
- cleared on logout, account deletion, and when the connection base URL changes;
- never written to logs, analytics, or crash reports (the debug `HttpLoggingInterceptor`
  is capped at `BASIC` level — method/URL/response code only — and explicitly redacts
  the `Authorization` header).

`core/auth/TokenStorage` is an interface specifically so tests can substitute an
in-memory fake instead of touching the real Android Keystore (which isn't available on
a plain JVM).

## Known limitations

- **API.md does not fully specify the contract.** It documents health/auth/portfolio
  update semantics and error shapes in detail, but points to a `openapi.yaml` for the
  complete contract, which was not available while building this app. Registration,
  session validation/refresh, account (profile/change-password/deactivate), and *all*
  admin endpoints are therefore modeled on plausible REST conventions rather than a
  confirmed spec. Every assumed endpoint is called out explicitly in
  [`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md) — verify each one against the
  real server (or a future `openapi.yaml`) before shipping.
- **"Edit holding" is inline, not a separate screen.** Holdings are edited directly in
  the Portfolio list (quantity field + include-in-chart checkbox per row) rather than
  via a dedicated navigation destination, to avoid splitting the same pending-edit
  state across two screens/ViewModels for a very small form.
- **`androidx.hilt.navigation.compose:hiltViewModel()` is flagged deprecated** by this
  toolchain snapshot (moved to a package not yet published in any stable artifact this
  project could pin). It's still fully functional; every screen in this app uses it.
  Revisit once the replacement ships in a stable `androidx.hilt` release.
- **`connectedAndroidTest` was written but not executed** in the environment this app
  was built in (no attached device/emulator). The tests compile
  (`./gradlew compileDebugAndroidTestKotlin`) and follow the same patterns validated by
  the 53 passing JVM unit tests; run them on a real device/emulator before relying on
  them as a merge gate.
- **No pagination** is implemented for admin users/login-history lists, since API.md
  doesn't document pagination parameters for any endpoint.
- **Release signing** is not configured — `assembleRelease` uses Android Studio's
  default debug signing locally; wire up a real signing config before distributing a
  release build.
