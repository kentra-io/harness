# Harness — Agent Instructions

This repo (`kentra/harness`) is the **wrapper/design repo** for the agentic coding harness. It holds the domain designs (`kentra-rpir.md`, `observability.md`), decision research (`references/`), and session-resumable state (`tasks/`).

## Repo structure pattern: standalone primitives as submodules

**Every standalone, reusable primitive we build lives in its own repo, added here as a git submodule.** The harness wraps and consumes primitives; it does not absorb them.

- [`adr-sourced-constitution/`](./adr-sourced-constitution/) — event-sourced ADR log → deterministic `constitution.md` projection (Go CLI + skills).
- [`kentra-rpir/`](./kentra-rpir/) — the **process** plugin (formerly `spec-lifecycle`): one change runs spec → plan → implement → review. Interactive spec with background premise research; a `milestoned-plan-dag` plan detailed enough that an implementer never explores; parallel milestones in `.worktrees/` with a fresh verifier each, orchestrated by the main session; the spec and plan sessions are kept as a trimmed, redacted planning log (`planning-log.jsonl`) in the change folder, and the retro reads every session's transcript where Claude Code keeps it. No CLI and no gates. The only machine validation is the plan schema. Design: [`kentra-rpir.md`](./kentra-rpir.md).
- [`kentra-agentic-plugins/`](./kentra-agentic-plugins/) — the branded aggregator **plugin catalog** (public/MIT): one `marketplace.json` listing each primitive plugin by its own repo; the single marketplace consumers register.
- [`kentra-skills/`](./kentra-skills/) — hand-authored architecture skills as a Claude Code plugin (public/MIT): `java-hexagonal` (framework-neutral JVM hexagonal; authoritative for kafka-dq per its ADR-0011), `testcontainers-java`, `subagent-workflow` (`spring-boot-hexagonal` was removed/replaced 2026-07-19). Listed in the catalog. **Shipped 2026-07-19.** Headless box consumption pending (thread F).
- [`milestoned-plan-dag/`](./milestoned-plan-dag/) — framework-neutral, unbranded (MIT) DAG-of-milestones plan primitive: a machine-first YAML plan format describing a DAG of verifiable milestones, a Go CLI (`validate`/`resolve`/`render`), and an agent-agnostic authoring skill; no module-level constitution (deliberate design decision). A plan is scoped to **one git repository** — multi-repo / multi-module (separate git roots) plans are not supported, matching kentra-rpir's one-repo worktrees. **Shipped — v0.3.0** (explicit edges only; a milestone without `depends-on` is a root) (`brew install kentra-io/tap/milestoned-plan-dag`); listed in the plugin catalog, so its `plan-author` skill installs like any other.
- Future primitives follow suit. Default assumption when specing something framework-neutral and reusable: **standalone repo + submodule**, not a harness-internal directory.

**Neutral mechanism, branded methodology.** Primitive repos stay framework-neutral and unbranded (MIT, reusable beyond kentra). The `kentra-` prefix lives only at the branded layer: the `kentra-rpir` process plugin, and the umbrella methodology [`kentra-sdlc`](./kentra-sdlc.md) — the opinionated composition of the primitives into kentra's end-to-end SDLC (parked/conventions-only; owns the deferred TODO-capture + documentation concerns).

Conventions for primitives: framework-neutral core, agent-agnostic skills, thin per-framework adapters, MIT, own spec + implementation plan in-repo (see `adr-sourced-constitution` as the reference shape).

## Current state pointers

- Methodology map / umbrella: [`kentra-sdlc.md`](./kentra-sdlc.md) (the intent pipeline, primitive registry, and the two deferred concerns — TODO capture + documentation).
- Process design: [`kentra-rpir.md`](./kentra-rpir.md) (approved 2026-10-11); implementation plan: [`docs/superpowers/plans/2026-10-11-kentra-rpir.md`](./docs/superpowers/plans/2026-10-11-kentra-rpir.md). Archived planning-module state: `tasks/retro-archive/planning-module-handoff.md`.
- Current work + open-issue index across all repos: [`tasks/todo.md`](./tasks/todo.md).
- Latest research: `references/rpi-rpir-research-2026-10.md` and `references/agent-session-retro-research-2026-10.md`.

## Glossary guard

- **SDD** in this repo = **spec-driven development**. (Obra Superpowers uses "SDD" for *subagent*-driven development — different thing, don't conflate.)

## This repo is not itself governed or gated

The harness is the wrapper/design repo. It does **not** run `kentra-rpir` or
`constitution` on itself — there is no change folder, no ADR log, and no
installed governance skills. Both primitives are still built, shipped,
and consumed here as submodules; they are simply not applied to this repo.
Work on them happens in their own repos.
