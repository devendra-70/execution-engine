# Behaviour: Config Permission

## Purpose
No agent may modify a configuration file without first presenting a diff to the human
and receiving explicit `ALLOW`. This behaviour applies to ALL agents without exception.

---

## What Counts as a Configuration File

Any file matching one or more of the following patterns is a protected config file:

```
pom.xml             (Maven build descriptor)
**/pom.xml          (nested module pom files)
checkstyle.xml      (style rules)
logback*.xml        (logging config)
web.xml, persistence.xml, beans.xml
*.yml
*.yaml
*.env
*.env.*             (.env.local, .env.production, etc.)
*.toml
*.properties
*.config.js
*.config.ts
*.config.json
application*.*      (Spring Boot config files)
docker-compose*
Dockerfile
*.gradle
*.gradle.kts
.gitignore
.gitlab-ci.yml
.github/workflows/**
```

When in doubt, treat the file as protected and request permission.

**Explicitly NOT protected** (read/write freely):
- `target/**` (build output — ephemeral, never committed)
- `src/test/java/**` (test source code)
- `src/test/resources/application.properties` (test config — append-only by Unit Test Agent)

---

## Permission Request Protocol

When an agent determines it needs to modify a config file, it MUST:

### Step 1 — STOP
Do not make any changes yet.

### Step 2 — Generate Diff Preview
Produce a diff in unified format showing exactly what would change:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚙️  CONFIG CHANGE REQUEST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Requesting Agent : {AGENT_NAME}
Ticket           : {TICKET_ID}
File             : {exact file path}
Reason           : {why this change is needed — one sentence}

DIFF PREVIEW:
--- {file path} (current)
+++ {file path} (proposed)
@@ line range @@
 (unchanged context line)
-{removed line}
+{added line}
 (unchanged context line)

─────────────────────────────────────────────────────
RESPOND WITH:
  ALLOW   → Apply the change and continue
  DENY    → Do not apply the change (agent will work around it or escalate)
─────────────────────────────────────────────────────
```

### Step 3 — Wait for Human Response
Do not proceed, time out, or assume a default. Wait for explicit `ALLOW` or `DENY`.

### Step 4 — Act on Response

**If `ALLOW`:**
- Apply the change as shown in the diff (no additional modifications)
- Log to `.github/logs/configuration-change-log.md`:
  ```
  | {TIMESTAMP} | {AGENT_NAME} | {file path} | {change summary} | ALLOWED |
  ```
- Log to `.github/logs/master-agent-log.md`:
  ```
  | {TIMESTAMP} | {AGENT_NAME} | Config change applied: {file} | {TICKET_ID} | ALLOWED |
  ```
- Continue with the original task

**If `DENY`:**
- Do NOT apply the change
- Log to `.github/logs/configuration-change-log.md`:
  ```
  | {TIMESTAMP} | {AGENT_NAME} | {file path} | {change summary} | DENIED |
  ```
- The agent must then decide:
  - Can the task be completed without the config change? → Continue without it and note the limitation in the output report
  - Can the task NOT be completed without it? → Halt and report to orchestrator:
    ```
    BLOCKED: Task cannot complete without denied config change to {file}.
    Awaiting orchestrator instruction.
    ```

---

## Multiple Config Changes in One Task

If an agent needs to modify more than one config file in a single task run:
- Each file requires a separate `ALLOW` / `DENY` decision
- Do NOT batch multiple config file changes into a single permission request
- Process them one at a time in the order they are needed

---

## `.env` File Special Rule

Files matching `*.env` or `.env.*` have an additional restriction:

- The agent MUST NOT write actual secret values (passwords, API keys, tokens) even after receiving `ALLOW`
- Instead, write placeholder values:
  ```
  DB_PASSWORD=REPLACE_WITH_ACTUAL_VALUE
  API_KEY=REPLACE_WITH_ACTUAL_VALUE
  ```
- Note this substitution explicitly in the config change log

---

## Non-Negotiable Rules

- An agent MUST NOT modify a config file and ask for permission afterward
- An agent MUST NOT modify a config file because it inferred permission from context
- An agent MUST NOT modify a config file because a previous agent was allowed to change a different config file
- Each config change request is independent and requires its own `ALLOW`