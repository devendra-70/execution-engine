# Skill: Git Branch Manager

## Purpose
Provide a single, deterministic interface the Orchestrator uses to:
1. Ask for the **team prefix** once at pipeline start.
2. **Create / checkout** the correct Git branch for a ticket using the team
   naming convention (driven by Jira issue type, with human override).
3. **Stage and commit** changes after each pipeline stage — only when the
   human explicitly prompts the orchestrator to do so.
4. **Push** the branch to GitLab only when the human explicitly issues a
   `PUSH` command.

This skill does NOT generate code, does NOT auto-commit, and does NOT
auto-push. Every Git side-effect requires a human checkpoint.

---

## When This Skill Is Invoked

| Trigger | Operation |
|---|---|
| Pipeline start, immediately after `ticket-context-builder` | `setup_branch` |
| After each pipeline stage (1, 2, 3a, 3b, 3c, 4) when human types `COMMIT` | `commit_stage` |
| Any time the human types `PUSH` | `push_branch` |
| Any time the human types `GIT STATUS` | `report_status` |

The skill is **idempotent**: re-invoking `setup_branch` for an already-prepared
ticket reuses the existing branch and does not recreate it.

---

## Tooling

This skill assumes the IDE has the **GitLab MCP server** and standard `git`
CLI available. Operations are performed in this order of preference:
1. GitLab MCP commands (for remote-aware ops: branch create on origin, push, MR).
2. Local `git` CLI (for stage, commit, status, checkout).

If neither is available, the skill MUST halt and surface the failure to the
orchestrator — it never attempts a partial Git operation.

---

## Inputs Required

| Input | Source | Notes |
|---|---|---|
| `TICKET_ID` | Already in `ticket-context.md` | e.g. `AIS-101` |
| `TICKET_TYPE` | From `ticket-context.md` (Jira) | Story / Bug / Task / Sub-task |
| `TEAM_PREFIX` | Asked from human at pipeline start | One of `AIS` / `EXE` / `CVS` / `FE` |
| `STAGE_LABEL` | Provided by orchestrator at commit time | e.g. `architecture`, `implementation`, `tests-run-2` |
| `FILES_CHANGED` | Read from stage artifact (`files-changed.md`, `run-N-bugfix.md`, etc.) | Used to scope `git add` |

---

## Naming Convention (Single Source of Truth)

### Team Prefixes
| Prefix | Domain |
|---|---|
| `AIS` | AI Services |
| `EXE` | Execution Engine |
| `CVS` | Core Service |
| `FE`  | Frontend |

### Branch Patterns
| Jira Issue Type | Pattern | Example |
|---|---|---|
| Story / Task / Sub-task (default) | `<TEAM_PREFIX>-<TICKET_NUMBER>/<slug>` | `AIS-101/add-model-router` |
| Bug | `<TEAM_PREFIX>-<TICKET_NUMBER>/bugfix/<slug>` | `EXE-205/bugfix/timer-expiry-fix` |
| Release | `release/<version>` | `release/1.0.0` |
| Hotfix | `hotfix/<TICKET_ID>-<slug>` | `hotfix/EXE-500-timeout-crash` |

`<slug>` rules:
- Derived from Jira ticket title.
- Lowercase, ASCII only, words separated by `-`.
- Strip punctuation; collapse repeated `-`; max 50 chars.

`TICKET_ID` does not already include the prefix (Jira ticket id is like `ABCDEF-123`).
If the orchestrator's `TICKET_ID` lacks the prefix, the skill prepends
asks for team prefix and prepends it to form the full `TICKET_ID` for branch naming.

### Base Branch Policy
| Branch Type | Base Branch                      |
|---|----------------------------------|
| Feature / Story | `develop`                        |
| Bugfix | `feature` , `develop` , `release` |
| Release | `develop`                        |
| Hotfix | `main`                           |

The bugfix branch type is flexible to allow branching off the most appropriate stable branch depending on the context of the bug.
If the configured base branch does not exist locally, the skill fetches it
from `origin` first.

---

## Operation 1 — `setup_branch`

### Step 1.1 — Ask Team Prefix (once per ticket)
If `tickets/{TICKET_ID}/git-context.md` does not exist, the orchestrator
prompts the human:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌿 GIT BRANCH SETUP — {TICKET_ID}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Which team owns this ticket?
  AIS  → AI Services
  EXE  → Execution Engine
  CVS  → Core Service
  FE   → Frontend

Detected Jira issue type : {TICKET_TYPE}
Proposed branch type     : {auto-detected: feature | bugfix | release | hotfix}
Proposed branch name     : {computed name}

RESPOND WITH:
  PREFIX: <AIS|EXE|CVS|FE>                  → Use detected type
  PREFIX: <AIS|EXE|CVS|FE> TYPE: <type>     → Override branch type
  CANCEL                                    → Skip git setup for this run
─────────────────────────────────────────────────────
```

If the human responds `CANCEL`, the skill records `git_enabled: false` in
`git-context.md` and the orchestrator continues without any Git operations.

### Step 1.2 — Pre-flight Checks
Before creating any branch, verify:
1. Working tree is clean (`git status --porcelain` is empty). If dirty, halt
   and surface the dirty file list to the human.
2. We are not in a detached HEAD state.
3. The configured base branch exists (fetch from `origin` if missing).
4. The remote `origin` is reachable.

Any failure → halt, surface error, do NOT auto-recover.

### Step 1.3 — Create or Reuse Branch
```
git fetch origin
git checkout <BASE_BRANCH>
git pull --ff-only origin <BASE_BRANCH>

if branch <COMPUTED_NAME> exists locally:
    git checkout <COMPUTED_NAME>
elif branch exists on origin:
    git checkout -b <COMPUTED_NAME> origin/<COMPUTED_NAME>
else:
    git checkout -b <COMPUTED_NAME>
```

The branch is **not pushed** at this stage.

### Step 1.4 — Persist Git Context
Write `tickets/{TICKET_ID}/git-context.md`:

```markdown
# Git Context — {TICKET_ID}

**Generated:** {ISO_8601_TIMESTAMP}
**git_enabled:** true

| Field         | Value                            |
|---------------|----------------------------------|
| Team prefix   | {AIS|EXE|CVS|FE}                 |
| Branch type   | feature | bugfix | release | hotfix |
| Branch name   | {COMPUTED_NAME}                  |
| Base branch   | {BASE_BRANCH}                    |
| Remote        | origin                           |
| Pushed        | false                            |

## Commits
| # | Stage | Type | Message | SHA | Pushed |
|---|---|---|---|---|---|
```

### Step 1.5 — Log
Append to master log:
```
| {TS} | Orchestrator | BRANCH_CREATED | {COMPUTED_NAME} (base={BASE_BRANCH}) | COMPLETE |
```

### Step 1.6 — Return
```
GIT BRANCH READY:
  Branch : {COMPUTED_NAME}
  Base   : {BASE_BRANCH}
  Status : checked out (local only)
```

---

## Operation 2 — `commit_stage`

Triggered when, after a stage's APPROVE checkpoint, the human types `COMMIT`
at the orchestrator's commit checkpoint prompt.

### Step 2.1 — Resolve Files To Stage
| Stage | Source of file list |
|---|---|
| `architecture` | `tickets/{id}/architecture/architecture-decision.md` (artifact only) |
| `implementation` | `tickets/{id}/implementation/files-changed.md` (read "Created" + "Modified") |
| `tests-run-N` | Files listed in `tickets/{id}/test-reports/run-N-report.md` (test files written) |
| `review-run-N` | Artifact only: `review-reports/run-N-review.md` |
| `bugfix-run-N` | `tickets/{id}/bugfix-reports/run-N-bugfix.md` (read "Files modified") |
| `pipeline-complete` | All remaining tracked changes |

`git add` is scoped to that explicit list — never `git add .`.

### Step 2.2 — Build Conventional Commit Message
Default `<type>` per stage:

| Stage | Default `<type>` |
|---|---|
| architecture | `docs` |
| implementation (Story/Task) | `feat` |
| implementation (Bug) | `fix` |
| tests-run-N | `test` |
| review-run-N | `docs` |
| bugfix-run-N | `fix` |
| refactor-only changes | `refactor` |
| CI / pipeline-only changes | `ci` |
| build / deps-only changes | `chore` |
| performance-only changes | `perf` |

Format:
```
<type>: <short summary> -- <TICKET_ID>

[optional body — bullet list of key changes]

[optional footer — e.g. Refs: <TICKET_ID>, Co-authored-by: ...]
```

`<short summary>` rules:
- Imperative mood (`add`, `fix`, `update`, not `added`/`fixed`).
- ≤ 60 chars before the `-- <TICKET_ID>` suffix.
- Lowercase first letter, no trailing period.

### Step 2.3 — Commit Checkpoint
The orchestrator presents:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💾 COMMIT? — {TICKET_ID} — Stage: {STAGE_LABEL}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Branch : {BRANCH_NAME}
Files  :
  + path/to/new/file.java
  ~ path/to/modified/file.java
  - path/to/deleted/file.java

Proposed message:
  {type}: {short} -- {TICKET_ID}

  {optional body}

─────────────────────────────────────────────────────
RESPOND WITH:
  COMMIT             → Commit with proposed message
  EDIT: {full msg}   → Commit with your message instead
  SKIP               → Skip commit for this stage
  GIT STATUS         → Show working tree status first
─────────────────────────────────────────────────────
```

- `COMMIT` → run `git add <files>` then `git commit -m "..."`.
- `EDIT: ...` → use the human-provided message verbatim (still validated against
  the `<type>: <short> -- <TICKET_ID>` shape; reject and re-prompt if malformed).
- `SKIP` → record the skip in `git-context.md` and the master log; do not commit.

### Step 2.4 — Update git-context.md
Append a row to the Commits table with `Pushed = false`.

### Step 2.5 — Log
```
| {TS} | Orchestrator | COMMIT_CREATED | {SHA} {type}: {short} -- {TICKET_ID} | COMPLETE |
| {TS} | Orchestrator | COMMIT_SKIPPED | stage={STAGE_LABEL} | SKIPPED |
```

---

## Operation 3 — `push_branch`

Triggered ONLY when the human types `PUSH` at any point after at least one
commit exists on the branch.

### Step 3.1 — Pre-flight
- Verify branch has commits ahead of `origin/<BASE_BRANCH>`.
- Verify no rebase/merge in progress.
- Refuse to push if branch is `main`, `master`, `develop`, or matches
  `release/*` without an explicit `PUSH FORCE-RELEASE` confirmation.

### Step 3.2 — Push
```
git push -u origin <BRANCH_NAME>
```
Never `--force` or `--force-with-lease` from this skill. If the remote has
diverged, halt and surface to the human.

### Step 3.3 — Update & Log
- Set `Pushed: true` in `git-context.md` header and on each pushed commit row.
- Append:
  ```
  | {TS} | Orchestrator | PUSH_CREATED | {BRANCH_NAME} -> origin | COMPLETE |
  ```

### Step 3.4 — Return
```
PUSH COMPLETE:
  Branch : {BRANCH_NAME}
  Commits pushed : {N}
  Remote : origin
  Next   : open MR/PR manually, or ask the orchestrator for the MR URL helper.
```

---

## Operation 4 — `report_status`

Returns a compact summary of:
- Current branch
- Ahead / behind counts vs base
- Staged / unstaged / untracked file counts
- Last 3 commits on the branch (SHA + message)

Read-only. No side effects.

---

## Error Handling

| Situation | Action |
|---|---|
| Dirty working tree at `setup_branch` | Halt, list files, ask human to clean or stash |
| Branch already exists locally | Reuse (checkout); log `BRANCH_REUSED` |
| Branch already exists on origin only | Check out tracking branch; log `BRANCH_REUSED_REMOTE` |
| Detached HEAD | Halt; require human to checkout a branch first |
| Invalid team prefix entered | Re-prompt up to 3 times, then halt |
| Malformed commit message in `EDIT:` | Re-prompt; do not commit |
| Push rejected (non-fast-forward) | Halt; surface remote SHA and ask for guidance |
| GitLab MCP / git CLI unavailable | Halt; record `git_enabled: false` so subsequent stages skip git ops |
| Human types `COMMIT` with no staged changes | Surface "nothing to commit" and treat as `SKIP` |

The skill never auto-resolves any of these — every recovery path goes through
the human via the orchestrator.

---

## Inherited Behaviours

This skill obeys:
- `log-writer.behaviour.md` — every Git event is logged.
- `human-checkpoint.behaviour.md` — PREFIX, COMMIT, PUSH are all explicit checkpoints.
- `file-lock.behaviour.md` — when committing, acquires a short-lived lock on
  `tickets/{TICKET_ID}/git-context.md` while writing.

---

## Non-Goals

- Does NOT open merge/pull requests (separate future skill).
- Does NOT rebase, squash, cherry-pick, or rewrite history.
- Does NOT force-push under any circumstance.
- Does NOT auto-commit or auto-push without explicit human input.
- Does NOT modify `.gitignore`, `.gitattributes`, or any Git config files
  (those go through `config-permission.behaviour.md`).

