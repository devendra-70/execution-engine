# Skill: Loop Tracker

## Purpose
Track issues across review loop runs for a given ticket.
Identify issues that persist across 3 or more consecutive runs and surface
them as persistent issues requiring human escalation.
Provide the orchestrator with loop state so it can make informed decisions
about continuing or exiting the loop.

---

## Data Model

The loop tracker maintains state in: `tickets/{TICKET_ID}/loop-state.md`

This file is created on the first loop run and updated after every subsequent run.

### loop-state.md Format

```markdown
# Loop State — {TICKET_ID}

**Last updated:** {ISO_8601_TIMESTAMP}

## Run Counter

| Metric                  | Value     |
|-------------------------|-----------|
| Current Run Number      | {N}       |
| Total Runs Completed    | {N}       |
| Loop Status             | ACTIVE / EXITED |
| Exit Reason             | {reason or N/A} |

## Issue Registry

| Issue Fingerprint | First Seen Run | Last Seen Run | Consecutive Count | Status     |
|-------------------|----------------|---------------|-------------------|------------|
| {fingerprint}     | {run N}        | {run N}       | {count}           | ACTIVE / RESOLVED / ESCALATED |
```

---

## Operations

### `start_run(ticket_id)`

Increments the run counter and marks the loop as active.

**Process:**
1. Read `tickets/{ticket_id}/loop-state.md` (create if not exists with run = 0)
2. Increment `Current Run Number` by 1
3. Set `Loop Status` to `ACTIVE`
4. Write updated state
5. Return: `RUN_STARTED — Run {N} for {ticket_id}`

Log to master-agent-log:
```
| {TS} | Orchestrator | LOOP_STARTED | tickets/{id}/loop-state.md | Run {N} |
```

---

### `get_run_number(ticket_id)`

Returns the current run number without modifying state.

Returns: `{N}` (integer)

---

### `register_issues(ticket_id, run_number, issues)`

Registers the issues found in the current run's review report.

`issues` is a list of issue fingerprints extracted from `review-reports/run-{N}-review.md`.

**Fingerprint extraction rules:**
- Extract all CRITICAL and HIGH severity findings from the review report
- Create a fingerprint for each as: `{category}:{file}:{brief description}`
  - Example: `Security:UserController.java:IDOR missing ownership check`
  - Example: `SOLID:OrderService.java:SRP violation — persistence in business layer`
- Fingerprints should be stable across runs — the same underlying issue should produce the same fingerprint even if the line number changes

**Process for each issue:**
1. Check if fingerprint already exists in the issue registry
2. If NEW: add row with `First Seen Run = {N}`, `Consecutive Count = 1`, `Status = ACTIVE`
3. If EXISTING and last seen in run {N-1} (consecutive): increment `Consecutive Count`, update `Last Seen Run`
4. If EXISTING but last seen was NOT run {N-1} (gap in runs): reset `Consecutive Count = 1`, update `Last Seen Run`
5. After updating: check if any issue has `Consecutive Count >= 3` → trigger `escalate_persistent_issues`

---

### `register_resolved_issues(ticket_id, run_number, resolved_fingerprints)`

Called after a bugfix run to mark issues as resolved.

For each fingerprint in `resolved_fingerprints`:
1. Find matching row in issue registry
2. Set `Status = RESOLVED`, update `Last Seen Run`

---

### `escalate_persistent_issues(ticket_id)`

Called automatically when any issue reaches `Consecutive Count >= 3`.

**Process:**
1. Collect all issues with `Consecutive Count >= 3` and `Status = ACTIVE`
2. Write each to `.github/logs/persistent-issues-log.md`:
   ```
   | {ticket_id} | {fingerprint} | Run {first_seen} | {count} | {ISO_8601_TIMESTAMP} | ACTIVE |
   ```
3. Update their `Status` in loop-state.md to `ESCALATED`
4. Surface escalation to orchestrator:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ⚠️  PERSISTENT ISSUES DETECTED — {TICKET_ID}
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   The following issues have appeared in {N} consecutive review runs
   and have NOT been resolved by the Bugfix Agent:

   {for each escalated issue:}
   • {fingerprint}
     First seen: Run {N} | Consecutive runs: {count}

   These issues may require architectural changes, manual developer
   intervention, or a decision to accept the risk.

   RESPOND WITH:
     CONTINUE    → Keep looping (not recommended for escalated issues)
     ESCALATE    → Flag to development team and exit loop
     OVERRIDE    → Mark as accepted risk and exit loop
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```

---

### `exit_loop(ticket_id, reason)`

Marks the loop as exited in state.

Valid reasons: `HUMAN_DONE`, `ZERO_ISSUES`, `MAX_RUNS`, `ESCALATION`, `ABORT`

**Process:**
1. Set `Loop Status = EXITED`, `Exit Reason = {reason}`
2. Write state
3. Log:
   ```
   | {TS} | Orchestrator | LOOP_EXITED | tickets/{id}/loop-state.md | {reason} |
   ```

---

### `get_loop_summary(ticket_id)`

Returns a summary for the orchestrator's checkpoint presentation:

```
LOOP SUMMARY — {TICKET_ID}
  Runs completed : {N}
  Open issues    : {count of ACTIVE issues}
  Resolved       : {count of RESOLVED issues}
  Escalated      : {count of ESCALATED issues}
  Recommended    : {CONTINUE / EXIT based on issue counts and run number}
```

**Exit recommendation logic:**
- `CONTINUE` if: open CRITICAL or HIGH issues exist AND run count < max (default 10) AND no escalated issues
- `EXIT — ZERO_ISSUES` if: no ACTIVE issues remain
- `EXIT — MAX_RUNS` if: run count >= max runs
- `EXIT — ESCALATION` if: any ESCALATED issues exist (human decision required)