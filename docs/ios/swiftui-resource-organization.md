# SwiftUI Resource Organization

## Purpose

This document defines how UI values must be named, grouped, and organized in the
iOS app's SwiftUI code.

It covers:

- user-visible strings;
- colors;
- typography;
- spacing;
- reusable component styling (`ButtonStyle`, `TextFieldStyle`, `ViewModifier`).

This document applies only to SwiftUI code. There is no UIKit code in this
project to migrate.

## Core Principles

1. Static, app-authored user-visible text must use a String Catalog entry once one
   exists (see User-Visible Strings below for the project's current state).
2. Shared design values must use the project's existing `Core/Theme` types
   (`MaterialColor`, `MaterialFont`) rather than inline literals.
3. Screen-specific values must remain close to the screen that owns them.
4. Repeated or meaningful UI literals must be replaced with named values.
5. Names must describe purpose rather than numeric value.
6. Values inside a resource enum must be grouped by semantic role, in that role's
   natural reading order — not alphabetically (see `type-organization.md`'s Enum
   Namespace Organization for why, with `MaterialColor`/`MaterialFont` as the
   verified real examples).
7. Existing project architecture and terminology must be preserved.

## Current State

Colors and typography already have an established, working convention:
`Core/Theme/MaterialColor.swift` and `Core/Theme/MaterialTypography.swift`, an
enum-namespace-of-`static let` pattern matching the Android client's Material 3
baseline palette (see the doc comments in those files). Component styling
(`Core/Theme/MaterialComponents.swift`) follows the same idea, as `ButtonStyle`/
`TextFieldStyle`/`ViewModifier` types with a `static var` accessor extension
(`.materialFilled`, `.materialOutlined`, `.materialList()`).

Spacing and user-visible text do not have an equivalent convention yet:

- Every screen uses ad-hoc numeric literals for padding and corner radius
  (`.padding(.vertical, 12)`, `cornerRadius: 4`, and similar) — there is no
  spacing-scale type today.
- Every user-visible string in the app is a hardcoded Swift string literal
  directly in the `View` (`Text("Log In")`, `TextField("Username", ...)`).
  There is no String Catalog (`.xcstrings`), `.strings` file, or `.lproj`
  directory anywhere in the project — `CFBundleDevelopmentRegion` in `Info.plist`
  is the only localization-related setting, and it's unused boilerplate.

The sections below describe the **target** state to design toward for spacing and
strings, following the same conventions already established for colors and
typography — not something already implemented.

## User-Visible Strings

Static, app-authored user-visible text should use a String Catalog
(`Localizable.xcstrings`, Xcode's default mechanism since Xcode 15 — this
project's iOS 17 deployment target already supports it) once one is added to the
project, instead of a Swift string literal.

Use (once a String Catalog exists):

```swift
Text("screen.login.title")
```

Do not introduce a String Catalog and migrate all existing literals in the same
change as an unrelated feature — that's a broader migration requiring an explicit
request, per Architecture Preservation below. Until a String Catalog exists, a
literal like `Text("Log In")` is the project's current, accepted convention, not a
violation to fix opportunistically.

This rule does not apply to dynamic runtime content, which cannot be a String
Catalog entry because it doesn't exist until the app is running: usernames, asset
or item names, server-provided error messages (for example, `AppError`'s own
`message`, or `error.localizedDescription` shown via `errorMessage = error.localizedDescription`
in every ViewModel's `catch` block), other API content, and user-entered text.
Only the static, app-authored label/fallback text around such values belongs in a
String Catalog entry — the dynamic value itself is interpolated in as a format
argument or displayed as received.

## Colors

Application-wide colors must use `MaterialColor` (`Core/Theme/MaterialColor.swift`)
rather than a literal `Color(...)` or `UIColor(...)` value.

Use:

```swift
.foregroundStyle(MaterialColor.onSurface)
.background(MaterialColor.primary.opacity(configuration.isPressed ? 0.85 : 1))
```

Do not use:

```swift
.foregroundStyle(Color(red: 0.1, green: 0.1, blue: 0.1))
```

Do not scatter direct color literals across `View` bodies.

If a new color role is genuinely needed and `MaterialColor` doesn't have it, add
it to `MaterialColor` following its existing `dynamic(light:dark:)` pattern and
role-grouped ordering — do not create a separate, screen-specific color file, and
do not migrate to native Asset Catalog colorsets (`AccentColor.colorset` exists
today only as an empty Xcode-generated placeholder, not the project's real color
source).

## Typography

Application-wide text styling must use `MaterialFont`
(`Core/Theme/MaterialTypography.swift`) rather than a literal `Font.system(...)`
value.

Use:

```swift
Text("Tracker Tavex")
    .font(MaterialFont.headlineSmall)
```

Do not declare a direct `Font.system(size:weight:)` value when an existing
`MaterialFont` role already represents the intended design.

If a new type-scale role is genuinely needed, add it to `MaterialFont` in its
existing hierarchy-ordered position (headline → title → body → label, largest
first) — do not create a screen-specific typography file.

## Spacing

Repeated or semantically important spacing/corner-radius values should not remain
as literals inside `View` bodies once a spacing scale exists.

Recommended target shape, following `MaterialColor`/`MaterialFont`'s existing
pattern:

```swift
enum AppSpacing {
    static let small: CGFloat = 4
    static let medium: CGFloat = 12
    static let large: CGFloat = 20
}
```

```swift
.padding(.vertical, AppSpacing.medium)
```

Order values by scale (smallest to largest), matching `MaterialFont`'s
hierarchy-ordered convention — not alphabetically.

A one-time literal may remain inline when it is used once, has no reusable design
meaning, and naming it would not improve readability. The exception must not be
used for primary screen padding or a value repeated across multiple screens.

## Component-Specific Styling

A reusable visual style shared by multiple screens should be a `ButtonStyle`,
`TextFieldStyle`, or `ViewModifier` type in `Core/Theme/MaterialComponents.swift`,
with a `static var` accessor extension — matching `materialFilled`,
`materialTonal`, `materialOutlined`, and `materialList()`.

Use:

```swift
Button("Save") { ... }
    .buttonStyle(.materialFilled)
```

Do not duplicate a component's styling inline in a specific screen's `View` when
an equivalent `MaterialComponents` style already exists.

A screen-specific visual treatment that only one screen uses does not need to
live in `MaterialComponents.swift` — keep it local to that screen's `View` file
unless it's genuinely reused elsewhere.

## Screen Definition

A screen is a single user-visible navigation destination, matching this project's
`Features/` folder structure: Login, Register, Portfolio, PortfolioHistory,
Assets, AssetDetail, Account, Admin, AdminLogDetail, Connection.

## File Organization

Shared theme and design-system values belong in `Core/Theme/`, alongside the
existing `MaterialColor.swift`, `MaterialTypography.swift`, and
`MaterialComponents.swift`. A new shared value type (for example `AppSpacing`)
belongs in that same package, in its own file — for example `Core/Theme/Spacing.swift`.
Do not create it speculatively; add it only when a spacing scale is actually
introduced.

Do not create a parallel package when `Core/Theme/` already exists as the
authoritative location for these values.

## Allowed Inline Values

The following values may remain directly in Swift code:

- framework-required constants;
- algorithmic constants unrelated to rendering;
- values used only in calculations;
- one-time trivial UI literals without reusable design meaning.

The exception must not be used for user-visible text, repeated spacing, primary
screen padding, direct repeated colors, or shared typography.

## Architecture Preservation

These rules must adapt to the existing project architecture.

An agent must not:

- introduce a String Catalog and migrate every existing string literal to it in
  the same change as an unrelated feature, without an explicit request;
- migrate `MaterialColor`/`MaterialFont`/`MaterialComponents` to native Asset
  Catalog resources or SwiftUI's built-in styles without an explicit request;
- replace the project's Material 3 baseline palette with a different design
  system without an explicit request;
- reorganize unrelated screens while implementing a local change.

When existing code conflicts with this document, apply these rules only to new or
modified code unless a broader migration is explicitly requested.

## Review Checklist

Before completing a change, verify that:

- static, app-authored user-visible text uses a String Catalog entry, if one
  exists in the project by the time of the change (dynamic runtime content —
  usernames, asset/item names, server-provided messages, other API content, and
  user-entered text — is exempt, see User-Visible Strings);
- shared colors use `MaterialColor`, not literal `Color`/`UIColor` values;
- shared typography uses `MaterialFont`, not literal `Font.system(...)` values;
- repeated or meaningful spacing/corner-radius literals are named, once a spacing
  scale exists to name them with;
- reusable component styling uses an existing `MaterialComponents` style rather
  than being duplicated inline;
- values inside a resource enum are grouped by semantic role, not forced into
  alphabetical order;
- names describe purpose rather than numeric value;
- the existing project architecture (`Core/Theme`, Material 3 baseline palette)
  has not been changed without an explicit request.
