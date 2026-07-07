# Workflow Orchestration Layer — Analysis

*Generated: 2026-06-02 | Companion to [README.md](./README.md). Full research in [../tasks/research-spec-rot.md](../tasks/research-spec-rot.md) and the workflow research below.*

> **⏱ SUPERSEDED IN PART — 2026-07-06 (read first).** Kept as provenance; two premises are now stale. **(1)** The **Beads / YAML-DAG-compiler** framing below is superseded by the settled decision: orchestration = **Microsoft Conductor** (deterministic YAML route/`when` engine), runtime = **claudebox**, agents = a custom neutral **agent-definition primitive** — Beads is dropped. **(2)** "Claude Code subagent nesting is 1 level" is **wrong — it is now 5 levels.** The current orchestration design lives in [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md) (Stage 3); the general "spec layer ≠ orchestration layer" separation below still holds.

## The question

Can the harness, running **inside Claude Code**, support:
1. Workflows **defined in YAML**;
2. **One orchestrator agent delegating to sub-agents**;
3. **Tasks forming a DAG** with explicit dependencies (B and C depend on A)?

## Verdict: yes — but the workflow/DAG layer is SEPARATE from the spec layer

The single most important finding: **spec-driven tools (OpenSpec, Spec-Kit) do not provide workflows, DAGs, or sub-agent delegation.** Their `tasks.md` is a flat `- [ ]` checklist with no dependency edges. You assemble the orchestration layer as a distinct concern. The README architecture already separates these correctly.

## Requirements scored against the field

| Requirement | Spec tools | Native Claude Code | Right tool |
|---|---|---|---|
| **YAML workflow definition** | ❌ none (BMAD has YAML *agents*, not workflow DAGs) | ❌ native "dynamic workflows" exist but are **JavaScript, not YAML** | Author own YAML — borrow **Argo** `dependencies:[A]` syntax or **MS Conductor** agent-native YAML |
| **Orchestrator → sub-agent** | ⚠️ only BMAD | ✅ **native & mature**: `.claude/agents/`, per-subagent `model:` override, parallel (~16 concurrent), `isolation: worktree` | **Claude Code subagents** |
| **DAG w/ explicit deps** | ⚠️ only **Taskmaster** (`"dependencies":[ids]`, cycle-validated) | ⚠️ Tasks API has `blocked_by` but **no auto-dispatch / ready-set** | **Beads** (`bd dep add`, `bd ready --json`) |

## Key findings

1. **Only Taskmaster (27k⭐) and Beads (~24k⭐) give a true DAG with explicit dependency edges.** OpenSpec has nothing; Spec-Kit (108k⭐) has phase-ordering + `[P]` parallel flags but no per-task edges (open issue #1934 requests exactly this). → Beads as the DAG/state engine is the validated choice.

2. **Claude Code's orchestrator→subagent half is now fully native** (stronger than when the README was written): per-agent model override (Opus orchestrator + Sonnet executors), parallel dispatch, worktree isolation. **Hard limit: subagents cannot spawn subagents — 1 level of nesting only.** The Opus→Sonnet design fits within this.

3. **No native YAML workflow engine.** Claude Code's native "dynamic workflows" *is* a real DAG-capable orchestrator (≤1000 agents, 16 concurrent) but defined in **JavaScript**. For YAML, write a thin **YAML→Beads compiler** (or YAML→JS). YAML stays the human-authored source of truth; Beads is the runtime dependency engine; subagents are executors driven by `bd ready`.

4. **Two updates since the README:**
   - **MS Conductor is now real** (opensource.microsoft.com, May 2026): YAML-first, deterministic routing, calls Claude as a model provider, per-agent model overrides. Supersedes the stale "146 stars / v0.1.1" memory note. *Caveat:* runs as a **separate CLI beside Claude Code, not inside it**, and uses route/state-machine semantics (`routes: when:`) rather than explicit `dependencies:[A]` edges.
   - **`affaan-m/claude-swarm`** (~191⭐, hackathon-stage): the only single tool already doing YAML + topological-sort DAG + Opus-orchestrator→workers on `claude-agent-sdk`. Too immature to depend on; valuable as a reference implementation of the exact target.

## Validated stack (refinement of the README)

```
YAML workflow file (Argo-style `dependencies: [A]` edges)      ← human-authored source of truth
        │  thin compiler  (the only piece you build)
        ▼
Beads DAG  (bd dep add; bd ready --json = dispatchable set)    ← runtime dependency/state engine
        │  orchestrator loops on `bd ready`, claims (bd update --claim)
        ▼
Claude Code subagents (Opus orchestrator → Sonnet executors,  ← execution
                       worktree isolation, 1-level nesting)
```

Runs **entirely inside Claude Code** (the stated constraint). Only the YAML→Beads compiler is custom; the rest is off-the-shelf (Beads) or native (subagents).

### Argo-style YAML DAG syntax to borrow
```yaml
dag:
  tasks:
    - { name: A, template: ... }
    - { name: B, dependencies: [A], template: ... }
    - { name: C, dependencies: [A], template: ... }
    - { name: D, dependencies: [B, C], template: ... }
```

## Optional / candidate components (not yet decided)

These are noted as options to revisit — **not** part of the validated stack above yet.

- **DBOS (durable execution engine) — possibly recommended, optional.** Postgres-backed durable-execution *library* ([`dbos-transact-ts`](https://github.com/dbos-inc/dbos-transact-ts) ~1.2k⭐, [`dbos-transact-py`](https://github.com/dbos-inc/dbos-transact-py) ~1.4k⭐, MIT; also Java/Go). You annotate `@DBOS.workflow()` / `@DBOS.step()` and it checkpoints state to Postgres, so workflows **survive crashes/restarts and resume exactly where they left off**; also provides durable queues and cron scheduling — no separate orchestrator server (the explicit contrast with Temporal, which needs an external worker+server). **Where it fits:** the *durability/resumability* layer, complementary to Beads — Beads gives the DAG + `ready` set; DBOS would make the orchestrator's execution itself crash-resilient (cf. Fabro's "git-based checkpointing for resumable runs"). **Open question:** whether the harness needs durable execution given it runs inside Claude Code, and whether a Postgres dependency is worth it. See [DBOS Transact](https://www.dbos.dev/dbos-transact).

- **Fabro (workflow definition + integration) — optional.** Already profiled in [references/technologies.md](./references/technologies.md) (~960⭐, MIT). An **alternative way to *define* workflows and integrate them with the harness**: deterministic workflow graphs authored in Graphviz **DOT** (branching, loops, human-in-the-loop gates), multi-model routing, sandboxed execution with git-based checkpointing, REST API + web UI. **Where it fits:** a substitute for the "Argo-style YAML → Beads compiler → subagents" path — i.e. Fabro could own both the definition format (DOT instead of YAML) and the orchestration runtime. **Caveat:** it's a separate platform (single Rust binary, runs *beside* Claude Code rather than inside it), so adopting it trades the "entirely inside Claude Code" constraint for an off-the-shelf graph engine.

## Open decisions for next session

- **YAML schema:** adopt Argo's `dependencies: [A]` edge form vs. Conductor's route/`when` form. (Argo is the cleaner explicit-DAG match.)
- **Where the orchestrator runs:** native Claude Code subagents (in-process, the constraint) vs. native JS "dynamic workflows" vs. `claude-agent-sdk` script. All three keep it inside Claude Code; differ on determinism and reusability.
- **Beads integration path:** orchestrator shells out to `bd ready --json` (CLI) vs. an MCP server.

## Star counts (verified, June 2026)
Spec-Kit 107.7k · OpenSpec 52.4k · BMAD 48.5k · Taskmaster 27.3k · Beads ~24.3k · claude-swarm ~191.
