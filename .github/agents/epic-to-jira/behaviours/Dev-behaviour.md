# Developer Behavior Prompt

## Objective

Bias the backlog generation toward a **developer / system implementation perspective**.

The agent must interpret the requirement document with a focus on **system behavior, processing logic, orchestration, and internal responsibilities**, rather than user-facing validation or UI flows.

---

## Core Developer Mindset

Always think:

* “What should the system do?”
* “How should this be processed?”
* “What are the internal steps?”
* “What validations must occur before processing?”
* “How do components interact?”
* “What are the system responsibilities?”

---

## Story Generation Bias

Prioritize generation of user stories that cover:

### 1. Core System Behavior

* processing logic
* execution flow
* data handling
* transformation steps

### 2. Orchestration Logic

* multi-step workflows
* sequencing between components
* coordination between services/models

### 3. Validation Logic (System-Side)

* pre-check validations
* input validation logic before processing
* execution gating conditions

### 4. Integration Behavior

* interaction between modules
* model interactions (e.g., Model 1 → Model 2/3)
* data passing between components

### 5. Pipeline Behavior

* execution pipelines
* parallel processing flows
* dependency management

### 6. Retry and Fallback Logic

* retry mechanisms
* failure recovery
* fallback strategies
* escalation logic

---

## Story Category Rules

* Generate only stories where:
  `storyCategory = "DEV"`

* Exclude:

    * UI-only behavior
    * manual/admin-only workflows
    * purely user interaction flows unless they trigger system behavior

---

## Actor Rules

Always use:

* `actor = "System"`

Unless a system-triggered user action must be explicitly described.

---

## Acceptance Criteria Requirements

Acceptance Criteria must describe:

* system execution behavior
* validation logic
* processing outcomes
* orchestration results
* success and failure handling

---

### Strong DEV AC Examples

Instead of:

* “System processes input”

Write:

* “System validates input before triggering question generation”
* “System forwards generated question to Model 2 and Model 3 in parallel”
* “System halts execution if validation fails”
* “System retries test case generation once upon failure”

---

## Business Rules Emphasis

Focus on:

* execution constraints
* processing rules
* orchestration conditions
* sequencing rules
* retry limits
* fallback strategies
* dependency requirements

### Examples:

* “Model 2 and Model 3 must execute in parallel after Model 1”
* “System must retry test case generation once before failover”
* “Validation must complete before pipeline execution”
* “Failover must occur if retry fails”

---

## System Thinking Rule (VERY IMPORTANT)

For every major flow, explicitly consider:

* What triggers this process?
* What happens step-by-step?
* What dependencies exist?
* What conditions must be satisfied before execution?
* What happens if a step fails?
* What is the fallback path?

---

## Output Quality Rules

* Every story must describe system behavior clearly
* Stories must represent executable system logic
* Avoid vague statements like:

    * “system works correctly”
    * “data is processed”
* Prefer precise statements describing:

    * validation
    * execution
    * routing
    * processing outcomes

---

## Exclusions

Do NOT generate:

* UI-only stories
* manual admin workflows without system involvement
* pure validation from user perspective
* automation scripts
* test cases
* BDD scenarios

---

## Priority Bias

Assign higher priority to:

* core pipeline execution
* orchestration logic
* validation gating logic
* failure handling mechanisms

Assign lower priority to:

* optional flows
* secondary system enhancements

---

## Final Instruction

Generate backlog artifacts such that:

* developers can clearly understand system responsibilities
* stories describe executable logic
* acceptance criteria define how the system behaves
* orchestration and processing flows are clearly represented

[//]: # (Use the following command to run the agent with this prompt:)


[//]: # (The output must remain aligned with the JSON structure defined by the main requirements agent.)

[//]: # ()
[//]: # (#file:agents/orchestrator-agent/orchestrator-agent.md)

[//]: # (#file:prompts/Dev-prompts.md)

[//]: # ()
[//]: # (RUN BACKLOG CATEGORY=DEV PROJECT=CodEval INPUT=projects/CodEval/requirements/SRS.txt)