# kentra-rpir Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the gated `spec-lifecycle` primitive with `kentra-rpir`, a Claude Code plugin running one change through spec → plan → implement → review, and remove the custom orchestrator from harness.

**Architecture:** The design is `harness/kentra-rpir.md` (approved 2026-10-11) — read it once before starting; every task below restates what it needs, so you never have to go back to it for a name. Work happens in five git repositories under `/Users/jony/code/kentra/harness/`: four submodules (`milestoned-plan-dag`, `adr-sourced-constitution`, `spec-lifecycle` → renamed `kentra-rpir`, `kentra-agentic-plugins`) and harness itself. Each Part below is one repository and ends with its own commit(s); Parts A, B and C are independent and can run in parallel; D, E and F run after them, in that order.

**Tech Stack:** Go 1.2x (milestoned-plan-dag, adr-sourced-constitution), POSIX `sh` (session hook), JavaScript (Claude Code workflow scripts — plain JS, top-level `await`, no TypeScript), Markdown skills and agents for Claude Code plugins, `gh` CLI.

---

## Ground rules for every task

- **Repositories are separate git roots.** `cd` into the repository named in the task before any git command. Commit with explicit paths: `git commit -m "..." -- <paths>` (two sessions may share an index).
- **Commit author** is the configured git user. End every commit message with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- **GitHub operations** run as the bot: run every `gh` command as `GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh ...` (`command` bypasses the zsh `gh` wrapper, which would override GH_TOKEN with a stale env token). Never echo the token.
- **Outward-facing steps are marked 🔒.** Pushing tags, renaming a GitHub repository, editing the Homebrew tap, and pushing to `main` need the user's explicit go-ahead in the session at that moment. Stop and ask; do not batch them.
- **Scratch files** go in `/private/tmp/claude-501/-Users-jony-code-kentra-harness/4f73540f-e042-4a0e-8c0e-149b052a5c45/scratchpad/` (written `$SCRATCH` below), never `/tmp`.
- **Writing skills, agents and prompts:** load `writing-for-agents` (`~/.claude/skills/writing-for-agents/SKILL.md`) before writing any of them. The full text of every skill is in this plan; apply the skill when you adjust wording, and keep the substance.

## File map

| Repository | Path | Responsibility |
|---|---|---|
| milestoned-plan-dag | `internal/plan/types.go` | `Description`, `Rules` on `Plan` and `Milestone` |
| | `schema/plan.schema.json`, `internal/schema/plan.schema.json` | the same two fields in the published and embedded schema |
| | `internal/resolve/resolve.go` | carry `description`/`rules` into the projection |
| | `internal/render/render.go` | plan preamble, milestone description/rules, multi-line steps |
| | `testdata/valid/context-and-rules.yaml` | fixture exercising every new field |
| | `skills/plan-author/SKILL.md`, `skills/plan-author/example.yaml` | authoring bar raised to the superpowers plan; new fields |
| | `README.md`, `spec.md`, `docs/releasing.md`, `.goreleaser.yaml`, `.github/workflows/release.yml`, `cmd/milestoned-plan-dag/{main,version,version_test}.go` | drop spec-lifecycle as the consumer; document fields; v0.2.0 pin |
| adr-sourced-constitution | `skills/plan-gate/SKILL.md` (+ generated copies) | model-invocable; verdict in session; `deviation.json` optional |
| spec-lifecycle → kentra-rpir | everything Go, `openspec/`, `lifecycle.yml`, `evals/`, `.goreleaser.yaml`, release workflow, old `skills/` | deleted |
| | `.claude-plugin/plugin.json` | plugin `kentra-rpir` 0.3.0 |
| | `hooks/hooks.json`, `hooks/capture-session.sh`, `tests/capture-session.test.sh` | session capture with redaction |
| | `templates/{spec.md,impl-report.md,spec-retro.md,kentra-rpir.yaml}` | the files each stage writes |
| | `agents/{reviewer,implementer,verifier,retro}.md` | role system prompts |
| | `workflows/{research,self-review,spec-retro}.js`, `tests/check-workflows.mjs` | the three workflows and their syntax check |
| | `skills/{spec,plan,implement,review}/SKILL.md` (+ prompt files) | the four stages |
| | `README.md`, `docs/process.md`, `AGENTS.md`, `CLAUDE.md`, `.github/workflows/ci.yml` | docs and CI |
| kentra-agentic-plugins | `.claude-plugin/marketplace.json`, `README.md` | `kentra-rpir` entry replaces `spec-lifecycle` and `agent-orchestration-skills` |
| harness | `.gitmodules`, submodule paths, `.claudebox/Dockerfile`, `AGENTS.md`, `CLAUDE.md`, `README.md`, `kentra-sdlc.md`, `tasks/todo.md`, 8 deleted docs | rename, removal, pointer bumps |

---

# Part A — milestoned-plan-dag v0.2.0

Repository: `/Users/jony/code/kentra/harness/milestoned-plan-dag`. Standard check: `go test ./...` (run from the repo root). Start from a clean `main`: `git switch main && git pull --ff-only && git switch -c plan-context-and-rules`.

What changes and why: a plan must carry everything an implementer needs, so the format gains a markdown `description` at plan and milestone level (the why and how), and a `rules` list at both levels (references — skill names or repo paths — to what the implementer must load and the verifier grades against). Steps may already span several lines in YAML; the markdown renderer must indent the continuation lines so a step with a code block stays one list item.

### Task A1: `description` and `rules` in the model and schema

**Files:**
- Create: `testdata/valid/context-and-rules.yaml`
- Modify: `internal/plan/types.go` (struct `Plan`, struct `Milestone`)
- Modify: `schema/plan.schema.json`, `internal/schema/plan.schema.json` (must stay byte-identical — `TestEmbeddedMatchesPublished`)
- Test: `internal/plan/load_test.go`

- [ ] **Step 1: Write the fixture**

`testdata/valid/context-and-rules.yaml`:

````yaml
schemaVersion: 0.2.0
description: |
  Adds a greeting endpoint. Every milestone follows the hexagonal layout.
rules:
  - java-hexagonal
  - constitution/constitution.md
milestones:
  - number: 1
    slug: greeter
    goal: Greeter domain service
    description: |
      The domain service the adapter in milestone 2 calls.
    rules:
      - docs/naming.md
    deliverables:
      create:
        - src/main/java/greeter/Greeter.java
      test:
        - src/test/java/greeter/GreeterTest.java
    contract:
      check: ./gradlew test
      criteria:
        - name: src/test/java/greeter/GreeterTest.java::greetsByName
          given: a Greeter
          when: greet("Ada") is called
          then: it returns "Hello, Ada"
      paths:
        - src/main/java/greeter/**
        - src/test/java/greeter/**
    steps:
      - |
        [ ] Write the failing test
        ```java
        assertThat(new Greeter().greet("Ada")).isEqualTo("Hello, Ada");
        ```
      - "[ ] Implement Greeter.greet"
````

- [ ] **Step 2: Write the failing load test**

Append to `internal/plan/load_test.go`:

```go
// plan-schema: "A plan and its milestones carry context and rules".
func TestContextAndRulesLoad(t *testing.T) {
	p := loadFixture(t, "context-and-rules.yaml")
	if p.Description != "Adds a greeting endpoint. Every milestone follows the hexagonal layout.\n" {
		t.Errorf("plan description = %q", p.Description)
	}
	if len(p.Rules) != 2 || p.Rules[0] != "java-hexagonal" || p.Rules[1] != "constitution/constitution.md" {
		t.Errorf("plan rules = %v", p.Rules)
	}
	m := p.Milestones[0]
	if m.Description != "The domain service the adapter in milestone 2 calls.\n" {
		t.Errorf("milestone description = %q", m.Description)
	}
	if len(m.Rules) != 1 || m.Rules[0] != "docs/naming.md" {
		t.Errorf("milestone rules = %v", m.Rules)
	}
	want := "Write the failing test\n```java\nassertThat(new Greeter().greet(\"Ada\")).isEqualTo(\"Hello, Ada\");\n```"
	if m.Steps[0].Text != want {
		t.Errorf("multi-line step text = %q, want %q", m.Steps[0].Text, want)
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `go test ./internal/plan/ -run TestContextAndRulesLoad`
Expected: FAIL — compile error `p.Description undefined (type *Plan has no field or method Description)`.

- [ ] **Step 4: Add the fields**

In `internal/plan/types.go`, replace struct `Plan` with:

```go
// Plan is a complete plan: a schema-versioned list of milestones.
type Plan struct {
	// SchemaVersion is semantic-versioned without a "v" prefix (e.g. 0.1.0),
	// so consumers can detect the format revision (plan-schema: "The plan
	// declares a schema version").
	SchemaVersion string `yaml:"schemaVersion"`
	// Description is optional markdown context every milestone shares: what
	// the plan builds and how its milestones fit together.
	Description string `yaml:"description,omitempty"`
	// Rules are references — skill names or repository paths — that apply to
	// every milestone: what an implementer loads before starting and what a
	// verifier grades against. References, never copied content.
	Rules []string `yaml:"rules,omitempty"`
	// Milestones are in document order; index 0 is the first milestone.
	Milestones []Milestone `yaml:"milestones"`
}
```

In struct `Milestone`, insert after the `Goal` field:

```go
	// Description is optional markdown: why this milestone exists and how it
	// is meant to be built, beyond what deliverables and steps say.
	Description string `yaml:"description,omitempty"`
	// Rules are references (skill names or repository paths) this milestone
	// needs in addition to the plan-level rules.
	Rules []string `yaml:"rules,omitempty"`
```

- [ ] **Step 5: Add the fields to the schema**

In `schema/plan.schema.json`, add to the top-level `"properties"` object, after `"schemaVersion"`:

```json
    "description": {
      "description": "Markdown context shared by every milestone.",
      "type": "string",
      "minLength": 1
    },
    "rules": { "$ref": "#/$defs/rules" },
```

Add to `"$defs"`, after `"slug"`:

```json
    "rules": {
      "description": "References (skill names or repository paths) an implementer loads and a verifier grades against.",
      "type": "array",
      "items": { "type": "string", "minLength": 1 }
    },
```

Add to `"$defs" → "milestone" → "properties"`, after `"goal"`:

```json
        "description": { "type": "string", "minLength": 1 },
        "rules": { "$ref": "#/$defs/rules" },
```

Then copy the file over the embedded copy: `cp schema/plan.schema.json internal/schema/plan.schema.json`.

- [ ] **Step 6: Run the tests**

Run: `go test ./...`
Expected: PASS. `TestValidFixturesShapeValidate` now also shape-validates the new fixture.

- [ ] **Step 7: Commit**

```bash
git add testdata/valid/context-and-rules.yaml
git commit -m "Add description and rules to plans and milestones" -- testdata/valid/context-and-rules.yaml internal/plan/types.go internal/plan/load_test.go schema/plan.schema.json internal/schema/plan.schema.json
```

### Task A2: carry the new fields through `resolve`

**Files:**
- Modify: `internal/resolve/resolve.go` (structs `Projection`, `MilestoneProjection`; funcs `Resolve`, `milestoneProjection`)
- Test: `internal/resolve/resolve_test.go`

- [ ] **Step 1: Write the failing test**

Append to `internal/resolve/resolve_test.go`:

```go
// The projection carries plan- and milestone-level description and rules, so
// an executor reading only the resolve output has the full context.
func TestResolve_CarriesContextAndRules(t *testing.T) {
	p := loadFixture(t, "../../testdata/valid/context-and-rules.yaml")

	proj, err := Resolve(p)
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if proj.Description == "" || len(proj.Rules) != 2 {
		t.Fatalf("plan context lost: description=%q rules=%v", proj.Description, proj.Rules)
	}
	m := proj.Milestones[0]
	if m.Description == "" || len(m.Rules) != 1 || m.Rules[0] != "docs/naming.md" {
		t.Fatalf("milestone context lost: description=%q rules=%v", m.Description, m.Rules)
	}

	out, err := proj.Marshal()
	if err != nil {
		t.Fatalf("Marshal: %v", err)
	}
	for _, want := range []string{"description: |", "rules:\n    - java-hexagonal", "- docs/naming.md"} {
		if !strings.Contains(string(out), want) {
			t.Errorf("marshalled projection missing %q:\n%s", want, out)
		}
	}
}
```

Add `"strings"` to the file's import block.

- [ ] **Step 2: Run it to verify it fails**

Run: `go test ./internal/resolve/ -run TestResolve_CarriesContextAndRules`
Expected: FAIL — compile error `proj.Description undefined`.

- [ ] **Step 3: Implement**

In `internal/resolve/resolve.go`, replace struct `Projection` with:

```go
type Projection struct {
	SchemaVersion string   `yaml:"schemaVersion"`
	Description   string   `yaml:"description,omitempty"`
	Rules         []string `yaml:"rules,omitempty"`
	// Order is a valid topological order over the milestones (Kahn's
	// algorithm, ties broken by number — see internal/dag.TopoOrder),
	// deterministic and therefore byte-identical across runs.
	Order      []string              `yaml:"order"`
	Milestones []MilestoneProjection `yaml:"milestones"`
}
```

In struct `MilestoneProjection`, insert after `Goal`:

```go
	Description  string       `yaml:"description,omitempty"`
	Rules        []string     `yaml:"rules,omitempty"`
```

In func `Resolve`, extend the `proj := &Projection{...}` literal:

```go
	proj := &Projection{
		SchemaVersion: p.SchemaVersion,
		Description:   p.Description,
		Rules:         p.Rules,
		Order:         order,
		Milestones:    make([]MilestoneProjection, 0, len(p.Milestones)),
	}
```

In func `milestoneProjection`, extend the returned literal:

```go
	return MilestoneProjection{
		Number:       m.Number,
		Slug:         m.Slug,
		Goal:         m.Goal,
		Description:  m.Description,
		Rules:        m.Rules,
		Deliverables: deliverablesProjection(m.Deliverables),
		Contract:     contractProjection(m.Contract),
		Steps:        stepsProjection(m.Steps),
		DependsOn:    dependsOn,
	}
```

- [ ] **Step 4: Run the tests**

Run: `go test ./...`
Expected: PASS. `TestResolve_Golden` still passes unchanged: `branching.yaml` has neither field and both are `omitempty`.

- [ ] **Step 5: Commit**

```bash
git commit -m "Carry description and rules through resolve" -- internal/resolve/resolve.go internal/resolve/resolve_test.go
```

### Task A3: render the new fields and multi-line steps

**Files:**
- Modify: `internal/render/render.go` (funcs `Render`, `renderMilestone`, `renderSteps`)
- Test: `internal/render/render_test.go`

- [ ] **Step 1: Write the failing test**

Append to `internal/render/render_test.go` (add `"strings"` to its imports if it is not there):

```go
// The plan preamble, milestone description and rules render, and a
// multi-line step stays one list item: continuation lines are indented two
// spaces so a fenced code block inside a step does not break the list.
func TestRender_ContextRulesAndMultilineSteps(t *testing.T) {
	p := loadFixture(t, "../../testdata/valid/context-and-rules.yaml")

	out, err := Render(p)
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	for _, want := range []string{
		"## Plan\n\nAdds a greeting endpoint. Every milestone follows the hexagonal layout.\n\n**Rules** — java-hexagonal, constitution/constitution.md\n\n## Milestone 1: Greeter domain service",
		"**Goal** — Greeter domain service\n\n**Description**\n\nThe domain service the adapter in milestone 2 calls.\n\n**Rules** — docs/naming.md\n\n**Deliverables**",
		"- [ ] Write the failing test\n  ```java\n  assertThat(new Greeter().greet(\"Ada\")).isEqualTo(\"Hello, Ada\");\n  ```\n- [ ] Implement Greeter.greet\n",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("render output missing:\n%s\n--- got:\n%s", want, out)
		}
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `go test ./internal/render/ -run TestRender_ContextRulesAndMultilineSteps`
Expected: FAIL — `render output missing: ## Plan ...`.

- [ ] **Step 3: Implement**

In `internal/render/render.go`, replace func `Render` with:

```go
// Render produces the full markdown document for a plan: an optional "##
// Plan" preamble (the plan-level description and rules), then one "##
// Milestone N: <goal>" section per milestone, in document order, each
// carrying its Goal, optional Description and Rules, Deliverables, a fenced
// contract block, and its steps as `- [ ]`/`- [x]` checkboxes.
func Render(p *plan.Plan) (string, error) {
	var b strings.Builder
	if p.Description != "" || len(p.Rules) > 0 {
		b.WriteString("## Plan\n\n")
		renderContext(&b, p.Description, p.Rules)
	}
	for i, m := range p.Milestones {
		if i > 0 {
			b.WriteString("\n")
		}
		renderMilestone(&b, m)
	}
	return b.String(), nil
}

// renderContext writes a description paragraph and a one-line rules list,
// each only when present.
func renderContext(b *strings.Builder, description string, rules []string) {
	if d := strings.TrimSpace(description); d != "" {
		fmt.Fprintf(b, "%s\n\n", d)
	}
	if len(rules) > 0 {
		fmt.Fprintf(b, "**Rules** — %s\n\n", strings.Join(rules, ", "))
	}
}
```

In func `renderMilestone`, insert after the `**Goal**` line:

```go
	if strings.TrimSpace(m.Description) != "" {
		b.WriteString("**Description**\n\n")
	}
	renderContext(b, m.Description, m.Rules)
```

Replace func `renderSteps` with:

```go
// renderSteps writes one `- [ ]`/`- [x]` bullet per step. A multi-line step
// keeps its first line on the bullet and indents every following line by two
// spaces, so the whole step — code blocks included — stays one list item.
func renderSteps(b *strings.Builder, steps []plan.Step) {
	for _, s := range steps {
		mark := " "
		if s.Done {
			mark = "x"
		}
		lines := strings.Split(s.Text, "\n")
		fmt.Fprintf(b, "- [%s] %s\n", mark, lines[0])
		for _, line := range lines[1:] {
			if line == "" {
				b.WriteString("\n")
				continue
			}
			fmt.Fprintf(b, "  %s\n", line)
		}
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `go test ./...`
Expected: PASS, including both existing golden tests (neither golden fixture has a description, rules or multi-line step).

- [ ] **Step 5: Add a golden for the new fixture and check it by eye**

```bash
go run ./cmd/milestoned-plan-dag render testdata/valid/context-and-rules.yaml > testdata/golden/context-and-rules.render.md
cat testdata/golden/context-and-rules.render.md
```

Expected: the `## Plan` preamble, then the milestone with `**Description**`, `**Rules** — docs/naming.md`, and the step's code block indented under its bullet. Then append to `internal/render/render_test.go` (add `"os"` to its imports if it is not there):

```go
// TestRender_ContextGolden pins the exact markdown for the new fields.
func TestRender_ContextGolden(t *testing.T) {
	p := loadFixture(t, "../../testdata/valid/context-and-rules.yaml")
	got, err := Render(p)
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	want, err := os.ReadFile("../../testdata/golden/context-and-rules.render.md")
	if err != nil {
		t.Fatalf("read golden: %v", err)
	}
	if got != string(want) {
		t.Fatalf("render output does not match golden:\ngot:\n%s\nwant:\n%s", got, want)
	}
}
```

Run: `go test ./...` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add testdata/golden/context-and-rules.render.md
git commit -m "Render plan context, milestone rules and multi-line steps" -- internal/render/render.go internal/render/render_test.go testdata/golden/context-and-rules.render.md
```

### Task A4: raise the `plan-author` bar and document the new fields

**Files:**
- Modify: `skills/plan-author/SKILL.md` (full replacement below)
- Modify: `skills/plan-author/example.yaml` (add plan and milestone `description`/`rules`)
- Modify: `README.md` lines 25–28, `spec.md` (section "Optional structured detail slots"), `docs/releasing.md`, `.goreleaser.yaml`, `.github/workflows/release.yml`, `cmd/milestoned-plan-dag/main.go`, `cmd/milestoned-plan-dag/version.go`, `cmd/milestoned-plan-dag/version_test.go`
- Test: `cmd/milestoned-plan-dag/skilldrift_test.go` (existing — must stay green)

- [ ] **Step 1: Replace `skills/plan-author/SKILL.md`**

Keep the existing frontmatter `name: plan-author` and rewrite its `description:` line to:

```
description: Writes and validates milestoned-plan-dag plans — YAML DAGs of milestones an implementer can execute without exploring the codebase. Use when writing, editing or reviewing a plan.yaml.
```

Then replace the body. Keep these existing sections **verbatim**: "Scope: one plan = one git repository", "Top-level shape" (add the two new keys, below), "Milestone fields" table (add two rows, below), "Identity: `number` + `slug`", "The DAG and the implicit-chain fallback", "The contract: `check` / `criteria` / `paths`", "Authoring workflow", "See also". Replace the section "Self-contained is the bar" with the section below, and replace "Steps" with the "Steps" section below.

Top-level shape gains:

```yaml
schemaVersion: 0.2.0
description: |          # optional — context every milestone shares
  ...
rules:                  # optional — references every milestone loads
  - <skill name or repo path>
milestones:
  - number: 1
    ...
```

Milestone fields table gains, after `goal`:

```markdown
| `description` | no | Markdown: why this milestone exists and how to build it — the decisions, the shape of the code, the gotchas. Context for the implementer, not a restatement of the steps. |
| `rules` | no | References (skill names or repo paths) this milestone needs beyond the plan-level `rules`. |
```

New section replacing "Self-contained is the bar":

````markdown
## The bar: an implementer never explores

A milestone is handed to an implementer **alone** — this repository, the
plan-level `description` and `rules`, this one milestone, nothing else. It
must start work without opening another document, searching the codebase to
learn conventions, or asking a question. Write every milestone so a skilled
engineer with zero context for this codebase and questionable taste could
finish it.

- **Names, not references.** `deliverables` lists every file path, and the
  milestone's `description` or steps name each type or function the file
  introduces with its signature. "As described in the design doc" is a
  plan failure.
- **Code, not intentions.** A step that changes code shows the code. A step
  that runs a command shows the command and its expected output.
- **Criteria written out.** Each criterion states `given`/`when`/`then` in full
  and `name`s the test that proves it.
- **Cross-milestone types are named once, where they are born.** A type,
  function or file one milestone creates and another uses is named with its
  signature in the milestone that creates it. Later milestones use exactly
  that name.
- **Versions are pinned.** A dependency is added with an exact version in the
  earliest milestone that needs it. "Latest" is not a deliverable.
- **Every milestone ends green** on the standard `check`, and keeps every
  earlier milestone's tests green.

Test it: read one milestone with the rest of the plan covered. If you would
have to look something up or ask before starting, the milestone is not done.

## Rules: references the implementer loads

`rules` exist so the implementer loads the right guidance up front and the
verifier grades against a known list.

- List **references, never copies**: a skill name (`java-hexagonal`) or a repo
  path (`constitution/constitution.md`, `docs/naming.md`).
- Put what every milestone needs in plan-level `rules`; put only additions in a
  milestone's `rules`.
- Leave out what the agent runtime loads by itself — for Claude Code,
  `CLAUDE.md`/`AGENTS.md` and path-scoped `.claude/rules/*.md`.
- Keep the list minimal and relevant. Every reference costs the implementer
  context; a rule that does not bear on the milestone's files does not belong.

## Parallel milestones

Two milestones with no dependency path between them may run at the same time,
in separate worktrees, and are merged afterwards. Give them non-overlapping
`paths` (`validate` warns when they could touch the same file) and put shared
types, shared files and dependency pins in a milestone both depend on.
````

New "Steps" section:

````markdown
### Steps

`steps` is a non-empty list of checkbox items. Each step is one action of two
to five minutes: write the failing test, run it and see it fail, write the
code, run it and see it pass, commit. A step may span several lines — use a
YAML block scalar and start it with the checkbox:

```yaml
steps:
  - |
    [ ] Write the failing test
    ```go
    func TestHandlesEmptyInput(t *testing.T) {
        if got := Parse(""); got != nil {
            t.Fatalf("Parse(\"\") = %v, want nil", got)
        }
    }
    ```
  - "[ ] Run `go test ./internal/foo/ -run TestHandlesEmptyInput` — expect FAIL: undefined: Parse"
  - text: "[ ] Implement Parse"
    files:
      - internal/foo/parse.go
```

Every entry must begin with `[ ]` (not started) or `[x]` / `[X]` (done); the
loader strips the marker and records a boolean `done` flag plus the remaining
text.
````

In the "Authoring workflow" section, change the editor pin example's version from `v0.1.1` to `v0.2.0`.

- [ ] **Step 2: Update `skills/plan-author/example.yaml`**

Set `schemaVersion: 0.2.0`. After `schemaVersion`, add:

```yaml
description: |
  A worked example: a foundation milestone, then two independent branches
  that can run in parallel, then a join.
rules:
  - docs/conventions.md
```

To the first milestone add, after its `goal`:

```yaml
    description: |
      Lays down the shared package both branches import. Nothing in it is
      branch-specific.
```

Run: `go run ./cmd/milestoned-plan-dag validate skills/plan-author/example.yaml`
Expected: exit 0 (warnings allowed).

- [ ] **Step 3: Drop spec-lifecycle as the named consumer**

Make exactly these edits:

1. `README.md`: delete the blockquote at lines 25–28 (starts `> **Using this with [`spec-lifecycle`]`). In the "Authoring a plan" paragraph, change "the mandatory `check`/`criteria`/`paths` contract and its conscious escapes, checkbox steps)" to "the mandatory `check`/`criteria`/`paths` contract and its conscious escapes, plan- and milestone-level `description` and `rules`, multi-line checkbox steps)".
2. `spec.md`, section "Optional structured detail slots": append the sentence "A plan and each milestone may also carry a markdown `description` and a `rules` list of references (skill names or repository paths); a milestone's step may span several lines."
3. `docs/releasing.md`: in the paragraph at line 12–16 replace "the same tap that carries the [`constitution`](...) and [`lifecycle`](...) casks" with "the same tap that carries the [`constitution`](https://github.com/kentra-io/adr-sourced-constitution) cask". Delete the blockquote at lines 18–22 (`> **Installing this alongside `lifecycle`?**`). Replace the section "The `--version` output is a cross-repo contract" (lines ~88–95) with:
   ```markdown
   ## The `--version` output format

   `milestoned-plan-dag --version` prints `milestoned-plan-dag version <version>`.
   Tools that check the installed version parse that prefix;
   `cmd/milestoned-plan-dag/version_test.go` pins the format.
   ```
   In the prerequisites list, replace "already established by the constitution and spec-lifecycle casks" with "already established by the constitution cask"; at line ~142 replace "alongside `constitution` and `lifecycle`" with "alongside `constitution`".
4. `.goreleaser.yaml`: line 8 → `# that tap, alongside \`constitution\`).`; replace the comment block at lines 14–19 with:
   ```yaml
   # project_name drives the binary name, archive names, and cask name. The
   # repo name and the binary name are identical, and tools that call this
   # CLI look it up as exactly "milestoned-plan-dag" on PATH — keep it.
   ```
   line 47 → `    # ReadBuildInfo.`
5. `.github/workflows/release.yml`: delete the sentence "This mirrors the spec-lifecycle sibling primitive's release workflow." (line 12); line 59 → `          # org-level secret the constitution repo uses.`
6. `cmd/milestoned-plan-dag/main.go`: the `binName` comment's last line becomes `// and the name callers look up on PATH.`
7. `cmd/milestoned-plan-dag/version.go`: delete the line `// Mirrors cmd/lifecycle/version.go in the spec-lifecycle sibling primitive.` and the `//` line above it; replace the `versionLine` comment with:
   ```go
   // versionLine is what `--version` prints. Callers that check the installed
   // version parse the leading "milestoned-plan-dag version " prefix, so it is
   // a format contract: see version_test.go.
   ```
8. `cmd/milestoned-plan-dag/version_test.go`: replace the comment on `planDAGVersionRe` with `// planDAGVersionRe is the format callers parse; changing the output breaks them.`; rename `TestVersionLineMatchesSpecLifecycleContract` to `TestVersionLineFormat`; change its failure message to `"versionLine() = %q does not match %q"`; in the `TestVersionFlagsExitZero` comment replace "the defect that made the released binary unusable to spec-lifecycle's preflight" with "a defect that made a released binary unusable to version checks".

Run: `grep -rn "spec-lifecycle\|lifecycle" --exclude-dir=.git --exclude-dir=dist --exclude=implementation-plan.md .`
Expected: no output.

- [ ] **Step 4: Run the tests**

Run: `go test ./...`
Expected: PASS (including `TestSkillInvocationsNameRealCommands`).

- [ ] **Step 5: Commit**

```bash
git commit -m "Raise the plan-author bar; document description, rules and multi-line steps" -- skills/plan-author README.md spec.md docs/releasing.md .goreleaser.yaml .github/workflows/release.yml cmd/milestoned-plan-dag
```

### Task A5: release v0.2.0

**Files:**
- Modify: every file carrying the pinned schema URL (`TestPinnedSchemaURLsAgree` lists them when it fails)
- Modify: `.claude-plugin/plugin.json` (`version`, `description`)

- [ ] **Step 1: Bump the pin**

```bash
grep -rl "milestoned-plan-dag/v0.1.1/schema" --exclude-dir=.git --exclude-dir=dist . | xargs sed -i '' 's#milestoned-plan-dag/v0.1.1/schema#milestoned-plan-dag/v0.2.0/schema#g'
cp schema/plan.schema.json internal/schema/plan.schema.json
go test ./...
```

Expected: PASS (`TestPinnedSchemaURLsAgree`, `TestEmbeddedMatchesPublished`).

- [ ] **Step 2: Bump the plugin**

In `.claude-plugin/plugin.json` set `"version": "0.2.0"` and replace the description's "checkbox steps —" with "plan- and milestone-level description and rules, multi-line checkbox steps —".

- [ ] **Step 3: Commit, push the branch, open the PR**

```bash
git commit -m "Release v0.2.0: pin the schema URL and bump the plugin" -- $(git diff --name-only)
git push -u origin plan-context-and-rules
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr create --title "Plan context and rules (v0.2.0)" --body "Adds plan- and milestone-level \`description\` and \`rules\`, renders multi-line steps as one list item, raises the plan-author bar to the superpowers plan, and drops spec-lifecycle as the named consumer. Design: kentra-io/harness \`kentra-rpir.md\`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

- [ ] **Step 4: 🔒 Merge and tag** — ask the user, then:

```bash
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr merge --squash --delete-branch
git switch main && git pull --ff-only
git tag v0.2.0 && git push origin v0.2.0
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh run watch $(GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')
brew update && brew upgrade kentra-io/tap/milestoned-plan-dag && milestoned-plan-dag --version
```

Expected: the release workflow succeeds; `milestoned-plan-dag version 0.2.0`.

---

# Part B — adr-sourced-constitution: `plan-gate` becomes model-invocable

Repository: `/Users/jony/code/kentra/harness/adr-sourced-constitution`. Standard check: `go test ./...`. Start: `git switch main && git pull --ff-only && git switch -c plan-gate-in-session`.

What changes and why: the constitution has to work without kentra-rpir (for example, alongside superpowers), so `plan-gate` stays here. Two changes: an agent may invoke it without the user typing `/plan-gate`, and its result is a verdict in the session. `deviation.json` is written only when someone asks for one.

Facts you need, all checked on `main` at `1c1d409`:

- `skills/plan-gate/SKILL.md` is the source. `.claude/skills/plan-gate/SKILL.md`, `.agents/skills/plan-gate/SKILL.md` and `.cursor/skills/plan-gate/SKILL.md` are generated from it by `constitution regen`, and must stay byte-identical to it. No test catches drift, so regenerating is a manual step.
- `cmd/constitution/testdata/script/init_happy.txtar:47` asserts `grep 'disable-model-invocation: true' .claude/skills/plan-gate/SKILL.md`.
- `init_skills_real_bodies.txtar` greps `Check a plan against this repo` in the plan-gate body. Keep that sentence.
- `skill_doc_test.go` requires every SKILL.md to contain `` `constitution` 0.3.1 or newer ``, `constitution --version` and `**stop**`. Keep the `## Requires` block as it is.
- No ADR is needed. `constitution/constitution.md` reads "No standing rules yet". ADR-0009, which made `deviation.json` mandatory, has been deprecated by ADR-0012.
- The release checklist is in `docs/releasing.md` under "Release checklist: skills and the plugin catalog". No new CLI behaviour is needed, so `SkillsMinCLIVersion` stays `0.3.1`.

### Task B1: assert the new frontmatter

**Files:**
- Modify: `cmd/constitution/testdata/script/init_happy.txtar:47`

- [ ] **Step 1: Flip the assertion**

Replace line 47:

```
grep 'disable-model-invocation: true' .claude/skills/plan-gate/SKILL.md
```

with:

```
! grep 'disable-model-invocation' .claude/skills/plan-gate/SKILL.md
grep 'description: Checks a plan against' .claude/skills/plan-gate/SKILL.md
```

- [ ] **Step 2: Run it to verify it fails**

Run: `go test ./cmd/constitution/ -run TestScript/init_happy`
Expected: FAIL. The `! grep 'disable-model-invocation'` line matches the current frontmatter.

### Task B2: rewrite `plan-gate`

**Files:**
- Modify: `skills/plan-gate/SKILL.md` (full replacement below)
- Regenerate: `.claude/skills/plan-gate/SKILL.md`, `.agents/skills/plan-gate/SKILL.md`, `.cursor/skills/plan-gate/SKILL.md`

- [ ] **Step 1: Replace `skills/plan-gate/SKILL.md`**

````markdown
---
name: plan-gate
description: Checks a plan against the project's constitution rule by rule and reports, in the session, every ADR rule the plan would break. Use before a plan is executed, when a plan or design changes, or when asked whether something conforms to the constitution. Writes deviation.json only when asked.
---

# plan-gate

## Requires

This skill is written for `constitution` 0.3.1 or newer. Before doing
anything else, run `constitution --version`. If the binary is older,
**stop** and report the mismatch — an older CLI does not have the
flags this skill uses, and the workarounds an older skill taught (such
as hand-editing `constitution.yml`) are forbidden here. Do not
improvise around the gap.

Check a plan against this repo's constitution before it is executed. Read
every standing rule, decide for each one whether the plan conforms, and report
the verdict in the session, citing each broken rule by `ADR-NNNN`. You change
nothing: not the plan, not the log, not `constitution.md`.

`constitution.md` is a **curated projection**: it holds only the standing
rules — the `## Rules` of active ADRs. Point-in-time records live in
`constitution/adr/` and never appear in it, and a plan cannot deviate from
them. The rules in `constitution.md` are the complete set a plan is checked
against.

If the `constitution` binary is not on PATH, install the prebuilt release —
`curl -sSfL https://raw.githubusercontent.com/kentra-io/adr-sourced-constitution/main/install.sh | sh`
— do **not** build from source or install a Go toolchain.

## Inputs

- The plan: the path you were given, or the plan under discussion. Any format —
  markdown, YAML, a design document.
- The constitution: `constitution/constitution.md`.

## Procedure

1. **Load the rules.** `cat constitution/constitution.md`. Each standing rule is
   a `#### <slug>` entry under a `### <category>` heading, followed by the line
   naming its ADR. If it reads "No standing rules yet", every plan conforms:
   say so and stop.
2. **Decide rule by rule.** For every rule, decide whether the plan conforms or
   conflicts. Check all of them: a plan can break a rule it never mentions. For
   each conflict note the plan location (file and line span when you can), the
   `ADR-NNNN`, a one-line summary, a severity (`CRITICAL | HIGH | MEDIUM |
   LOW`), and the remedy: `conform` (change the plan) or `amend` (change the
   rule through a new ADR).
3. **Run guard.** `constitution guard --format json`. Each violation it reports
   is an out-of-band edit to the ADR log. Add it as a conflict citing that ADR,
   normally `CRITICAL` with remedy `conform`.
4. **Report the verdict in the session.** One line first — **conforms**, or
   **conflicts with N rules** — then one row per conflict:

   | ADR | Severity | Rule | Remedy | Summary |
   |---|---|---|---|---|
   | ADR-0007 | HIGH | guard-is-advisory | amend | The plan makes guard a required merge gate; the rule keeps it advisory. |

   Cite only ADRs whose rules appear in `constitution.md`.
5. **Write `deviation.json` only when asked** — by the user, or by the skill or
   tool that invoked you, which also names the path (default
   `./deviation.json`). Its schema is `docs/deviation.schema.json`:

   ```json
   {
     "generatedAt": "2026-07-03T14:02:00Z",
     "constitutionHash": "",
     "plan": "tasks/plan.md",
     "deviations": [
       {"id": "D-001", "adrId": "ADR-0007", "severity": "HIGH",
        "rule": "guard-is-advisory",
        "location": {"file": "tasks/plan.md", "lines": "12-40"},
        "summary": "The plan makes guard a required merge gate; the rule keeps it advisory.",
        "recommendation": "amend",
        "recommendationDetail": "Propose an ADR that supersedes the advisory decision."}
     ],
     "summary": {"critical": 0, "high": 1, "medium": 0, "low": 0}
   }
   ```

   Then validate it — the CLI owns the schema:
   `constitution deviation validate <path>`. Exit 0 is valid; when stderr shows
   a `constitutionHash mismatch` advisory, copy the `sha256:…` value it prints
   into `constitutionHash` and validate once more. Exit 1 lists precise errors:
   fix the report and re-run. Exit 2 means it could not run: report that
   instead of the file.

You are done when the session shows the verdict line and one row per conflict
— and, if one was asked for, a `deviation.json` that validates.

## Never

- Never edit the plan, the ADR log, or `constitution.md`.
- Never cite an ADR that does not appear in `constitution.md`.
- Never present a `deviation.json` that `constitution deviation validate` has
  not accepted.
````

- [ ] **Step 2: Regenerate the three copies**

```bash
go build -o "$SCRATCH/constitution" ./cmd/constitution
"$SCRATCH/constitution" regen
diff -q skills/plan-gate/SKILL.md .claude/skills/plan-gate/SKILL.md && diff -q skills/plan-gate/SKILL.md .agents/skills/plan-gate/SKILL.md && diff -q skills/plan-gate/SKILL.md .cursor/skills/plan-gate/SKILL.md && echo in-sync
git status --short
```

Expected: `in-sync`. `git status` lists only `skills/plan-gate/SKILL.md`, its three copies, `constitution/.state`, and the txtar from Task B1. If `regen` reports drift and refuses to overwrite, copy the file by hand (`cp skills/plan-gate/SKILL.md .claude/skills/plan-gate/SKILL.md`, and the same for `.agents` and `.cursor`), then run `regen` once more so `constitution/.state` records the new hashes.

- [ ] **Step 3: Run the tests**

Run: `go test ./...`
Expected: PASS, including `TestScript/init_happy` and the `skill_doc_test.go` checks.

- [ ] **Step 4: Update the docs that describe plan-gate**

Run `grep -rn "plan-gate" README.md docs/ AGENTS.md`. Edit each sentence that says `plan-gate` must be invoked with `/plan-gate` or always emits `deviation.json` so it says instead: `plan-gate` reports its verdict in the session, and writes `deviation.json` when asked. Change nothing else in those files.

- [ ] **Step 5: Commit**

```bash
git commit -m "plan-gate: model-invocable, verdict in session, deviation.json on request" -- skills/plan-gate .claude/skills/plan-gate .agents/skills/plan-gate .cursor/skills/plan-gate constitution/.state cmd/constitution/testdata/script/init_happy.txtar README.md docs AGENTS.md
```

### Task B3: release v0.3.2

**Files:**
- Modify: `.claude-plugin/plugin.json` (`version`, `description`)
- Modify: `skill_doc_test.go:240` (`lastSkillReleaseVersion`)

- [ ] **Step 1: Bump**

In `.claude-plugin/plugin.json` set `"version": "0.3.2"`. In `skill_doc_test.go` set `lastSkillReleaseVersion = "0.3.1"`. In the plugin description, replace "The ongoing-governance skills (constitution-gov, adr-draft, plan-gate)" with "The ongoing-governance skills (constitution-gov, adr-draft, and the model-invocable plan-gate)".

Run: `go test ./...` — Expected: PASS (`TestPluginVersionAheadOfLastRelease`: 0.3.2 > 0.3.1).

- [ ] **Step 2: Commit, push, open the PR**

```bash
git commit -m "Release v0.3.2: plugin version for the plan-gate change" -- .claude-plugin/plugin.json skill_doc_test.go
git push -u origin plan-gate-in-session
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr create --title "plan-gate: model-invocable, verdict in session" --body "plan-gate drops \`disable-model-invocation\`, reports its verdict in the session, and writes \`deviation.json\` only when asked. No ADR: the projection has no standing rules, and ADR-0009 is deprecated. Design: kentra-io/harness \`kentra-rpir.md\`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

- [ ] **Step 3: 🔒 Merge and tag** — ask the user, then:

```bash
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr merge --squash --delete-branch
git switch main && git pull --ff-only
git tag v0.3.2 && git push origin v0.3.2
brew update && brew upgrade kentra-io/tap/constitution && constitution --version
```

Expected: `constitution version 0.3.2`.

---

# Part C — the kentra-rpir plugin

Repository: `/Users/jony/code/kentra/harness/spec-lifecycle` (renamed in Part E; until then, the directory and the GitHub remote keep the old name). Start: `git switch main && git pull --ff-only && git switch -c kentra-rpir`.

The design for every file in this Part is `harness/kentra-rpir.md`. The sections below give the full text of each file. Where a file's wording is adjusted under `writing-for-agents`, keep its substance: the steps, the names and the checklists.

### Task C1: verify the platform assumptions (spike)

The skills depend on five Claude Code behaviours. Check them before writing anything that relies on them. The spike runs in a throwaway local plugin.

**Files:**
- Create: `$SCRATCH/spike-plugin/.claude-plugin/plugin.json`, `$SCRATCH/spike-plugin/skills/probe/SKILL.md`, `$SCRATCH/spike-plugin/agents/probe-agent.md`, `$SCRATCH/spike-plugin/workflows/probe-wf.js`

- [ ] **Step 1: Write the spike plugin**

```bash
mkdir -p "$SCRATCH/spike-plugin/.claude-plugin" "$SCRATCH/spike-plugin/skills/probe" "$SCRATCH/spike-plugin/agents" "$SCRATCH/spike-plugin/workflows"
cat > "$SCRATCH/spike-plugin/.claude-plugin/plugin.json" <<'JSON'
{ "name": "rpir-spike", "version": "0.0.1", "description": "spike" }
JSON
cat > "$SCRATCH/spike-plugin/skills/probe/SKILL.md" <<'MD'
---
name: probe
description: Spike probe. Use only when asked to run the rpir spike probe.
---
Print these two values exactly as they appear here, then stop:
PLUGIN_ROOT=${CLAUDE_PLUGIN_ROOT}
SKILL_DIR=${CLAUDE_SKILL_DIR}
SESSION_ID=${CLAUDE_SESSION_ID}
MD
cat > "$SCRATCH/spike-plugin/agents/probe-agent.md" <<'MD'
---
name: probe-agent
description: Spike agent that reports its working directory.
model: haiku
---
Run `pwd` and reply with its output only.
MD
cat > "$SCRATCH/spike-plugin/workflows/probe-wf.js" <<'JS'
export const meta = { name: 'probe-wf', description: 'spike', phases: [{ title: 'Probe' }] }
const r = await agent('Your only job: run `pwd` and return its output.', { label: 'pwd', phase: 'Probe', model: 'haiku' })
return { pwd: r, args }
JS
```

- [ ] **Step 2: Run the probes**

Start `claude --plugin-dir "$SCRATCH/spike-plugin"` in `/Users/jony/code/kentra/harness/milestoned-plan-dag` and, in that session:

1. Ask: "run the rpir spike probe". Record which of `PLUGIN_ROOT`, `SKILL_DIR` and `SESSION_ID` came through substituted.
2. Ask: "Use the Agent tool with subagent_type `rpir-spike:probe-agent` and cwd `/Users/jony/code/kentra/harness/adr-sourced-constitution`." Record the printed directory, and whether the Agent tool accepted `cwd` at all.
3. Ask: "Run the workflow `rpir-spike:probe-wf` with args `{\"x\":1}`." Record whether the plugin workflow resolves by that name and gets its `args`.
4. Ask: "Write a tiny workflow script that calls `await workflow('rpir-spike:probe-wf', {x: 2})` and returns the result, and run it." Record whether nested workflows resolve plugin workflows.

- [ ] **Step 3: Record the results, and choose the fallbacks the results call for**

Write the four results to `$SCRATCH/spike-results.md`. Then apply each row that matches:

| Result | Consequence for this Part |
|---|---|
| `${CLAUDE_PLUGIN_ROOT}` not substituted in skills | Skills locate plugin files from `${CLAUDE_SKILL_DIR}/../..`. Replace `${CLAUDE_PLUGIN_ROOT}` with that in every SKILL.md in Tasks C8–C11. Probe `${CLAUDE_SKILL_DIR}` the same way first. |
| `${CLAUDE_SKILL_DIR}` not substituted | The implement skill (Task C10) names its prompt files as `${CLAUDE_PLUGIN_ROOT}/skills/implement/<file>`. |
| `${CLAUDE_SESSION_ID}` not substituted | Skills ask the hook for the id instead: add a `SessionStart` hook to `hooks/hooks.json` that writes `session_id` to `.kentra-rpir/current-session`, and skills read that file. |
| Agent tool has no `cwd` | The implement skill (Task C10) gives each subagent the worktree's absolute path. The implementer and verifier prompts then open with: "Work only in `<worktree>`. Start every shell command with `cd <worktree> &&`, and give every file tool an absolute path under it." **Tell the user**: this is weaker than `cwd`. |
| Plugin workflows not resolvable by `plugin:name` | Skills run workflows by script path: `Workflow({ scriptPath: "${CLAUDE_PLUGIN_ROOT}/workflows/<name>.js", args })`. Nested calls receive the path in `args`. |

You are done with this task when every row has a recorded result and its fallback is noted. If two or more fallbacks apply, stop and show the user the results before continuing.

### Task C2: delete the old primitive

**Files:**
- Delete: `cmd/`, `internal/`, `openspec/`, `evals/`, `skills/` (all seven `lifecycle-*`), `.claude/`, `.agents/`, `.cursor/`, `.agent-claude/`, `embed.go`, `go.mod`, `go.sum`, `lifecycle.yml`, `spec-lifecycle.md`, `implementation-plan.md`, `.goreleaser.yaml`, `.golangci.yml`, `.github/workflows/release.yml`, `docs/releasing.md`
- Keep: `LICENSE`, `.gitattributes`, `.claudebox/config.yaml`, `.github/workflows/ci.yml` (rewritten in Task C12), `.claude-plugin/plugin.json`, `README.md`, `AGENTS.md`, `CLAUDE.md`, `.gitignore` (all rewritten below)

- [ ] **Step 1: Delete**

```bash
git rm -r -q cmd internal openspec evals skills .claude .agents .cursor .agent-claude embed.go go.mod go.sum lifecycle.yml spec-lifecycle.md implementation-plan.md .goreleaser.yaml .golangci.yml .github/workflows/release.yml docs/releasing.md
rm -rf dist
git ls-files
```

Expected: `git ls-files` lists only `.claude-plugin/plugin.json`, `.claudebox/config.yaml`, `.gitattributes`, `.github/workflows/ci.yml`, `.gitignore`, `AGENTS.md`, `CLAUDE.md`, `LICENSE`, `README.md`.

- [ ] **Step 2: Rewrite `.gitignore`**

```gitignore
# Editors / OS
.DS_Store
*.swp

# kentra-rpir's own working state when the plugin is dogfooded on this repo
.kentra-rpir/
.worktrees/
```

- [ ] **Step 3: Rewrite `AGENTS.md` and `CLAUDE.md`**

`AGENTS.md`:

```markdown
# kentra-rpir — agent instructions

This repository is a Claude Code plugin: skills, agents, workflows, hooks and
templates. There is no build. `docs/process.md` is the process it implements;
read it before changing a skill.

- Every edit to a skill, agent, workflow, hook or template bumps `version` in
  `.claude-plugin/plugin.json` — `claude plugin update` compares only that field.
- Run `sh tests/run.sh` before committing. It runs the hook tests and the
  workflow checks.
- Workflow scripts are plain JavaScript run by Claude Code's Workflow tool: top-level
  `await`, `return` at the end, a pure-literal `export const meta`.
- Write skills and prompts following the `writing-for-agents` skill.
```

`CLAUDE.md`:

```markdown
@AGENTS.md
```

- [ ] **Step 4: Commit**

```bash
git add .gitignore AGENTS.md CLAUDE.md
git commit -m "Remove the spec-lifecycle CLI, OpenSpec engine and gated skills" -- .
```

### Task C3: plugin manifest and templates

**Files:**
- Modify: `.claude-plugin/plugin.json`
- Create: `templates/kentra-rpir.yaml`, `templates/spec.md`, `templates/impl-report.md`, `templates/spec-retro.md`

- [ ] **Step 1: Write `.claude-plugin/plugin.json`**

```json
{
  "name": "kentra-rpir",
  "description": "Runs one change — feature, bug or chore — through spec → plan → implement → review. Interactive spec with background premise research, a milestoned-plan-dag plan an implementer can follow without exploring, parallel milestone implementation in worktrees with a fresh verifier per milestone, and a retro read from the session transcripts.",
  "version": "0.3.0",
  "author": { "name": "Kentra" },
  "homepage": "https://github.com/kentra-io/kentra-rpir",
  "repository": "https://github.com/kentra-io/kentra-rpir",
  "license": "MIT",
  "keywords": ["rpi", "rpir", "spec", "plan", "implement", "review", "retro", "worktrees", "subagents"]
}
```

- [ ] **Step 2: Write `templates/kentra-rpir.yaml`**

```yaml
# kentra-rpir project configuration. Missing keys take these defaults.
changesDir: docs/changes            # where change folders live
tracker: none                       # none | github | jira
constitution: false                 # true: read constitution/constitution.md in every stage
spec:
  questionBatch: 5                  # max questions asked in one message
roles:                              # agent: a subagent type; model: overrides the agent's own
  reviewer:    { agent: kentra-rpir:reviewer,    model: opus }    # spec and plan self-review
  implementer: { agent: kentra-rpir:implementer, model: sonnet }
  verifier:    { agent: kentra-rpir:verifier,    model: opus }
  impl-orchestrator: { model: opus }                            # the main session running implement
  retro:       { agent: kentra-rpir:retro,       model: opus }    # synthesis, reconciliation, remediation
  prepass:     { agent: kentra-rpir:retro,       model: haiku }   # transcript signals and skeleton timeline
  reader:      { agent: kentra-rpir:retro,       model: sonnet }  # per-milestone blind readers
research:
  workflow: kentra-rpir:research    # or any workflow with the same args contract
  collector: haiku
  reasoner: opus
implement:
  maxAttempts: 3                    # per blocker: 2 re-dispatches with guidance, then the user
  maxVerifyRounds: 3                # implement → verify → fix rounds per milestone
  worktreesDir: .worktrees          # relative to the project root; gitignored
  branchPrefix: rpir/
  openPullRequest: true             # only when the remote is GitHub and gh is authenticated
```

- [ ] **Step 3: Write `templates/spec.md`**

```markdown
---
title: <short name>
type: feature            # feature | bug | chore
status: draft            # draft | agreed | planned | implementing | in-review | done
issue:                   # tracker link, when a tracker is configured
sessions: sessions/      # the transcripts of every session that worked on this change
---

## Problem

<What is wrong or missing, for whom, and how we know. For a bug: the observed
behaviour, the expected behaviour, and how to reproduce it.>

## Goals

1. <An outcome the change delivers.>

## Non-goals

- <What this change deliberately does not do, including what the session ruled out.>

## Premises

| # | Premise | Status | Source |
|---|---|---|---|
| P1 | <Something the change assumes is true.> | verified · refuted · unverified | <URL, file:line at a pinned version, or spike path> |

## Requirements

1. <A plain, testable statement of behaviour.>

## Design

<The decisions and their rationale. For each decision with alternatives: the
options considered, the one chosen, and why.>

## Open questions

<Must be empty at status: agreed.>

<!-- writer-gaps: technical gaps self-review found and fixed, one per line -->
```

- [ ] **Step 4: Write `templates/impl-report.md`**

```markdown
# Implementation report — <title>

- **Change:** <changesDir>/<folder>
- **Branch:** rpir/<slug> · **Pull request:** <url or "none">
- **Orchestrator session:** <session id> · **Models:** implementer <model>, verifier <model>, orchestrator <model>
- **Plugin:** kentra-rpir <version> · **Claude Code:** <version>

## Milestones

| # | Slug | Outcome | Dispatches | Verify rounds | Merge commit |
|---|---|---|---|---|---|
| 1 | <slug> | merged · blocked · dropped | <n> | <n> | <sha> |

### M<n> <slug>

- **Verifier FAILs:** <round: findings, one line each — or "none">
- **Concerns raised (DONE_WITH_CONCERNS):** <and how each was settled — or "none">
- **Observed, not fixed:** <what agents noticed outside the milestone's scope — or "none">

## Plan adjustments

| Milestone | Change | Reason | Commit |
|---|---|---|---|

## Decisions the user made

| Question | Decision |
|---|---|

## Merge conflicts resolved

| Milestones | Files | Resolution |
|---|---|---|
```

- [ ] **Step 5: Write `templates/spec-retro.md`**

```markdown
# Retro — <title>

- **Change:** <folder> · **Commits:** <base>..<head>
- **Models:** <role: model, for every role used>
- **Versions:** kentra-rpir <version> · Claude Code <version> · skills <name@version, …>
- **Sessions read:** <session id: stage, for every session>

## Outcome

<Two or three sentences, from the verifier results, not from impl-report.md.>

## Self-report reconciliation

| Claim in impl-report.md | Verdict | Evidence |
|---|---|---|
| <claim> | confirmed · contradicted · unsupported | <transcript ref or commit> |

## Timeline

### M<n> <slug>

| Step | Ref | Event |
|---|---|---|
| <n> | <session>:<line> | <what happened> **(decisive)** |

## Findings

| Id | Class | Decisive step | How it made sense at the time | Severity |
|---|---|---|---|---|
| F1 | SPEC · SKILL · PROCESS · ENV · MODEL | <ref> | <what the agent had in front of it> | high · medium · low |

## What went well

- <What to keep, so that a fix does not undo it.>

## Actions

| Finding | Action | Target | Detail | Decision |
|---|---|---|---|---|
| F1 | diff · eval case · automated check · no action | <file, or kentra-io/kentra-rpir> | <before/after, eval prompt + fixture + pass condition, check, or reasoning> | <filled at review: applied <commit> · filed <issue url> · deferred · rejected — reason> |

## Recurring

<Fault classes and findings also seen in earlier retros, with links.>

## Open questions

- <What the evidence could not settle.>
```

- [ ] **Step 6: Commit**

```bash
git add .claude-plugin/plugin.json templates
git commit -m "kentra-rpir plugin manifest and templates" -- .claude-plugin/plugin.json templates
```

### Task C4: session capture hook (TDD)

**Files:**
- Create: `tests/capture-session.test.sh`, `tests/run.sh`
- Create: `hooks/capture-session.sh`, `hooks/hooks.json`

Contract of `hooks/capture-session.sh`, which runs under POSIX `sh` with no `jq`:

- **As a hook**, it reads the hook's JSON from stdin. It takes `session_id`, `transcript_path` and `cwd` out of it with `sed`.
- **Called directly**, as `capture-session.sh --session <id> [--project <dir>]`:
  - `--project` defaults to `git rev-parse --show-toplevel`.
  - It finds the transcript at `${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects/*/<id>.jsonl`.
- **The project** is `cwd`'s git top-level, or `cwd` itself when that fails.
- **`.kentra-rpir/sessions.map`** has one line per registration: `<session-id> <change-dir relative to project> <stage>`. The last line for a session wins. A session that is not in the map is ignored: exit 0, no output.
- **What it copies:** the transcript to `<change>/sessions/<id>.jsonl`, and the transcript's sibling directory `<id>/subagents/` to `<change>/sessions/<id>/subagents/`. Each file passes through the redaction `sed`.
- **`<change>/sessions/index`** gets `<session-id> <stage>`, once per session-and-stage pair.
- **Exit code** is always 0; errors go to stderr. A hook must never block compaction or exit.

- [ ] **Step 1: Write the failing test**

`tests/capture-session.test.sh`:

```sh
#!/bin/sh
# Tests for hooks/capture-session.sh. POSIX sh; run from the repo root.
set -u
here=$(cd "$(dirname "$0")/.." && pwd)
script="$here/hooks/capture-session.sh"
fails=0
check() { # check <description> <command...>
  desc=$1; shift
  if "$@"; then echo "ok   - $desc"; else echo "FAIL - $desc"; fails=$((fails + 1)); fi
}

root=$(mktemp -d "${TMPDIR:-/tmp}/rpir-capture.XXXXXX")
trap 'rm -rf "$root"' EXIT
export CLAUDE_CONFIG_DIR="$root/claude"
proj="$root/project"
sid=11111111-2222-3333-4444-555555555555
tdir="$CLAUDE_CONFIG_DIR/projects/-project"
mkdir -p "$tdir/$sid/subagents" "$proj/.kentra-rpir" "$proj/docs/changes/2026-10-11-demo"
git -C "$proj" init -q

cat > "$tdir/$sid.jsonl" <<'JSONL'
{"type":"user","message":"token ghp_abcdefghijklmnopqrstuvwxyz0123456789 here"}
{"type":"assistant","message":"keys sk-ant-api03-abcdefghijklmnopqrstuv AKIAABCDEFGHIJKLMNOP xoxb-1234567890-abcdef github_pat_11ABCDEFG0123456789_abcdefghijk"}
{"type":"tool","content":"-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAA=\n-----END OPENSSH PRIVATE KEY-----"}
{"type":"user","message":"plain text stays"}
JSONL
echo '{"type":"user","message":"sub ghp_zyxwvutsrqponmlkjihgfedcba9876543210"}' > "$tdir/$sid/subagents/agent-a1.jsonl"

change=docs/changes/2026-10-11-demo
out="$proj/$change/sessions"

# 1. Unmapped session: nothing happens.
printf '{"session_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"PreCompact"}' \
  "$sid" "$tdir/$sid.jsonl" "$proj" | sh "$script"
check "unmapped session exits 0" test $? -eq 0
check "unmapped session copies nothing" test ! -e "$out"

# 2. Mapped session via hook payload.
echo "$sid $change spec" > "$proj/.kentra-rpir/sessions.map"
printf '{"session_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"PreCompact"}' \
  "$sid" "$tdir/$sid.jsonl" "$proj" | sh "$script"
check "transcript copied" test -f "$out/$sid.jsonl"
check "subagent transcript copied" test -f "$out/$sid/subagents/agent-a1.jsonl"
check "plain text kept" grep -q 'plain text stays' "$out/$sid.jsonl"
for secret in ghp_abcdef sk-ant-api03 AKIAABCDEF xoxb-1234 github_pat_11 'b3BlbnNzaC1r'; do
  check "redacted $secret" sh -c "! grep -q '$secret' '$out/$sid.jsonl'"
done
check "subagent redacted" sh -c "! grep -q ghp_zyx '$out/$sid/subagents/agent-a1.jsonl'"
check "redaction marker present" grep -q 'REDACTED' "$out/$sid.jsonl"
check "index records session and stage" grep -qx "$sid spec" "$out/index"

# 3. Direct call with a new stage: index gains one line, no duplicates on repeat.
echo "$sid $change plan" >> "$proj/.kentra-rpir/sessions.map"
sh "$script" --session "$sid" --project "$proj"
sh "$script" --session "$sid" --project "$proj"
check "direct call adds the new stage" grep -qx "$sid plan" "$out/index"
check "index has no duplicates" test "$(wc -l < "$out/index" | tr -d ' ')" -eq 2

# 4. Later copy replaces the earlier one.
echo '{"type":"user","message":"later line"}' >> "$tdir/$sid.jsonl"
sh "$script" --session "$sid" --project "$proj"
check "later copy includes new lines" grep -q 'later line' "$out/$sid.jsonl"

[ "$fails" -eq 0 ] && echo "all capture-session tests passed"
exit "$fails"
```

`tests/run.sh`:

```sh
#!/bin/sh
# Runs every test in this repository. POSIX sh; run from the repo root.
set -e
cd "$(dirname "$0")/.."
sh tests/capture-session.test.sh
node tests/check-workflows.mjs
```

- [ ] **Step 2: Run it to verify it fails**

Run: `sh tests/capture-session.test.sh`
Expected: FAIL lines starting at "transcript copied", because `hooks/capture-session.sh` does not exist yet.

- [ ] **Step 3: Write `hooks/capture-session.sh`**

```sh
#!/bin/sh
# Copies a Claude Code session transcript (and its subagent transcripts) into
# the kentra-rpir change folder the session is registered to, redacting common
# credentials. Runs as a PreCompact/SessionEnd hook (JSON on stdin) or directly:
#   capture-session.sh --session <id> [--project <dir>]
# Always exits 0: a capture failure must never block compaction or exit.

sid=""; transcript=""; project=""
while [ $# -gt 0 ]; do
  case "$1" in
    --session) sid=$2; shift 2 ;;
    --project) project=$2; shift 2 ;;
    *) shift ;;
  esac
done

field() { # field <name> <json>: extract a top-level string field
  printf '%s' "$2" | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

if [ -z "$sid" ]; then
  payload=$(cat)
  sid=$(field session_id "$payload")
  transcript=$(field transcript_path "$payload")
  cwd=$(field cwd "$payload")
  [ -n "$cwd" ] && project=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null || printf '%s' "$cwd")
fi
[ -n "$sid" ] || exit 0
[ -n "$project" ] || project=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

map="$project/.kentra-rpir/sessions.map"
[ -f "$map" ] || exit 0
line=$(grep "^$sid " "$map" | tail -n 1)
[ -n "$line" ] || exit 0
change=$(printf '%s' "$line" | cut -d' ' -f2)
stage=$(printf '%s' "$line" | cut -d' ' -f3)

if [ -z "$transcript" ] || [ ! -f "$transcript" ]; then
  transcript=$(find "${CLAUDE_CONFIG_DIR:-$HOME/.claude}/projects" -maxdepth 2 -name "$sid.jsonl" 2>/dev/null | head -n 1)
fi
[ -n "$transcript" ] && [ -f "$transcript" ] || { echo "kentra-rpir: transcript for $sid not found" >&2; exit 0; }

out="$project/$change/sessions"
mkdir -p "$out" || exit 0

redact() { # redact <src> <dst>
  sed -E \
    -e 's/-----BEGIN ([A-Z ]*)PRIVATE KEY-----[^-]*-----END [A-Z ]*PRIVATE KEY-----/[REDACTED PRIVATE KEY]/g' \
    -e 's/github_pat_[A-Za-z0-9_]{20,}/[REDACTED]/g' \
    -e 's/gh[opsu]_[A-Za-z0-9]{20,}/[REDACTED]/g' \
    -e 's/sk-ant-[A-Za-z0-9_-]{20,}/[REDACTED]/g' \
    -e 's/sk-[A-Za-z0-9_-]{20,}/[REDACTED]/g' \
    -e 's/AKIA[0-9A-Z]{16}/[REDACTED]/g' \
    -e 's/xox[abprs]-[A-Za-z0-9-]{10,}/[REDACTED]/g' \
    "$1" > "$2.tmp" && mv "$2.tmp" "$2"
}

redact "$transcript" "$out/$sid.jsonl"

subdir="${transcript%.jsonl}/subagents"
if [ -d "$subdir" ]; then
  (cd "$subdir" && find . -type f -name '*.jsonl') | while IFS= read -r f; do
    mkdir -p "$out/$sid/subagents/$(dirname "$f")"
    redact "$subdir/$f" "$out/$sid/subagents/$f"
  done
fi

entry="$sid ${stage:-unknown}"
grep -qx "$entry" "$out/index" 2>/dev/null || echo "$entry" >> "$out/index"
exit 0
```

- [ ] **Step 4: Run the test**

Run: `sh tests/capture-session.test.sh`
Expected: every line `ok`, then `all capture-session tests passed`.

- [ ] **Step 5: Write `hooks/hooks.json`**

```json
{
  "hooks": {
    "PreCompact": [
      { "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh\"" } ] }
    ],
    "SessionEnd": [
      { "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh\"" } ] }
    ]
  }
}
```

If Task C1 recorded that `${CLAUDE_SESSION_ID}` is not substituted, also add the `SessionStart` entry that the C1 table describes.

- [ ] **Step 6: Commit**

```bash
git add hooks tests/capture-session.test.sh tests/run.sh
git commit -m "Session capture hook with credential redaction" -- hooks tests
```

### Task C5: agent definitions

**Files:**
- Create: `agents/reviewer.md`, `agents/implementer.md`, `agents/verifier.md`, `agents/retro.md`

Agents set a system prompt and a default model only. They have no `tools` field, so they get the full default toolset. Each `.kentra-rpir.yaml` role may override the model per dispatch.

- [ ] **Step 1: Write `agents/reviewer.md`**

```markdown
---
name: reviewer
description: Independent reviewer for kentra-rpir specs and plans. Answers a fixed checklist yes or no with evidence, and triages each "no" as a user decision or a technical gap.
model: opus
---

You review a document someone else wrote — a spec or a plan — against its
sources. You did not write it and you owe it nothing.

- Answer every checklist item **yes** or **no**. "Mostly" is no.
- Every "no" cites evidence: a quoted line and its location in the document, and
  the source it conflicts with (a transcript line, a spec requirement, a rule
  with its file or ADR id).
- Read the sources yourself. Do not trust the document's description of them.
- List every claim you could not check from the files in front of you as an
  unchecked claim — a version, an API's behaviour, a library's default. Research
  will check them.
- Change nothing. You report.

When asked to triage, classify each "no":

- **decision** — intent is ambiguous, there is a trade-off, the document conflicts
  with what the session decided, or it conflicts with the constitution. Only the
  user can settle it. Phrase it as a question with options.
- **technical** — the writer missed something with one clear answer: a missing
  type name, an unpinned version, overlapping paths, a requirement with no
  milestone. Give the exact fix.

When unsure, classify as decision.
```

- [ ] **Step 2: Write `agents/implementer.md`**

```markdown
---
name: implementer
description: Implements one kentra-rpir milestone in its worktree, exactly as the milestone describes, and reports DONE, DONE_WITH_CONCERNS, NEEDS_CONTEXT or BLOCKED.
model: sonnet
---

You implement one milestone of a plan, in the git worktree you were given. The
milestone and the plan-level context in your task are your specification.

- Load every rule listed in your task before writing code: a skill by name with
  the Skill tool, a path by reading it.
- Follow the steps in order. Write the test first when a step says so, and watch
  it fail before you make it pass.
- Stay inside the milestone's `paths`. A change outside them is a deviation: stop
  and report it instead of making it.
- Do not explore the codebase to decide what to build. Read the files the
  milestone names. If the milestone does not tell you something you need, report
  NEEDS_CONTEXT with the exact question.
- Commit on the current branch when the contract's `check` passes, with a message
  naming the milestone.

Report exactly this, and nothing longer:

    STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
    COMMITS: <sha subject>, one per line
    CHECK: <the command> → <pass/fail and the summary line>
    CONCERNS: <for DONE_WITH_CONCERNS: what may be wrong, and why>
    QUESTION: <for NEEDS_CONTEXT: the exact information you need>
    BLOCKER: <for BLOCKED: what stops you, what you tried, what would unblock you>
    OBSERVED: <anything wrong you noticed outside your milestone and left alone>
```

- [ ] **Step 3: Write `agents/verifier.md`**

```markdown
---
name: verifier
description: Fresh, independent verifier for one kentra-rpir milestone. Checks the contract, the named tests, the diff's paths, the milestone's intent and its rules; returns PASS or FAIL with findings.
model: opus
---

You verify a milestone someone else implemented, in its worktree. You did not
write the code. Trust nothing the implementer said; check the code and run the
commands yourself.

Check, in this order:

1. **Check command.** Run the contract's `check`. It must pass.
2. **Criteria.** For each criterion: the named test exists, it asserts what the
   criterion's given/when/then says — not something weaker — and it passes.
3. **Paths.** `git diff --name-only <base>...HEAD` lists only files matching the
   milestone's `paths`.
4. **Intent.** The code does what the milestone's description and steps intend:
   nothing missing, nothing extra.
5. **Rules.** Load every listed rule (a skill with the Skill tool, a path by
   reading it). The code follows each one.

Report exactly this:

    VERDICT: PASS | FAIL
    CHECK: <command> → <result>
    FINDINGS:
    - [criteria|paths|intent|rules|check] <file:line> — <what is wrong> — <what the fix is>

PASS only when every check holds. A finding you are unsure of is still a
finding; say what you are unsure of.
```

- [ ] **Step 4: Write `agents/retro.md`**

```markdown
---
name: retro
description: Reads coding-agent session transcripts for a kentra-rpir retro — pre-pass signals, blind per-milestone reading, synthesis, reconciliation and remediation — citing transcript evidence for every claim.
model: opus
---

You analyse what happened in coding-agent sessions, from their transcripts.
Transcripts are JSONL files written by Claude Code. Their format is internal and
changes between versions, so read them leniently: use `jq` or `grep` when they
help, and do not fail on an unexpected field.

- Every claim cites evidence: `<file>:<line>` and a short verbatim quote.
- Describe how each action made sense to the agent at the time, given what was
  in its context. Ask how, not why — never invent motives.
- The decisive point of a failure is the earliest step where the agent acted on
  a wrong belief. Work back to it from the first visible symptom; it is usually
  well before the symptom.
- A passing run can be a lucky pass: check that the verification ran and checked
  what it claims to.
- Never edit the files you analyse. Write only the output file your task names.
```

- [ ] **Step 5: Commit**

```bash
git add agents
git commit -m "Reviewer, implementer, verifier and retro agents" -- agents
```

### Task C6: workflow test harness

Workflow scripts run inside Claude Code's Workflow tool, which provides `args`, `agent`, `parallel`, `pipeline`, `log`, `phase` and `workflow`. The harness parses each script the same way and runs it against stubs. Each stub `agent()` returns a value that conforms to the schema it was given, so the control flow is exercised offline. A fixture can override the value returned for a given agent label.

**Files:**
- Create: `tests/check-workflows.mjs`
- Create: `tests/workflow-fixtures.mjs`

- [ ] **Step 1: Write `tests/check-workflows.mjs`**

```js
// Parses and dry-runs every workflows/*.js against stubbed Workflow globals.
// Usage: node tests/check-workflows.mjs   (from the repo root)
import { readFileSync, readdirSync } from 'node:fs'
import { basename, join } from 'node:path'
import { fixtures } from './workflow-fixtures.mjs'

const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
let failures = 0
const fail = (wf, msg) => { failures++; console.log(`FAIL - ${wf}: ${msg}`) }
const ok = (wf, msg) => console.log(`ok   - ${wf}: ${msg}`)

// A value that satisfies a JSON schema: required properties filled, enums take
// their first value, arrays empty.
export function fake(schema) {
  if (!schema) return 'stub result'
  if (schema.enum) return schema.enum[0]
  switch (schema.type) {
    case 'object': {
      const o = {}
      for (const k of schema.required || []) o[k] = fake(schema.properties?.[k])
      return o
    }
    case 'array': return []
    case 'string': return ''
    case 'boolean': return false
    case 'number': case 'integer': return 0
    default: return null
  }
}

function load(file) {
  const src = readFileSync(file, 'utf8')
  const m = src.match(/^export const meta = (\{[\s\S]*?\n\})\n/m)
  if (!m) throw new Error('no pure-literal `export const meta = {...}` block at column 0')
  const meta = Function(`"use strict"; return (${m[1]})`)()
  const body = src.replace(m[0], '')
  const run = new AsyncFunction('args', 'agent', 'parallel', 'pipeline', 'log', 'phase', 'workflow', body)
  return { src, meta, run }
}

const dir = 'workflows'
for (const f of readdirSync(dir).filter(f => f.endsWith('.js'))) {
  const wf = basename(f, '.js')
  let loaded
  try { loaded = load(join(dir, f)) } catch (e) { fail(wf, `does not parse: ${e.message}`); continue }
  const { src, meta, run } = loaded
  if (meta.name !== wf) fail(wf, `meta.name is "${meta.name}", expected "${wf}"`)
  const titles = new Set((meta.phases || []).map(p => p.title))
  for (const [, t] of src.matchAll(/phase: '([^']+)'/g)) {
    if (!titles.has(t)) fail(wf, `phase '${t}' is used but not declared in meta.phases`)
  }

  const fx = fixtures[wf]
  if (!fx) { fail(wf, 'no fixture in tests/workflow-fixtures.mjs'); continue }
  for (const [caseName, c] of Object.entries(fx)) {
    const calls = { agents: [], workflows: [] }
    const agent = async (prompt, opts = {}) => {
      calls.agents.push({ prompt, ...opts })
      const override = Object.entries(c.overrides || {}).find(([prefix]) => (opts.label || '').startsWith(prefix))
      return override ? structuredClone(override[1]) : fake(opts.schema)
    }
    const workflow = async (name, a) => { calls.workflows.push({ name, args: a }); return c.workflowResult ?? { reports: [], failed: [], index: null } }
    const parallel = thunks => Promise.all(thunks.map(t => t()))
    const pipeline = (items, ...stages) => Promise.all(items.map(async item => {
      let v = await stages[0](item)
      for (const s of stages.slice(1)) v = await s(v, item)
      return v
    }))
    try {
      const result = await run(structuredClone(c.args), agent, parallel, pipeline, () => {}, () => {}, workflow)
      for (const [desc, assert] of Object.entries(c.expect || {})) {
        if (assert(result, calls)) ok(wf, `${caseName}: ${desc}`)
        else fail(wf, `${caseName}: ${desc}`)
      }
      if (!c.expect) ok(wf, `${caseName}: runs`)
    } catch (e) {
      if (c.throws && c.throws.test(e.message)) ok(wf, `${caseName}: throws as expected`)
      else fail(wf, `${caseName}: threw ${e.stack}`)
    }
  }
}
if (failures) { console.log(`${failures} workflow check(s) failed`); process.exit(1) }
console.log('all workflow checks passed')
```

- [ ] **Step 2: Write `tests/workflow-fixtures.mjs`**

```js
// Per-workflow dry-run cases for tests/check-workflows.mjs.
// overrides: agent label prefix → the value that agent returns.
const roles = {
  reviewer: { agent: 'kentra-rpir:reviewer', model: 'opus' },
  prepass: { agent: 'kentra-rpir:retro', model: 'haiku' },
  reader: { agent: 'kentra-rpir:retro', model: 'sonnet' },
  retro: { agent: 'kentra-rpir:retro', model: 'opus' },
}
const research = { workflow: 'kentra-rpir:research', collector: 'haiku', reasoner: 'opus' }

export const fixtures = {
  research: {
    'rejects missing args': { args: {}, throws: /needs args/ },
    'one topic': {
      args: { today: '2026-10-11', topics: [{ name: 'Kafka 4.1 headers' }], collector: 'haiku', reasoner: 'opus' },
      expect: {
        'collectors use the configured model': (_, c) => c.agents.filter(a => a.label.startsWith('collect:')).every(a => a.model === 'haiku'),
        'one collector per default angle': (_, c) => c.agents.filter(a => a.label.startsWith('collect:')).length === 4,
        'reasoner uses the configured model': (_, c) => c.agents.some(a => a.label === 'reason:kafka-4-1-headers' && a.model === 'opus'),
        'every prompt opens with the only-job preamble': (_, c) => c.agents.every(a => a.prompt.startsWith('Your only job is the task below.')),
      },
    },
  },
  'self-review': {
    'rejects a bad stage': { args: { today: '2026-10-11', stage: 'x', document: '/d', projectRoot: '/p', reviewer: roles.reviewer, research }, throws: /stage/ },
    'clean spec: no research, no triage': {
      args: { today: '2026-10-11', stage: 'spec', document: '/p/spec.md', projectRoot: '/p', transcripts: ['/p/s.jsonl'], constitution: null, reviewer: roles.reviewer, research },
      overrides: { review: { checks: [{ id: 'S1', answer: 'yes', evidence: '' }], unchecked_claims: [] } },
      expect: {
        'reviewer runs with the role agent and model': (_, c) => c.agents[0].agentType === 'kentra-rpir:reviewer' && c.agents[0].model === 'opus',
        'research not called': (_, c) => c.workflows.length === 0,
        'no triage agent': (_, c) => !c.agents.some(a => a.label === 'triage'),
        'returns no findings': r => Array.isArray(r.findings) && r.findings.length === 0,
      },
    },
    'plan with a no and a claim: research then triage': {
      args: { today: '2026-10-11', stage: 'plan', document: '/p/plan.yaml', projectRoot: '/p', spec: '/p/spec.md', constitution: '/p/constitution/constitution.md', reviewer: roles.reviewer, research },
      overrides: {
        review: { checks: [{ id: 'P4', answer: 'no', evidence: 'jackson unpinned' }], unchecked_claims: [{ claim: 'Jackson 2.18 supports records', why: 'M2 relies on it' }] },
        triage: { findings: [{ check: 'P4', kind: 'technical', summary: 'pin jackson', evidence: 'plan.yaml:40', fix: 'pin 2.18.2' }], research_outcomes: [] },
      },
      expect: {
        'research workflow called with the claim': (_, c) => c.workflows.length === 1 && c.workflows[0].name === 'kentra-rpir:research' && c.workflows[0].args.topics[0].name === 'Jackson 2.18 supports records',
        'research gets the configured models': (_, c) => c.workflows[0].args.collector === 'haiku' && c.workflows[0].args.reasoner === 'opus',
        'plan checklist used': (_, c) => c.agents[0].prompt.includes('P12'),
        'triage ran': (_, c) => c.agents.some(a => a.label === 'triage'),
        'finding returned': r => r.findings.length === 1 && r.findings[0].kind === 'technical',
      },
    },
  },
  'spec-retro': {
    'two milestones, one troubled': {
      args: {
        today: '2026-10-11', changeDir: '/p/docs/changes/2026-10-11-demo', projectRoot: '/p',
        spec: '/p/c/spec.md', plan: '/p/c/plan.yaml', implReport: '/p/c/impl-report.md', out: '/p/c/spec-retro.md',
        template: '/plugin/templates/spec-retro.md',
        sessions: [{ id: 's1', stage: 'spec', path: '/p/c/sessions/s1.jsonl' }, { id: 's2', stage: 'implement', path: '/p/c/sessions/s2.jsonl' }],
        milestones: [{ number: 1, slug: 'core', paths: ['src/core/**'], diffRange: 'a..b' }, { number: 2, slug: 'cli', paths: ['src/cli/**'], diffRange: 'b..c' }],
        priorRetros: [], roles, versions: { plugin: '0.3.0' },
      },
      overrides: {
        'prepass:s2': { claudeCodeVersion: '2.1.296', milestones: [
          { slug: 'core', dispatches: 1, verifierFails: 0, writesOutsidePaths: [], testRuns: 2, errors: 0, blocked: false },
          { slug: 'cli', dispatches: 3, verifierFails: 1, writesOutsidePaths: [], testRuns: 3, errors: 2, blocked: false },
        ], skeletons: [] },
      },
      expect: {
        'prepass only reads implement sessions': (_, c) => c.agents.filter(a => a.label.startsWith('prepass:')).map(a => a.label).join() === 'prepass:s2',
        'one reader per milestone': (_, c) => c.agents.filter(a => a.label.startsWith('read:')).length === 2,
        'troubled milestone read in full': (_, c) => c.agents.find(a => a.label === 'read:cli').prompt.includes('MODE: full'),
        'clean milestone skimmed': (_, c) => c.agents.find(a => a.label === 'read:core').prompt.includes('MODE: skim'),
        'readers never see impl-report': (_, c) => c.agents.filter(a => a.label.startsWith('read:')).every(a => !a.prompt.includes('impl-report.md')),
        'reconciliation reads impl-report': (_, c) => c.agents.find(a => a.label === 'reconcile').prompt.includes('/p/c/impl-report.md'),
        'writer targets the out path': (_, c) => c.agents.find(a => a.label === 'write').prompt.includes('/p/c/spec-retro.md'),
        'models follow roles': (_, c) => c.agents.find(a => a.label === 'read:cli').model === 'sonnet' && c.agents.find(a => a.label === 'synthesis').model === 'opus',
      },
    },
  },
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `node tests/check-workflows.mjs`
Expected: FAIL. The `workflows` directory does not exist yet, so `readdirSync` throws `ENOENT`.

### Task C7: the three workflows

**Files:**
- Create: `workflows/research.js`, `workflows/self-review.js`, `workflows/spec-retro.js`

- [ ] **Step 1: Write `workflows/research.js`**

This is the user-space `~/.claude/workflows/research.js` with the following changes:

- models come from `args`;
- the default angles are the design's (official, source, field, spike);
- every prompt opens with the only-job preamble.

```js
export const meta = {
  name: 'research',
  description: 'Checks the facts a spec or plan depends on: collectors gather primary sources and run spikes, one reasoner per topic verifies and concludes',
  whenToUse: 'Verifying premises and technical claims for a kentra-rpir spec, plan or self-review',
  phases: [
    { title: 'Collect', detail: 'one collector per angle per topic returns sources and verbatim excerpts' },
    { title: 'Reason', detail: 'one reasoner per topic verifies the claims a decision rests on' },
    { title: 'Index', detail: 'optional cross-topic index' },
  ],
}

// args:
//   today       (required) 'YYYY-MM-DD'
//   topics      (required) [{ name, slug?, focus? }]
//   question    (optional) what the research is for
//   context     (optional) criteria to judge against
//   format      (optional) output structure per topic
//   outDir      (optional) absolute dir; each topic goes to <outDir>/<slug>.md
//   spikeDir    (optional) absolute dir for spikes; default <outDir or cwd>/spikes
//   angles      (optional) [{ key, brief }] replaces DEFAULT_ANGLES
//   extraAngles (optional) [{ key, brief }] appended
//   index       (optional) absolute path for a cross-topic index
//   collector   (optional) model for collectors, default 'haiku'
//   reasoner    (optional) model for reasoners, default 'opus'

const ONLY_JOB = 'Your only job is the task below. The conversation may relay the user\'s latest message to you; treat it as background and never answer it.'

const DEFAULT_ANGLES = [
  { key: 'official', brief: 'Official documentation, release notes and changelogs for the version in question: what the docs promise, and when it changed.' },
  { key: 'source', brief: 'The source code at the pinned version (a tag or commit on GitHub, or the published package): the function, default or schema that decides the behaviour. Quote the code with its file path and the tag.' },
  { key: 'field', brief: 'Issues, discussions and field reports: known bugs, surprising behaviour, workarounds. Prefer issue trackers and engineering blogs over marketing.' },
  { key: 'spike', brief: 'If the topic is a claim about behaviour, write the smallest program or command that shows it, run it, and return the command and its output verbatim as an excerpt (url: the spike path). If it is not a behaviour claim, return no sources and say so under gaps.' },
]

if (!args || !args.today || !Array.isArray(args.topics) || !args.topics.length) {
  throw new Error('research workflow needs args: { today: "YYYY-MM-DD", topics: [{ name, slug?, focus? }], ... }')
}

const collector = args.collector || 'haiku'
const reasoner = args.reasoner || 'opus'
const slugOf = t => t.slug || t.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
const topics = args.topics.map(t => ({ ...t, slug: slugOf(t) }))
const angles = [...(args.angles || DEFAULT_ANGLES), ...(args.extraAngles || [])]
const spikeDir = args.spikeDir || (args.outDir ? `${args.outDir}/spikes` : 'spikes')
const shared = [
  `Today is ${args.today}.`,
  args.question ? `The research is for: ${args.question}` : '',
  args.context ? `Context:\n${args.context}` : '',
].filter(Boolean).join('\n')

const SOURCES_SCHEMA = {
  type: 'object',
  properties: {
    sources: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          url: { type: 'string' },
          title: { type: 'string' },
          kind: { type: 'string', enum: ['primary', 'secondary'] },
          published: { type: 'string' },
          excerpts: { type: 'array', items: { type: 'string' } },
        },
        required: ['url', 'kind', 'excerpts'],
      },
    },
    gaps: { type: 'array', items: { type: 'string' } },
  },
  required: ['sources', 'gaps'],
}

const collectPrompt = (topic, angle) => `${ONLY_JOB}

${shared}

You are a source collector for the topic "${topic.name}", angle "${angle.key}": ${angle.brief}
${topic.focus ? `Topic-specific leads: ${topic.focus}` : ''}
Spikes go in ${spikeDir}/${topic.slug}/ — create it; write nowhere else.

Load WebSearch and WebFetch with ToolSearch ("select:WebSearch,WebFetch"). Return sources with VERBATIM excerpts: the exact sentences, code, version strings and dates that matter. Mark official project pages, source code and your own spikes as primary, everything else as secondary.

You are done when you have the best 3-10 sources for this angle, each with the excerpts that carry its facts, and every question you could not answer is listed under gaps.`

const reasonPrompt = (topic, collected) => `${ONLY_JOB}

${shared}

You are the reasoner for the topic "${topic.name}". ${angles.length} collectors gathered evidence from different angles; it is below. Treat it as leads: collectors quote but do not judge, and they miss things.

1. Build the answer from the evidence.
2. Verify every claim the conclusion rests on against a primary source yourself (load WebSearch and WebFetch with ToolSearch, "select:WebSearch,WebFetch"). Prefer source code at a pinned version over documentation, and documentation over posts. When the claim is about behaviour and no spike settled it, run one in ${spikeDir}/${topic.slug}/.
3. Where sources conflict, prefer the newer primary source and say so. Mark anything you could not confirm as **(unverified)** where you state it.
4. End with a verdict line: VERDICT: verified | refuted | unverified — <one sentence>.
${topic.focus ? `\nTopic-specific leads: ${topic.focus}` : ''}
${args.format ? `\nOutput structure:\n${args.format}` : '\nOutput structure: a concise Markdown report — the verdict, the findings with their sources, open questions, and a numbered Sources list.'}
${args.outDir ? `\nWrite the report to ${args.outDir}/${topic.slug}.md and nothing else.` : '\nInclude the full Markdown report in your return value.'}

You are done when every claim the conclusion rests on is verified or marked (unverified), and the verdict line is written.

Collected evidence (JSON):
${JSON.stringify(collected)}`

const REPORT_SCHEMA = {
  type: 'object',
  properties: {
    topic: { type: 'string' },
    verdict: { type: 'string', enum: ['verified', 'refuted', 'unverified'] },
    file: { type: 'string' },
    report: { type: 'string' },
    key_findings: { type: 'array', items: { type: 'string' } },
    sources: { type: 'array', items: { type: 'string' } },
    unverified: { type: 'array', items: { type: 'string' } },
    open_questions: { type: 'array', items: { type: 'string' } },
  },
  required: ['topic', 'verdict', 'key_findings', 'sources', 'unverified', 'open_questions'],
}

log(`${topics.length} topic(s) x ${angles.length} collectors (${collector}), reasoner ${reasoner}`)

const results = await pipeline(
  topics,
  topic => parallel(angles.map(angle => () =>
    agent(collectPrompt(topic, angle), {
      label: `collect:${topic.slug}:${angle.key}`, phase: 'Collect', model: collector, schema: SOURCES_SCHEMA,
    }).then(r => r && { angle: angle.key, ...r }))),
  (collected, topic) => agent(reasonPrompt(topic, collected.filter(Boolean)), {
    label: `reason:${topic.slug}`, phase: 'Reason', model: reasoner, schema: REPORT_SCHEMA,
  }).then(r => r && { ...r, topic: topic.name }),
)

const reports = results.filter(Boolean)
const failed = topics.filter(t => !reports.some(r => r.topic === t.name)).map(t => t.name)
if (failed.length) log(`No report for: ${failed.join(', ')}`)

let index = null
if (args.index && reports.length) {
  index = await agent(`${ONLY_JOB}

${shared}

Write a cross-topic index to ${args.index}: purpose and date, a table of topics with their verdicts and key facts, and consolidated open questions. Keep it brief.

Report summaries (JSON): ${JSON.stringify(reports.map(r => ({ ...r, report: undefined })))}

Return a 10-line summary of the findings that matter most.`,
    { label: 'index', phase: 'Index', model: reasoner })
}

return { reports, failed, index }
```

- [ ] **Step 2: Write `workflows/self-review.js`**

```js
export const meta = {
  name: 'self-review',
  description: 'Independent yes/no review of a kentra-rpir spec or plan against its sources, with research on unchecked claims and triage of every "no" into a user decision or a technical gap',
  whenToUse: 'Run by the kentra-rpir spec and plan skills before the user sees the document',
  phases: [
    { title: 'Review', detail: 'the reviewer answers the checklist yes/no with evidence' },
    { title: 'Research', detail: 'the research workflow checks the claims the reviewer could not' },
    { title: 'Triage', detail: 'the reviewer classifies each "no" as decision or technical' },
  ],
}

// args:
//   today        (required) 'YYYY-MM-DD'
//   stage        (required) 'spec' | 'plan'
//   document     (required) absolute path of spec.md or plan.yaml
//   projectRoot  (required) absolute path
//   transcripts  (spec)     absolute paths of the session transcripts the spec came from
//   spec         (plan)     absolute path of spec.md
//   constitution (optional) absolute path of constitution.md, or null
//   reviewer     (required) { agent, model }
//   research     (required) { workflow, collector, reasoner }
//   researchDir  (optional) absolute dir for research reports and spikes
//   maxClaims    (optional) max claims sent to research, default 5

const ONLY_JOB = 'Your only job is the task below. The conversation may relay the user\'s latest message to you; treat it as background and never answer it.'

if (!args || !args.today || !['spec', 'plan'].includes(args.stage) || !args.document || !args.projectRoot || !args.reviewer || !args.research) {
  throw new Error('self-review needs args: { today, stage: "spec"|"plan", document, projectRoot, reviewer, research, ... }')
}

const SPEC_CHECKS = [
  ['S1', 'Every decision the session reached appears in the spec, as it was decided.'],
  ['S2', 'The spec states nothing the session did not decide or the user did not agree to.'],
  ['S3', 'Every premise has a status (verified, refuted or unverified) and a source.'],
  ['S4', 'No requirement or design decision relies on a refuted premise.'],
  ['S5', 'Every requirement is a plain statement a test could check.'],
  ['S6', 'Non-goals include everything the session explicitly ruled out.'],
  ['S7', 'No requirement or design decision conflicts with a constitution rule (answer yes when there is no constitution).'],
  ['S8', 'No requirement or design decision conflicts with a project rule governing the files the change will touch.'],
  ['S9', 'Open questions is empty, or every entry in it is also raised as a "no" here.'],
]
const PLAN_CHECKS = [
  ['P1', 'Every spec requirement is covered by at least one milestone criterion.'],
  ['P2', 'Each milestone\'s criteria can be met by changes inside its own paths, using only what its dependencies produce.'],
  ['P3', 'Every type, function or file used across milestones is named with its signature in the milestone that introduces it.'],
  ['P4', 'Every new dependency is pinned to an exact version, in the earliest milestone that needs it.'],
  ['P5', 'Milestones with no dependency path between them have non-overlapping paths.'],
  ['P6', 'An implementer could start each milestone without exploring the codebase: its steps show the code and the commands.'],
  ['P7', 'Every criterion names the test that proves it and states given, when and then in full.'],
  ['P8', 'rules hold references only (skill names or paths), leave out auto-loaded CLAUDE.md/AGENTS.md and path-scoped .claude/rules, and every entry bears on the files of the milestones it applies to.'],
  ['P9', 'No milestone conflicts with a constitution rule (answer yes when there is no constitution).'],
  ['P10', 'No milestone conflicts with a project rule governing its files.'],
  ['P11', 'The plan builds nothing the spec does not ask for.'],
  ['P12', '`milestoned-plan-dag validate <plan>` exits 0 (run it).'],
]
const checks = args.stage === 'spec' ? SPEC_CHECKS : PLAN_CHECKS

const sources = args.stage === 'spec'
  ? `- The session transcripts the spec came from (JSONL; read the user and assistant messages, skip tool output unless you need it): ${(args.transcripts || []).join(', ') || '(none given — answer S1, S2 and S6 no, with evidence "no transcript")'}`
  : `- The spec it implements: ${args.spec}`

const projectRules = `"Project rules" are the rules that govern the files this change will touch:
- the CLAUDE.md and AGENTS.md files in those files' directories and every parent directory up to ${args.projectRoot};
- .claude/rules/*.md files whose \`paths\` frontmatter matches those files;
- the skills those files or the plan's \`rules\` call for (load them with the Skill tool).
Find the touched files from the ${args.stage === 'spec' ? "spec's design and the code it names" : "plan's deliverables and paths"}.`

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    checks: { type: 'array', items: { type: 'object', properties: {
      id: { type: 'string' }, answer: { type: 'string', enum: ['yes', 'no'] }, evidence: { type: 'string' },
    }, required: ['id', 'answer', 'evidence'] } },
    unchecked_claims: { type: 'array', items: { type: 'object', properties: {
      claim: { type: 'string' }, why: { type: 'string' },
    }, required: ['claim', 'why'] } },
    rules_read: { type: 'array', items: { type: 'string' } },
  },
  required: ['checks', 'unchecked_claims'],
}

const review = await agent(`${ONLY_JOB}

Review the ${args.stage} at ${args.document}, in the project at ${args.projectRoot}.

Sources to check it against:
${sources}
- The constitution: ${args.constitution || '(none — this project has no constitution)'}
- The project rules, as defined below.

${projectRules}

Answer every item yes or no. Every "no" cites the document line and the source it conflicts with.
${checks.map(([id, q]) => `${id}. ${q}`).join('\n')}

Then list every claim the document depends on that you could not check from the files — a version, an API's behaviour, a default — as unchecked_claims, each with why it matters.

You are done when every item from ${checks[0][0]} to ${checks[checks.length - 1][0]} has an answer with evidence.`,
  { label: 'review', phase: 'Review', agentType: args.reviewer.agent, model: args.reviewer.model, schema: REVIEW_SCHEMA })

const noes = (review?.checks || []).filter(c => c.answer === 'no')
const claims = (review?.unchecked_claims || []).slice(0, args.maxClaims || 5)
log(`${noes.length} "no" answer(s), ${claims.length} claim(s) for research`)

let research = null
if (claims.length) {
  try {
    research = await workflow(args.research.workflow, {
      today: args.today,
      topics: claims.map(c => ({ name: c.claim, focus: c.why })),
      question: `Self-review of ${args.document}: is each claim true?`,
      collector: args.research.collector,
      reasoner: args.research.reasoner,
      ...(args.researchDir ? { outDir: args.researchDir } : {}),
    })
  } catch (e) {
    log(`research failed: ${e.message}`)
  }
}

if (!noes.length && !claims.length) {
  return { stage: args.stage, checks: review?.checks || [], findings: [], research: [] }
}

const TRIAGE_SCHEMA = {
  type: 'object',
  properties: {
    findings: { type: 'array', items: { type: 'object', properties: {
      check: { type: 'string' },
      kind: { type: 'string', enum: ['decision', 'technical'] },
      summary: { type: 'string' },
      evidence: { type: 'string' },
      question: { type: 'string' },
      options: { type: 'array', items: { type: 'string' } },
      fix: { type: 'string' },
    }, required: ['check', 'kind', 'summary', 'evidence'] } },
    research_outcomes: { type: 'array', items: { type: 'object', properties: {
      claim: { type: 'string' }, status: { type: 'string', enum: ['verified', 'refuted', 'unverified'] }, source: { type: 'string' },
    }, required: ['claim', 'status', 'source'] } },
  },
  required: ['findings', 'research_outcomes'],
}

const triage = await agent(`${ONLY_JOB}

You reviewed ${args.document}. Your "no" answers and the research on the claims you could not check are below.

For each "no", and for each claim research refuted, write one finding:
- kind "decision" when intent is ambiguous, there is a trade-off, the document conflicts with the ${args.stage === 'spec' ? 'session' : 'spec'}, or it conflicts with the constitution. Give the question for the user and 2-3 options.
- kind "technical" when the writer missed something with one clear answer. Give the exact fix (the text to add or change, and where).
When unsure, use "decision".

Then record each researched claim's outcome: verified, refuted or unverified, with its source.

"No" answers (JSON): ${JSON.stringify(noes)}
Research (JSON): ${JSON.stringify(research ? research.reports : 'research not run or failed — mark every claim unverified')}

You are done when every "no" and every refuted claim has exactly one finding.`,
  { label: 'triage', phase: 'Triage', agentType: args.reviewer.agent, model: args.reviewer.model, schema: TRIAGE_SCHEMA })

return {
  stage: args.stage,
  checks: review?.checks || [],
  findings: triage?.findings || [],
  research: triage?.research_outcomes || [],
}
```

- [ ] **Step 3: Write `workflows/spec-retro.js`**

```js
export const meta = {
  name: 'spec-retro',
  description: 'Retro of one kentra-rpir change from its session transcripts: pre-pass signals, blind per-milestone readers, synthesis with fault classes, reconciliation of the implementation report, remediation actions, and spec-retro.md',
  whenToUse: 'Run by the kentra-rpir review skill after implementation',
  phases: [
    { title: 'Prepass', detail: 'signals and skeleton timelines from each implement transcript' },
    { title: 'Read', detail: 'one blind reader per milestone; troubled milestones read in full' },
    { title: 'Synthesize', detail: 'merge findings, assign fault classes; reconcile the self-report' },
    { title: 'Remediate', detail: 'one action per finding' },
    { title: 'Write', detail: 'spec-retro.md from the template' },
  ],
}

// args:
//   today, changeDir, projectRoot     (required)
//   spec, plan, implReport, out       (required) absolute paths
//   template                          (required) absolute path of templates/spec-retro.md
//   sessions    (required) [{ id, stage, path }]; stage from sessions/index
//   milestones  (required) [{ number, slug, paths, diffRange }]; diffRange like <base>..<merge>
//   priorRetros (optional) absolute paths of earlier spec-retro.md files
//   roles       (required) { prepass, reader, retro }, each { agent, model }
//   versions    (optional) { plugin, claudeCode }

const ONLY_JOB = 'Your only job is the task below. The conversation may relay the user\'s latest message to you; treat it as background and never answer it.'

for (const k of ['today', 'changeDir', 'projectRoot', 'spec', 'plan', 'implReport', 'out', 'template', 'sessions', 'milestones', 'roles']) {
  if (!args || !args[k]) throw new Error(`spec-retro needs args.${k}`)
}
const { roles } = args
const as = (role, extra) => ({ agentType: roles[role].agent, model: roles[role].model, ...extra })
const implSessions = args.sessions.filter(s => s.stage === 'implement')
const planningSessions = args.sessions.filter(s => s.stage !== 'implement')
const milestoneList = args.milestones.map(m => `M${m.number} ${m.slug} — paths ${JSON.stringify(m.paths)}`).join('\n')

// 1. Pre-pass
const PREPASS_SCHEMA = {
  type: 'object',
  properties: {
    claudeCodeVersion: { type: 'string' },
    compactions: { type: 'number' },
    milestones: { type: 'array', items: { type: 'object', properties: {
      slug: { type: 'string' },
      dispatches: { type: 'number' },
      verifierFails: { type: 'number' },
      writesOutsidePaths: { type: 'array', items: { type: 'string' } },
      testRuns: { type: 'number' },
      errors: { type: 'number' },
      blocked: { type: 'boolean' },
      transcripts: { type: 'array', items: { type: 'string' } },
    }, required: ['slug', 'dispatches', 'verifierFails', 'writesOutsidePaths', 'testRuns', 'errors', 'blocked'] } },
    skeletons: { type: 'array', items: { type: 'object', properties: {
      slug: { type: 'string' },
      events: { type: 'array', items: { type: 'object', properties: { ref: { type: 'string' }, event: { type: 'string' } }, required: ['ref', 'event'] } },
    }, required: ['slug', 'events'] } },
  },
  required: ['milestones', 'skeletons'],
}

phase('Prepass')
const prepasses = await parallel(implSessions.map(s => () => agent(`${ONLY_JOB}

Extract signals from an implementation session. The orchestrator transcript is ${s.path}; its subagents' transcripts are under ${s.path.replace(/\.jsonl$/, '')}/subagents/. Each subagent's first user message is its dispatch prompt and names its milestone.

Milestones:
${milestoneList}

For each milestone, count: implementer dispatches, verifier FAIL verdicts, errors and non-zero exits, test runs. List files written outside its paths, and the transcript files that belong to it. Mark it blocked if it was never merged. Record the Claude Code version (the "version" field on transcript lines) and the number of compact_boundary markers.

Then write a skeleton timeline per milestone: one event per dispatch, verdict, error, retry, user message and merge, each with its ref <file>:<line>.

Use jq and grep through Bash; read leniently and skip lines you cannot parse. You are done when every milestone listed above has counts and a skeleton.`,
  as('prepass', { label: `prepass:${s.id}`, phase: 'Prepass', schema: PREPASS_SCHEMA }))))

const signals = new Map()
const skeletons = new Map()
for (const p of prepasses.filter(Boolean)) {
  for (const m of p.milestones || []) {
    const prev = signals.get(m.slug)
    signals.set(m.slug, prev ? {
      ...prev,
      dispatches: prev.dispatches + m.dispatches,
      verifierFails: prev.verifierFails + m.verifierFails,
      writesOutsidePaths: [...prev.writesOutsidePaths, ...m.writesOutsidePaths],
      testRuns: prev.testRuns + m.testRuns,
      errors: prev.errors + m.errors,
      blocked: prev.blocked && m.blocked,
      transcripts: [...(prev.transcripts || []), ...(m.transcripts || [])],
    } : m)
  }
  for (const sk of p.skeletons || []) skeletons.set(sk.slug, [...(skeletons.get(sk.slug) || []), ...sk.events])
}
const claudeCodeVersion = prepasses.find(p => p?.claudeCodeVersion)?.claudeCodeVersion || args.versions?.claudeCode || 'unknown'

const troubled = s => !s || s.blocked || s.dispatches > 1 || s.verifierFails > 0 || s.errors > 0 || s.writesOutsidePaths.length > 0 || s.testRuns === 0

// 2. Blind readers
const READ_SCHEMA = {
  type: 'object',
  properties: {
    milestone: { type: 'string' },
    mode: { type: 'string', enum: ['full', 'skim'] },
    timeline: { type: 'array', items: { type: 'object', properties: { ref: { type: 'string' }, event: { type: 'string' }, decisive: { type: 'boolean' } }, required: ['ref', 'event'] } },
    findings: { type: 'array', items: { type: 'object', properties: {
      summary: { type: 'string' },
      decisive_ref: { type: 'string' },
      wrong_belief: { type: 'string' },
      symptom_ref: { type: 'string' },
      evidence: { type: 'array', items: { type: 'object', properties: { ref: { type: 'string' }, quote: { type: 'string' } }, required: ['ref', 'quote'] } },
    }, required: ['summary', 'evidence'] } },
    went_well: { type: 'array', items: { type: 'string' } },
  },
  required: ['milestone', 'mode', 'timeline', 'findings', 'went_well'],
}

phase('Read')
const reads = await parallel(args.milestones.map(m => () => {
  const s = signals.get(m.slug)
  const mode = troubled(s) ? 'full' : 'skim'
  return agent(`${ONLY_JOB}

MODE: ${mode}

You are a blind reader for milestone M${m.number} ${m.slug} of the change in ${args.changeDir}. You have not seen what the implementing agents said about their own work, and you must not look for it — do not open any report file in the change folder.

Read:
- the spec: ${args.spec}
- the plan: ${args.plan} (milestone ${m.slug})
- the diff: \`git -C ${args.projectRoot} diff ${m.diffRange}\`
- the signals: ${JSON.stringify(s || 'none — the pre-pass found nothing for this milestone')}
- the skeleton timeline: ${JSON.stringify(skeletons.get(m.slug) || [])}
- the transcripts: ${JSON.stringify(s?.transcripts || implSessions.map(x => x.path))}
${planningSessions.length ? `- only if a finding points at the spec or plan: the planning sessions ${planningSessions.map(x => x.path).join(', ')}` : ''}

${mode === 'full'
    ? 'Read the transcripts in full. Write the timeline. For every failure, retry or deviation, find the decisive point: the earliest step where an agent acted on a wrong belief, working back from the first visible symptom.'
    : 'The signals look clean. Read the skeleton, the diff and the verifier\'s transcript; confirm the verification ran the named tests and checked what the criteria say. A passing run can still be a lucky pass. Read in full only if something does not add up.'}

Every claim cites <file>:<line> and a short verbatim quote. Note what went well. You are done when the timeline is written and every finding has evidence.`,
    as('reader', { label: `read:${m.slug}`, phase: 'Read', schema: READ_SCHEMA }))
}))

// 3. Synthesis and reconciliation, in parallel
const SYNTH_SCHEMA = {
  type: 'object',
  properties: {
    outcome: { type: 'string' },
    findings: { type: 'array', items: { type: 'object', properties: {
      id: { type: 'string' },
      class: { type: 'string', enum: ['SPEC', 'SKILL', 'PROCESS', 'ENV', 'MODEL'] },
      summary: { type: 'string' },
      decisive_ref: { type: 'string' },
      made_sense: { type: 'string' },
      severity: { type: 'string', enum: ['high', 'medium', 'low'] },
      milestones: { type: 'array', items: { type: 'string' } },
    }, required: ['id', 'class', 'summary', 'decisive_ref', 'made_sense', 'severity'] } },
    went_well: { type: 'array', items: { type: 'string' } },
  },
  required: ['outcome', 'findings', 'went_well'],
}
const RECON_SCHEMA = {
  type: 'object',
  properties: {
    claims: { type: 'array', items: { type: 'object', properties: {
      claim: { type: 'string' },
      verdict: { type: 'string', enum: ['confirmed', 'contradicted', 'unsupported'] },
      evidence: { type: 'string' },
    }, required: ['claim', 'verdict', 'evidence'] } },
  },
  required: ['claims'],
}

phase('Synthesize')
const readsJson = JSON.stringify(reads.filter(Boolean))
const [synthesis, reconciliation] = await parallel([
  () => agent(`${ONLY_JOB}

Merge the blind readers' findings for the change in ${args.changeDir} into one list. Merge duplicates; keep every distinct finding. Give each an id (F1, F2, …) and one fault class:
- SPEC: the spec or plan was ambiguous or wrong
- SKILL: a skill, agent definition or prompt was missing, wrong or harmful
- PROCESS: the workflow itself — a handoff, a verification step, the order of steps
- ENV: tools, dependencies, sandbox, flaky infrastructure
- MODEL: the model failed with adequate context in front of it
For each, say how the action made sense to the agent at the time, given what was in its context. Ask how, not why.
Write the outcome in two or three sentences from the verifier results in the readers' evidence — not from any report.

Readers' output (JSON): ${readsJson}

You are done when every reader finding is in exactly one merged finding.`,
    as('retro', { label: 'synthesis', phase: 'Synthesize', schema: SYNTH_SCHEMA })),
  () => agent(`${ONLY_JOB}

Check every claim in the implementation report ${args.implReport} against the blind readers' evidence below and, where that is silent, against the transcripts and \`git -C ${args.projectRoot} log\`. A claim is a statement of fact: an outcome, a count, a test passing, a decision, a concern settled. Verdict per claim: confirmed, contradicted or unsupported, with evidence.

Readers' output (JSON): ${readsJson}

You are done when every claim in the report has a verdict.`,
    as('retro', { label: 'reconcile', phase: 'Synthesize', schema: RECON_SCHEMA })),
])

const contradicted = (reconciliation?.claims || []).filter(c => c.verdict === 'contradicted')

// 4. Remediation
const REMEDY_SCHEMA = {
  type: 'object',
  properties: {
    actions: { type: 'array', items: { type: 'object', properties: {
      finding: { type: 'string' },
      kind: { type: 'string', enum: ['diff', 'eval case', 'automated check', 'no action'] },
      target: { type: 'string' },
      detail: { type: 'string' },
    }, required: ['finding', 'kind', 'target', 'detail'] } },
    recurring: { type: 'array', items: { type: 'string' } },
  },
  required: ['actions', 'recurring'],
}

const remediation = await agent(`${ONLY_JOB}

Propose exactly one action per finding below. Contradicted self-report claims are findings too: give them ids R1, R2, … and class PROCESS unless the evidence says otherwise.
- diff: a change to a named skill, agent definition, template or CLAUDE.md, written as before and after. Say what text it removes, or why its added length pays for itself.
- eval case: the prompt, the fixture commit, and what counts as a pass.
- automated check: a hook, lint, test or schema rule, and where it lives.
- no action: model variance, with the reasoning.
Prefer an automated check to more prose. The target is a file path in ${args.projectRoot}, or "kentra-io/kentra-rpir: <file>" when the fix belongs in a kentra-rpir skill, agent, workflow or template.

Then compare with earlier retros and list recurring fault classes and findings: ${(args.priorRetros || []).join(', ') || '(no earlier retros)'}

Findings (JSON): ${JSON.stringify(synthesis?.findings || [])}
Contradicted claims (JSON): ${JSON.stringify(contradicted)}

You are done when every finding and every contradicted claim has one action.`,
  as('retro', { label: 'remediate', phase: 'Remediate', schema: REMEDY_SCHEMA }))

// 5. Write
await agent(`${ONLY_JOB}

Write ${args.out} following the template ${args.template}. Fill every section from the JSON below; do not add findings of your own. Write nothing else.

Header facts: change ${args.changeDir}; commits ${args.milestones.map(m => m.diffRange).join(', ')}; models ${JSON.stringify(Object.fromEntries(Object.entries(roles).map(([k, v]) => [k, v.model])))}; kentra-rpir ${args.versions?.plugin || 'unknown'}; Claude Code ${claudeCodeVersion}; sessions ${args.sessions.map(s => `${s.id}: ${s.stage}`).join(', ')}.

Readers (timelines): ${readsJson}
Synthesis: ${JSON.stringify(synthesis)}
Reconciliation: ${JSON.stringify(reconciliation)}
Remediation: ${JSON.stringify(remediation)}

You are done when ${args.out} has every template section filled.`,
  as('retro', { label: 'write', phase: 'Write' }))

return {
  out: args.out,
  findings: synthesis?.findings || [],
  contradicted,
  actions: remediation?.actions || [],
}
```

- [ ] **Step 4: Run the workflow checks**

Run: `node tests/check-workflows.mjs`
Expected: every line `ok`, then `all workflow checks passed`. If a `fixtures` assertion fails, fix the workflow rather than the fixture — the fixture states the design.

- [ ] **Step 5: Run all tests and commit**

```bash
sh tests/run.sh
git add workflows tests/check-workflows.mjs tests/workflow-fixtures.mjs
git commit -m "Research, self-review and spec-retro workflows with offline dry-run checks" -- workflows tests
```

### Shared conventions for the four skills (Tasks C8–C11)

Every skill below relies on these conventions. The text of each skill repeats the parts it uses, so a skill reads on its own.

- **Config:** `.kentra-rpir.yaml` lives at the project root. A missing key takes its value from `${CLAUDE_PLUGIN_ROOT}/templates/kentra-rpir.yaml`.
- **Finding the change:**
  1. The folder the user names.
  2. Otherwise, the folder this session is registered to in `.kentra-rpir/sessions.map`.
  3. Otherwise, the newest folder under `changesDir` whose `spec.md` status suits the stage. Confirm it with the user in one line.
- **Registering the session:**
  ```
  mkdir -p .kentra-rpir && echo "${CLAUDE_SESSION_ID} <change dir, relative to the project root> <stage>" >> .kentra-rpir/sessions.map
  ```
- **Capturing the session:**
  ```
  sh "${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh" --session "${CLAUDE_SESSION_ID}" --project "$(git rev-parse --show-toplevel)"
  ```
- **Running a workflow:** use the Workflow tool with `name` set to the configured workflow (for example `kentra-rpir:self-review`) and an `args` **object**. Workflows run in the background and notify on completion.
- **Fallbacks:** if Task C1 recorded any, apply them in every skill.

### Task C8: `skills/spec/SKILL.md`

**Files:**
- Create: `skills/spec/SKILL.md`

- [ ] **Step 1: Write the skill**

````markdown
---
name: spec
description: Turns an idea, bug or chore into an agreed spec.md for kentra-rpir — interactive questions in batches, background research checking every premise, an independent self-review. Use when starting a change, or when the user wants to write or revise a spec.
---

# spec — stage 1 of kentra-rpir

Turn the user's idea into `spec.md` in a new change folder, and keep going until
the user says the spec is right. This stage is a conversation. Your job is to
find out what the user means, check what the change assumes, and write it
down.

**Where things are:**
- the config is `.kentra-rpir.yaml` at the project root (defaults:
  `${CLAUDE_PLUGIN_ROOT}/templates/kentra-rpir.yaml`);
- the spec template is `${CLAUDE_PLUGIN_ROOT}/templates/spec.md`;
- the full process is `${CLAUDE_PLUGIN_ROOT}/docs/process.md`.

## Checklist

Make a task for each item and complete them in order.

1. Set up the project and the change folder
2. Explore the context
3. Ask, and record premises as they surface
4. Research premises in the background
5. Propose approaches for the open design choices
6. Write `spec.md`
7. Self-review
8. Agree, capture, commit

### 1. Set up

- **First use in this project** (no `.kentra-rpir.yaml`): ask these three
  questions in one message, then write the file from the template with the
  answers.
  - Where should change folders live? (default `docs/changes`)
  - Which issue tracker: none, GitHub or Jira?
  - Should the stages read `constitution/constitution.md`? Recommend yes when
    that file exists.

  Add `.kentra-rpir/` and the `worktreesDir` (default `.worktrees/`) to
  `.gitignore`.
- **Change folder:** `<changesDir>/<today>-<slug>/`. The slug is 2–4 kebab-case
  words. Copy the spec template into it as `spec.md`, with `status: draft`.
- **Register this session:**
  `mkdir -p .kentra-rpir && echo "${CLAUDE_SESSION_ID} <change folder> spec" >> .kentra-rpir/sessions.map`
- **Tracker:**
  - `github`: link the issue the user names, or create one with
    `gh issue create`.
  - `jira`: link the key the user gives.

  Put the link in the frontmatter `issue:`.

### 2. Explore the context

Read what this change touches: the README, the constitution when enabled, the
code in the area, and `git log --oneline -20`. Read enough to ask good
questions, and no more.

### 3. Ask

- Ask up to `spec.questionBatch` questions in one message when they do not
  depend on each other's answers. Ask one at a time when an answer changes what
  you ask next.
- Prefer multiple choice, and put your recommendation first.
- Cover the problem, the goals, what is out of scope, and how the user will
  know it works.
- **Premises.** A premise is anything the change assumes is true: how an API
  behaves, what a library version supports, what users do. Add each one to the
  spec's Premises table as `unverified` the moment it surfaces.

### 4. Research in the background

Start the research workflow (`research.workflow`) for each premise about an
external system. Run it in the background, with args:

```json
{ "today": "<YYYY-MM-DD>", "topics": [{ "name": "<the premise as a claim>", "focus": "<why the change depends on it>" }],
  "question": "Is this premise true for <change title>?", "outDir": "<abs change folder>/research",
  "collector": "<research.collector>", "reasoner": "<research.reasoner>" }
```

Keep talking while it runs. When a report arrives, set the premise's status
(`verified`, `refuted` or `unverified`) and source from its verdict. A
**refuted** premise goes to the user immediately: it usually changes the
design.

### 5. Propose approaches

For each design choice the conversation leaves open, give 2–3 options with
their trade-offs and your recommendation. Record the choice and the options not
taken in the Design section.

### 6. Write `spec.md`

Fill the template. The sections scale with the change: a bug's spec can be a
few lines. Requirements are plain, numbered, testable statements. Show the user
the spec section by section, and adjust as they react.

### 7. Self-review

Capture the session first, so the reviewer reads the conversation as it is:

```
sh "${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh" --session "${CLAUDE_SESSION_ID}" --project "$(git rev-parse --show-toplevel)"
```

Run the `kentra-rpir:self-review` workflow with:

```json
{ "today": "<YYYY-MM-DD>", "stage": "spec", "document": "<abs path>/spec.md", "projectRoot": "<abs>",
  "transcripts": ["<abs change folder>/sessions/<session-id>.jsonl"],
  "constitution": "<abs path of constitution/constitution.md, or null when disabled>",
  "reviewer": { "agent": "<roles.reviewer.agent>", "model": "<roles.reviewer.model>" },
  "research": { "workflow": "<research.workflow>", "collector": "<research.collector>", "reasoner": "<research.reasoner>" },
  "researchDir": "<abs change folder>/research" }
```

When it returns:

- **`technical` findings:** apply each `fix`. Tell the user in one line per
  fix, and add each as a line in the `<!-- writer-gaps -->` comment at the end
  of `spec.md`.
- **`decision` findings:** stop. Ask the user each `question` with its
  `options`, then edit the spec to match their answers.
- **`research` outcomes:** update the Premises table.

### 8. Agree

When the user says the spec is right:

1. Make sure Open questions is empty.
2. Set `status: agreed`.
3. Capture the session (the command above).
4. Commit the change folder:
   `git add <change folder> && git commit -m "spec: <title>" -- <change folder>`.

The stage is done when the spec is committed with `status: agreed`. Tell the user
the next stage is `kentra-rpir:plan`, preferably in a fresh session.

## Red flags

| Thought | Reality |
|---|---|
| "This premise is obviously true" | Then research will confirm it quickly. Start it. |
| "I'll ask all twelve questions at once" | Up to `spec.questionBatch`, and only questions that do not depend on each other. |
| "The user seems happy, I'll mark it agreed" | Only the user's explicit agreement sets `status: agreed`. |
| "Self-review flagged a decision, but I know the answer" | A decision finding goes to the user. |
````

- [ ] **Step 2: Commit**

```bash
git add skills/spec
git commit -m "spec skill" -- skills/spec
```

### Task C9: `skills/plan/SKILL.md`

**Files:**
- Create: `skills/plan/SKILL.md`

- [ ] **Step 1: Write the skill**

````markdown
---
name: plan
description: Turns an agreed kentra-rpir spec.md into a milestoned-plan-dag plan.yaml detailed enough that an implementer never explores the codebase — deeper research, parallel-safe milestones, rules by reference, an independent self-review. Use when a spec is agreed, or to revise a plan.
---

# plan — stage 2 of kentra-rpir

Turn `spec.md` into `plan.yaml` in the same change folder. The plan must be
detailed enough that an implementer — a cheaper model, in a fresh context,
holding one milestone — builds it correctly without exploring the codebase.
This stage is mostly hands-off: ask the user only about intent, never about
facts you can look up.

**Where things are:**
- the config is `.kentra-rpir.yaml` (defaults:
  `${CLAUDE_PLUGIN_ROOT}/templates/kentra-rpir.yaml`);
- the plan format is the `milestoned-plan-dag:plan-author` skill — load it now;
- the full process is `${CLAUDE_PLUGIN_ROOT}/docs/process.md`.

## Checklist

1. Read the spec, the constitution and the code
2. Research the details the plan depends on
3. Draft `plan.yaml`
4. Validate after every edit
5. Self-review
6. Present, agree, capture, commit

### 1. Read

1. Find the change. Its `spec.md` should be `status: agreed`; if it is not, say
   so and ask whether to continue.
2. Register this session:
   `echo "${CLAUDE_SESSION_ID} <change folder> plan" >> .kentra-rpir/sessions.map`.
3. Read `spec.md`, the constitution when enabled, and the code the change
   touches: the files the spec's design names, their tests, and the build file.

### 2. Research

List what the plan must state exactly and you do not know for certain:

- signatures of APIs the code will call;
- builder and method names;
- the versions to pin;
- the test framework's idioms in this repo.

Look up what the repo answers. Send the rest to the research workflow
(`research.workflow`, args as in the spec stage, `outDir` =
`<change folder>/research`). Any claim about a third-party API's behaviour gets
a spike.

### 3. Draft `plan.yaml`

Follow `plan-author`. Every milestone carries:

- **`description`** — why it exists and how it fits the others, plus the design
  decisions an implementer would otherwise have to make. The plan-level
  `description` holds what every milestone shares.
- **`deliverables`** — every file, and the type each introduces with its
  responsibility.
- **`steps`** — bite-sized and ordered. Each step that changes code shows the
  code; each step that runs a command shows the command and its expected
  result. Tests come before implementation.
- **`contract`** — the repo's standard `check`, criteria naming the test that
  proves each behaviour with given/when/then written out, and `paths`.
- **`rules`** — references only (a skill name or a repo path) to what the
  implementer must load and the verifier grades against. What every milestone
  needs goes in plan-level `rules`. Leave out `CLAUDE.md`/`AGENTS.md` and
  path-scoped `.claude/rules`, which load by themselves. Include the
  constitution path when it is enabled.

**Design for parallel work:**

- Milestones with no dependency path between them get non-overlapping `paths`.
- Shared types, shared files and version pins go in the earliest milestone
  that needs them.
- Every type that crosses a milestone boundary is named with its signature in
  the milestone that introduces it.

**Coverage:** every spec requirement lands in at least one milestone criterion.

### 4. Validate

Run `milestoned-plan-dag validate <change folder>/plan.yaml` after every edit.
Fix every error. Read every warning: an overlap warning between independent
milestones means adding a dependency or narrowing `paths`.

### 5. Self-review

Run the `kentra-rpir:self-review` workflow with `"stage": "plan"`,
`"document": "<abs>/plan.yaml"` and `"spec": "<abs>/spec.md"`. The other args
are as in the spec stage, without `transcripts`.

When it returns:

- **`technical` findings:** apply each `fix`, re-validate, and tell the user in
  one line per fix. Record each one under a top-level YAML comment block
  `# writerGaps:` at the end of `plan.yaml`.
- **`decision` findings:** stop and ask the user each `question` with its
  `options`.
- **Too many or irrelevant `rules`** (check P8): suggest to the user which skills
  to disable in the project's or user's settings.

### 6. Present and agree

1. Show the user the plan:
   - one line per milestone: number, goal, the tests it adds;
   - the DAG, as `milestoned-plan-dag resolve` orders it, marking which
     milestones can run in parallel;
   - every item self-review flagged.
2. On approval:
   - set the spec's `status: planned`;
   - capture the session:
     `sh "${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh" --session "${CLAUDE_SESSION_ID}" --project "$(git rev-parse --show-toplevel)"`;
   - commit the change folder.

The stage is done when `plan.yaml` validates, is committed, and the spec says
`status: planned`. The next stage is `kentra-rpir:implement`.

## Red flags

| Thought | Reality |
|---|---|
| "The implementer can look up the class names" | It gets one milestone. The names go in the milestone. |
| "`check: go test ./internal/foo/...`" | Use the repo's standard check. A narrowed check hides regressions. |
| "I'll copy the architecture skill's rules into the description" | `rules` lists the skill by name. Never copy. |
| "The spec doesn't settle this; I'll decide here" | That is a decision finding. Ask the user. |
| "It validates, so it's ready" | `validate` checks grammar, not whether an implementer could start. |
````

- [ ] **Step 2: Commit**

```bash
git add skills/plan
git commit -m "plan skill" -- skills/plan
```

### Task C10: `skills/implement/` — orchestrator skill and dispatch prompts

**Files:**
- Create: `skills/implement/SKILL.md`, `skills/implement/implementer-prompt.md`, `skills/implement/verifier-prompt.md`

- [ ] **Step 1: Write `skills/implement/implementer-prompt.md`**

The orchestrator fills each `{{slot}}` at dispatch time. These slots are runtime template variables, not unfinished parts of this plan.

````markdown
You are implementing milestone M{{number}} `{{slug}}` of the change "{{title}}".

**Work only in `{{worktree}}`**, on branch `{{branch}}`. Everything you need is
below. Do not open the plan file or the spec.

## Plan context (shared by every milestone)

{{plan_description}}

## Rules — load every one before writing code

{{rules}}
(Load a skill name with the Skill tool. Read a path.)

## Milestone

**Goal:** {{goal}}

{{milestone_description}}

**Deliverables:**
{{deliverables}}

**Steps:**
{{steps}}

**Contract:**
- check: `{{check}}`
- criteria:
{{criteria}}
- paths (your write-set; nothing outside it): {{paths}}

{{guidance}}

## When you are done

Commit on `{{branch}}` once `{{check}}` passes. Report in the format your agent
definition gives: STATUS, COMMITS, CHECK, then CONCERNS, QUESTION, BLOCKER or
OBSERVED as they apply.
````

- [ ] **Step 2: Write `skills/implement/verifier-prompt.md`**

````markdown
You are verifying milestone M{{number}} `{{slug}}` of the change "{{title}}",
in `{{worktree}}` on branch `{{branch}}`. The base to diff against is
`{{base}}`. You did not write this code.

## Rules to grade against

{{rules}}

## Milestone

**Goal:** {{goal}}

{{milestone_description}}

**Deliverables:**
{{deliverables}}

**Steps:**
{{steps}}

**Contract:**
- check: `{{check}}`
- criteria:
{{criteria}}
- paths: {{paths}}

Run your checks — check command, criteria, paths
(`git diff --name-only {{base}}...HEAD`), intent, rules — and report VERDICT,
CHECK and FINDINGS in the format your agent definition gives.
````

- [ ] **Step 3: Write `skills/implement/SKILL.md`**

````markdown
---
name: implement
description: Orchestrates the implementation of a kentra-rpir plan.yaml — parallel milestones in git worktrees, an implementer and a fresh verifier per milestone, merges into an integration branch, an escalation ladder, and impl-report.md. Use when a plan is approved and the user wants it built.
---

# implement — stage 3 of kentra-rpir

**You are the orchestrator.** Build the plan by dispatching an implementer and a
fresh verifier per milestone, running independent milestones in parallel, each
in its own worktree. Your judgement does the following:

- unblocking implementers;
- resolving merge conflicts;
- adjusting the plan without changing the spec;
- deciding what goes to the user.

The protocol below is fixed: DAG order, a fresh verifier per milestone, and the
escalation ladder.

**Where things are:**
- the config is `.kentra-rpir.yaml` (defaults:
  `${CLAUDE_PLUGIN_ROOT}/templates/kentra-rpir.yaml`);
- the dispatch prompts are `${CLAUDE_SKILL_DIR}/implementer-prompt.md` and
  `${CLAUDE_SKILL_DIR}/verifier-prompt.md`;
- the report template is `${CLAUDE_PLUGIN_ROOT}/templates/impl-report.md`.

If this session is not running on `roles.impl-orchestrator.model`, tell the
user before starting and let them choose to continue or switch models.

Stay in the project root for the whole stage. Reach into worktrees with
`git -C <worktree>` and absolute paths.

## 1. Start

1. Find the change; its spec should say `status: planned`.
2. Validate the plan:
   `milestoned-plan-dag validate <plan>`, then
   `milestoned-plan-dag resolve <plan>`. The resolve output is your milestone
   list and order.
3. Create the integration worktree from the current branch:
   `git worktree add <worktreesDir>/<slug> -b <branchPrefix><slug>`.
4. Register this session to the change folder **inside the integration
   worktree**, so its transcript is committed with the implementation:
   `echo "${CLAUDE_SESSION_ID} <worktreesDir>/<slug>/<change folder> implement" >> .kentra-rpir/sessions.map`.
5. In the integration worktree:
   - set the spec's `status: implementing`;
   - commit;
   - run the plan's `check` once, so you know the baseline is green.

Keep a running log for `impl-report.md`: dispatches, verify rounds, FAILs,
concerns, adjustments, decisions and conflicts.

## 2. Dispatch implementers

For every milestone whose dependencies are all merged and which is not yet
started:

1. Create its worktree **from the integration branch's current head**:
   `git worktree add <worktreesDir>/<slug>--m<n>-<mslug> -b <branchPrefix><slug>--m<n> <branchPrefix><slug>`.
2. Fill `implementer-prompt.md`:
   - `plan_description` is the plan's `description`;
   - `rules` is the plan-level rules followed by the milestone's;
   - the milestone fields come from the resolve output;
   - `guidance` is empty on the first dispatch.
3. Dispatch with the Agent tool:
   - `subagent_type`: `roles.implementer.agent`;
   - `model`: `roles.implementer.model`;
   - `cwd`: the milestone worktree;
   - the filled prompt.

   Dispatch every ready milestone in the same message so they run in parallel.

Handle the report:

| STATUS | You do |
|---|---|
| `DONE` | Verify (step 3). |
| `DONE_WITH_CONCERNS` | Read the concerns. Settle those about correctness or scope (re-dispatch with guidance, or decide per the table below), then verify. |
| `NEEDS_CONTEXT` | Find the answer and re-dispatch with it in `guidance`. |
| `BLOCKED` | Decide per the table below: re-dispatch with guidance, adjust the plan, or ask the user. |

## 3. Verify

Dispatch a **new** verifier for every finished milestone, never reusing the
implementer's context:

- `subagent_type`: `roles.verifier.agent`, `model`: `roles.verifier.model`;
- `cwd`: the milestone worktree;
- the prompt: `verifier-prompt.md` filled, with `base` = the integration-branch
  commit the milestone branched from.

- **PASS** → integrate (step 4).
- **FAIL** → re-dispatch the implementer with the findings as `guidance`, then
  verify again with a new verifier. After `implement.maxVerifyRounds` FAILs,
  treat the milestone as blocked.

## 4. Integrate

On PASS:

1. Merge in the integration worktree:
   `git -C <worktreesDir>/<slug> merge --no-ff <milestone branch> -m "Merge M<n> <mslug>"`.
2. Resolve any conflict yourself. Both sides' intent is in the two milestones;
   keep both, and record the resolution.
3. Run the plan's `check` in the integration worktree. If it fails, the merge
   broke something: fix it on the integration branch, or re-dispatch the
   milestone with the failure.
4. Clean up:
   `git worktree remove <milestone worktree>` and `git branch -d <milestone branch>`.
5. Dispatch every milestone this one unblocked (step 2).

## Decide or ask

The implementer follows the milestone exactly. You unblock it, and you never let
it deviate silently.

| You resolve | You ask the user |
|---|---|
| Missing context: supply it | A change to a requirement, or to behaviour the spec describes |
| A technical contradiction with one clear answer | A trade-off the spec did not settle |
| Merge conflicts between milestones | A conflict with the constitution |
| Flaky or environment failures you can fix | Dropping or deferring a milestone, or any other change to scope |
| Plan adjustments that keep the spec intact: renaming a file, reordering or adding a step, splitting a milestone, a different patch version | A new dependency, or an API or contract other code relies on that the plan did not name |
| | A blocker still unresolved after two re-dispatches |

**Escalation ladder, per blocker:**

- Allow two re-dispatches, each with new guidance or context. Never re-send a
  failing dispatch unchanged.
- Then stop and ask the user (`implement.maxAttempts`).
- Milestones that do not depend on the blocked one keep running while you wait.

**Plan adjustments:**

1. Edit `plan.yaml` on the integration branch.
2. Validate it and commit it, with the reason in the commit message.
3. Log the adjustment.

Anything that would change `spec.md` goes to the user.

## 5. Finish

When every milestone is merged, or blocked and settled with the user:

1. Write `impl-report.md` in the change folder in the integration worktree,
   from the template and your log. Report what happened, including FAILs and
   concerns. The retro checks every claim in it against the transcripts.
2. Set the spec's `status: in-review`.
3. Capture this session:
   `sh "${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh" --session "${CLAUDE_SESSION_ID}" --project "$(git rev-parse --show-toplevel)"`.
4. Commit the change folder on the integration branch.
5. Push:
   `git -C <worktreesDir>/<slug> push -u origin <branchPrefix><slug>`.
6. When `implement.openPullRequest` is true, the remote is GitHub, and
   `gh auth status` succeeds: open a pull request whose body links the spec,
   the plan and `impl-report.md`.

The stage is done when the integration branch is pushed with `impl-report.md`
and the spec says `status: in-review`. The next stage is `kentra-rpir:review`.
Leave the integration worktree in place.

## Red flags

| Thought | Reality |
|---|---|
| "The implementer said DONE, I'll merge" | Every milestone passes a fresh verifier first. |
| "I'll reuse the implementer to verify" | A new verifier, every round. |
| "Same prompt again, it might work this time" | Each re-dispatch carries new guidance or context. |
| "The spec is slightly wrong; I'll fix it in the plan" | Anything that changes the spec goes to the user. |
| "I'll leave the FAIL out of the report" | The retro reads the transcripts. Report it. |
````

- [ ] **Step 4: Commit**

```bash
git add skills/implement
git commit -m "implement skill: worktree orchestrator with implementer and verifier prompts" -- skills/implement
```

### Task C11: `skills/review/SKILL.md`

**Files:**
- Create: `skills/review/SKILL.md`

- [ ] **Step 1: Write the skill**

````markdown
---
name: review
description: Final stage of a kentra-rpir change — presents the implementation for the user's code review while the spec-retro workflow reads the session transcripts, then walks through the retro's actions with the user. Use after implement, or to run a retro on a finished change.
---

# review — stage 4 of kentra-rpir

Review has two halves, and the user does both: they review the code, and they
review the process through the retro. Start the retro first, because it runs in
the background while the code review happens.

**Where things are:**
- the config is `.kentra-rpir.yaml` (defaults:
  `${CLAUDE_PLUGIN_ROOT}/templates/kentra-rpir.yaml`);
- the retro template is `${CLAUDE_PLUGIN_ROOT}/templates/spec-retro.md`;
- the plugin version is `version` in `${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json`.

## 1. Start

1. Find the change; its spec should say `status: in-review`.
2. Work in the change folder inside the integration worktree
   (`<worktreesDir>/<slug>/`) when it exists, or in the main checkout once the
   branch is merged.
3. Register this session to that folder with stage `review`.

## 2. Launch the retro

Gather the args:

- **`sessions`:** each line of `<change folder>/sessions/index`
  (`<id> <stage>`) gives `{ id, stage, path: "<abs change folder>/sessions/<id>.jsonl" }`.
- **`milestones`:** for each milestone in `plan.yaml`, `{ number, slug, paths }`
  plus a `diffRange` of `<merge>^1..<merge>`, where `<merge>` is the merge
  commit listed in `impl-report.md`'s Milestones table. Reading the report's
  table for commits is fine; the readers never see the report.
- **`priorRetros`:** every other `<changesDir>/*/spec-retro.md`.

Run the `kentra-rpir:spec-retro` workflow in the background with:

```json
{ "today": "<YYYY-MM-DD>", "changeDir": "<abs>", "projectRoot": "<abs checkout holding the change>",
  "spec": "<abs>/spec.md", "plan": "<abs>/plan.yaml", "implReport": "<abs>/impl-report.md",
  "out": "<abs>/spec-retro.md", "template": "${CLAUDE_PLUGIN_ROOT}/templates/spec-retro.md",
  "sessions": [...], "milestones": [...], "priorRetros": [...],
  "roles": { "prepass": <roles.prepass>, "reader": <roles.reader>, "retro": <roles.retro> },
  "versions": { "plugin": "<plugin version>" } }
```

## 3. Code review

While the retro runs, present the change:

- the pull request link, or the branch and `git diff --stat <base>...<branch>`;
- one line per milestone: its outcome, and what it added;
- the verifier findings and concerns from `impl-report.md`, plan adjustments,
  and blocked milestones;
- anything in the report that needs the user's decision.

Fix what the user asks for on the integration branch — directly for small
changes, or by dispatching an implementer and a fresh verifier as the implement
stage does. Run `check` after each fix.

## 4. Retro review

When the workflow finishes:

1. Present `spec-retro.md`: the outcome, the contradicted self-report claims,
   and the findings with their fault classes.
2. Go through the Actions with the user, one at a time. They choose one of:
   - **Apply:** the target is in this repo. Make the change and commit it.
   - **File:** the target is a kentra-rpir skill, agent, workflow or template.
     Run `gh issue create --repo kentra-io/kentra-rpir`, with the finding, its
     evidence and the proposed action in the body.
   - **Defer** or **reject:** with the user's reason.
3. Record each choice in the Decision column.

## 5. Finish

When the user has finished both reviews:

1. Set the spec's `status: done`.
2. Capture the session:
   `sh "${CLAUDE_PLUGIN_ROOT}/hooks/capture-session.sh" --session "${CLAUDE_SESSION_ID}" --project "$(git rev-parse --show-toplevel)"`.
3. Commit the change folder, and push when on the integration branch.

Merging the pull request is the user's call. Once it is merged, remove the
integration worktree:
`git worktree remove <worktreesDir>/<slug>`.

The stage is done when the spec says `status: done` and every retro action has a
decision.
````

- [ ] **Step 2: Commit**

```bash
git add skills/review
git commit -m "review skill" -- skills/review
```

### Task C12: README, process doc and CI

**Files:**
- Modify: `README.md` (full replacement)
- Create: `docs/process.md`
- Modify: `.github/workflows/ci.yml` (full replacement)

- [ ] **Step 1: Write `README.md`**

````markdown
# kentra-rpir

A Claude Code plugin that runs one change — a feature, a bug or a chore — from
idea to merged code, in four stages:

| Stage | Skill | You | It writes |
|---|---|---|---|
| **spec** | `/kentra-rpir:spec` | answer questions until you agree | `spec.md` |
| **plan** | `/kentra-rpir:plan` | approve | `plan.yaml` |
| **implement** | `/kentra-rpir:implement` | settle what the orchestrator asks | code, `impl-report.md` |
| **review** | `/kentra-rpir:review` | review the code and the retro | `spec-retro.md` |

It is RPI (Research → Plan → Implement) with an explicit Review stage, for a solo
founder:

- **Files are the handoff.** Each stage reads the previous stage's file, not
  its conversation.
- **Checked against the source.** An independent reviewer checks the spec and
  the plan against the conversation, the spec, the project's rules and its
  constitution. Research agents check every premise against primary sources
  and spikes.
- **Parallel, verified implementation.** Milestones from the plan's DAG run in
  parallel git worktrees. Each one is checked by a fresh verifier before it
  merges.
- **The process records itself.** Every session's transcript is copied into the
  change folder (credentials redacted). The retro reads the transcripts, not the
  agents' account of their work.

Everything for one change lives in `docs/changes/<date>-<slug>/`. See
[`docs/process.md`](docs/process.md) for the whole process.

## Install

```
/plugin marketplace add kentra-io/kentra-agentic-plugins
/plugin install kentra-rpir@kentra-agentic-plugins
brew install kentra-io/tap/milestoned-plan-dag
```

Optional: [`adr-sourced-constitution`](https://github.com/kentra-io/adr-sourced-constitution)
for a project constitution that every stage reads.

## Configure

The first `/kentra-rpir:spec` in a project writes `.kentra-rpir.yaml`. Models and
agents per role, question batch size, worktree location and research settings
live there — see [`templates/kentra-rpir.yaml`](templates/kentra-rpir.yaml).

## History

This repository was `spec-lifecycle`: a gated, OpenSpec-format lifecycle with a
Go CLI. Its last release is v0.2.0; the history is in git.

## License

MIT
````

- [ ] **Step 2: Write `docs/process.md`**

```bash
cp ../kentra-rpir.md docs/process.md
```

Then edit `docs/process.md`:

1. Delete the `**Status:**` line (line 3) and the blank line after it.
2. Delete the whole `## Changes in other repositories` section, up to the next
   `## ` heading.
3. In `## Sources`, replace each relative link
   `references/<file>.md` with
   `https://github.com/kentra-io/harness/blob/main/references/<file>.md`.

Run: `grep -n "Status:\|Changes in other\|](references/" docs/process.md`
Expected: no output.

- [ ] **Step 3: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

jobs:
  test:
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-latest]   # GNU and BSD sed both run the hook
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-node@v4
        with:
          node-version: "22"
      - run: sh tests/run.sh
      - if: matrix.os == 'ubuntu-latest'
        run: shellcheck -s sh hooks/capture-session.sh tests/*.sh
```

- [ ] **Step 4: Run everything locally**

```bash
sh tests/run.sh
shellcheck -s sh hooks/capture-session.sh tests/*.sh || brew install shellcheck && shellcheck -s sh hooks/capture-session.sh tests/*.sh
```

Expected: all tests pass; shellcheck prints nothing. Fix every shellcheck finding in the hook, and re-run the tests after each fix.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/process.md .github/workflows/ci.yml
git commit -m "README, process doc and CI for kentra-rpir" -- README.md docs/process.md .github/workflows/ci.yml
```

### Task C13: dogfood smoke test (with the user)

A real run of all four stages on a throwaway project. It is the only test of the skills themselves, so do it with the user at the keyboard. They answer the spec questions and approve.

**Files:**
- Create: `$SCRATCH/rpir-smoke/` (throwaway project; never committed anywhere)

- [ ] **Step 1: Build the throwaway project**

```bash
mkdir -p "$SCRATCH/rpir-smoke" && cd "$SCRATCH/rpir-smoke" && git init -q
cat > greet.sh <<'SH'
#!/bin/sh
# greet.sh NAME — prints a greeting.
printf 'Hello, %s\n' "$1"
SH
cat > test.sh <<'SH'
#!/bin/sh
set -e
[ "$(sh greet.sh Ada)" = "Hello, Ada" ]
echo "all tests passed"
SH
printf '# greet\n\nRun `sh test.sh` to test.\n' > README.md
git add . && git commit -q -m "greet.sh"
```

- [ ] **Step 2: Run the four stages**

Ask the user to start `claude --plugin-dir /Users/jony/code/kentra/harness/spec-lifecycle` in `$SCRATCH/rpir-smoke`, then run the following, each in a fresh session:

1. `/kentra-rpir:spec add a --shout flag that upper-cases the greeting, and a --polite flag that appends ", please"`. Answer the questions; constitution: no; tracker: none. Agree.
2. `/kentra-rpir:plan`. Ask for two independent milestones (one per flag) so the parallel path runs. Approve.
3. `/kentra-rpir:implement`.
4. `/kentra-rpir:review`.

- [ ] **Step 3: Check what each stage left behind**

```bash
cd "$SCRATCH/rpir-smoke"
c=$(ls -d docs/changes/*/ | head -n 1)
cat .kentra-rpir.yaml; cat .kentra-rpir/sessions.map
grep -q '^status: done' "$c/spec.md" && echo spec-done
milestoned-plan-dag validate "$c/plan.yaml" && echo plan-valid
test -f "$c/impl-report.md" && test -f "$c/spec-retro.md" && echo reports
cat "$c/sessions/index"; ls "$c/sessions"
grep -c REDACTED "$c"/sessions/*.jsonl || true
git log --oneline --graph --all | head -30
git worktree list
```

Expected:

- `spec-done`, `plan-valid` and `reports` are printed.
- `sessions/index` lists sessions for all four stages, and `sessions/` holds their transcripts and `subagents/` directories.
- The git graph shows two milestone branches merged into `rpir/<slug>`.
- `spec-retro.md` has every template section filled.

- [ ] **Step 4: Fix what the smoke test exposed**

For each problem: fix the skill, workflow, hook or template in the plugin repo, and bump `version` in `.claude-plugin/plugin.json` (0.3.0 → 0.3.1 → …). Commit each fix separately, naming the problem. Re-run the stage that failed. Note each fix for the PR body.

You are done with this task when one run passes all four stages and every check in Step 3 without manual repair.

### Task C14: open the pull request

- [ ] **Step 1: Push and open the PR**

```bash
cd /Users/jony/code/kentra/harness/spec-lifecycle
sh tests/run.sh
git push -u origin kentra-rpir
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr create --title "kentra-rpir: replace spec-lifecycle with the RPIR plugin" --body "Replaces the gated spec-lifecycle CLI with kentra-rpir, a Claude Code plugin that runs spec → plan → implement → review. Design: kentra-io/harness \`kentra-rpir.md\` (copied to docs/process.md).

- Deleted: Go CLI, OpenSpec engine, gated lifecycle-* skills, release tooling.
- Added: 4 skills, 4 agents, 3 workflows (research, self-review, spec-retro), session-capture hook with redaction, templates, offline workflow dry-run tests.
- Smoke-tested end to end on a throwaway project: <list the fixes from Task C13, or 'no fixes needed'>.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

- [ ] **Step 2: 🔒 Merge** — ask the user, then:

```bash
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr merge --squash --delete-branch
git switch main && git pull --ff-only
```

---

# Part D — outward actions (🔒 every step)

Run after Part C is merged. Each step changes something other people can see, so ask the user before each one.

### Task D1: rename the GitHub repository

- [ ] **Step 1: 🔒 Rename and describe**

```bash
export GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w)
command gh repo rename kentra-rpir --repo kentra-io/spec-lifecycle --yes
command gh repo edit kentra-io/kentra-rpir --description "Claude Code plugin: spec → plan → implement → review for one change, with parallel verified milestones and transcript-based retros" --add-topic claude-code --add-topic rpi --add-topic agentic-coding
command gh repo view kentra-io/kentra-rpir --json name,description -q '.name + ": " + .description'
unset GH_TOKEN
```

Expected: `kentra-rpir: Claude Code plugin: …`. GitHub redirects the old URL, so existing clones keep working until Part F re-points the submodule.

### Task D2: remove the `lifecycle` cask

- [ ] **Step 1: 🔒 Remove it from the tap**

```bash
git clone git@github.com:kentra-io/homebrew-tap.git "$SCRATCH/homebrew-tap"
cd "$SCRATCH/homebrew-tap"
git rm Casks/lifecycle.rb
grep -rn "lifecycle" README.md || true
```

If the tap's README lists `lifecycle`, delete that line or row. Then:

```bash
git commit -m "Remove the lifecycle cask: spec-lifecycle is now the kentra-rpir plugin, which has no CLI" -- Casks/lifecycle.rb README.md
git push origin main
```

- [ ] **Step 2: 🔒 Uninstall locally** — ask the user, then: `brew uninstall lifecycle`.

---

# Part E — kentra-agentic-plugins catalog

Repository: `/Users/jony/code/kentra/harness/kentra-agentic-plugins`. Start: `git switch main && git pull --ff-only && git switch -c kentra-rpir`.

### Task E1: replace the spec-lifecycle and agent-orchestration entries

**Files:**
- Modify: `.claude-plugin/marketplace.json`
- Modify: `README.md` (section "Catalog status" and "Companion CLIs")

- [ ] **Step 1: Edit `marketplace.json`**

1. Replace the whole `"spec-lifecycle"` plugin object with the object below. It has no `skills` key, so Claude Code discovers the plugin's own `skills/`, `agents/`, `workflows/` and `hooks/`.

   ```json
   {
     "name": "kentra-rpir",
     "source": { "source": "github", "repo": "kentra-io/kentra-rpir" },
     "description": "Runs one change through spec → plan → implement → review: interactive spec with background premise research, a milestoned-plan-dag plan an implementer follows without exploring, parallel milestones in git worktrees with a fresh verifier each, and a retro read from the session transcripts. Needs the milestoned-plan-dag CLI.",
     "license": "MIT",
     "category": "sdlc",
     "keywords": ["rpi", "rpir", "spec", "plan", "implement", "review", "retro", "worktrees", "subagents"]
   }
   ```

2. Delete the whole `"agent-orchestration-skills"` plugin object.
3. In the `"milestoned-plan-dag"` description, replace "the mandatory check/criteria/path contract, checkbox steps —" with "the mandatory check/criteria/paths contract, plan- and milestone-level description and rules, multi-line checkbox steps —".

Run: `jq -r '.plugins[].name' .claude-plugin/marketplace.json`
Expected: `adr-sourced-constitution`, `kentra-rpir`, `milestoned-plan-dag`, `kentra-skills`.

- [ ] **Step 2: Edit `README.md`**

In the "Catalog status" table:

- replace the `spec-lifecycle` row with:
  ```markdown
  | `kentra-rpir` | Live (v0.3) — spec → plan → implement → review for one change; parallel verified milestones in worktrees; transcript-based retros. Replaces `spec-lifecycle`. |
  ```
- delete the `agent-orchestration-skills` row.

Replace the "Companion CLIs" section body with:

````markdown
Two plugins are the agent-facing half of a primitive whose other half is a CLI.
`kentra-rpir` needs `milestoned-plan-dag`; `constitution` is optional:

```
brew install kentra-io/tap/milestoned-plan-dag kentra-io/tap/constitution
```
````

Run: `grep -n "spec-lifecycle\|lifecycle\|agent-orchestration" README.md .claude-plugin/marketplace.json`
Expected: only the "Replaces `spec-lifecycle`" mention.

- [ ] **Step 3: Check that the catalog installs**

In a fresh Claude Code session in `$SCRATCH`:

```
/plugin marketplace add /Users/jony/code/kentra/harness/kentra-agentic-plugins
/plugin install kentra-rpir@kentra-agentic-plugins
```

Expected: the install succeeds, and `/kentra-rpir:spec`, `/kentra-rpir:plan`, `/kentra-rpir:implement` and `/kentra-rpir:review` appear in the slash-command list. Then remove the local marketplace again (`/plugin marketplace remove kentra-agentic-plugins`) and re-add the GitHub one if it was registered before.

- [ ] **Step 4: Commit, push, PR, 🔒 merge**

```bash
git commit -m "Catalog: kentra-rpir replaces spec-lifecycle; drop agent-orchestration-skills" -- .claude-plugin/marketplace.json README.md
git push -u origin kentra-rpir
GH_TOKEN=$(security find-generic-password -a kentra-gh-bot -s kentra-gh-token -w) command gh pr create --title "kentra-rpir replaces spec-lifecycle" --body "Lists kentra-rpir in place of spec-lifecycle, removes agent-orchestration-skills, and documents milestoned-plan-dag v0.2.0's new fields.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

Ask the user before merging; then `gh pr merge --squash --delete-branch` (with the bot token), `git switch main && git pull --ff-only`.

---

# Part F — harness

Repository: `/Users/jony/code/kentra/harness`. Run after Parts A–E are merged. The harness already has uncommitted changes that belong to the user. Commit **only** the paths each task names, using `git commit -- <paths>`. Never stage or commit:

- `kentra-skills`;
- `references/technologies.md`;
- `roadmap-ideas.md`, unless Task F3 adds to it with the user's agreement.

### Task F1: re-point the spec-lifecycle submodule as `kentra-rpir`

- [ ] **Step 1: Confirm nothing local would be lost**

```bash
git -C spec-lifecycle status --short
git -C spec-lifecycle fetch -q && git -C spec-lifecycle log --oneline origin/main..HEAD
ls -A spec-lifecycle/.worktrees
```

Expected: no output from the first two, and `.worktrees` is empty. If any of them prints something, stop and show it to the user.

- [ ] **Step 2: Replace the submodule**

```bash
git submodule deinit -f spec-lifecycle
git rm -q -f spec-lifecycle
rm -rf .git/modules/spec-lifecycle
git submodule add -b main git@github.com:kentra-io/kentra-rpir.git kentra-rpir
git -C kentra-rpir log --oneline -1
grep -n -A3 'kentra-rpir' .gitmodules
```

Expected: the head is the merged Part C commit. `.gitmodules` has a `[submodule "kentra-rpir"]` section with `url = git@github.com:kentra-io/kentra-rpir.git` and `branch = main`, and no `spec-lifecycle` section.

- [ ] **Step 3: Commit**

```bash
git commit -m "Replace the spec-lifecycle submodule with kentra-rpir (renamed repo)" -- .gitmodules spec-lifecycle kentra-rpir
```

### Task F2: remove the agent-orchestration submodule

- [ ] **Step 1: Confirm nothing local would be lost**

```bash
git -C agent-orchestration status --short
git -C agent-orchestration fetch -q && git -C agent-orchestration log --oneline origin/main..HEAD
```

Expected: no output. Otherwise stop and show the user.

- [ ] **Step 2: Ask about the running daemon**

Ask the user whether the host orchestrator daemon and its boxes should be stopped (`orch daemon status`). Do not stop them unasked. Removing the submodule does not touch them.

- [ ] **Step 3: Remove**

```bash
git submodule deinit -f agent-orchestration
git rm -q -f agent-orchestration
rm -rf .git/modules/agent-orchestration
git commit -m "Remove the agent-orchestration submodule: execution is kentra-rpir's implement stage" -- .gitmodules agent-orchestration
```

### Task F3: delete the superseded design docs

- [ ] **Step 1: Delete**

The user decided (2026-10-11) to delete `tasks/orchestration-runtime-handoff.md` with the rest, uncommitted edits and Stage-4/5 sections included — no extra checks.

```bash
git rm -q -f orchestration.md orchestration-implementation-plan.md tasks/orchestration-runtime-handoff.md agent-definition.md planning.md mvp-plan.md fabro-vs-conductor-evaluation.md workflow-orchestration-analysis.md
```

- [ ] **Step 2: Fix the links to them**

Run:

```bash
grep -rn --exclude-dir=.git --exclude-dir=kentra-rpir --exclude-dir=milestoned-plan-dag --exclude-dir=adr-sourced-constitution --exclude-dir=kentra-agentic-plugins --exclude-dir=kentra-skills --exclude-dir=kafka-dq --exclude-dir=docs \
  -e 'orchestration\.md' -e 'orchestration-implementation-plan\.md' -e 'orchestration-runtime-handoff\.md' -e 'agent-definition\.md' -e 'planning\.md' -e 'mvp-plan\.md' -e 'fabro-vs-conductor-evaluation\.md' -e 'workflow-orchestration-analysis\.md' .
```

Fix every hit outside `AGENTS.md`, `README.md`, `CLAUDE.md`, `kentra-sdlc.md` and `tasks/todo.md` (Task F5 rewrites those):

- **`references/*.md`, `tasks/retro-archive/*.md`, `observability.md`, `roadmap-ideas.md`** — historical documents. Replace the markdown link `[text](path)` with `text (deleted 2026-10-11; see git history)`. Leave the rest of the sentence alone.
- **`.claudebox/Dockerfile`** — Task F4 rewrites the comment.

Run the grep again. Expected: hits only in the five files Task F5 rewrites, and in `.claudebox/Dockerfile`.

### Task F4: claudebox Dockerfile and config

**Files:**
- Modify: `.claudebox/Dockerfile`, `.claudebox/config.yaml`

- [ ] **Step 1: Edit the Dockerfile**

1. Replace the uv comment block (`# --- uv: Python toolchain for the agent-orchestration module …`, through the line before `RUN curl -LsSf https://astral.sh/uv/install.sh`) with:
   ```dockerfile
   # --- uv: Python toolchain. cb itself is not baked in: build it on demand
   # from the claudebox checkout (`go build -o ~/.local/bin/cb .`).
   ```
2. Delete the whole `# --- orch: …` block: its comments, `ARG ORCH_REF=…`, and the `RUN … uv tool install … agent-orchestration … && orch --help >/dev/null`.
3. In the constitution comment, replace `(see\n# spec-lifecycle/docs/releasing.md)` with `(see\n# adr-sourced-constitution/docs/releasing.md)`. Set `ARG CONSTITUTION_VERSION=0.3.2`.
4. Delete the whole `# lifecycle (spec-lifecycle CLI): …` block: its comments, `ARG LIFECYCLE_REF=…`, and its `RUN`.
5. Replace the milestoned-plan-dag comment block with:
   ```dockerfile
   # milestoned-plan-dag: BUILT FROM SOURCE at a commit pin, so the box can
   # dogfood unreleased work; consumers outside the box install the release
   # (`brew install kentra-io/tap/milestoned-plan-dag`). kentra-rpir's plan and
   # implement stages call it.
   # NOTE: keep MPD_REF in sync with the harness's milestoned-plan-dag submodule
   # pointer (`git -C milestoned-plan-dag rev-parse HEAD`) whenever it bumps.
   ```
   Set `ARG MPD_REF=` to the output of `git -C milestoned-plan-dag rev-parse origin/main` after `git -C milestoned-plan-dag fetch -q`, which is the merged Part A.

Run:

```bash
grep -n -i "orch\|lifecycle\|planning\.md\|orchestration" .claudebox/Dockerfile
```

Expected: no output.

- [ ] **Step 2: Edit `.claudebox/config.yaml`**

Delete:

- the comment block that opens with `# Opt this project's boxes into the host agent-orchestration daemon`;
- the `ORCHESTRATION_DAEMON_URL` and `ORCHESTRATION_DAEMON_TOKEN` entries;
- the comment at line 39 (`# Opt-in for the orchestration build …`) **and the setting it documents**. First show those lines to the user, and delete them only if they serve only the orchestration build.

Run: `grep -n -i "orch" .claudebox/config.yaml` — Expected: no output.

- [ ] **Step 3: Bump the submodule pointers in lockstep**

```bash
git -C milestoned-plan-dag switch -q main && git -C milestoned-plan-dag pull -q --ff-only
git -C adr-sourced-constitution switch -q main && git -C adr-sourced-constitution pull -q --ff-only
git -C kentra-agentic-plugins switch -q main && git -C kentra-agentic-plugins pull -q --ff-only
test "$(git -C milestoned-plan-dag rev-parse HEAD)" = "$(sed -n 's/^ARG MPD_REF=//p' .claudebox/Dockerfile)" && echo mpd-in-lockstep
git -C adr-sourced-constitution describe --tags --exact-match HEAD
```

Expected: `mpd-in-lockstep`, and `v0.3.2`. If the constitution head is past the tag, check out `v0.3.2` in the submodule (the Dockerfile pins the release).

- [ ] **Step 4: Commit**

```bash
git commit -m "Box: drop orch and lifecycle; MPD_REF and CONSTITUTION_VERSION in lockstep with the submodules" -- .claudebox/Dockerfile .claudebox/config.yaml milestoned-plan-dag adr-sourced-constitution kentra-agentic-plugins
```

- [ ] **Step 5: Rebuild and verify the box (the user runs it)**

Ask the user to rebuild from a fresh shell (`cb build`, then `cb rm && cb run`). Inside the box:

- `command -v lifecycle orch` prints nothing;
- `constitution --version` prints 0.3.2;
- `milestoned-plan-dag --version` runs.

### Task F5: rewrite the harness docs

**Files:**
- Modify: `AGENTS.md`, `CLAUDE.md`, `README.md`, `kentra-sdlc.md`, `tasks/todo.md`

- [ ] **Step 1: `AGENTS.md`**

- Replace the `spec-lifecycle/` bullet with:
  ```markdown
  - [`kentra-rpir/`](./kentra-rpir/) — the **process** plugin (formerly `spec-lifecycle`): one change runs spec → plan → implement → review. Interactive spec with background premise research; a `milestoned-plan-dag` plan detailed enough that an implementer never explores; parallel milestones in `.worktrees/` with a fresh verifier each, orchestrated by the main session; a retro read from the committed session transcripts. No CLI and no gates. The only machine validation is the plan schema. Design: [`kentra-rpir.md`](./kentra-rpir.md).
  ```
- Delete the `agent-orchestration/` bullet and the "Planned (Stage 3, not yet a repo)" `agent-definition` bullet.
- In the `milestoned-plan-dag/` bullet, replace "mirroring the executor's one-run-one-repo constraint (committed by design — `agent-orchestration` §1/§13, its ADR-0004; its #24 closed)" with "matching kentra-rpir's one-repo worktrees". Replace "**Shipped — v0.1.0**" with "**Shipped — v0.2.0** (plan- and milestone-level `description` and `rules`)".
- In "Neutral mechanism, branded methodology", replace "the OpenSpec schema `kentra-spec-lifecycle`, and the umbrella methodology" with "the `kentra-rpir` process plugin, and the umbrella methodology".
- Replace the "Resumable design-session state" bullet in "Current state pointers" with:
  ```markdown
  - Process design: [`kentra-rpir.md`](./kentra-rpir.md) (approved 2026-10-11); implementation plan: [`docs/superpowers/plans/2026-10-11-kentra-rpir.md`](./docs/superpowers/plans/2026-10-11-kentra-rpir.md). Archived planning-module state: `tasks/retro-archive/planning-module-handoff.md`.
  ```
  If Task F3 moved the Stage-4/5 designs, add a bullet pointing at their new location.
- In "Doc status", delete the sentence about the `planning.md` / `mvp-plan.md` banners and the one about `workflow-orchestration-analysis.md`.
- In "This repo is not itself governed or gated", replace "It does **not** run `lifecycle` or `constitution` on itself — there is no `openspec/` tree, no ADR log, and no installed stage or governance skills." with "It does **not** run `kentra-rpir` or `constitution` on itself — there is no change folder, no ADR log, and no installed governance skills."

- [ ] **Step 2: `CLAUDE.md`**

Replace the three-bullet list with:

```markdown
- `milestoned-plan-dag` → `MPD_REF` = `milestoned-plan-dag` pin
- `constitution` → `CONSTITUTION_VERSION` (release-pinned; only advances on a new tag)
```

Replace the last paragraph's example with: "To verify freshness after a rebuild, compare `MPD_REF` in the Dockerfile against `git -C milestoned-plan-dag rev-parse HEAD`, and `constitution --version` in the box against `CONSTITUTION_VERSION`." Also replace the `uv tool install --reinstall` sentence with: "Build from-source CLIs in a fresh clone at the pinned ref, so a prior layer's install is never reused."

- [ ] **Step 3: `README.md`**

Rewrite lines 1–93 (everything above the final `---` and historical note) to:

````markdown
# Agentic Coding Harness (`kentra/harness`)

The **wrapper / design repo** for kentra's agentic coding setup: Claude Code as
the runtime, a spec → plan → implement → review process, and a governance rail.
This repo holds the designs, the decision research and the working notes, and
**composes standalone primitives as git submodules** rather than absorbing them.

> **Orientation:** [`AGENTS.md`](./AGENTS.md) is the canonical agent-facing map.
> This README is the human front door. Where they differ, `AGENTS.md` wins.

## Repo pattern — standalone primitives as submodules

Every standalone, reusable primitive lives in **its own repo** and is consumed
here as a submodule. Primitives stay framework-neutral, agent-agnostic, and MIT.
The `kentra-` prefix appears only at the branded layer (the `kentra-rpir`
process plugin; the [`kentra-sdlc`](./kentra-sdlc.md) umbrella methodology).

## The pieces

| Piece | What it is | State |
|---|---|---|
| [`kentra-rpir`](./kentra-rpir/) | Claude Code plugin: one change runs spec → plan → implement → review, with parallel verified milestones in worktrees and a retro read from the session transcripts. Design: [`kentra-rpir.md`](./kentra-rpir.md) | **v0.3** (replaces `spec-lifecycle`) |
| [`milestoned-plan-dag`](./milestoned-plan-dag/) | YAML plan format — a DAG of verifiable milestones — and its Go CLI | **v0.2.0** |
| [`adr-sourced-constitution`](./adr-sourced-constitution/) | Event-sourced ADR log → deterministic `constitution.md`; optional governance rail | **v0.3.2** |
| [`kentra-skills`](./kentra-skills/) | Architecture skills (`java-hexagonal`, `testcontainers-java`, `subagent-workflow`) | Shipped |
| [`kentra-agentic-plugins`](./kentra-agentic-plugins/) | The plugin catalog consumers register | Live |
| Proxy / observability | LiteLLM + Langfuse | Decided, deferred |
| Auto-improvement | Online champion-challenger A/B over agent config | Designed, deferred |

## How a change runs

`/kentra-rpir:spec` → `spec.md` (agreed with the user, premises researched) →
`/kentra-rpir:plan` → `plan.yaml` (validated by `milestoned-plan-dag`,
self-reviewed) → `/kentra-rpir:implement` → code on `rpir/<slug>` +
`impl-report.md` → `/kentra-rpir:review` → `spec-retro.md`. Everything for one
change, transcripts included, lives in `docs/changes/<date>-<slug>/`. Full
process: [`kentra-rpir.md`](./kentra-rpir.md).

## Map of the repo

- **Designs:** [`kentra-rpir.md`](./kentra-rpir.md), [`observability.md`](./observability.md)
- **Methodology umbrella:** [`kentra-sdlc.md`](./kentra-sdlc.md) (parked / conventions-only)
- **Working notes:** [`tasks/todo.md`](./tasks/todo.md); archived: [`tasks/retro-archive/`](./tasks/retro-archive/)
- **Decision research:** [`references/`](./references/) — latest: [`references/rpi-rpir-research-2026-10.md`](./references/rpi-rpir-research-2026-10.md), [`references/agent-session-retro-research-2026-10.md`](./references/agent-session-retro-research-2026-10.md)
- **Deferred ideas:** [`roadmap-ideas.md`](./roadmap-ideas.md)

## Glossary guard

**SDD** here = **spec-driven development** (not Obra Superpowers' "subagent-driven
development" — different thing).
````

Keep the final `---` and the historical note. Append one sentence to that note: "The gated `spec-lifecycle` CLI and the Conductor-based `agent-orchestration` executor were retired on 2026-10-11 in favour of `kentra-rpir`."

- [ ] **Step 4: `kentra-sdlc.md`**

Make these edits:

- **Line 9:** replace the `spec-lifecycle` link with ``[`kentra-rpir`](./kentra-rpir.md)``, and "the schema it configures (`kentra-spec-lifecycle`)" with "the `kentra-rpir` plugin".
- **Lines 16–20:** in the diagram and the paragraph below it, replace `spec-lifecycle` with `kentra-rpir`, and "(issue → archive)" with "(spec → review)".
- **Line 27:** the table row becomes
  ``| Change lifecycle — spec → plan → implement → review | [`kentra-rpir`](./kentra-rpir/) — plugin, plan schema via `milestoned-plan-dag` | **v0.3** |``.
- **Line 40:** replace "enters `spec-lifecycle`" with "enters `kentra-rpir`'s spec stage".
- **Line 47:** replace "Already deferred inside `spec-lifecycle` §11 (\"living-spec prose synthesis\") — recorded here" with "Recorded here".
- **Line 56:** the Consistency cell becomes "`milestoned-plan-dag validate` + `constitution guard` as pre-commit/CI checks".
- **Line 60:** delete the sentence "Add `flock` for the one narrow intra-repo race (two `lifecycle approve` on `approval-state.json`)." and keep the rest of the paragraph.
- **Line 64:** append "2026-10-11: `spec-lifecycle` replaced by `kentra-rpir` (`kentra-rpir.md`)."

- [ ] **Step 5: `tasks/todo.md`**

- Replace the "Open" and "In flight" text (lines 7–18) with:
  ```markdown
  ## Open

  ### In flight

  - **kentra-rpir** — design `kentra-rpir.md`, plan
    `docs/superpowers/plans/2026-10-11-kentra-rpir.md`. Replaces `spec-lifecycle` and
    `agent-orchestration`.
  - **Parked:** kafka-dq `002-rule-engine` plan-audit fixes (unanswered at the
    2026-10-11 switch to kentra-rpir; kafka-dq is to be rebuilt).
  ```
- In the issue index (lines ~39–61 and ~128–147):
  - delete the `agent-orchestration` issue rows;
  - relabel `spec-lifecycle` rows as `kentra-rpir`, and mark rows about gates, OpenSpec, `lifecycle` verbs or living specs "obsolete — retired 2026-10-11". Leave rows about other repos as they are.

- [ ] **Step 6: Verify no stale references remain**

```bash
grep -n -e 'spec-lifecycle' -e 'agent-orchestration' -e '`lifecycle' -e 'orchestration\.md' -e 'agent-definition' -e 'planning\.md' -e 'mvp-plan' AGENTS.md CLAUDE.md README.md kentra-sdlc.md tasks/todo.md
```

Expected: only these:

- historical mentions that say "formerly", "replaced", "retired" or "obsolete";
- the `AGENTS.md` bullet's "(formerly `spec-lifecycle`)".

- [ ] **Step 7: Commit the docs, the design, the research and this plan**

```bash
git add kentra-rpir.md references/agent-session-retro-research-2026-10.md references/rpi-rpir-research-2026-10.md docs/superpowers/plans/2026-10-11-kentra-rpir.md
git commit -m "kentra-rpir: process design, research, plan; retire spec-lifecycle and agent-orchestration docs" -- AGENTS.md CLAUDE.md README.md kentra-sdlc.md tasks/todo.md kentra-rpir.md references/agent-session-retro-research-2026-10.md references/rpi-rpir-research-2026-10.md docs/superpowers/plans/2026-10-11-kentra-rpir.md orchestration.md orchestration-implementation-plan.md tasks/orchestration-runtime-handoff.md agent-definition.md planning.md mvp-plan.md fabro-vs-conductor-evaluation.md workflow-orchestration-analysis.md observability.md references tasks/retro-archive
git show --stat HEAD
```

Read the whole `--stat` output. It must not include `kentra-skills` or `references/technologies.md`. It may include `roadmap-ideas.md` only if Task F3 added to it.

- [ ] **Step 8: 🔒 Push** — the harness is ahead of `origin/main` by two commits from before this work, as well as these. Ask the user, then `git push origin main`.

---

## Done when

- `milestoned-plan-dag` v0.2.0 and `constitution` v0.3.2 are released and installed.
- `kentra-io/kentra-rpir` holds the plugin; its CI is green; one smoke run passed all four stages.
- The catalog lists `kentra-rpir`, and lists neither `spec-lifecycle` nor `agent-orchestration-skills`.
- The harness has the `kentra-rpir` submodule and no `spec-lifecycle` or `agent-orchestration` submodule.
- The harness box has no `lifecycle` or `orch`; `MPD_REF` and `CONSTITUTION_VERSION` match the submodule pointers.
- `grep` finds no stale links in the harness docs.
