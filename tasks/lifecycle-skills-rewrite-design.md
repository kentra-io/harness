# Lifecycle skills rewrite — design

**Date:** 2026-08-11
**Status:** design approved, implementation plan pending
**Scope:** `spec-lifecycle/skills/*` + `milestoned-plan-dag/skills/plan-author`

## Context

The six `spec-lifecycle` skills are drafts. They were written alongside the CLI
and never tested, reviewed, or held to an authoring standard. An audit
(`.claudebox/tmp/lifecycle-skills-audit.md`, 2026-07-24) found they
"systematically fail the two stated goals: plans are neither self-contained nor
schema-conformant."

Two facts have changed since the audit:

- **The YAML flip shipped** (PR #8, `515c84a`). `plan.yaml` replaced the old
  markdown task list, and the schema now makes `contract` mandatory with all
  three keys (`check`, `criteria`, `paths`) required. 007's 0-of-8-contracts
  disaster is structurally impossible in the new format.
- **`lifecycle-plan` was deleted** by 007's M8. The plan stage has no driver
  skill today.

So the remaining gap is not schema coverage — it is **quality inside the
required fields**. `check: make test`, `criteria: it works`, and
`paths: ["**"]` all validate clean and produce an unexecutable plan. That is
skill territory.

## Goals / Non-Goals

**Goals**

- A plan whose milestones are **self-contained**: an implementer can start work
  from one milestone without navigating to other documents or asking questions.
- Every scenario in the change's spec delta is covered by some milestone.
- Skills that agree with the CLI they drive, verified mechanically.
- Fix four live correctness defects in the existing skills.

**Non-Goals**

- No change to `plan.schema.json`.
- No new CLI capabilities (`lifecycle explain` is deferred; skills point at
  existing `--help`).
- No new orchestration capabilities. Plan mutability, the run-time deviation
  append, and mpd#1 (`checkpoint: true`, plan-level healthcheck) are all out.
- No dependency on the third-party superpowers plugin at runtime — we take its
  patterns, not a dependency.

## Decisions

### D1 — The neutral/branded ownership split

The placement test for every rule: *would a solo dev using `milestoned-plan-dag`
without `spec-lifecycle` want this?* Yes → `plan-author`. Only meaningful with an
approved spec upstream → `/lifecycle-plan`.

| Concern | `plan-author` (mpd, neutral) | `/lifecycle-plan` (branded) |
|---|---|---|
| Grammar, DAG, contract keys | owns | — |
| `check` = repo's standard validation command | owns | — |
| Self-containment bar (names, not lookups) | owns | — |
| Prose criteria, "just enough to be clear" | owns | — |
| Scenario → criterion → named test chain | — | owns |
| Coverage of every delta scenario | — | owns |
| `design.md` components → milestone deliverables | — | owns |
| HALT when no approved artifact covers the work | — | owns |
| Gate 3 mechanics and plan immutability | — | owns |

`/lifecycle-plan` invokes `plan-author` for format and bar; it never restates the
grammar. `plan-author` never mentions gates, spec deltas, or spec-lifecycle.

Grounding: the plan artifact is `openspec/changes/<change>/plan.yaml`;
`lifecycle validate --stage plan` delegates wholesale to `milestoned-plan-dag
validate` over it (`internal/validate/doc.go`); `lifecycle archive` refuses while
`milestoned-plan-dag resolve` reports any milestone not done
(`ErrTasksIncomplete`). A change with no `plan.yaml` is never gated by either.

### D2 — Verification strictness concentrates in named tests

`contract.check` is **the repo's standard validation command** (`./gradlew clean
test`, `go test ./...`) — typically identical across milestones. It guarantees the
two things a general command can: nothing regressed, and new tests ran inside the
real suite rather than under an isolating filter.

What it cannot guarantee is that any test was written. That obligation moves to
`criteria`, using the schema slot that already exists — entries are
`{name?, given, when, then}`, and `name` carries the test identity (file path +
test name). The chain:

```
spec.yaml scenario → milestone criterion (GWT, inline) → named test → green check
```

Everything else in the milestone is prose, just clear enough to act on.

**Rejected:** requiring `check` to name behaviour the milestone creates. A
milestone normally adds test *and* implementation together, so running such a
check on the pre-milestone tree fails because the test does not exist — red for
the wrong reason. Enforcing it would mandate TDD or an artificial
test-milestone/implementation-milestone split. Neither is wanted.

`red` survives as a leading word only in the bug flow, where the repro genuinely
fails before the fix exists.

### D3 — Class-level structure lives in `design.md`, projected into milestones

Every mature SDD framework puts component and interface naming in the design
document, not the task list: Kiro's `design.md` holds components, data models and
interfaces as "a blueprint for implementation"; Spec-Kit emits `data-model.md` and
`contracts/` as plan-phase siblings while keeping the plan itself "high-level and
readable". Our `design.md` template has **no such section** — that is the actual
gap, and it is why class naming looked like a plan-schema question.

So:

- `design.md` gains a **Components & Interfaces** section — the source of truth,
  reviewed by a human at **gate 2**, where a wrong decomposition is cheap to fix.
- Each milestone carries the **projection**: file, class, one-line responsibility,
  in `deliverables.create/modify/test` (a field the schema already has).
- The implementer needs the *names* to start; it reads `design.md` only for the
  *why*. Lean, because design.md is not copied into every milestone;
  self-contained, because no milestone needs a lookup to begin.

**Rejected:** a structured `components:` field in the plan schema. It adds
validation surface to a shipped neutral primitive for something no validator can
grade — `responsibility: "handles stuff"` validates clean. That repeats exactly
the 007 trap of a mandatory field satisfied vacuously.

Consequence: **a change needing class-level design cannot take `designSkip`.**
That converts "small, local, architecturally inert" from a judgement call into a
test refine can apply.

### D4 — The plan is immutable after gate 3

`lifecycle approve` hashes the change's artifacts into the gate entry
(`internal/approve/approve.go`, `hashFiles`), so gate 3 records a hash of
`plan.yaml`. `contract.paths` confines the implementer's diff and never includes
`plan.yaml`. The orchestrator step in `workflows/milestone.yaml` emits only
`guidance` text and has no file-write path.

The approved artifact is the executed artifact. Deviations are the orchestrator's
to handle during escalation and to report after execution. If a deviation means
the *plan* is wrong rather than a local detour, that is the ladder's third rung —
`Needs human input`, human amends, gate 3 runs again on the amended plan.

Assumed, not built: no deviation machinery is added by this work.

### D5 — Skill shape

`/lifecycle-plan` is `disable-model-invocation: true` — human-typed only, because
it terminates at a consent gate and autonomous invocation is the failure being
designed against. `plan-author` stays model-invoked so `/lifecycle-plan` can reach
it. No third tier: measured duplication across the six existing skills is ~38 of
~485 lines (~8%), which does not justify a shared reference skill, and
`plan-author` already serves that role.

`/lifecycle-plan` is a **sequence** document; `plan-author` is **reference**.

Adopted from superpowers: one HARD-GATE at the consent boundary; a numbered
checklist mapped to tasks; a process digraph for the real branches (`designSkip`,
untraceable scenario → HALT, validation failure → fix loop, changes requested →
revise without approving); a Red Flags / Excuse → Reality table built from 007's
actual failures.

Modified per `writing-for-agents`:

- **`## Never` sections are converted to positive targets.** Prohibition drags the
  forbidden behaviour into context. `"Never author markdown"` becomes *"The YAML is
  the source of truth; `spec.md` is a read-only projection."* Only the `lifecycle
  approve` guardrail stays a negation — it cannot be phrased purely positively —
  and it is paired with its positive target.
- **Doc-pointer citations are cut.** The existing skills carry 14
  `spec-lifecycle.md §N` references to a file `lifecycle init` never installs into
  consumer repos. The environment replaces them: `--help`, the templates, the
  schema.
- **Leading word: `self-contained`.** Already AO's own word —
  `workflows/milestone.yaml:227` justifies the Sonnet implementer with
  "self-contained plans carry the context that made Opus necessary". Shared
  vocabulary across plan, executor, and skill.

Size budget: existing skills run 68–91 lines; `/lifecycle-plan` targets that
range, with reference-shaped material pushed into `plan-author`.

### D6 — Four correctness fixes, in scope

Verified against the built binary:

1. `lifecycle-bug` instructs `lifecycle validate --stage repro` and `--stage fix`.
   The CLI accepts only `[refine design plan]`. **The skill is broken today.**
2. `lifecycle-archive` says a bug's gates are `repro` "and `fix`, if the fix stage
   ran". Both gates are unconditional (`internal/status`).
3. `lifecycle-init` says "already initialized — say so and stop" (line 19) and
   "every step is independently idempotent" (line 57).
4. `lifecycle-new-feature` runs bare `gh issue create` with no `--repo`, and never
   mentions `designSkip`.

## Verification

**Drift check — deterministic, in CI.** Extract every CLI invocation from the
skills' fenced blocks; check each subcommand, flag, and enumerated value against
the CLI's registered definitions. No execution, so `approve`/`archive`/`init` are
checked without being run. One test in `spec-lifecycle`, a sibling in
`milestoned-plan-dag`. This catches defect D6.1 statically. Known limit, accepted:
one-directional — it will not notice the CLI growing a flag the skill should
mention.

**Eval — subagent, on demand.** A committed fixture change folder with
gate-approved `proposal.md`, `specs/**/spec.yaml`, and a `design.md` carrying a
real Components section. A fresh agent gets the skill and the folder, nothing
else, and produces `plan.yaml`. Pass conditions:

1. `milestoned-plan-dag validate` exits 0.
2. Every scenario in the delta appears in some milestone's criteria (mechanical —
   both sides are YAML).
3. A second fresh agent, handed **one milestone** plus the repo, states what it
   will do **without asking a question**.

Condition 3 is the `self-contained` bar, deliberately binary rather than a quality
score. On demand, not CI — it costs tokens and is not deterministic.

**Done:** drift check green in both repos, eval passing all three conditions, four
defects fixed.

## Build order

Pilot slice first — `/lifecycle-plan` end to end, plus `plan-author`'s quality-bar
changes, its eval case, and the drift check. Plan is greenfield (M8 deleted the
old skill), is where 007's damage occurred, is the only stage crossing the repo
boundary, and has an objective pass condition. The remaining skills are then
rewritten against the proven pattern.

## Risks / Trade-offs

- **Uniform `check` weakens L1's per-milestone signal.** It still catches
  milestone 3 breaking milestone 2, but can no longer confirm milestone 3 did
  anything; that detection rests on the named-test list and the verifier. Accepted:
  007 produced vacuous checks anyway, so trading a check the planner cannot author
  for a test list it can is the right side of the trade.
- **Committing a decomposition up front will sometimes be wrong.** Mitigated by the
  existing escape: report the deviation, do not silently redesign.
- **Eval condition 3 is a judgement.** Mitigated by making it binary — did the
  agent ask a question or not.

## Deferred

- `lifecycle explain` / richer `--help` as the home for CLI mechanics.
- mpd#1 (`checkpoint: true`, plan-level healthcheck).
- Making `deliverables` required in the schema — revisit after the pilot shows
  whether skill-level instruction suffices.
- AO's implementer prompt names `spec.md` and `tasks.md`, both pre-YAML-flip
  filenames that no longer exist (`workflows/milestone.yaml:243`). Separate AO
  issue.
- The run-time deviation append: specced in `orchestration.md` §5.2/§6.2 and wired
  at the gate (`lifecycle approve` runs `constitution deviation validate`), but
  absent from AO's Python. Separate AO issue.
