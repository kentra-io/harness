# kentra-sdlc — the opinionated SDLC methodology

*Status: **PARKED / conventions-only** (2026-07-03). This is a map, not a build. It names how kentra runs a software project end-to-end and registers the primitives that implement each part. Two concerns are **deferred / unplanned**: TODO capture and documentation generation (§4). Materialize a CLI only if a mechanism appears that no sub-primitive covers.*

## 1. What it is

`kentra-sdlc` is kentra's **branded methodology** — an opinionated set of conventions for managing a software project's durable state as it evolves. It **composes framework-neutral primitives**; it does not absorb them.

- **Neutral mechanism, branded methodology.** The primitives ([`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md), [`adr-sourced-constitution`](./adr-sourced-constitution/adr-sourced-constitution.md)) stay MIT, reusable, and unbranded — a team can adopt one alone. `kentra-sdlc` is the composition that says "here's how kentra runs the *whole* pipeline." The `kentra-` prefix lives only at this branded layer and in the schema it configures (`kentra-spec-lifecycle`).
- **Not the harness.** The harness is the *runtime wrapper* (engine, observability, model routing). `kentra-sdlc` is the *methodology* — the document + spec discipline. Different axes; a team could adopt one without the other.

## 2. The pipeline — intent at decreasing altitude

```
idea / TODO  →  issue  →  [ refine → design → plan ]  →  execution  →  archive → living-spec  →  docs
   §4.1                        └──────── spec-lifecycle ────────┘         └─ spec-lifecycle ─┘   §4.2
                          ↑———————— governed by the constitution throughout ————————↑
```

`spec-lifecycle` owns the **middle** (issue → archive). `adr-sourced-constitution` is the **governance rail** that runs the whole length. The **upstream** (how an idea becomes an issue) and the **downstream** (how delivered behavior becomes human reference) are the two open concerns — §4.

## 3. Primitive registry

| Concern | Home | Status |
|---|---|---|
| Governance — the *HOW* of the project (decisions, invariants) | [`adr-sourced-constitution`](./adr-sourced-constitution/) — ADR log → `constitution.md` | design complete; implementing |
| Issue lifecycle — refine → design → plan, living-spec fold | [`spec-lifecycle`](./spec-lifecycle/) — gates, records, `kentra-spec-lifecycle` schema | **shipped — v0.1.0** (pure-Go engine; PR #1 → `main`) |
| TODO capture (pre-issue) | §4.1 convention below | deferred (convention leaning documented) |
| Documentation (human reference from specs + constitution + code) | §4.2 | deferred / unplanned |

**Shared invariant across all of it (the spine):** append-only events · derived projections · tool-only writes · verifiable fidelity · issue-linked provenance. Both primitives obey it; new ones should too.

## 4. Deferred concerns

### 4.1 TODO capture — pre-issue intent (convention leaning; tooling deferred)

Purpose: a zero-friction place to jot things down **before they earn an issue**. Not roadmap/backlog management — just a flat list of independent items.

- **Root `TODO.md`, one flat file.** Each line is one independent item (`- [ ] …`), freeform text after it. No sections, no priorities, no dates required. That's the whole schema.
- **Only the mouth of the funnel.** The moment an item earns structure (acceptance criteria, design), it graduates to a GitHub issue → enters `spec-lifecycle`, and the line leaves `TODO.md`. It never mirrors issues — no dual bookkeeping.
- **[`roadmap-ideas.md`](./roadmap-ideas.md) is the same convention, scoped.** That file is this exact pattern applied to design/architecture deferrals for a primitive. The convention is "a flat list of independent, pre-issue items"; `TODO.md` is the general instance, `roadmap-ideas.md` a named one. A folder-of-notes is an escape hatch only when an item needs multi-paragraph thought — at which point it's usually ready to be an issue.

*Deferred:* any tooling/enforcement (a `todo` verb, promotion automation). The convention is adopt-now; nothing to build yet.

### 4.2 Documentation — human reference (unplanned)

Human-facing reference derived from the living specs + constitution + code: a deterministic projection with optional prose synthesis. Already deferred inside `spec-lifecycle` §11 ("living-spec prose synthesis") — recorded here as a named future primitive, not designed.

## 5. Consistency substrate — git is the transactional database

Canonical state is human-diffable files in the repo; **git provides ACID** for them, so no transactional DB is needed for the record layer:

| ACID | Mechanism |
|---|---|
| Atomicity | one commit = one atomic multi-file write; per-file writes use temp-file + `rename()` (the constitution's atomic-write internal) |
| Consistency | `lifecycle validate` + `lifecycle guard` + `constitution guard` as pre-commit/CI checks (pure-Go; no OpenSpec runtime — Option B) |
| Isolation | branch-per-change / worktree-per-agent + the GitHub claim-mutex serializing work at capability grain |
| Durability | the git object store + remote |

Add `flock` for the one narrow intra-repo race (two `lifecycle approve` on `approval-state.json`). **A real transactional DB (SQLite/Postgres/Dolt) is reserved strictly for operational/derived state** owned by the engine (run-state, telemetry, claim registry) — never for canonical documents, because file-canonical is exactly what keeps ejection cheap and review git-native.

## 6. Provenance

Discussion 2026-07-03 (naming + scope): umbrella framing adopted; primitive repos stay neutral; schema → `kentra-spec-lifecycle`; this doc parked with TODO + documentation as the deferred concerns. Prior decisions: `references/sdd-framework-research-2026-07.md`, `tasks/retro-archive/planning-module-handoff.md` (archived).
