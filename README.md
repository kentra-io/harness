# Agentic Coding Harness

Orchestrated, spec-driven development using Claude Code as the runtime.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Spec-Kit (tailored)                                      │
│  Input: requirements / PRD / issue                        │
│  Output: Beads DAG (tasks + dependencies + gates)         │
│  Workflow: Specify → Plan → Tasks → Beads issues          │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  Beads (bd)                                               │
│  Persistent DAG task store (Dolt-backed)                  │
│  bd ready --json → dispatch-ready tasks                   │
│  bd close <id>  → mark complete                           │
│  BeadBoard UI   → visualize DAG progress                  │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  Orchestration (Claude Code - Opus)                       │
│  Reads ready tasks from Beads                             │
│  Dispatches to Sonnet subagents via Agent tool            │
│  Runs gate checks (tests/lint) at phase boundaries        │
│  Workflow definitions use Conductor YAML syntax           │
└────────────────────────┬─────────────────────────────────┘
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
     ┌───────────┐ ┌───────────┐ ┌───────────┐
     │  Sonnet   │ │  Sonnet   │ │  Sonnet   │
     │ Subagent  │ │ Subagent  │ │ Subagent  │
     │ (worktree)│ │ (worktree)│ │ (worktree)│
     └───────────┘ └───────────┘ └───────────┘
```

## Components

### 1. Planning Layer — Spec-Kit (tailored)
- Adapted from [github/spec-kit](https://github.com/github/spec-kit)
- Structured workflow: Specify → Plan → Tasks → Beads DAG
- Outputs Beads issues with dependencies, acceptance criteria, and phase assignments
- Tailored to enforce separation of functional requirements, non-functional requirements, and technical architecture (TBD)

### 2. Task DAG — Beads
- [steveyegge/beads](https://github.com/steveyegge/beads) (~22K stars)
- Dolt-backed version-controlled SQL database, git-syncable
- Tasks with explicit `blocked_by` dependencies form a DAG
- `bd ready --json` returns only tasks whose dependencies are all complete
- Hash-based IDs prevent merge collisions across parallel agents
- Compaction summarizes old closed tasks to save context window

### 3. Visualization — BeadBoard
- Community dashboard for Beads
- Live DAG visualization, agent status, progress tracking

### 4. Workflow Definition — Conductor YAML Syntax
- Borrowed from [microsoft/conductor](https://github.com/microsoft/conductor)
- YAML-defined DAG workflows with phases, tasks, dependencies, gates
- Per-agent model overrides (Opus for orchestration, Sonnet for execution)
- Human-in-the-loop gates at phase boundaries
- Deterministic routing — zero LLM tokens spent on orchestration

### 5. Orchestration Runtime — Claude Code
- Opus thread reads workflow definition + Beads state
- Dispatches tasks to Sonnet subagents via `Agent` tool
- `model: "sonnet"` for implementation, `model: "opus"` for planning/review
- `isolation: "worktree"` for runtime isolation
- `run_in_background: true` for parallel execution
- Gate checks (test commands) run between phases
- All usage under Claude Max subscription — no separate API billing

## Execution Flow

1. **Plan** — Opus runs the planning skill, producing a Beads DAG from requirements
2. **Dispatch** — Opus queries `bd ready --json`, dispatches ready tasks to Sonnet subagents
3. **Execute** — Each Sonnet subagent works in an isolated worktree on its assigned task
4. **Complete** — On success, Opus runs `bd close <task-id>`
5. **Gate** — When all tasks in a phase are done, Opus runs the phase gate (e.g., `./gradlew test`)
6. **Advance** — If gate passes, Opus queries `bd ready --json` again for the next phase
7. **Stop** — If gate fails, Opus reports and awaits human decision

## Open Problems (Next Session)

- **Spec rot** — keeping specs in sync with evolving implementation
- **Requirement separation** — formal split of:
  - Functional requirements (what the system does)
  - Non-functional requirements (performance, security, reliability)
  - Technical architecture (rigid, project-level decisions)
- **Spec vs documentation** — specs are forward-looking (what to build); docs are backward-looking (synthesis of what was built). How to manage the lifecycle.

## Research Reference

Full research report with 38 evaluated tools: [tasks/research-agentic-workflow-tools.md](../tasks/research-agentic-workflow-tools.md)
