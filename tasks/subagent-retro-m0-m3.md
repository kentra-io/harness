# Subagent Retrospective — adr-sourced-constitution M0–M3

**Date:** 2026-07-03
**Scope:** 15 subagent sessions (implementers, spec reviewers, quality reviewers, fix rounds) across milestones M0 (bootstrap), M1 (read path), M2 (write path), M3 (guard).
**Method:** synthesis of per-transcript friction reports.

---

## TL;DR

| # | Recurring issue | Occurrences | Root cause | Fix (short) |
|---|---|---|---|---|
| 1 | Write/Edit-before-Read tool-contract violations | ~24 errors across 5 agents (M0, M1, M2, M3 implementers + fix round) | Dispatch-prompt gap | Add read-before-write rule to standard dispatch boilerplate (see block at bottom) |
| 2 | Shell-state / cwd / binary-path thrash during smoke tests | 3 agents (M2 impl, M3 Sonnet impl, M2 spec reviewer) | Harness (cwd resets between bash calls) + missing dispatch guidance | Boilerplate: build binary once to `/tmp/<uniquename>`, absolute paths only, never `go run` outside the module, never name binary after an existing dir |
| 3 | M2 write-path defect cluster found only at quality review (6 non-trivial issues, only major-detour of the retro) | 1 milestone, but 3 sessions consumed (impl + 2 review cycles) | Spec/plan gap | Encode adversarial constraints (parser grammar edges, fsync-on-dir durability, canonicalization injection, re-parse-before-write) as binding constraints in implementation-plan.md; enumerate DoD per-verb |
| 4 | Mid-response API connection drops | 2 agents (M1 spec reviewer, M3 quality reviewer — latter needed manual recovery) | Harness/environment | Orchestrator auto-detects "Connection closed mid-response" and sends resume message; reviewers checkpoint findings incrementally |
| 5 | Environment gaps: `goimports` missing; CRLF generation + missing `.gitattributes` | 2 agents (M0 quality reviewer; M1 implementer) | Environment provisioning | Bake `goimports` into `.claudebox/Dockerfile`; `.gitattributes` with `* text=auto eol=lf` in repo (M0 review already flagged this) |

**Single highest-leverage fix:** a standard dispatch-prompt boilerplate block (bottom of this report). Issues #1 and #2 — the two most frequent frictions, appearing in nearly every implementer session — are both preventable with ~10 lines of copy-paste text in every dispatch.

---

## Recurring issues

### 1. Write/Edit-before-Read tool-contract violations

**Occurrences (5 agents, ~24 tool errors):**
- M0 implementer (Sonnet): 1 — attempted to edit `ci.yml` without reading it.
- M1 implementer (Sonnet): 6 — during file-creation phase; pattern stopped after first recovery.
- M2 implementer (Opus): 13 — worst case; also fought exact-string matching on a literal-vs-escaped BOM.
- M3 implementer (Opus): 3 — during unrequested post-completion work.
- M3 fix-round agent: 1 — Write on `guard_errors.txtar` (3-second recovery).

**Evidence pattern:** `<tool_use_error>File has not been read yet. Read it first before writing to it.</tool_use_error>`

**Root cause bucket:** (a) dispatch-prompt gap. The tool contract already blocks the mistake, so cost per instance is small, but the aggregate (~24 errors, minutes each in M1/M2) is the most consistent friction in the entire dataset. Both Sonnet and Opus exhibit it, so it is not model-specific — it's an unprompted default that the dispatch prompt never counteracts.

**Recommended fix:** add to the standard dispatch boilerplate (verbatim text below): *"Before every Edit or Write to an existing file, Read it first in the same session — the tools enforce this and will reject the call otherwise. When creating many new files, Write is fine; when modifying, always Read → Edit."* Optionally, a mechanical backstop: a PreToolUse hook that intercepts Edit/Write on unread files and injects the file content, turning the error into a no-op — worth doing if the boilerplate doesn't drop the rate to ~0.

### 2. Shell-state / working-directory / binary-path thrash

**Occurrences (3 agents):**
- M2 implementer (Opus): 3–4 failed smoke-test rounds — `constitution: command not found` (6×), `/tmp/m2smoke/constitution: Is a directory` (binary name collided with the `constitution/` output dir), `go.mod` not found from wrong cwd. Minutes of debugging to consolidate into single shell invocations.
- M3 implementer (Sonnet): `go run` from inside a scratch git repo → `cannot find main module` (3×); recovered by building to `/tmp/constitution`.
- M2 spec reviewer: `getcwd` error after deleting the temp dir the shell was sitting in (trivial, post-completion).

**Root cause bucket:** (c) harness/environment — cwd resets between bash calls are a documented harness property — combined with (a) dispatch-prompt gap: no dispatch told agents the canonical smoke-test pattern.

**Recommended fix (mechanical, in boilerplate):**
- Build once, up front: `go build -o /tmp/adrc-<milestone> ./cmd/constitution` from the repo root (absolute path).
- Invoke the binary by absolute path everywhere; never rely on PATH or a prior `cd`.
- Never name the test binary the same as a directory the CLI creates (`constitution` collides — hence `adrc-*`).
- Never `go run` from outside the module; compound `cd X && ...` within a single bash call only.
- `cd` out of temp dirs before `rm -rf`-ing them.

### 3. M2 write-path defects surfaced only at quality review (spec/plan gap)

**Occurrences:** concentrated in M2 but consumed three sessions (implementer, quality reviewer, re-review) and was the only `major-detour` in 15 reports.
- Spec review found the crash-injection DoD gap: only supersede's 3 seams tested; renumber's 2 and deprecate's 1 untested — the DoD said "killing the process between each pair of writes" but didn't enumerate per verb, so the implementer under-covered. Also a checkpoint-name collision (renumber reused supersede's `after-new-adr`).
- Quality review found 6 non-trivial defects: parser/patch grammar mismatch (`status : accepted`), no directory fsync after rename, canonicalization newline-injection collision, weak fuzz minimality property, divergent boundary detection, and (re-review) multi-line YAML backslash-continuation corruption requiring re-parse-before-write guards.

**Root cause bucket:** (d) spec/plan gap, with a caveat: the review pipeline *worked as designed* — it caught everything before merge. The gap is that a high-risk milestone (byte-preserving mutation, crash safety) shipped with a plan that stated goals ("crash-safe") but not the adversarial constraint checklist that operationalizes them.

**Recommended fix:**
1. In implementation-plan.md, DoD items for mutating verbs must **enumerate** the seams/paths (e.g., "crash-injection at all 6 checkpoints: supersede×3, renumber×2, deprecate×1"), not describe them by rule. Enumerated lists get fully covered; quantified prose doesn't.
2. Add a reusable "durability & injection constraints" binding-constraints block to any milestone touching file mutation: fsync file *and* parent dir after rename; canonicalization must be injection-proof (length-prefix or escape before joining); any patched file must re-parse to the intended semantic value before the write is committed; parser and patcher must share one grammar.
3. Require unique checkpoint names across verbs (trivial lint or naming convention `<verb>-<seam>`).

### 4. Mid-response API connection drops

**Occurrences (2 agents):**
- M1 spec reviewer: "Connection closed mid-response" mid-narrative; self-recovered, trivial.
- M3 quality reviewer: same error cut off verification work; required a manual recovery message from the orchestrator and re-execution of remaining steps (minutes).

**Root cause bucket:** (c) harness/environment — infrastructure, not agent behavior.

**Recommended fix:** orchestrator-side: watch subagent output for the literal string `Connection closed mid-response` and auto-send "Pick up where you left off; your last complete output was: …". Agent-side mitigation in reviewer dispatch prompts: "Record findings incrementally (e.g., append to a findings list as you verify each item) rather than holding the full report for one final message, so an interrupted response loses at most one item."

### 5. Environment provisioning gaps (Go toolchain hygiene)

**Occurrences (2 agents, related):**
- M0 quality reviewer: `goimports: command not found` during formatting checks.
- M1 implementer: generated CRLF line endings in a test string literal → gofmt lint failure; M0 quality review had independently flagged the missing `.gitattributes`.

**Root cause bucket:** (c) harness/environment.

**Recommended fix:**
- `.claudebox/Dockerfile`: `RUN go install golang.org/x/tools/cmd/goimports@latest` (plus any other tools reviewers reach for: `staticcheck` if not covered by golangci-lint).
- Repo: commit `.gitattributes` with `* text=auto eol=lf` (and `*.txtar text eol=lf`) — makes the CRLF class of failure impossible at the git layer.
- Boilerplate reminder for Go implementers: run `gofmt -l .` before claiming lint-clean.

---

## One-off but costly

- **Submodule checkout reset underneath the M0 implementer** (external `git checkout main` during its fix phase): the agent hit missing files mid-edit, had to diagnose and restore branch state. Root cause: (b) orchestration process. Fix: **never mutate a workspace a subagent is actively using** — give each implementer an isolated git worktree (`superpowers:using-git-worktrees`), or gate any orchestrator git operations on the subagent having returned. This is the one issue in the set that only the orchestrator can prevent.
- **"No checks reported" immediately after PR open** (M1 implementer): CI hadn't queued yet. Trivial, but avoidable: boilerplate says use `gh pr checks --watch` (or sleep 30s before first poll).
- **`ReportFindings` with `outcome: null`** (M3 spec reviewer): schema wants the field absent, not null. One line in reviewer dispatch prompts: "omit `outcome` entirely on first-pass reviews; never pass null."

## What worked (keep)

- **The review pipeline earns its cost.** Spec review + quality review caught every substantive defect before merge across all four milestones, including a critical guard bypass (M3 `core.quotepath` evasion) and the entire M2 durability/injection cluster. Zero defects are known to have escaped.
- **Empirical reviewers.** Reviewers that build, run, fuzz, and adversarially probe (M1 quality: 1.25M fuzz execs + byte-exact golden checks; M3 fix-verifier: tested evasion variants beyond the suite) consistently produced the highest-signal findings. Keep "verify by execution, not by reading" as an explicit reviewer instruction.
- **Detailed dispatch prompts → frictionless sessions.** The four zero-issue sessions (M0 spec review, M1 quality review, M2 quality review, M3 fix verification) all had dispatch prompts described as clear, structured, with per-item verification lists. Prompt specificity is the strongest predictor of a clean session in this dataset.
- **Agent self-correction.** Multiple agents caught their own bugs before review (M3 Sonnet's mislabeled resurrection test; M1's CRLF fix during lint). The verify-before-done loop is functioning.
- **Proactive live verification** (M0 implementer re-checking `actions/checkout` versions against live releases) — cheap and caught staleness.
- **Mixed-model staffing** (Sonnet implement/spec-check, Opus for high-risk implementation and quality review) showed no capability-driven failures attributable to Sonnet on its assigned tiers; keep the current split.

## Suggested dispatch-prompt boilerplate additions

Copy-paste into every implementer/fix-round dispatch (reviewer prompts: include the Shell/paths and CI sections):

```markdown
## Working rules (harness mechanics — follow exactly)

**File edits**
- Before every Edit or Write to an EXISTING file, Read it first in this session. The
  tools enforce this and will reject the call. New files: Write directly is fine.

**Shell & paths**
- Your cwd RESETS between bash calls. Use absolute paths everywhere; if you need a
  working directory, use a single compound command: `cd /abs/path && <cmds>`.
- Build the CLI once, up front, to a fixed absolute path:
  `go build -o /tmp/adrc-test ./cmd/constitution` (run from the repo root).
  Invoke it by that absolute path in all smoke tests. Do NOT `go run` from outside
  the module, and do NOT name the binary after a directory the CLI creates.
- `cd` out of a temp directory before deleting it.

**Go hygiene**
- All generated file content uses LF line endings (including string literals in tests).
- Run `gofmt -l .` and `golangci-lint run` before claiming lint-clean.

**CI**
- After opening/updating a PR, poll with `gh pr checks --watch` (or wait ~30s before
  the first check) — checks take time to queue.

**Scope**
- When your deliverable is complete and reported, STOP. Do not start unrequested
  follow-up work; flag concerns in your report instead.

**Reviewers only**
- Verify by execution (build, run, probe), not only by reading.
- Record findings incrementally as you verify each item, so an interrupted response
  loses at most one finding.
- In ReportFindings, omit the `outcome` field entirely on first-pass reviews (never null).
```

Orchestrator-side rules (not prompt text):
1. Never touch a subagent's checkout while it is active; prefer per-subagent git worktrees.
2. Auto-recover subagents on "Connection closed mid-response".
3. For mutation-heavy milestones, the plan must enumerate crash seams / adversarial constraints per verb before dispatch (see Recurring issue 3).
