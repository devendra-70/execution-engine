# Skill: Ticket Context Builder

## Purpose
Pull a Jira ticket's full details and write a standardised `ticket-context.md`
into `tickets/{TICKET_ID}/` so every subsequent pipeline agent has a single,
authoritative source of truth for the ticket.

---

## When This Skill Is Invoked

The Orchestrator invokes this skill at pipeline start, after a human pulls a
ticket and provides the ticket ID. It runs ONCE per ticket run.

---

## Inputs Required

| Input             | Source                          | Notes                                        |
|-------------------|---------------------------------|----------------------------------------------|
| `TICKET_ID`       | Human provides at pipeline start | e.g. `EPMICMPCOD-137`                        |
| Jira credentials  | Pre-configured in MCP/env        | Agent uses existing jira-sync.agent.md config |

---

## Step 1 — Pull Ticket from Jira

Use the existing Jira agent configuration to fetch:

- Ticket title / summary
- Ticket description (full text)
- Acceptance criteria (if present as a field or in the description)
- Ticket type (Story / Bug / Task / Sub-task)
- Story points / estimate
- Linked epic (epic key + epic name)
- Linked tickets (blocks / blocked-by / relates-to)
- Labels and components
- Assignee
- Reporter
- Current status
- Sprint (if assigned)
- Attachments list (names only, not content)

If the Jira fetch fails:
TICKET FETCH ERROR: Could not retrieve {TICKET_ID} from Jira.
Reason: {error message}
The pipeline cannot start without ticket context. Please verify the ticket ID
and Jira connectivity, then retry.

Halt. Do not create a partial context file.

---

## Step 2 — Create Ticket Directory

Create the following folder structure if it does not exist:

Paths resolved from agent-config.yml:

{tickets.root}/{TICKET_ID}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.locks}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.architecture}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.implementation}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.test_reports}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.review_reports}/
{tickets.root}/{TICKET_ID}/{tickets.subfolders.bugfix_reports}/


---

## Step 3 — Write ticket-context.md

Write the file to: `{tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file}`

Use the EXACT template below. Do not omit any section.
If a field is not available from Jira, write `N/A`.

```markdown
# Ticket Context — {TICKET_ID}

**Generated:** {ISO_8601_TIMESTAMP}
**Pipeline Run:** 1
**Status at Pull:** {Jira status}

---

## Ticket Details

| Field            | Value                                |
|------------------|--------------------------------------|
| Ticket ID        | {TICKET_ID}                          |
| Title            | {ticket summary/title}               |
| Type             | Story / Bug / Task / Sub-task        |
| Epic             | {EPIC_KEY} — {Epic Name}             |
| Story Points     | {points or N/A}                      |
| Assignee         | {name or N/A}                        |
| Reporter         | {name}                               |
| Sprint           | {sprint name or N/A}                 |
| Labels           | {comma-separated or N/A}             |
| Components       | {comma-separated or N/A}             |

---

## Description

{Full ticket description, preserving formatting as markdown}

---

## Acceptance Criteria

{Acceptance criteria as a checklist. If not explicitly defined in Jira, extract them
from the description. If none can be determined, write: "No formal acceptance criteria defined."}

- [ ] {criterion 1}
- [ ] {criterion 2}

---

## Linked Tickets

| Relationship | Ticket ID     | Title                 |
|--------------|---------------|-----------------------|
| Blocks       | {KEY}         | {title}               |
| Blocked by   | {KEY}         | {title}               |
| Relates to   | {KEY}         | {title}               |

---

## Attachments

{List of attachment filenames, or "None"}

---

## Pipeline Metadata

| Field               | Value               |
|---------------------|---------------------|
| Pipeline started    | {ISO_8601_TIMESTAMP} |
| Current stage       | Context Built       |
| Architecture run    | Pending             |
| Implementation run  | Pending             |
| Review loop runs    | 0                   |
##Step 4 — Log Creation
Append to {github.logs.master_agent_log}:

| {TIMESTAMP} | Orchestrator | ARTIFACT_CREATED | {tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file} | COMPLETE |
##Step 5 — Signal Ready
Return to the orchestrator:

TICKET CONTEXT BUILT:
  Ticket : {TICKET_ID} — {title}
  Epic   : {EPIC_KEY}
  Type   : {type}
  Path   : tickets/{TICKET_ID}/ticket-context.md

Ready to advance to Architecture stage.
Idempotency Rule
If {tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file} already exists (re-run scenario):

Do NOT overwrite it

Instead, append a new ## Pipeline Re-run — {TIMESTAMP} section with a note that the pipeline was re-started

Preserve all prior pipeline metadata