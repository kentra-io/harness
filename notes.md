> **RESOLVED 2026-07-02 — every question in this file is now answered in [`spec-lifecycle/spec-lifecycle.md`](./spec-lifecycle/spec-lifecycle.md)** (naming → OpenSpec vocabulary + `plan.md`; NFRs → §4.1 routing rule; plan granularity → `lifecycle.yml`; TDD → validation contracts + repro-first; interface → files canonical; living-spec → deterministic delta fold + replay guard; constitution scope → the sibling primitive). Kept as the raw origin of the questions.

Planning:
- relationship between living-spec and specs, and ADRs & constitution?
- naming?
- NFRs vs technical design (reconciliate)


Parameters:
- plan granularity

Todo:
- tdd?
- interface (files vs conductor)
- constitution scope

Constitution:
- supports planning. Purpose: the "HOW" of the project, how we build things. This is the document that requires most human activity. Basically human takes proper look into constitution and functional specifications, other aspects are lower priority
- event sourced projection from ADR
  - change in a constitution always written as ADR
- greenfield vs brownfield?
  - greenfield now, brownfield deferred
- governance:
  - right now: each plan is validated whether it conforms with the constitution
  - deferred: we need an async background process to find deviations between codebase and constitution (create a todo) 

Functional spec:
- feature specs rolled up into current system spec (projection, event sourced)

Shared element: event sourcing docs (both functional spec & constitution)

Spec says what
plan says how (based on the constitution)
tasks is step-by-step breakdown
    - how big should each step be? this should be tunable by "plan granularity" - a repo-scoped configuration property