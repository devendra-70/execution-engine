# Behaviour: Human Checkpoint

## Purpose
Ensure a human reviews and explicitly approves the output of every agent stage
before the orchestrator advances the pipeline. No stage transition is automatic.

---

## When This Behaviour Triggers

This behaviour is invoked by the **Orchestrator Agent** after EVERY agent completes its task.
Individual agents do not invoke this behaviour directly — they signal completion and halt.

Checkpoint triggers:
- Architecture Agent completes → Checkpoint A
- Backend Implementation Agent completes → Checkpoint B
- Unit Test Agent completes (with coverage check) → Checkpoint H-{N} (per loop run)
- Bugfix Agent completes → Checkpoint F-{N} (per loop run)
- Code Review Agent completes → Checkpoint D-{N} (per loop run)
- Coverage Verification (post-refactor) completes → Checkpoint I-{N} (per loop run)
- Any agent requests a config change → Checkpoint CONFIG (see config-permission.behaviour.md)

---

## Orchestrator Checkpoint Presentation Format

When presenting a checkpoint to the human, the orchestrator MUST output this structure:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔵 CHECKPOINT {LABEL} — {AGENT NAME} completed
Ticket: {TICKET_ID}  |  Pipeline Run: {N}  |  Time: {TIMESTAMP}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 ARTIFACT: tickets/{TICKET_ID}/{output-path}

SUMMARY:
{3–5 sentence summary of what the agent did, key decisions made, and any risks or flags raised}

⚠️  FLAGS (if any):
{List any issues, config change requests, boundary warnings, or escalations the agent raised}

─────────────────────────────────────────────────────
RESPOND WITH ONE OF:

  APPROVE          → Advance to next stage
  REJECT           → Re-run this agent from scratch (provide feedback below)
  REVISE: {notes}  → Re-run this agent with your specific instructions appended
  DONE             → Exit the loop and close this ticket run (loop stage only)
─────────────────────────────────────────────────────
```

---

## Human Response Handling

### `APPROVE`
- Orchestrator advances to the next pipeline stage
- Logs approval to `.github/logs/master-agent-log.md`:
  ```
  | {TIMESTAMP} | Human | APPROVED Checkpoint {LABEL} | tickets/{TICKET_ID} | APPROVED |
  ```

### `REJECT`
- Orchestrator re-invokes the same agent
- Appends the following to the agent's context before re-run:
  ```
  REJECTION FEEDBACK FROM HUMAN:
  Previous output was rejected. Reason: {human's rejection message}
  Please redo your task addressing the above concern.
  ```
- Logs rejection:
  ```
  | {TIMESTAMP} | Human | REJECTED Checkpoint {LABEL} — re-running {AGENT} | tickets/{TICKET_ID} | REJECTED |
  ```
- Rejection counter increments. After **3 consecutive rejections** of the same agent:
  - Orchestrator surfaces an escalation notice:
    ```
    ⚠️ ESCALATION: {AGENT} has been rejected 3 times consecutively.
    This stage may require manual intervention or architectural changes.
    Options: OVERRIDE (proceed anyway) | ABORT (stop pipeline) | REVISE: {deep notes}
    ```

### `REVISE: {notes}`
- Treated the same as REJECT, but the specific revision instructions are injected as a directive override
- The agent receives:
  ```
  REVISION DIRECTIVE FROM HUMAN:
  {human's notes}
  Apply these instructions in addition to your normal task.
  ```

### `DONE` (loop stage only)
- Valid only at loop-stage checkpoints: H-{N}, F-{N}, D-{N}, or I-{N}
- Orchestrator exits the loop regardless of remaining issues
- Logs:
  ```
  | {TIMESTAMP} | Human | DONE — exiting review loop at run {N} | tickets/{TICKET_ID} | LOOP_EXITED |
  ```

### `/skip-coverage` (Checkpoint H-{N} only)
- Valid only at Unit Test checkpoint (H-{N})
- Overrides coverage threshold enforcement
- Orchestrator proceeds to Bugfix Agent regardless of coverage metrics
- Logs:
  ```
  | {TIMESTAMP} | Human | COVERAGE_THRESHOLD_OVERRIDE | tickets/{TICKET_ID} | /skip-coverage |
  ```

### `RESTART` (Checkpoint I-{N} only)
- Valid only at Coverage Verification checkpoint (I-{N})
- Restarts the loop at Step 3a (Unit Test Agent) for run N+1
- Used when post-refactor coverage has degraded and needs remediation
- Logs:
  ```
  | {TIMESTAMP} | Human | LOOP_RESTART_COVERAGE_DEGRADED | tickets/{TICKET_ID} | RESTART |
  ```

---

## Checkpoint Labels Reference

| Label  | Stage                              | Agent                          |
|--------|------------------------------------|---------------------------------|
| A      | Architecture complete              | Architecture Agent              |
| B      | Implementation complete            | Backend Implementation Agent    |
| H-{N}  | Unit tests + coverage, run N       | Unit Test Agent                 |
| F-{N}  | Bugfix complete, run N             | Bugfix Agent                    |
| D-{N}  | Code review complete, run N        | Code Review Agent               |
| I-{N}  | Post-refactor coverage, run N      | Coverage Verification (Orchestrator) |
| CONFIG | Config change requested            | Any agent                       |

---

## Non-Negotiable Rules

- The orchestrator MUST NOT advance the pipeline without an explicit human response
- The orchestrator MUST NOT interpret silence or inactivity as approval
- A human response of anything other than the valid options (`APPROVE`, `REJECT`, `REVISE:`, `DONE`, `/skip-coverage`, `RESTART`) must be clarified before proceeding
- Checkpoint-specific responses (`/skip-coverage`, `RESTART`) are only valid at their designated checkpoints — reject them elsewhere
- Checkpoint summaries must be honest — if the agent raised warnings or flags, they MUST appear in the ⚠️ FLAGS section, never suppressed