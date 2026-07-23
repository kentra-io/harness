## Milestone 1: Repo scaffold — standalone Go primitive, MIT, ADR-0003 shape
**Goal** — Stand up `milestoned-plan-dag` as a framework-neutral, MIT-licensed single-binary Go repo carrying the primitive shape the harness constitution mandates.
**Deliverables** — `go.mod` (module `milestoned-plan-dag`); `LICENSE` (MIT); `README.md`; `cmd/milestoned-plan-dag/main.go` (arg dispatch skeleton for `validate`/`render`/`resolve`); empty `internal/` package skeleton; in-repo `spec.md` + `implementation-plan.md`; `.github/workflows/ci.yml` running build + test + vet.
**Validation contract** — checkable acceptance criteria, pre-committed:
  - `go build ./... && go vet ./...` — compiles clean.
  - `LICENSE` is MIT and `spec.md` + `implementation-plan.md` exist — ADR-0003 repo shape present.
  - No `kentra` branding anywhere in the repo — ADR-0002 neutral mechanism.
  - Foundational — makes no spec scenario pass directly.

  ```contract
  check: go build ./... && go vet ./... && test -f LICENSE && grep -q MIT LICENSE && test -f spec.md && test -f implementation-plan.md && ! grep -riq kentra .
  criteria: (project root = the new milestoned-plan-dag repo) A standalone Go module named milestoned-plan-dag builds and vets clean with a single cmd/milestoned-plan-dag entrypoint that dispatches to validate/render/resolve subcommand stubs. The repo is framework-neutral (no kentra branding, no non-Go language-runtime dependency), MIT-licensed, and ships an in-repo spec.md + implementation-plan.md per harness ADR-0003. CI runs go build + go vet + go test on every push.
  paths:
    - go.mod
    - go.sum
    - LICENSE
    - README.md
    - cmd/milestoned-plan-dag/**
    - internal/**
    - spec.md
    - implementation-plan.md
    - .github/workflows/ci.yml
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] `go mod init milestoned-plan-dag`; add MIT `LICENSE` and a `README.md` stating the primitive's purpose (machine-first YAML plan format, DAG of verifiable milestones) with no kentra branding.
  2. [ ] Add `cmd/milestoned-plan-dag/main.go` dispatching on `os.Args[1]` to `validate`/`render`/`resolve` stub handlers (each returning "not implemented" for now) and a `--help`/usage path.
  3. [ ] Write in-repo `spec.md` (the plan format + validator + projection contract, distilled from this change's proposal/design/spec deltas) and `implementation-plan.md` (this milestone list) per ADR-0003.
  4. [ ] Add `.github/workflows/ci.yml` running `go build ./... && go vet ./... && go test ./...` on push/PR.

## Milestone 2: Plan data model, YAML loader, and published JSON Schema
**Goal** — Define the canonical YAML plan format as typed Go + a published draft-2020-12 JSON Schema, and load+shape-validate a plan file.
**Deliverables** — `internal/plan/` (types: `Plan{schemaVersion, milestones}`, `Milestone{number, slug, goal, deliverables, contract, steps, dependsOn}`, `Contract{check, criteria, paths}`, `Step{text, done}`, plus optional slots — `Deliverables{create,modify,test}`, per-`Step.files`, test-shaped `criteria`); `internal/plan/load.go` (YAML → model with `# yaml-language-server: $schema=` header support); `schema/plan.schema.json` (draft 2020-12); `testdata/valid/*.yaml` fixtures; `internal/schema/` (embeds + shape-validates against the JSON Schema).
**Validation contract** — checkable acceptance criteria, pre-committed:
  - `go test ./internal/plan/... ./internal/schema/...` — model, loader, and shape-validation tests pass.
  - `schema/plan.schema.json` is valid draft-2020-12 and shape-validates the fixtures.
  - Makes pass (plan-schema): "A plan stamps its schema version", "Steps carry checkbox state", "A plan omitting the optional slots is still valid".

  ```contract
  check: go test ./internal/plan/... ./internal/schema/... && go vet ./...
  criteria: (project root = the milestoned-plan-dag repo) The YAML plan model round-trips schemaVersion (semver, no v-prefix, 0.1.0), milestone number + kebab slug (slug defaults to a derived kebab of the title when omitted, explicit override allowed), goal, deliverables (prose OR structured create/modify/test map), a native contract mapping (check/criteria/paths), steps as [{text, done}] parsed from [ ]/[x] checkbox state, optional per-step files, and depends-on slug lists. A published draft-2020-12 JSON Schema at schema/plan.schema.json shape-validates each testdata/valid fixture; a plan omitting every optional slot (prose deliverables, no per-step files, no test-shaped criteria) still validates; a plan lacking schemaVersion fails shape validation.
  paths:
    - go.mod
    - internal/plan/**
    - internal/schema/**
    - schema/plan.schema.json
    - testdata/**
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] Define the Go model in `internal/plan/types.go` (Plan/Milestone/Contract/Step + optional slots), with slug-derivation from title.
  2. [ ] Write `internal/plan/load.go` parsing YAML into the model, mapping `[ ]`/`[x]` to `Step.done`, and tolerating the `# yaml-language-server: $schema=` editor header.
  3. [ ] Author `schema/plan.schema.json` (draft 2020-12) covering shape: required `schemaVersion`, required per-milestone `number`/`slug`/`contract{check,criteria,paths}`/`steps`, optional slots; embed it via `go:embed` in `internal/schema` and add a JSON-Schema shape-validation pass.
  4. [ ] Add `testdata/valid/` fixtures (sequential, branching, contract-with-`none`, `paths: []`, optional-slots-present, optional-slots-absent) and tests asserting load + shape-validate + schemaVersion/steps/optional-slot behavior.

## Milestone 3: `validate` — DAG resolution, semantic checks, and the path-overlap warning
**Goal** — Implement the `validate` command: shape validation plus the semantic DAG guarantees (cycle/dangling/duplicate/contract) and the non-fatal overlapping-write-paths warning.
**Deliverables** — `internal/dag/` (edge resolution: implicit document-order chain for no-`depends-on` milestones, cycle detection, reachability); `internal/validate/` (semantic rules + a warning surface); `validate` wired into `cmd/milestoned-plan-dag`; `testdata/invalid/*.yaml` + `testdata/warn/*.yaml` fixtures.
**Validation contract** — checkable acceptance criteria, pre-committed:
  - `go test ./internal/dag/... ./internal/validate/...` — resolution + all reject/warn cases pass.
  - `go run ./cmd/milestoned-plan-dag validate testdata/valid/sequential.yaml` — exits 0.
  - Makes pass (plan-schema): "A milestone is addressable by a position-independent slug", "No explicit edges yields a sequential chain", "Explicit edges yield a branching DAG", "An unverifiable milestone declares check none", "An empty path set means an empty diff". Makes pass (plan-validation): "A dependency cycle is rejected", "A dangling edge is rejected", "A duplicate slug is rejected", "A milestone with no contract is rejected", "An empty criteria is rejected", "Overlapping paths on independent milestones warn but pass".

  ```contract
  check: go test ./internal/dag/... ./internal/validate/... && go run ./cmd/milestoned-plan-dag validate testdata/valid/sequential.yaml
  criteria: "(project root = the milestoned-plan-dag repo) validate runs JSON-Schema shape validation then semantic validation. Edge resolution gives every milestone with no depends-on an implicit edge on the immediately preceding milestone (document order), so a no-edge plan is a sequential chain and explicit depends-on yields a branching DAG; slugs are position-independent and moving a milestone keeps edges resolving. It rejects (exit 1, naming the offender): dependency cycles, depends-on slugs that reference no milestone, duplicate slug or duplicate number, and a missing/malformed contract (check that is neither a command nor the none sentinel, empty criteria, or absent paths). It accepts check: none and paths: [] as well-formed. When two milestones with no dependency path between them declare overlapping paths globs, it emits a warning naming both milestones and the glob but still exits 0."
  paths:
    - internal/dag/**
    - internal/validate/**
    - cmd/milestoned-plan-dag/**
    - testdata/**
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] `internal/dag/resolve.go`: build the edge set (explicit `depends-on` ∪ implicit preceding-milestone chain), with a reachability helper for "is there a dependency path between A and B".
  2. [ ] `internal/dag/cycle.go`: cycle detection that names the cycle members.
  3. [ ] `internal/validate/validate.go`: run shape validation, then reject cycles / dangling slugs / duplicate slug or number / malformed-or-missing contract (naming each offender); accept `check: none` and `paths: []`.
  4. [ ] Add the overlapping-write-paths warning for independent milestones (glob overlap between milestones with no dependency path), non-fatal (exit 0, warning on stderr).
  5. [ ] Wire `validate <plan.yaml>` into `cmd/milestoned-plan-dag` (exit 0 clean/warn, 1 on rejection); add `testdata/invalid/` + `testdata/warn/` fixtures and tests for every reject/warn scenario.

## Milestone 4: Projection — `resolve` (machine YAML) and `render` (human markdown)
**Goal** — Emit the machine-readable YAML projection with an authoritative edge set + deterministic topological order (`resolve`), and a read-only markdown view for humans (`render`).
**Deliverables** — `internal/resolve/` (YAML projection: per-milestone number/slug/goal/deliverables/contract/steps+done/`depends_on`, plus a computed topological order, tie-broken by `number`); `internal/render/` (YAML → markdown with `[ ]`/`[x]` steps + contract block); both wired into `cmd/milestoned-plan-dag`; golden fixtures under `testdata/`.
**Validation contract** — checkable acceptance criteria, pre-committed:
  - `go test ./internal/resolve/... ./internal/render/...` — projection + render golden tests pass, including a determinism assertion (two emissions byte-identical).
  - `resolve` exposes no `--format json` flag (retired).
  - Makes pass (plan-projection): "A consumer reads contracts without parsing markdown", "Independent milestones are linearized deterministically", "There is no JSON format flag". (`render` realizes design D1/D7; it makes no spec scenario pass.)

  ```contract
  check: go test ./internal/resolve/... ./internal/render/... && go run ./cmd/milestoned-plan-dag resolve testdata/valid/branching.yaml >/dev/null && ! go run ./cmd/milestoned-plan-dag resolve --format json testdata/valid/sequential.yaml
  criteria: (project root = the milestoned-plan-dag repo) resolve reads a validated plan and emits a YAML projection exposing, per milestone, number/slug/goal/deliverables, the full contract (check/criteria/paths), steps with checkbox/done state, and depends_on edges; the depends_on edges are authoritative and the projection additionally carries a computed valid topological order that is deterministic (ties broken by number) and byte-identical across repeated runs. YAML is the sole output format and resolve rejects a --format json flag (unknown-flag, non-zero exit). render emits read-only human markdown (checkbox steps + contract block) from the same model; nothing parses it back.
  paths:
    - internal/resolve/**
    - internal/render/**
    - cmd/milestoned-plan-dag/**
    - testdata/**
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] `internal/resolve/resolve.go`: reuse `internal/dag` to produce a deterministic topological order (Kahn's algorithm with a `number` tie-break), then marshal the projection struct (edges + order + full per-milestone data) to YAML.
  2. [ ] Wire `resolve <plan.yaml>` into `cmd/milestoned-plan-dag` with YAML-only output and no `--format` flag; add a golden fixture + a determinism test (emit twice, assert byte-identical).
  3. [ ] `internal/render/render.go`: YAML model → markdown matching the human `tasks.md` shape (Goal/Deliverables/Validation contract/Steps with `[ ]`/`[x]`); wire `render <plan.yaml>` and add a golden-markdown test.

## Milestone 5: Authoring skills
**Goal** — Ship the agent-agnostic authoring skill(s) that guide writing a valid YAML plan against this schema.
**Deliverables** — `skills/plan-author/SKILL.md` (grammar + CLI workflow) and `skills/plan-author/example.yaml` (a worked plan that validates); a README section pointing at the skill.
**Validation contract** — checkable acceptance criteria, pre-committed:
  - The skill's worked example validates via the real CLI.
  - Makes no spec scenario pass — skills are the authoring surface, not a schema behavior; depth-raising to RPI-grade is the separate linked follow-on.

  ```contract
  check: test -f skills/plan-author/SKILL.md && go run ./cmd/milestoned-plan-dag validate skills/plan-author/example.yaml
  criteria: (project root = the milestoned-plan-dag repo) An agent-agnostic authoring skill documents the YAML plan grammar (number+slug identity, depends-on DAG with the implicit-chain fallback, the mandatory check/criteria/paths contract with its none/[]/** semantics, checkbox steps, schemaVersion 0.1.0, and the reserved optional detail slots) and the CLI workflow (validate → resolve/render). It ships a worked example plan that passes validate. The skill names no specific agent runtime.
  paths:
    - skills/**
    - README.md
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] Write `skills/plan-author/SKILL.md`: the YAML grammar, the mandatory-contract rules + conscious escapes (`check: none`, `paths: []`, `**`), the DAG/implicit-chain model, and the `validate`/`resolve`/`render` workflow.
  2. [ ] Add `skills/plan-author/example.yaml` — a small branching plan exercising slugs, `depends-on`, contracts, and checkbox steps — and confirm it passes `validate`.
  3. [ ] Add a README section linking the skill and the JSON Schema for editor wiring.

## Milestone 6: Integrate as a harness submodule + record consumer follow-ons
**Goal** — Register the finished primitive as a harness git submodule per ADR-0001 and point consumers at the CLI/YAML contract, without reworking them here.
**Deliverables** — `.gitmodules` entry + submodule at `milestoned-plan-dag/` (pinned commit); a primitive-registry line in harness `AGENTS.md`; a follow-on note in `tasks/dag-plan-primitive-design-handoff.md` recording the two out-of-scope consumer reworks.
**Validation contract** — checkable acceptance criteria, pre-committed:
  - Submodule present and builds from its pinned commit.
  - `AGENTS.md` lists the new primitive; the handoff note records the two linked follow-ons.
  - Makes no spec scenario pass — integration/governance milestone conforming to ADR-0001/0003.

  ```contract
  check: git submodule status milestoned-plan-dag && (cd milestoned-plan-dag && go build ./...) && grep -q milestoned-plan-dag AGENTS.md
  criteria: (project root = the harness repo) milestoned-plan-dag is added as a git submodule of harness per ADR-0001, pinned to a commit that builds clean; harness AGENTS.md's primitive registry lists it as a framework-neutral, MIT, standalone-repo-as-submodule primitive per ADR-0003 (no kentra branding, no module-level constitution — the design decision); and tasks/dag-plan-primitive-design-handoff.md records the two out-of-scope linked follow-ons — spec-lifecycle's plan-stage rework to shell out to `milestoned-plan-dag validate`, and agent-orchestration reading the `resolve` YAML for execution ordering.
  paths:
    - .gitmodules
    - milestoned-plan-dag
    - AGENTS.md
    - tasks/dag-plan-primitive-design-handoff.md
  ```
**Steps** — ordered breakdown, sized per `planGranularity` (lifecycle.yml, spec-lifecycle.md §10):
  1. [ ] Push the `milestoned-plan-dag` repo and add it as a harness submodule (`git submodule add`), pinning the commit.
  2. [ ] Add the primitive-registry line to harness `AGENTS.md` (neutral/MIT/submodule, CLI-only YAML contract, no module constitution).
  3. [ ] Append a follow-on note to `tasks/dag-plan-primitive-design-handoff.md` scoping the two consumer reworks (spec-lifecycle plan-stage shell-out; agent-orchestration `resolve` consumption) as separate changes.
