# Android build variants and Google Play tracks

The app has one `environment` flavor dimension and three product flavors. All
release variants deliberately use the same application ID and belong in one Google
Play Console application.

| Variant | Purpose | Play track | API environment | Application ID | Version name |
|---|---|---|---|---|---|
| `alphaRelease` | Personal early testing | Internal testing | Development/internal | `io.github.spasarnaudov.trackertavex` | `0.1.0-alpha` |
| `betaRelease` | External testers | Closed testing | Test | `io.github.spasarnaudov.trackertavex` | `0.1.0-beta` |
| `productionRelease` | Official release | Production | Production | `io.github.spasarnaudov.trackertavex` | `0.1.0` |
| `alphaDebug` | Local alpha development | Not uploaded | Local development | `io.github.spasarnaudov.trackertavex.alpha.debug` | `0.1.0-alpha-debug` |
| `betaDebug` | Local beta development | Not uploaded | Test | `io.github.spasarnaudov.trackertavex.beta.debug` | `0.1.0-beta-debug` |
| `productionDebug` | Local production checks | Not uploaded | Production | `io.github.spasarnaudov.trackertavex.production.debug` | `0.1.0-debug` |

Internal, Closed, and Production are tracks in the same Play application, not
separate applications. Because the three release variants have the same application
ID, they cannot be installed next to each other. All three debug variants can coexist
with each other and with the Play app because each has a flavor-specific package ID.

## API environments

Each flavor points at its own port on the same Tailscale-only host (MagicDNS name
`piglet.tailf5e9c9.ts.net`) — see
[`../README.md`](../README.md#using-the-tailscale-backend) for details:

| Flavor | `API_BASE_URL` |
|---|---|
| `alpha` | `http://piglet.tailf5e9c9.ts.net:5002/api/v1/` |
| `beta` | `http://piglet.tailf5e9c9.ts.net:5001/api/v1/` |
| `production` | `http://piglet.tailf5e9c9.ts.net:5000/api/v1/` |

No public HTTPS backend is planned currently. Every build variant — all three debug
flavors and all three release variants — includes a network security override
allowing cleartext HTTP to that one host only (the exception is scoped by hostname,
not port, so it covers all three flavors' ports the same way):
`app/src/<flavor>Debug/res/xml/network_security_config_<flavor>_debug.xml` for debug,
and `app/src/release/res/xml/network_security_config_release.xml` (shared by all
three release variants) for release. Every other host stays blocked in every variant.

If separate backend **hosts** (with real HTTPS for beta/production) become necessary
later — e.g. before a public Play upload — give each flavor its own host in
`API_BASE_URL` in `app/build.gradle.kts`, remove the corresponding cleartext
exceptions, and update this section.

## Version codes

Every AAB uploaded to this Play application must have a new, higher `versionCode`,
regardless of track. Never upload two different AAB files with the same version code.
Update the single value in `defaultConfig` manually before each upload. For example:

```text
versionCode 1 -> alphaRelease
versionCode 2 -> alphaRelease
versionCode 3 -> betaRelease
versionCode 4 -> productionRelease
versionCode 5 -> next alphaRelease
```

## Build commands and outputs

```bash
./gradlew assembleAlphaDebug
./gradlew assembleBetaDebug
./gradlew assembleProductionDebug

./gradlew bundleAlphaRelease
./gradlew bundleBetaRelease
./gradlew bundleProductionRelease
```

APK outputs are written below `app/build/outputs/apk/<flavor>/debug/`. AAB outputs
are written below `app/build/outputs/bundle/<flavor>Release/`.

Every `bundle<Flavor>Release` task also triggers a `rename<Flavor>Release` task
(`app/build.gradle.kts`) that copies the `.aab` to
`app/build/outputs/bundle-renamed/tracker-tavex-android-<environment>-<versionName>.aab`
— use this copy for uploads; its name identifies the environment and version
without opening the file.

## Launcher icons

The supplied production artwork is the common launcher-icon source. The white outer
canvas was removed while preserving the rounded icon, shadows, and logo. Android
density assets are generated at 48, 72, 96, 144, and 192 px, plus a 432 px adaptive
foreground.

Alpha adds a `#F44336` red dot, Beta adds a `#FFC107` yellow dot, and
`productionDebug` adds a `#2196F3` blue dot. All dots share the same 11% diameter and
position. `productionRelease` uses the untouched artwork. Adaptive icon XML and the
background layer remain shared and unchanged.

## Signing

No signing secrets, credentials, or machine-specific paths are stored in this
project. Configure Play App Signing and a secure upload-key signing configuration
outside source control before uploading release AAB files. Do not commit keystores,
store passwords, key aliases, or key passwords.
