# Behaviour: Log Writer

## Purpose
Every agent MUST record its actions in the master agent log.
Logging is not optional — it is a required step at agent start and agent completion.
This creates an immutable audit trail of who did what and when across the full pipeline.

---

## Log File Locations

| Log File                                      | Updated By          | Contains                                      |
|-----------------------------------------------|---------------------|-----------------------------------------------|
| `{github.logs.master_agent_log}`            | ALL agents          | Every agent start, completion, and key action |
| `{github.logs.config_change_log}`            | Any agent that touches config + Orchestrator | Config change requests and outcomes |
| `{github.logs.persistent_issues_log}`        | Orchestrator + Loop Tracker skill | Issues seen across 3+ loop runs |

---

## Master Agent Log — Entry Format

Every agent appends to `.github/logs/master-agent-log.md`.
Use the **exact pipe-delimited format** below. Never deviate.

| {ISO_8601_TIMESTAMP} | {AGENT_NAME} | {ACTION} | {ARTIFACT_PATH} | {STATUS} |


### Field Definitions

| Field              | Value Rules                                                               |
|--------------------|---------------------------------------------------------------------------|
| ISO_8601_TIMESTAMP | UTC time: `2026-04-23T14:32:01Z`                                          |
| AGENT_NAME         | Exact agent name from the agent's `name:` frontmatter                    |
| ACTION             | One of the defined action codes below                                     |
| ARTIFACT_PATH      | Relative path to the file created/read/modified, or `N/A`               |
| STATUS             | `STARTED`, `COMPLETE`, `FAILED`, `BLOCKED`, `AWAITING_HUMAN`, `SKIPPED` |

### Action Codes (use exactly these)

| Code                   | When to use                                                    |
|------------------------|----------------------------------------------------------------|
| `CONTEXT_READ`         | Agent finished reading all context sources                     |
| `LOCK_ACQUIRED`        | Agent acquired a file lock                                     |
| `LOCK_RELEASED`        | Agent released a file lock                                     |
| `LOCK_CONFLICT`        | Agent found an existing lock and halted                        |
| `ARTIFACT_CREATED`     | Agent created a new file                                       |
| `ARTIFACT_UPDATED`     | Agent modified an existing file                                |
| `CONFIG_CHANGE_REQUESTED` | Agent surfaced a config change for human approval           |
| `CONFIG_CHANGE_ALLOWED`   | Human approved a config change                              |
| `CONFIG_CHANGE_DENIED`    | Human denied a config change                                |
| `CHECKPOINT_REACHED`   | Orchestrator presented a human checkpoint                      |
| `HUMAN_APPROVED`       | Human responded APPROVE at a checkpoint                        |
| `HUMAN_REJECTED`       | Human responded REJECT at a checkpoint                         |
| `HUMAN_REVISED`        | Human responded REVISE at a checkpoint                         |
| `LOOP_STARTED`         | Orchestrator started a review loop run                         |
| `LOOP_EXITED`          | Human or orchestrator exited the review loop                   |
| `PIPELINE_STARTED`     | Orchestrator began processing a ticket                         |
| `PIPELINE_COMPLETE`    | Orchestrator completed all stages for a ticket                 |
| `AGENT_FAILED`         | An agent halted due to error or unresolvable conflict          |
| `BOUNDARY_VIOLATION`   | Agent attempted to write outside its designated area           |

---

## Mandatory Log Events Per Agent

### All Agents — Required Log Entries

1. **On invocation start:**
   ```
   | {TS} | {AGENT} | CONTEXT_READ | tickets/{id}/ticket-context.md | COMPLETE |
   ```

2. **On lock acquisition (if applicable):**
   ```
   | {TS} | {AGENT} | LOCK_ACQUIRED | tickets/{id}/.locks/{area}.lock | COMPLETE |
   ```

3. **On each file created or updated:**
   ```
   | {TS} | {AGENT} | ARTIFACT_CREATED | tickets/{id}/{path/to/file.md} | COMPLETE |
   ```

4. **On lock release:**
   ```
   | {TS} | {AGENT} | LOCK_RELEASED | tickets/{id}/.locks/{area}.lock | COMPLETE |
   ```

5. **On task completion:**
   ```
   | {TS} | {AGENT} | {PRIMARY_ACTION_CODE} | {primary artifact path} | COMPLETE |
   ```

6. **On failure or block:**
   ```
   | {TS} | {AGENT} | AGENT_FAILED | {context} | FAILED |
   ```

---

## Configuration Change Log — Entry Format

Append to `.github/logs/configuration-change-log.md`:

| {ISO_8601_TIMESTAMP} | {AGENT_NAME} | {FILE_PATH} | {CHANGE_SUMMARY} | {HUMAN_DECISION} |


| Field           | Value                                      |
|-----------------|--------------------------------------------|
| CHANGE_SUMMARY  | One sentence: what changed and why         |
| HUMAN_DECISION  | `ALLOWED`, `DENIED`, or `PENDING`          |

Log the entry at the time of the request (status = `PENDING`), then update to `ALLOWED` or `DENIED` after the human responds. If the log system does not support in-place updates, append a second row with the final decision.

---

## Persistent Issues Log — Entry Format

Append to `.github/logs/persistent-issues-log.md` when the Loop Tracker skill
identifies an issue appearing in 3 or more consecutive runs:

| {TICKET_ID} | {ISSUE_FINGERPRINT} | {FIRST_SEEN_RUN} | {RUN_COUNT} | {LAST_SEEN_TIMESTAMP} | {STATUS} |


| Field              | Value                                                       |
|--------------------|-------------------------------------------------------------|
| ISSUE_FINGERPRINT  | Short description identifying the issue uniquely            |
| STATUS             | `ACTIVE`, `RESOLVED`, `ESCALATED`                           |

---

## File Initialisation Rule

If any log file does not exist when an agent first tries to append to it,
the agent MUST create it with the appropriate header before appending:

**{github.logs.master_agent_log} header:**
```markdown
# Master Agent Log

> All agent actions across all ticket runs are recorded here.
> Format: Timestamp | Agent | Action | Artifact | Status

| Timestamp | Agent | Action | Artifact | Status |
|-----------|-------|--------|----------|--------|
```

**{github.logs.config_change_log} header:**
```markdown
# Configuration Change Log

> All configuration file change requests and outcomes are recorded here.

| Timestamp | Agent | Config File | Change Summary | Human Decision |
|-----------|-------|-------------|----------------|----------------|
```

**{github.logs.persistent_issues_log} header:**
```markdown
# Persistent Issues Log

> Issues that have appeared in 3 or more consecutive loop runs are recorded here.

| Ticket ID | Issue | First Seen Run | Run Count | Last Seen | Status |
|-----------|-------|---------------|-----------|-----------|--------|
```

---

## Non-Negotiable Rules

- Logging is NEVER optional — a task is not complete if it has no log entry
- Agents MUST NOT modify or delete prior log entries
- Agents MUST NOT reformat or reorder existing log rows
- All timestamps MUST be UTC

- Log files are owned by the Orchestrator; individual agents append only