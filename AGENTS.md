# Harness — Agent Instructions

This repo (`kentra/harness`) is the **wrapper/design repo** for the agentic coding harness. It holds the domain designs (`planning.md`, `mvp-plan.md`, `observability.md`), decision research (`references/`), and session-resumable state (`tasks/`).

## Repo structure pattern: standalone primitives as submodules

**Every standalone, reusable primitive we build lives in its own repo, added here as a git submodule.** The harness wraps and consumes primitives; it does not absorb them.

- [`adr-sourced-constitution/`](./adr-sourced-constitution/) — event-sourced ADR log → deterministic `constitution.md` projection (Go CLI + skills).
- [`spec-lifecycle/`](./spec-lifecycle/) — the planning module: staged, gated issue lifecycle in the **OpenSpec format** (directory layout + delta grammar + fold, reimplemented in pure Go — no OpenSpec/Node runtime); gate records + living-spec replay guard (Go CLI + skills + `kentra-spec-lifecycle` schema). **Shipped — v0.1.0.**
- [`kentra-agentic-plugins/`](./kentra-agentic-plugins/) — the branded aggregator **plugin catalog** (public/MIT): one `marketplace.json` listing each primitive plugin by its own repo; the single marketplace consumers register.
- [`kentra-skills/`](./kentra-skills/) — hand-authored architecture skills as a Claude Code plugin (public/MIT): `java-hexagonal` (framework-neutral JVM hexagonal; authoritative for kafka-dq per its ADR-0011), `testcontainers-java`, `subagent-workflow` (`spring-boot-hexagonal` was removed/replaced 2026-07-19). Listed in the catalog. **Shipped 2026-07-19.** Headless box consumption pending (thread F).
- [`milestoned-plan-dag/`](./milestoned-plan-dag/) — framework-neutral, unbranded (MIT) DAG-of-milestones plan primitive: a machine-first YAML plan format describing a DAG of verifiable milestones, a Go CLI (`validate`/`resolve`/`render`), and an agent-agnostic authoring skill; no module-level constitution (deliberate design decision). A plan is scoped to **one git repository** — multi-repo / multi-module (separate git roots) plans are not supported, mirroring the executor's one-run-one-repo constraint (committed by design — `agent-orchestration` §1/§13, its ADR-0004; its #24 closed). **Shipped — pinned commit `78acd13`.**
- [`agent-orchestration/`](./agent-orchestration/) — the **execution** leg: **shipped**, extends the Conductor fork (Python core), driving an approved `spec-lifecycle` plan to merged code through the agent cast, implement→verify→escalate loop with **3-layer verification** (executable acceptance check + generic healthcheck + advisory judge; **author≠verifier** as the trust spine; diff-confined-to-declared-paths) and a fixed **3-attempt escalation ladder** (1 solo + 2 orchestrator-guided, human on the 3rd → `Needs human input`), live since 2026-07 — most recently shipped the 015-github-mirror feature. Open: #30 (gate-time pytest tmp bug, fix in flight), #32 (consume `milestoned-plan-dag` for execution ordering). Historical spec/plan references (design predates the build): [`orchestration.md`](./orchestration.md), [`orchestration-implementation-plan.md`](./orchestration-implementation-plan.md).
- **Planned (Stage 3, not yet a repo) — spec drafted:** [`agent-definition`](./agent-definition.md) — a neutral declarative agent schema (`system_prompt` / `skills` / `model` + experiment slots) that **conforms to the Agent Format envelope** (`.agf.yaml`) and owns a thin extension for the two gaps it lacks (`skills`, `harness`); conform-to-format / own-the-engine (CLI `agentdef`), materializes a def → `.claude/agents/<role>.md` inside claudebox, consumed by Conductor via a ClaudeboxProvider (now `agent-orchestration`, shipped). Full spec: [`agent-definition.md`](./agent-definition.md); Stage-3 build-time open questions in [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md).
- Future primitives follow suit. Default assumption when specing something framework-neutral and reusable: **standalone repo + submodule**, not a harness-internal directory.

**Neutral mechanism, branded methodology.** Primitive repos stay framework-neutral and unbranded (MIT, reusable beyond kentra). The `kentra-` prefix lives only at the branded layer: the OpenSpec schema `kentra-spec-lifecycle`, and the umbrella methodology [`kentra-sdlc`](./kentra-sdlc.md) — the opinionated composition of the primitives into kentra's end-to-end SDLC (parked/conventions-only; owns the deferred TODO-capture + documentation concerns).

Conventions for primitives: framework-neutral core, agent-agnostic skills, thin per-framework adapters, MIT, own spec + implementation plan in-repo (see `adr-sourced-constitution` as the reference shape).

## Current state pointers

- Methodology map / umbrella: [`kentra-sdlc.md`](./kentra-sdlc.md) (the intent pipeline, primitive registry, and the two deferred concerns — TODO capture + documentation).
- Resumable design-session state: `tasks/retro-archive/planning-module-handoff.md` (archived; planning module — now **shipped**) and [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md) (**read this first when picking up Stage-3 orchestration/runtime/agent work**).
- Latest framework research: `references/sdd-framework-research-2026-07.md`.
- Doc status (2026-07-06 reconciliation): `README.md` rewritten to current architecture. `planning.md` / `mvp-plan.md` carry **read-first STATUS banners** — their in-body Spec-Kit/OpenSpec-runtime text is historical and superseded by those banners (Option B: pure-Go, OpenSpec = format not runtime). `workflow-orchestration-analysis.md` is superseded-in-part (Beads/nesting) — banner at top.

## Glossary guard

- **SDD** in this repo = **spec-driven development**. (Obra Superpowers uses "SDD" for *subagent*-driven development — different thing, don't conflate.)

<!-- BEGIN spec-lifecycle v1 (managed — do not edit by hand; `lifecycle init` updates it) -->
This project uses `lifecycle` (spec-lifecycle) for staged, gated planning — see `openspec/`. Run `lifecycle status` for gate state; approve gates only via `lifecycle approve`, never by hand-editing `approval-state.json`.
<!-- END spec-lifecycle v1 -->
