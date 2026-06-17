# Library Analysis

Deeper-than-README analysis of every position in the reference library ([technologies.md](./technologies.md)) plus the named candidates from [workflow-orchestration-analysis.md](../workflow-orchestration-analysis.md). For each project: what it actually is (beyond the README), core features, what it could bring to *our* setup, and 2-4 open-source alternatives (>1000⭐) frequently cited in the same category.

*Companion to `technologies.md` (which stays the clean catalog). Generated 2026-06-16 via parallel web research, one focused pass per project.*

> **Method & caveats.** Star counts and licenses were verified via web search / GitHub API in **June 2026** and will drift. Several projects lean on **self-reported benchmarks** (notably Ruflo and rf-harness) — treated with skepticism below. A handful of repos have recently **moved orgs** (Beads → `gastownhall`, opencode → `anomalyco`, goose → `aaif-goose`); old URLs 301-redirect. "Runs *beside* Claude Code" vs. "runs *inside* it" is called out per project, since that's our core constraint.

---

## Summary table

| Project | Layer | Repo | ⭐ | License | One-line role |
|---|---|---|---|---|---|
| **Omnigent** | Meta-harness | omnigent-ai/omnigent | ~2.1k | Apache-2.0 | Vendor-neutral common layer over Claude Code/Codex/Pi (Databricks) |
| **Ruflo** | Meta-harness | ruvnet/ruflo | ~59.7k | MIT | Swarm orchestration meta-harness for Claude (renamed claude-flow) |
| **Gas Town** | Multi-agent orchestration | steveyegge/gastown | ~16k | MIT | Workspace manager built *on Beads*; Mayor→Polecat agent crews |
| **Symphony** | Multi-agent orchestration | openai/symphony | ~25k | Apache-2.0 | Task-board-driven autonomous Codex runs (OpenAI spec + Elixir ref) |
| **Factory.ai** | Commercial reference | factory.ai | — | commercial | Agent-native dev platform: Droids + multi-agent "Missions" |
| **claude-swarm** | YAML-DAG orchestration (ref) | affaan-m/claude-swarm | ~0.24k | MIT | Hackathon prototype of YAML→topo-sort DAG→Opus-orchestrator |
| **Fabro** | Workflow engine | fabro-sh/fabro | ~1.3k | MIT | Deterministic DOT-graph workflow engine, single Rust binary |
| **MS Conductor** | Workflow engine | microsoft/conductor | ~0.25k | MIT | YAML CLI, deterministic LLM-free routing (Microsoft, 2026) |
| **Spec-Kit** | Spec / planning | github/spec-kit | ~112k | MIT | GitHub's spec-driven development toolkit (our planning layer) |
| **OpenSpec** | Spec / planning | Fission-AI/OpenSpec | ~55k | MIT | Lightweight, brownfield-first, delta-spec SDD |
| **BMAD** | Planning methodology | bmad-code-org/BMAD-METHOD | ~49k | MIT | Agile role-persona agents + "story file" hand-offs |
| **Beads** | Task DAG / state | gastownhall/beads | ~24.5k | MIT | Dolt-backed dependency-graph issue tracker (our DAG engine) |
| **PlanDB** | Task DAG / state | Agent-Field/plandb | ~0.09k | Apache-2.0 | Local-first SQLite compound-graph tracker (Beads alternative) |
| **Taskmaster** | Task DAG / planning | eyaltoledano/claude-task-master | ~27.6k | MIT + Commons Clause | PRD→tasks with explicit dependency array + cycle validation |
| **DBOS** | Durable execution | dbos-inc/dbos-transact-* | ~1.2–1.4k | MIT | Postgres-checkpointed durable-execution *library* (not a server) |
| **rf-harness** | Agent-team authoring | revfactory/harness | ~7k | Apache-2.0 | Meta-skill that *generates* Claude Code agent/skill teams |
| **Compound Engineering** | Methodology plugin | EveryInc/compound-engineering-plugin | ~21.5k | MIT | brainstorm→plan→review→**compound** skills/agents (Every) |
| **LiteLLM** | Model gateway | BerriAI/litellm | ~50.5k | MIT (+ enterprise) | Unified gateway/SDK to 100+ LLM providers |
| **nono** | Sandboxing | always-further/nono | ~2.7k | Apache-2.0 | Kernel-enforced, containerless per-agent sandbox (Sigstore team) |

**Recurring alternatives** (cited across multiple categories — the "usual suspects"): **Claude Squad** (parallel terminal-agent manager), **CrewAI** (role-based agent framework), **OpenHands** (open autonomous coder), **LangGraph** (code-defined graph runtime), **Backlog.md** (markdown task manager).

**Added to the library this revision** (also catalogued in `technologies.md`): **Gas Town** (steveyegge/gastown, ~16k⭐, MIT) — an orchestrator built *on Beads*, the most directly relevant reference for our orchestration+Beads layer. Full section below.

---

# Meta-harness / multi-agent orchestration

## Omnigent
- **Repo:** https://github.com/omnigent-ai/omnigent · ~2.1k⭐ · Apache-2.0 · Python · meta-harness / multi-agent orchestration
- **Links:** https://omnigent.ai · [Databricks launch blog](https://www.databricks.com/blog/introducing-omnigent-meta-harness-combine-control-and-share-your-agents)

**What it actually is:** Omnigent is a Databricks-authored (Matei Zaharia, Kasey Uhlenhuth, Corey Zumar) "meta-harness" — a common interface that sits *one level above* individual agent harnesses (Claude Code, Codex, Pi, agent SDKs, custom agents). Its founding observation is that all agent interfaces are fundamentally alike ("messages and files in, text streams and tool calls out"), so they can be wrapped behind a single API and made interchangeable. Released June 2026 in alpha, it bundles cross-device collaborative sessions, a contextual policy engine, and pluggable cloud sandboxes around whatever underlying agent you run. It's a standalone Python platform, not a Claude Code plugin.

**Core features:**
- **Harness abstraction + cross-harness subagents:** define a custom agent once in YAML and port it across harnesses with a one-line change; a *single* agent can mix subagents running on different harnesses (e.g., Claude Code for one, Codex for another) — something no individual harness allows.
- **Contextual policy engine:** governance that tracks dynamic per-session state (not just static allowlists) — e.g., require approval to push code *after* a new package was downloaded; enforces cost budgets, permissions, shell/file/tool access at server/agent/session scopes.
- **OS sandbox + egress proxy:** locks down OS access and intercepts/transforms network requests, so secrets (e.g. a GitHub token) are never visible to the agent and are injected only by the proxy on approved egress.
- **Disposable cloud sandboxes:** runs agents in Modal, Daytona, or Islo.
- **Real-time collaborative sessions:** start in terminal, continue in browser or phone; messages, subagents, terminals, and files stay synced; teams co-drive or fork a live session.
- **Roadmap:** meta-level prompt/agent optimization via GEPA, code-based agent introspection (MemEx/RLM-style), an Omnigent Server MCP.

**What it could bring to our setup:** Omnigent runs *beside* Claude Code as an outer control plane, not inside it — at odds with our "entirely inside Claude Code" constraint — so it's most useful as a **reference** for two layers: (1) its YAML-agent-portable-across-harnesses authoring model and cross-harness subagent composition is a cleaner abstraction than locking subagents to Claude Code natives, and (2) its *stateful* policy engine and egress-proxy secret-injection pattern are a strong blueprint for the sandboxing/governance layer if we ever need guardrails beyond prompt-level permissions.

**Alternatives (OSS, >1000⭐):**
- **Ruflo** (ruvnet/ruflo · ~59.7k⭐ · MIT) — *(also a library entry)* The most-cited "agent meta-harness for Claude," but a maximalist swarm/coordination layer built around Claude Code + MCP rather than a vendor-neutral harness abstraction with sandboxing/policy governance.
- **Claude Squad** (smtg-ai/claude-squad · ~7.6k⭐ · AGPL-3.0 · Go) — TUI managing multiple terminal agents (Claude Code, Codex, Aider, Gemini, OpenCode, Amp) in parallel over tmux + git worktrees. Overlaps the "many agents, many harnesses" promise but is a local parallel-session *runner* — no policy engine, egress sandboxing, collaborative sessions, or portable YAML agent abstraction. (AGPL is a consideration.)

*No other strong OSS match clears the bar: adjacent projects are single-agent harnesses (OpenHands, Devika, Pi), sandbox infra (e2b), or unified-orchestration projects well under 1000⭐.*

## Ruflo
- **Repo:** https://github.com/ruvnet/ruflo · ~59.7k⭐ · MIT · TypeScript · meta-harness / multi-agent orchestration
- **Links:** Docs: [USERGUIDE.md](https://github.com/ruvnet/ruflo/blob/main/docs/USERGUIDE.md) · Web UI: flo.ruv.io · GOAP: goal.ruv.io

**What it actually is:** Ruflo is the renamed **claude-flow** (Reuven Cohen / ruvnet, Agentics Foundation) — the most-starred OSS multi-agent orchestration layer for Claude Code, rebranded Feb 2026 over trademark concerns (the npm/CLI still aliases `claude-flow`). It's not a coding agent; it's a meta-harness on top of Claude Code (and Codex) that turns a single agent into coordinated swarms of 100+ specialized agents with shared memory, self-learning, and cross-machine federation. Installs two ways: lightweight Claude Code plugins (slash commands + agent defs), or a full CLI + MCP server (`npx ruflo init`) exposing ~210 tools. **It leans heavily on self-reported benchmarks** (e.g. 84.8% SWE-bench, 75% cost savings) that should be treated with skepticism.

**Core features:**
- Swarm coordination across hierarchical (Queen/Raft-led), mesh, ring, star, and adaptive topologies, with consensus protocols (Raft, BFT, Gossip, CRDT).
- **GOAP** (Goal-Oriented Action Planning): converts plain-English goals into an A* search over preconditions/actions/dependencies, with adaptive replanning and a live plan-tree dashboard.
- Self-learning memory: AgentDB with HNSW-indexed vector store, SONA neural pattern matching, and a ReasoningBank that persists reasoning patterns across sessions.
- Hooks-based orchestration (27 trigger points) + 12 auto-triggered background workers (audit, optimize, CVE scan, test-gap detection…).
- Zero-trust federation: mTLS + ed25519 identity, behavioral trust scoring, PII detection, HIPAA/SOC2/GDPR modes for cross-machine collaboration.
- Multi-provider routing/failover (Claude, GPT, Gemini, Cohere, Ollama).

**What it could bring to our setup:** Ruflo is the closest existing implementation of the full meta-harness vision, so it's the single best **reference architecture** for swarm topologies, GOAP planning, vector-DB self-learning memory, and hooks-driven orchestration — even if we don't adopt it. It works *with* Claude Code (plugins or MCP server beside it) rather than purely inside it, and its scope (210 tools, federation, 100+ agents) is far heavier than our validated stack; treat it as a feature-superset to mine selectively, especially given the unverified claims and partial-rename rough edges.

**Alternatives (OSS, >1000⭐):**
- **Gas Town** (steveyegge/gastown · ~16k⭐ · MIT · Go) — Steve Yegge's Claude-Code/Copilot/Codex orchestrator with a Mayor→Crew hierarchy and git-backed work tracking. The most directly comparable peer, lighter and more opinionated — and notably **built around Beads** (the same DAG engine in our stack), making it the most relevant reference for the orchestration+Beads layer. *(Strong candidate to add to the library.)*
- **CrewAI** (crewAIInc/crewAI · ~50k⭐ · MIT · Python) — General-purpose role-based multi-agent framework (Crews for autonomy, Flows for production) with MCP and multi-LLM backends. A standalone library for building agent teams from scratch rather than a harness layered on Claude Code; relevant for agent-team/role/topology patterns.
- **Claude Squad** (smtg-ai/claude-squad · ~7.6k⭐ · AGPL-3.0 · Go) — Parallel terminal-agent manager (tmux + worktrees); a session/parallelism manager, not a true orchestrator — no swarm consensus, planning, memory, or auto-merge.

## Symphony
- **Repo:** https://github.com/openai/symphony · ~25k⭐ · Apache-2.0 · Elixir · multi-agent orchestration
- **Links:** SPEC.md in-repo · [announcement](https://openai.com/index/open-source-codex-orchestration-symphony/)

**What it actually is:** OpenAI's open-source orchestration **spec** (plus an experimental Elixir reference implementation) that turns a project-management board — Linear in the demo — into an autonomous control plane for Codex coding agents. Each open issue spawns a dedicated, isolated per-issue workspace ("workpad"); the agent codes, tests, opens a PR, and retires, with a human (or PM agent) steering only by creating/accepting work. It's intentionally minimal and explicitly **not** a maintained product — OpenAI positions it as a reference to fork or have a coding agent rebuild from the spec. Built on the Codex App Server and the same harness primitives (AGENTS.md, hooks, skills, sandbox policies) as the Codex CLI.

**Core features:**
- Task-board-driven autonomy: watches an issue tracker and auto-spawns one isolated agent run per work item; concurrent runs scale across dozens of issues.
- Workpad pattern: each issue gets an isolated workspace (conceptually like worktree isolation).
- Status-driven FSM + `WORKFLOW.md` convention: issue status transitions drive the agent lifecycle declaratively.
- Proof-of-work protocol as the handoff signal: CI status, PR review feedback, walkthrough videos, so humans gate on PR acceptance rather than supervising steps.
- Language-agnostic `SPEC.md`: design is portable; the Elixir impl is just the reference.
- Reported internal impact: ~500% increase in landed PRs in the first three weeks for some OpenAI teams.

**What it could bring to our setup:** Symphony is the closest published **blueprint** for the exact pattern we're building — declarative workflow (`WORKFLOW.md` ≈ our YAML), status-driven state machine (≈ our Beads DAG), per-issue isolation (≈ our worktrees), and PR-acceptance HITL gates with proof-of-work artifacts. A reference to study/adapt rather than adopt: it's tied to the Codex App Server, runs *beside* Claude Code, and the reference impl is Elixir — but the conventions (workpad, status FSM, proof-of-work handoff) port directly.

**Alternatives (OSS, >1000⭐):**
- **OpenHands** (OpenHands/OpenHands · ~77k⭐ · MIT) — The leading open autonomous coding agent; its GitHub Resolver watches issues (label-triggered or batch) and auto-files PRs in a sandboxed Docker env. A full self-hostable agent *runtime* rather than a thin orchestration spec over an external agent server + board.
- **Sweep** (sweepai/sweep · ~7.7k⭐ · Apache-2.0) — Turns labeled GitHub issues into PRs with tests via repo-aware vector search. Same issue→PR async loop but GitHub-issue-centric and single-agent (and has since pivoted toward a JetBrains assistant); lacks Symphony's multi-agent board-as-control-plane and proof-of-work protocol.
- **Claude Squad** (smtg-ai/claude-squad · ~7.6k⭐ · AGPL-3.0) — Shares the isolated-concurrent-runs idea but is a local, human-driven parallelization tool with no task-board intake or proof-of-work/auto-merge gates.

## Factory.ai
- **Site:** https://factory.ai/ · commercial (no public ⭐) · web/desktop app + terminal-native "Droid CLI", model-agnostic
- **Links:** [Missions architecture](https://factory.ai/news/missions-architecture) · [Missions launch](https://factory.ai/news/missions) · [docs](https://docs.factory.ai/cli/features/missions) · [Terminal-Bench SOTA](https://factory.ai/news/terminal-bench)

**What it actually is:** Factory.ai is an "agent-native" software development platform built around **Droids** — autonomous coding agents that run end-to-end SDLC tasks from the terminal or app (posted a then-SOTA 58.75% on Terminal-Bench). Its standout layer is **Missions**: a multi-agent framework where an **orchestrator** decomposes a natural-language goal into milestones/features and delegates to fresh-context **worker** agents, then **validator** agents (code-scrutiny + black-box user-testing) independently verify against a pre-defined "validation contract." Sessions are genuinely long-horizon — median ~2h, 14% exceed 24h, longest reportedly 16 days. It's the closest product-grade realization of the orchestrator→executor→validator pattern we're designing.

**Core features:**
- **Orchestrator/worker/validator/subagent role separation** with strict "separation of concerns and incentives" — each agent has a single goal and clean context, deliberately avoiding an agent validating its own work.
- **Externalized shared state instead of giant contexts** — validation contract, feature list, services config, accumulating knowledge base persisted as artifacts; each agent reads only what its task needs (close to our Beads DAG/state intent).
- **Two-level TDD as a coordination mechanism** — workers write tests before code; orchestrators define behavioral validation contracts before features exist.
- **Serial-with-targeted-parallelism execution** — Factory found broad parallelism counterproductive (agents conflict/duplicate/drift); workers run features sequentially within a milestone, validators fan out in parallel after each milestone, coordination via Git.
- **Model-agnostic** (different models per role); inherits user MCP integrations, skills, lifecycle hooks, custom subagents.
- **Enterprise tier** — SOC-2 Type II, SSO/SAML, private/BYOC, no permanent code storage; usage/token billing.

**What it could bring to our setup:** The closest commercial proof-point for our architecture — validating the Opus-orchestrator → Sonnet-executor → validator topology, worktree/Git isolation, and a persistent externalized-state engine over a spec-defined DAG. Two lessons translate directly: (1) prefer **serial execution with targeted parallelism** over broad fan-out, and (2) make a **pre-committed validation contract** (not the builder's own judgment) the completion gate in our Beads DAG.

**Alternatives (OSS, >1000⭐):**
- **OpenHands** (OpenHands/OpenHands · ~77k⭐ · MIT, sep-licensed `enterprise/`) — Most complete open autonomous software engineer (formerly OpenDevin); single sandboxed agent driving editor/terminal/browser. Research-grade single-agent autonomy without Factory's productized multi-role orchestrator/validator framework.
- **opencode** (anomalyco/opencode · ~175k⭐ · MIT) — Terminal-native, provider-agnostic coding agent, the closest open Droid-CLI analog. Focuses on interactive single-session dev; lacks Missions' milestone decomposition and validation contracts. *(Star count is the agent's API read — unusually high; treat as approximate.)*
- **Cline** (cline/cline · ~63k⭐ · Apache-2.0) — Autonomous agent as a VS Code extension; plan/act modes, MCP, every action human-approved. IDE-embedded and human-in-the-loop by default rather than long-horizon autonomous Missions.
- **goose** (aaif-goose/goose · ~49.5k⭐ · Apache-2.0) — Block-originated editor-agnostic local agent (any LLM + MCP extensions). A single extensible local agent, not a managed orchestrator+worker+validator platform.

## claude-swarm
- **Repo:** https://github.com/affaan-m/claude-swarm · ~0.24k⭐ · MIT · Python · YAML-DAG orchestration (reference implementation)
- **Links:** README/docs in-repo; built for the Claude Code Hackathon, Feb 10–16 2026

**What it actually is:** This is the **Python** claude-swarm by Affaan Mustafa — *not* the well-known Ruby `claude-swarm` by parruda (now `parruda/swarm`, ~1.7k⭐). It's a hackathon-stage multi-agent orchestrator for Claude Code that decomposes a task into a dependency graph of subtasks (topologically sorted via NetworkX), runs independent subtasks in parallel and blocks dependent ones, then runs an Opus quality gate over the merged output. Built directly on the `claude-agent-sdk` (v0.1.35+). Very immature (~7 commits, v0.2.0 dated Feb 11 2026, 44 tests) but essentially a working prototype of our exact target architecture.

**Core features:**
- YAML swarm config: `swarm` (name, max_concurrent, budget_usd, model), `agents` (per-agent description/model/tools/prompt), `connections` (from/to dependency edges) — close to our YAML-into-DAG model.
- NetworkX topological sort over a subtask dependency graph; independent nodes fan out, dependent nodes wait.
- Tiered model selection: Opus for planning (decomposition) + final quality-review gate, cheaper workers (Haiku) for execution — mirrors Opus-orchestrator → cheaper-executor.
- Built on `claude-agent-sdk`; adds file-conflict detection, per-run USD budget enforcement, JSONL session recording/replay, a Rich TUI dashboard.

**What it could bring to our setup:** The single closest existing reference for our YAML → topological-sort DAG → Opus-orchestrator-delegating-to-cheaper-workers design on the `claude-agent-sdk` — a valuable blueprint for the compile-YAML-to-DAG step, dependency-gated parallel dispatch, and the Opus quality gate. It runs *beside* Claude Code as a Python CLI driving SDK subprocesses, and at ~0.24k⭐ / 7 commits is far too immature to depend on — mine it for design, don't adopt it.

**Alternatives (OSS, >1000⭐):**
- **swarm (Ruby "Claude Swarm" by parruda)** (parruda/swarm · ~1.7k⭐ · MIT) — The original, more mature `claude-swarm`: YAML topology where each node is a real Claude Code instance with its own role/tools/dir, connected via MCP; the newer SwarmSDK redesign is single-process Ruby with node workflows + persistent memory. Closest direct analog, but Ruby and MCP-based rather than topo-sort over the agent-SDK.
- **CrewAI** (crewAIInc/crewAI · ~50k⭐ · MIT) — `agents.yaml` + `tasks.yaml` declaratively define agents/tasks; sequential, hierarchical, parallel processes + event-driven Flows. Mature and provider-agnostic, but a general framework, not Claude-Code-specific, with lighter task wiring than an explicit topo-sort DAG.
- **LangGraph** (langchain-ai/langgraph · ~34.5k⭐ · MIT) — Low-level graph orchestration for stateful agents (nodes/edges, checkpointing, HITL). The graph/DAG model overlaps strongly, but graphs are defined in *Python code*, not YAML, and it's model-agnostic rather than a Claude/subagent orchestrator.

## Gas Town
- **Repo:** https://github.com/steveyegge/gastown (mirror: gastownhall/gastown) · ~16k⭐ · MIT · Go (~95%) · multi-agent workspace manager / orchestrator
- **Links:** [docs/overview.md](https://github.com/steveyegge/gastown/blob/main/docs/overview.md) · [Steve Yegge intro](https://steve-yegge.medium.com/welcome-to-gas-town-4f25ee16dd04) · [DoltHub: "A Day in Gas Town"](https://www.dolthub.com/blog/2026-01-15-a-day-in-gas-town/)

**What it actually is:** Gas Town is Steve Yegge's multi-agent **workspace manager built on top of Beads** (his other project) — the closest existing assembly of "an orchestration layer sitting on a Beads DAG engine," i.e. roughly the shape of our own stack. You drive it through a **Mayor**, which is itself a Claude Code instance pre-loaded with full context about your workspace, projects, and agents; you tell the Mayor what you want and it orchestrates a fleet of worker agents (the design target is reliably running 20-30+ agents at once). Work state persists in Git via worktree-based "Hooks" so agents survive crashes and restarts, and Beads provides the underlying issue/memory layer. Reception is mixed-but-respectful: reviewers call it visionary yet note an overwhelming surface of overlapping, idiosyncratic concepts.

**Core features:**
- A themed role hierarchy: **Mayor** (primary coordinator, a Claude Code session), **Rigs** (per-git-repo project containers), **Crew** (your hands-on workspace within a rig), **Polecats** (worker agents with persistent identity but ephemeral sessions), **Witness** (per-rig health monitor) + **Deacon** (cross-rig supervisor daemon), and **Refinery** (a bisecting merge-queue processor).
- **Beads-backed work tracking:** beads are git-backed JSONL issues; **Convoys** bundle multiple beads assigned to agents — convoys labelled "mountain" get autonomous stall detection + smart skip logic for epic-scale execution. Town mail/messaging also rides on beads.
- Severity-routed escalation: agents hitting blockers run `gt escalate`, creating tracked beads routed through Deacon → Mayor → Overseer at CRITICAL/HIGH/MEDIUM (P0/P1/P2).
- A scheduler acting as a config-driven capacity governor for polecat dispatch (batches concurrency to avoid API rate-limit exhaustion).
- Multi-runtime presets (claude, gemini, codex, cursor, auggie, amp, opencode, copilot, pi, omp); Wasteland federation for distributed work; OpenTelemetry.

**What it could bring to our setup:** Gas Town is the single most directly relevant reference we have — it's a working, ~16k⭐ implementation of "orchestrator delegating to worker agents over a Beads task/state graph," exactly the layer we're designing. It's a **hybrid on our core constraint**: you interact *inside* a Claude Code session (the Mayor), but the coordination plane (Deacon, scheduler, Refinery, daemons) runs *beside* as a Go service — more in-Claude-Code-friendly than Omnigent/Symphony/Fabro, but not purely in-process. Best mined for concrete design answers we still owe ourselves: how to structure escalation, merge-queue verification, capacity governing, and persistent-identity/ephemeral-session workers on top of Beads. Caveat: its large, ad-hoc concept surface is a cautionary tale on keeping our own abstraction count small.

**Alternatives (OSS, >1000⭐):**
- **Ruflo** (ruvnet/ruflo · ~59.7k⭐ · MIT) — *(also a library entry)* The other large Claude-Code swarm orchestrator; far more features (GOAP planning, vector memory, federation) but built around its own MCP/hooks runtime and self-reported benchmarks rather than Gas Town's leaner Beads-backed, git-worktree model.
- **Vibe Kanban** (BloopAI/vibe-kanban · ~27k⭐ · Apache-2.0) — Board-driven parallel-worktree runner (now sunsetting / community-maintained). Human-in-the-loop (you triage a board) vs. Gas Town's agent-to-agent Mayor-led autonomy; lighter to adopt but less ambitious coordination.
- **Claude Squad** (smtg-ai/claude-squad · ~7.8k⭐ · AGPL-3.0) — Terminal manager for parallel agents over tmux + worktrees; a session launcher with no Beads-style state graph, escalation, or merge queue.

---

# Workflow definition / orchestration engine

## Fabro
- **Repo:** https://github.com/fabro-sh/fabro · ~1.3k⭐ · MIT · Rust (~87%; TS ~12% web UI) · workflow definition / orchestration engine
- **Links:** https://fabro.sh · docs: https://docs.fabro.sh

**What it actually is:** Fabro is an open-source "dark software factory" that orchestrates AI coding agents through deterministic workflow graphs authored in **Graphviz DOT**, positioning itself between micromanaging an agent line-by-line and blindly accepting a 500-line diff. Each node is a typed stage (agent session, prompt, shell command, conditional, parallel fan-out/merge, human-in-the-loop gate) and the engine walks the graph exactly as written, so the process is diffable, reviewable, and version-controlled. It runs as a persistent single-binary service (REST API + SSE + React web UI, no external database) and even vendors Graphviz via a `graphviz-sys` crate to render DOT→SVG.

**Core features:**
- DOT graph workflow engine: branching, loops, parallelism, human-approval gates as a Graphviz graph rather than imperative code — auditable in PRs.
- CSS-like stylesheets for multi-model routing: route each node to a specific model/provider with automatic fallback chains.
- Cloud sandbox execution via Daytona VMs: snapshot setup, network isolation, auto cleanup; `fabro sandbox ssh` / `preview` for live debugging.
- Git-based checkpointing: every stage commits code + execution metadata to Git branches → resumable, revertible, traceable runs.
- Unified observability: every tool call/agent turn/shell command captured in an event stream, queryable via DuckDB/SQL; automatic per-run retrospectives.
- Single compiled Rust binary, minimal deps, no database.

**What it could bring to our setup:** The most direct external analog to our "YAML workflow compiled into a Beads DAG" layer — it validates DOT-as-graph + stylesheet model-routing + git checkpointing as a deterministic orchestration substrate, and its stylesheet routing is a clean reference for our Opus/Sonnet split. It runs *beside* Claude Code (a standalone Rust service spawning its own agents in Daytona sandboxes), so it's a design reference and a competitor to our compiler/engine rather than something we'd embed; its sandbox model is cloud-VM-based vs. our worktree isolation.

**Alternatives (OSS, >1000⭐):**
- **Symphony** (openai/symphony · ~25k⭐ · Apache-2.0) — *(also a library entry)* OpenAI's board-driven spec + Elixir ref that builds a task DAG and runs an agent per unblocked task. Spec-only (unmaintained as a product) rather than a graph-authored, fully-featured engine with a binary and UI.
- **Roo Code** (RooCodeInc/Roo-Code · ~24k⭐ · Apache-2.0) — In-editor (VS Code) AI dev team where an Orchestrator mode delegates subtasks to specialized modes. Lives inside the editor with LLM-driven (non-deterministic) delegation and no DOT graph, sandbox VMs, or git-checkpoint resume.
- **Vibe Kanban** (BloopAI/vibe-kanban · ~24k⭐ · Apache-2.0; now community-maintained/sunsetting) — Rust Kanban board orchestrating Claude Code/Codex/other agents across parallel git worktrees. A board/worktree task manager for human-driven parallelism, not a deterministic code-as-graph compiler.

*Note: **MS Conductor** (microsoft/conductor) is the closest conceptual peer — deterministic YAML workflows with a DAG dashboard and human gates — but at ~0.25k⭐ falls below the threshold (and is itself a library entry below).*

## MS Conductor
- **Repo:** https://github.com/microsoft/conductor · ~0.25k⭐ · MIT · Python · YAML workflow-definition engine
- **Links:** [Open Source blog (May 14, 2026)](https://opensource.microsoft.com/blog/2026/05/14/conductor-deterministic-orchestration-for-multi-agent-ai-workflows/) · docs in-repo (`docs/configuration.md`)

**What it actually is:** Conductor is Microsoft's 2026 open-source CLI (`conductor run workflow.yaml`) for defining deterministic multi-agent workflows in a single YAML file — **not** the old Netflix Conductor (`conductor-oss/conductor`) nor the Mac-only "Conductor" Claude Code GUI. Its defining idea is removing the LLM from the orchestration loop: routing decisions are made by Jinja2 templates and conditional expressions, so the same inputs always traverse the same path with zero tokens spent deciding what runs next. Announced at the Open Source Summit (May 2026); a deliberately lighter YAML-first surface alongside the heavier Microsoft Agent Framework (MAF) SDK. Still v0.1.x — feature set real but thin and moving fast.

**Core features:**
- **Deterministic, LLM-free routing:** `routes` with `when` clauses, first-match-wins via Jinja2/expressions — branching logic rather than explicit DAG dependency edges; the DAG is derived, not hand-drawn.
- **Step types:** agent (LLM call with prompt/model/structured-output schema), script, set, wait, terminate; plus HITL gates and multi-turn "dialog mode."
- **Parallelism + composition:** static parallel groups, dynamic `for_each` fan-out, reusable sub-workflows with templated `input_mapping`, workflow registries (share/version by short name).
- **Provider-pluggable:** GitHub Copilot SDK, Anthropic Claude, Claude Agent SDK (delegates tools/MCP to the `claude` CLI), custom endpoints (Ollama, vLLM, Azure OpenAI); per-agent model overrides; unified `low/medium/high/xhigh` reasoning-effort abstraction.
- **Web dashboard (`--web`):** zoomable DAG with animated execution edges, live streaming, per-node cost detail, in-browser HITL gates, background mode.
- Auto-injects `AGENTS.md`/`CLAUDE.md`/`copilot-instructions.md`; max-iteration + wall-clock limits; dry-run plan preview.

**What it could bring to our setup:** The closest external analog to our own vision — a version-controlled YAML workflow with deterministic, condition-based routing — making it a strong reference for our YAML schema and route/`when` semantics (vs. our planned Beads DAG with explicit edges). In practice it runs **beside** Claude Code: it shells out to the `claude` CLI as one provider, so adopting it means an external orchestrator driving Claude rather than Opus orchestrating natively. Best treated as design inspiration for the workflow-definition layer (and its dashboard's HITL gates).

**Alternatives (OSS, >1000⭐):**
- **CrewAI** (crewAIInc/crewAI · ~50k⭐ · MIT · Python) — Closest category match: declare agents/tasks in `agents.yaml`/`tasks.yaml` with sequential/hierarchical/parallel processes. Orchestration logic still lives in Python glue (`crew.py`) and routing leans on roles/LLM collaboration rather than token-free `when` routing.
- **Dify** (langgenius/dify · ~135k⭐ · Apache-2.0 w/ commercial conditions · TS/Python) — Production agentic-workflow platform with visual drag-and-drop orchestration, RAG, MCP, human-input nodes, supervisor multi-agent mode. A low-code visual/server platform (GUI-authored) rather than a Git-diffable CLI.
- **Kestra** (kestra-io/kestra · ~27k⭐ · Apache-2.0 · Java) — Declarative YAML, event-driven orchestration; v1.0 added AI-agent tasks + an AI Copilot that generates flow YAML. A broad data/infra/ops orchestrator (1600+ plugins, server-based) that pivoted toward agents, vs. Conductor's narrow agent-native CLI.

*AutoGen, Semantic Kernel, LangGraph, and the OpenAI Agents SDK are frequently cited as peers but are code-first/programmatic rather than YAML-defined.*

---

# Spec / planning layer

## Spec-Kit
- **Repo:** https://github.com/github/spec-kit · ~112k⭐ · MIT · Python · spec-driven development / planning layer
- **Links:** Docs: https://github.github.com/spec-kit/ · [Microsoft dev blog walkthrough](https://developer.microsoft.com/blog/spec-driven-development-spec-kit)

**What it actually is:** GitHub's open-source toolkit (CLI `specify`, installed via `uv`) that operationalizes Spec-Driven Development — specifications, not code, are the durable source of truth, and working code is generated from them through structured multi-step refinement. Agent-agnostic, shipping the same workflow as slash commands across 30+ AI coding environments (Claude Code, Copilot, Gemini, Cursor, Windsurf, Codex, Kiro…). The flow is `constitution → specify → (clarify) → plan → tasks → (analyze) → implement`, each step emitting a Markdown artifact under `.specify/`. Positioned as the disciplined middle ground between "vibe coding" and heavyweight process. Latest v0.10.2 (June 11, 2026).

**Core features:**
- **Seven-command phase-gated flow:** Constitution (principles/governance), Specify (what/why + user stories), Plan (tech stack), Tasks (breakdown), Implement, plus optional Clarify (de-risk) and Analyze (cross-artifact consistency).
- **Constitution as a persistent governance layer** constraining all downstream specs/plans — distinctive vs. lighter tools that only model per-change deltas.
- **Agent-agnostic by design:** one workflow, 30+ agents, switchable with one command; slash-command or skills modes.
- **Artifact-driven grounding:** every phase persists Markdown to `.specify/` (re-readable audit trail — at ~20-40% higher token spend from re-reading).
- **Known limitation — no real DAG:** `tasks.md` is a flat checklist with phase-ordering conventions + `[P]` parallel-safe flags, but NO per-task dependency edges. Open issue [#1934](https://github.com/github/spec-kit/issues/1934) proposes explicit `(depends on T001)` annotations; today there's no dependency graph or sub-agent delegation.

**What it could bring to our setup:** The strongest off-the-shelf planning layer to adapt — its constitution→specify→plan→tasks artifact chain maps cleanly onto our planning front-end, and its `tasks.md` is the natural input to compile into our Beads DAG. Critically, the gap it explicitly does NOT fill (per-task dependency edges, sub-agent delegation — #1934) is exactly what our Beads-DAG + Claude Code subagent layers add — a complementary fit, not an overlap.

**Alternatives (OSS, >1000⭐):**
- **OpenSpec** (Fission-AI/OpenSpec · ~55k⭐ · MIT · TypeScript) — *(also a library entry)* Lightweight, brownfield-first SDD on a propose→apply→archive workflow using per-change "delta specs"; minimalist and change-scoped (no heavy constitution/phase gates), cheaper to run.
- **BMAD-METHOD** (bmad-code-org/BMAD-METHOD · ~49k⭐ · MIT · JS) — *(also a library entry)* Heavyweight multi-agent framework orchestrating 12+ role agents across the SDLC; emphasizes agent-team orchestration over rigorous spec artifacts.
- **Kiro** (AWS, kiro.dev) — Frequently cited as the IDE-native SDD reference (requirements→design→tasks), but **proprietary AWS IDE, not OSS** — listed only as the commonly-referenced "Kiro-style" comparison point.

## OpenSpec
- **Repo:** https://github.com/Fission-AI/OpenSpec · ~55k⭐ · MIT · TypeScript · spec-driven development / planning layer
- **Links:** https://openspec.dev/ · docs: https://openspec.pro/

**What it actually is:** OpenSpec is a lightweight, brownfield-first spec-driven development framework that adds a spec layer so humans and agents agree on *what* to build before any code. Its defining idea is a clean separation between `openspec/specs/` (durable source of truth for current behavior) and `openspec/changes/` (self-contained change proposals, each with `proposal.md`, `design.md`, `tasks.md`, and "delta specs"). Delta specs describe only what changes via semantic markers (`## ADDED / MODIFIED / REMOVED Requirements`) rather than rewriting whole docs, and on archive merge back into the canonical specs — git-like, where specs are "main" and changes are "feature branches with explicit diffs." Positioned as a lighter alternative to Spec-Kit (no Python, ~5-min setup, ~250 vs ~800 lines of spec); works with 20+ tools via slash commands or a fallback `AGENTS.md`.

**Core features:**
- Change-proposal-driven three-phase cycle: Propose (write delta specs) → Apply (implement) → Archive (merge deltas into source of truth, date-stamped).
- Delta specs as the authoring model — only the diff is written, keeping a living, accumulating system spec rather than N isolated feature specs.
- No separate `/tasks` phase; goes straight from proposal to implementation. Tasks are a flat checklist — no dependency edges/DAG.
- Dedicated verification gate (`/opsx:verify`) producing a structured completeness/correctness/coherence report (CRITICAL/WARNING/SUGGESTION).
- "OPSX" slash commands (`/opsx:new`, `:continue`, `:ff`, `:verify`, `:bulk-archive`, `:onboard`) + optional MCP server; broad tool support, no IDE lock-in.

**What it could bring to our setup:** A strong, lighter Spec-Kit alternative for the planning layer — Node-native (no Python), and its delta-spec + archive model would give the harness a persistent, **accumulating** source of truth that compounds across changes rather than one-off feature specs. But like Spec-Kit it stops at a flat task checklist with no dependency graph or sub-agent delegation, so it would still feed into our YAML→Beads-DAG step rather than replace it.

**Alternatives (OSS, >1000⭐):**
- **Spec-Kit** (github/spec-kit · ~112k⭐ · MIT) — *(also a library entry)* GitHub's spec-driven toolkit; four explicit sequential phases. Heavier (Python + `uv`, rigid phase gates) and treats each feature spec as disposable scaffolding rather than maintaining a living system spec; no delta/archive model.
- **BMAD-METHOD** (bmad-code-org/BMAD-METHOD · ~49k⭐ · MIT) — *(also a library entry)* Persona-agent team across the full SDLC with role boundaries and quality gates. Far heavier and more agentic — owns the whole workflow including multi-agent collaboration, whereas OpenSpec is just a thin spec layer.
- **Agent OS** (buildermethods/agent-os · ~4.9k⭐ · MIT) — System for injecting codebase standards and writing better specs; v3 defers spec authoring to Claude Code's plan mode, focusing on standards/context injection. Narrower scope — complements planning rather than providing a full change-proposal/archive lifecycle.

## BMAD (BMAD-METHOD)
- **Repo:** https://github.com/bmad-code-org/BMAD-METHOD · ~49k⭐ · MIT (BMad/BMAD-METHOD are trademarks of BMad Code, LLC) · JavaScript · agentic planning methodology + agent defs
- **Links:** docs/site in-repo; Discord community; v6.x line current (v6.6.0, Apr 2026)

**What it actually is:** BMAD ("Breakthrough Method for Agile AI-Driven Development") is a methodology + a library of Markdown/YAML agent personas that simulate a full agile team (Analyst, PM, Architect, UX, Scrum Master, Dev, QA, Tech Writer — 12+ in v6). Two phases: a **planning phase** where role agents collaboratively produce versioned artifacts (brainstorm → product brief → PRD → architecture → UX spec), and a **development phase** where a Scrum Master agent shards those docs into self-contained "story files" that a Dev agent implements one at a time and a QA agent reviews. Its signature trick is **context engineering**: each agent gets a tightly scoped context and hands off a durable artifact, so the implementation agent reads a single hyper-detailed story rather than whole project history. v6 added "scale-adaptive" planning, "Party Mode" multi-agent discussion, renamed expansion packs to "modules," plus web bundles (Gemini Gems / ChatGPT Custom GPTs).

**Core features:**
- Two-phase flow: artifact-producing planning agents → SM-sharded story files → Dev/QA loop, with handoffs via durable documents (not live context).
- Role-based agent personas as portable Markdown+YAML, IDE-agnostic (Claude Code, Cursor, Codex CLI…).
- **"Story files" as the core unit of work** — self-contained, fully-specified task docs that isolate dev context (conceptually close to a Beads task node).
- Scale-adaptive planning (varies depth by complexity); "Party Mode" multi-persona brainstorming; modules/expansion packs into non-software domains; web bundles offload planning to flat-rate web LLMs.
- Distinctly **NOT a workflow DAG engine** — orchestration is convention/prompt-driven, sequenced by the human + SM agent rather than a compiled dependency graph.

**What it could bring to our setup:** BMAD's role personas and especially its **"story file" pattern** are a strong reference for what our Beads task nodes should contain — fully self-contained, context-isolated work units a Sonnet executor can implement without re-reading the whole repo. Its two-phase planning→dev artifact chain validates our Spec-Kit → YAML-workflow split, and its agent definitions could be lifted as the persona layer for our Opus→Sonnet subagents. The gap it leaves (which our stack fills) is the missing persistent DAG/state engine: BMAD sequences by convention, whereas we compile into a Beads dependency graph.

**Alternatives (OSS, >1000⭐):**
- **Spec-Kit** (github/spec-kit · ~112k⭐ · MIT) — *(also a library entry)* Phase-based Spec→Plan→Tasks→Implement pipeline emitting Markdown artifacts. Lighter, process-phase-based rather than role-persona-based, and tool-agnostic.
- **GSD / Get Shit Done** (gsd-build/get-shit-done · ~48–64k⭐ · MIT) — Lean meta-prompting framework (69 commands, 24 agents) for Claude Code, organized around discuss→plan→execute→verify. Explicitly the low-ceremony anti-BMAD: clean per-task contexts, git-commit-per-task, no heavy role personas. *(Star counts vary across sources/forks.)*
- **MetaGPT** (FoundationAgents/MetaGPT · ~68k⭐ · MIT) — Python multi-agent SDK ("first AI software company") with PM/Architect/Engineer/QA roles driven by encoded SOPs. Same team-simulation philosophy but a runnable framework/library rather than a methodology of prompt definitions; runs its own loop instead of living inside an existing coding agent.
- **CrewAI** (crewAIInc/crewAI · ~50k⭐ · MIT) — Role-based multi-agent orchestration library (sequential or hierarchical-manager modes). Adjacent rather than identical — a general SDK, not a software-dev methodology; competes with our orchestrator layer rather than the planning methodology.

---

# Task DAG / persistent state engine

## Beads
- **Repo:** https://github.com/gastownhall/beads (was steveyegge/beads; 301-redirects) · ~24.5k⭐ · MIT · Go · task DAG / persistent state engine
- **Links:** Docs: https://gastownhall.github.io/beads/

**What it actually is:** Beads (`bd`) is a git-native, dependency-aware issue tracker built by Steve Yegge specifically to give coding agents persistent memory across sessions (his "50 First Dates" problem). Rather than markdown TODO files, work is modeled as a graph of typed-issue nodes with blocking/parent-child/related/discovered-from edges, so agents can plan and execute long-horizon work that survives context resets, branches, and multiple collaborating agents. The current architecture is built on **Dolt** (version-controlled SQL with cell-level merge + native branching); defaults to **embedded mode** (Dolt in-process, `.beads/embeddeddolt/`, single-writer with file locking) with a **server mode** for concurrent multi-agent access. Note the storage shift: earlier versions used SQLite + a `.beads/issues.jsonl` export as the git-syncable interchange; Dolt is now the source of truth with `bd dolt push/pull`.

**Core features:**
- Dependency DAG with `bd ready` surfacing only unblocked tasks; four typed edge semantics (blocks, parent-child, related, discovered-from) richer than a generic two-way link.
- Hash-based IDs (e.g. `bd-a1b2`) eliminate merge collisions when multiple agents/branches create issues concurrently.
- Compaction / "semantic memory decay" summarizing old closed tasks to conserve context window.
- Git-native distributed model, no central server required; offline-first, version-controlled task state traveling with the repo.
- First-class Claude Code integration: `bd setup claude`, a `.claude-plugin` with hooks + an MCP server; `bd init --stealth` to use locally without committing to a shared repo.
- Agent-optimized JSON output; messaging/threading issue types for agent-to-agent coordination.

**What it could bring to our setup:** As our chosen DAG/state engine, Beads is the persistence + scheduling backbone that turns a compiled YAML workflow into durable, dependency-aware task nodes — `bd ready` becomes the natural hand-off point for the Opus orchestrator to dispatch unblocked work to Sonnet executors, while hash IDs + git-syncable state make it safe across worktree-isolated subagents. Its native Claude Code plugin/MCP and embedded mode mean it runs entirely inside Claude Code with no external server, satisfying our core constraint.

**Alternatives (OSS, >1000⭐):**
- **Taskmaster** (eyaltoledano/claude-task-master · ~27.6k⭐ · MIT + Commons Clause · JS) — *(also a library entry)* PRD→tasks/subtasks with dependency ordering + complexity analysis, drop-in via CLI/MCP. PRD/plan-generation-first with file-based (`tasks.json`) state, vs. Beads' version-controlled SQL graph focused on persistent memory + conflict-free multi-agent concurrency.
- **Backlog.md** (MrLesk/Backlog.md · ~5.8k⭐ · MIT · TypeScript) — Markdown-native, git-stored task manager with terminal Kanban + web UI for human+agent collaboration. Exactly the markdown-file approach Beads argues against: tasks are plain `.md` files with status fields rather than a true DAG with `ready`-scheduling, hash IDs, or compaction.

*Several closer design-philosophy clones inspired by Beads exist (Tracer, Trekker, kata, agent-issue-tracker) but are all well under 1000⭐ as of June 2026. **Gas Town** (steveyegge/gastown, ~16k⭐) is an orchestrator built on top of Beads — a peer in the ecosystem rather than an alternative tracker.*

## PlanDB
- **Repo:** https://github.com/Agent-Field/plandb · ~0.09k⭐ · Apache-2.0 · Rust · task DAG / state engine
- **Links:** https://agentfield.ai/plandb · [ARCHITECTURE.md](https://github.com/Agent-Field/plandb/blob/main/docs/ARCHITECTURE.md) · part of the AgentField ecosystem

**What it actually is:** A local-first issue tracker built specifically for AI agents — "Linear/Jira, but for your Claude Code" — shipped as a single Rust binary backed by SQLite, no cloud/accounts/setup. Its distinguishing model is a **compound graph**: tasks recursively contain subtasks to any depth (containment, like nested folders) while dependency edges connect tasks *across* containment boundaries (like symlinks), so a backend subtask can depend directly on a frontend task. The pitch is that agents decompose mid-flight, parallelize across branches, and pivot whole subtrees when an approach fails — the graph tells agents what's independent and safe to run in parallel. Composite/parent tasks auto-complete when all children finish.

**Core features:**
- Compound graph (containment hierarchy + cross-boundary dependency edges) rather than a flat list or simple tree — `split` turns a stuck task into parallel subtasks, `--dep` wires ordering.
- Atomic multi-agent claiming via `plandb go` (prevents two agents claiming the same work); loop is `plandb go` → `plandb done --next`.
- Pre/post conditions (`--pre`/`--post`) surfaced to agents at claim and completion time.
- Critical-path analysis (longest dependency chains / bottlenecks).
- BM25 full-text search across tasks, descriptions, context, notes.
- Multi-interface: CLI, MCP server, HTTP API; installer auto-configures Claude Code (rules + skill), Cursor, Codex, Gemini, Aider, OpenCode, Windsurf.

**What it could bring to our setup:** A near drop-in alternative to Beads for the DAG/state slot, with a genuinely richer **compound graph** — the containment-plus-cross-cutting-dependency model maps cleanly onto a YAML workflow compiled into a task hierarchy, while atomic claiming + critical-path analysis directly support the Opus/Sonnet parallel pattern with worktree isolation. Trade-offs vs. Beads: SQLite single-file local-first (simpler, no Dolt/git-sync, but no built-in multi-branch merge story or memory compaction); installs natively inside Claude Code; but very immature (~0.09k⭐) vs. Beads' battle-tested ecosystem.

**Alternatives (OSS, >1000⭐):**
- **Beads** (gastownhall/beads · ~24.5k⭐ · MIT · Go) — *(also a library entry)* The category-defining git/Dolt-backed dependency-graph tracker; hash-based IDs for zero-conflict multi-agent work, semantic compaction, graph link types — but a flatter DAG rather than PlanDB's compound graph.
- **Taskmaster** (eyaltoledano/claude-task-master · ~27.6k⭐ · MIT + Commons Clause · JS) — *(also a library entry)* PRD-to-tasks engine over a relatively linear list-with-dependencies model (not a true compound graph); more a planning/PRD layer than a concurrency-safe state engine.
- **Backlog.md** (MrLesk/Backlog.md · ~5.8k⭐ · MIT · TypeScript) — Markdown-and-git-native task manager + Kanban; stores tasks as plain markdown files rather than a SQLite graph, and lacks atomic claiming, critical-path, and cross-hierarchy dependencies.

## Taskmaster (Task Master AI)
- **Repo:** https://github.com/eyaltoledano/claude-task-master · ~27.6k⭐ · MIT + Commons Clause · JavaScript/TypeScript · task DAG / planning state engine
- **Links:** https://docs.task-master.dev · npm: `task-master-ai`

**What it actually is:** An AI-powered task-management system that parses a PRD into a structured `tasks.json` and manages the resulting tasks via both a CLI (`task-master`) and a 36-tool MCP server that drops into Cursor, Claude Code, Windsurf, Roo, etc. Each task is a JSON object with a `dependencies: [ids]` array, status, priority, and an arbitrary `metadata` field for external IDs (GitHub/Jira/Linear). It's AI-native at the management layer itself: it can auto-expand a task into subtasks, run a "complexity analysis" pass recommending how much to break work down, and uses a multi-model config (primary / research / fallback roles across Anthropic, OpenAI, Google, Perplexity, xAI, OpenRouter, local).

**Core features:**
- Explicit dependency DAG as `dependencies: [ids]` arrays per task, with `validate-dependencies` / `fix-dependencies` detecting and repairing circular deps (true cycle validation, scoped per tag).
- PRD-to-tasks parsing as the primary entry point, plus AI task expansion into subtasks and complexity-scoring to guide breakdown.
- Tagged Task Lists (v0.16.2+): multiple independent task contexts (per branch/feature), IDs/deps unique and validated within each tag.
- Dual interface: full CLI + MCP server (~36 tools, selectable core/standard/all).
- Pluggable model roles across many providers, decoupling management from any one model.

**What it could bring to our setup:** With Beads, one of the few tools offering a genuine dependency DAG with cycle validation — but its data model is a single AI-generated `tasks.json` rather than Beads' version-controlled Dolt graph (lighter and trivially diffable, but weaker for distributed multi-agent merge and long-horizon memory compaction). Its real differentiator is the **front of the pipeline**: PRD parsing + complexity-driven expansion overlaps directly with our Spec-Kit planning stage, so Taskmaster is more a planning-state alternative to the Spec-Kit→YAML→Beads flow than a drop-in Beads replacement. The Commons Clause license (vs. Beads' plain MIT) is worth noting for any commercial productization.

**Alternatives (OSS, >1000⭐):**
- **Beads** (gastownhall/beads · ~24.5k⭐ · MIT) — *(also a library entry)* Same true-DAG category but stored in version-controlled Dolt (cell-level merge, hash IDs, `bd ready`, semantic compaction). Storage/memory-first and git-distributed rather than PRD/AI-parsing-first; the engine in our validated stack.
- **Backlog.md** (MrLesk/Backlog.md · ~5.8k⭐ · MIT) — Markdown-native git task manager; supports explicit deps (`--dep`) but tasks are `.md` files, with no PRD-parsing or AI complexity analysis.
- **Vibe Kanban** (BloopAI/vibe-kanban · ~24k⭐ · Apache-2.0) — Rust orchestration board running multiple agents in parallel isolated worktrees, with an MCP server exposing the board. An orchestration/execution layer (worktree-per-task) rather than a true dependency-DAG state engine; reportedly being sunset but staying open source.

## DBOS (DBOS Transact)
- **Repo:** [dbos-transact-py](https://github.com/dbos-inc/dbos-transact-py) (~1.4k⭐) · [dbos-transact-ts](https://github.com/dbos-inc/dbos-transact-ts) (~1.2k⭐) · also Java/Go SDKs · MIT · durable execution engine / library
- **Links:** https://www.dbos.dev/ · https://docs.dbos.dev/ · [architecture](https://docs.dbos.dev/architecture)

**What it actually is:** DBOS Transact is an open-source **library** (not a server) embedded in your app to make functions durable: annotate `@DBOS.workflow()` and `@DBOS.step()`, point it at Postgres, and it checkpoints workflow status, inputs, and each step's output to Postgres as it runs. After a crash/restart it replays the workflow from its last completed checkpoint. The distinctive trick is "piggybacking" the checkpoint into the same Postgres transaction as a step's DB write (via `@DBOS.Transaction`), yielding exactly-once execution for database steps — non-DB steps default to at-least-once and should be idempotent. Because a checkpoint is a ~1ms Postgres write rather than a network round-trip to an external orchestrator, DBOS claims ~25x lower latency than dispatch-based engines like AWS Step Functions.

**Core features:**
- Library-not-server architecture: durable execution as in-process function calls, single deployable service, "your durability = your Postgres's durability" — explicit contrast with Temporal's separate cluster + worker rearchitecture.
- Workflow/step checkpointing to Postgres with automatic crash recovery from the last completed step.
- Exactly-once semantics for transactional (DB) steps via checkpoint-in-transaction; idempotency keys for whole-workflow dedup.
- Durable, Postgres-backed queues (concurrency/rate limits, timeouts, priority) — no separate broker.
- Durable cron scheduling, long sleeps, exactly-once event/webhook processing; workflows are queryable Postgres rows that can be paused/resumed/restarted.
- Multi-language SDKs (Python, TS, Java, Go), all MIT.

**What it could bring to our setup:** The natural candidate for the **durability/resumability layer** — an Opus orchestrator wrapped in `@DBOS.workflow()` would survive crashes and resume mid-DAG without re-running completed subagent steps, with state in the same Postgres that could back Beads. **Open question:** Beads already gives the DAG + ready-set and persists task state, so DBOS is only justified if we need *in-flight orchestrator process* resumability (surviving a crash mid-step) beyond Beads' restartable task graph; if a crashed run can simply be re-derived from `bd ready`, DBOS may be redundant. Also note its strongest guarantee (exactly-once) applies to DB steps, whereas LLM/subagent calls are non-transactional and need idempotency handling regardless.

**Alternatives (OSS, >1000⭐):**
- **Temporal** (temporalio/temporal · ~21k⭐ · MIT) — The market leader, the architectural opposite of DBOS: a separate orchestration cluster (frontend/history/matching + datastore) your code connects to as workers/clients. Heavier footprint and network hops per step, but battle-tested for long-running, polyglot, enterprise-scale workflows with full deterministic replay.
- **Hatchet** (hatchet-dev/hatchet · ~7.4k⭐ · MIT) — Postgres-backed orchestration engine for background tasks, AI agents, and durable workflows; doubles as task queue + DAG orchestrator. Shares the Postgres-durability choice but runs as a self-hosted server you deploy and connect workers to, rather than an embedded library.
- **Restate** (restatedev/restate · ~4k⭐ · BSL-1.1, converts to OSS after ~4y) — Single-binary durable-execution platform running as a lightweight sidecar intercepting service calls; adds durable "virtual objects" + state. Lighter than Temporal but still a separate runtime, unlike DBOS's pure in-process library.
- **Inngest** (inngest/inngest · ~5.5k⭐ · SSPL server/CLI w/ delayed Apache-2.0; SDKs Apache-2.0) — Event-driven "durable functions" (event/cron/webhook triggers, step retries, flow control). Event/server-oriented and not strictly OSS at the server tier.

---

# Agent-team authoring / methodology

## rf-harness (revfactory/harness)
- **Repo:** https://github.com/revfactory/harness · ~7k⭐ · Apache-2.0 · agent-team authoring / generation factory
- **Links:** Docs: https://revfactory.github.io/harness/ · Companion: [revfactory/harness-100](https://github.com/revfactory/harness-100) (~1k⭐)

**What it actually is:** A Claude Code **meta-skill** (marketplace plugin or global skill) that takes a natural-language domain description ("build a harness for this project") and emits a tailored multi-agent team as `.claude/agents/` + `.claude/skills/` files. It's an **authoring/generation tool, not a runtime orchestrator** — its output is the artifacts you then run, not a running engine. The author positions it at an "L3 Meta-Factory / Team-Architecture Factory" layer: a thing that generates harnesses rather than being one. An author-run A/B study (n=15) claims +60% average quality, 15/15 win-rate, −32% variance (**self-reported, not independent**).

**Core features:**
- Six-phase generate→validate workflow: Domain Analysis → Team Architecture Design → Agent Definition Generation → Skill Generation → Integration & Orchestration → Validation & Testing (trigger verification, dry-run, with/without-skills comparison).
- Picks one of 6 team-architecture patterns from the prompt: Pipeline, Fan-out/Fan-in, Expert Pool, Producer-Reviewer, Supervisor, Hierarchical Delegation.
- Two execution modes: Agent Teams (default; wires TeamCreate/SendMessage/TaskCreate, needs `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`) or one-off Subagents.
- Generates agents with explicit roles/principles/I/O protocols/team-comm contracts, and skills with YAML frontmatter, "pushy" trigger descriptions, and Progressive Disclosure.
- Companion `harness-100`: 100 ready-made team configs across 10 domains (EN+KO), each 4-5 specialists + orchestrator skill — usable as templates.
- Claude Code native (no Gemini/Codex out of the box).

**What it could bring to our setup:** Directly addresses the hand-authoring pain in our flow — instead of writing `.claude/agents/` and `.claude/skills/` by hand, we could use its 6-phase generate→validate process to bootstrap first-draft executor agents and skill stubs from a spec, then refine. Its pattern catalog (Pipeline / Producer-Reviewer / Supervisor / Hierarchical) maps onto our Opus→Sonnet model, and `harness-100` offers ready templates. Caveat: it generates Agent-Teams-style artifacts (TeamCreate/SendMessage), so output would need adapting to our subagent + Beads-DAG runtime.

**Alternatives (OSS, >1000⭐):**
- **claude-code-templates / aitmpl** (davila7/claude-code-templates · ~28k⭐ · MIT) — CLI + web catalog (aitmpl.com) installing 1000+ pre-built Claude Code components. A package-manager/scaffolder distributing ready-made definitions rather than synthesizing a coordinated team from free text.
- **wshobson/agents** (wshobson/agents · ~37k⭐ · MIT) — Multi-harness agentic plugin marketplace (Claude Code, Codex, Cursor, OpenCode, Copilot, Gemini) supplying curated agent definitions. Marketplace-oriented; provides agents to install, not a generator that designs a bespoke team per domain.
- **VoltAgent/awesome-claude-code-subagents** (VoltAgent/awesome-claude-code-subagents · ~22k⭐ · MIT) — 100+ curated subagent catalog with a companion browse/fetch skill. A library of hand-written subagents, whereas rf-harness *generates and validates* new mutually-wired agent+skill sets.

*Tools in rf-harness's exact niche — prompt-to-agent-team **generators** — are mostly well under 1000⭐ (skill-builder 109⭐, claude-code-skill-factory 802⭐). rf-harness appears fairly distinctive in actually synthesizing + validating a coordinated team from a single prompt.*

## Compound Engineering plugin (Every)
- **Repo:** https://github.com/EveryInc/compound-engineering-plugin · ~21.5k⭐ · MIT · TypeScript · workflow/methodology plugin
- **Links:** [Definitive Guide](https://every.to/source-code/compound-engineering-the-definitive-guide) · [Guide](https://every.to/guides/compound-engineering) · [How Every Codes With Agents](https://every.to/chain-of-thought/compound-engineering-how-every-codes-with-agents)

**What it actually is:** A Claude Code/Cursor/Codex/Copilot plugin (37 skills + 51 agents) by Dan Shipper and Kieran Klaassen of Every, operationalizing their "compound engineering" methodology: the inversion that each unit of work should make the next *easier*. Instead of features accumulating complexity and tech debt, the system accumulates **capability** — bug fixes eliminate categories of future bugs, and codified patterns become reusable tools for future agents. The distinctive move is its explicit time-allocation discipline (~Plan 40% / Work 20% / Review 20% / Compound 20%) and research-first planning that mines codebase history, docs, and external best practices before any code. Every uses it to run five real in-house products each maintained primarily by a single engineer.

**Core features:**
- Structured loop strategy → ideate/brainstorm → requirements → plan → work → debug → review → **compound**, as `/ce-strategy`, `/ce-brainstorm`, `/ce-plan`, `/ce-work`, `/ce-debug`, `/ce-code-review`, `/ce-compound` (+ `/ce-product-pulse`).
- The `/ce-compound` step is the differentiator: codifies learnings, discovered patterns, and mistakes-to-avoid into persistent docs that future agents read — turning each session into a knowledge-capture event.
- Research-first, planning-heavy bias backed by a standardized plan document (context, research findings, testable acceptance criteria, implementation steps).
- Multi-agent code review and judgment calibration; cross-tool (not Claude-locked).

**What it could bring to our setup:** Essentially a productized, battle-tested version of our brainstorm→plan→review→learn loop — the `/ce-compound` knowledge-capture phase directly models our "learn" stage and is the part most harnesses omit, so its skill/agent prompts are a strong reference for designing our Beads "learning doc" artifacts. The planning-heavy time allocation and standardized plan-document schema map onto our Spec-Kit planning → YAML→Beads compile step, and since it runs natively as Claude Code skills/agents it fits the "inside Claude Code" constraint with minimal adaptation.

**Alternatives (OSS, >1000⭐):**
- **Superpowers** (obra/superpowers · six figures⭐ · MIT) — Jesse Vincent's agentic skills framework + methodology; auto-triggering composable skills (Socratic brainstorming, TDD, systematic debugging, verification-before-completion) with `/brainstorm`, `/write-plan`, `/execute-plan` and subagent-driven execution. More TDD/discipline-enforcement and skill-centric rather than centering a knowledge-*compounding* phase; the closest direct peer. *(Exact star count is noisy across sources — unambiguously six figures.)*
- **BMAD-METHOD** (bmad-code-org/BMAD-METHOD · ~49k⭐ · MIT) — *(also a library entry)* Persona-agent team across the SDLC; models a full agile-team role simulation rather than a single solo-engineer compounding loop. Heavier and more ceremony-oriented.
- **Get Shit Done (GSD)** (gsd-build/get-shit-done · ~48–64k⭐ · MIT) — Lightweight meta-prompting / context-engineering / spec-driven system with phase-based planning and progress tracking. Deliberately lightweight rather than a skills+agents suite with a dedicated learning phase.
- **Agent OS** (buildermethods/agent-os · ~4.9k⭐ · MIT) — System for injecting codebase standards and writing better specs. Narrower — focuses on standards-injection and spec quality (the planning front-end) rather than an end-to-end execute-review-compound loop.

---

# Cross-cutting infrastructure

## LiteLLM
- **Repo:** https://github.com/BerriAI/litellm · ~50.5k⭐ · MIT (core) + proprietary `enterprise/` dir · Python · model gateway
- **Links:** https://docs.litellm.ai · https://models.litellm.ai

**What it actually is:** LiteLLM ships in two forms that share one normalization layer: a Python **SDK** that translates calls to 100+ providers into OpenAI-compatible request/response shapes, and a self-hosted **Proxy Server** (the "AI Gateway") that wraps that layer in a multi-tenant HTTP service. The SDK already includes the `Router` (retry, fallback chains, load balancing across deployments, app-level cost tracking) — so routing/failover is NOT proxy-exclusive. The Proxy adds the governance plane: virtual keys, per-key/team/user/tag budgets, rate limits, an admin dashboard, and logging callbacks (Langfuse, MLflow…). The de facto default OSS gateway, with the broadest provider coverage in the category.

**Core features:**
- Widest provider breadth (100+ APIs) normalized to OpenAI format.
- Router with automatic fallback chains, retries, load balancing — available in the SDK alone, no proxy required.
- Proxy-only governance: virtual keys, hierarchical budgets (key/team/user/tag) with reset durations, rate limits, spend tracking, admin UI.
- Split licensing: MIT core fully self-hostable; SSO, RBAC, audit logs, enhanced UI behind the paid Enterprise tier.

**What it could bring to our setup:** If the harness ever needs provider failover (e.g. Anthropic → Bedrock Claude during an outage) or hard cost ceilings beyond what Claude Max gives us, a single self-hosted LiteLLM Proxy in front of our Sonnet executors would centralize budget caps, per-agent virtual keys, and spend telemetry without touching agent code. Only worth pulling in once we want to break Claude-only lock-in or enforce budgets — for a pure-Claude-Max setup it's optional infrastructure.

**Alternatives (OSS, >1000⭐):**
- **Portkey Gateway** (Portkey-AI/gateway · ~11.5k⭐ · Apache-2.0) — TypeScript gateway routing to 1,600+ LLMs with integrated guardrails, ~1ms latency, tiny footprint; "Gateway 2.0" (Mar 2026) open-sourced previously-SaaS features (circuit breakers, MCP gateway, usage policies). More performance/guardrail-focused than LiteLLM's Python ergonomics.
- **Bifrost** (maximhq/bifrost · ~5.8k⭐ · Apache-2.0) — Go gateway marketed as a high-performance LiteLLM alternative (claimed ~50x lower overhead, sub-100µs at 5k RPS) with adaptive load balancing, cluster mode, semantic caching, MCP gateway. Fewer providers (~23) and smaller community; chosen when the gateway itself is a latency bottleneck.
- **TensorZero** (tensorzero/tensorzero · ~11.6k⭐ · Apache-2.0) — Rust LLMOps stack unifying a sub-1ms gateway with observability, evaluation, optimization, experimentation; no paid-feature gating. **Caveat:** the repo was archived (read-only) June 12, 2026 — a reference point rather than a live option.

*Others often cited (OpenRouter, Helicone, Kong AI Gateway) are either not OSS gateways in the same self-hosted-router sense or are observability/API-management adjacent.*

## nono
- **Repo:** https://github.com/always-further/nono · ~2.7k⭐ · Apache-2.0 · Rust · sandboxing / isolation layer
- **Links:** https://nono.sh · docs: https://nono.sh/docs · [blog](https://alwaysfurther.ai/blog/why-i-built-nono)

**What it actually is:** nono is a **kernel-enforced capability sandbox** for AI agents and any POSIX process, built by Luke Hinds and the team behind Sigstore. Unlike policy/filter sandboxes, it uses OS security primitives directly — **Landlock** on Linux/WSL2 and **Seatbelt** (sandbox-exec) on macOS — to create irrevocable, kernel-enforced allow-lists with no daemon, container, VM, or disk overhead, so unauthorized filesystem/network operations are structurally impossible rather than merely intercepted. The pitch is that this resists prompt-injection by design (a kernel rule can't be talked around like a filter), and once restrictions apply there's no escape API — not even for nono itself. Its second pillar is supply-chain provenance: it ties into Sigstore keyless attestation so agent instruction files (CLAUDE.md, SKILLS.md, AGENT.md) are verified at the kernel level before the agent can read them.

**Core features:**
- Containerless kernel isolation: Landlock (Linux/WSL2) + Seatbelt (macOS), zero setup/latency, inherited by all child processes and irreversible from inside.
- Least-privilege defaults: SSH keys, AWS/cloud creds, shell configs blocked by default; env-var filtering; dangerous-command interception (e.g. `rm`) as defense-in-depth.
- L7 network filtering via a localhost micro-proxy: host allowlist, hardcoded deny of cloud-metadata (169.254.169.254)/RFC1918/loopback, DNS-rebinding protection, full audit logging; selective TLS interception for CONNECT.
- Credential injection ("phantom token"): agent sends a dummy 256-bit token, proxy swaps in the real key from the OS keyring before forwarding — the agent never sees the secret.
- Supervisor mode (Linux seccomp user-notification): pauses on out-of-policy syscalls, prompts for authorization, injects the fd so the agent never executes `open()` itself; TOCTOU re-verification.
- Profile registry (registry.nono.sh): per-agent profiles bundling filesystem scope + network allowlist + hooks/skills, composable via `--extends`, published with Sigstore keyless signing and verified client-side.
- Content-addressable snapshots (SHA-256 + Merkle tree) for rollback; Rust core with FFI for Python/TS/Go.

**What it could bring to our setup:** A near-perfect fit for the "per-agent isolation outside claudebox" slot — where **claudebox** is a heavyweight Docker sandbox at the environment level, **nono** gives per-agent, per-worktree least-privilege confinement with zero container overhead, so each Sonnet executor could get a tight filesystem/network scope (and hidden creds) without spinning up a VM. Its profile registry + composable allowlists map onto our YAML-workflow-driven agents, and the credential-injection proxy plus Sigstore attestation of CLAUDE.md/skill files would harden orchestrator→executor delegation against prompt injection and tampered instruction files in a way claudebox doesn't address.

**Alternatives (OSS, >1000⭐):**
- **microsandbox** (zerocore-ai/microsandbox · ~6.6k⭐ · Apache-2.0) — Self-hosted hardware-isolated microVM sandboxes for AI agents (~150ms boot, runs OCI images, built-in MCP, multi-language SDKs). Stronger isolation boundary (separate kernel per sandbox) but heavier and VM-based, where nono's whole premise is no VM/container.
- **gVisor** (google/gvisor · ~18.5k⭐ · Apache-2.0) — Google's application kernel (`runsc` OCI runtime) intercepting syscalls in a userspace Go kernel; used in production at Google, Anthropic, OpenAI, Cloudflare. A much stronger third-way isolation model than allow-lists, but container/OCI-oriented infrastructure, not a zero-setup per-command wrapper.
- **bubblewrap** (containers/bubblewrap · ~7.5k⭐ · LGPL-2.0+) — Low-level unprivileged sandboxing (the engine behind Flatpak) using namespaces. Same containerless-on-Linux spirit, but a primitive with no built-in policy, no macOS, no network L7/credential layer, no agent profiles.
- **firejail** (netblue30/firejail · ~7.2k⭐ · GPL-2.0) — Lightweight SUID sandbox using namespaces, seccomp-bpf, capabilities with bundled per-app profiles. Closer to nono's "wrap a process" ergonomics, but Linux-only, SUID-based, desktop-app focused; lacks the L7 proxy, credential injection, and supply-chain attestation.

---

## Notes & open threads for design

- **Inside vs. beside Claude Code.** Most orchestration/workflow positions (Omnigent, Symphony, Fabro, MS Conductor; Ruflo partially) run *beside* Claude Code. **Gas Town** is a hybrid (driven through a Claude Code Mayor, coordinated by a Go daemon beside). Of the field, **claude-swarm**, the **Compound Engineering** plugin, **rf-harness**, **Beads**, **PlanDB**, and **nono** are the ones that genuinely live inside / native to the Claude Code session — the most constraint-compatible references.
- **Gas Town is the closest pre-built analog to our stack.** Steve Yegge's orchestrator built *on Beads* — "our orchestration layer + our chosen DAG engine, already assembled." Now catalogued; the prime candidate to study directly for escalation, merge-queue, capacity-governing, and worker-lifecycle design (and a cautionary tale on abstraction sprawl).
- **The DAG question is essentially three tools.** Beads (chosen), PlanDB (richer compound graph, immature), Taskmaster (PRD-first, Commons Clause). DBOS sits orthogonally as durability, not a DAG.
- **Recurring cross-category players** (Claude Squad, CrewAI, OpenHands, LangGraph, Backlog.md) keep surfacing — useful shortlist if we ever want a single off-the-shelf tool spanning layers, though each is a partial fit.
- **License flags for productization:** Claude Squad (AGPL-3.0), Taskmaster (Commons Clause), Dify/Inngest/Restate (source-available tiers), LiteLLM (enterprise features proprietary).
- **Verify before depending:** several star counts here are single-source June-2026 reads (notably opencode's ~175k and the various GSD/Superpowers figures); re-verify before any adoption decision.
