# Finding: the orchestration verifies the working tree but never commits milestones

*Surfaced 2026-07-15 completing the kafka-dq `001-e2e-poc` dogfood run (agent-orchestration M9 acceptance). Not a crash — a design gap in the "finish" contract.*

**Status: BUILT 2026-07-23 (agent-orchestration PR #19)** — option 1 below,
as designed: a deterministic `commit` script step
(`orchestration.launch.milestone_commit`) on milestone.yaml's verifier-pass
route; `git add` confined to the milestone's declared `contract.paths`;
message `M<n>: <title> (<change_id>)`; hermetic default dry-run, launcher
flips `commit_dry_run=false` for the production (box) tier. Identity:
repo/env config wins (box env forces the bot identity), neutral fallback
otherwise; no trailer in v1.

## What happened

The `execute-change` run completed cleanly — all 7 milestones processed,
verifier passed (author≠verifier), change-level `full_healthcheck` green,
workflow reached `archive_handoff` and returned
`{"milestones_processed": 7, "status": "dry_run"}`. **Yet the entire M7
diff was left uncommitted in the worktree** (untracked `app/src/test/`, the
new composition-root classes, modified `build.gradle.kts`, ticked
`tasks.md`, deleted `AppModule` placeholder). Git log stopped at the prior
milestone commit; a human had to `git commit` M7 out-of-band.

This is the **same** shape as the M2–M6 "salvage commit" (`3d592d7`): those
milestones were also verified in-run but committed by hand afterward. So it
is not a one-off — the workflow **never** commits milestone output. It
operates entirely on the working tree.

## Why it matters

- **Durability gap.** Between a milestone passing verification and a human
  committing, all the work lives only in the worktree. A crash, a
  `git checkout`, or worktree cleanup loses verified work. The whole point of
  "Conductor as durable spine" is undercut if the *artifact* (the code) is not
  persisted at the same cadence as the *checkpoint* (the cursor index).
- **Resume ambiguity.** On resume, completed-milestone code is assumed present
  in the tree. If it were ever cleaned, resume would re-run against a tree
  missing prior milestones' code — silent divergence.
- **Verification target.** The verifier and the diff-confined-to-declared-paths
  gate operate on the **working-tree** diff, not a commit. That works, but it
  means "what was verified" is never pinned to an immutable object — the tree
  can change after the verifier looked at it and before a human commits.
- **Archive gate.** `lifecycle archive` folds a change; if milestone commits
  are the intended unit of record, an uncommitted change is in a half-state at
  archive time.

## The fix to design (not yet built)

Add a **commit step to the milestone ladder** — after a milestone's verifier +
gates pass, the orchestration itself commits the diff (confined to declared
paths) with a conventional message (`M<n>: <title> (<change_id>)`). Options:

1. **A deterministic `script` step** appended to `milestone.yaml`'s success
   path (`git add <declared paths> && git commit -m ...`). Keeps the commit
   verb out of any LLM's hands (consistent with the sec-7.3 consent boundary
   that keeps `archive_handoff` a `script` step). Message templated from
   `milestone_id`/`milestone_summary`. **Preferred** — deterministic, no new
   agent authority, commits exactly the verified diff.
2. Give the implementer a commit instruction in its prompt — rejected: puts a
   git-write verb in an agent's hands and lets the author commit its own work
   (weakens the author≠verifier spine; the thing committed may not be the thing
   verified).

Confine the commit to the milestone's `contract.paths` (the same set the diff
gate already enforces) so an errant write outside declared paths still fails
the gate rather than getting committed. Decide the author identity
(orchestration bot vs. inherit) and whether to carry a trailer.

## Immediate state (this run)

M7 committed by hand as `0a08039` (message follows the M2–M6 style). All 7
milestones now committed; working tree clean; 9/9 e2e green. The change is
**implemented + verified but not yet archived** (`archive_dry_run` was true —
`lifecycle archive` never ran). See `kafka-dq-e2e-poc` memory for the archive +
retro sequencing (transcripts must be preserved before any worktree cleanup).

## Related

- `tasks/execute-change-box-input-spec.md`, `tasks/cb-run-detach-spec.md` —
  other gaps the same first-live-run surfaced.
- `agent-orchestration/docs/conductor-fork-patches-pending.md` — the .venv
  provider patches (do not `uv sync`).
