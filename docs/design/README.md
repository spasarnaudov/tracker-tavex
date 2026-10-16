# Design Documentation — Android App

This folder holds the instructions Claude Design follows when designing the
Tracker Tavex Android app's UI. It defines **process and constraints**, not
the design itself — the actual screens, colors, and components are produced by
Claude Design during the workflow below and reviewed by the project owner.

## Files

- [`claude-design-workflow.md`](claude-design-workflow.md) — the mandatory
  process: analysis first, no code changes, approval gates, implementation
  stage. Claude Design-specific; extends the general AI rules in
  [`../../AI_INSTRUCTIONS.md`](../../AI_INSTRUCTIONS.md).
- [`design-system.md`](design-system.md) — requirements the visual design
  system must satisfy (Material Design 3, light/dark theme, accessibility,
  density). Not a finished palette — Claude Design proposes the actual tokens
  within these constraints.
- [`ux-guidelines.md`](ux-guidelines.md) — information hierarchy, navigation,
  required screen states, accessibility, responsive behavior.
- [`screen-brief-template.md`](screen-brief-template.md) — the fixed template
  Claude Design fills in for every screen before implementation.

## Related documents

- [`../../AI_INSTRUCTIONS.md`](../../AI_INSTRUCTIONS.md) — general AI workflow
  rules for this project (all tools, not just Claude Design).
- [`../android/class-organization.md`](../android/class-organization.md),
  [`../android/function-organization.md`](../android/function-organization.md),
  [`../android/compose-resource-organization.md`](../android/compose-resource-organization.md),
  [`../android/xml-resource-organization.md`](../android/xml-resource-organization.md)
  — how approved designs get organized in Kotlin/Compose code. This folder
  defines *what* to design; those define *how the result is coded*.
- [`../../apps/android/README.md`](../../apps/android/README.md) — current
  Android architecture, screens, and technologies.

## Current state vs. target state

The app currently uses default Material 3 with no defined color, type, or
spacing tokens, and no documented dark theme, accessibility, or state
(loading/empty/error) conventions. The documents in this folder describe the
**target** state Claude Design should design toward, not something already
implemented.
