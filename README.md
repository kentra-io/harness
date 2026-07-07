# Agentic Coding Harness (`kentra/harness`)

The **wrapper / design repo** for an agentic coding harness: staged, gated,
spec-driven development with Claude Code as the runtime. This repo holds the
domain designs, the decision research, and the resumable session state — and
**composes standalone primitives as git submodules** rather than absorbing them.

> **Orientation:** [`AGENTS.md`](./AGENTS.md) is the canonical agent-facing map.
> This README is the human front door. Where they differ, `AGENTS.md` wins.

## Repo pattern — standalone primitives as submodules

Every standalone, reusable primitive lives in **its own repo** and is consumed
here as a submodule. Primitives stay framework-neutral, agent-agnostic, and MIT.
The `kentra-` prefix appears only at the branded layer (the
`kentra-spec-lifecycle` schema; the [`kentra-sdlc`](./kentra-sdlc.md) umbrella
methodology). See [`AGENTS.md`](./AGENTS.md) for the full convention.

## Stage map — 2 settled, 3 planned

| Stage | Domain | State |
|---|---|---|
| 1 | **Governance** — [`adr-sourced-constitution`](./adr-sourced-constitution/): event-sourced ADR log → deterministic `constitution.md` | Settled, building |
| 2 | **Planning** — [`spec-lifecycle`](./spec-lifecycle/): staged/gated issue lifecycle in the OpenSpec **format**, reimplemented in pure Go | **Shipped — v0.1.0** |
| 3 | **Orchestration + runtime + agent abstraction** — [`orchestration`](./orchestration.md) (Conductor-extended execution loop) + claudebox + [`agent-definition`](./agent-definition.md) (both specs drafted) | Designed (planned) |
| 4 | **Proxy / observability** — LiteLLM + Langfuse | Decided, deferred |
| 5 | **Auto-improvement** — experiment controller (online champion-challenger A/B over agent config) | Designed, deferred |

## Settled primitives

### `adr-sourced-constitution` (Stage 1)
Event-sourced ADR log projected deterministically into `constitution.md` — the
governed **HOW** of a project. Go single-binary CLI + agent-agnostic skills.
MADR-compliant; append-only; tool-only writes.

### `spec-lifecycle` (Stage 2) — shipped
The planning module: a staged, gated issue lifecycle (**refine → design → plan**,
each emitting a human-approved artifact into a per-issue change folder), plus the
living-spec fold on archive and the seams to the constitution and to an external
enforcement engine. It **conforms to the OpenSpec on-disk format** (directory
layout, delta grammar, fold semantics) but **reimplements the whole engine in
pure Go — no OpenSpec runtime, no Node, no shell-out.** Six verbs: `init`,
`validate`, `approve`, `status`, `archive`, `guard`. Gates are **records, not
enforcement** — the primitive writes them; an engine (Conductor) or CI reads and
blocks. Files are the canonical interface.

## Planned stages (design-ahead)

Stage 3+ is designed but **not built** — nothing starts until `spec-lifecycle`
dogfooding surfaces the need. The current design:

- **Orchestration = [`orchestration`](./orchestration.md)** (spec drafted), the
  execution business logic that **extends** Microsoft Conductor (`microsoft/conductor`,
  MIT — deterministic YAML route/`when`, durable run-state) rather than owning it.
  Conductor is the durable spine; the module adds the implement→verify→escalate
  loop, a **3-layer verification** model (executable check + generic healthcheck +
  advisory judge, with **author≠verifier** as the trust spine), and a fixed
  **3-attempt escalation ladder** (1 solo + 2 orchestrator-guided, then human).
  *(Not Conductor.build, the Melty macOS app.)*
- **Runtime = claudebox / Docker.** microVMs evaluated and deferred.
- **Agents = [`agent-definition`](./agent-definition.md)**, a custom neutral
  primitive (own repo + submodule; **spec drafted**): minimal fields
  `system_prompt` / `skills` / `model` + experiment slots, **conforming to the
  Agent Format envelope** (`.agf.yaml`) with a thin owned extension for `skills` +
  `harness`; conform-to-format / own-the-engine, same play as `spec-lifecycle`. A
  def materializes → `.claude/agents/<role>.md` in claudebox. Approval is
  **launch-context-bound** — never in a headless agent's tool surface.
- **Proxy/obs = LiteLLM + Langfuse** (Stage 4).
- **Auto-eval = online champion-challenger A/B** over agent config (Stage 5) —
  not GEPA text-mutation.

Full design + open questions: [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md).

## Two driving modes (control-flow model)

One workflow, two modes: **Mode A** (interactive planning — human collaborates,
approves conversationally; the engine is passive) and **Mode B** (headless
execution — Conductor drives). The file-based gate records are the identical seam
under both. See [`mvp-plan.md`](./mvp-plan.md) §2.

## Map of the repo

- **Designs:** [`planning.md`](./planning.md), [`mvp-plan.md`](./mvp-plan.md), [`observability.md`](./observability.md)
- **Primitive specs (pre-extraction):** [`orchestration.md`](./orchestration.md) (Stage 3 execution loop, design drafted) + [`orchestration-implementation-plan.md`](./orchestration-implementation-plan.md) (plan drafted, pending review), [`agent-definition.md`](./agent-definition.md) (Stage 3 agent abstraction, design drafted)
- **Methodology umbrella:** [`kentra-sdlc.md`](./kentra-sdlc.md) (parked / conventions-only)
- **Resumable session state:** [`tasks/planning-module-handoff.md`](./tasks/planning-module-handoff.md), [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md)
- **Decision research:** [`references/`](./references/) — latest: [`references/sdd-framework-research-2026-07.md`](./references/sdd-framework-research-2026-07.md)
- **Deferred ideas:** [`roadmap-ideas.md`](./roadmap-ideas.md)

## Glossary guard

**SDD** here = **spec-driven development** (not Obra Superpowers' "subagent-driven
development" — different thing).

---

*Historical note: earlier revisions of this repo described a Spec-Kit + Beads +
BeadBoard architecture. That was dropped — Spec-Kit eliminated
([research](./references/sdd-framework-research-2026-07.md)), Beads never adopted.
Do not build from git history that references them.*
