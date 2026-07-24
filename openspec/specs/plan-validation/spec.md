# plan-validation Specification

## Purpose
TBD - created by archiving change 001-dag-plan-primitive. Update Purpose after archive.
## Requirements
### Requirement: A valid plan is an acyclic dependency graph
The validator SHALL reject a plan whose `depends-on` edges (explicit or
implicit) form a cycle, so that a valid plan always admits at least one
topological order.

#### Scenario: A dependency cycle is rejected
- **GIVEN** milestones `a` with `depends-on: [b]` and `b` with `depends-on: [a]`
- **WHEN** the plan is validated
- **THEN** validation fails and names the cycle

### Requirement: Dependency edges resolve to existing milestones
The validator SHALL reject any `depends-on` entry that references a slug not
defined by a milestone in the same plan.

#### Scenario: A dangling edge is rejected
- **GIVEN** a milestone declaring `depends-on: [does-not-exist]`
- **WHEN** the plan is validated
- **THEN** validation fails and names the unresolved slug

### Requirement: Milestone identity is unique
The validator SHALL reject a plan containing two milestones with the same `slug`
or the same `number`.

#### Scenario: A duplicate slug is rejected
- **GIVEN** two milestones that both declare `slug: parse-dag`
- **WHEN** the plan is validated
- **THEN** validation fails and names the duplicated slug

### Requirement: Every milestone has a well-formed contract
The validator SHALL reject a milestone whose contract is missing or malformed —
a `check` that is neither a command nor the `none` sentinel, an empty
`criteria`, or an absent `paths` key.

#### Scenario: A milestone with no contract is rejected
- **GIVEN** a milestone that declares no `contract` block
- **WHEN** the plan is validated
- **THEN** validation fails and names the milestone

#### Scenario: An empty criteria is rejected
- **GIVEN** a milestone whose contract has an empty `criteria`
- **WHEN** the plan is validated
- **THEN** validation fails and names the milestone

### Requirement: Concurrently-runnable milestones with overlapping write-paths are warned
The validator SHALL emit a warning — not a failure — when two milestones with no
dependency path between them (and therefore potentially runnable concurrently by
a future executor) declare overlapping `paths`, surfacing the write-conflict
hazard without blocking today's serial execution.

#### Scenario: Overlapping paths on independent milestones warn but pass
- **GIVEN** independent milestones `b` and `c` that both list `internal/foo/**` in `paths`
- **WHEN** the plan is validated
- **THEN** validation succeeds
- **AND** a warning names `b`, `c`, and the overlapping path

