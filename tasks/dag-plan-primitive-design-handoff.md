# DAG plan primitive — design-stage handoff (harness#1 / 001-dag-plan-primitive)

**Status (2026-07-23):** refine gate 1 **approved**; design gate 2 **approved**
(`design.md` hashed; plan-gate clean vs ADR-0001/0002/0003, `deviation.json`
valid). **Plan stage pending** — run in a **fresh `/lifecycle-plan
001-dag-plan-primitive` session** (produces `tasks.md` build milestones for the
new primitive, re-runs the plan-gate, stops at gate 3). The design decisions
below are now settled in the approved `design.md`; kept here for continuity. Do
not edit the hashed artifacts:
- `openspec/changes/001-dag-plan-primitive/{proposal.md,design.md}`
- `.../specs/plan-schema/spec.md`, `.../plan-validation/spec.md`, `.../plan-projection/spec.md`

**Design outcomes (see `design.md` for full rationale):** repo
**`milestoned-plan-dag`**; **YAML is source of truth, markdown is a read-only
`render`**; module = **schema + CLI + skills, Go, CLI-only consumption**; identity
= number + slug (slug derived-from-title by default, explicit for stability);
steps → `steps:[{text,done}]`, contract → native `contract:` mapping (D6
realization, no refine amendment); no module-level constitution.

## Design-stage decisions still OWED (resolve at design)

1. **New repo name** (framework-neutral, MIT) + create it as a submodule per
   ADR-0001/0003. **User decided NOT to give this module its own constitution /
   registering ADR** — skip governance ceremony for it (reading (a)).
2. **How `spec-lifecycle` consumes the primitive** — shell-out to the binary vs.
   link as a Go library vs. vendor. spec-lifecycle stops owning the plan grammar.
3. **JSON Schema publishing** mechanics + editor wiring (YAML Language Server via
   a `# yaml-language-server: $schema=…` header or filename glob).
4. **Migration** of existing sequential `tasks.md` (implicit-chain rule ⇒ minimal).
5. **Exact YAML grammar syntax + CLI command names** — incl. the projection
   command that replaces `apply --format json`.
6. **Impl language** — family convention is pure Go (spec-lifecycle is Go);
   validator/projection likely Go. Confirm at design.

## Locked refine constraints design MUST honor

- **YAML is the ONLY serialization**; retire `--format json`. JSON Schema = the
  published cross-language validation contract. `schemaVersion: 0.1.0` (no `v`).
- **Milestone identity** = ordinal `number` + stable kebab `slug`
  (position-independent). `depends-on` references slugs; single edge type
  ("must complete before"). **No-deps = implicit document-order chain**, so
  existing sequential plans stay valid.
- **DAG-capable schema + projection expose edges; concurrent EXECUTION is a
  non-goal** (executor stays serial; edges are there for a future consumer).
- **Contract REQUIRED** per milestone: `check` (with `none` sentinel for
  unverifiable work), `criteria` (non-empty), `paths` (present; `[]` = empty
  diff, `**` = conscious unconfined). **Checkbox steps required**
  (`--force-incomplete-tasks` escape).
- **Projection**: YAML view (number/slug/goal/deliverables/contract/steps+state/
  `depends_on`); edges authoritative + deterministic topological order
  (tie-break by number).
- **Validator**: reject cycles, dangling edges, duplicate slug/number, malformed
  contract; **WARN** (not fail) on overlapping write-`paths` between independent
  (concurrently-runnable) milestones — hard-enforce in the concurrency era.
- **Optional depth slots reserved, not mandated** (Create/Modify/Test
  deliverables, per-step file refs, test-shaped criteria). Raising authoring
  depth to RPI-grade is a **separate skill change**; the design-stage
  "file-map / research" artifact belongs to **spec-lifecycle's** `design`
  stage and is a linked follow-on — not this change.

## Consumers

- `spec-lifecycle` — plan stage (consumes the schema/CLI).
- `agent-orchestration` — consumes the YAML projection for execution ordering;
  stays serial.

## Cross-refs

- `tasks/spec-lifecycle-backlog.md` #3 (stable identifiers — the slug aligns with
  the derived-slug direction) and #4 (traceability).
- `tasks/plan-structured-contracts-gap.md` (why contracts are mandatory-for-us —
  the vacuous-gate incident this schema makes impossible by construction).
- Factory.ai comparison (this session): Factory has no machine-checkable
  per-milestone verification, no structural checklist, no DAG — the required
  contract + checkbox integrity is the differentiator, not a reinvention.
