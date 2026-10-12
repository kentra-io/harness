# Agentic Coding Harness (`kentra/harness`)

The **wrapper / design repo** for kentra's agentic coding setup: Claude Code as
the runtime, a spec → plan → implement → review process, and a governance rail.
This repo holds the designs, the decision research and the working notes, and
**composes standalone primitives as git submodules** rather than absorbing them.

> **Orientation:** [`AGENTS.md`](./AGENTS.md) is the canonical agent-facing map.
> This README is the human front door. Where they differ, `AGENTS.md` wins.

## Repo pattern — standalone primitives as submodules

Every standalone, reusable primitive lives in **its own repo** and is consumed
here as a submodule. Primitives stay framework-neutral, agent-agnostic, and MIT.
The `kentra-` prefix appears only at the branded layer (the `kentra-rpir`
process plugin; the [`kentra-sdlc`](./kentra-sdlc.md) umbrella methodology).

## The pieces

| Piece | What it is | State |
|---|---|---|
| [`kentra-rpir`](./kentra-rpir/) | Claude Code plugin: one change runs spec → plan → implement → review, with parallel verified milestones in worktrees and a retro read from the session transcripts. Design: [`kentra-rpir.md`](./kentra-rpir.md) | **v0.3.1** (replaces `spec-lifecycle`) |
| [`milestoned-plan-dag`](./milestoned-plan-dag/) | YAML plan format — a DAG of verifiable milestones — and its Go CLI | **v0.2.0** |
| [`adr-sourced-constitution`](./adr-sourced-constitution/) | Event-sourced ADR log → deterministic `constitution.md`; optional governance rail | **v0.3.2** |
| [`kentra-skills`](./kentra-skills/) | Architecture skills (`java-hexagonal`, `testcontainers-java`, `subagent-workflow`) | Shipped |
| [`kentra-agentic-plugins`](./kentra-agentic-plugins/) | The plugin catalog consumers register | Live |
| Proxy / observability | LiteLLM + Langfuse | Decided, deferred |
| Auto-improvement | Online champion-challenger A/B over agent config | Designed, deferred |

## How a change runs

`/kentra-rpir:spec` → `spec.md` (agreed with the user, premises researched) →
`/kentra-rpir:plan` → `plan.yaml` (validated by `milestoned-plan-dag`,
self-reviewed) → `/kentra-rpir:implement` → code on `rpir/<slug>` +
`impl-report.md` → `/kentra-rpir:review` → `spec-retro.md`. Everything for one
change lives in `docs/changes/<date>-<slug>/`: the spec and plan sessions are
kept as a trimmed, redacted planning log (`planning-log.jsonl`) in the change
folder, and the retro reads every session's transcript where Claude Code keeps
it. Full process: [`kentra-rpir.md`](./kentra-rpir.md).

## Map of the repo

- **Designs:** [`kentra-rpir.md`](./kentra-rpir.md), [`observability.md`](./observability.md)
- **Methodology umbrella:** [`kentra-sdlc.md`](./kentra-sdlc.md) (parked / conventions-only)
- **Working notes:** [`tasks/todo.md`](./tasks/todo.md); archived: [`tasks/retro-archive/`](./tasks/retro-archive/)
- **Decision research:** [`references/`](./references/) — latest: [`references/rpi-rpir-research-2026-10.md`](./references/rpi-rpir-research-2026-10.md), [`references/agent-session-retro-research-2026-10.md`](./references/agent-session-retro-research-2026-10.md)
- **Deferred ideas:** [`roadmap-ideas.md`](./roadmap-ideas.md)

## Glossary guard

**SDD** here = **spec-driven development** (not Obra Superpowers' "subagent-driven
development" — different thing).

---

*Historical note: earlier revisions of this repo described a Spec-Kit + Beads +
BeadBoard architecture. That was dropped — Spec-Kit eliminated
([research](./references/sdd-framework-research-2026-07.md)), Beads never adopted.
Do not build from git history that references them. The gated `spec-lifecycle`
CLI and the Conductor-based `agent-orchestration` executor were retired on
2026-10-11 in favour of `kentra-rpir`.*
