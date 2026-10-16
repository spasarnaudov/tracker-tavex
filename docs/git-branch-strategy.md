# Git Branch Strategy

## Purpose

This document defines how branches, commits, and pushes are organized in this repository.

It applies to:

- feature branches;
- fix branches;
- documentation and chore branches;
- branches created during AI coding sessions.

This document defines branch and push workflow only. It does not define commit message conventions, code style, or release/versioning strategy.

## Core Principles

1. `main` is always stable and deployable.
2. Work happens on short-lived branches, not directly on `main`.
3. Each branch represents one logical change.
4. Branches are merged into `main` through a pull request, even for solo work.
5. Pushing to any remote branch requires explicit confirmation from the project owner for that specific push.
6. Merged branches are deleted.

## Branch Types

Prefix branch names by type:

- `feature/` — new functionality
- `fix/` — bug fixes
- `docs/` — documentation-only changes
- `chore/` — maintenance, tooling, dependency updates
- `refactor/` — internal restructuring without behavior change

Prefix by domain when a change is scoped to one part of the project, combined with the type:

- `db/chore/...`
- `scripts/chore/...`

Use plain `feature/`, `fix/`, etc. for changes to `apps/flask` or for cross-cutting changes.

Branches created during AI coding sessions keep their generated `claude/<description>-<id>` name and are not renamed to match this scheme; they are treated as `feature/`-equivalent branches for workflow purposes.

## Naming

Use lowercase, hyphen-separated descriptions after the prefix.

Prefer:

```text
feature/portfolio-value-chart
fix/session-timeout-logout
docs/git-branch-strategy
```

Avoid:

```text
new-stuff
JohnTest
fix2
```

## Workflow

1. Create a branch from the latest `main`.
2. Make one logical change per branch.
3. Keep the branch short-lived; merge before it diverges significantly from `main`.
4. Sync with `main` (merge or rebase — pick one and stay consistent within a branch) if `main` moves while the branch is open.
5. Open a pull request into `main`, even when working solo — it documents *why*, and gives the project owner an explicit review point (per `AI_INSTRUCTIONS.md`).
6. Merge only after the project owner approves.

## Push Policy

Pushing to the remote is a separate decision from committing locally.

- Local commits may be created while iterating, per the commit rule in `AI_INSTRUCTIONS.md`.
- Every push to a remote branch — a new branch, an update to an existing branch, or a force-push — requires explicit confirmation from the project owner immediately before that specific push. A previous approval does not carry over to a later push.
- Before running `git push`, an AI agent must state which branch and which commits it intends to push, and wait for explicit confirmation.
- Force-pushing is only performed when explicitly requested, and never on `main`.

## Branch Protection

- No direct pushes to `main`, except trivial non-code fixes (for example, a typo in `README.md`) explicitly approved by the project owner.
- No force-push to `main`.
- `main` only receives merges through reviewed pull requests.

## Cleanup

- Delete a branch, both local and remote, immediately after it is merged.
- Do not keep long-lived branches "just in case" — recreate from `main` if the work resumes later.

## Review Checklist

Before opening a pull request, verify that:

- the branch name follows the type/domain prefix convention;
- the branch represents a single logical change;
- the branch is reasonably short-lived and close to current `main`;
- every push to the remote was explicitly confirmed beforehand;
- no unrelated changes were bundled in.
