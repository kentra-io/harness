# Technologies

A compilation of references relevant to the agentic coding harness — some we'll use directly, others we'll draw on as inspiration.

## Fabro
- [fabro-sh/fabro](https://github.com/fabro-sh/fabro) (~960 stars, MIT)
- Open-source platform that orchestrates AI coding agents through defined workflow graphs — a middle path between micromanaging agents and reviewing massive unvetted diffs
- Deterministic workflow graphs defined in Graphviz DOT, with branching, loops, and human-in-the-loop approval gates
- Multi-model routing via CSS-like stylesheets to optimize cost and performance
- Cloud sandbox execution with network isolation; git-based checkpointing for resumable runs
- REST API with web UI and SSE streaming
- Single compiled Rust binary, no runtime dependencies (deployable via Docker or standalone CLI)

## Omnigent
- [omnigent-ai/omnigent](https://github.com/omnigent-ai/omnigent) (~1.6k stars, Apache-2.0, Python) · [omnigent.ai](https://omnigent.ai)
- Candidate for the **multi-agent orchestration layer**. "A meta-harness for all your AI agents" — a common layer over Claude Code, Codex, Pi, and custom agents: swap or combine harnesses without rewriting
- YAML-based agent definitions (prompts, tools, sub-agents); agents can author new agents; runtime harness switching via `/model`
- Policy-driven governance: approval gates for risky actions, spending caps, tool-access limits at server/agent/session levels; sandboxing
- Real-time collaboration: sessions sync across devices; teammates watch live, co-drive, or fork sessions
- Cloud sandbox execution via Modal, Daytona, Islo; web-first UI with mobile support
- **Yet to research:** how a meta-harness layer plays into our "entirely inside Claude Code" vision (it sits *above* harnesses). Compare with Symphony below.

## Ruflo
- [ruvnet/ruflo](https://github.com/ruvnet/ruflo) (~60k stars, MIT, TypeScript) · [Cognitum.One](https://Cognitum.One)
- Another **multi-agent orchestration / meta-harness** candidate (cf. Omnigent, Symphony) — "the leading agent meta-harness for Claude": native Claude Code / Codex integration via a plugin system (slash commands, agent defs, MCP server registration for memory + swarm init + agent spawning)
- Multi-agent **swarms**: 100+ specialized agents (coder, tester, reviewer, architect, security) with hierarchical/mesh/adaptive topologies, queen-led hierarchy, and Raft/Byzantine consensus
- Workflow definition via **GOAP** (Goal-Oriented Action Planning) — converts plain-English goals into executable A* state-space plans
- Self-learning memory: persistent vector DB (AgentDB + HNSW) with SONA neural patterns and ReasoningBank, so agents retrieve past solutions and improve over time
- Federation across machines (mTLS + ed25519 identity, PII-stripping, trust scoring); multi-provider LLM routing (Claude/GPT/Gemini/Cohere/Ollama) with failover; background workers + 32+ plugins
- **Yet to research:** like Omnigent it's a meta-harness layer — far larger scope (swarms, federation, learning memory). Fit with the "inside Claude Code" vision and whether its native CC plugin model sidesteps the meta-harness/constraint tension is open.

## Symphony
- [openai/symphony](https://github.com/openai/symphony) (~25k stars, Apache-2.0, Elixir) · [announcement](https://openai.com/index/open-source-codex-orchestration-symphony/)
- OpenAI's take on **multi-agent orchestration** — similar problem space to Omnigent; "turns project work into isolated, autonomous implementation runs, allowing teams to manage work instead of supervising coding agents"
- Watches a task board (Linear in the demo) and auto-spawns agents per work item; agents run isolated, concurrent implementation cycles
- Proof-of-work per run: CI status, PR review feedback, complexity analysis, walkthrough videos; human-in-the-loop via PR acceptance gates (manage work strategically, not agent actions)
- Built on "harness engineering" principles: a language-agnostic spec (`SPEC.md`) + an experimental Elixir reference implementation; teams encouraged to build their own
- **Yet to research:** fit with our vision; useful as a reference for the work-board-driven, isolated-run orchestration model. Contrast with Omnigent (meta-harness/common-layer) vs. Symphony (work-management/spec-driven).

## `rf-harness` (revfactory/harness) — *team-architecture factory, not an orchestrator*
- [revfactory/harness](https://github.com/revfactory/harness) (~7k stars, Apache-2.0) · [docs](https://revfactory.github.io/harness/)
- ⚠️ **Namespacing:** name collides with *our* project, which we call **`harness`** (this repo, `kentra/harness`). To disambiguate, refer to this external project as **`rf-harness`** throughout our docs. It's a different layer: a **meta-skill that *designs* domain-specific agent teams** and generates the skills/agent defs they use — an authoring/generation tool, not a runtime orchestration layer like Omnigent/Symphony/Ruflo
- From a natural-language prompt, picks one of 6 team-architecture patterns (Pipeline, Fan-out/Fan-in, Expert Pool, Producer-Reviewer, Supervisor, Hierarchical Delegation) and emits `.claude/agents/` + `.claude/skills/` files
- Claude Code native: runs as a marketplace plugin / global skill; uses Agent Teams (TeamCreate/SendMessage/TaskCreate) or one-off Subagents mode; 6-phase generate→validate workflow
- Companion `harness-100`: 100 ready-made agent-team configs across 10 domains; author-measured "+60% quality" A/B test (n=15)
- Self-positions as an "L3 Meta-Factory" coexisting with Archon (runtime config), a Codex meta-harness port, and an ECC standardization layer
- **Yet to research:** could be useful for *bootstrapping* our agent/skill definitions (it generates the artifacts we hand-author), distinct from the orchestration-layer question

## Factory.ai
- [factory.ai](https://factory.ai/) (commercial product)
- Agent-native software development platform — closest commercial analog to what we're building; useful as a reference for product direction
- Powered by "Droids" — autonomous AI agents that handle code generation and engineering tasks
- "Mission"-based development workflow
- Desktop app + CLI (curl-installed)
- Enterprise tier with security compliance (SLA, DPA, BAA)
- Talk: ["The Multi-Agent Architecture That Actually Ships"](https://www.youtube.com/watch?v=ow1we5PzK-o) by Luke Alvoeiro (Factory) — presentation on their workflow approach

## LiteLLM
- [BerriAI/litellm](https://github.com/BerriAI/litellm) (~50k stars, MIT, Python) · [docs](https://docs.litellm.ai/docs/)
- **Model-gateway layer**: one unified interface to call 100+ LLM providers (OpenAI, Anthropic, Gemini, Bedrock, Azure, Vertex, Cohere, vLLM, …) in OpenAI-compatible (or native) format
- Two forms: a Python **SDK** for direct integration, and a self-hosted **AI Gateway / proxy server** for centralized team access
- Production features: routing with retry/fallback across deployments, load balancing, spend/cost tracking, rate limiting, **virtual keys with budget controls**, admin dashboard
- Used by Stripe, Google, Netflix, 22k+ projects; YC W23
- **Yet to research:** how a multi-provider gateway fits our (Claude-centric) harness — likely relevant if we want provider failover, cost/budget controls, or per-agent model routing without coupling to one vendor. Overlaps the routing features baked into MS Conductor / Ruflo / Omnigent

## MS Conductor
- [microsoft/conductor](https://github.com/microsoft/conductor) (~250 stars, MIT, Python)
- **YAML workflow-definition layer** candidate: a standalone CLI (`conductor run workflow.yaml`) for multi-agent workflows defined in plain YAML — "repeatable, deterministic, version-controlled," conditional routing instead of LLM-decided orchestration
- Routing uses **routes / `when` conditions** (Jinja2 templates, first-match-wins) rather than explicit `dependencies:[A]` DAG edges; step types: agent, script, set, terminate, wait; parallel exec (static groups + dynamic for-each), human-in-the-loop gates
- Providers: GitHub Copilot SDK, Anthropic Claude (API key), Claude Agent SDK (via `claude` CLI); per-agent model overrides; custom endpoints (Ollama, Azure OpenAI, vLLM); unified `reasoning.effort`
- Web dashboard with interactive DAG graphs + live streaming; auto-discovers `AGENTS.md`/`CLAUDE.md`; workflow registries; pre-runtime validation; background mode
- ⚠️ Already discussed in [workflow-orchestration-analysis.md](../workflow-orchestration-analysis.md): the YAML-first option, but **runs as a separate CLI *beside* Claude Code (not inside it)** and uses route/state-machine semantics rather than explicit DAG edges — in tension with the "entirely inside Claude Code" constraint. (Metadata now ~250⭐/MIT/Python, superseding the stale "146⭐/v0.1.1" note referenced there.)

## Spec-Kit
- [github/spec-kit](https://github.com/github/spec-kit) (~112k stars, MIT, Python) · [docs](https://github.github.com/spec-kit/)
- The **spec layer**: GitHub's toolkit for Spec-Driven Development — specifications are the source of truth that generate code, via structured multi-step refinement (not single-prompt)
- 7 commands: Constitution (principles), Specify, Plan (tech choices), Tasks, Implement, plus optional Clarify and Analyze
- Integrates with 30+ agents (Claude Code, Copilot, Gemini CLI, Cursor, …); slash-command or skills-mode; artifacts version-controlled under `.specify/`
- ⚠️ Already analyzed in [workflow-orchestration-analysis.md](../workflow-orchestration-analysis.md): provides the spec/`tasks.md` checklist but **no DAG / dependency edges / sub-agent delegation** — the orchestration layer is a separate concern

## Beads
- [gastownhall/beads](https://github.com/gastownhall/beads) (~24k stars, MIT, Go) · [docs](https://gastownhall.github.io/beads/)
- The **DAG / state engine** (validated choice in our analysis): a distributed graph issue tracker built on Dolt (version-controlled SQL), giving agents persistent dependency-aware task graphs instead of markdown plans
- DAG + ready-set: `bd dep add <child> <parent>` creates blocking edges; `bd ready` surfaces only unblocked tasks. Core CLI: `bd create`, `bd update --claim` (atomic), `bd show`, `bd close`, `bd prime` (inject context), `bd remember` (persistent memory)
- State in `.beads/` (embedded Dolt default, or external server for multi-writer); hash-based IDs avoid merge collisions; `bd dolt push/pull` syncs via Git remotes
- ⚠️ Already the chosen runtime DAG engine in [workflow-orchestration-analysis.md](../workflow-orchestration-analysis.md) (YAML→Beads→subagents stack)

## PlanDB
- [Agent-Field/plandb](https://github.com/Agent-Field/plandb) (~90 stars, Apache-2.0, Rust) · [agentfield.ai](https://agentfield.ai/)
- **DAG/state engine alternative to Beads** — local-first issue tracker for agents ("Linear/Jira, but for your Claude Code"), single Rust binary + SQLite, zero cloud infra
- Compound graph: containment (recursive subtasks) **+** dependency edges crossing the hierarchy; pre/post conditions, atomic multi-agent claiming, critical-path analysis, BM25 search over task context
- Agent loop: `plandb go` (claim ready task) → `plandb done --next` (complete + surface next); auto-parallelizes independent work
- Installs as always-on rules file + skill for Claude Code (auto-config for Cursor/Codex/Gemini/Aider); also MCP server + HTTP API
- **Yet to research:** trade-offs vs. Beads (Dolt/Git-sync graph vs. SQLite local-first); smaller/newer (~90⭐)

## Compound Engineering plugin (Every)
- [EveryInc/compound-engineering-plugin](https://github.com/EveryInc/compound-engineering-plugin) (~21k stars, MIT, TypeScript) · [guide](https://every.to/guides/compound-engineering)
- **Workflow/methodology layer** as a Claude Code / Codex / Cursor plugin: 37 skills + 51 agents implementing "compound engineering" — each unit of work should make the next *easier*, via planning/review/knowledge-capture
- Structured loop: strategy → ideation → requirements → planning → execution → code review → learning docs, each cycle feeding the next
- Commands: `/ce-brainstorm`, `/ce-plan`, `/ce-work`, `/ce-code-review`, `/ce-compound` (documentation), plus `/ce-debug`, `/ce-strategy`, `/ce-product-pulse`
- **Yet to research:** overlaps our own brainstorm→plan→review→learn workflow (cf. CLAUDE.md); useful as a reference for the methodology and as a ready-made skill/agent set

## nono
- [always-further/nono](https://github.com/always-further/nono) (~2.7k stars, Apache-2.0, Rust) · [nono.sh](https://nono.sh)
- **Sandboxing / isolation layer**: "sandbox any AI agent in seconds — zero setup, zero latency," no daemons/containers/VMs; least-privilege by default (restricts dirs, hides SSH keys & cloud creds from agents)
- Profile registry (registry.nono.sh) bundling filesystem scope + network allowlists + policies; custom profiles extend existing ones
- macOS / Linux / Windows (WSL2) / Nix; Rust core with Python/TS/Go FFI; L7 filtering, audit logging, credential injection, supply-chain verification — built by the Sigstore team
- **Yet to research:** how it compares to our existing **claudebox** sandbox; relevant if the harness runs agents needing per-agent isolation outside claudebox

## Gas Town
- [steveyegge/gastown](https://github.com/steveyegge/gastown) (also mirrored at gastownhall/gastown) (~16k stars, MIT, Go) · [docs](https://github.com/steveyegge/gastown/blob/main/docs/overview.md) · [Steve Yegge intro](https://steve-yegge.medium.com/welcome-to-gas-town-4f25ee16dd04)
- **Multi-agent workspace manager built on Beads** (above), by the same author (Steve Yegge) — the closest existing assembly of "an orchestration layer + a Beads DAG engine." Coordinates 20-30+ coding agents across projects
- Role cast: **Mayor** (primary coordinator — itself a Claude Code instance with full workspace context), **Rigs** (per-git-repo project containers), **Crew** (your hands-on workspace), **Polecats** (worker agents: persistent identity, ephemeral sessions), **Witness/Deacon** (per-rig health monitor / cross-rig supervisor daemon), **Refinery** (bisecting merge-queue processor)
- **Beads** are the work-tracking units (git-backed JSONL issues); **Convoys** bundle beads assigned to agents ("mountain" convoys get autonomous stall detection for epic-scale runs); severity-routed escalation (`gt escalate` → Deacon/Mayor/Overseer); a scheduler caps concurrency to avoid rate-limit exhaustion
- Git-worktree-based persistent storage ("Hooks") survives crashes/restarts; multi-runtime (claude, gemini, codex, cursor, copilot, amp, opencode, pi, …); Wasteland federation + OpenTelemetry
- **Fit note:** hybrid — you drive it *through* a Claude Code session (the Mayor), but the coordination daemon (Deacon, scheduler, Refinery) runs *beside* as a Go service. More in-Claude-Code-friendly than most orchestrators; reception is mixed (powerful, but a large idiosyncratic concept surface). Deep analysis in [library-analysis.md](./library-analysis.md)
