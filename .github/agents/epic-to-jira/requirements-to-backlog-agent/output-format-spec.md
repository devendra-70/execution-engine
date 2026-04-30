# Output Format Specification — requirements-to-backlog-agent

## Overview

The agent produces two output formats:

| Format | Purpose | Authority |
|--------|---------|-----------|
| JSON Bundle | Machine-readable, Jira-importable | **Source of truth** |
| Word Document | Human-readable review document | Derived from JSON |

The Word document must always be generated from the JSON bundle. They must never diverge.

---

## JSON Bundle — Full Structure

```json
{
  "projectName": "string",
  "projectKey": "string",
  "component": "string",
  "documentType": "string",
  "sourceFile": "string",
  "inputFilePath": "string",
  "generatedAt": "ISO 8601 timestamp",
  "generatedBy": "requirements-to-backlog-agent",
  "documents": {
    "jsonBundlePath": "string",
    "wordDocumentPath": "string"
  },
  "epics": []
}
```

---

## Top-Level Fields

| Field | Type | Description |
|-------|------|-------------|
| `projectName` | string | Full human-readable name of the project |
| `projectKey` | string | Short Jira-compatible key, uppercase (e.g., `CODEVAL`) |
| `component` | string | Canonical Jira Component value extracted from the source document header (e.g., `JAN2026-JAVA-TEAM4`). Mirrored into every Epic and Story `components` array. |
| `documentType` | string | Type of source document: `SRS`, `BRD`, `PRD`, or `SPEC` |
| `sourceFile` | string | Filename of the input document (e.g., `SRS.txt`) |
| `inputFilePath` | string | Full relative path to the input document |
| `generatedAt` | string | ISO 8601 timestamp of when the agent ran |
| `generatedBy` | string | Always `"requirements-to-backlog-agent"` |
| `documents.jsonBundlePath` | string | Save path of the full JSON bundle |
| `documents.wordDocumentPath` | string | Save path of the Word document |
| `epics` | array | Array of Epic objects (see below) |

## Team Prefix Rule

Before generating Epics and Stories, extract the team number from the first non-empty line of the source requirements file.

- The first non-empty line must contain a team number (for example: `Team 4`, `Team: 4`, `team no 4`)
- Normalize the prefix as `Team<N>-` (example: `Team4-`)
- Apply this prefix to every Epic `title`, Epic `epicName`, and Story `title`
- If the first non-empty line does not contain a team number, the output is invalid and generation must stop with a validation error

## Component Extraction Rule

Before generating Epics and Stories, extract the Jira Component from the source requirements file header.

- Scan the header (before the first numbered section) for a line matching `Component\s*[:\-]\s*<value>` (case-insensitive)
- Preserve the extracted value's original casing (for example: `JAN2026-JAVA-TEAM4`)
- Populate the top-level `component` field with this value
- Ensure every Epic `components` array and every Story `components` array contains this value as its first element (deduplicate against any other components derived from content)
- If no `Component` line is present in the header, stop with a validation error (`MissingComponent`)

---

## Epic Object — Field Definitions

```json
{
  "epicId": "EPIC-001",
  "issueType": "Epic",
  "title": "string",
  "epicName": "string",
  "summary": "string",
  "description": "string",
  "labels": ["string"],
  "components": ["string"],
  "status": "Draft",
  "sourceTrace": ["string"],
  "savePath": "string",
  "userStories": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `epicId` | string | Unique sequential ID: `EPIC-001`, `EPIC-002`, etc. |
| `issueType` | string | Always `"Epic"` |
| `title` | string | Short, descriptive title of the capability, prefixed as `Team<N>-...` |
| `epicName` | string | Jira Epic Name value; must match `title` prefix and begin with `Team<N>-...` |
| `summary` | string | One-sentence summary of the epic's purpose |
| `description` | string | Full explanation of the capability and its value |
| `labels` | array of strings | Tags for categorization (e.g., `["ai", "generation"]`) |
| `components` | array of strings | System components this epic touches |
| `status` | string | Always `"Draft"` on generation |
| `sourceTrace` | array of strings | References to source document sections that informed this epic |
| `savePath` | string | Relative path where this epic's individual JSON is saved |
| `userStories` | array | Array of User Story objects nested within this epic |

---

## User Story Object — Field Definitions

```json
{
  "userStoryId": "US-001",
  "epicId": "EPIC-001",
  "issueType": "Story",
  "parent": "EPIC-001",
  "title": "string",
  "summary": "string",
  "description": "string",
  "story": "As a <actor>, I want <goal>, so that <benefit>.",
  "actor": "System",
  "priority": "High",
  "storyPoints": 3,
  "labels": ["string"],
  "components": ["string"],
  "status": "Draft",
  "sourceTrace": ["string"],
  "acceptanceCriteria": [],
  "formattedAcceptanceCriteria": "string",
  "businessRules": [],
  "formattedBusinessRules": "string",
  "savePath": "string"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `userStoryId` | string | Globally unique ID: `US-001`, `US-002`, etc. |
| `epicId` | string | ID of the parent epic this story belongs to |
| `issueType` | string | Always `"Story"` |
| `parent` | string | Same as `epicId` — used for Jira hierarchy mapping |
| `title` | string | Short, imperative title of the story, prefixed as `Team<N>-...` |
| `summary` | string | One-sentence description of what the story achieves |
| `description` | string | Detailed explanation of the behavior being described |
| `story` | string | Full story in format: `As a <actor>, I want <goal>, so that <benefit>.` |
| `actor` | string | Always `System` for DEV-only generation |
| `priority` | string | One of: `Low`, `Medium`, `High`, `Critical` |
| `storyPoints` | integer | Complexity estimate: `1`, `2`, `3`, `5`, or `8` |
| `labels` | array of strings | Tags for categorization |
| `components` | array of strings | System components this story touches |
| `status` | string | Always `"Draft"` on generation |
| `sourceTrace` | array of strings | References to source document sections |
| `acceptanceCriteria` | array | Array of AC objects (see below) |
| `formattedAcceptanceCriteria` | string | Newline-separated numbered list of AC statements |
| `businessRules` | array | Array of BR objects (see below) |
| `formattedBusinessRules` | string | Newline-separated numbered list of BR statements |
| `savePath` | string | Relative path where this story's individual JSON is saved |

---

## Acceptance Criteria Object — Field Definitions

```json
{
  "acId": "AC-001",
  "statement": "string"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `acId` | string | ID scoped to the story: `AC-001`, `AC-002`, etc. |
| `statement` | string | Single testable, measurable statement describing expected behavior |

### `formattedAcceptanceCriteria`

The `formattedAcceptanceCriteria` field is a pre-formatted string representation of the `acceptanceCriteria` array, ready for display in Jira description fields.

**Format:**
```
1. <statement of AC-001>
2. <statement of AC-002>
3. <statement of AC-003>
```

---

## Business Rules Object — Field Definitions

```json
{
  "brId": "BR-001",
  "statement": "string"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `brId` | string | ID scoped to the story: `BR-001`, `BR-002`, etc. |
| `statement` | string | Single precise constraint or system policy |

### `formattedBusinessRules`

The `formattedBusinessRules` field is a pre-formatted string of the `businessRules` array.

**Format:**
```
1. <statement of BR-001>
2. <statement of BR-002>
```

---

## Jira Field Mapping

The following table maps JSON fields to their corresponding Jira fields for import:

| JSON Field | Jira Field | Applies To |
|------------|------------|------------|
| `epicId` | Issue Key | Epic |
| `issueType` | Issue Type | Epic, Story |
| `title` | Summary | Epic, Story |
| `description` | Description | Epic, Story |
| `labels` | Labels | Epic, Story |
| `components` | Components | Epic, Story |
| `status` | Status | Epic, Story |
| `parent` | Epic Link / Parent | Story |
| `story` | Description (top section) | Story |
| `actor` | Custom Field or Label | Story |
| `priority` | Priority | Story |
| `storyPoints` | Story Points | Story |
| `formattedAcceptanceCriteria` | Acceptance Criteria field | Story |
| `formattedBusinessRules` | Business Rules / Description section | Story |

> **Note:** `formattedAcceptanceCriteria` and `formattedBusinessRules` can be mapped to custom Jira fields, or appended to the `Description` field depending on your Jira configuration.

---

## Save Path Conventions

| Artifact | Path Pattern |
|----------|--------------|
| Full JSON bundle | `projects/<project-name>/artifacts/epics/epics-bundle.json` |
| Individual Epic | `projects/<project-name>/artifacts/epics/<epic-slug>/<epic-id-lowercase>.json` |
| Individual Story | `projects/<project-name>/artifacts/epics/<epic-slug>/user-stories/<story-id-lowercase>.json` |
| Word Document | `projects/<project-name>/artifacts/documents/backlog-review.docx` |

### Slug Generation Rules

| Input | Slug |
|-------|------|
| `AI Question Generation` | `ai-question-generation` |
| `Test Case Generator` | `test-case-generator` |
| `Editor Interface` | `editor-interface` |

Rules:
- Lowercase all characters
- Replace spaces with hyphens
- Remove special characters
- Trim leading/trailing hyphens

---

## Allowed Field Values

### `actor`

| Value | Usage |
|-------|-------|
| `System` | Automated system/backend behavior |

### `priority`

| Value | When to Use |
|-------|-------------|
| `Critical` | Core system functions — system unusable without it |
| `High` | Primary feature, required for main use case |
| `Medium` | Supporting feature, important but not blocking |
| `Low` | Optional, edge-case, or enhancement |

### `storyPoints`

| Value | Complexity |
|-------|------------|
| `1` | Trivial |
| `2` | Simple |
| `3` | Moderate |
| `5` | Complex |
| `8` | Very complex |

### `status`

Always `"Draft"` on agent generation. Updated manually post-review.

### `issueType`

| Value | Applies To |
|-------|------------|
| `"Epic"` | Epic objects |
| `"Story"` | User Story objects |

---

## Word Document Structure

The Word document must be structured as follows (derived strictly from JSON):

```
[TITLE]
Requirements Backlog Document

[METADATA]
Project Name:  <projectName>
Source File:   <sourceFile>
Generated At:  <generatedAt>
Generated By:  <generatedBy>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[EPIC SECTION — repeated for each epic]

EPIC-001 — <title>
Summary:      <summary>
Description:  <description>

  [USER STORY — repeated for each story in epic]

  US-001 — <title>
  Story:    As a <actor>, I want <goal>, so that <benefit>.
  Actor:    <actor>
  Priority: <priority>

  Acceptance Criteria:
    1. <statement>
    2. <statement>

  Business Rules:
    1. <statement>
    2. <statement>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Consistency Rules Between JSON and Word Document

| Rule |
|------|
| Every Epic in JSON must appear in the Word document |
| Every User Story in JSON must appear in the Word document |
| AC statements in Word must match `formattedAcceptanceCriteria` exactly |
| BR statements in Word must match `formattedBusinessRules` exactly |
| No additional content may appear in Word that is not in JSON |
| No JSON data may be omitted from the Word document |

