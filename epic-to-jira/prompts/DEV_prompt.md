# DEV Backlog Generation Prompt

## How to Use This Prompt

1. Attach this file using `#file: DEV_prompt.md`
2. Attach your requirements file using `#file: <your-SRS-file>` (e.g. `#file: SRS.txt`)
3. Send the following command:

```
RUN BACKLOG CATEGORY=DEV PROJECT=<project-name>
```

**Example:**
```
#file: DEV_prompt.md
#file: SRS.txt

RUN BACKLOG CATEGORY=DEV PROJECT=CodEval
```

> The agent will automatically use the attached requirements file as the INPUT source.
> You do NOT need to specify an INPUT path manually.

---

## Active Instructions

You are running as the **orchestrator-agent** defined in:
`agents/orchestrator-agent/orchestrator-agent.md`

Your downstream agents are:
- `agents/requirements-to-backlog-agent/requirements-to-backlog-agent.md`
- `agents/backlog-materializer-agent/backlog-materializer-agent.md`

---

## Input Resolution Rule

The requirements document is provided as an **attached file** in this conversation via `#file:`.

- Do NOT look for a hardcoded file path
- Use the content of the attached requirements file directly as the INPUT source
- The `inputFilePath` field in JSON output must reflect the attached file's name (e.g. `projects/<project-name>/requirements/SRS.txt`)

---

## Runtime Directive

**CATEGORY_FILTER = DEV**

Apply the following rules for this run:

- Generate **only DEV user stories** (`storyCategory = "DEV"`)
- Set `actor = "System"` for all stories
- Treat this as the only supported generation mode in this repository
- Focus on: system behavior, backend processing, orchestration, validations, integration logic, pipeline behavior
- All Acceptance Criteria and Business Rules must be relevant to DEV (system-level) behavior only

---

## Orchestration Steps

Execute the full pipeline in this order:

### Step 1 — Validate Command
Confirm that `CATEGORY` and `PROJECT` are present.
Confirm the requirements file is attached in the conversation.

### Step 2 — Invoke Requirements Agent
Read the requirements content from the attached file.
Apply `CATEGORY_FILTER = DEV`.
Generate the Jira-ready JSON bundle containing only DEV stories.

Save bundle to:
`projects/<project-name>/artifacts/DEV/epics/epics-bundle-dev.json`

### Step 3 — Invoke Backlog Materializer Agent
Read the generated JSON bundle.
Materialize:
- Individual Epic JSON files → `projects/<project-name>/artifacts/DEV/epics/<epic-name>/<epic-id>.json`
- Individual User Story JSON files → `projects/<project-name>/artifacts/DEV/epics/<epic-name>/user-stories/<story-id>.json`
- Backlog review document → `projects/<project-name>/artifacts/DEV/documents/backlog-review-dev.md`

### Step 4 — Return Final Summary
Return a structured JSON summary:

```json
{
  "projectName": "<project-name>",
  "category": "DEV",
  "inputFile": "<attached-filename>",
  "bundlePath": "projects/<project-name>/artifacts/DEV/epics/epics-bundle-dev.json",
  "epicCount": 0,
  "userStoryCount": 0,
  "readableDocumentPath": "projects/<project-name>/artifacts/DEV/documents/backlog-review-dev.md",
  "status": "Success"
}
```

---

## Output Quality Checklist

Before completing, verify:

- [ ] Only DEV stories are present (`storyCategory = "DEV"` on every story)
- [ ] All actors are `"System"`
- [ ] Every story follows: `"As a System, I want <goal>, so that <benefit>."`
- [ ] Every story has at least 2 Acceptance Criteria
- [ ] Every story has at least 1 Business Rule
- [ ] No duplicate epics or stories
- [ ] All IDs are globally unique
- [ ] All `status` fields are `"Draft"`
- [ ] JSON is syntactically valid
- [ ] Save paths follow `artifacts/DEV/` structure

---

## Constraints

- Do NOT generate non-DEV stories
- Do NOT generate test cases or BDD scenarios
- Do NOT generate code or scripts
- Do NOT invent requirements not present in the attached document
- JSON bundle is the single source of truth — the readable document is derived from it


