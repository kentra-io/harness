# Planning Domain — Design & Implementation Analysis

*Generated: 2026-06-16 | Companion to [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md), [references/technologies.md](./references/technologies.md), [references/library-analysis.md](./references/library-analysis.md), and [references/spec-kit-ecosystem-research.md](./references/spec-kit-ecosystem-research.md).*

> **What this document is.** The design for the harness's **planning domain** — the entire issue lifecycle from intake to docs — and an honest assessment of how much of it the reference library covers off-the-shelf versus how much we build. Produced through a structured grilling session; every decision below was made deliberately, and deferred decisions are flagged as such rather than silently resolved.
>
> **Scope — the whole planning domain:** GitHub issue intake → workflow definitions (feature, bug) → refinement → technical design → planning → plan execution → docs maintenance, plus the architectural constitution that governs all of it.

---

## 1. Verdict

**No single library tool implements the planning domain.** The closest matches each cover one slice:

- **Fabro** (workflow engine) — owns workflow definition, visualization, per-node metadata, orchestration, git-checkpoint resume, observability. **Adopted as the engine, forked.**
- **GitHub Issues + Projects v2** — system of record for issues and coarse lifecycle state.
- **Spec-Kit / OpenSpec / adr-tools / Log4brains / Mneme** — references for the spec-management and constitution layers; **none adopted wholesale** — we steal proven *models* from each.

The two genuinely **novel** pieces — a **governed multi-document constitution with a human amendment gate**, and a **continuous drift detector that files issues** — exist in *no tool, in no combination* (confirmed by dedicated research, 2026-06-16). These are the harness's differentiators and we build them regardless.

**Custom work required, in rough order of effort:** (1) Fabro claudebox sandbox executor; (2) Fabro GitHub adapter; (3) Fabro UI extension for workflow visualization + custom node metadata; (4) the spec-management layout + event-sourced constitution; (5) the deviation/amendment governance; (6) the drift detector (deferred to a background worker). Everything else is off-the-shelf (Fabro, GitHub, Claude Code subagents) or borrowed convention.

---

## 2. The two-level workflow model

The single most important structural decision. There are **two distinct things** that were both being called "workflow," and they use different machinery:

- **Level A — the per-issue lifecycle.** An issue moves through *states* (e.g. feature: `idea → refined → designed → planned → in-progress → in-review → done`). This is a **status-driven state machine**, one definition per issue *type*. *This* is what "workflow definition" means — `feature` and `bug` are two different Level-A workflows.
- **Level B — execution within a stage.** Originally a DAG of milestones. **Dropped — execution is now sequential.** This removed the YAML→Beads-DAG compiler, the single most complex custom piece of the old stack (see [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md)). Milestones run in order, in one worktree.

> **Consequence:** Beads is fully dropped. No DAG engine, no dependency scheduler. State lives in three single-purpose stores (§4).

---

## 3. Architecture at a glance

```
  GitHub Issues  (system of record: backlog, issue TYPE, ownership)
  GitHub Projects v2  (coarse status: New / In Progress / Needs Input / Closed)
        │  claim = assign + transition  (the concurrency mutex, BEFORE Fabro starts)
        ▼
  Fabro (forked)  ── Level-A workflow engine ──────────────────────────────┐
   • DOT workflow defs (authored conversationally by Claude)               │
   • per-node k-v metadata (implementer=sonnet, verifier=haiku, …)         │
   • web UI: visualize workflows + "needs attention" gates                 │
   • git-checkpoint resume, observability event stream                     │
        │  one sandbox + one worktree PER ISSUE  (issue-level parallelism)  │
        ▼                                                                   │
  claudebox sandbox executor (custom — replaces Daytona)                    │
   • spawns Claude sessions: Opus orchestrator → Sonnet impl → Haiku verify │
        │                                                                   │
        ▼                                                                   │
  Target project repo ◄───────────────────────────────────────────────────┘
   • constitution (principles + projected architecture + ADR event log)
   • spec-folders (one per issue: requirements / design / plan)
   • code
```

**Repo split:** **workflow definitions live in the harness repo** (shared lifecycles — feature/bug are general engineering process). The **constitution and spec-folders live in each target project's repo** (project-specific). The harness operates *on* a project, reading/writing that project's constitution, spec-folders, and GitHub issues, but driving them through harness-owned workflows.

**The "inside Claude Code" constraint is relaxed:** Claude Code is the *agent runtime* (spawned execution/verification sessions); a thin forked-Fabro layer beside it owns workflow definitions, run-state, and the UI. This was a conscious trade for the persistent multi-issue dashboard and "needs attention" view.

---

## 4. State lives in three single-purpose stores

| Store | Owns | Granularity |
|---|---|---|
| **GitHub Issues + Projects v2** | Backlog, issue **type** (feature/bug), **ownership/assignment**, **coarse status** | Coarse: New / In Progress / Needs Input / Closed |
| **Issue's spec-folder** (in project repo) | The staged artifacts: requirements / design / plan | Per-issue artifacts |
| **Fabro run-state** | The fine-grained lifecycle state, which milestone, pass/fail, checkpoints | Fine-grained, per-run |

**No 1:1 mapping** between Fabro's fine-grained state and GitHub's coarse status. GitHub answers *"who owns what, and does anything need me?"*; Fabro answers *"what's happening inside this run."* The **GitHub Projects board** is the durable "where is every issue" view; the **Fabro dashboard** is the live "what's happening now" view. Complementary, not redundant.

**Claiming is the concurrency mutex.** Agents have their own **GitHub identity**. `New → In Progress` + assignment happens **first, as an atomic claim, before Fabro starts** — so no other identity picks up an in-flight issue. Issue-level parallelism is safe because each in-flight issue is owned + sandboxed + single-worktree. Discovered work during execution becomes a **new GitHub issue** that re-enters the pipeline.

---

## 5. Intake & triage (the front door)

Issues enter two ways — a human **files a GitHub issue**, or a **Claude conversation** decides "this should become tracked work" (and may pre-fill/advance early stages in that same session). Both converge on the lifecycle.

- **Classification = GitHub's native issue *type* field.** No agent classifier. v1 handles exactly two types: **Feature** and **Bug**. Other types sit untouched until their workflows are added later.
- **Routing is automatic by type; *initiation* is asymmetric:**
  - **Bug** → can **auto-start**: repro-first; if an agent reproduces it, it proceeds autonomously; not reproducible ⇒ `Needs Input` (human).
  - **Feature** → **human-initiated**. A feature is an *idea*; it is **not** auto-implemented. A human starts a planning session (the conversation-first entry). Rationale: a reproduced bug has an objective contract; a feature-idea needs human intent-setting before compute is spent.
  - **Other types** → wait for human action (no v1 workflow).

**Library coverage:** none needed — routing is "read the GitHub issue type, dispatch the matching Level-A workflow." Custom: a small **GitHub adapter** (read type, create/transition/assign issues) — Fabro extension #2.

---

## 6. Spec-management — issue-as-folder, separated artifacts

**Each GitHub issue maps to one version-controlled folder** in the target project's repo, holding that issue's staged artifacts. The feature pipeline produces **three artifacts**, each in a fresh orchestrator thread, each a **self-contained, user-approved** hand-off:

| Stage (fresh thread, user-approved gate) | Artifact |
|---|---|
| **Refinement** | `requirements` — **functional + non-functional, kept distinct** |
| **Technical design** | `design` — *where the plan-time constitution check bites hardest* (design vs architecture + ADRs) |
| **Planning** | `plan` — design translated into **milestones, each carrying an explicit validation contract** |

**Handoff principle (domain-wide):** each stage runs in a **fresh thread**, consumes the previous stage's approved artifact, emits the next. No context bleeds across stages; **the artifact is the interface**; a user-approval gate sits on every boundary (and is what the "needs attention" UI surfaces).

**Library coverage & decision (research-backed, 2026-06-16):** No tool matches the exact artifact separation (func-vs-NFR-vs-design-vs-plan). **Decision: custom layout, Spec-Kit-shaped, stealing four proven models** rather than adopting any framework wholesale:

- **Folder + living-spec lifecycle** ← **OpenSpec's** change-folder + **delta/archive** model (the canonical living-spec implementation).
- **ADR substrate** ← **adr-tools / Log4brains** one-decision-per-file Markdown convention.
- **Code-time constitution enforcement** ← **Mneme HQ's** PreToolUse-hook pattern (check the diff against compiled ADR constraints *as code is generated*).
- **Build ourselves:** the governed multi-doc constitution + amendment gate, and the drift detector — *no tool has these*.

> **Why not adopt Spec Kitty** (Priivacy-ai/spec-kitty, the closest single match — MIT, ~1.3k⭐, very active)? Its strengths (worktree isolation, lifecycle lanes, review/accept/merge gates) are *exactly what forked-Fabro + the claudebox executor already own*. Adopting it means two systems fighting over execution orchestration. It's a reference for artifact structure, not a dependency.

---

## 7. The constitution — event-sourced governance

The constitution is **not one document** and **not all authored**. It is an **event-sourced system**:

| Layer | Role | Mutability |
|---|---|---|
| **Principles** | The *true* constitution — authored root, governs everything | **Human-authored / human-amended** |
| **Per-issue specs + ADRs** | The **event log** — preserved, append-only archive of what was decided & built | **Append-only.** ADRs are immutable events; you don't edit one, you append a **superseding** ADR |
| **Logical architecture + living system spec** | **Projections** — current-state views computed from the sum of all per-issue specs (+ ADRs) | **Derived / regenerated**, never hand-edited |
| **Human-facing reference docs** | Derived from the projections | **Deferred** (see §12) |

This model resolves several things cleanly:

- **Logical architecture can't drift from the ADRs** — it's *regenerated from* them. Governed transitively by governing the events.
- **Deviation detection cites a specific principle or ADR** — both are the stable, addressable layer.
- **Amendment** is either amend a **principle** (human consent) or append a **superseding ADR** (Opus proposes, human consents). The archive is never mutated — you supersede.

**Projection regeneration: eager.** Projections regenerate as the **closing step of each issue's docs stage**. Always-current architecture/living-spec views (they're what humans and the constitution-checker read). A background worker validating projection-vs-event-log fidelity is **deferred** (§12).

**Bootstrap — greenfield only.** A dedicated **`/constitution-init`** command (borrowing Spec-Kit's `/speckit.constitution` *interaction* pattern: Claude interviews you, drafts for approval) seeds **only the principles**. Architecture + ADRs start empty and **accumulate** through the pipeline. Brownfield constitution-extraction is **deferred**.

> A **skill/rule** codifies the governed-set structure (what principles vs architecture vs ADRs are for, how agents must consult them). Every planning/execution agent loads it.

---

## 8. Deviation detection & amendment

**Detection at three moments, one shared detector primitive** (compare an artifact against its governing source, cite violations):

1. **Plan-time gate** (planning/design stage) — the proposed design/plan vs principles + architecture + ADRs, *before code exists*. (≈ Spec-Kit's `/analyze`.) **Blocks.**
2. **Code-time gate** (execution) — the diff vs the constitution, on the full diff before "done." Mneme-style generation-time hook is the mechanism reference. **Blocks.**
3. **Background sweep** — periodically scans the existing repo for drift. **Non-blocking**; files a tracked GitHub issue that re-enters the pipeline. **Deferred** (§12).

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

A **new orchestrator thread with fresh context** starts from the approved, self-contained plan.

---

## 10. Execution stage

Per-milestone loop, sequential, in the issue's single worktree:

```
implement (Sonnet) → verify (Haiku) against the milestone's pre-committed contract → pass → next
```

**Failure handling — every loop bounded, escalation always climbs:**

1. **Same implementer retries**, fed the verifier's specific rejection reasons (holds the most context). Verifier always grades against the **pre-committed contract**, never a moving target. **Cap: 2 implementer attempts.**
2. On the 2nd failure, the milestone bounces to the **Opus orchestrator**, which triages: *code* problem (re-dispatch with sharper guidance / fresh context) or *plan* problem (the contract was mis-specified). **Opus gets up to 2 bounded attempts** — **but may escalate to a human immediately at its discretion** when it judges the problem warrants human input right away (clearly a plan/architecture/constitution issue).
3. **Human escalation** → `Needs Input`. A plan problem proposes a **plan revision** (a user-approved artifact → may loop back into planning).
4. **Final whole-issue integration check** after all milestones pass — against the *original requirements* (milestones can each pass yet not compose). The **code-time constitution check** runs here on the full diff. Failure escalates Opus → human.

**Escalation principle (domain-wide):** implementer → orchestrator → human. Never silent retries; every loop has a hard cap.

---

## 11. The bugfix workflow (distinct Level-A workflow)

A compressed, separate state machine that *reuses* shared nodes:

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

## 13. What we adopt vs. what we build

| Capability | Off-the-shelf / borrowed | Custom build |
|---|---|---|
| Workflow definition + engine + UI | **Fabro** (DOT, per-node metadata, viz, checkpoint, observability) | Fork extensions (below) |
| Workflow authoring | — | **Conversational** — Claude writes/edits the DOT; user reviews rendered graph |
| Issue tracking + coarse state | **GitHub Issues + Projects v2** | GitHub adapter (read type, claim, transition) |
| Execution sandbox | — | **Claudebox executor** (replaces Daytona): 1 sandbox + 1 worktree per issue |
| Agent runtime | **Claude Code subagents** (Opus/Sonnet/Haiku) | spawn glue |
| Spec-folder + living spec | **OpenSpec** delta/archive *model* | the func/NFR/design/plan layout |
| ADR substrate | **adr-tools / Log4brains** one-file-per-decision *convention* | — |
| Code-time constitution enforcement | **Mneme** PreToolUse-hook *pattern* | the checker integration |
| Governed multi-doc constitution + amendment gate | **nothing exists** | **build** |
| Drift detector (continuous, files issues) | **nothing exists** | **build (deferred to background worker)** |

### Model assignment defaults (per-node metadata, configurable)
`orchestrator = Opus` · `implementer = Sonnet` · `verifier / constitution-checker / intake = Haiku`. These are **node metadata** (`implementer=sonnet`, …), overridable per workflow — the extensibility that also carries future `observability`/`cost_tracking` keys.

### Fabro fork — tracked extensions
1. **Claudebox sandbox executor** — replace Daytona; per-issue sandbox + dedicated worktree.
2. **GitHub adapter** — issue create/transition/assign, read issue type.
3. **Workflow visualization** — render Level-A workflows (custom node types, per-node k-v metadata, "needs-attention" gates) in the Fabro UI.
4. **Open k-v node metadata** — surface arbitrary metadata for observability/cost experiments.

> **Standing risk:** how many extensions before Fabro stops being "off-the-shelf"? If the fork gets too deep, the **MS Conductor** fallback (Python, YAML, claude-CLI provider, built-in HITL gates + per-node cost dashboard) or a thin custom engine comes back on the table. Fabro is MIT — no licensing obstacle.

---

## 14. Hard rules & domain-wide principles

- **HARD RULE:** No constitution amendment without explicit user consent. Opus proposes; only the human approves.
- **Escalation always climbs** (implementer → orchestrator → human); every loop is bounded; no silent retries.
- **No agent validates its own work** (separate verifier/checker nodes).
- **The artifact is the interface** — fresh thread per stage, self-contained user-approved hand-offs, no cross-stage context bleed.
- **The event log is never mutated** — ADRs are superseded, not edited; projections are regenerated, not patched.
- **Bugs are always reproduced first** (local-first projects make this universal).
- **Claim before work** — GitHub assign+transition is the concurrency mutex, before Fabro starts.

---

## 15. Open decisions (deliberately deferred)

| # | Decision | Lean |
|---|---|---|
| 1 | Where the human approval/gate action physically happens (Fabro UI artifact-review vs GitHub) | Fabro UI for artifact review; GitHub status mirrors |
| 2 | Exact Fabro↔GitHub status-sync timing beyond claim / needs-input / closed | — |
| 3 | Background drift worker mechanism: scheduled Fabro run vs self-hosted ECC-style agent ([continuous-learning-v2](https://github.com/affaan-m/ECC/tree/main/skills/continuous-learning-v2)) | Start as a simple scheduled run; self-hosted later (may lack self-hosted models early) |
| 4 | Brownfield constitution extraction | Deferred — greenfield only in v1 |
| 5 | `chore` / `question` workflows; per-project workflow overrides | Deferred — feature + bug only in v1 |
| 6 | Human-facing reference-doc generation | Deferred |
| 7 | Fabro fork depth / fallback to MS Conductor | Revisit if fork grows too large |

---

## 16. Research provenance

- **Library analysis:** [references/library-analysis.md](./references/library-analysis.md), [references/technologies.md](./references/technologies.md).
- **Workflow/DAG layer:** [workflow-orchestration-analysis.md](./workflow-orchestration-analysis.md).
- **Spec-Kit ecosystem research (2026-06-16):** full report at [references/spec-kit-ecosystem-research.md](./references/spec-kit-ecosystem-research.md) — 23 verified claims / 23 sources / 2 refuted. Key findings: no single tool covers all four target features (issue-as-folder + separated artifacts; governed-set constitution + amendment gate; continuous drift-and-file; living delta/archive spec). Closest single matches: **Spec Kitty** (feature 1), **OpenSpec** (feature 4). ADR substrate: **adr-tools / Log4brains**. Generation-time enforcement: **Mneme HQ**. The **governed multi-doc constitution with amendment gate** and the **continuous background drift scanner that files issues** are unmet by any tool — confirmed gaps we build.
