# plan-schema Specification

## Purpose
TBD - created by archiving change 001-dag-plan-primitive. Update Purpose after archive.
## Requirements
### Requirement: Milestone identity
Every milestone SHALL carry a stable, kebab-case `slug` and an ordinal
`number`. The `slug` is the identity dependency edges reference; the `number`
gives humans a reading order and a deterministic tie-break. A `slug` SHALL be
unique within a plan and SHALL be independent of the milestone's position, so
reordering milestones does not change any edge.

#### Scenario: A milestone is addressable by a position-independent slug
- **GIVEN** a plan whose second milestone has `slug: parse-dag`, `number: 2`
- **WHEN** the milestone is moved earlier so its `number` becomes `1`
- **THEN** its `slug` is still `parse-dag`
- **AND** any milestone declaring `depends-on: [parse-dag]` still resolves to it

### Requirement: Dependency edges form a DAG
A milestone MAY declare `depends-on` listing the slugs of milestones that MUST
complete before it. The relation SHALL be interpreted as a directed acyclic
graph of "must complete before" edges; `depends-on` is the only edge type. A
milestone that declares no `depends-on` SHALL inherit an implicit dependency on
the immediately preceding milestone in document order, so a plan with no
explicit edges is a sequential chain and an existing sequential plan stays
valid unchanged.

#### Scenario: No explicit edges yields a sequential chain
- **GIVEN** a three-milestone plan in which no milestone declares `depends-on`
- **WHEN** the plan's dependency relation is resolved
- **THEN** milestone 2 depends on milestone 1 and milestone 3 depends on milestone 2

#### Scenario: Explicit edges yield a branching DAG
- **GIVEN** milestones `a`, `b`, `c` where `b` and `c` each declare `depends-on: [a]`
- **WHEN** the dependency relation is resolved
- **THEN** `b` and `c` both depend on `a` and neither depends on the other

### Requirement: Every milestone carries a verification contract
Every milestone SHALL carry a verification `contract` with three fields:
`check`, `criteria`, and `paths`. `check` SHALL be a single executable command
or the sentinel `none` for genuinely unverifiable work — it MUST be present, so
absence is never silent. `criteria` SHALL be non-empty plain-language pass/fail
text a judge can evaluate. `paths` SHALL be present as the milestone's allowed
write-set; an empty list means the milestone's diff MUST be empty, and a `**`
wildcard means the diff is consciously unconfined.

#### Scenario: An unverifiable milestone declares check none
- **GIVEN** a milestone whose outcome has no automated proof
- **WHEN** it declares `check: none` with non-empty `criteria` and a `paths` set
- **THEN** the contract is well-formed

#### Scenario: An empty path set means an empty diff
- **GIVEN** a verify-only milestone with `paths: []`
- **WHEN** a consumer interprets its contract
- **THEN** the milestone is required to produce no file changes

### Requirement: Steps are checkbox-tracked
A milestone's steps SHALL be authored as checkbox items (`[ ]` / `[x]`) so step
completion is structurally visible and a downstream consumer can refuse to treat
a plan as complete while tracked steps remain unchecked.

#### Scenario: Steps carry checkbox state
- **GIVEN** a milestone with three steps
- **WHEN** the milestone is authored
- **THEN** each step is a `[ ]` or `[x]` item rather than a plain bullet

### Requirement: The plan declares a schema version
A plan SHALL declare a `schemaVersion` using semantic versioning without a `v`
prefix, beginning at `0.1.0`, so consumers can detect the format revision and
the format can evolve without silently breaking them.

#### Scenario: A plan stamps its schema version
- **GIVEN** a plan authored against the initial format
- **WHEN** it is written
- **THEN** it declares `schemaVersion: 0.1.0`

### Requirement: Optional structured detail slots
The schema SHALL reserve optional slots that let a plan carry file-specific
detail without mandating it: a milestone's deliverables MAY be a structured
`Create` / `Modify` / `Test` file list, a step MAY reference the file(s) it
touches, and a milestone's `criteria` MAY take a test-case shape. A plan that
omits all of these SHALL remain valid.

#### Scenario: A plan omitting the optional slots is still valid
- **GIVEN** a milestone with prose deliverables and no per-step file refs
- **WHEN** the plan is validated
- **THEN** it is accepted

