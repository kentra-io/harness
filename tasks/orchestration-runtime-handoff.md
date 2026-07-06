# Orchestration + Runtime + Agent-Runtime — Long-Term Plan & Session Handoff

*Created: 2026-07-05. Purpose: a **self-contained resume point** for the post-`spec-lifecycle` domains (orchestration, runtime, agent abstraction, proxy, auto-improvement). Sibling to [`planning-module-handoff.md`](./planning-module-handoff.md) (which covers the `spec-lifecycle` planning module). If picking this up cold, read this top-to-bottom, then the source docs it points to.*

> **One-paragraph resume.** The two foundational modules are settled: **constitution** (`adr-sourced-constitution`, building) and **planning** (`spec-lifecycle`, pure-Go, **implementing now — the critical path**). This doc plans everything *after* those, as **three future stages**. The design decisions below were made in a design conversation on **2026-07-04/05**, backed by two `deep-research` passes (agent-runtime/orchestration/auto-eval; and agent-schema/UI). **Nothing here starts until `spec-lifecycle` lands and dogfooding surfaces the need** — this is design-ahead, so Stage-3 decisions aren't made under build pressure.

---

## The stage map — 5 stages, 2 settled, 3 planned

| Stage | Domain | State |
|---|---|---|
| 1 | **Constitution** — `adr-sourced-constitution` (event-sourced ADR log → deterministic `constitution.md`) | Settled, building |
| 2 | **Planning** — `spec-lifecycle` (staged/gated issue lifecycle, OpenSpec-format, pure Go) | Specced — **implementing now (critical path)** |
| **3** | **Orchestration + runtime + agent abstraction** — Microsoft Conductor + claudebox + a new custom **agent-definition primitive** | Designed (this plan) |
| **4** | **Proxy / observability** — LiteLLM + Langfuse | Decided; specced in `observability.md` / `mvp-plan.md` |
| **5** | **Auto-improvement** — experiment controller (online A/B over agent config) | Designed (this plan) |

*(These renumber `mvp-plan.md`'s older Phase 2/3/4: engine-integration + execution collapse into Stage 3; obs = Stage 4. Reconcile on the next `mvp-plan.md` sweep.)*

---

## Locked decisions (2026-07-04/05 design conversation)

- **Orchestration engine = Microsoft Conductor** (`github.com/microsoft/conductor`, MIT, deterministic YAML route/`when` workflow engine). Confirmed, **not** displaced. Reinforced by the A/B design (`for_each` over an injected variant list; explore/exploit decided at launch to preserve determinism).
  - ⚠️ **Name collision:** *Microsoft Conductor* (our workflow engine) ≠ *Conductor.build* (Melty Labs' macOS app for running Claude Code agents in parallel). Always disambiguate.
- **Runtime = claudebox / Docker.** microVMs (microsandbox, Firecracker) evaluated → **deferred**: not clearly lower-maintenance, and **Firecracker is disqualified on Mac** (no native macOS). Keep Docker; park **microsandbox** as a watch-item; **Sculptor** (Imbue, container-per-agent) noted as a design reference.
- **Omnigent = reference only, NOT adopted.** Cloud sandboxes (Modal/Daytona/Islo/E2B), alpha (v0.4.0), runs *beside* Claude Code, GEPA optimization roadmap-only. We take **one idea**: `executor.harness` as a first-class agent field (runtime-agnosticism). Its `tools`/`policies` fields are Python import-paths into the Omnigent runtime → welded to a runtime we're not running → not adoptable wholesale.
- **GEPA = dropped** as the auto-improve mechanism. It optimizes *text artifacts* (prompts/skills/code) via reflection; needs an evaluator + dataset a solo founder doesn't have. Our auto-eval is **online A/B over agent config** instead (Stage 5).
- **Agent schema = a custom, neutral, declarative primitive that we own** (see next section).
- **Two-agent-abstraction collision resolved.** Claude Code's `.claude/agents` and Conductor's `AgentDef` are competing "agent" concepts that **collide, don't compose** (Conductor's default `claude` provider hits the raw Anthropic API and never sees Claude Code skills/subagents). Resolution: **our neutral agent file is the single source of truth**; a Conductor step is a *thin driver*; the **ClaudeboxProvider** compiles the neutral def → `claude -p` — **never Conductor's default `claude` provider**.
- **Consent / approval authority = orchestration-only + launch-context-bound (resolved 2026-07-05).** `lifecycle approve`/`archive` are **never in a Conductor-spawned agent's tool surface** — self-approval is *structurally* impossible, not merely prompt-gated. Two approval paths, both human-present: (a) a **human-launched interactive planning session** (host or claudebox) carries the `lifecycle-approve` skill and approves conversationally, writing `approval-state.json` directly — no Conductor round-trip; (b) **headless workflows** approve via Conductor's `human_gate` → a Conductor step runs the verb. The capability is bound to the **launch context** (human-launched vs orchestrator-spawned), *not* to the agent card — so the deferred `tools` field stays deferred; approval simply isn't a card capability. **Why this was a finding:** `spec-lifecycle`'s spec names its v1 consent checkpoint as "the agent-harness permission boundary" (a withheld CC permission *prompt*) — but our runtime is **always `bypassPermissions`**, so that prompt never fires. `permissionMode` stays dropped; in Claude Code, bypass-mode and the tool-allowlist are **orthogonal**, so the boundary lives on the allowlist / launch context, not the prompt. No change needed in `spec-lifecycle` (it correctly only *writes records*; who may call `approve` is the harness's problem).
- **UI: nothing off-the-shelf fits.** Conductor's `--web` gives a **single-run** DAG/stream/gate view for free. The **fleet/"where is everything" view is a thin custom dashboard** (host-daemon event bus + Langfuse + GitHub Projects) — already in the Phase-4 plan. The Claude-fleet-UI category (Conductor.build, Vibe Kanban, Crystal, Sculptor, Claude Squad) **churns hard** (Crystal deprecated Feb 2026; Vibe Kanban's company shut Apr 2026) and none manages declarative defs + a workflow engine — **mine as references, don't depend**.
- **Correction found:** Claude Code subagent nesting is now **5 levels, not 1** (stale premise in `workflow-orchestration-analysis.md`).

---

## The agent-definition primitive (new — Stage 3's centerpiece)

**It is its own standalone primitive** — own repo + git submodule, per the [`AGENTS.md`](../AGENTS.md) pattern (like `adr-sourced-constitution`, `spec-lifecycle`). Framework-neutral core + thin per-runtime adapters.

**Philosophy = the same play as `spec-lifecycle` w/ OpenSpec:** *conform to a neutral format's shape, own a tiny Go loader/compiler, take no runtime dependency.* Center the shape on **Agent Format (`.agf.yaml`, Snap, JSON-Schema-validated, Go parser, vendor-neutral)**, graft in **Omnigent's `executor.harness`** and **Claude's `skills`**, add **our experiment slots**. No single existing schema has {skills + harness-portability + JSON-Schema validation + experiment-slots}, so a thin owned schema is unavoidable anyway.

**Minimal v1 field set (deliberately tiny — everything else added later):**
- `system_prompt` (the persona/role instructions)
- `skills` (which skills this agent has)
- `model`
- (+ `id`/`name`, + **experiment slots** — see Stage 5)

**Explicitly deferred fields:**
- **`tools` / `mcps`** — not v1. When added, these become **references**, not inline defs. Design v1 so they can slot in later without rework.
- **`executor.harness`** — the runtime-portability field. Take the *idea* now (agents are runtime-agnostic), wire the field when a second runtime appears.
- **`permissionMode` — dropped entirely.** Always sandboxed ⇒ always `bypassPermissions`; it's a **runtime-adapter constant**, not a per-agent knob. (Impl note: `bypassPermissions` is refused as root → the box execs as the non-root `agent` user.)
- **governance-as-data** (approval/budget/constraints as declarative fields) — **not needed.** Governance lives in the **constitution + spec-lifecycle gates**, not the agent card. The card describes behavior only.

**Persona materialization (leading option to evaluate in Stage 3):** the neutral agent def is compiled/rendered into a **Claude Code agent inside claudebox** (`.claude/agents/<role>.md`), and `claude -p` references it by name. Requires **tuning claudebox** to materialize personas. Alternative: a compiler that translates the neutral def → `claude -p` flags (`--model`/`--system-prompt`/skill set) at call time. Both keep the neutral file as source of truth.

### Initial agent cast (TBD — minimal, fits our workflow)
| Role | Lifecycle stage | Mode |
|---|---|---|
| **Business Analyst** (planner) | refine / requirements | A (interactive) |
| **Tech Lead** (technical architect) | design / ADR proposals | A (interactive) |
| **Implementer** | execute | B (headless) |
| **Verifier** | verify vs milestone contract | B (headless) |
| **Bug-repro** | repro-first bug flow | B (headless) |
| **Orchestrator** | triage / routing / escalation | B (headless) |

Mode-A agents (BA, Tech Lead) drive the interactive planning half; Mode-B agents run headless under Conductor. Cast is minimal-by-intent; refine during Stage-3 design.

---

## Stage 3 — design surface & open questions

- **Spec the agent-definition primitive** (own repo + submodule): minimal field set above; Go loader + compiler; conform-to-Agent-Format-shape.
- **R1 — Conductor's transition-awareness seam (sharpened + deferred, 2026-07-05).** Headless (Mode-B) needs **no** seam: Conductor spawns `claude -p` and awaits subprocess exit = completion signal. The seam is needed *only* for a **single workflow that spans the interactive-approval boundary** (Conductor learning you approved in-session). Resolution: **poll the canonical `approval-state.json` via `lifecycle status --format json` — primary, not fallback** (zero new API, no container→host networking, aligns P5); no push/webhook until poll latency demonstrably hurts. **Build-time open (Stage-3 entry, not now):** can `human_gate` (or a small wait-step) release on a *polled file-predicate* without patching Conductor core — a code-read on Conductor. **May be moot entirely** if we let interactive planning be human-driven (Conductor enters only at execution) instead of orchestrating across the interactive boundary. *That fork is the one genuine Stage-3-entry decision.*
- **Persona materialization mechanism** (above) — evaluate "materialize `.claude/agents` inside claudebox + reference" vs "compile to flags." Needs claudebox tuning.
- **Per-variant skill injection** — for the Stage-5 A/B slots; via `--bare` (disables auto-discovery ⇒ exactly the passed skill set) + explicit provisioning. Verify exact flags at build time.
- **ClaudeboxProvider** — build (~150 LOC; reconned in `references/conductor-integration-notes.md`). Must NOT use Conductor's default `claude` provider.
- **`AgentDef.metadata`** — one-line Conductor fork patch (schema is `extra="forbid"`) for correlation keys (R2).
- **Conductor-MCP tool surface = thin 1:1 wrapper over the real 6 verbs** (reconciled 2026-07-05 against the built CLI): `get_state`←`status`, `validate_stage`←`validate --stage`, `record_approval`←`approve`, `archive_change`←`archive`, `run_guard`←`guard`. **Dropped the two invented verbs:** `submit_artifact` (artifacts are plain file writes into the mounted change folder — not MCP-mediated) and `request_transition` (no "advance" command exists; transition is *derived* — Conductor `when`-routing reads `get_state`, and the skip-aware DAG over approved gate records says what's satisfied). `record_approval`/`archive_change` are exposed **only** to the orchestration context (the consent chokepoint above), never to a Mode-B agent's tools.
- **GitHub adapter** — claim (one bot + lock label), transition, read type; bot identity provisioning.

---

## Stage 4 — proxy / observability (long-term; decided, deferred)
LiteLLM (pinned + digest-verified) + Langfuse + Claude Code OTel via local compose; route boxes via `ANTHROPIC_BASE_URL`; **issue-ID + `experiment_id`/`variant` tagging** (designed here so Stage 5 can read per-variant metrics). Open spikes R3–R6 in `mvp-plan.md` §7 (passthrough spend-tracking; supply-chain pin; Langfuse local footprint; cost authority / no-double-count).

## Stage 5 — auto-improvement / experiment controller (long-term; designed, deferred)
**Online champion-challenger A/B over agent config** (NOT GEPA text-mutation):
- Agent cards carry **experiment slots**: `model: {experiment: [sonnet, opus]}`, skill present/absent, or skill A vs B.
- Orchestrator, at low ε (e.g. ~5–10%), spawns **two variants on the SAME task in isolated worktrees**; both are scored (**paired** same-task comparison = low-variance).
- Metrics accumulate in the proxy/obs layer: **LiteLLM** = cost/latency/tokens per variant (free, via tags); **Langfuse** = quality scores.
- An **experiment controller** (lives in the host watcher daemon) summarizes after N runs (configurable horizon) → proposes a **champion flip** → **human gate → git commit** of the winning config (governed like an ADR; auto-flip allowed on a decisive margin).
- **Targets Mode-B agents first** (objective scores: contract pass/fail, verifier-caught-bug, retries, cost). Mode-A agents use occasional human pairwise preference.
- **The evaluator (non-determinism acceptable — roadmap-only).** For the **bug flow** it's objective: the pre-committed failing test passes or it doesn't. For **feature milestones** it's a **verifier *agent*** grading against the `tasks.md` validation contract — `spec-lifecycle` guarantees the contract is *present and well-formed* (`lifecycle validate` checks structure) but does **not** check *satisfaction* (no `lifecycle verify` verb; execution is out of the primitive's scope). Agent-graded ⇒ non-deterministic, which is fine for an A/B signal. *(Corrects the earlier "contracts **are** the evaluator" overstatement — the primitive structures the contract; an execution-domain agent evaluates it.)* Same machinery gives **best-of-N** sampling for free.

---

## Housekeeping / doc-reconciliation debt (not blocking)
- `roadmap-ideas.md` (2026-07-04 agent-runtime entry) — now **wrong**: says "use omnigent as the agent runtime" and GEPA auto-improve. Supersede with: runtime = claudebox; omnigent = reference (only `executor.harness`); auto-eval = online A/B; agent def = own primitive.
- `workflow-orchestration-analysis.md` — stale: **1-level nesting → now 5**; the Beads / YAML-DAG-compiler framing is superseded by the Conductor decision.
- `mvp-plan.md` / `planning.md` — still Spec-Kit-saturated in places; renumber Phase 2/3/4 → Stage 3/4; fold in the agent-def primitive.
- `kentra-sdlc.md` (umbrella) — add the agent-definition primitive + auto-eval to the primitive registry.

## Pointers
- Source design: `mvp-plan.md` (§2 two-mode control flow; §7 spikes), `planning.md`, `observability.md`, `references/conductor-integration-notes.md` (build recon).
- Research provenance: two `deep-research` passes (2026-07-04/05) — (1) omnigent-vs-conductor / runtime / auto-eval; (2) agent-schema / Claude-native agents / fleet UIs. Key verified facts embedded above.
- Settled primitives: `adr-sourced-constitution/`, `spec-lifecycle/` (submodules).
