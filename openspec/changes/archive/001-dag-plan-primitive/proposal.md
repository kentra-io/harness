---
issue: "kentra-io/harness#1"
designSkip: false  # architectural — new standalone primitive + DAG model + cross-primitive consumers; design stage runs
type: feature  # feature (default) | bug — the change type recorded at intake (spec-lifecycle.md §8, §0's Stage glossary)
---

# Extract a standalone, DAG-capable plan primitive (schema + CLI + skills)

## Why

The plan format — the `tasks.md` milestone grammar with its per-milestone
verification contracts (`check` / `criteria` / `paths`) — is a reusable
primitive currently trapped inside `spec-lifecycle` and hard-wired to a
**strictly sequential** milestone list. Sequential-only rules out plans with
parallelizable or independently-orderable work, and keeping the format buried
in one consumer prevents it from standing on its own as a schema others can
adopt. Extracting it (per ADR-0001) and generalizing the ordered list into a
**DAG of milestones** unblocks future concurrent execution and lets both
`spec-lifecycle` (plan stage) and `agent-orchestration` (execution ordering)
consume one shared primitive instead of each re-parsing the grammar.

## What Changes

- **New capability `plan-schema`** — the authored on-disk plan format,
  generalized from an ordered list to a DAG. Each milestone gains a stable
  kebab-case **slug** alongside its ordinal **number**; dependencies are
  declared with `depends-on: [slug, …]` ("must complete before"). A milestone
  with no `depends-on` inherits an implicit edge on the preceding milestone, so
  **an existing sequential plan stays valid unchanged** (the sequential chain is
  the degenerate DAG). The per-milestone verification **contract becomes
  required** (`check` — with a `none` sentinel for genuinely unverifiable work;
  `criteria` — always non-empty; `paths` — present, `[]` meaning "empty diff",
  `**` meaning conscious unconfined). **Step checkboxes become required.** A
  `schemaVersion` (semver, `0.1.0`) is stamped so the format can evolve without
  silently breaking consumers. Optional structured slots are reserved (but not
  yet mandated) so a later skill change can produce file-specific, RPI-grade
  plans: a `Create/Modify/Test` deliverables list, per-step file refs, and
  test-case-shaped criteria.
- **New capability `plan-validation`** — the validator guarantees a plan is a
  well-formed DAG: it rejects dependency cycles, `depends-on` edges pointing at
  a nonexistent slug, duplicate slugs or numbers, and any milestone lacking a
  well-formed contract. It **warns** (not fails) when two milestones with no
  dependency path between them declare overlapping write-`paths` — surfacing the
  concurrency hazard now, to be hard-enforced when concurrent execution lands.
- **New capability `plan-projection`** — a single CLI command emits a
  machine-readable **YAML** view of the plan (milestone number, slug, goal,
  deliverables, contract, steps with checkbox state, and `depends_on` edges).
  The edges are authoritative; the projection also carries a computed valid
  **topological order** (deterministic tie-break by number) that today's
  one-at-a-time executor consumes without doing its own sort.
- **YAML is the sole serialization.** The `--format json` flag is retired
  everywhere; the projection emits YAML. A published **JSON Schema** remains the
  cross-language validation contract (it validates the YAML data model — YAML
  1.2 being a JSON superset — and drives editor tooling).

## Impact

- **New standalone repo** (framework-neutral, MIT), consumed by the harness as a
  git submodule per ADR-0001; naming + a constitution ADR registering it are a
  design-stage decision.
- **`spec-lifecycle`** stops owning the plan grammar and instead **consumes**
  this primitive at its plan stage; *how* it consumes it (shell-out to the
  binary vs. Go library vs. vendored) is a design-stage decision.
- **`agent-orchestration`** consumes the YAML **projection** for execution
  ordering; it keeps running milestones one at a time (no concurrency work here).
- Existing sequential `tasks.md` plans remain valid — the implicit-chain rule
  means migration is minimal.
- **Authoring-skill depth is out of scope** (tracked as a separate change):
  this change only ensures the schema *affords* file-specific, RPI-grade plans
  via the reserved optional slots. The companion "research / file-map" gate-2
  artifact lives in `spec-lifecycle`'s `design` stage and is a linked follow-on,
  not built here.

## Non-Goals

- **Concurrent execution.** The schema and projection are DAG-capable so a
  future consumer *can* run independent milestones together, but no
  concurrent-execution engine is built in this change; the executor stays serial.
- **Non-verifiable or non-decomposable work.** This primitive's thesis is that
  every milestone is verifiable and diff-confined. A plan with no verifiable
  outcomes, or work that doesn't decompose into milestones, is deliberately out
  of scope — such use cases should not use this schema. The `check: none`,
  `paths: []`, and `--force-incomplete-tasks` escapes cover legitimate edge cases
  *within* the methodology (spikes, verify-only milestones, in-progress
  archives); they are not a route to opting out of verification wholesale.
- **Authoring-skill rewrite and the design-stage file-map.** See Impact.
