# Planning Domain — Design & Implementation Analysis

*Generated: 2026-06-16 | Revised: 2026-06-16 (engine decision changed Fabro → Conductor, see §0; **planning/spec layer DECIDED = extend Spec-Kit via a committed `kentra` extension+preset bundle, see §6b**; runtime-agnostic promoted to a first-class v1 requirement, see §0.4). Companion to [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md), [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md), [references/technologies.md](./references/technologies.md), [references/library-analysis.md](./references/library-analysis.md), and [references/spec-kit-ecosystem-research.md](./references/spec-kit-ecosystem-research.md).*

> **What this document is.** The design for the harness's **planning domain** — the entire issue lifecycle from intake to docs — and an honest assessment of how much of it the reference library covers off-the-shelf versus how much we build. Produced through structured grilling; every decision below was made deliberately, and deferred decisions are flagged as such rather than silently resolved.
>
> **Scope — the whole planning domain:** GitHub issue intake → workflow definitions (feature, bug) → refinement → technical design → planning → plan execution → docs maintenance, plus the architectural constitution that governs all of it.

> **STATUS BANNER — 2026-07-02, updated same day (read first).** The planning module is now **SPECCED**: it is the standalone primitive [**`spec-lifecycle`**](./spec-lifecycle/spec-lifecycle.md) (a git submodule, like the constitution; design pending review). **All §6c open decisions are RESOLVED** — see the §6c table. Headlines:
> 1. **Constitution: settled & extracted.** [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) (submodule; design complete) is **authoritative** for everything constitution/ADR — §7/§8 here are summaries that defer to it.
> 2. **SDD framework DECIDED (P0, 2026-07-02): OpenSpec as the pinned artifact runtime** via a custom schema, + **Obra Superpowers co-installed** for execution disciplines, + the novel spine (gates, records, contracts, bug flow) built as `spec-lifecycle`. **Spec-Kit is eliminated** (its core now ships a competing workflow engine; no safe customize-and-upgrade path). Full evidence: [references/sdd-framework-research-2026-07.md](./references/sdd-framework-research-2026-07.md). All "Spec-Kit"/"`kentra` bundle" text below (§6b) is a **retained historical worked example, superseded** — do not build from it.
> 3. **[`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md) is now authoritative** for the staged lifecycle: stages/gates, artifact schema, NFR routing, `approval-state.json`, living-spec fold + replay guard, capability taxonomy, the constitution seam, and the bug-flow profile. §6 here remains as design rationale; where they differ, the spec wins.

---

## 0. Engine decision & framing decisions (changed 2026-06-16)

The original design adopted **Fabro** as the workflow engine. A genuine head-to-head re-evaluation ([fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md)) **reversed that choice to Conductor** (Microsoft, Python, MIT). The reversal follows from three framing decisions made this session:

| # | Decision | Consequence |
|---|---|---|
| **D1** | Genuine head-to-head re-evaluation of the engine | The Fabro lean was re-opened, not assumed. |
| **D2** | An **agent node = a full Claude Code session** (`claude -p --output-format stream-json`) running inside the sandbox | The engine supplies **neither** the agent runtime — we get skills, hooks, MCP, constitution rules, the whole harness — **nor** raw LLM calls. |
| **D3** | Sandboxes are spawned by **reusing the existing `cb`/claudebox tooling** | The engine does **not** own sandbox lifecycle either; `cb run` makes the container + worktree per issue. |

**Why Conductor wins under D1–D3.** D2+D3 demote the engine to a *thin control plane* (sequence nodes → spawn a runtime box → drive an agent CLI → capture its stream → surface gates). In that role:
- **Conductor's extensions are additive / low-drift**: a `ClaudeboxProvider` (~150 LOC against a clean `AgentProvider` ABC) that runs `cb exec <ctr> claude -p …`; a one-line per-step `metadata` field; GitHub via `gh` in `script` steps. It is **already a claude-CLI driver**, its **fresh-subprocess-per-step** model *is* "the artifact is the interface" (§6), and its **provider abstraction is the natural home for future non-Claude runtimes** (§0.1). Python, same ecosystem as the Claude Agent SDK, cheap to maintain.
- **Fabro's extensions are invasive / high-drift**: it has **no claude-CLI backend** (and Claude Code has **no ACP mode**), so adoption means a new backend inside its Rust router; reusing `cb` fights its sandbox-owns-the-workspace design. Its one standout asset — a multi-run dashboard — **no longer counts**, because the dashboard is a non-blocker we own separately (§0.2, §12a).

**Engine adopted: Conductor (forked minimally / extended additively, MIT).** Thin-custom engine stays as the live off-ramp if the additive surface ever turns invasive.

### 0.1 Multi-model runtimes — claudebox is the first "agentbox"
`claudebox` is Claude-specific. A milestone may later be implemented/verified by **a different model or runtime** (a Codex CLI box, a Gemini CLI box, a raw-API agent). This generalizes D2/D3 rather than breaking it: a runtime is `(box image) + (agent CLI invocation)`, and each is **one additive Conductor provider**, chosen per node via `agent.provider`/`agent.model`. Keep the provider seam narrow: `spawn box → exec agent CLI → stream events → return artifact/output`; runtime quirks live in the provider, never the engine.

### 0.2 Observability is a separate plane, below the engine
Under D2 the engine never sees the model calls (the agent CLI makes them), so telemetry is **not** an engine feature — it lives in a plane *beneath* the runtime and is therefore **engine-agnostic**. See §12a. Headline: **LiteLLM proxy (pinned version)** as the cross-provider cost/governance gateway, optionally feeding **Langfuse** (and/or Claude Code's native OpenTelemetry) for traces.

### 0.3 Topology — engine on host, every agent in a `cb` box
The **engine control-plane runs on the host**; **every agent invocation — intake, the Opus orchestrator, planning, design, implementation, verification — runs as a `claude` session inside a `cb`-spawned claudebox**. "Everything runs in claudebox" and "the engine may run on the host" are consistent: *steps* run in boxes; the engine's next-node bookkeeping runs on the host. Because the engine is on the host, `cb run` hits the host Docker daemon directly — **no nested-Docker-through-the-socket-proxy problem**.

### 0.4 Runtime-agnostic is a first-class v1 requirement (decided 2026-06-16)
The harness targets **running multiple coding agents**, and the planning/spec layer must be **runtime-agnostic** — "no good reason it should be Claude-specific." This **supersedes the earlier §0.1 "claudebox-first, defer multi-runtime" lean**: agent-agnostic *artifact/command tooling* is weighted as a present requirement, and is a primary reason the planning layer adopts **Spec-Kit** (it generates command/skill files per agent — Claude Code, Cursor, Copilot, Gemini, … — §6b) rather than a Claude-Code-native bespoke build. Conductor's provider seam (§0.1) remains the multi-*model-runtime* mechanism; this decision is about the *tooling* being agent-neutral.

---

## 1. Verdict

**No single library tool implements the planning domain.** The closest matches each cover one slice:

- **Conductor** (workflow engine, MS, Python, MIT) — owns workflow definition (YAML), sequencing, conditional routing, bounded retry, HITL gates, per-step model/provider selection, resume/checkpoint, per-step cost *reporting* (surfaced on its event bus; distinct from the LiteLLM cost *gateway*, §0.2), and an embeddable event bus. **Adopted as the engine, extended additively** (§0). *(Fabro was the prior choice; see [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md).)*
- **GitHub Issues + Projects v2** — system of record for issues and coarse lifecycle state.
- **Spec-Kit — a leading candidate for the spec-management layer (adoption REOPENED 2026-07-02, §6b/§6c-P0)**; if chosen, extended via a committed **`kentra` extension+preset bundle** (§6b) that owns **only the staged lifecycle artifacts** (requirements/design/plan templates + intent-named commands/hooks). The **event-sourced constitution — ADR log, `constitution.md` projection, and the plan-time deviation gate — was extracted into the standalone [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) primitive** (authoritative; now a git submodule; Spec-Kit is just one of its adapters). *(This reverses the earlier "none adopted wholesale / custom layout" lean — source-level investigation of Spec-Kit's v0.10 extension system, 2026-06-16.)*
- **LiteLLM + Langfuse** — the observability plane (cost/trace/telemetry), adopted off-the-shelf, beneath the engine (§12a).

The two genuinely **novel** pieces — a **governed constitution as an event-sourced projection of an immutable ADR log with a human amendment gate**, and a **continuous drift detector that files issues** — exist in *no tool, in no combination* (confirmed by dedicated research, 2026-06-16/07-02). The constitution is now **fully specified and extracted as the standalone [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) primitive** (v1, design complete); the drift detector stays deferred to a background worker (§15).

**Custom work required, in rough order of effort:** (1) Conductor `ClaudeboxProvider` (drive `claude -p` inside a `cb` box); (2) GitHub adapter (via `gh` script steps — incl. the Projects-v2 claim=assign+transition, which **no** Spec-Kit extension provides); (3) the **`kentra` Spec-Kit bundle** (preset: lifecycle templates — requirements/design/plan; extension: intent-named lifecycle commands + docs-stage hook — §6b); (4) the deviation/amendment **enforcement in Conductor** (the primitive/extensions only emit structured records; Conductor blocks); (5) the **`adr-sourced-constitution` primitive** — immutable ADR log + `constitution.md` projection + plan-time deviation gate (specced standalone as a Go CLI + skills; §7); (6) the multi-run "issue board" dashboard; (7) the drift detector (deferred to a background worker). Everything else is off-the-shelf (Conductor, Spec-Kit, GitHub, Claude Code subagents, LiteLLM/Langfuse) or borrowed convention.

---

## 2. The two-level workflow model

The single most important structural decision. There are **two distinct things** that were both being called "workflow," and they use different machinery:

- **Level A — the per-issue lifecycle.** An issue moves through *states* (e.g. feature: `idea → refined → designed → planned → in-progress → in-review → done`). This is a **status-driven state machine**, one definition per issue *type*, expressed as **one Conductor YAML workflow per type** (`feature.yaml`, `bug.yaml`) — steps with conditional `routes`/`when`, bounded `retry`, and `human_gate` steps. *This* is what "workflow definition" means.
- **Level B — execution within a stage.** Originally a DAG of milestones. **Dropped — execution is now sequential.** This removed the YAML→Beads-DAG compiler, the single most complex custom piece of the old stack (see [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md)). Milestones run in order, in one worktree.

> **Consequence:** Beads is fully dropped. No DAG engine, no dependency scheduler. State lives in three single-purpose stores (§4).

---

## 3. Architecture at a glance

```
  GitHub Issues  (system of record: backlog, issue TYPE, ownership)
  GitHub Projects v2  (coarse status: New / In Progress / Needs Input / Closed)
        │  claim = assign + transition  (the concurrency mutex, BEFORE the engine starts)
        ▼
  Conductor (engine, on HOST) ── Level-A workflow engine ───────────────────┐
   • YAML workflow defs (authored conversationally by Claude)               │
   • per-step model/provider + open k-v metadata (issue=, role=, …)         │
   • conditional routes, bounded retry, human_gate, resume, cost            │
   • embeddable: one engine.run() per issue, one shared event bus           │
        │  one cb box + one worktree PER ISSUE  (issue-level parallelism)    │
        ▼                                                                   │
  ClaudeboxProvider (custom, ~150 LOC)                                       │
   • cb run → claudebox + worktree;  cb exec <ctr> claude -p (per node)      │
   • spawns Claude sessions: Opus orchestrator → Sonnet impl → Haiku verify │
   • (future: CodexboxProvider / GeminiboxProvider / ApiProvider — §0.1)    │
        │            ▲                                                       │
        │            │ all model traffic via ANTHROPIC_BASE_URL             │
        ▼            │                                                       │
  Target project repo│   LiteLLM proxy (pinned) ──► Langfuse / OTel (§12a)   │
   • constitution (principles + projected architecture + ADR event log)     │
   • spec-folders (one per issue: requirements / design / plan)             │
   • code ◄──────────────────────────────────────────────────────────────┘

  Dashboards (separate, engine-agnostic):
   • multi-run "issue board"  ← Conductor event bus + GitHub Projects  (build thin, §12a)
   • cost / traces            ← LiteLLM UI + Langfuse                  (adopt OSS)
   • per-issue drill-down     ← Conductor's built-in single-run web view (reuse)
```

**Repo split:** **workflow definitions live in the harness repo** (shared lifecycles — feature/bug are general engineering process). The **constitution and spec-folders live in each target project's repo** (project-specific). The harness operates *on* a project, reading/writing that project's constitution, spec-folders, and GitHub issues, but driving them through harness-owned workflows.

**The "inside Claude Code" constraint is relaxed:** Claude Code is the *agent runtime* (spawned execution/verification sessions, one full `claude` session per node); a thin Conductor layer on the host owns workflow definitions, run-state, and the event stream.

---

## 4. State lives in three single-purpose stores

| Store | Owns | Granularity |
|---|---|---|
| **GitHub Issues + Projects v2** | Backlog, issue **type** (feature/bug), **ownership/assignment**, **coarse status** | Coarse: New / In Progress / Needs Input / Closed |
| **Issue's spec-folder** (in project repo) | The staged artifacts: requirements / design / plan | Per-issue artifacts |
| **Conductor run-state** | The fine-grained lifecycle state, which step, pass/fail, checkpoints, cost — exposed via the in-process **event bus** + a durable per-run **JSONL event log** | Fine-grained, per-run |

**No 1:1 mapping** between Conductor's fine-grained state and GitHub's coarse status. GitHub answers *"who owns what, and does anything need me?"*; Conductor answers *"what's happening inside this run."* The **GitHub Projects board** is the durable "where is every issue" view; the **multi-run issue board** (built on Conductor's event bus, §12a) is the live "what's happening now" view. Complementary, not redundant.

**Claiming is the concurrency mutex.** Agents have their own **GitHub identity**. `New → In Progress` + assignment happens **first, as an atomic claim, before the engine starts** — so no other identity picks up an in-flight issue. Issue-level parallelism is safe because each in-flight issue is owned + sandboxed + single-worktree. Discovered work during execution becomes a **new GitHub issue** that re-enters the pipeline. Every run is tagged with its issue number via `config.workflow.metadata`, so all run-state and telemetry are joinable by issue.

---

## 5. Intake & triage (the front door)

Issues enter two ways — a human **files a GitHub issue**, or a **Claude conversation** decides "this should become tracked work" (and may pre-fill/advance early stages in that same session). Both converge on the lifecycle.

- **Classification = GitHub's native issue *type* field.** No agent classifier. v1 handles exactly two types: **Feature** and **Bug**. Other types sit untouched until their workflows are added later.
- **Routing is automatic by type; *initiation* is asymmetric:**
  - **Bug** → can **auto-start**: repro-first; if an agent reproduces it, it proceeds autonomously; not reproducible ⇒ `Needs Input` (human).
  - **Feature** → **human-initiated**. A feature is an *idea*; it is **not** auto-implemented. A human starts a planning session (the conversation-first entry). Rationale: a reproduced bug has an objective contract; a feature-idea needs human intent-setting before compute is spent.
  - **Other types** → wait for human action (no v1 workflow).

**Library coverage:** none needed for routing — "read the GitHub issue type, dispatch the matching Level-A workflow." Custom: a small **GitHub adapter** — read type, create/transition/assign issues — implemented as **`gh` calls inside Conductor `script` steps** (additive; no engine change).

---

## 6. Spec-management — issue-as-folder, separated artifacts

**Each GitHub issue maps to one version-controlled folder** in the target project's repo, holding that issue's staged artifacts. The feature pipeline produces **three artifacts**, each in a fresh orchestrator thread, each a **self-contained, user-approved** hand-off:

| Stage (fresh thread, user-approved gate) | Artifact |
|---|---|
| **Refinement** | `requirements` — **functional + non-functional, kept distinct** |
| **Technical design** | `design` — *where the plan-time constitution check bites hardest* (design vs architecture + ADRs) |
| **Planning** | `plan` — design translated into **milestones, each carrying an explicit validation contract** |

**Handoff principle (domain-wide):** each stage runs in a **fresh thread**, consumes the previous stage's approved artifact, emits the next. No context bleeds across stages; **the artifact is the interface**; a user-approval gate sits on every boundary (and is what the "needs attention" view surfaces).

> **Engine fit (free win):** Conductor spawns a **fresh `claude` subprocess per step** (no session carryover), and sequential steps share the issue's worktree, so a step reads the file the previous step wrote. "Fresh thread per stage, the artifact file is the interface" is therefore the engine's **native behavior**, not something to engineer.

> **Correction (2026-06-25, see [mvp-plan.md §2](./mvp-plan.md)):** the *planning* stages (refine/design/plan) are **not** headless subprocesses — they are **interactive agent sessions** (Mode A) that *call* Conductor via a **Conductor-MCP** server and advance human-approved gates conversationally. Only the *execution* loop (§10) is engine-driven headless `claude -p` (Mode B). The "fresh thread per stage, artifact-is-the-interface" principle holds in both modes; the artifact/gate records in the spec-folder are the interface either way.

**Library coverage & decision (REVISED 2026-06-16; adoption REOPENED 2026-07-02 — see §6b/§6c-P0):** No tool matches the exact artifact separation off-the-shelf. The 2026-06-16 lean was **adopt Spec-Kit and extend it via a committed `kentra` bundle** (Spec-Kit is a first-class extension platform) — *not* a from-scratch layout. That framework commitment is **now reopened: spec-kit vs openspec vs superpowers, TBD in the planning-module spec.** Regardless of framework, we still **mine proven models** from:

- **Folder + living-spec lifecycle** ← **OpenSpec's** change-folder + **delta/archive** model (the canonical living-spec implementation).
- **ADR substrate** ← **adr-tools / Log4brains** one-decision-per-file Markdown convention.
- **Code-time constitution enforcement** ← a generation-time PreToolUse-hook pattern (check the diff against constitution constraints *as code is generated*; execution-domain, deferred).
- **Build ourselves:** the governed multi-doc constitution + amendment gate, and the drift detector — *no tool has these*.

> **Why not adopt Spec Kitty** (Priivacy-ai/spec-kitty)? Its strengths (worktree isolation, lifecycle lanes, review/accept/merge gates) are *exactly what Conductor + the `cb` claudebox executor already own*. Adopting it means two systems fighting over execution orchestration. It's a reference for artifact structure, not a dependency.

---

## 6b. SDD-framework adoption — SUPERSEDED historical worked example (do not build from this)

> **SUPERSEDED 2026-07-02.** The Spec-Kit commitment was first retracted (P0 reopened), then **resolved against Spec-Kit** the same day: fresh research ([references/sdd-framework-research-2026-07.md](./references/sdd-framework-research-2026-07.md)) found Spec-Kit's core now ships its own workflow-orchestration engine (a direct Conductor collision — the Spec-Kitty problem, in core) and has no safe customize-and-upgrade path (#2319). **Decision: OpenSpec as the pinned artifact runtime; the lifecycle layer is the standalone [`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md) primitive.** This section is retained only as the historical worked example of what a Spec-Kit adoption would have looked like; the capability-line principle it articulated (framework = veneer, engine = enforcement kernel) survives in the new design. The constitution research (§9.2 of [`adr-sourced-constitution.md`](./adr-sourced-constitution/adr-sourced-constitution.md)) had independently found Spec-Kit the worst philosophical fit.
>
> **UPDATE 2026-07-02 — the constitution/ADR/governance pieces were split OUT into a standalone primitive.** The governance items described below (immutable ADR log, projection/`regen`, `constitution-init`, plan-time deviation gate) have been **extracted from the `kentra` Spec-Kit bundle into a standalone, framework-neutral primitive — [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) (authoritative).** It ships as a **Go CLI + agent-agnostic skills + thin per-framework adapters** (Spec-Kit is now just *one* adapter), integrating by default via a `constitution/` folder + an AGENTS.md/CLAUDE.md pointer. The **`kentra` bundle below still stands for the *lifecycle* layer** (requirements/design/plan preset + commands) only.
>
> **Verified corrections to the text below (2026-06-30/07-02):** the reference extensions **`spec-validate`, `architecture-guard`, and `Mneme HQ` could not be verified to exist** — treat as misremembered; **bare command aliases are NOT supported** (commands must be `speckit.{ext}.{cmd}`); `specify init --force` **clobbers** `.specify/templates/` + `.specify/scripts/` (all customization must ship as an extension/preset, not hand-edited core). The ADR record now also carries a **`status`** field (minimal-MADR-compliant) with immutability enforced by a field-scoped guard — see the standalone spec.

*(Conditional worked example — the framework choice is reopened, §6b banner / §6c-P0.)* **If Spec-Kit is chosen: the planning/spec layer is built by EXTENDING GitHub Spec-Kit** (MIT, Python CLI, agent-agnostic), packaged as a custom **`kentra` extension + preset bundle**. Conductor still owns orchestration and *all hard enforcement*; the framework owns artifact structure and the lifecycle command/hook surface. (The plan-time deviation gate is the constitution primitive's, not the framework's — §6b banner.) This supersedes §6's earlier *build-the-layout-from-scratch* lean (the reference models there are retained as references).

**The capability line (load-bearing).** A Spec-Kit extension is a **structuring + lifecycle-triggering + doc-generation veneer**; **Conductor is the enforcement kernel.** Extension hooks are *non-blocking / agent-honored* (no runtime stop), and the catalog `effect` tag is advisory, not a sandbox. Therefore:
- *In the `kentra` bundle:* artifact templates, intent-named commands, doc generators, context hooks.
- *In Conductor:* every hard gate (human approval, blocking deviation, amendment), capability boundaries.
- *The seam:* the extension **emits a structured record**; Conductor **reads it and enforces** the halt.

**Decided specifics (this session):**

| # | Decision | Detail |
|---|---|---|
| **Packaging** | **Committed `.specify/` per project** | Each target repo commits its full `.specify/` (pinned Spec-Kit tooling + the `kentra` bundle + `memory/` artifacts) → projects are self-contained / reproducible without the harness. **Accepted cost: upgrades fan out** — mitigated by a harness-owned `kentra` sync/update command + version pinning (LiteLLM/claudebox precedent). The Spec-Kit CLI is installed via `.claudebox/Dockerfile` (uv/pipx), not at runtime. |
| **Commands** | **Intent-named, convention-conformant** | `speckit.kentra.requirements` / `…design` / `…plan` (conforms to Spec-Kit's required `speckit.{ext}.{cmd}` pattern; **bare aliases are NOT supported** — verified). Named for *our* intent — resolves the native mismatch (Spec-Kit `plan`≈our design, `tasks`≈our plan); the native templates/flow run underneath. **`analyze`/`adr`/`regen`/`constitution-init` are NOT kentra commands** — they belong to the standalone [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) primitive. Baked into the repo. |
| **Gate wiring** | **Structured file in the spec-folder** | Each check writes `deviation.json` (constitution gate; defined by the primitive) / `approval-state.json` (stage approval) into the issue's spec-folder; Conductor reads it at the gate step and enforces block/proceed. Decoupled, inspectable, committed beside the artifact, runtime-agnostic. *(The `spec-validate`/`architecture-guard` extensions cited in earlier drafts as models could not be verified to exist — the JSON-record seam stands on its own.)* |
| **v1 scope** | **All four governance pieces ship** | ADR append-only log · projection regeneration · amendment gate (HARD RULE) · plan-time deviation gate. Only the continuous drift-filer + brownfield extraction stay deferred (§15). |

**The `kentra` bundle — concrete contents (lifecycle layer only; the constitution/ADR pieces live in the [primitive](./adr-sourced-constitution/adr-sourced-constitution.md)):**
- **Preset (template overrides):** `requirements` (functional + NFR — kept distinct; the exact artifact vocabulary is an **open decision**, see §6c), `design`, `plan` (milestones, each with a per-milestone **validation contract / DoD**).
- **Extension commands** (`speckit.kentra.*`, no bare aliases): `requirements` · `design` · `plan` — intent-named entry points over the native flow.
- **Hooks:** docs-stage trigger that invokes the primitive's `constitution regen`; `before_*` constitution context-injection (memory-loader pattern). *(Hooks only prompt; Conductor enforces.)*
- **NOT in this bundle** (owned by [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md)): `constitution init` · `adr new`/`supersede`/`deprecate` · `regen` · the plan-time deviation gate (`analyze` → `deviation.json`). The primitive ships as a **Go CLI + agent-agnostic skills**; Spec-Kit is one of its adapters.
- **Reference patterns mined** (patterns, **not** runtime deps): `spec-kit-arch` (projection-regen mechanism), `DocGuard` (ADR template), `superpowers-bridge` (bounded-gate pattern), `spec-kit-bugfix` (surgical spec-tracing for the §11 bug flow). *(`spec-validate`, `architecture-guard`, and `Mneme HQ`, cited in earlier drafts, could not be verified to exist — treat as misremembered.)*

**Hard limits to respect:**
- **Avoid all orchestration-flavored extensions** (Fleet, MAQA, Loop/Ralph, Conduct, worktree-*) — they assert execution/worktree/HITL ownership and collide with Conductor (the Spec-Kitty problem). Only `spec-kit-schedule` is collision-free (could *feed* Conductor).
- **No GitHub extension fits** our Projects-v2 claim=assign+transition or issue-as-folder model (the issue extensions *invert* it) — build the GitHub adapter ourselves (§5); borrow only the `gh` traceability-comment pattern.
- **No tool provides** the immutable append-only ADR log + supersede semantics (adr-tools / DocGuard give only the convention/template), projection-from-specs+ADRs, or the continuous drift-filer — these stay custom (§1, §13).

---

## 6c. Planning-module decisions — ALL RESOLVED 2026-07-02 (specced in [`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md))

The constitution was extracted (§7 → the [primitive](./adr-sourced-constitution/adr-sourced-constitution.md)); the staged lifecycle layer is now specced as the standalone [**`spec-lifecycle`**](./spec-lifecycle/spec-lifecycle.md) primitive (submodule). Resolution ledger:

| # | Question | RESOLUTION (2026-07-02) |
|---|---|---|
| **P0** | **SDD framework** | **OpenSpec (pinned) as artifact runtime** via a custom `openspec/schemas/kentra-spec-lifecycle/` schema + **Superpowers co-installed** (execution disciplines) + the novel spine built in `spec-lifecycle`. Spec-Kit **eliminated** (core workflow engine collides with Conductor; broken customize-upgrade path). Evidence: [references/sdd-framework-research-2026-07.md](./references/sdd-framework-research-2026-07.md). |
| **P1** | **Artifact vocabulary & count** | Follows OpenSpec: `proposal` + `specs/` delta (= the requirements artifact) + `design` + **`plan`** (our one rename of stock `tasks`, content genuinely differs). Three stages: refine → design → plan. |
| **P2** | **NFRs vs technical design** | NFRs are requirements: measurable/behavior-observable → spec delta (merged with functional); project-wide invariants → constitution ADR; internal-quality → design.md. Design must discharge every declared NFR; perf validation is async (not per-milestone). |
| **P3** | **`tasks` / plan granularity** | **No distinct tasks stage** — steps live inside `plan.md` milestones, sized by `planGranularity` in `lifecycle.yml`. |
| **P4** | **TDD** | Validation contract (acceptance criteria) mandatory per milestone; bugs repro-first (failing test = requirement + contract); no blanket test-first mandate. |
| **P5** | **Stage interface** | **Files canonical** (`approval-state.json` / `deviation.json` in the change folder); Conductor-MCP layers on top in Phase 2 reading the same files. |
| **P6** | **living-spec** | **In MVP, inside `spec-lifecycle`**: deterministic fold of structured deltas (OpenSpec archive) + a **replay guard** (`lifecycle guard`) — the constitution's fidelity discipline applied to the functional side. Not agent-synthesized (that reframing was the research's key insight); prose synthesis stays deferred. |

---

## 7. The constitution — event-sourced governance

> **UPDATE 2026-07-02:** the constitution is now fully specified as a standalone primitive in [`adr-sourced-constitution.md`](./adr-sourced-constitution/adr-sourced-constitution.md) (**authoritative**). Refinements since this section: ADRs are **minimal-MADR-compliant with a `status` field**; the **projection is `constitution.md`** — the governed "HOW", rendered deterministically from the *active* ADR set — while the **living-spec** (architecture view synthesized from feature-specs) is a **separate** projection/module; and the whole thing is **framework-neutral** (Spec-Kit is one adapter). The event-sourcing model described below stands.

The constitution is **not one document** and **not all authored**. It is an **event-sourced system**:

| Layer | Role | Mutability |
|---|---|---|
| **Principles** | The *true* constitution — authored root, governs everything | **Human-authored / human-amended** |
| **Per-issue specs + ADRs** | The **event log** — preserved, append-only archive of what was decided & built | **Append-only.** ADRs are immutable events; you don't edit one, you append a **superseding** ADR |
| **Logical architecture + living system spec** | **Projections** — current-state views computed from the sum of all per-issue specs (+ ADRs) | **Derived / regenerated**, never hand-edited |
| **Human-facing reference docs** | Derived from the projections | **Deferred** (see §15) |

This model resolves several things cleanly (note: the **principles** layer is itself a single authored file; "not one document" refers to the *whole governed set*, not the principles):

- **Logical architecture can't drift from the ADRs** — it's *regenerated from* them. Governed transitively by governing the events.
- **Deviation detection cites a specific principle or ADR** — both are the stable, addressable layer.
- **Amendment** is either amend a **principle** (human consent) or append a **superseding ADR** (Opus proposes, human consents). The archive is never mutated — you supersede.

**Projection regeneration: eager.** Projections regenerate as the **closing step of each issue's docs stage**. (Mechanism: the `kentra` `regen` command, fired on a docs-stage `after_*` hook, reads all specs + ADRs and rewrites the views — §6b.) Always-current architecture/living-spec views (they're what humans and the constitution-checker read). A background worker validating projection-vs-event-log fidelity is **deferred** (§15).

**Bootstrap — greenfield only.** The primitive's **`constitution init`** command (an interview: the agent interviews you, drafts founding-principle ADRs for approval) seeds **only the principles**. Architecture + ADRs start empty and **accumulate** through the pipeline. Brownfield constitution-extraction is **deferred**. *(Authoritative: [`adr-sourced-constitution.md §7`](./adr-sourced-constitution/adr-sourced-constitution.md).)*

> A **skill/rule** codifies the governed-set structure (what principles vs architecture vs ADRs are for, how agents must consult them). Every planning/execution agent loads it. Because nodes are full Claude Code sessions (D2), this is a normal skill/CLAUDE.md load inside the box.

---

## 8. Deviation detection & amendment

**Detection at three moments, one shared detector primitive** (compare an artifact against its governing source, cite violations):

1. **Plan-time gate** (planning/design stage) — the proposed design/plan vs the constitution (`constitution.md` = principles + active ADRs), *before code exists*. Implemented as the **plan-validation gate of the [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) primitive** (a Layer-2 skill; the chosen SDD framework's adapter wraps it — §6c) emitting a `deviation.json` record, each finding citing the specific `ADR-id`, that **Conductor** reads and enforces. **Blocks.**
2. **Code-time gate** (execution) — the diff vs the constitution, on the full diff before "done." A generation-time PreToolUse hook is the mechanism reference. **Blocks.**
3. **Background sweep** — periodically scans the existing repo for drift. **Non-blocking**; files a tracked GitHub issue that re-enters the pipeline. **Deferred** (§15).

**Checker = a dedicated cheap-model (Haiku) agent**, run as its own node — **never the implementer** (factory.ai rule: no agent validates its own work). Output: a structured deviation record **citing the specific principle/ADR**.

**The amendment loop — every flagged deviation → conform-or-amend decision:**
- **Conform** — fix the artifact (revise plan / correct code) to match the constitution; or
- **Amend** — append a superseding ADR or amend a principle.

The **Opus orchestrator triages** every deviation: genuine improvement worth an amendment, implementer over-creativity to rein in, or just a mistake. Only genuine candidates become amendment *proposals*.

> ### HARD RULE
> **No amendment to the constitution is ever applied without explicit user consent. No exceptions, no agent auto-approval.** The constitution is authoritative; Opus may *propose*, only the human *approves*.

---

## 9. Planning → execution handoff

**Planning owns the validation contracts.** Planning's real output is not "milestones" — it's **milestones + per-milestone, pre-committed validation contracts** (the handoff criteria). "What done means" is decided by the reasoning-heavy planning step *before* any code exists, so the cheap execution loop has an objective target. Execution *consumes* contracts; it never authors them.

A **new orchestrator thread with fresh context** (a fresh `claude` session, §6) starts from the approved, self-contained plan.

---

## 10. Execution stage

Per-milestone loop, sequential, in the issue's single worktree:

```
implement (Sonnet) → verify (Haiku) against the milestone's pre-committed contract → pass → next
```

Mapped to Conductor: an `implementer` agent step (`provider=claudebox model=sonnet`, `retry.max_attempts: 3`), a `verifier` step (`model=haiku`) whose structured output drives `routes`/`when`, and `human_gate` steps for escalation.

**Failure handling — every loop bounded, escalation always climbs:**

1. **Same implementer retries**, fed the verifier's specific rejection reasons (holds the most context). Verifier always grades against the **pre-committed contract**, never a moving target. **Cap: 2 retries** (`retry.max_attempts: 3` = 1 initial + 2 retries = 3 attempts total; or a route loop-back bounded by `limits.max_iterations`).
2. After the 3rd attempt fails, the milestone **routes to the Opus orchestrator** step, which triages: *code* problem (re-dispatch with sharper guidance / fresh context) or *plan* problem (the contract was mis-specified). **Opus gets up to 2 bounded attempts** — **but may route to a human gate immediately at its discretion** when it judges the problem warrants human input right away (clearly a plan/architecture/constitution issue).
3. **Human escalation** → `human_gate` → GitHub `Needs Input`. A plan problem proposes a **plan revision** (a user-approved artifact → may loop back into planning).
4. **Final whole-issue integration check** after all milestones pass — against the *original requirements* (milestones can each pass yet not compose). The **code-time constitution check** runs here on the full diff. Failure escalates Opus → human.

**Escalation principle (domain-wide):** implementer → orchestrator → human. Never silent retries; every loop has a hard cap.

---

## 11. The bugfix workflow (distinct Level-A workflow)

A compressed, separate state machine (its own `bug.yaml`) that *reuses* shared steps:

- **Repro first, always.** An agent writes a **failing test capturing the bug**. That failing test is *both* the requirement artifact *and* the validation contract ("done" = test passes + no regressions). Reproducible ⇒ proceed; not reproducible ⇒ `Needs Input`.
  - *Safe because all target projects are **local-first** (testcontainers, 100% local setup), so every functional issue is reproducible.*
- **Refinement folds into repro** (no separate requirements gate). **Technical design skipped by default** (most fixes are local).
- **Execution** = the fix in one worktree, verified against the now-passing test. **Code-time constitution check still runs.**
- **Docs** only if documented behavior changed.
- **Escalation hatch:** if Opus judges the fix touches architecture (spans layers, or would require a constitutional deviation), it stops at a `Needs Input` gate and proposes **promoting the bug into the full feature pipeline** (inserting design + planning).

---

## 12. Docs domain

The documentation domain *is* the event-sourced constitution model (§7):
- **Docs stage (per completed issue):** append the issue's spec (+ any new/superseding ADRs) to the event log, then **eagerly regenerate the projections** (logical architecture + living system spec). Mechanical.
- **Human-facing reference docs** (README, architecture overview, API, onboarding) are **derived from the projections** — regenerated, never hand-edited (so they can't drift independently). **Deferred** — secondary, details later.

---

## 12a. Observability & dashboards (the plane below the engine)

Under D2 the engine doesn't see model calls, so telemetry lives **beneath the runtime** and is **engine-agnostic**. Two concerns, handled separately:

> **DECIDED (2026-06-17) — full decision record in [observability.md](./observability.md).** The telemetry stack is settled: **LiteLLM (pinned gateway) + Langfuse (self-hosted obs/eval/persistence) + Claude Code native OTel.** This resolves [§15 open-decision #8](#15-open-decisions-deliberately-deferred). Langfuse won the obs/eval slot decisively: it is now **ClickHouse-backed** (acquired 2026-01-16, active, public MIT/OSS/self-host commitment) and has the deepest OSS eval layer (LLM-judge + code scorers + **datasets + experiments** = the eval-of-configs half of the goal set). **Helicone was rejected** (acquired by Mintlify 2026-03-03 → maintenance mode; its only Claude-Code-fitting ingress is a deprecated legacy proxy; cloud/enterprise-first vs the local-first priority). **TensorZero rejected** (reported unmaintained; function-shaped not session-shaped). See observability.md §3–§4 for rationale and build-time verification items.

### Telemetry / cost / traces — adopt OSS *(decided — see observability.md)*
- **LiteLLM proxy (PINNED version)** is the single gateway every runtime points at (`ANTHROPIC_BASE_URL` + `ANTHROPIC_AUTH_TOKEN` for Claude Code; native/OpenAI format for other runtimes). It provides cost, tokens, latency, logging, routing, fallbacks, budgets, guardrails, an admin UI, and Prometheus `/metrics`. **Pin the version** and verify the image digest — releases **1.82.7 / 1.82.8 shipped credential-stealing malware**. Also confirm spend tracking fires for native Anthropic `/v1/messages` passthrough (a reported 2026 gap) by **defining each model explicitly in `config.yaml`**; set `CLAUDE_CODE_ATTRIBUTION_HEADER=0` if caching at the proxy.
- **Langfuse** (MIT, self-host, OTel-native, ClickHouse-backed; LiteLLM has a native `langfuse_otel` callback) for traces / sessions / per-issue cost analytics + the eval/dataset/experiment layer. *(Arize Phoenix — most OTel-native but ELv2 — and Helicone were evaluated and not chosen; see observability.md §3.)*
- **Claude Code native OpenTelemetry** (`CLAUDE_CODE_ENABLE_TELEMETRY=1` + OTLP env) for span-level depth on Claude nodes; can ship to the same Langfuse/Grafana backend.
- **Join by issue:** every run is tagged (LiteLLM metadata / `X-Claude-Code-Session-Id` / OTel resource attr = issue number), so cost/traces are queryable per issue.

### The "where is every issue / what needs me" board — build thin
This is harness-specific, so it's the one piece to build — and Conductor makes it small because it is **embeddable as a library**:
- Stand up one long-lived asyncio service that runs **one `WorkflowEngine.run()` per issue concurrently**, subscribing one callback to each run's `WorkflowEventEmitter`. Events (`agent_started`, `gate_presented`, `agent_completed.cost_usd`, `workflow_completed/failed`, …) carry `run_id` + the injected issue number → one aggregator sees all runs live.
- *Or* out-of-process: tail the per-run JSONL logs (`$TMPDIR/conductor/conductor-<name>-<ts>-<run_id>.events.jsonl`, append-only, flushed per line) + a `run_id→issue` side table.
- **GitHub Projects** remains the durable backlog/coarse board; the custom app adds the live overlay + the gate-answer action.
- Conductor's **built-in single-run web view** (FastAPI + WebSocket, React-Flow graph, `/api/state` + `/ws`, gate-answer POST) is reused as the **per-issue drill-down**.

> The dashboard is a **non-blocker** (vibe-code our own / adopt OSS) and is **not an engine-selection criterion**.

---

## 13. What we adopt vs. what we build

| Capability | Off-the-shelf / borrowed | Custom build |
|---|---|---|
| Workflow definition + engine | **Conductor** (YAML, conditional routes, bounded retry, human_gate, per-step model/provider, resume, cost, embeddable event bus) | Additive extensions (below) |
| Workflow authoring | — | **Conversational** — Claude writes/edits the YAML; user reviews |
| Agent runtime | **Claude Code sessions** (`claude -p`, Opus/Sonnet/Haiku) — full skills/hooks/MCP/constitution rules | `ClaudeboxProvider` spawn/stream glue (~150 LOC) |
| Execution sandbox | **existing `cb`/claudebox tooling** (reused, not rebuilt) | provider calls `cb run` / `cb exec`: 1 box + 1 worktree per issue |
| Issue tracking + coarse state | **GitHub Issues + Projects v2** | GitHub adapter via `gh` `script` steps (read type, claim, transition) |
| Observability / cost / traces | **LiteLLM (pinned) + Langfuse / OTel** | tagging + dashboards wiring |
| Multi-run "issue board" | Conductor event bus + GitHub Projects + Conductor's per-run view | thin aggregator service + frontend |
| Spec-folder (lifecycle artifacts) | **OpenSpec (pinned)** — change folders, delta grammar, deterministic fold, 30-tool command generation; + **Superpowers** co-installed (execution disciplines) | **[`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md) primitive** — custom `kentra-spec-lifecycle` schema, gates + `approval-state.json`, `plan.md` validation contracts, bug profile, replay guard, constitution seam |
| ADR substrate + constitution projection | **MADR v4** convention | **`adr-sourced-constitution` primitive** — immutable append-only ADR log + supersede/deprecate + deterministic `constitution.md` projection (specced standalone; Go CLI) |
| Code-time constitution enforcement | generation-time PreToolUse-hook *pattern* (execution domain) | the checker integration (deferred to execution) |
| Governed constitution (event-sourced) + amendment gate | **nothing exists** | **built as the standalone [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) primitive (design complete)** |
| Drift detector (continuous, files issues) | **nothing exists** | **build (deferred to background worker)** |

### Model assignment defaults (per-step metadata, configurable)
`orchestrator = Opus` · `implementer = Sonnet` · `verifier / constitution-checker / intake = Haiku`. These are **per-step `model`/`provider`** values (`provider=claudebox model=sonnet`, …), overridable per workflow. Per-step model selection is `claude --model {opus|sonnet|haiku}` inside the box. The same per-step `metadata` mechanism carries future `role`/`cost_tracking`/`issue` keys (Conductor's per-step `metadata` field is a one-line additive schema change).

### Conductor — tracked extensions (all additive)
1. **`ClaudeboxProvider`** — implements `AgentProvider`; `cb run` to spawn the box + worktree, `cb exec <ctr> claude -p --output-format stream-json` per node, parse the stream into `event_callback`. ~150 LOC, no core edits.
2. **Per-step `metadata: dict`** — one-line schema field on `AgentDef` (currently `extra="forbid"`), for observability/correlation keys.
3. **GitHub adapter** — `gh issue` create/transition/assign + read type, as `script` steps (lives in YAML, not the engine).
4. **Future runtime providers** — `CodexboxProvider`, `GeminiboxProvider`, `ApiProvider` (§0.1), each additive behind the same narrow seam.

> **Standing risk:** the additive surface staying additive. If a needed change starts touching Conductor's core control flow, the **thin-custom engine** off-ramp comes back on the table (it's now a serious contender, not just a fallback — §0). Conductor is MIT — no licensing obstacle. *(The prior Fabro-fork-depth risk is retired by the engine change.)*

---

## 14. Hard rules & domain-wide principles

- **HARD RULE:** No constitution amendment without explicit user consent. Opus proposes; only the human approves.
- **Escalation always climbs** (implementer → orchestrator → human); every loop is bounded; no silent retries.
- **No agent validates its own work** (separate verifier/checker nodes).
- **The artifact is the interface** — fresh thread (fresh `claude` session) per stage, self-contained user-approved hand-offs, no cross-stage context bleed.
- **The event log is never mutated** — ADRs are superseded, not edited; projections are regenerated, not patched.
- **Bugs are always reproduced first** (local-first projects make this universal).
- **Claim before work** — GitHub assign+transition is the concurrency mutex, before the engine starts.
- **Observability lives below the engine** — telemetry is engine-agnostic (LiteLLM/Langfuse/OTel), never an engine feature.
- **Pin external dependencies** — LiteLLM (and the claudebox image) are version-pinned and digest-verified (malware advisory precedent).

---

## 15. Open decisions (deliberately deferred)

| # | Decision | Lean |
|---|---|---|
| 1 | Where the human approval/gate action physically happens (custom issue-board vs Conductor's per-run view vs GitHub) | Custom issue-board for artifact review; GitHub status mirrors; Conductor per-run view as drill-down |
| 2 | Exact engine↔GitHub status-sync timing beyond claim / needs-input / closed | — |
| 3 | Background drift worker mechanism: scheduled run vs self-hosted ECC-style agent ([continuous-learning-v2](https://github.com/affaan-m/ECC/tree/main/skills/continuous-learning-v2)) | Start as a simple scheduled run; self-hosted later |
| 4 | Brownfield constitution extraction | Deferred — greenfield only in v1 |
| 5 | `chore` / `question` workflows; per-project workflow overrides | Deferred — feature + bug only in v1 |
| 6 | Human-facing reference-doc generation | Deferred |
| 7 | Engine: Conductor adopted (§0). Thin-custom off-ramp if the additive surface turns invasive | Stay on Conductor; revisit only if forced into core changes |
| 8 | ~~Observability stack specifics (Langfuse vs Phoenix vs Helicone; self-host topology)~~ **RESOLVED 2026-06-17** | **DECIDED — LiteLLM + Langfuse + Claude Code OTel; see [observability.md](./observability.md) & §12a.** Build-time items (minimal local-first footprint, MIT/`/ee` durability, cost-attribution no-double-count) tracked in observability.md §4. |
| 9 | Multi-*model-runtime* roadmap (which non-Claude model runtimes, when) — §0.1. *Tooling agent-agnosticism is already first-class (§0.4); this row is only about model runtimes.* | Deferred — claudebox first; keep the provider seam narrow |
| 10 | Dashboard build vs adopt for the issue-board; embedded-library vs JSONL-tail ingestion | Lean embedded-library aggregator + GitHub Projects |

> **Resolved 2026-06-16 (see §6b & §0.4):** planning/spec tooling = **extend Spec-Kit** via the committed `kentra` bundle; **#1 gate-action seam** = a structured record file in the spec-folder, read + enforced by Conductor; **runtime-agnostic** = first-class v1 (supersedes the §0.1 defer-lean); **v1 governance scope** = ADR append-only log + projection regeneration + amendment gate + plan-time deviation gate all ship in v1 (only the continuous drift-filer (#3) and brownfield (#4) remain deferred). **#7 engine** stays Conductor.
>
> **Resolved 2026-06-17 (see [observability.md](./observability.md) & §12a):** **#8 observability stack** = **LiteLLM (pinned gateway) + Langfuse (self-hosted obs/eval/persistence) + Claude Code native OTel.** Helicone (→ Mintlify maintenance mode) and TensorZero (reported unmaintained) evaluated and rejected.

---

## 16. Research provenance

- **Engine head-to-head (Fabro vs Conductor):** [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md) (2026-06-16) — source-level analysis of both engines under D1–D3; recommends Conductor. Verified facts: Claude Code has no ACP mode and Fabro has no claude-CLI backend; Conductor is embeddable (`WorkflowEngine`), runs N concurrent runs, clean `AgentProvider` ABC, fresh-subprocess-per-step; Claude Code's official LLM-gateway + OTel support; LiteLLM caveats (passthrough spend gap; 1.82.7/1.82.8 malware advisory).
- **Library analysis:** [references/library-analysis.md](./references/library-analysis.md), [references/technologies.md](./references/technologies.md).
- **Workflow/DAG layer:** [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md).
- **Spec-Kit ecosystem research (2026-06-16):** full report at [references/spec-kit-ecosystem-research.md](./references/spec-kit-ecosystem-research.md) — 23 verified claims / 23 sources / 2 refuted. Key findings: no single tool covers all four target features. Closest single matches: **Spec Kitty** (feature 1), **OpenSpec** (feature 4). ADR substrate: **adr-tools / Log4brains**. Generation-time enforcement: **Mneme HQ**. The **governed multi-doc constitution with amendment gate** and the **continuous background drift scanner that files issues** are unmet by any tool — confirmed gaps we build.
- **Spec-Kit extension-system & catalog investigation (2026-06-16, this session):** source-level review of Spec-Kit v0.10's extension API (`extension.yml`, `before_/after_` lifecycle hooks on all 9 phases, script/`gh` capability, Extensify scaffolder) and the ~172-entry community catalog. Verified: `/speckit.analyze` is a reusable plan-time deviation engine (extracts MUST/SHOULD, flags CRITICAL, cites locations); projection regeneration exists in the wild (`spec-kit-arch`, `repoindex`); an approval-state model exists (`spec-validate`); a bounded-gate pattern exists (`superpowers-bridge`). Confirmed limits: extension hooks are **non-blocking / agent-honored** (hard gates must live in Conductor), the `effect` tag is advisory (not a sandbox), `.specify/` survival across `init --force` is unspecified, and **no extension provides** the immutable ADR substrate, projection-from-specs+ADRs, the Projects-v2 claim, or a continuous drift-filer. All relevant extensions are single-author/low-star — **mined as reference patterns, not taken as dependencies**. Basis for the §6b decision.
