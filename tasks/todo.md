# tasks/todo.md

*Prior content (2026-07-23/24 session trackers A–G: conductor fork patches,
auto-commit, box wiring, 001-dag-plan-primitive finish, and the zero-input bug
batch) is complete and removed; outcomes live in auto-memory, git history, and
the review below.*

## Review — zero-input bug batch (2026-07-24, delegated + orchestrated)

All five open agent-orchestration bugs fixed, verified, merged, live-proven.
Execution: two parallel worktree streams, each milestone = fresh Sonnet
implementer + fresh Opus verifier (author≠verifier); daemon treated as a
serialized shared resource (stopped during test windows, orchestrator-only).

- **PR #28 (merged `b2f2eea`)** — #14 stall misreport (`done: awaiting
  dashboard disconnect` via terminal ROOT event in events JSONL) + #7 tail
  (`provider-exit` verdict w/ `orch resume` remedy, matched on the exact
  empty-diagnostics placeholder).
- **PR #29 (merged `0f53105`)** — #27 `materialize_box` merges instead of
  clobbering `.claudebox/config.yaml`; #16 per-milestone `contract.check`
  wired into the L1 gate (absent/`none` → l1 omitted = native skip; static
  input kept as hermetic override); #23 `empty_paths` loud failure + the
  BONUS latent bug: ScriptExecutor never enforces exit codes and the commit
  route was unconditional → any nonzero commit exit previously reached `$end`
  like success; now conditional route + `commit_failed` terminate, change-level
  propagation verified against vendored conductor source.
- **Live proof (M4)**: daemon image rebuilt from main + restarted; production
  `orch launch` of a scratch one-milestone change → `done`/`success`: merged
  box config (env preserved, `${ORCHESTRATION_DAEMON_TOKEN}` daemon-resolved),
  real L1 (`l1_command` = the milestone's actual check), real paths-confined
  commit (`commit_status: committed`). Scratch repo/box/registry entry removed.
- **Issue state**: #27/#16/#23/#14 closed by PR keywords; #7 closed w/ status
  comment. Open by design: #24 (one-run-one-repo record), #15 (github-mirror —
  feature, all lifecycle gates pending, needs user consent to start).
- **Follow-ups recorded in PR bodies** (deliberately not done): add `done:`
  prefix to the resume 409 gate (belt-and-suspenders); `data.subworkflow_path`
  root-event discriminator is engine-coupled (fail-safe direction).

## Open

- [x] 015-github-mirror SHIPPED (PR #31 merged 2026-07-24, issue #15 closed).
      Remaining open `agent-orchestration` items: #30 (gate-time pytest tmp
      bug — fix PR in flight) and #32 (consume `milestoned-plan-dag` for
      execution ordering, follow-on from harness#1, soft-blocked on
      spec-lifecycle#7).
