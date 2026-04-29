# Behaviour: File Lock

## Purpose
Prevent concurrent file modifications by multiple agents operating on the same ticket.
Lock files are plain text files stored in `tickets/{TICKET_ID}/.locks/`.

---

## Lock Areas

Each agent owns one or more lock areas. An agent MUST only acquire locks for its own areas.

| Lock File                  | Owner Agent               | Protected Paths                                      |
|----------------------------|---------------------------|----------------------------------------------|
| `{locks.architecture}`     | Architecture Agent        | `{tickets.root}/{id}/{tickets.subfolders.architecture}/**` |
| `{locks.implementation}`   | Backend Implementation Agent | `{tickets.root}/{id}/{tickets.subfolders.implementation}/**` + `{codegen.base}/**` |
| `{locks.tests}`            | Unit Test Agent           | `{tickets.root}/{id}/{tickets.subfolders.test_reports}/**` + `{testgen.base}/**` |
| `{locks.review}`           | Code Review Agent         | `{tickets.root}/{id}/{tickets.subfolders.review_reports}/**` (READ-ONLY agent — lock is advisory) |
| `{locks.bugfix}`           | Bugfix Agent              | `{tickets.root}/{id}/{tickets.subfolders.bugfix_reports}/**` + `{codegen.base}/**` (patching) |
| `{locks.refactor}`         | Code Refactor Agent       | `{codegen.base}/**` (refactoring) + `REFACTOR_REPORT.md` |
| `{locks.orchestrator}`     | Orchestrator Agent        | `{tickets.root}/{id}/*.md` + `{github.logs.root}/**` |

---

## Acquire Lock Protocol

Before writing ANY file, an agent MUST:

1. Check if `{tickets.root}/{TICKET_ID}/{tickets.subfolders.locks}/{locks.<area>}` exists
2. If the lock file EXISTS:
   - Read the lock file to identify the owner agent and timestamp
   - HALT immediately
   - Report to the orchestrator:
     ```
     LOCK CONFLICT: {area}.lock is held by {owner-agent} since {timestamp}.
     Cannot proceed. Waiting for orchestrator to resolve.
     ```
   - Do NOT proceed under any circumstance
3. If the lock file DOES NOT EXIST:
   - Create it immediately with the following content:
     ```
     agent: {AGENT_NAME}
     ticket: {TICKET_ID}
     acquired: {ISO_8601_TIMESTAMP}
     purpose: {brief description of what the agent is about to write}
     ```
   - Proceed with the write operation

---

## Release Lock Protocol

After completing a write operation, the agent MUST:

1. Verify the lock file content matches its own agent name (safety check)
2. Delete the lock file
3. Log the release to `.github/logs/master-agent-log.md`

An agent MUST NEVER delete a lock file it did not create.

---

## Lock Timeout Rule

If a lock file is older than **30 minutes** (compare `acquired` timestamp to current time):
- The lock is considered stale
- The orchestrator (and ONLY the orchestrator) may delete the stale lock
- Individual agents must NOT self-resolve stale locks — they must report and wait

---

## Cross-Area Rule

An agent MUST NOT write to files outside its designated lock areas, even if no lock exists for that area.

| Agent                  | MAY write to                                   | MUST NOT write to                          |
|------------------------|------------------------------------------------|--------------------------------------------|
| Architecture Agent     | `{tickets.root}/{id}/{tickets.subfolders.architecture}/**` | `{codegen.base}/**`, `implementation/**` |
| Backend Agent          | `{tickets.root}/{id}/{tickets.subfolders.implementation}/**`, `{codegen.base}/**` | `{testgen.base}/**` |
| Unit Test Agent        | `{tickets.root}/{id}/{tickets.subfolders.test_reports}/**`, `{testgen.base}/**` | `{codegen.base}/**` |
| Code Review Agent      | `{tickets.root}/{id}/{tickets.subfolders.review_reports}/**` | ALL source files (read-only agent) |
| Bugfix Agent           | `{tickets.root}/{id}/{tickets.subfolders.bugfix_reports}/**`, `{codegen.base}/**` (patch only) | `{testgen.base}/**` |
| Code Refactor Agent    | `{codegen.base}/**` (refactoring), `REFACTOR_REPORT.md` | `{testgen.base}/**` (test generation), `pom.xml` |
| Orchestrator           | `{tickets.root}/{id}/*.md`, `{github.logs.root}/**`, `{tickets.subfolders.locks}/**` | All source files |

---

## Violation Handling

If an agent detects it is about to write outside its designated area:
- STOP immediately
- Do NOT write the file
- Report the violation to the orchestrator with full context:
BOUNDARY VIOLATION ATTEMPT:
Agent: {AGENT_NAME}
Attempted path: {file path}
Reason flagged: Outside designated area
Action taken: Aborted. Awaiting orchestrator instruction.