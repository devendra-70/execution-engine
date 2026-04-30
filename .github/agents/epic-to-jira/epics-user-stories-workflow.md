# Epics and User Stories Agent Workflow

This workflow is a short chain of agents that turns a requirements document into DEV-only Epic and User Story artifacts.

## High-Level Flow

1. A run starts from DEV_prompt.md.
2. That prompt activates the orchestrator-agent.
3. The orchestrator validates the command, project name, and attached requirements file.
4. The orchestrator sends the requirements into requirements-to-backlog-agent.
5. The requirements agent reads the document, identifies major capabilities, creates Epics, and creates DEV User Stories under each Epic.
6. The requirements agent outputs a DEV backlog JSON bundle as the source of truth.
7. The orchestrator then passes that bundle to backlog-materializer-agent.
8. The materializer creates the final artifact structure: epic files, user story files, and a readable backlog review document.
9. The flow ends with a short summary showing what was generated.

## How The Agents Behave

### Prompt Layer

- DEV_prompt.md runs the flow for DEV stories only.
- The prompt applies DEV-only routing and passes the attached requirements file into the chain.

### Orchestrator Agent

- Acts as the controller of the workflow.
- Does not create backlog content itself.
- Runs downstream agents in order.
- Ensures outputs are saved in the DEV artifact folders.

### Requirements-to-Backlog Agent

- Reads the requirements document.
- Groups related functionality into Epics.
- Creates DEV User Stories for each Epic.
- Adds Acceptance Criteria and Business Rules from a system perspective.
- Produces the main DEV JSON backlog bundle.

### Backlog Materializer Agent

- Uses the JSON bundle as the only source of truth.
- Does not reinterpret or rewrite requirements.
- Splits the bundle into individual Epic and User Story files.
- Generates the readable backlog review document.

## DEV Routing

- Story category is always DEV.
- Actor is system-focused.
- The chain always produces only DEV artifacts.

## Simple Chain View

Prompt -> Orchestrator Agent -> Requirements-to-Backlog Agent -> Backlog Materializer Agent -> DEV Epic and User Story Artifacts

## Final Output

At the end of the workflow, the project gets:

- a DEV epic bundle JSON
- individual Epic JSON files
- individual User Story JSON files
- a readable backlog review document

So in short: one agent controls the flow, one agent creates the backlog structure, and one agent materializes that structure into project files.
