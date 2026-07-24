# Planning Module — Session Handoff / Resumable State

*Created: 2026-07-02. Purpose: a **self-contained resume point** so this design conversation survives a lost session / claudebox container recreation. If you're picking this up cold, read this file top-to-bottom first, then the source docs it points to. The repo is host-mounted, so this file persists across container rebuilds.*

> **⏱ STATUS 2026-07-05 (supersedes the pre-build framing below).** The planning module is **specced AND building.** `spec-lifecycle` is at **M5 committed** (format engine · schema+validate · approve/status/constitution-seam · archive+ledger · guard), with **M6 (`init`) in the working tree.** Disregard "NOT yet specced" / "next move: build M1" below — historical. Remaining: finish M6 wiring, M7 (skills + dogfood), M8 (dist), M9 (harness acceptance). Cross-primitive Stage-3 reconciliation (consent / transition / Conductor-MCP seams, surfaced by reading the built CLI against the Stage-3 plan) lives in [`orchestration-runtime-handoff.md`](./orchestration-runtime-handoff.md).

> **How to resume in one paragraph (UPDATED 2026-07-03, Option-B pivot).** The decision phase is **COMPLETE** and the planning module is **SPECCED + PLANNED**: it is the standalone primitive [**`spec-lifecycle`**](../spec-lifecycle/spec-lifecycle.md) (own repo, submoduled — per the pattern in [AGENTS.md](../AGENTS.md); seed-committed, no remote yet). **P0 REVERSED → Option B (pure Go), user decision 2026-07-03:** `spec-lifecycle` **conforms to the OpenSpec on-disk *format*** (directory layout + delta grammar + fold semantics) but **reimplements the whole mechanism in Go — no OpenSpec runtime, no Node, no shell-out.** OpenSpec-the-tool is a **reference oracle** for a static conformance corpus, not a dependency. Both [spec-lifecycle.md](../spec-lifecycle/spec-lifecycle.md) and [implementation-plan.md](../spec-lifecycle/implementation-plan.md) were **rewritten for Option B (2026-07-03)** — the format engine (parse·validate·fold·render, ~900–1400 LOC) is now M1 and the critical path; owning the fold makes the replay guard a *true from-empty recompute*; the OpenSpec-runtime risk class (young `[experimental]` schema loader, #1246 silent data-loss, #409/#1192 no archive order) is **eliminated as external risk**. Retained: Obra Superpowers referenced-by-suggestion; the novel spine (3 stages/gates, `approval-state.json`, `tasks.md` validation contracts [**not** `plan.md` — filename load-bearing], bug repro-first, living-spec fold + guard, constitution seam via a **runtime CLI process boundary**); copy-don't-couple for the 3 frozen helpers (constitution untouched); naming resolved (neutral repo/CLI, `kentra-spec-lifecycle` schema, parked `kentra-sdlc.md` umbrella). Spec-Kit still **eliminated**. **Next move: (1) user reviews the two rewritten `spec-lifecycle` docs; (2) apply the P0-reversal reconciliation to the harness docs — the exact edit list (planning.md L11/L167/L209/L351, mvp-plan.md L7/L89/L142, kentra-sdlc.md L56, + a superseding banner on [references/sdd-framework-research-2026-07.md](../references/sdd-framework-research-2026-07.md)) is written but NOT yet applied, pending review; (3) M0 = create `kentra-io/spec-lifecycle` + copy helpers, then build M1 (format engine).** *The section below is the pre-decision state, kept for provenance.*

---

## Source-of-truth documents (read in this order)
1. [planning.md](../planning.md) — planning-domain design + analysis. **§6c = the open-decisions table (P0–P6).** Note its 2026-07-02 STATUS BANNER.
2. [mvp-plan.md](../mvp-plan.md) — MVP scope + phased build. **§2 = the two-mode control flow.** Note its 2026-07-02 STATUS BANNER.
3. [notes.md](../notes.md) — the raw brain-dump the open questions were distilled from (spec/plan/tasks vocabulary, plan-granularity, TDD, living-spec).
4. [adr-sourced-constitution/adr-sourced-constitution.md](../adr-sourced-constitution/adr-sourced-constitution.md) + [implementation-plan.md](../adr-sourced-constitution/implementation-plan.md) — the **settled** governance substrate (submodule).
5. [observability.md](../observability.md) — settled telemetry plane (not in scope for the current discussion).

---

## Two lanes, moving at different speeds
- **Lane 1 — constitution primitive: SETTLED, entering build.** `adr-sourced-constitution` extracted to a submodule (commits `67ec390`, `321ee5a`). Go single-binary CLI `constitution` with **7 verbs**: `init`, `adr new`, `supersede`, `deprecate`, `adr renumber`, `regen`, `guard`. Agent-agnostic skills. Milestones M0–M7 planned; **no code yet** (one seed commit; `implementation-plan.md` is untracked, README/spec modified in working tree — should be committed to lock the baseline).
- **Lane 2 — planning module: NOT yet specced.** planning.md (design) + mvp-plan.md (sequencing) exist; the actual planning-module *spec* hasn't started. We are in the decision phase that gates it (mvp-plan Phase 1: "first resolve the §6c opens, then build").

---

## Inconsistencies found (so they are not re-discovered)
1. **Artifact vocabulary is both "decided" and "open."** planning.md hardcodes `requirements→design→plan` everywhere (incl. `speckit.kentra.*` command names) while §6c-P1 lists vocabulary as open; notes.md leans a *different* model `spec→plan→tasks`. → **Resolved framing this session: this whole tension is an artifact of the (now-retracted) Spec-Kit commitment (see Decisions).**
2. **Distinct `tasks`/step-breakdown stage** exists in notes.md (sized by a repo-scoped `plan granularity` config) but not in planning.md (which folds step-breakdown into `plan` = milestones + validation contracts). 3-stage vs 4-stage, unreconciled → deferred (P3).
3. **NFRs decided-and-reopened:** planning.md §6 states "functional + non-functional, kept distinct"; §6c-P2 + notes.md list it open. → **Resolved this session (P2 below).**
4. **Framework retracted on paper, but docs are Spec-Kit-saturated,** and the settled primitive's own research (§9.2) rates **Spec-Kit the *worst* philosophical fit** (authored-mutable vs a never-hand-edited projection); OpenSpec/superpowers "clean complements."
5. **Known-stale references (companion-sync debt, flagged by the primitive spec §14):** cited extensions **`spec-validate`/`architecture-guard`/`Mneme HQ` could not be verified to exist** (treat as misremembered); Spec-Kit **bare command aliases unsupported**; `specify init --force` **clobbers** `.specify/templates/` + `.specify/scripts/`.
6. **mvp-plan Phase 1 is ahead of the primitive:** it assumes a Spec-Kit-*wired* constitution, but primitive v1 ships **core + zero-framework default (folder + AGENTS.md/CLAUDE.md pointer) only — all framework adapters deferred out of v1.** Whatever P0 picks, Phase-1 integration is folder+pointer.
7. **Stage-interface (P5) more resolved than planning.md admits** — mvp-plan §2 resolves it (files canonical, Conductor-MCP on top). → confirmed this session.
8. **`living-spec` (P6) was "unowned"** across docs. → **Changed this session: it's now IN the MVP (see P6).**
9. **Memory index was stale** (said "planning/spec layer DECIDED = extend Spec-Kit"). → fixed 2026-07-02.

---

## Decisions made THIS session (2026-07-02)
- **P0 — SDD framework: stays OPEN (deliberate).** Consequence: **the planning module must be specced framework-agnostically** — no baked-in Spec-Kit assumptions, no presupposed `kentra` bundle. Stages/artifacts/gate-records are ours; a framework is just a delivery mechanism underneath. The §6b "worked example" is no longer load-bearing.
- **P1 — vocabulary folds into P0/P3.** The `requirements→design→plan` vs `spec→plan→tasks` tension exists *only* because Spec-Kit's fixed verbs (`spec`/`plan`/`tasks`) forced an intent-renaming layer. With Spec-Kit un-committed, we pick vocabulary on its own merits — **last**, after functional decomposition and framework choice.
- **P2 — NFRs: DECIDED.** NFRs are **a type of requirement**; **technical design** is responsible for accounting for them. Performance/benchmark tests are **NOT folded into milestone acceptance** (too expensive per-milestone). Instead: **design constraint + engineering judgment** during planning/implementation; benchmarks/perf tests **live in the codebase but run asynchronously** alongside main dev. **Whether continuous performance testing is part of a project is itself a constitution/planning decision** (declared per-project, not assumed).
- **P3 — sequencing DECIDED, content deferred.** Don't fix stage count/vocabulary yet. **Align on functional requirements first,** then choose naming/terminology to **conform to established framework norms.** (Whether a distinct `tasks` stage + `plan granularity` config exists is decided here, later.)
- **P4 — confirmed (mostly "confirm-and-label").** Each **milestone carries a pre-committed validation contract = acceptance criteria** (planning.md §9/§10 already). **Bugs are reproduced first** — the bug workflow **already exists** (planning.md §11, `bug.yaml`): a failing test capturing the bug is *both* the requirement artifact *and* the validation contract; not reproducible ⇒ `Needs Input`. **No blanket test-first-on-everything mandate.**
- **P5 — DECIDED.** **File-based touchpoints are canonical**: artifacts + gate records (`approval-state.json`, `deviation.json`) committed in the issue's spec-folder; works with **no engine at all** (Phase 1). **Conductor-MCP is a live coordination channel layered on in Phase 2** that reads/writes the *same* files; it never replaces them. Residual: exact split of run-state (engine-only vs mirrored-to-files) pins when Phase 2 lands.
- **P6 — CHANGED: living-spec is now IN the MVP,** bundled with **archiving/compaction (OpenSpec-style change → delta → archive).** To be designed as part of the planning module. (Was previously "separate/unowned/deferred.")
- **Framework leaning (not a decision):** warming to **building the module as an extension of Obra Superpowers** (lightweight, skill-shaped; we add the missing artifact/lifecycle/governance capabilities) — but **wants to evaluate others first.** OpenSpec's **archive/compaction pattern** is a direct input to P6 regardless of the base chosen.

### What `living-spec` is (for whoever resumes)
The **functional twin of the constitution.** Both are event-sourced projections:
- **Constitution** = projection of the **ADR log** → `constitution.md` = the governed **HOW**. **Deterministic** render (the settled Go CLI).
- **Living-spec** = projection of all **per-feature specs** → current system spec = the current **WHAT**. **Agent-synthesized** (an LLM merges/dedupes/reconciles many feature-specs into one current-state view). That non-determinism is why the constitution primitive **excludes** it and it needs its own design.

---

## The decision chain (what's actually left)
```
functional decomposition  →  framework choice (P0)  →  naming/vocabulary (P1/P3)
   (open workstream)          (open, research-fed)      (falls out last)
```
Everything else (P2, P4, P5) is settled; P6 is newly in-scope and least-specified.

---

## Open FUNCTIONAL aspects to nail (A–G; A–D are core)
- **A. Stage/gate decomposition (the spine).** How many distinct human-approved artifacts an issue passes through, and each one's boundary. Can requirements & design collapse for small work (as the bug flow collapses refinement into repro)? Distinct step-breakdown stage or milestones-in-plan (P3)? **Settle first.**
- **B. What a "functional requirement" is here.** Format/content of the first artifact (user stories? scenarios? acceptance criteria?), now also holding NFRs as declared constraints (P2). **This is the natural starting point.**
- **C. The design ↔ ADR ↔ constitution seam.** How the design stage *proposes* ADRs, how consent is taken, how accepted ADRs feed `constitution regen`. The **main integration with the settled primitive** — currently hand-wavy; must be precise (design is where the plan-time deviation gate bites).
- **D. Living-spec + archiving/compaction (newest, least specified).** (1) rollup model (feature-specs → current system spec) + regeneration timing + how the agent-synthesized result is reviewed/governed; (2) OpenSpec-style compaction — on issue completion the feature-spec's delta folds into the living-spec and the active change is archived; (3) relationship between a per-issue requirements artifact and the living system spec.
- **E. Intake / conversational entry (mechanics).** Paths are clear (feature = human-initiated, bug = auto-repro); mechanics of an interactive session minting a new issue and advancing it aren't drawn.
- **F. Docs/projection stage now regenerates TWO projections** — deterministic constitution (`regen`) + agent-synthesized living-spec. Trigger order + review of the synthesized one are open.
- **G. Loop-back from execution.** planning.md §10 says a "plan problem" escalates back into planning as a revised, re-approved artifact — mechanics unspecified.

---

## Agreed NEXT MOVE (not yet started)
1. **Launch a fresh framework-research pass** — evaluate **superpowers vs OpenSpec vs Spec-Kit vs native agent plan-mode** *as a base to extend for the planning module*, weighting: staged artifacts + gates, folder-per-issue, the living-spec rollup, and **archiving/compaction**. (Prior research — `references/spec-kit-ecosystem-research.md` and the primitive's §9.2 — is **stale + Spec-Kit-framed** and must be re-run for this lens.) Superpowers is the candidate-to-beat; OpenSpec's archive model is a direct P6 input. **← proposed, awaiting go-ahead.**
2. **Work the functional spine:** **B (functional-requirement format)** → **A (stage decomposition)**, per the "functional-first, name-last" sequencing (P3).

---

## Hard contracts the planning-module spec MUST honor (from the settled primitive)
- **Folder layout:** `constitution/` (top-level), `constitution/adr/ADR-NNNN-slug.md` (zero-padded, monotonic, append-only), `constitution/constitution.md` (the file every consumer reads), `constitution.yml` at **repo root**. `deviation.json` is **NOT** under `constitution/` (per-plan output).
- **ADR schema (MADR v4):** frontmatter `id,title,category,date,status,source,supersedes` (+ derived `superseded-by`); **`status` is the ONLY mutable field** (`accepted|superseded|deprecated`; `proposed/rejected` never enter the store). Body headings verbatim incl. `Considered Options` (mandatory).
- **`deviation.json`:** SARIF-shaped; **`adrId` REQUIRED on every deviation**; severity `CRITICAL|HIGH|MEDIUM|LOW`; `recommendation: conform|amend`. Never write into `adr/` directly; never hand-edit `constitution.md`; never treat its git-diff as source of truth.
- **Consent:** default **`strict`** (HARD RULE — no ADR/amendment without explicit human consent), enforced via the agent-harness permission boundary (skills don't pre-grant mutating commands).
- **Enforcement lives OUTSIDE the primitive** (Conductor gate / CI in Phase 2); the primitive only emits records. Code-time constitution check lives in the execution domain, **not** the primitive.

---

## Housekeeping flags (open, not blocking)
- **planning.md §6b/§7 stale** — companion-sync debt: misremembered extensions (`spec-validate`/`architecture-guard`/`Mneme HQ`), bare-alias + `init --force` corrections. Also planning.md still lacks the two-mode/Conductor-MCP/host-daemon model (mvp-plan §10 flags this).
- **Constitution submodule has uncommitted files** — `implementation-plan.md` untracked; README/spec modified. Commit to lock the baseline.
- **Doc reconciliation** — once P0/P1 land, sweep planning.md/mvp-plan.md to drop the Spec-Kit-committed framing they've formally retracted.

---

## Working style reminders (from the user)
- Design via **`grill-me` / brainstorming** skills — one question at a time, with a recommended answer; `AskUserQuestion` batches used well.
- Plan-mode for non-trivial work; verify before claiming done; subagents liberally to keep context clean.
