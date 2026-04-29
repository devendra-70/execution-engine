# Behaviour: Git Operations

## Purpose
Define the **safety invariants** every agent and skill in the pipeline must
respect when interacting with Git or GitLab. This behaviour guards the
repository against accidental destructive actions, off-branch writes, and
unauthorised commits/pushes — regardless of which agent or skill is running.

The `git-branch-manager.skill.md` is the **only** component allowed to
execute Git side-effects. All other agents are read-only with respect to Git.

---

## Scope

This behaviour is inherited by:
- Orchestrator Ticket Agent (executes Git via the skill)
- Architecture / Backend / Unit Test / Code Review / Bugfix agents
  (must NOT execute Git directly; they only produce artifacts the skill reads)

---

## Core Invariants (NEVER violate)

1. **No automatic Git side-effects.** No agent commits, pushes, merges,
   rebases, or creates branches without an explicit human prompt routed
   through the orchestrator.
2. **Single executor.** Only `git-branch-manager.skill.md` runs Git or
   GitLab MCP commands. Specialist agents that need to know the current
   branch must request it via `report_status`, never invoke `git` themselves.
3. **No protected-branch writes.** Never commit directly to, push directly to,
   or check out (for editing) any of: `main`, `master`, `develop`,
   `release/*`. Hotfix branches base off `main` but writes happen on the
   hotfix branch, never on `main`.
4. **No history rewriting.** No `--force`, `--force-with-lease`, `rebase -i`,
   `reset --hard` against shared branches, `commit --amend` after push,
   `cherry-pick`, or `filter-branch`.
5. **No `git add .` / `git add -A`.** Staging is always scoped to the explicit
   file list resolved from the stage's artifact (`files-changed.md`,
   `run-N-bugfix.md`, etc.).
6. **No silent recovery.** Any Git error (dirty tree, divergence, push
   rejection, missing remote, detached HEAD, merge in progress) halts the
   pipeline and surfaces to the human via the orchestrator.
7. **Conventional Commit shape enforced.** Every commit message must match
   `<type>: <short> -- <TICKET_ID>` (with optional body/footer). Messages
   provided via `EDIT:` are validated and rejected if malformed.
8. **One ticket = one branch.** An agent must never write to source files
   while the working tree is on a branch that does not belong to the
   active `TICKET_ID` recorded in `git-context.md`.

---

## Required Pre-flight (before any Git side-effect)

The skill must verify, in order:

1. `git-context.md` exists for the active `TICKET_ID` and `git_enabled: true`.
2. Current branch matches the `Branch name` recorded in `git-context.md`.
3. Working tree state is consistent with the requested operation
   (clean for `setup_branch`, has staged-able changes for `commit_stage`,
   has commits ahead of base for `push_branch`).
4. No rebase / merge / cherry-pick / bisect is in progress
   (`.git/MERGE_HEAD`, `.git/REBASE_HEAD`, `.git/CHERRY_PICK_HEAD` absent).
5. Remote `origin` is reachable for any operation that touches the network.

If any check fails → halt, log, surface to human.

---

## Branch Boundary Rules

| Agent | May trigger Git via skill | May read Git status | May execute `git` directly |
|---|---|---|---|
| Orchestrator | YES (only path allowed) | YES | NO |
| Architecture | NO | YES (status only) | NO |
| Backend Implementation | NO | YES | NO |
| Unit Test | NO | YES | NO |
| Code Review | NO | YES | NO |
| Bugfix | NO | YES | NO |

If a non-orchestrator agent emits a Git command or attempts to spawn a Git
process, that is a **boundary violation** and must be aborted with the same
report format used in `file-lock.behaviour.md`:

```
GIT BOUNDARY VIOLATION:
  Agent          : {AGENT_NAME}
  Attempted op   : {git command attempted}
  Reason flagged : Only the orchestrator + git-branch-manager skill may run Git
  Action taken   : Aborted. Awaiting orchestrator instruction.
```

---

## Commit / Push Authorisation

A Git side-effect is **authorised** only if ALL of the following are true:

1. The orchestrator presented the corresponding checkpoint
   (commit checkpoint or `PUSH` prompt) to the human.
2. The human responded with `COMMIT`, `EDIT: ...`, or `PUSH` (exactly).
3. The active `TICKET_ID`'s `git-context.md` shows `git_enabled: true`.
4. Pre-flight checks (above) all passed.

Silence, partial input, or ambiguous responses MUST NOT be interpreted as
authorisation.

---

## Logging Requirements

Every Git event MUST be appended to `master-agent-log.md`. Required event
codes (owned by the orchestrator):

```
BRANCH_CREATED        → setup_branch created a new branch
BRANCH_REUSED         → setup_branch reused an existing local branch
BRANCH_REUSED_REMOTE  → setup_branch checked out a tracking branch from origin
COMMIT_CREATED        → commit_stage produced a new commit
COMMIT_SKIPPED        → human responded SKIP at a commit checkpoint
PUSH_CREATED          → push_branch pushed to origin
GIT_PREFLIGHT_FAILED  → any pre-flight check failed
GIT_BOUNDARY_VIOLATION→ a non-authorised agent attempted Git
GIT_DISABLED          → git_enabled: false recorded for the ticket
```

A dedicated `git-operations-log.md` (Step 6) mirrors these for Git-only auditing.

---

## File Lock Interaction

`git-context.md` is owned by the orchestrator and protected by the
`{locks.orchestrator}` lock area defined in `file-lock.behaviour.md`.
The skill must acquire this lock before mutating `git-context.md`
(append commit row, flip `Pushed` flag, mark disabled) and release it
immediately after.

The skill MUST NOT acquire any agent-area lock (architecture, implementation,
tests, review, bugfix). It only reads stage artifacts to resolve file lists.

---

## Config-Permission Interaction

Any change to `.gitignore`, `.gitattributes`, `.gitmodules`, Git hooks under
`.git/hooks/`, or repo-level Git config is a **config change** and must go
through `config-permission.behaviour.md`. The Git skill itself never edits
these files; if a stage's artifact lists one of them, the skill must:

1. Stop staging that file.
2. Surface a `CONFIG_CHANGE_REQUEST` to the orchestrator.
3. Resume staging the remaining files only after the human ALLOWs the change
   (the change itself is then made by the responsible agent, not the Git skill).

---

## Failure Modes & Required Responses

| Failure | Required response |
|---|---|
| Working tree dirty at `setup_branch` | Halt; list files; ask human to clean/stash |
| Branch belongs to a different `TICKET_ID` | Halt; refuse all Git ops until human switches |
| Detached HEAD | Halt; require explicit checkout |
| Push rejected (non-fast-forward) | Halt; surface remote SHA; never auto-resolve |
| Remote unreachable | Halt; mark operation as failed; do not retry silently |
| Authorised `COMMIT` but no staged changes | Treat as `SKIP`; log `COMMIT_SKIPPED` |
| Malformed `EDIT:` message | Reject; re-prompt; do not commit |
| GitLab MCP + `git` CLI both unavailable | Set `git_enabled: false`; continue pipeline without Git |

---

## Non-Goals

- Does NOT govern the *content* of code commits — only the act of committing.
- Does NOT define MR/PR opening rules (future behaviour).
- Does NOT replace `file-lock.behaviour.md` or `config-permission.behaviour.md`
  — it composes with them.

