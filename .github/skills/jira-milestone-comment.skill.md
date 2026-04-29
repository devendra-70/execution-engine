---
name: Jira Milestone Comment Skill
description: >
  Post progress milestone comments to Jira tickets asynchronously.
  Non-blocking: failures logged but do not halt the pipeline.
  Invoked by orchestrator at each major stage approval.
---

# JIRA_MILESTONE_COMMENT Skill

## Role

You are a Jira integration utility. Your sole responsibility is to post human-readable
milestone progress comments to Jira tickets when invoked by the orchestrator or other agents.

You do NOT:
- Modify ticket fields
- Change ticket status
- Create subtasks
- Update Jira configurations
- Wait for user confirmation

## Supported Milestones

| Milestone | Stage | Emoji | When Triggered |
|-----------|-------|-------|---|
| `BACKLOG_CREATED` | Stage 0 | 📋 | Ticket context loaded |
| `READY_FOR_DEV` | Stage 0.5 | 🟢 | Git branch setup complete |
| `ARCHITECTURE_APPROVED` | Stage 1 | 🏗️ | Architecture checkpoint approved |
| `DEVELOPMENT_STARTED` | Stage 2 | 👨‍💻 | Implementation approved |
| `TESTING_IN_PROGRESS` | Stage 3a | 🧪 | Unit tests started |
| `COVERAGE_VERIFIED` | Stage 3a | ✅ | Coverage thresholds met |
| `BUGFIX_IN_PROGRESS` | Stage 3b | 🔧 | Bugfix cycle started |
| `CODE_REVIEW_IN_PROGRESS` | Stage 3c | 👀 | Code review checkpoint |
| `REFACTORING_IN_PROGRESS` | Stage 3d | 🔨 | Automatic refactoring started |
| `POST_REFACTOR_VERIFICATION` | Stage 3f | 📊 | Coverage re-verified post-refactor |
| `READY_FOR_MERGE` | Stage 3 Exit | 🚀 | All quality gates passed |
| `PIPELINE_COMPLETED` | Stage 4 | ✨ | Pipeline finished |
| `PIPELINE_ABORTED` | Any stage | 🛑 | Pipeline aborted (build failure / human ABORT / escalation) |

---

## Input Format

```json
{
  "ticket_id": "AIS-123",
  "milestone": "COVERAGE_VERIFIED",
  "summary": "Test coverage thresholds met",
  "metrics": {
    "service_coverage": 92,
    "controller_coverage": 100,
    "overall_coverage": 87,
    "critical_issues": 0,
    "high_issues": 1,
    "medium_issues": 5,
    "tests_passed": 150,
    "tests_failed": 0
  },
  "artifacts": [
    {
      "name": "run-1-report.md",
      "path": "tickets/AIS-123/test-reports/run-1-report.md"
    },
    {
      "name": "architecture-decision.md",
      "path": "tickets/AIS-123/architecture/architecture-decision.md"
    }
  ],
  "stage_label": "Unit Test (Run 1)",
  "timestamp": "2026-04-28T14:32:15Z"
}
```

---

## Output Format

```json
{
  "ticket_id": "AIS-123",
  "milestone": "COVERAGE_VERIFIED",
  "status": "SUCCESS",
  "comment_id": "12345",
  "jira_url": "https://jira.company.com/browse/AIS-123",
  "timestamp": "2026-04-28T14:32:15Z",
  "message": "Comment posted successfully"
}
```

Or on failure (non-blocking):

```json
{
  "ticket_id": "AIS-123",
  "milestone": "COVERAGE_VERIFIED",
  "status": "FAILED",
  "error": "Connection timeout to Jira API",
  "timestamp": "2026-04-28T14:32:15Z",
  "message": "Comment not posted; pipeline continues"
}
```

---

## Comment Templates by Milestone

### BACKLOG_CREATED
```
📋 **Backlog Entry Created**

Story moved to automated processing pipeline.

**Details:**
• Ticket context loaded from Jira
• Pipeline auto-assigned
• Ready for architecture phase

**Status:** ✅ Ready to proceed
```

### READY_FOR_DEV
```
🟢 **Ready for Development**

Git branch created and checked out locally.

**Branch:** {BRANCH_NAME}
**Base:** {BASE_BRANCH}
**Commits:** Will track all changes on this branch

**Next:** Architecture design phase
```

### ARCHITECTURE_APPROVED
```
🏗️ **Architecture Design Approved**

Detailed architecture and design decisions documented.

**Artifacts:**
• [Architecture Decision](link to artifact)

**Decisions:**
{Architecture summary from ticket-context.md}

**Next Phase:** Backend Implementation
```

### DEVELOPMENT_STARTED
```
👨‍💻 **Development Phase Started**

Backend implementation approved and code generation complete.

**Files Created/Modified:** {count}
**Dependencies Added:** {count} (all approved)
**Build Status:** ✅ Passed

**Artifacts:**
• [Files Changed](link)
• [Implementation Notes](link)

**Next Phase:** Quality verification loop
```

### TESTING_IN_PROGRESS
```
🧪 **Unit Testing & Coverage Analysis**

Automated test suite executed. Coverage metrics extracted.

**Run:** Loop Run {N}, Attempt {attempt}/{max}

**Test Results:**
• Passed: {count}
• Failed: {count}
• Skipped: {count}

**Build:** ✅ Successful

**Artifacts:**
• [Test Report](link)

**Status:** Awaiting human approval
```

### COVERAGE_VERIFIED
```
✅ **Test Coverage Thresholds Verified**

All test coverage targets met. Code is adequately tested.

**Coverage Metrics:**
| Layer | Coverage | Target | Status |
|---|---|---|---|
| Service Layer | {X}% | ≥90% | ✅ |
| Controller Layer | {Y}% | ≥100% | ✅ |
| Overall | {Z}% | ≥85% | ✅ |

**Test Summary:**
• Total: {total} tests
• Passed: {passed}
• Failed: {failed}

**Artifacts:**
• [Detailed Report](link)

**Next Phase:** Automated bugfix cycle
```

### BUGFIX_IN_PROGRESS
```
🔧 **Automated Bugfix Cycle Running**

Bugfix Agent analyzing test failures and code issues.

**Run:** Loop Run {N}

**Issues Identified:** {count}
• CRITICAL: {c}
• HIGH: {h}
• MEDIUM: {m}

**Status:** Fixes applying...

**Artifacts:**
• [Bugfix Report](link)

**Next Phase:** Code review
```

### CODE_REVIEW_IN_PROGRESS
```
👀 **Code Review in Progress**

Automated code quality and architecture review phase.

**Run:** Loop Run {N}

**Review Focus:**
• Code quality assessment
• Architectural compliance check
• Performance review
• Security scan

**Status:** Review in progress...

**Artifacts:**
• [Code Review Report](link)

**Next Phase:** Automatic refactoring
```

### REFACTORING_IN_PROGRESS
```
🔨 **Automatic Code Refactoring**

Refactoring critical and high-severity issues identified in review.

**Run:** Loop Run {N}

**Refactoring Scope:**
• CRITICAL issues: {count}
• HIGH issues: {count}
• Techniques: Extract methods, consolidate duplicates, optimize performance

**Status:** Refactoring applied. Build check in progress...

**Note:** Silent stage - unless build fails, no human intervention needed.
```

### POST_REFACTOR_VERIFICATION
```
📊 **Post-Refactor Coverage Verification**

Coverage re-measured after refactoring changes.

**Coverage After Refactor:**
| Layer | After | Before | Delta | Status |
|---|---|---|---|---|
| Service Layer | {after}% | {before}% | {delta} | {✅/⚠️} |
| Controller Layer | {after}% | {before}% | {delta} | {✅/⚠️} |
| Overall | {after}% | {before}% | {delta} | {✅/⚠️} |

**Result:** Coverage {maintained/improved/degraded}

**Artifacts:**
• [Coverage Details](link)

**Next Phase:** Exit condition evaluation
```

### READY_FOR_MERGE
```
🚀 **Ready for Merge — All Gates Passed**

All automated quality gates passed successfully!

**Final State:**
✅ Coverage thresholds met
✅ All tests passing
✅ Code review approved
✅ No critical/high issues

**Loop Iterations:** {N} runs
**Total Time:** {duration}

**Artifacts:**
• [Architecture](link)
• [Implementation](link)
• [Test Reports](link)
• [Code Review](link)

**Next Step:** 
→ Push branch to origin (type `PUSH`)
→ Create MR/PR for human code review
→ Merge after human approval
```

### PIPELINE_COMPLETED
```
✨ **Automated Pipeline Completed**

All pipeline stages finished successfully!

**Final Metrics:**
• Loop Iterations: {N}
• Duration: {duration}
• Test Coverage: {coverage_line}% line / {coverage_branch}% branch
• Issues: CRITICAL={c}, HIGH={h}, MEDIUM={m}

**Deliverables:**
✅ Architecture decision
✅ Production code
✅ Comprehensive tests
✅ Code review
✅ Build verification

**Artifacts Created:**
```
tickets/{TICKET_ID}/
├── architecture/architecture-decision.md
├── implementation/files-changed.md
├── implementation/implementation-notes.md
├── test-reports/run-1..{N}-report.md
├── review-reports/run-1..{N}-review.md
└── bugfix-reports/run-1..{N}-bugfix.md
```

**Next Steps:**
1. Review all artifacts
2. Push branch: type `PUSH`
3. Create MR/PR in GitLab/GitHub
4. Request code review from human team
5. After approval: merge and deploy

**Handled by:** Codeval Orchestrator Automated Pipeline
**Build:** ✅ Successful | **Quality:** ✅ Verified | **Status:** ✅ Ready
```

### PIPELINE_ABORTED
```
🛑 **Pipeline Aborted**

The automated pipeline was halted before completion.

**Reason:** {reason}
**Stage at Abort:** {stage}
**Loop Run:** {N}

**Completed Before Abort:**
{summary of completed stages and artifacts}

**Action Required:** Manual developer review and intervention.

**Next Steps:**
1. Inspect the artifacts produced so far
2. Diagnose the abort reason
3. Either restart the pipeline or take over manually
```

---

## Implementation Rules

1. **Non-Blocking Failures:**
   - If Jira API is unreachable → log error, continue pipeline
   - If authentication fails → log error, continue pipeline
   - If comment is too long → truncate gracefully, post anyway
   - Never halt the pipeline due to Jira issues

2. **IDE Integration:**
   - Use the Jira integration available in the IDE
   - Authenticate via IDE tokens (already configured)
   - No need to pass new credentials to this skill

3. **Comment Length Limits:**
   - Jira max comment: 32KB
   - If metrics too long, use summary + artifact links
   - Always include artifact links to detailed reports

4. **Timestamp Format:**
   - Use ISO 8601 format: `YYYY-MM-DDTHH:MM:SSZ`
   - Ensure timezone consistency (UTC)

5. **Logging:**
   - Log all Jira API calls (method, endpoint, status)
   - Log post-comment success with comment_id
   - Log failures with error details but continue

6. **Artifact Links:**
   - Include human-readable links to artifacts
   - Link format: `[display_text](artifact_path)` for Markdown rendering
   - Provide direct paths so humans can access from IDE

---

## Invocation Pattern

The Orchestrator will invoke this skill like:

```
invoke jira-milestone-comment.skill with:
  ticket_id = {TICKET_ID}
  milestone = COVERAGE_VERIFIED
  stage_label = "Unit Test Coverage (Run 1)"
  metrics = {
    coverage: { service: 92, controller: 100, overall: 87 },
    tests: { passed: 150, failed: 0 }
  }
  artifacts = [
    { name: "run-1-report.md", path: "tickets/AIS-123/test-reports/run-1-report.md" }
  ]
```

---

## Error Handling

| Scenario | Handling | Log Event |
|----------|----------|-----------|
| Jira API timeout | Log warning, continue | `JIRA_COMMENT_TIMEOUT` |
| Auth failure | Log error, continue | `JIRA_AUTH_FAILED` |
| Comment too long | Truncate + post summary | `JIRA_COMMENT_TRUNCATED` |
| Ticket not found | Log error, continue | `JIRA_TICKET_NOT_FOUND` |
| Rate limit hit | Log warning, skip | `JIRA_RATE_LIMITED` |
| Success | Log with comment_id | `JIRA_COMMENT_POSTED` |

---

## Benefits

✅ **Non-intrusive** — Failures don't break pipeline  
✅ **Audit trail** — Full history of progress in Jira  
✅ **Human visibility** — Team sees real-time automation progress  
✅ **Artifact linking** — Quick access to detailed reports  
✅ **Engagement** — Comments keep stakeholders informed  
✅ **Reusable** — Any agent can invoke this skill  

---

