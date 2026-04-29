# Git Branch & Commit Naming — Reference

> **Single source of truth** for branch names and commit messages in the
> per-ticket development pipeline. The `git-branch-manager.skill.md` and
> `git-operations.behaviour.md` both defer to this document. If anything
> here changes, those two files must be re-aligned (no inline duplication
> of these tables anywhere else).

---

## 1. Team Prefixes

Asked once per ticket at pipeline start (`PREFIX:` response). The prefix
becomes part of every branch name and is recorded in `tickets/{TICKET_ID}/git-context.md`.

| Prefix | Domain            | Notes |
|--------|-------------------|-------|
| `AIS`  | AI Services       | |
| `EXE`  | Execution Engine  | |
| `CVS`  | Core Service      | |
| `FE`   | Frontend          | |

> Jira ticket keys do **not** carry the team prefix natively (e.g. Jira
> may issue `ABCDEF-123`). The pipeline composes the full `TICKET_ID`
> as `<TEAM_PREFIX>-<TICKET_NUMBER>` (e.g. `AIS-123`) for branch and
> commit message use.

---

## 2. Branch Patterns

Branch type is auto-detected from the Jira issue type, with human override
available at the `PREFIX:` checkpoint.

| Branch Type | Pattern                                          | Example                                |
|-------------|--------------------------------------------------|----------------------------------------|
| Feature     | `<TEAM_PREFIX>-<TICKET_NUMBER>/<slug>`           | `AIS-101/add-model-router`             |
| Bugfix      | `<TEAM_PREFIX>-<TICKET_NUMBER>/bugfix/<slug>`    | `EXE-205/bugfix/timer-expiry-fix`      |
| Release     | `release/<version>`                              | `release/1.0.0`                        |
| Hotfix      | `hotfix/<TICKET_ID>-<slug>`                      | `hotfix/EXE-500-timeout-crash`         |

### Slug rules
- Lowercase ASCII only.
- Words separated by `-`.
- Strip punctuation; collapse repeated `-`; trim leading/trailing `-`.
- Max 50 characters.
- Derived from the Jira ticket title (or release version for `release/*`).

### Auto-detect mapping (Jira issue type → branch type)
| Jira Issue Type        | Branch Type |
|------------------------|-------------|
| Story                  | Feature     |
| Task                   | Feature     |
| Sub-task               | Feature     |
| Bug                    | Bugfix      |
| Release / Release Task | Release     |
| Hotfix / Incident      | Hotfix      |

If the issue type is missing or unrecognised, default to **Feature** and
require human confirmation before proceeding.

---

## 3. Base Branch Policy

| Branch Type | Base Branch(es) Allowed     | Default Base |
|-------------|-----------------------------|--------------|
| Feature     | `develop`                   | `develop`    |
| Bugfix      | `feature/*`, `develop`, `release/*` | `feature`    |
| Release     | `develop`                   | `develop`    |
| Hotfix      | `main`                      | `main`       |

> Bugfix is intentionally flexible — pick the most stable branch that
> reproduces the bug. The skill asks the human if any base other than the
> default is needed.

---

## 4. Conventional Commit Format

Every commit MUST match this shape:

```
<type>: <short summary> -- <TICKET_ID>

[optional body]

[optional footer]
```

### `<type>` values

| Type       | Use for                                              |
|------------|------------------------------------------------------|
| `feat`     | New feature                                          |
| `fix`      | Bug fix                                              |
| `refactor` | Code restructuring (no behaviour change)             |
| `test`     | Tests added or updated                               |
| `docs`     | Documentation only (incl. pipeline artifacts)        |
| `chore`    | Build / dependencies                                 |
| `perf`     | Performance improvement                              |
| `ci`       | CI / pipeline configuration changes                  |

### `<short summary>` rules
- Imperative mood (`add`, `fix`, `update` — not `added`/`fixed`).
- ≤ 60 characters before the ` -- <TICKET_ID>` suffix.
- Lowercase first letter.
- No trailing period.

### Optional body
- Wrap at 72 chars per line.
- Bullet list of the key changes is preferred.
- Separated from the subject by one blank line.

### Optional footer
- `Refs: <TICKET_ID>` or related ticket IDs.
- `Co-authored-by: Name <email>`
- `BREAKING CHANGE: <description>` if the commit breaks public contract.

### Examples

```
feat: add container memory limit -- EXE-205
```

```
fix: handle null tokens in router -- AIS-101

- Guard against empty payloads in ModelRouter.dispatch
- Add unit coverage for the empty-token path

Refs: AIS-101
```

```
test: cover timer-expiry edge cases -- EXE-205/bugfix/timer-expiry-fix
```

```
docs: record architecture decision -- CVS-318
```

---

## 5. Stage → Default Commit Type

The orchestrator pre-fills the commit type at each commit checkpoint
based on the stage. Humans can always override via `EDIT:`.

| Stage                                | Default `<type>` |
|--------------------------------------|------------------|
| Stage 1 — Architecture               | `docs`           |
| Stage 2 — Implementation (Story/Task)| `feat`           |
| Stage 2 — Implementation (Bug)       | `fix`            |
| Stage 3a — Unit Tests (run N)        | `test`           |
| Stage 3b — Code Review (run N)       | `docs`           |
| Stage 3c — Bugfix (run N)            | `fix`            |
| Refactor-only changes                | `refactor`       |
| CI / pipeline-only changes           | `ci`             |
| Build / deps-only changes            | `chore`          |
| Performance-only changes             | `perf`           |

---

## 6. Forbidden Patterns

The skill MUST reject (and the behaviour MUST flag) any of the following:

- Branch names containing whitespace, uppercase letters (except the team
  prefix), or characters outside `[a-z0-9._/-]`.
- Branch names that begin with `feature/`, `bug/`, or any pattern other
  than the four listed above.
- Direct commits or pushes to `main`, `master`, `develop`, or `release/*`.
- Commit messages missing the ` -- <TICKET_ID>` suffix.
- Commit subject lines longer than 72 chars including the suffix.
- Commit `<type>` values outside the table in §4.

---

## 7. Quick Reference Card

```
Branch  : <PREFIX>-<NUM>/<slug>                    (feature)
        : <PREFIX>-<NUM>/bugfix/<slug>             (bug)
        : release/<version>                        (release)
        : hotfix/<PREFIX>-<NUM>-<slug>             (hotfix)

Commit  : <type>: <short> -- <PREFIX>-<NUM>
Types   : feat | fix | refactor | test | docs | chore | perf | ci

Examples:
  AIS-101/add-model-router
  EXE-205/bugfix/timer-expiry-fix
  release/1.0.0
  hotfix/EXE-500-timeout-crash

  feat: add container memory limit -- EXE-205
  fix:  handle null tokens in router -- AIS-101
  test: cover timer-expiry edge cases -- EXE-205
```

