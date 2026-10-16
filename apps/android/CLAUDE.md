# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A native Android client (Kotlin, Jetpack Compose, Material 3) for a Tracker Tavex
`/api/v1` REST API. `API.md` at the repository root is documented as the source of
truth for the API contract, but it does not currently exist in this checkout — treat
any API-shape assumptions with the caution described in
[`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md).

For anything beyond exact dev commands, this file links out rather than repeating
detail that lives elsewhere and would otherwise drift out of sync:

- **Architecture, technologies, running the app, token storage, known
  limitations** — [`README.md`](README.md)
- **API contract, auth flow, error handling, portfolio-update payload
  semantics, decimal/date handling** — [`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md)
- **Build variants, Play tracks, version codes, signing** — [`docs/BUILD_VARIANTS.md`](docs/BUILD_VARIANTS.md)

## Commands

```bash
./gradlew test                       # JVM unit tests (repositories, validators, interceptors, error parsing)
./gradlew test --tests "*.PortfolioValidatorTest"   # single test class
./gradlew test --tests "*.PortfolioValidatorTest.someMethodName"  # single test method
./gradlew connectedAndroidTest       # Compose UI tests — needs a connected device/emulator
./gradlew assembleDebug              # all 3 flavors' debug APKs -> app/build/outputs/apk/<flavor>/debug/
./gradlew assembleAlphaDebug          # single flavor's debug APK — see docs/BUILD_VARIANTS.md
./gradlew assembleRelease            # all 3 flavors' release APKs (no signing config configured yet)
./gradlew :app:compileAlphaDebugKotlin  # fast compile-only check for one flavor, no test/packaging
```

Unit tests live under `app/src/test`; Compose UI tests live under `app/src/androidTest`.
Both use hand-built fakes (`testutil/FakeTokenStorage`, `testutil/TestApiServiceFactory`)
and `MockWebServer` instead of the real API — ViewModels/repositories are constructed
directly in tests, no Hilt required, no network/backend needed.
`connectedAndroidTest` compiles but was never executed against a real device/emulator —
don't assume it's a validated merge gate.

Requires JDK 21, Android SDK Platform 36. Gradle wrapper handles the Gradle version
itself (no separate Gradle install needed).

## Architecture

MVVM, one-way data flow: `Composable → ViewModel (StateFlow<UiState>) → Repository →
ApiService (Retrofit)`. Screens/ViewModels never call Retrofit directly, and the UI
layer never sees a DTO. See [`README.md` → Architecture](README.md#architecture) for
the full package-by-package breakdown (`core/network`, `core/data`, `core/model`,
`core/auth`, `core/storage`, `core/ui`, `core/di`, `feature/*`, `navigation`).

## Known limitations

See [`README.md` → Known limitations](README.md#known-limitations) — most notably,
several endpoints are **ASSUMED** rather than confirmed against a real spec (every
one is flagged in [`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md) and at its
declaration in `ApiService.kt`).
