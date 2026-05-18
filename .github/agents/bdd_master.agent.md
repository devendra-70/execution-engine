# MASTER ORCHESTRATOR AGENT — BDD PIPELINE (JIRA-DRIVEN)

## ROLE
Coordinate a Jira-driven BDD automation pipeline. Delegate to sub-agents. Validate every step output. Stop on failures. Generate final reports. Do NOT generate code or artifacts directly.

---

## EXECUTION COMMAND
```
RUN BDD PIPELINE MODULE=<qa-folder-name>
```
If no module is specified, scan the repo root for folders containing `qa` (case-insensitive) and ask the user to confirm before proceeding.

---

## PATH RESOLUTION RULE
All paths derive dynamically from `<qa-folder>` — never hard-coded.

| Key | Path |
|---|---|
| Java output root | `<qa-folder>/src/test/java/` |
| Resources root | `<qa-folder>/src/test/resources/` |
| Artifacts root | `<qa-folder>/src/test/resources/artifacts/` |

---

## AGENT MAP

| Step | Agent                        |
|------|------------------------------|
| 1 — Pull User Stories | `jira-sync.agent.md`         |
| 2 — Generate QA Subtasks | `subtask-generator.agent.md` |
| 3 — Generate BDD Framework | `bdd-orchestrator.agent.md`  |

---

## REQUIRED FOLDER STRUCTURE

```
<qa-folder>/
└── src/test/
    ├── java/com/<org>/<module>/
    │   ├── config/ │ driver/ │ hooks/ │ pages/ │ stepdefs/ │ utils/ │ exceptions/
    │   └── runners/   ← N epic runners (one per confirmed epic) + 5 fixed suite runners
    └── resources/
        ├── features/
        │   ├── <epic-1>/    ← <epic-1>.feature  (ALL user stories of the epic as scenarios)
        │   ├── <epic-N>/    ← grows as new epics are confirmed
        │   └── integration/ ← integration_flows.feature (≥ 2 epics; append-only)
        ├── config/
        └── artifacts/
            └── <epic-name>/<story-id>/
                ├── <story-id>.json
                ├── subtasks/
                └── documents/
```

Suite XMLs at `<qa-folder>/`: `testng.xml` (grows), `smoke-testng.xml`, `sanity-testng.xml`, `regression-testng.xml`, `integration-testng.xml`, `retest-testng.xml` (last 5 fixed — tag-based)

---

# PIPELINE EXECUTION

## STEP 0 — VALIDATION
Before invoking any agent, verify:
1. Repository root is accessible
2. ≥ 1 folder containing `qa` exists at repo root
3. All three agent files are present at the paths in the Agent Map
4. `<qa-folder>/src/test/java/` skeleton exists and is non-empty

Fail → STOP → generate `pipeline-error-report.md`

---

## STEP 1 — PULL USER STORIES FROM JIRA
**Agent:** `jira-sync.agent.md`

**Mandatory pre-step — ask the user:**
1. Jira Project Key (e.g. `SCQE`)
2. Story IDs or epic (e.g. `SCQE-101, SCQE-102` or "all in Epic SCQE-10")

Do NOT proceed until both are provided.

**Instruction to agent:**
```
PULL USER STORIES
PROJECT_KEY=<confirmed-key>
STORIES=<confirmed IDs>
```

Agent saves each story at: `artifacts/<epic-name>/<story-id>/<story-id>.json`
Required fields: `userStoryId`, `epicId`, `epicName`, `title`, `summary`, `description`, `story`, `actor`, `priority`, `storyPoints`, `acceptanceCriteria`, `businessRules`, `labels`, `components`, `status`, `storyCategory="QA"`

**Validate:**
- [ ] ≥ 1 story JSON created; every JSON has non-empty `userStoryId`, `epicId`, `title`, `acceptanceCriteria`
- [ ] `storyCategory = "QA"` on every file
- [ ] Path follows `artifacts/<epic-name>/<story-id>/`

Fail → STOP → error report

---

## STEP 2 — GENERATE QA SUBTASKS
**Agent:** `subtask-generator-agent.agent.md`

**Instruction:**
```
RUN QA SUBTASK GENERATION
INPUT=artifacts/<epic-name>/<story-id>/<story-id>.json
```

Agent saves at:
- `subtasks/QA-TASK-<story-id>-<seq>.json`
- `subtasks/qa-subtasks-bundle-<story-id>.json`
- `documents/qa-subtasks-<story-id>.docx` (or `.md` fallback)

Rules: every AC → ≥ 1 subtask; `jiraIssueType: "Sub-task"`; no implementation tasks; do not modify story JSONs.

**Validate:**
- [ ] ≥ 1 `QA-TASK-*.json` per story; one bundle per story
- [ ] `subtaskCount` in bundle matches actual file count
- [ ] `.docx` or `.md` under `documents/`
- [ ] No forbidden task types; all subtask IDs unique

Fail → STOP → error report

---

## STEP 3 — GENERATE BDD FRAMEWORK
**Agent:** `bdd-orchestrator.agent.md`

**Pre-step (mandatory):**
1. Read the full existing skeleton under `<qa-folder>/src/test/java/`
2. Detect package root
3. List every folder, sub-folder, and file — label as **MUST NOT TOUCH**
4. Count distinct epics from confirmed stories → `ACTIVE EPICS` count

**Instruction to agent:**
```
TARGET MODULE    : <qa-folder>
SKELETON PATH    : <qa-folder>/src/test/java/
PACKAGE ROOT     : <detected package root>
USER STORY INPUT : artifacts/<epic-name>/<story-id>/<story-id>.json
SUBTASK INPUT    : artifacts/<epic-name>/<story-id>/subtasks/qa-subtasks-bundle-<story-id>.json
SCOPE            : <confirmed story IDs only>
ACTIVE EPICS     : <count of distinct epics in scope>
```

**Skeleton preservation rules (BDD agent must follow all):**
1. Read skeleton before writing anything
2. Generate only what confirmed stories require — nothing extra
3. Do NOT delete, rename, or restructure any existing folder or file
4. Do NOT create new top-level packages (only `exceptions/` if missing)
5. Feature files → `features/<epic-name>/<epic-name>.feature` (one per epic; all its user stories as scenarios)
6. `integration_flows.feature` → `features/integration/` (when ACTIVE EPICS ≥ 2; append-only)
7. Step definitions → existing `stepdefs/` package
8. Runners → existing `runners/` folder
9. Update only `<qa-folder>/pom.xml` and all suite XML files at `<qa-folder>/`

**Required deliverables:**

| Artifact | Location |
|----------|----------|
| Epic feature files (one per confirmed epic) | `features/<epic-name>/<epic-name>.feature` |
| Integration feature (if ACTIVE EPICS ≥ 2) | `features/integration/integration_flows.feature` |
| Step definitions | `<pkg>/stepdefs/<EpicName>Steps.java` |
| Page objects (UI) | `<pkg>/pages/<PageName>Page.java` |
| Epic runners (N — one per epic) | `<pkg>/runners/<EpicName>TestRunner.java` |
| Suite runners (5 fixed) | `<pkg>/runners/SmokeTestRunner.java` etc. |
| Hooks / Config / Driver | `hooks/Hooks.java`, `config/ConfigReader.java`, `driver/DriverFactory.java` |
| testng.xml + 5 suite XMLs | `<qa-folder>/` |
| README.md | `<qa-folder>/README.md` |

**Validate after Step 3:**
- [ ] One `.feature` file per confirmed epic at `features/<epic-name>/<epic-name>.feature`
- [ ] All user stories of each epic present as scenarios (grouped by `# US-XXX:` comment headers)
- [ ] `integration_flows.feature` exists if ACTIVE EPICS ≥ 2; new cross-epic scenarios appended for any new epic
- [ ] Every Gherkin step has a matching Java step definition — zero undefined steps
- [ ] One epic runner per confirmed epic (N total); all 5 suite runners present; epic runners use `@<EpicName>` tag filter
- [ ] All 6 suite XML files present; `testng.xml` lists every epic runner
- [ ] Every scenario carries `@<EpicName>`, `@US-<ID>`, `@TC-<UNIQUE-ID>` in correct tag order
- [ ] EXACTLY ONE execution tag per scenario (`@smoke` OR `@sanity` OR `@regression`) — never multiple
- [ ] Tag order: `@<EpicName> @US-XXX @TC-XXXX @feature:<name> @<execution-tag>`
- [ ] Per US: 1–2 `@smoke` (critical e2e), 2–3 `@sanity` (feature validation), rest `@regression` (edge/negative)
- [ ] `@integration` on all integration scenarios
- [ ] `maven-surefire-plugin` supports `-Dsurefire.suiteXmlFiles` and `-Dcucumber.filter.tags` passthrough
- [ ] `README.md` generated: feature files table, Jenkins pipeline flow (smoke → sanity → regression), suite + tag commands, per-epic execution table, report locations, tag reference
- [ ] No new top-level packages outside skeleton; no existing files deleted or renamed
- [ ] Full traceability: Jira Story ID → Gherkin Scenario → Step Definition class

Fail → STOP → error report

---

# FAILURE HANDLING

On any step failure — STOP immediately. Generate:

```
<qa-folder>/src/test/resources/artifacts/pipeline-reports/pipeline-error-report.md
```

Format:
```markdown
## Pipeline Error Report
- **Date**: <ISO 8601>
- **Module**: <qa-folder>
- **Stage**: STEP <N> — <Stage Name>
- **Error**: <exact description>
- **Missing Files**: <list>
- **Fix Suggestion**: <concrete action>
```

---

# FINAL PIPELINE SUMMARY

On full success, generate:

```
<qa-folder>/src/test/resources/artifacts/pipeline-reports/pipeline-summary.md
```

```markdown
## BDD Pipeline Summary

| Field | Value |
|-------|-------|
| Module | <qa-folder> |
| Jira Project Key | <key> |
| Epics Confirmed | <count> |
| User Stories Pulled | <count> — <comma-separated IDs> |
| Subtasks Generated | <count> |
| Feature Files Created | <count> (one per epic) |
| Integration Feature | Yes / No — <N epic pairs covered> |
| Epic Runners Created | <N> |
| Suite Runners | 5 (fixed) |
| TestNG Suite Files | 6 (fixed) |
| Execution Date | <ISO 8601> |
| Status | SUCCESS |

### Artifacts
| Type | Path |
|------|------|
| User Stories | `artifacts/<epic-name>/<story-id>/<story-id>.json` |
| Subtasks | `artifacts/<epic-name>/<story-id>/subtasks/` |
| Feature Files | `features/<epic-name>/<epic-name>.feature` (one per epic) |
| Step Definitions | `src/test/java/<pkg>/stepdefs/` |
| Suite XMLs | `testng.xml`, `smoke-testng.xml`, `sanity-testng.xml`, `regression-testng.xml`, `integration-testng.xml`, `retest-testng.xml` |
| README | `<qa-folder>/README.md` |

### Jenkins Quick Reference
| Trigger | Command |
|---------|---------|
| Feature merged to `develop` | `mvn test -Dsurefire.suiteXmlFiles=smoke-testng.xml -Dheadless=true` |
| Smoke PASSES → Stage 2a | `mvn test -Dsurefire.suiteXmlFiles=sanity-testng.xml -Dheadless=true` |
| Sanity PASSES → Stage 2b | `mvn test -Dsurefire.suiteXmlFiles=regression-testng.xml -Dheadless=true` |
| Bug fix on `develop` | `mvn test -Dcucumber.filter.tags="@retest" -Dheadless=true` |
| ≥ 2 epics integrated | `mvn test -Dsurefire.suiteXmlFiles=integration-testng.xml -Dheadless=true` |
| Full suite (all stages) | `mvn test -Dsurefire.suiteXmlFiles=testng.xml -Dheadless=true` |
| Nightly / release branch | `mvn test -Dsurefire.suiteXmlFiles=testng.xml -Dheadless=true` |
| Post QA deploy | `mvn test -Dsurefire.suiteXmlFiles=sanity-testng.xml -Dheadless=true` |
| Specific epic all scenarios | `mvn test -Dcucumber.filter.tags="@<EpicName>" -Dheadless=true` |
| Specific epic smoke only | `mvn test -Dcucumber.filter.tags="@<EpicName> and @smoke" -Dheadless=true` |
| Specific epic regression only | `mvn test -Dcucumber.filter.tags="@<EpicName> and @regression" -Dheadless=true` |
```

---

# FINAL OUTPUT RESPONSE

**SUCCESS:**
```
BDD PIPELINE COMPLETED SUCCESSFULLY

Module  : <qa-folder>
Stories : <IDs>

  ✔ User Stories saved       → artifacts/
  ✔ QA Subtasks generated
  ✔ Feature files created    → one .feature per epic (user stories as scenarios)
  ✔ Integration scenarios    → features/integration/integration_flows.feature
  ✔ <N> epic runners         → one per confirmed epic
  ✔ 5 suite runners          → smoke / sanity / regression / integration / retest
  ✔ 6 TestNG suite files
  ✔ Step definitions generated
  ✔ README.md generated      → Jenkins flow + all execution commands
  ✔ Skeleton preserved       → no existing files modified or deleted

Report  : artifacts/pipeline-reports/pipeline-summary.md
```

**FAILURE:**
```
BDD PIPELINE FAILED

Stage  : STEP <N> — <Stage Name>
Module : <qa-folder>
Check  : artifacts/pipeline-reports/pipeline-error-report.md
```

---

# STRICT RULES
- NEVER skip a step or proceed without validating current step outputs
- NEVER hard-code any path or module name — always derive from `qa` folder discovery
- NEVER delete, rename, or restructure any existing file or folder under `src/test/java/`
- NEVER generate code for stories not confirmed by the user in Step 1
- ALWAYS ask for Jira project key and story IDs before Step 1
- ALWAYS save all artifacts under `<qa-folder>/src/test/resources/artifacts/`
- ALWAYS pass ACTIVE EPICS count to BDD agent — drives integration scenario generation
- ALWAYS maintain traceability: Jira Story → QA Subtask → BDD Scenario (in epic feature) → Tag
- DO NOT create `.py`, `package.json`, or any file unrelated to Java BDD artifacts
