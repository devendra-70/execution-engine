# Skill: File Lock Manager

## Purpose
Provide the Orchestrator with explicit lock management operations across all
agents and ticket runs. While individual agents self-manage their locks via
`file-lock.behaviour.md`, the Orchestrator uses this skill to monitor,
resolve conflicts, and clean up stale locks.

---

## Operations

### `acquire_lock(ticket_id, area, agent_name, purpose)`

Creates a lock file at `{tickets.root}/{ticket_id}/{tickets.subfolders.locks}/{locks.<area>}`.

**Pre-condition check:**
1. Read `{tickets.root}/{ticket_id}/{tickets.subfolders.locks}/{locks.<area>}`
2. If it exists and is NOT stale (< {locks.stale_threshold_minutes} minutes old):
   - Return: `CONFLICT — held by {owner} since {timestamp}`
   - Do NOT overwrite
3. If it exists and IS stale (≥ {locks.stale_threshold_minutes} minutes old):
   - Return: `STALE_LOCK — held by {owner} since {timestamp}. Use force_release to clear.`
   - Do NOT auto-clear without orchestrator instruction
4. If it does not exist:
   - Write lock file with content:
     ```
     agent: {agent_name}
     ticket: {ticket_id}
     area: {area}
     acquired: {ISO_8601_TIMESTAMP}
     purpose: {purpose}
     ```
   - Return: `ACQUIRED`

---

### `release_lock(ticket_id, area, agent_name)`

Deletes the lock file at `{tickets.root}/{ticket_id}/{tickets.subfolders.locks}/{locks.<area>}`.

**Safety check:**
1. Read the lock file
2. Verify the `agent` field matches `agent_name`
3. If match: delete the file, return `RELEASED`
4. If mismatch: return `DENIED — lock owned by {actual owner}, not {agent_name}`
5. If file doesn't exist: return `NOT_FOUND — no lock to release`

---

### `check_lock(ticket_id, area)`

Reads lock status without modifying anything.

Returns one of:
- `FREE` — no lock file exists
- `LOCKED by {agent_name} since {timestamp} for: {purpose}` — active lock
- `STALE — locked by {agent_name} since {timestamp} (> {locks.stale_threshold_minutes} min ago)` — stale lock

---

### `list_locks(ticket_id)`

Lists all current locks for a ticket.

Returns a table:
Area          ,Owner Agent          ,Acquired            ,Age    ,Status  
implementation,Backend Agent        ,2026-04-23T14:00:00Z,5 min  ,ACTIVE  
tests          ,Unit Test Agent      ,2026-04-23T13:20:00Z,45 min  ,STALE  

---

### `force_release(ticket_id, area)`

Orchestrator-only operation. Forcibly deletes a stale or orphaned lock.

**Conditions for use:**
- Lock is stale (≥ {locks.stale_threshold_minutes} minutes old), OR
- Orchestrator has confirmed the owning agent has terminated or failed

**Process:**
1. Read the lock file and log its contents before deletion:
   ```
   | {TS} | Orchestrator | LOCK_RELEASED (forced) | {tickets.root}/{id}/{tickets.subfolders.locks}/{locks.<area>} | COMPLETE |
   ```
2. Delete the lock file
3. Return: `FORCE_RELEASED — previous owner was {agent_name}`

---

### `release_all_locks(ticket_id)`

Orchestrator-only operation. Releases ALL locks for a ticket.
Used at pipeline completion, pipeline abort, or after a human `ABORT` instruction.

**Process:**
1. List all `.lock` files in `{tickets.root}/{ticket_id}/{tickets.subfolders.locks}/`
2. Log each one before deletion
3. Delete all of them
4. Return: `ALL_LOCKS_RELEASED — {N} locks cleared`

---

## Lock Area Reference

| Area             | Lock File              | Owning Agent                  |
|------------------|------------------------|-------------------------------|
| `architecture`   | `{locks.architecture}` | Architecture Agent            |
| `implementation` | `{locks.implementation}` | Backend Implementation Agent  |
| `tests`          | `{locks.tests}`        | Unit Test Agent               |
| `review`         | `{locks.review}`       | Code Review Agent             |
| `bugfix`         | `{locks.bugfix}`       | Bugfix Agent                  |
| `orchestrator`   | `{locks.orchestrator}` | Orchestrator Agent            |

---

## Lock Conflict Resolution Guide (for Orchestrator)

| Scenario                                                | Recommended Action                                  |
|---------------------------------------------------------|-----------------------------------------------------|
| Agent A trying to write, Agent B holds lock             | Wait for Agent B to complete and release            |
| Lock is stale (agent timed out or crashed)              | `force_release`, then re-invoke the owning agent    |
| Two agents both need `implementation.lock`              | Serialise — complete one agent, then the other      |
| Pipeline aborting mid-run                               | `release_all_locks` before exit                     |
| Lock file is corrupted / unparseable                    | `force_release`, log incident, notify human         |