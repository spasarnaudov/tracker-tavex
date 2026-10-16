# Claude Design Workflow — Android App

## Purpose

This document defines how Claude Design must work on the Tracker Tavex
Android app's UI. It applies only to the design phase described here — general
AI-agent rules for this project (commit discipline, explaining changes,
asking before broad changes) already exist in
[`../../AI_INSTRUCTIONS.md`](../../AI_INSTRUCTIONS.md) and are not repeated
here. Where the two overlap, this document is the stricter, design-phase
version; it does not replace the general rules.

## Scope

Applies to: Claude Design working on the Android app's UI/UX. Does not apply
to the Flask app, iOS app, or backend/database work.

## Mandatory rules

MUST:

- Analyze the real project and existing screens before proposing anything.
- Base every proposal on the app's actual features and data — see
  [`../../apps/android/README.md`](../../apps/android/README.md) for the
  current architecture and screen list.
- Produce design concepts and visual variants only, until a screen is
  explicitly approved.
- Present every screen using [`screen-brief-template.md`](screen-brief-template.md).
- Wait for feedback after each screen, or each logically grouped set of
  screens, before moving to the next.
- Apply corrections to a screen until the project owner approves it.
- Treat an approved design as the source of truth once approved — do not
  silently redesign an approved screen while working on a later one.
- Flag assumptions and open questions explicitly, as part of the screen
  brief, instead of silently deciding them.

MUST NOT:

- Invent features that do not exist in the project.
- Change business logic.
- Change API contracts, models, or navigation without explicit approval.
- Edit files containing program code, until the separate Implementation stage
  below has been explicitly requested.
- Generate production implementation during the design phase.
- Create a commit, branch, or pull request, until the separate Implementation
  stage below has been explicitly requested.
- Implement a screen before it has been explicitly approved.

## Workflow stages

1. Analyze the repository (existing screens, architecture, constraints).
2. Describe the current state.
3. Identify UX/UI problems in the current state.
4. Propose a design direction.
5. Define the design system within [`design-system.md`](design-system.md)'s
   constraints.
6. Create visual variants of screens, one screen (or group) at a time, using
   [`screen-brief-template.md`](screen-brief-template.md).
7. Present for review.
8. Apply corrections.
9. Get explicit approval.
10. Implementation — separate stage, starts only on an explicit, separate
    command from the project owner (see below).

## Implementation stage

Implementation is a distinct stage, not an automatic continuation of design
approval. It MUST NOT start until the project owner gives an explicit,
separate command to implement.

Once implementation is explicitly requested:

- Implement only screens that have been explicitly approved.
- Follow the existing code-organization rules —
  [`../android/class-organization.md`](../android/class-organization.md),
  [`../android/function-organization.md`](../android/function-organization.md),
  [`../android/compose-resource-organization.md`](../android/compose-resource-organization.md).
- Do not deviate from the approved design without going back for a new
  approval.
- Follow the general commit/branch/PR rules in
  [`../../AI_INSTRUCTIONS.md`](../../AI_INSTRUCTIONS.md) (no commit without
  an explicit request).
