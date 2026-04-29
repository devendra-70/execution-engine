# Behaviour: Config Reader

## Purpose
All agents resolve every file path by reading `.github/agent-config.yml` first.
No agent hardcodes any path. If the config file moves or values change,
agents adapt automatically without modification.

---

## When This Behaviour Runs

**Before any other behaviour or action on every invocation.**
Config is loaded once at startup and held for the duration of the agent's run.

---

## How to Load the Config

1. Read `.github/agent-config.yml`
2. If the file does not exist:
   ```
   CONFIG ERROR: .github/agent-config.yml not found.
   Cannot resolve paths. Pipeline cannot continue.
   Reporting to orchestrator.
   ```
   Halt immediately.
3. Parse all keys into an internal reference table (see Path Resolution below)

---

## Path Resolution Reference

Agents build all paths using this substitution table.
`{TICKET_ID}` and `{N}` are runtime values injected by the orchestrator.

### Ticket-Scoped Paths

| What you need | How to build it |
|---|---|
| Ticket folder root | `{tickets.root}/{TICKET_ID}/` |
| Ticket context file | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.context_file}` |
| Locks folder | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.locks}/` |
| A specific lock file | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.locks}/{locks.<area>}` |
| Architecture decision | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.architecture}/{tickets.artifacts.architecture_decision}` |
| files-changed.md | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.implementation}/{tickets.artifacts.files_changed}` |
| implementation-notes.md | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.implementation}/{tickets.artifacts.implementation_notes}` |
| Test report run N | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.test_reports}/{tickets.artifacts.test_report}` (replace `{N}`) |
| Review report run N | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.review_reports}/{tickets.artifacts.review_report}` (replace `{N}`) |
| Bugfix report run N | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.bugfix_reports}/{tickets.artifacts.bugfix_report}` (replace `{N}`) |
| Loop state | `{tickets.root}/{TICKET_ID}/{tickets.subfolders.loop_state}` |

### Infrastructure Paths

| What you need | Config key |
|---|---|
| Requirements folder | `{github.requirements}` |
| Global architecture docs | `{architecture.global_docs}` |
| Master agent log | `{github.logs.master_agent_log}` |
| Config change log | `{github.logs.config_change_log}` |
| Persistent issues log | `{github.logs.persistent_issues_log}` |
| Document templates | `{github.document_templates}` |

### Code & Test Generation Paths

| What you need | Config key |
|---|---|
| Where to write production code | `{codegen.base}` |
| Default package root | `{codegen.package_root}` |
| Where to write test files | `{testgen.base}` |
| Test resources | `{testgen.resources}` |
| JaCoCo XML report | `{testgen.coverage_report}` |
| Surefire reports | `{testgen.report_root}` |
| Root convenience test report | `{testgen.convenience_report}` |

### Control Values

| What you need | Config key |
|---|---|
| Max loop runs | `{loop.max_runs}` |
| Persistent issue threshold | `{loop.persistent_issue_threshold}` |
| Lock stale threshold (minutes) | `{locks.stale_threshold_minutes}` |
| Protected config file patterns | `{config_guard.patterns}` |

---

## Non-Negotiable Rules

- Agents NEVER write a literal path string — always derive from config
- If a config key is missing, halt and report: `CONFIG KEY MISSING: {key}`
- The config file itself is a protected config file — any change to it requires human approval via config-permission behaviour
- Agents read the config on every invocation — do not cache from a prior run