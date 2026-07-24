# Extract a standalone, DAG-capable plan primitive — Design

## Context

The plan format (milestone grammar + per-milestone verification contracts) is
today embedded in `spec-lifecycle`, authored as markdown `tasks.md`, strictly
sequential, and projected to a machine format via `apply --format json`. Gate 1
(refine) approved extracting it into a standalone primitive (ADR-0001) and
generalizing the ordered list to a DAG, with YAML as the sole serialization and
a published JSON Schema as the validation contract.

This design settles the *how*: the artifact model (what is authoritative), the
module shape, how consumers reach it, and how the mandatory-contract / DAG /
identity rules from the refine deltas are realized. It is framework-neutral and
MIT; per the user it does **not** get its own module-level constitution.

Constraints carried from refine (`proposal.md`, `specs/plan-{schema,validation,projection}/spec.md`):
number+slug identity, `depends-on` DAG with implicit-chain fallback, required
contract (`check` w/ `none`, non-empty `criteria`, `paths` present with `[]`/`**`
semantics), required step-completion tracking, `schemaVersion 0.1.0`, a
deterministic topological order, validator guarantees (cycle/dangling/dup/
malformed reject; path-overlap warn), and reserved optional detail slots.

## Goals / Non-Goals

**Goals:**
- A standalone repo `milestoned-plan-dag` (MIT, submodule) owning the plan format.
- **YAML as the single source of truth**; a canonical **JSON Schema**; a Go CLI
  (`schema + CLI + skills`) that validates, renders to markdown, and resolves the
  DAG order. Every consumer uses only the YAML/CLI contract.
- Realize the refine requirements (DAG, mandatory contract, identity, versioning,
  determinism) on the YAML model without regressing any behavioral guarantee.

**Non-Goals:**
- Concurrent execution engine (schema is DAG-capable; executor stays serial).
- The authoring-skill depth-raising and the spec-lifecycle design-stage
  "file-map" artifact — separate, linked changes.
- A module-level constitution / registering ADR for `milestoned-plan-dag`.
- A separately-published Go library (see D4 — YAGNI until a consumer needs it).

## Decisions

### D1 — YAML is the source of truth; markdown is a read-only projection
The agent authors the plan **in YAML** (guided by skills). Markdown is generated
*from* the YAML by the CLI (`render`) for human reading only; nothing parses
markdown. This inverts today's markdown-source/JSON-projection model.
- *Why:* a machine-first schema shared with the world should have a structured
  canonical form the JSON Schema validates directly and editors (YAML Language
  Server) validate live as it is written; markdown-as-truth forces every consumer
  to re-parse prose.
- *Alternatives:* (a) keep markdown-source + YAML projection — rejected: prose
  parsing, no live schema validation, weaker machine contract. (b) markdown-source
  + YAML projection with markdown authoritative — the status quo, rejected for the
  same reasons.

### D2 — The primitive is not bound by OpenSpec format-compat
`spec-lifecycle`'s ADR-0001/0002 bind its **`spec.md` delta** grammar to the
OpenSpec on-disk format. The milestone/`tasks.md` grammar was always a
kentra-origin extension, never OpenSpec. `milestoned-plan-dag` owns it outright,
which is what lets contracts and step-completion be **mandatory** (not opt-in).
- *Alternative:* preserve opt-in "for OpenSpec-tasks compat" — rejected: that
  compatibility never existed; opt-in is what produced the vacuous-gate incident.

### D3 — Module shape: schema + CLI + skills (Go)
- **schema:** the canonical YAML plan format + a published **JSON Schema**
  (draft 2020-12). Two-tier validation — JSON Schema covers shape (for external
  consumers + live editor validation); the CLI is authoritative for the semantic
  DAG rules JSON Schema cannot express (cycles, cross-milestone path overlap).
- **CLI:** one Go static binary (family convention: single binary, no runtime
  dep). Commands: `validate` (shape + semantic), `render` (YAML → markdown),
  `resolve` (YAML → YAML with the topological order + resolved implicit edges
  precomputed, for machine consumers). No JSON output anywhere.
- **skills:** the authoring skills that write the YAML plan (depth-raising is the
  separate follow-on).
- *Alternative:* non-Go impl — rejected: breaks the single-static-binary family
  convention and the shared-tooling story with `spec-lifecycle`.

### D4 — Consumption: CLI-only, uniform YAML boundary
All consumers use the CLI + YAML: `spec-lifecycle` (Go) shells out to
`milestoned-plan-dag validate` at its plan-stage gate; `agent-orchestration`
(Python) reads the YAML or `resolve` output; humans/editors use the CLI + JSON
Schema.
- *Why:* `agent-orchestration` (Python) must use the CLI regardless. Routing
  `spec-lifecycle` through the same CLI keeps every consumer on the identical
  YAML contract (D1's "only the YAML anywhere") — no privileged in-process path —
  and validation runs at interactive gate time where subprocess cost is
  negligible.
- *Alternative:* ship an importable Go **library** for `spec-lifecycle` to call
  in-process — rejected now (YAGNI): it reaches past the YAML contract into
  private structs, adds a compile-time submodule dependency, and buys only
  irrelevant gate-time latency. Can be exposed later if a real need appears.

### D5 — Milestone identity: number + slug, slug derived-by-default
Each milestone has an ordinal `number` and a stable kebab `slug`; `depends_on`
references slugs. `slug` is **explicit but defaults to a derived kebab-slug of
the title** when omitted.
- *Why:* derived-by-default gives legacy/simple plans a slug for free and keeps
  authoring light; an explicit slug is the escape for rename-stability. Crucially,
  slugs are only *referenced* when a milestone declares `depends_on`, so
  derived-slug rename-instability never bites the common no-deps/sequential case,
  and a rename that breaks an edge fails the dangling-edge check loudly.
- *Alternatives:* derived-only (no override) — rejected: no rename-stability for
  real DAGs. Stored-opaque-id — rejected: heavier than warranted; revisit with
  backlog #3.

### D6 — Realizing the markdown-worded refine requirements on YAML
- **Steps** (refine: "authored as checkbox items `[ ]`/`[x]`") → a native
  `steps: [{ text, done }]` list; `render` shows `[ ]`/`[x]`. The behavioral
  guarantee is unchanged (completion structurally tracked; archive refuses
  incomplete unless `--force-incomplete-tasks`).
- **Contract** (refine: "embedded block") → a native `contract:` mapping
  (`check` / `criteria` / `paths`), same fields and semantics (`check: none`,
  `paths: []` = empty diff, `paths: ["**"]` = conscious unconfined).
- **Optional detail slots** → optional YAML fields (`deliverables` as a
  `create/modify/test` map; per-step `files`; test-shaped `criteria`); absent =
  valid.
- These are serialization realizations of behavior the refine deltas already
  fixed, not requirement changes. Recorded here rather than amending the hashed
  refine artifacts; called out for the human at gate 2.

### D7 — Projection direction & determinism
`resolve` emits YAML with `depends_on` edges authoritative plus a computed valid
topological order, ties broken by `number` (reproducible). `render` emits
markdown for humans. Neither is JSON; the retired `--format json` is not
reintroduced.

### D8 — Migration
Existing markdown `tasks.md` plans convert to the YAML source once (a `migrate`
helper or manual). The DAG *dependency model* is preserved (no `depends_on` =
implicit chain), but the medium changes, so "existing plans stay valid unchanged"
is realized as "the sequential model is preserved; the file is converted." In
practice near-zero: kafka-dq (the only real prior plan) is irrelevant.

## NFR Discharge

(none declared with a design home.) The NFR-flavored properties this change
carries are **behavior-observable** and therefore live in the spec deltas, not
here: projection determinism/reproducibility and schema-version evolution
(`plan-projection`, `plan-schema`). The primitive-shape properties
(framework-neutral, MIT, standalone repo + submodule) are governed by the
harness constitution ADR-0001/0003, not a per-change NFR.

## ADR proposals

(none) — this change *follows* the harness constitution (ADR-0001 standalone
primitive as submodule, ADR-0002 neutral mechanism, ADR-0003 primitive repo
shape) and requires no amendment. Per the user, `milestoned-plan-dag` gets no
module-level constitution of its own.

## Risks / Trade-offs

- **YAML-source flip ripples into spec-lifecycle + a migration.** → Mitigation:
  spec-lifecycle's plan-stage rework is a small linked follow-on; migration is
  near-zero (kafka-dq irrelevant).
- **Derived-slug rename instability.** → Mitigation: slugs only matter under
  `depends_on`; the dangling-edge validator catches a rename that breaks an edge;
  explicit slug is the stability escape.
- **Mandatory contracts raise the authoring bar.** → Mitigation: conscious
  escapes (`check: none`, `paths: []`, `--force-incomplete-tasks`) + an explicit
  non-goal that non-verifiable/non-decomposable work should not use this schema.
- **CLI shell-out from a Go consumer.** → Mitigation: gate-time cadence makes
  subprocess cost negligible; uniform YAML contract is worth it.
- **Two refine requirements realized differently than worded (D6).** →
  Mitigation: behavioral guarantees are identical; surfaced explicitly at gate 2
  so the human can require a refine amendment instead if they prefer.
