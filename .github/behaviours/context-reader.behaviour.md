# Behaviour: Context Reader

## When This Behaviour Runs
After config-reader.behaviour.md loads the config, and before any other action.
All paths below are resolved from agent-config.yml keys — not hardcoded.

## Purpose
Every agent in the pipeline MUST read a defined set of context sources before
beginning any analysis, design, generation, or modification work.
This ensures all agents operate from a shared, consistent understanding of:
- What the ticket is asking for
- What architecture decisions have been made
- What the project-wide standards require
- What previous pipeline stages produced

---

## Mandatory Read Order

Every agent MUST read the following sources in the order listed below.
Do not skip any source. If a source does not exist yet (because a prior stage
hasn't run), note it as "not yet available" and proceed — do not halt.

### 1. Project-Wide Standards (always read first)

{github.requirements}/**

Read ALL files in this folder.
These define the non-negotiable project rules every agent must abide by.
If any planned action conflicts with these requirements, the agent MUST flag
the conflict before proceeding.

### 2. Global Architecture (always read second)

{github.agents}/architecture-agent.agent.md   (to understand the architecture output schema)
{architecture.global_docs}/**       (global architecture documents)

Read the most recent version of the global architecture document.
Understand the overall system design before making any ticket-scoped decisions.

### 3. Ticket Context (always read third)

{tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file}

This file is created by the Orchestrator at the start of every ticket run.
It contains: ticket title, description, acceptance criteria, linked epic, related tickets.

If this file does NOT exist:
CONTEXT ERROR: {tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file} not found.
Cannot proceed without ticket context. Reporting to orchestrator.

Halt and report. Do not proceed.

### 4. Ticket-Scoped Architecture (read fourth, if exists)

{tickets.root}/{TICKET_ID}/{tickets.subfolders.architecture}/{tickets.artifacts.architecture_decision}

Read if it exists. This is the architecture the Architecture Agent produced
specifically for this ticket. All implementation decisions must align with it.

### 5. Agent-Specific Prior Outputs (read fifth — agent-dependent)

Each agent reads the prior-stage outputs relevant to its work:

| Agent                        | Additional Context to Read                                               |
|------------------------------|--------------------------------------------------------------------------|
| Architecture Agent           | (none — produces the first artifact)                                     |
| Backend Implementation Agent | `{tickets.root}/{id}/{tickets.subfolders.architecture}/{tickets.artifacts.architecture_decision}` |
| Unit Test Agent              | `{tickets.root}/{id}/{tickets.subfolders.implementation}/{tickets.artifacts.files_changed}` + `{tickets.artifacts.implementation_notes}` |
| Code Review Agent            | `{tickets.root}/{id}/{tickets.subfolders.implementation}/{tickets.artifacts.files_changed}` + `{tickets.root}/{id}/{tickets.subfolders.test_reports}/{tickets.artifacts.test_report}` |
| Bugfix Agent                 | `{tickets.root}/{id}/{tickets.subfolders.test_reports}/{tickets.artifacts.test_report}` + `{tickets.root}/{id}/{tickets.subfolders.review_reports}/{tickets.artifacts.review_report}` |
| Orchestrator                 | All of the above                                                         |

---

## Context Validation

After reading all sources, each agent MUST perform a quick internal validation:

1. **Consistency check**: Does the ticket context align with the global architecture?
   If not, flag the inconsistency to the orchestrator before proceeding.

2. **Scope check**: Is the ticket asking for something outside the current system's
   defined boundaries? If so, flag it — do not silently extend scope.

3. **Prior stage check**: Are all required prior-stage artifacts present?
   If a required artifact is missing (e.g., an implementation agent running without an architecture decision), halt and report.

---

## Context Freshness Rule

An agent MUST re-read context sources at the START of every invocation, even if
it has been invoked before in the same ticket run. Prior context held in memory
from a previous run must not be assumed to be current.

---

## Conflict Escalation Format

If the agent detects a conflict between context sources, it MUST report using this format:

CONTEXT CONFLICT DETECTED:
Source A: {source file}  →  States: {relevant detail}
Source B: {source file}  →  States: {conflicting detail}

Impact: {what breaks or becomes ambiguous if this conflict is not resolved}
Recommendation: {which source the agent believes should take precedence and why}

Awaiting orchestrator instruction before proceeding