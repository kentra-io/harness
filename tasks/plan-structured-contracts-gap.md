# Gap: plans authored without structured `contract` blocks (found 2026-07-14)

**What happened.** The first real `/lifecycle-plan` run on the kafka-dq
testbed (change `001-e2e-poc`) produced a `tasks.md` whose validation
contracts were prose-only: no ` ```contract ` YAML blocks (`check` /
`criteria` / `paths`) and no `[ ]` step checkboxes. `lifecycle validate
--stage plan` passed (both additions are opt-in), and the gap surfaced only
at execution-launch prep, when `lifecycle apply --format json` returned
`contract: null` for every milestone — meaning the orchestration engine's
L1 acceptance gate, diff-confined-paths gate, and archive tasks-completion
gate would all have run vacuously.

**Root causes — two distinct defects:**

1. **Stale skill fan-out (process defect, primary).** The spec-lifecycle
   §5.5 change (agent-orchestration M3) updated the *source* skills in the
   `spec-lifecycle` repo (`skills/lifecycle-plan`, `skills/lifecycle-archive`)
   with the contract-block + checkbox guidance — but the copies `lifecycle
   init` had installed into consuming repos (`harness/.claude/skills/`,
   `kafka-dq/.claude/skills/`) were never refreshed. The plan session ran on
   the stale copy, which describes only the four prose labels. There is **no
   re-fan-out mechanism or discipline**: skills are installed once at `init`
   and drift silently as the primitive evolves.
   - *Fixed now (symptom):* all four stale copies (lifecycle-plan +
     lifecycle-archive × harness + kafka-dq) refreshed byte-identical from
     source, 2026-07-14.
   - *Owed (cause):* a `lifecycle init --refresh-skills` (or similar) verb in
     spec-lifecycle, or at minimum a documented convention to re-copy skills
     on every spec-lifecycle version bump. Candidate: a small spec-lifecycle
     change through its own pipeline.
   - Note (2026-07-24): `milestoned-plan-dag` (shipped, pinned `78acd13`)
     makes contract blocks mandatory-by-schema for new plans; re-check
     whether the two owed fixes below are still needed once spec-lifecycle's
     plan stage shells out to it (007).

2. **Source template never updated (doc defect in spec-lifecycle).** The
   updated `lifecycle-plan` skill text claims "the template shows both", but
   the embedded template (`internal/schema/templates/tasks.md`, and its
   projection into every repo's `openspec/schemas/kentra-spec-lifecycle/
   templates/tasks.md`) still shows the bare four-label shape — no contract
   block, no checkboxes. Even a fresh install today would template plans
   without them. Owed: update the template in the spec-lifecycle repo (and
   re-embed), so the skill's claim becomes true.

**Policy decision (user, 2026-07-14):** contracts are **not optional for
us** — every plan authored in this ecosystem carries a ` ```contract ` block
(executable `check`, plain-language `criteria`, allowed `paths` set) and
checkbox-tracked steps on every milestone, written *at planning time* by the
same session that writes the milestone (planning owns the contract;
execution never re-authors it). The spec-lifecycle grammar keeps them
opt-in for backward compatibility, but our skills/templates must make them
the default output, not an addendum.

**Authoring notes learned while writing the kafka-dq contracts** (fold into
the template/skill fix):

- Every milestone's `paths` must include the change's own
  `openspec/changes/<change>/tasks.md` and the repo-root `deviation.json` —
  the Implementer persona ticks `[x]` boxes and appends to the deviation
  log, and `orchestration.harness.diff_paths` has no built-in exemption for
  either, so omitting them fails the paths gate on every milestone.
- `check` is a single command run from the project root; pick the narrowest
  command that proves the milestone (`./gradlew :module:test`), reserving
  the full build for skeleton/final milestones.
- `criteria` must stand alone for a fresh judging agent: name the observable
  outcomes, not the effort.

**Remediation record (kafka-dq `001-e2e-poc`):** `tasks.md` amended
2026-07-14 — all 7 milestones now carry contracts (verified via `lifecycle
apply`: 7/7 extracted, all steps tracked); plan-stage validation green;
fresh gate-3 `deviation.json` re-validated; gate 3 re-approval required
(tasks.md hash changed after the original approval).
