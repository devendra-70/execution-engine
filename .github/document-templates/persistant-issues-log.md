# Persistent Issues Log Template

> **Location of live log:** `.github/logs/persistent-issues-log.md`
> **This file:** `.github/document-templates/persistent-issues-log.md`
>
> This is the canonical format template. The live log in `.github/logs/` is
> initialised from this template on first use. Do NOT write pipeline entries here.

---

## Purpose

The Persistent Issues Log records code review findings that have appeared in
**3 or more consecutive review loop runs** without being resolved by the
Bugfix Agent. These issues require human escalation — the automated loop
has failed to resolve them and manual developer intervention is likely needed.

This log:
- Surfaces systemic or deep architectural problems that automated fixing cannot address
- Provides a cross-ticket view of recurring issue patterns
- Serves as an input for technical debt discussions and backlog grooming

---

## Column Definitions

| Column              | Description                                                                          | Example |
|---------------------|--------------------------------------------------------------------------------------|---------|
| Ticket ID           | The Jira ticket where this persistent issue was detected                             | `EPMICMPCOD-137` |
| Issue Fingerprint   | Stable identifier: `{category}:{file}:{brief description}`                           | `Security:UserController.java:IDOR missing ownership check` |
| Severity            | From the code review report: `CRITICAL`, `HIGH`, or `MEDIUM`                        | `CRITICAL` |
| First Seen Run      | The review loop run number where this issue first appeared                           | `1` |
| Consecutive Runs    | How many consecutive runs this issue has appeared without resolution                 | `3` |
| Last Seen           | ISO 8601 UTC timestamp of the last run where this issue was seen                     | `2026-04-23T15:00:00Z` |
| Status              | Current resolution status                                                            | `ESCALATED` |
| Resolution Note     | How the issue was ultimately resolved, or why it was accepted                        | `Fixed in run 4 after manual dev review` |

### Valid Status Values

| Status       | Meaning                                                                          |
|--------------|----------------------------------------------------------------------------------|
| `ACTIVE`     | Issue is still appearing in the review loop                                      |
| `ESCALATED`  | Human has been notified; awaiting manual developer action                        |
| `RESOLVED`   | Issue no longer appears in review output; Bugfix Agent or developer fixed it     |
| `ACCEPTED`   | Human decided to accept this as a known risk and closed the loop                 |
| `DEFERRED`   | Issue is acknowledged but intentionally not fixed in this ticket; backlog item created |

---

## Live Log Initialisation Block

When `.github/logs/persistent-issues-log.md` is first created, initialise
it with exactly this content:

```markdown
# Persistent Issues Log

> Issues that have appeared in 3 or more consecutive review loop runs without resolution.
> Populated automatically by the Loop Tracker skill.
> Format defined in `.github/document-templates/persistent-issues-log.md`.
> DO NOT edit existing rows — append resolution notes in the Resolution Note column only.

| Ticket ID | Issue Fingerprint | Severity | First Seen Run | Consecutive Runs | Last Seen | Status | Resolution Note |
|-----------|-------------------|----------|----------------|------------------|-----------|--------|-----------------|
```

---

## Escalation Threshold

The Loop Tracker skill writes to this log when:

```
Consecutive Runs >= 3
AND Status == ACTIVE
AND the issue appeared in the IMMEDIATELY PRECEDING runs (no gaps)
```

A gap resets the consecutive counter. For example:
- Run 1: Issue present → count = 1
- Run 2: Issue present → count = 2
- Run 3: Issue resolved → count resets
- Run 4: Issue reappears → count = 1 (NOT escalated yet)
- Run 5: Issue present → count = 2
- Run 6: Issue present → count = 3 → ESCALATED and written to this log

---

## Human Response to Escalation

When an issue is escalated, the orchestrator presents it at the next checkpoint.
The human can respond with:

| Response       | Effect on log                                                       |
|----------------|---------------------------------------------------------------------|
| `CONTINUE`     | Status stays `ESCALATED`, loop continues (not recommended)          |
| `ESCALATE`     | Status → `ESCALATED`, orchestrator flags to dev team, loop exits    |
| `OVERRIDE`     | Status → `ACCEPTED`, Resolution Note = "Human accepted as known risk", loop exits |
| `DEFER`        | Status → `DEFERRED`, Resolution Note = "Deferred to backlog — {note}", loop exits |

---

## Cross-Ticket Pattern Analysis

Issues logged here from multiple tickets with the same fingerprint pattern
(same category + same file or class type) indicate a systemic problem.

Indicators to watch for:
- Same `Security:*:IDOR*` fingerprint across 3+ tickets → auth layer is structurally broken
- Same `SOLID:*:SRP violation*` across many classes → architectural guidance needs updating
- Same `pom.xml` config change denied repeatedly → engineering standards may need revision

These patterns should be surfaced in sprint retrospectives using this log as evidence.

---

## Example Entries

```
| EPMICMPCOD-137 | Security:UserController.java:IDOR missing ownership check | CRITICAL | 1 | 3 | 2026-04-23T15:00:00Z | ESCALATED | Flagged to dev team — requires AuthService refactor |
| EPMICMPCOD-137 | SOLID:OrderService.java:SRP violation persistence in business layer | HIGH | 2 | 3 | 2026-04-23T15:00:00Z | DEFERRED | Deferred to backlog EPMICMPCOD-201 — architectural change required |
| EPMICMPCOD-142 | Security:PaymentController.java:Hardcoded API key in source | CRITICAL | 1 | 4 | 2026-04-24T09:00:00Z | RESOLVED | Fixed in run 5 after developer manually refactored to use env config |
```