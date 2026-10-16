# Tracker Tavex — iOS

A native iOS client for the Tracker Tavex `/api/v1` REST API, built with Swift and
SwiftUI. It now covers close to the same feature set as the
[Android client](../android/README.md): login/register, an editable portfolio,
per-asset and portfolio-value price charts, account management, and an admin panel —
with a couple of deliberate simplifications noted under [Known limitations](#known-limitations).

Unlike the Android client — which had to guess several endpoint shapes because
`API.md`/`openapi.yaml` weren't available (see
[`apps/android/docs/API_INTEGRATION.md`](../android/docs/API_INTEGRATION.md)) — this
app's models are read directly from the real server implementation
([`apps/flask/api.py`](../flask/api.py) and [`apps/flask/repository.py`](../flask/repository.py)),
so they match the actual contract, not an assumption.

## Requirements

- Xcode 16 or later (the project file targets the modern build system; older Xcode
  versions may prompt to upgrade it, which is safe to accept)
- iOS 17.0+ deployment target
- Swift 5 language mode (set explicitly, independent of your Xcode's default)

## Project structure

```text
TrackerTavex/
├── TrackerTavexApp.swift       # App entry point
├── RootView.swift                  # Checking / Login / MainTabView by session state
├── MainTabView.swift                # Tabs after login (role-aware — see below)
├── Info.plist                      # Includes the ATS exception for the Tailscale host
├── Core/
│   ├── Networking/                 # APIClient (URLSession), request/response models,
│   │                                # error mapping, DateParsing (naive ISO timestamps)
│   ├── Auth/                       # Keychain-backed token storage, SessionStore
│   ├── Settings/                   # API base URL + override (AppSettings)
│   └── Theme/                      # Material 3 color/typography/component styling — see below
└── Features/
    ├── Login/                      # Login form, force-login (active session) flow
    ├── Register/                   # Account creation
    ├── Portfolio/                  # Editable holdings / manual items, total value
    ├── PortfolioHistory/           # Portfolio value chart (embedded as a section in Assets)
    ├── Assets/                     # Asset list + per-asset price history chart
    ├── Account/                    # Profile, change password, deactivate, log out
    ├── Admin/                      # Users, login history, log files (role-manager only)
    └── Connection/                 # Base URL override + "Test connection"
```

## Configuring the API base URL

The default base URL points at the production Tailscale backend (same one the Android
app's `production` flavor uses):

```
http://piglet.tailf5e9c9.ts.net:5000/api/v1/
```

The device/simulator needs to be on the same [Tailscale](https://tailscale.com) tailnet
to reach it. `Info.plist` allows cleartext HTTP only for that exact host (`NSAppTransportSecurity`
→ `NSExceptionDomains`), mirroring the Android client's per-host network security config.

To point at a different server without touching code, open **Login → Connection**, enter
a base URL (must start with `http://` or `https://` and end with `/`), tap **Test
connection**, then **Save**. The override is stored in `UserDefaults` and always takes
priority over the compiled-in default described below.

## Build configurations

Three build configurations mirror the debug half of Android's `alpha`/`beta`/`production`
flavors (`apps/android/docs/BUILD_VARIANTS.md`) — release counterparts aren't set up yet
since only debug builds are needed for now:

| Configuration | Bundle identifier | `API_BASE_URL` | Display name |
|---|---|---|---|
| Alpha Debug | `io.github.spasarnaudov.trackertavex.alpha.debug` | `…:5002/api/v1/` | Tracker Tavex Alpha |
| Beta Debug | `io.github.spasarnaudov.trackertavex.beta.debug` | `…:5001/api/v1/` | Tracker Tavex Beta |
| Production Debug | `io.github.spasarnaudov.trackertavex.debug` | `…:5000/api/v1/` | Tracker Tavex |

Each has a matching shared scheme (Xcode's scheme picker, next to the run/stop buttons) —
pick one and Run to install that variant. Distinct bundle identifiers mean all three can
be installed on the same simulator/device side by side, same as Android's per-flavor
`applicationIdSuffix`. The original `TrackerTavex` scheme/`Debug` configuration is
unchanged and still points at production, kept as a plain default.

`API_BASE_URL` is a build setting, threaded into the app via the `APIBaseURL` Info.plist
key (`$(API_BASE_URL)`) and read by `AppSettings.defaultBaseURL` — the same mechanism as
Android's per-flavor `BuildConfig.API_BASE_URL`, just expressed with Xcode's own tools
instead of Gradle's. `APP_DISPLAY_NAME` follows the same pattern for `CFBundleDisplayName`.
All three point at the same Tailscale host as the existing ATS exception in `Info.plist`
(only the port differs), so no ATS changes were needed to add them.

Each configuration also gets its own app icon (`ASSETCATALOG_COMPILER_APPICON_NAME`),
carried over from the Android client's per-variant launcher icons
(`apps/android/app/src/{alphaDebug,betaDebug,productionDebug}/res/drawable-nodpi/ic_launcher_foreground_image.png`):
same compass artwork, with the same colored double-letter badges (red "A A", yellow
"B B", blue "P P") in the same corner. Android composites these onto a separate
adaptive-icon background layer and lets the launcher's mask crop a 108dp canvas down to
a 72dp safe zone; there's no equivalent background layer or OS-side safe-zone crop on
iOS, so each source image was center-cropped to that same 66.7%-of-canvas region before
scaling up to a flat 1024×1024 (`AppIcon-Alpha`/`AppIcon-Beta`/`AppIcon-Production` in
`Assets.xcassets`) — otherwise the icon would show extra dark padding around it that
Android's launcher mask normally hides. The plain `AppIcon` (no badge, used by the
original `TrackerTavex` scheme) is Android's unbadged default the same way. Source
art is 432×432, so these are upscaled — fine for on-device/simulator use, but swap in
higher-resolution originals before an eventual App Store submission.

## Running the app

1. Open `TrackerTavex.xcodeproj` in Xcode.
2. Confirm a Development Team is set under the target's **Signing & Capabilities**
   (Automatic signing).
3. Pick a scheme (`TrackerTavex`, or one of the three above) and a simulator or
   device, then run.
4. If the simulator/device isn't on the backend's Tailscale tailnet, open **Connection**
   on the Login screen and point it at a reachable address.

## Visual style

The app is styled after Android's actual look rather than default iOS system chrome:
Android's Compose theme (`apps/android/.../ui/theme/Color.kt`) is the **unmodified**
Material 3 template — the stock purple baseline scheme, not custom branding — so
`Core/Theme/` reproduces that same baseline scheme in SwiftUI:

- `MaterialColor.swift` — the M3 baseline role colors (primary/secondary/tertiary,
  surface, error, etc.), each a dynamic `Color`/`UIColor` that switches light ↔ dark
  automatically, matching Android's `isSystemInDarkTheme()` switch. Android also layers
  Material You dynamic color from the wallpaper on API 31+; this fixed baseline is the
  closest iOS equivalent since there's no per-device wallpaper-derived scheme here.
- `MaterialTypography.swift` — the type scale sizes/weights Android's `Type.kt` uses.
- `MaterialComponents.swift` — `.materialFilled`/`.materialTonal` button styles,
  `.materialOutlined` text field style, and `.materialList()` (Material-colored list
  backgrounds/rows).
- `TrackerTavexApp.swift` configures `UINavigationBarAppearance`/`UITabBarAppearance`
  at launch, since the system chrome (nav bars, tab bar) is controlled by UIKit
  appearance proxies, not SwiftUI view modifiers.

Not reproduced: a custom Material `NavigationBar`/bottom-nav widget (native `TabView`
is retained, just recolored) and per-row `Card` elevation/shadow (native `List` with
`.insetGrouped`-style row backgrounds approximates it instead of a pixel-perfect clone).

## Navigation and roles

After login, `MainTabView` reads `currentUser.role`:

- **`user`** (the normal case) → **Portfolio**, **Assets**, **Account** tabs.
- **`role_manager` admin** → **Account**, **Admin** tabs only. The server's
  `require_api_auth` (`apps/flask/api.py`) returns `403` for that account on every
  portfolio/market-data endpoint, so those tabs are hidden rather than shown-and-broken —
  the same restriction described for the Android client in `apps/android/README.md`.

Every tab past the first/default one is only mounted the first time it's actually
selected (then stays mounted). A tab-bar-style `TabView` mounts *every* tab's content
as soon as it appears, not just the selected one — without this, logging in used to
fire `GET /portfolio`, `GET /assets`, `GET /portfolio/history`, and the account
screen's setup all at once, which felt like a frozen UI for a few seconds on a
Tailscale-latency connection.

`LoginView` also fires a fire-and-forget `GET /health` as soon as it appears, to warm
up the connection (DNS + TCP/TLS to the Tailscale host) while the user is still typing,
instead of paying that cost for the first time when they tap "Log In".

## API contract used

Endpoints called by this app, all read from the real Flask blueprint
(`apps/flask/api.py`):

| Endpoint | Used by |
|---|---|
| `GET /health` | Connection Settings → Test connection |
| `POST /auth/login` | Login (`409 active_session` triggers the force-login confirmation) |
| `POST /auth/register` | Register |
| `GET /auth/session` | Session restore on launch |
| `POST /auth/logout` | Account → Log out |
| `GET /portfolio` | Portfolio screen |
| `PUT /portfolio` | Portfolio → Save. Sends **every** holding (not just changed ones); an existing manual item includes its `id`, a new one sends `id: null`, a deleted one sends `delete: true` with its `id` — one added and removed again before ever being saved is just dropped from the payload. Reloads local state from the response body directly rather than issuing a follow-up `GET`. |
| `GET /portfolio/history` | Assets tab → "Portfolio value" section (`range` ∈ 1d/1w/1m/ytd/1y/all, `interval` ∈ hourly/daily/weekly) |
| `GET /assets` | Assets tab (asset list + gold buyback prices) |
| `GET /assets/{id}/prices` | Asset detail chart (adds `recorded`/`monthly` intervals over the portfolio-history set) |
| `PUT /account/password` | Account → Change password |
| `DELETE /account` | Account → Deactivate account |
| `GET /admin/users` | Admin → Users (includes `login_count`, so `GET /admin/login-stats` isn't called separately) |
| `GET /admin/login-history` | Admin → Login history |
| `GET /admin/logs` | Admin → Logs list |
| `GET /admin/logs/{name}` | Admin → Log detail (plain text, not JSON) |

`GET /charts/configuration` / `PUT /charts/configuration` (the web UI's configurable
multi-chart builder) are **not** implemented — see Known limitations.

Error responses (`{"error": {"code", "message", "details"?}}`) are mapped to a typed
`AppError` in `Core/Networking/APIError.swift`, the same shape Flask's `_error()` helper
produces for every `4xx`/`5xx` response.

## Known limitations

- **No configurable multi-chart builder.** The web UI's `GET`/`PUT /charts/configuration`
  (arbitrary saved chart definitions) isn't implemented — only the two fixed, directly
  useful charts (per-asset price history, portfolio value history) are.
- **Decimal precision.** Money/quantity fields are `Double`, parsed from/to plain text
  fields — fine for this scope, but the Android client uses `BigDecimal` specifically to
  avoid floating-point rounding on writes; revisit if editing precision becomes an issue.
- **Pull-to-refresh discards unsaved portfolio edits.** `PortfolioViewModel.load()` always
  replaces local state from the server; there's no dirty-state warning before a refresh.
- **Client-side validation is minimal**: negative quantities/prices and blank manual item
  names are rejected before sending (mirroring the Android client's `PortfolioValidator`),
  and username/password length is checked in Register/Change password — but nothing else
  is checked locally; server-side `422` validation errors still apply.
- **Admin screens have no pagination or search** — `API.md`/the server doesn't document
  any, matching the Android client's same known limitation.
- **Log content is fetched twice in effect**: `GET /admin/logs` already embeds each file's
  full content, but tapping a file still calls `GET /admin/logs/{name}` for parity with
  how the Android client fetches per-file detail. Harmless, just redundant.
