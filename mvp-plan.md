# MVP Plan — Design & Phased Build

*Generated: 2026-06-25. Status: **DESIGN — pending user review.** Companion to [planning.md](./planning.md) (planning domain), [observability.md](./observability.md) (telemetry plane), [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md), and [references/](./references/). This document consolidates the MVP scope decisions made 2026-06-25 and turns the settled architecture into a sequenced build.*

> **What this document is.** The MVP scope + build plan for the harness. [planning.md](./planning.md) and [observability.md](./observability.md) decided *what the system is*; this decides *what ships in the MVP, in what order, and the few things still to verify at build time*. Every decision below was made deliberately in a scoping session; deferred items are flagged as deferred, not silently dropped.

> **STATUS BANNER — 2026-07-02, updated same day (read first).** Three shifts since this was written (mirrors [planning.md](./planning.md)'s banner): **(1)** the **constitution is a standalone primitive** — [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) (submodule; design complete), a **Phase-1 dependency**. **(2)** the **planning module is now also a standalone primitive**: [**`spec-lifecycle`**](./spec-lifecycle/spec-lifecycle.md) (submodule; design pending review) — **P0 is DECIDED: OpenSpec (pinned) as artifact runtime + Superpowers co-installed + the novel spine (gates/records/contracts/bug flow) built in the primitive.** Spec-Kit eliminated ([research](./references/sdd-framework-research-2026-07.md)). Every "Spec-Kit"/"`kentra` bundle" mention below is superseded; the "Lifecycle bundle" component row = `spec-lifecycle`. **(3)** Phase 1's lifecycle work = **implementing `spec-lifecycle` per its spec** (CLI `init/approve/status/guard`, the `kentra-spec-lifecycle` OpenSpec schema, stage skills) — [planning.md §6c](./planning.md) is fully resolved, so "first resolve the opens" no longer gates the build. Note: constitution-primitive v1 integration is folder+pointer (its framework adapters are deferred), which `spec-lifecycle`'s seam already assumes.

---

## 0. Scope decisions (2026-06-25)

| # | Question | Decision |
|---|---|---|
| **S1** | MVP ambition | **Full v1 as written in planning.md** — all four governance pieces, both workflows, issue-level concurrency, thin dashboard. *Not* a thin slice. |
| **S2** | First workflow built | **Feature first** (the full refine→design→plan→execute pipeline) as the proving ground; `bug.yaml` follows in the same v1. |
| **S3** | Concurrency | **Multi-issue parallel from the start** — the claim-mutex, agent identity, and concurrent runs are in-scope. |
| **S4** | Observability depth | **Stand-up + tagging only**, and **sequenced after the planning domain** (S10). Full decided stack (LiteLLM + Langfuse + Claude Code OTel) via local compose, issue-ID tagging, eyeball traces/cost in Langfuse UI. **No** custom cost-rollups, complexity-estimation, or config-eval datasets in MVP. |
| **S5** | Human approval gate | **Interactive agent session**, agent-agnostic — see §2. Planning stages are interactive; the human approves conversationally. No custom approval web-UI on the critical path. |
| **S6** | Control flow | **Interactive planning, headless execution.** One Conductor workflow, **two driving modes** — see §2. |
| **S7** | Execution launch | **Host watcher daemon** starts the headless execution run once a plan is approved + claimed. |
| **S8** | Execution transitions | **Conductor-native routing** in the headless loop (verifier output drives routes; final transition via `gh` script step). Explicit MCP transition calls are reserved for the interactive half. |
| **S9** | Conductor consumption | **Tiny-patch fork + external plugins** (pin by SHA) — see §4. |
| **S10** | Build sequencing | **Planning domain first, standalone** (no engine, no obs), **then dogfood it** to plan the rest of the system — see §6. *(Revised 2026-06-25 from the earlier "walking skeleton first"; engine + execution + obs are planned using the planning vertical itself.)* |
| **S11** | Target project | First instance is **`kafka-dq`** — **created now as an empty greenfield shell** to be Phase-1's testbed (§6). Phase 1 produces its real constitution + first issue artifacts. The **harness is standalone and portable** across projects — nothing `kafka-dq`-specific lives in it (§5). Its **stack is an *output* of the planning vertical's design stage** (an ADR), not a pre-chosen input. |
| **S12** | Topology | **Fully local.** Everything on the dev machine: Conductor control-plane + host daemon + Conductor-MCP on the host, agent sessions in `cb` boxes, LiteLLM + Langfuse via a local compose profile. No cloud in MVP. |

---

## 1. What the MVP is, in one paragraph

A **standalone, portable harness** that drives a greenfield project's engineering lifecycle end-to-end: a human opens an **interactive agent session** to take a GitHub issue from *idea → planned* (refinement → design → plan, each a user-approved artifact, governed by an event-sourced constitution), then a **host daemon** hands the approved plan to **Conductor**, which drives a **headless** implement→verify loop inside a per-issue `claudebox` until the issue is *done*. Multiple issues run in parallel, mutexed by a GitHub claim. All model traffic flows through a local **LiteLLM** gateway into **Langfuse** for traces/cost, tagged by issue. The first project it operates on is `kafka-dq`; the harness itself knows nothing about `kafka-dq`.

---

## 2. The control-flow model — one workflow, two driving modes

This is the load-bearing refinement of [planning.md](./planning.md) made this session. planning.md §6 assumed *every* stage is a headless `claude -p` subprocess. It is not. The lifecycle is **one Conductor workflow per type**, but its stages are driven two different ways:

```
  feature.yaml  (ONE Conductor run, spans the whole lifecycle)

  ── MODE A: externally-driven (interactive) ──────────────────
   Human ⇄ interactive agent session
     • uses the kentra Spec-Kit skills/commands to produce artifacts
     • talks to Conductor via the Conductor-MCP server:
         get_state · submit_artifact · run_gate · record_approval · request_transition
     refine ──▶ design ──▶ plan        (approve conversationally at each gate)
        │  Conductor WAITS on these steps (human-advanced), records artifacts + approval records
        ▼  status = planned, plan artifact committed to the issue's spec-folder
  ── handoff (host watcher daemon: "planned & claimed" → engine.run()) ──
  ── MODE B: engine-driven (headless) ─────────────────────────
   Conductor drives, no human present:
     implement (Sonnet) ──▶ verify (Haiku, against the milestone contract) ──▶ next
     • per-step model/provider via ClaudeboxProvider (cb run / cb exec claude -p)
     • Conductor-native routing on verifier output; bounded retry; Opus escalation; human_gate
     final whole-issue integration check + code-time constitution check
        │  final transition via `gh` script step
        ▼  status = done   (or → Needs Input on escalation, re-opening an interactive session)
```

**Two relationships to the same engine:**
- **Mode A — Conductor is a passive state authority + gate enforcer** that the interactive session *calls* (via MCP). It does **not** spawn these sessions; it waits on them. *(Mechanism: planning-stage steps modeled as `human_gate`-style waiting steps — a **spike item**, §7.)*
- **Mode B — Conductor is its native active engine**: it *spawns* headless steps via `ClaudeboxProvider` and routes on their output.

**Agent-agnostic by construction.** The interactive half talks to Conductor only through the **Conductor-MCP** server and the **structured gate records** (`approval-state.json` / `deviation.json`) in the spec-folder — never a Claude-specific UI. Any MCP-capable agent (Claude Code, Cursor, Codex, …) can drive planning, satisfying the runtime-agnostic v1 requirement ([planning.md §0.4](./planning.md), [tasks/lessons.md](./tasks/lessons.md)).

> **Build order note (S10).** Phase 1 builds **Mode A *minus the engine*** — the interactive planning flow + governance + the gate *records*, with the human-in-the-loop honoring surfaced deviations. **Hard enforcement** (Conductor reading the records and *blocking*) and the **state authority** arrive when the engine is wired in Phase 2. The seam (structured records) is identical either way, so this is additive, not rework.

---

## 3. Component inventory

**Adopted (off-the-shelf / forked, not built):**
- **Conductor** — workflow engine (tiny-patch fork, §4).
- **SDD framework (TBD: spec-kit / openspec / superpowers)** — planning/spec layer; if Spec-Kit, extended via the committed `kentra` bundle ([planning.md §6b/§6c-P0](./planning.md)).
- **GitHub Issues + Projects v2** — system of record + coarse status.
- **`cb` / claudebox** — execution sandbox (reused, not rebuilt).
- **LiteLLM (pinned) + Langfuse + Claude Code OTel** — observability plane ([observability.md](./observability.md)).

**Built by us (lives in the harness repo as plugins, §4):**

| Component | Role | New vs planning.md? |
|---|---|---|
| **`ClaudeboxProvider`** | Conductor `AgentProvider`: `cb run` box+worktree, `cb exec … claude -p`, parse stream → events. ~150 LOC. | planning.md §13 |
| **GitHub adapter** | `gh` script steps: read issue type, **claim (one bot + lock label)**, transition, assign. | planning.md §5 |
| **Conductor-MCP server** | The agent-agnostic interface for **Mode A**: exposes lifecycle state, artifact submission, gate execution/enforcement, transitions to the interactive session. **This is the spine of the interactive half.** | **NEW (this session)** |
| **Host watcher daemon** | Long-lived host service: watches for *plan-approved & claimed* issues → `engine.run()` per issue; subscribes to each run's event bus (also the dashboard aggregator). | **NEW (this session — formalizes §12a)** |
| **`spec-lifecycle`** (submodule) | The planning module, specced: `lifecycle` CLI (`init`/`approve`/`status`/`guard`), custom `kentra-spec-lifecycle` OpenSpec schema (proposal / specs-delta / design / `plan.md` w/ validation contracts), stage skills, `approval-state.json`, living-spec fold + replay guard, bug profile, constitution seam. Runs on **OpenSpec (pinned)**; Superpowers co-installed. | [primitive spec](./spec-lifecycle/spec-lifecycle.md) |
| **`adr-sourced-constitution`** (submodule) | Immutable append-only ADR log + supersede/deprecate; deterministic `constitution.md` projection (`regen`); `constitution init`; plan-validation gate (→ `deviation.json`). Go CLI + agent-agnostic skills. **Design complete; a Phase-1 dependency.** | [primitive spec](./adr-sourced-constitution/adr-sourced-constitution.md) |
| **Governance wiring (harness side)** | Amendment gate (HARD RULE) consent enforcement + Conductor reading the primitive's records and blocking; code-time deviation gate (execution domain). | planning.md §7–8 |
| **Thin dashboard** | Live multi-run "issue board" overlay (host daemon event-bus subscription) + gate-answer; Conductor per-run view as drill-down; GitHub Projects as durable board. | planning.md §12a |
| **Conductor fork patch** | Minimal in-fork delta: `AgentDef.metadata` field; any provider/MCP/wait-step seam that can't be reached externally. | planning.md §13 |

---

## 4. Conductor consumption — tiny-patch fork + external plugins

```
fork/conductor   (pinned by SHA; our delta is a small, reviewable patch series)
  + AgentDef.metadata field            ← one-line (current schema is extra="forbid")
  + provider / MCP / wait-step seam    ← only if not externally reachable (spike, §7)

harness/  (our repo — all logic lives here, against Conductor's public ABCs)
  providers/claudebox_provider.py      ClaudeboxProvider
  mcp/conductor_mcp.py                 Conductor-MCP server (Mode A interface)
  adapters/github.py                   gh claim/transition/read-type
  watcher/daemon.py                    host watcher + event-bus aggregator
  lifecycle/                           SDD-framework bundle (TBD: spec-kit/openspec/superpowers) — templates + commands + hooks
  adr-sourced-constitution/            submodule: ADR log, projection regen, plan-validation gate (Go CLI + skills)
  governance/                         harness-side wiring: consent enforcement + Conductor gate-blocking on the primitive's records
  workflows/feature.yaml  bug.yaml     Level-A lifecycle definitions
  dashboard/                           thin live issue-board overlay
  deploy/compose.yml                   LiteLLM (pinned+digest) + Langfuse local profile
```

**Why this layout:** keeps the upstream divergence to the minimum that *cannot* be external, so rebasing onto new Conductor releases is trivial and our logic is never entangled with the engine. MIT — no licensing obstacle. The **thin-custom-engine off-ramp** ([planning.md §0](./planning.md)) stays live if the in-fork delta ever has to grow into core control flow.

---

## 5. Portability model — standalone harness, per-project config

The harness is a **standalone tool**; `kafka-dq` is just its first tenant. The split:

- **Harness repo owns** (project-neutral): the Conductor fork, all plugins (provider, MCP, GitHub adapter, watcher, governance core), the shared `feature.yaml` / `bug.yaml` lifecycles, the `kentra` bundle source, the obs compose profile.
- **Each target project repo commits** (project-specific, self-contained): the chosen SDD framework's committed tooling/bundle (e.g. `.specify/` if Spec-Kit) + the framework-neutral **`constitution/`** folder + `.claudebox/Dockerfile` (the project's stack + the `constitution` CLI baked in, + the framework CLI once P0 is decided), plus per-issue spec-folders. A target project is reproducible **without** the harness.
- **Nothing `kafka-dq`-specific enters the harness.** Stack/language specifics live only in `kafka-dq`'s committed `.claudebox/` + its `constitution/` folder (+ the chosen framework's config dir, once P0 is decided). `constitution init` interviews to seed `kafka-dq`'s principles greenfield.

> `kafka-dq` is **created now as an empty greenfield shell** (the Phase-1 testbed, §6). Its concrete stack is **not pre-chosen** — it falls out of the planning vertical's design stage as an ADR, targeting a local-first service with testcontainers ([planning.md §11](./planning.md) — which makes every bug reproducible). The shell needs only enough to commit `.specify/` + `.claudebox/`.

---

## 6. Phased build — planning domain first, then dogfood

**Strategy (S10).** Build the **standalone planning vertical** first — no engine, no observability. It is the best-specified and most differentiating part of the system, and once it exists it becomes **the tool we use to plan everything else** (engine integration, execution, obs, dashboard). Each phase has a hard **Definition of Done**; nothing is "complete" without proving it (per the user's verification-before-done principle).

### Phase 1 — Standalone planning vertical (no engine, no obs)
The interactive idea→plan flow + the event-sourced constitution, producing governed, user-approved artifacts in the issue's spec-folder. This is **Mode A minus hard enforcement** (§2 build-order note): the plan-time gate *surfaces* deviations and the human-in-the-loop honors them; the engine that *blocks* comes in Phase 2. **Two sub-parts** now that the constitution is a separate primitive:

*Prerequisite — the constitution primitive.* Build/consume [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md) (Go CLI + agent-agnostic skills; design complete): `constitution init` (greenfield principles interview), the immutable **ADR log + supersede/deprecate**, the deterministic **`constitution.md` projection** (`regen`), and the **plan-validation gate** (→ `deviation.json`, each finding citing an `ADR-id`). Framework-neutral; integrates via a `constitution/` folder + an AGENTS.md/CLAUDE.md pointer. *(living-spec — the multi-feature-spec projection — is a separate, out-of-scope module — [planning.md §6c-P6](./planning.md).)*

*The lifecycle layer (the planning module proper) — now specced as [`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md); Phase 1 = implement it:* ([planning.md §6c](./planning.md) is fully resolved — OpenSpec runtime, 3 stages, no tasks stage, NFR routing, files-canonical.)
- The `kentra-spec-lifecycle` OpenSpec schema (proposal / specs-delta / design / **`plan.md` with per-milestone validation contracts**) + the `lifecycle` CLI (`init`/`approve`/`status`/`guard`, incl. the living-spec **replay guard**) + stage skills (refine/design/plan/bug/archive).
- Plan-time deviation gate wired in from the constitution primitive at gates 2 and 3: surfaces deviations, human honors them (**no engine block yet**).
- Amendment gate (HARD RULE): per-ADR human consent at gate 2 (design's ADR proposals → `constitution adr new` on acceptance; the primitive's consent policy set to *strict* for our projects).
- The interactive flow: a human runs an agent session with the lifecycle skills + the constitution loaded; refine → design → plan, each writing artifacts + a hash-anchored `approval-state.json` gate entry on conversational approval; archive folds the delta into the living spec.

**DoD:** on a greenfield target, a human drives an issue *idea → plan* interactively; the constitution is bootstrapped via `constitution init`; all lifecycle artifacts are produced, governed, and user-approved in the spec-folder; an ADR can be appended and `constitution.md` regenerated; a **deliberately planted violation is surfaced** by the plan-validation gate, citing the `ADR-id`; an amendment requires explicit human consent. Agent-agnostic: the flow uses only the lifecycle commands + the constitution's folder/records + spec-folder records (no engine dependency).

### 🐕 Milestone — dogfood: plan the rest of the system
Use the Phase-1 planning vertical to **plan Phases 2–4 of the harness itself** (engine integration, execution domain, obs, dashboard) as governed requirements/design/plan artifacts. This both delivers the plan for the remaining system *and* is the first real exercise of the planning domain. *(Caveat: the harness repo is brownfield, so this uses the `kentra` planning commands conversationally rather than the greenfield `constitution-init` path; brownfield constitution extraction stays deferred — §8.)*

### Phase 2 — Engine integration (Mode A hard enforcement + lifecycle state)
- Conductor **tiny-patch fork** (§4): `AgentDef.metadata`; **spike the externally-driven wait-step seam** (R1, §7).
- **Conductor-MCP** server: `get_state` / `submit_artifact` / `run_gate` / `record_approval` / `request_transition` over the spec-folder records.
- GitHub adapter: read type, **claim via one bot + lock label**, transition (`gh` script steps).
- Promote the Phase-1 gates from *surface-and-honor* to **hard enforcement**: Conductor reads `deviation.json` / `approval-state.json` and blocks/proceeds; lifecycle state becomes the engine's run-state.

**DoD:** the same *idea → planned* flow now runs through Conductor as one workflow (Mode A); the plan-time gate **hard-blocks** a planted violation; two issues claim+advance concurrently with the lock-label mutex holding.

### Phase 3 — Execution domain (Mode B, headless)
- `ClaudeboxProvider`: `cb run` box+worktree, `cb exec … claude -p`, stream→events; per-step model (`implementer = Sonnet`, `verifier = Haiku`, `orchestrator = Opus`).
- Host watcher daemon: *plan-approved & claimed* → `engine.run()`; subscribe to the event bus.
- Per-milestone loop; **bounded retry** (3 attempts) fed verifier rejections; **Opus escalation** (2 attempts, may go straight to human); `human_gate` → GitHub `Needs Input`.
- Final whole-issue **integration check vs original requirements** + **code-time constitution check** on the full diff; Conductor-native routing; final transition via `gh` script step.
- `bug.yaml`: **repro-first** failing test = requirement *and* contract; design skipped; **promotion hatch** to the feature pipeline.

**DoD:** a planned feature executes headlessly through milestones; a deliberate verify-fail walks retry → orchestrator → human gate; integration + code-time constitution checks run; the issue reaches *done*; a bug runs repro→fix→done.

### Phase 4 — Observability + dashboard
- Obs stack: local compose for **LiteLLM (pinned + digest-verified)** + **Langfuse** + Claude Code OTel; route boxes via `ANTHROPIC_BASE_URL`; inject the **issue-ID tag**.
- Thin dashboard: live multi-run issue-board overlay off the host daemon's event-bus subscription — the **"where is everything / what needs me"** view. It **surfaces** pending gates and **routes** the human into the interactive session that owns the decision (artifact approval stays conversational per S5); it is **not** itself the approval surface. Reuse Conductor's per-run web view as the drill-down; GitHub Projects as the durable board.

**DoD:** a trace **and a cost row tagged by issue** appear in Langfuse for a full run; the dashboard shows live multi-run state and correctly surfaces a pending gate, routing into the interactive session that resolves it.

---

## 7. Spike / risk items (verify at build time, not blockers)

| # | Item | Phase | Why it matters |
|---|---|---|---|
| **R8** | **`.specify/` survival across `init --force`** (unspecified upstream) — confirm the committed bundle isn't clobbered. | 1 | Portability/reproducibility; first thing the planning vertical touches. |
| **R1** | **Conductor "externally-driven step waits on MCP input"** — can a planning stage be a step that Conductor *waits on* while the interactive session advances it (likely via `human_gate` repurposing), without core edits? | 2 | The whole Mode-A model rests on this. If unreachable, it becomes part of the fork patch. |
| **R2** | **`AgentDef.metadata` reachability** — current schema is `extra="forbid"`; confirm the one-line field is the only edit needed for correlation keys. | 2 | Determines fork-patch size. |
| **R7** | **Concurrent `engine.run()`** under the host daemon — N runs, one event bus per run, joinable by issue tag. | 3 | Multi-issue parallel. |
| **R3** | **LiteLLM Anthropic `/v1/messages` passthrough spend tracking** (reported 2026 gap) — **define each model explicitly in `config.yaml`**; confirm cost rows fire. | 4 | Cost-per-issue depends on it. |
| **R4** | **LiteLLM supply chain** — pin version + **verify image digest** (1.82.7 / 1.82.8 shipped credential-stealing malware); `CLAUDE_CODE_ATTRIBUTION_HEADER=0` if caching at the proxy. | 4 | Security. |
| **R5** | **Langfuse local footprint** — Postgres + ClickHouse (+ Redis + S3/MinIO); confirm a **minimal single-node compose** is comfortable on the dev machine; document the slim profile. | 4 | The main concession to local-first ([observability.md §4.1](./observability.md)). |
| **R6** | **Cost authority / no double-count** — full Claude Code session groups under one `sessionId`; decide LiteLLM-vs-Langfuse as the authoritative cost source. | 4 | Correct per-issue rollups. |

---

## 8. Deferred — explicitly *not* in MVP (later plans)

| Item | Why deferred | Source |
|---|---|---|
| Continuous **drift detector** / background sweep that files issues | Background worker; non-blocking; the two gates (plan-time + code-time) cover MVP | [planning.md §8, §15](./planning.md) |
| **Brownfield** constitution extraction | Greenfield only in v1; `kafka-dq` is greenfield | [planning.md §7, §15](./planning.md) |
| `chore` / `question` workflows; per-project workflow **overrides** | Feature + bug cover v1 | [planning.md §15](./planning.md) |
| **Human-facing reference docs** (README/architecture/API/onboarding) generation | Secondary; projections exist, human docs derive later | [planning.md §12, §15](./planning.md) |
| **Non-Claude model runtimes** (Codex/Gemini boxes, raw-API) | Provider seam stays narrow but unused; *tooling* is already agent-agnostic | [planning.md §0.1, §15](./planning.md) |
| Observability **custom layer** — cost-per-issue rollups, complexity-estimation, Langfuse datasets/experiments config-eval | Stand-up + tagging only in MVP (S4); the tagging makes these cheap to add later | [observability.md §5](./observability.md) |

---

## 9. Open items to confirm at build time

- **`kafka-dq` stack** — *decided by* the planning vertical's design stage (an ADR), not pre-set; the `.claudebox/Dockerfile` + testcontainers setup is finalized when execution lands (Phase 3). The empty shell is created now (§5, §6).
- **Conductor↔GitHub status-sync timing** beyond claim / needs-input / closed ([planning.md §15 #2](./planning.md)).
- **Bot identity provisioning** — the single GitHub App/bot account + the lock-label scheme details (§S3; [planning.md §4](./planning.md)).
- **Conductor-MCP tool surface** — exact tool list/contract (skeleton in Phase 1, firmed in Phase 2).

---

## 10. Provenance

Scope decisions S1–S12 were made in a structured scoping session (2026-06-25) over [planning.md](./planning.md) and [observability.md](./observability.md). The control-flow model (§2), the **Conductor-MCP server**, and the **host watcher daemon** are this session's additions to planning.md and should be reflected back into it on its next edit (companion-sync, mirroring [observability.md §7](./observability.md)).
