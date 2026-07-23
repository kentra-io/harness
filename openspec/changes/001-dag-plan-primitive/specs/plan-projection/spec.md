## ADDED Requirements

### Requirement: The plan is projectable to machine-readable YAML
The primitive SHALL provide a single CLI command that reads the human-authored
plan and emits a machine-readable **YAML** projection, so a consumer never has
to parse the authoring markdown itself. The projection SHALL expose, per
milestone: `number`, `slug`, `goal`, `deliverables`, the full `contract`
(`check` / `criteria` / `paths`), `steps` with their checkbox state, and
`depends_on` edges.

#### Scenario: A consumer reads contracts without parsing markdown
- **GIVEN** a validated plan authored in markdown
- **WHEN** the projection command is run
- **THEN** it emits YAML in which each milestone carries its slug, contract, steps with checkbox state, and depends_on edges

### Requirement: Edges are authoritative and a topological order is provided
The projection SHALL treat `depends_on` edges as the authoritative dependency
structure — from which a future concurrent consumer computes its own ready-set —
and SHALL additionally include a computed valid **topological order** for the
current serial executor to consume directly. The topological order SHALL be
deterministic, breaking ties by milestone `number`, so the projection is
reproducible.

#### Scenario: Independent milestones are linearized deterministically
- **GIVEN** a plan where milestones `2` and `3` both depend only on `1`
- **WHEN** the projection is emitted twice
- **THEN** both emissions list the same topological order with `2` before `3`
- **AND** the `depends_on` edges are present alongside that order

### Requirement: YAML is the only projection format
The projection command SHALL emit YAML as its sole format and SHALL NOT expose a
JSON format flag; the retired `--format json` option is not reintroduced.

#### Scenario: There is no JSON format flag
- **GIVEN** the projection command
- **WHEN** its interface is inspected
- **THEN** it emits YAML by default and offers no `--format json` option
