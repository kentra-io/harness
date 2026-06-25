# MVP Plan — Design & Phased Build

*Generated: 2026-06-25. Status: **DESIGN — pending user review.** Companion to [planning.md](./planning.md) (planning domain), [observability.md](./observability.md) (telemetry plane), [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md), and [references/](./references/). This document consolidates the MVP scope decisions made 2026-06-25 and turns the settled architecture into a sequenced build.*

> **What this document is.** The MVP scope + build plan for the harness. [planning.md](./planning.md) and [observability.md](./observability.md) decided *what the system is*; this decides *what ships in the MVP, in what order, and the few things still to verify at build time*. Every decision below was made deliberately in a scoping session; deferred items are flagged as deferred, not silently dropped.

---

## 0. Scope decisions (2026-06-25)

| # | Question | Decision |
|---|---|---|
| **S1** | MVP ambition | **Full v1 as written in planning.md** — all four governance pieces, both workflows, issue-level concurrency, thin dashboard. *Not* a thin slice. |
| **S2** | First workflow built | **Feature first** (the full refine→design→plan→execute pipeline) as the proving ground; `bug.yaml` follows in the same v1. |
| **S3** | Concurrency | **Multi-issue parallel from the start** — the claim-mutex, agent identity, and concurrent runs are in-scope. |
| **S4** | Observability depth | **Stand-up + tagging only.** Full decided stack (LiteLLM + Langfuse + Claude Code OTel) via local compose, issue-ID tagging, eyeball traces/cost in Langfuse UI. **No** custom cost-rollups, complexity-estimation, or config-eval datasets in MVP. |
| **S5** | Human approval gate | **Interactive agent session**, agent-agnostic — see §2. Planning stages are interactive; the human approves conversationally. No custom approval web-UI on the critical path. |
| **S6** | Control flow | **Interactive planning, headless execution.** One Conductor workflow, **two driving modes** — see §2. |
| **S7** | Execution launch | **Host watcher daemon** starts the headless execution run once a plan is approved + claimed. |
| **S8** | Execution transitions | **Conductor-native routing** in the headless loop (verifier output drives routes; final transition via `gh` script step). Explicit MCP transition calls are reserved for the interactive half. |
| **S9** | Conductor consumption | **Tiny-patch fork + external plugins** (pin by SHA) — see §4. |
| **S10** | Build sequencing | **Walking skeleton first** — see §6. |
| **S11** | Target project | First instance is **`kafka-dq`** (greenfield, not yet created). The **harness is standalone and portable** across projects — nothing `kafka-dq`-specific lives in it (§5). |
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

---

## 3. Component inventory

**Adopted (off-the-shelf / forked, not built):**
- **Conductor** — workflow engine (tiny-patch fork, §4).
- **Spec-Kit** — planning/spec layer, extended via the committed `kentra` bundle ([planning.md §6b](./planning.md)).
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
| **`kentra` Spec-Kit bundle** | Preset templates (requirements func+NFR / design / plan-with-validation-contracts) + intent-named commands (`requirements`/`design`/`plan`/`analyze`/`adr`/`regen`/`constitution-init`) + docs-stage `regen` hook. | planning.md §6b |
| **Governance core** | Immutable append-only ADR log + supersede semantics; projection regeneration; amendment gate (HARD RULE); plan-time + code-time deviation gates. | planning.md §7–8 |
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
  kentra/                              Spec-Kit bundle (preset + extension + hooks)
  governance/                         ADR log, projection regen, amendment + deviation gates
  workflows/feature.yaml  bug.yaml     Level-A lifecycle definitions
  dashboard/                           thin live issue-board overlay
  deploy/compose.yml                   LiteLLM (pinned+digest) + Langfuse local profile
```

**Why this layout:** keeps the upstream divergence to the minimum that *cannot* be external, so rebasing onto new Conductor releases is trivial and our logic is never entangled with the engine. MIT — no licensing obstacle. The **thin-custom-engine off-ramp** ([planning.md §0](./planning.md)) stays live if the in-fork delta ever has to grow into core control flow.

---

## 5. Portability model — standalone harness, per-project config

The harness is a **standalone tool**; `kafka-dq` is just its first tenant. The split:

- **Harness repo owns** (project-neutral): the Conductor fork, all plugins (provider, MCP, GitHub adapter, watcher, governance core), the shared `feature.yaml` / `bug.yaml` lifecycles, the `kentra` bundle source, the obs compose profile.
- **Each target project repo commits** (project-specific, self-contained): `.specify/` (pinned Spec-Kit tooling + the `kentra` bundle + `memory/` constitution) and `.claudebox/Dockerfile` (the project's stack + Spec-Kit CLI install), plus per-issue spec-folders ([planning.md §6b](./planning.md)). A target project is reproducible **without** the harness.
- **Nothing `kafka-dq`-specific enters the harness.** Stack/language specifics live only in `kafka-dq`'s committed `.claudebox/` + `.specify/memory/` (constitution). `constitution-init` interviews to seed `kafka-dq`'s principles greenfield.

> `kafka-dq` does not exist yet. The design is written against a **representative greenfield profile** (local-first service with testcontainers, per [planning.md §11](./planning.md) — which makes every bug reproducible). Its concrete stack is a build-time input confirmed when the repo is created.

---

## 6. Phased build — walking skeleton first

Each phase has a hard **Definition of Done**; nothing is "complete" without proving it (per the user's verification-before-done principle).

### Phase 1 — Walking skeleton (de-risk the integration spine)
Stand up the whole spine **end-to-end with no-op/stub stages**, so every integration unknown is hit on day one.
- Fork + pin Conductor; apply the `AgentDef.metadata` patch; **spike the externally-driven wait-step seam** (§7).
- `ClaudeboxProvider` (minimal): `cb run` + `cb exec … claude -p` running a trivial echo step; parse the stream into `event_callback`.
- GitHub adapter: read type, **claim via one bot + lock label**, transition (`gh` script steps).
- Conductor-MCP **skeleton**: `get_state` / `submit_artifact` / `run_gate` / `record_approval` / `request_transition` reading+writing spec-folder records.
- Host watcher daemon: detect *planned & claimed* → `engine.run()`; subscribe to the event bus.
- Obs stack: local compose for **LiteLLM (pinned + digest-verified)** + **Langfuse** + Claude Code OTel; point a box's `claude` at `ANTHROPIC_BASE_URL`; inject the **issue-ID tag**.
- `feature.yaml` + `bug.yaml` with **stub** stages.

**DoD:** a GitHub issue flows *idea → done* through stub stages; an interactive session advances the planning stubs via Conductor-MCP; one headless stub step runs in a `cb` box; a trace **and a cost row tagged by issue** appear in Langfuse; **two issues run concurrently** with the lock-label mutex holding.

### Phase 2 — Real interactive planning (Mode A)
- `kentra` bundle: preset templates (requirements **func + NFR distinct**, design, plan with **per-milestone validation contracts**) + intent-named commands + bare aliases.
- `constitution-init`: greenfield principles interview → `.specify/memory/`.
- Plan-time deviation gate: `speckit.kentra.analyze` → `deviation.json` → **Conductor enforces** block/proceed at the gate step.
- Wire the interactive session: human runs an agent session with the `kentra` skills + Conductor-MCP configured; refine → design → plan, each writing `approval-state.json` on conversational approval.

**DoD:** a real feature goes *idea → planned* interactively; the constitution is bootstrapped; all three artifacts are produced and user-approved; a **deliberately planted violation is blocked** by the plan-time gate.

### Phase 3 — Real headless execution (Mode B)
- `ClaudeboxProvider` (real): per-step model selection — `implementer = Sonnet`, `verifier = Haiku`, `orchestrator = Opus`.
- Per-milestone loop in the single worktree; **bounded retry** (3 attempts) fed the verifier's rejection reasons; **Opus orchestrator escalation** (2 attempts, may go straight to human at its discretion); `human_gate` → GitHub `Needs Input`.
- Final whole-issue **integration check vs original requirements** + **code-time constitution check** on the full diff.
- Conductor-native routing throughout; final transition via `gh` script step.

**DoD:** a planned feature executes headlessly through its milestones; a deliberate verify-fail walks retry → orchestrator → human gate; integration + code-time constitution checks run on the full diff; the issue reaches *done*.

### Phase 4 — Governance depth + bug flow + dashboard
- Governance core: immutable append-only **ADR log + supersede** semantics; **projection regeneration** (`regen` on the docs-stage `after_*` hook); **amendment gate** (HARD RULE — Opus proposes, human approves).
- `bug.yaml` (real): **repro-first** failing test = requirement *and* contract; design skipped by default; **promotion hatch** to the feature pipeline when Opus judges the fix touches architecture.
- Thin dashboard: live multi-run issue-board overlay off the host daemon's event-bus subscription — the **"where is everything / what needs me"** view. It **surfaces** pending gates and **routes** the human into the interactive session that owns the decision (artifact approval stays conversational per S5); it is **not** itself the approval surface. Reuse Conductor's per-run web view as the drill-down; GitHub Projects as the durable board.

**DoD:** full feature **and** bug lifecycles run; an ADR is appended and a projection regenerated; an amendment is **proposed and human-approved** (and a self-approval attempt is rejected); the dashboard shows live multi-run state and correctly surfaces a pending gate, routing into the interactive session that resolves it.

---

## 7. Spike / risk items (verify at build time, not blockers)

| # | Item | Phase | Why it matters |
|---|---|---|---|
| **R1** | **Conductor "externally-driven step waits on MCP input"** — can a planning stage be a step that Conductor *waits on* while the interactive session advances it (likely via `human_gate` repurposing), without core edits? | 1 | The whole Mode-A model rests on this. If unreachable, it becomes part of the fork patch. |
| **R2** | **`AgentDef.metadata` reachability** — current schema is `extra="forbid"`; confirm the one-line field is the only edit needed for correlation keys. | 1 | Determines fork-patch size. |
| **R3** | **LiteLLM Anthropic `/v1/messages` passthrough spend tracking** (reported 2026 gap) — **define each model explicitly in `config.yaml`**; confirm cost rows fire. | 1 | Cost-per-issue depends on it. |
| **R4** | **LiteLLM supply chain** — pin version + **verify image digest** (1.82.7 / 1.82.8 shipped credential-stealing malware); `CLAUDE_CODE_ATTRIBUTION_HEADER=0` if caching at the proxy. | 1 | Security. |
| **R5** | **Langfuse local footprint** — Postgres + ClickHouse (+ Redis + S3/MinIO); confirm a **minimal single-node compose** is comfortable on the dev machine; document the slim profile. | 1 | The main concession to local-first ([observability.md §4.1](./observability.md)). |
| **R6** | **Cost authority / no double-count** — full Claude Code session groups under one `sessionId`; decide LiteLLM-vs-Langfuse as the authoritative cost source. | 1–2 | Correct per-issue rollups. |
| **R7** | **Concurrent `engine.run()`** under the host daemon — N runs, one event bus per run, joinable by issue tag. | 1 | Multi-issue parallel. |
| **R8** | **`.specify/` survival across `init --force`** (unspecified upstream) — confirm the committed bundle isn't clobbered. | 2 | Portability/reproducibility. |

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

- **`kafka-dq` stack** — concrete language/runtime for `.claudebox/Dockerfile` + testcontainers, set when the repo is created (§5).
- **Conductor↔GitHub status-sync timing** beyond claim / needs-input / closed ([planning.md §15 #2](./planning.md)).
- **Bot identity provisioning** — the single GitHub App/bot account + the lock-label scheme details (§S3; [planning.md §4](./planning.md)).
- **Conductor-MCP tool surface** — exact tool list/contract (skeleton in Phase 1, firmed in Phase 2).

---

## 10. Provenance

Scope decisions S1–S12 were made in a structured scoping session (2026-06-25) over [planning.md](./planning.md) and [observability.md](./observability.md). The control-flow model (§2), the **Conductor-MCP server**, and the **host watcher daemon** are this session's additions to planning.md and should be reflected back into it on its next edit (companion-sync, mirroring [observability.md §7](./observability.md)).
