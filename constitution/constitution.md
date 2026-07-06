<!--
  GENERATED FILE -- projection of the ADR log in constitution/adr/.
  Do not hand-edit; changes will be overwritten by the next "constitution
  regen". Only rule-bearing (## Rule) active ADRs project here; to change a
  rule, add, supersede, or deprecate an ADR instead.
-->

# Constitution

## architecture

### Standalone primitives as submodules

Every standalone, reusable primitive lives in its own repo and is consumed by the
harness as a git submodule — never absorbed into a harness-internal directory.

ADR-0001 · 2026-07-05

### Neutral mechanism, branded methodology

Primitive repos stay framework-neutral, unbranded, and MIT-licensed (reusable beyond
kentra). The `kentra-` prefix appears only at the branded layer (schemas, umbrella
methodology).

ADR-0002 · 2026-07-05

### Primitive repo shape

Each primitive ships a framework-neutral core, agent-agnostic skills, thin
per-framework adapters, an in-repo spec + implementation plan, and an MIT license.

ADR-0003 · 2026-07-05
