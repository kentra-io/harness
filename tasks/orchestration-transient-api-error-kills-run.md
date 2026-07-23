# Bug: a transient API error kills the whole orchestration run

**Status:** FIXED 2026-07-23 — upstreamed into the conductor fork
(`kentra-io/conductor@ab0ff4c`, branch `kentra-patches`, with regression
tests): retryability now classified against stderr + stdout noise tail +
`result_error_message` (agent prose deliberately excluded), plus the sibling
64 MiB stream-limit fix. Pin bumped in agent-orchestration PR #18. Blast
radius additionally bounded by per-milestone auto-commit (PR #19 — see
`orchestration-does-not-commit-milestones.md`).
**Filed:** 2026-07-14, during the first full live kafka-dq `001-e2e-poc` execution.
**Severity:** high — a single network blip is fatal to a multi-hour run with no resume.
**Home for the fix:** `agent-orchestration` (the execution leg) + its Conductor fork.
Sibling to the StreamReader 64 KiB fix already logged in
`agent-orchestration/docs/conductor-fork-patches-pending.md`.

## What happened

Run `execute-change-20260714-174510` drove kafka-dq `001-e2e-poc` cleanly through
**6 of 7 milestones**. M1–M5 verified (pass:10, zero verification failures),
including the hard CEL/Avro spike and real Apicurio Testcontainers integration
(the Implementer even autonomously root-caused a `DOCKER_API_VERSION` docker-java
issue). M6's own 21 tests had already passed; the agent was on the cosmetic
task-box ticking when the run died.

## Failure chain

```
API Error: Connection closed mid-response          (transient, upstream/network)
  → claude subprocess exited with code 1: (no stderr output)
  → ProviderError                                  (ClaudeboxProvider)
  → subworkflow_failed                             (milestone_step)
  → workflow_failed                                (execute-change)
```

Terminal: confirmed (0-byte event growth after the failure). The run ended at
6/7 milestones. **All M1–M6 code was intact in the working tree** — but only M1
was committed (`ca1921d`); M2–M6 sat uncommitted (~37 changed/untracked paths).

## Root cause

Subtler than first assumed. `_classify_retryable` in
`conductor/providers/claudebox.py` **already** treats `"connection"` (and
`network`, `econnreset`, `timed out`, 5xx) as retryable. The bug is the *input*
it's given, not its keyword set:

- On a non-zero subprocess exit the provider called
  `_classify_retryable(stderr_text, exit_code)` — **stderr only**.
- The transient `API Error: Connection closed mid-response` notice is printed by
  the claude CLI as a **plain stdout line**, not a stream-json event and not on
  stderr. `_process_line` discards non-JSON stdout lines
  (`json.JSONDecodeError → skip`), so that text was captured nowhere.
- stderr was empty (`"(no stderr output)"`), so the classifier saw no signal and
  returned `is_retryable=False`. A retryable error was misclassified as fatal.

The 3-attempt escalation ladder does **not** cover this: it handles *verification
failures* (bad diffs, failing L1/L3), not *provider crashes*. And the provider is
experimental — "no checkpoint resume" — so there is no native mid-run resume
either. Net effect: any one-off connection drop anywhere in a run was
unrecoverable.

## Impact

- A ~2.5-hour run was lost at the last milestone to a blip that had nothing to do
  with the work (the agent's tests were already green).
- Recovery is manual: salvage the uncommitted tree, re-launch from a milestone
  boundary. Operationally brittle for anything longer than a toy change.

## Fix (applied to `.venv` stopgap, pending fork upstream)

Feed the retryability classifier the **stdout** error text it was missing, so its
already-correct `connection`/`network`/5xx keywords fire:

1. `_RunOutcome` gains a bounded `noise_lines` tail; `_process_line` now retains
   discarded non-JSON stdout lines (capped at `_MAX_NOISE_LINES = 50`) instead of
   dropping them.
2. The non-zero-exit path classifies against a `diag` string joining stderr +
   `noise_lines` + streamed content (`content_parts`, `result_text`,
   `result_error_message`) — not stderr alone.

Result: `API Error: Connection closed mid-response` → exit 1 now classifies
`is_retryable=True`, and Conductor's attempt machinery retries it instead of
failing the workflow. Byte-compiles clean.

Per constitution ADR-0001 this must be **upstreamed into the `kentra-io/conductor`
fork + pin-bump** (provider fixes live in the fork, not `.venv` edits). See
`agent-orchestration/docs/conductor-fork-patches-pending.md` §2 for the patch
record + upstream procedure.

## Longer-term / related

- **Real resume** would make transient failures a non-event regardless of retry.
  The experimental provider has no checkpoint resume; a milestone-boundary resume
  (re-enter `execute-change` at the failed cursor, reusing committed milestones)
  would bound blast radius to a single milestone. Track separately.
- **Auto-commit per verified milestone** would have made this a 1-line recovery
  instead of a 37-path salvage. Consider having the workflow commit on each
  milestone's verification pass.
- Observability gaps that made the death hard to diagnose are logged in
  `agent-orchestration/docs/observability-notes.md`.
