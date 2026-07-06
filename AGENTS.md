# Harness — Agent Instructions

This repo (`kentra/harness`) is the **wrapper/design repo** for the agentic coding harness. It holds the domain designs (`planning.md`, `mvp-plan.md`, `observability.md`), decision research (`references/`), and session-resumable state (`tasks/`).

## Repo structure pattern: standalone primitives as submodules

**Every standalone, reusable primitive we build lives in its own repo, added here as a git submodule.** The harness wraps and consumes primitives; it does not absorb them.

- [`adr-sourced-constitution/`](./adr-sourced-constitution/) — event-sourced ADR log → deterministic `constitution.md` projection (Go CLI + skills).
- [`spec-lifecycle/`](./spec-lifecycle/) — the planning module: staged, gated issue lifecycle in the **OpenSpec format** (directory layout + delta grammar + fold, reimplemented in pure Go — no OpenSpec/Node runtime); gate records + living-spec replay guard (Go CLI + skills + `kentra-spec-lifecycle` schema).
- Future primitives follow suit. Default assumption when specing something framework-neutral and reusable: **standalone repo + submodule**, not a harness-internal directory.

**Neutral mechanism, branded methodology.** Primitive repos stay framework-neutral and unbranded (MIT, reusable beyond kentra). The `kentra-` prefix lives only at the branded layer: the OpenSpec schema `kentra-spec-lifecycle`, and the umbrella methodology [`kentra-sdlc`](./kentra-sdlc.md) — the opinionated composition of the primitives into kentra's end-to-end SDLC (parked/conventions-only; owns the deferred TODO-capture + documentation concerns).

Conventions for primitives: framework-neutral core, agent-agnostic skills, thin per-framework adapters, MIT, own spec + implementation plan in-repo (see `adr-sourced-constitution` as the reference shape).

## Current state pointers

- Methodology map / umbrella: [`kentra-sdlc.md`](./kentra-sdlc.md) (the intent pipeline, primitive registry, and the two deferred concerns — TODO capture + documentation).
- Resumable design-session state: `tasks/planning-module-handoff.md` (read this first when picking up planning-module work).
- Latest framework research: `references/sdd-framework-research-2026-07.md`.
- `README.md` is **stale** (describes a dropped Spec-Kit + Beads architecture) — do not trust it over `planning.md`/`mvp-plan.md`.

## Glossary guard

- **SDD** in this repo = **spec-driven development**. (Obra Superpowers uses "SDD" for *subagent*-driven development — different thing, don't conflate.)

<!-- BEGIN spec-lifecycle v1 (managed — do not edit by hand; `lifecycle init` updates it) -->
This project uses `lifecycle` (spec-lifecycle) for staged, gated planning — see `openspec/`. Run `lifecycle status` for gate state; approve gates only via `lifecycle approve`, never by hand-editing `approval-state.json`.
<!-- END spec-lifecycle v1 -->
