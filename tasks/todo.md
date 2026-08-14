# tasks/todo.md

Working tracker. Completed session trackers are removed once their outcome lives
in git history, auto-memory, or a filed issue — see `tasks/retro-archive/` for
retros worth keeping.

## Open

Nothing in flight in this repo. Live work is tracked as issues in the primitive
repos (see below) and in `tasks/plans/2026-08-11-lifecycle-skills-rewrite.md`.

### In flight

- **Lifecycle skills rewrite** — design `tasks/lifecycle-skills-rewrite-design.md`,
  plan `tasks/plans/2026-08-11-lifecycle-skills-rewrite.md`. **Half executed:**
  Tasks 1–11 landed on branch `skills-rewrite-pilot` in both `milestoned-plan-dag`
  (`e74457a`, PR #2 open) and `spec-lifecycle` (`9007235`, no PR yet). Tasks 12–16
  are not done — `spec-lifecycle/skills/` still holds 6 skills with no
  `lifecycle-plan`, `evals/` does not exist, and `internal/validate/doc.go` is
  uncommitted on the pilot branch.

### Open issues by repo (as of 2026-08-11)

- `agent-orchestration` — #32 (consume `milestoned-plan-dag` for execution
  ordering; its spec-lifecycle#7 blocker cleared, now actionable), #42 (empty
  `contract.paths` → `git add -A`; **widened** — the diff-confinement gate is
  never wired at all), #43 (milestone contracts never run the linter), #47
  (change-level L2 healthcheck is the always-pass default on every production
  run), #48 (agent box gets empty `skills/`/`plugins/` — `kentra-skills` never
  seeded).
- `spec-lifecycle` — #5 (stable identifiers; slug half shipped, stored-ID and
  scenario-ops halves open), #6 (remove OpenSpec references; ~half done).
- `harness` — #2 (stale box bind mount; box reuse lacks a mount-liveness check).
- `claudebox` — #11 (`cb login` still provisions credential files against an
  `env_auth` box).
- `milestoned-plan-dag` — #1 (per-milestone `checkpoint: true` + plan-level
  `healthcheck`; the natural fix vehicle for agent-orchestration#47).

### Unfiled, tracked only in this directory

- **Refine elicitation** and **change → plan → conversation traceability** —
  `tasks/spec-lifecycle-backlog.md` §2 and §4. Not filed as issues anywhere.
- **`.claudebox/tmp/` convention** — `tasks/claudebox-tmp-convention.md`.
  Unimplemented: no rule in `AGENTS.md`/`CLAUDE.md`/`constitution.md`, no hook,
  and `.gitignore:17` ignores only `.claudebox/tmp/kentra-skills/` so the
  directory's scratch shows up as untracked (two files are even tracked).
- **Stage 4 / Stage 5 designs** (LiteLLM + Langfuse; A/B experiment controller)
  and the `agent-definition` decisions — sole home is
  `tasks/orchestration-runtime-handoff.md`.
- **`openspec/` directory rename** — `spec-lifecycle/README.md:5` points at
  spec-lifecycle#6 as the tracker, but #6's body explicitly excludes the rename.
  Nothing tracks it.

## Review — tasks/ cleanup (2026-08-11)

Verified all 8 open issues and all 27 `tasks/` entries against shipped code
rather than against their own status banners.

- **Issues:** 1 of 8 closeable — spec-lifecycle#2 closed as superseded (machine
  -readable status shipped as YAML in `cd467e9`, not JSON). The other 7 are real;
  three had no code touching their paths at all.
- **Docs:** 15 deleted (fixes verified shipped and pinned), 4 retros + 2 executed
  plans archived, 6 kept. Three of the deleted docs were actively misleading —
  `orchestration-box-auth-expiry.md` prescribed a `cb login` remedy that is now
  legacy-only, and `m6-cast-personas.md` claimed all-Opus while
  `workflows/milestone.yaml:226` records the Sonnet-implements decision.
- **Defects rescued before deletion:** three real gaps existed only inside docs
  slated for removal → filed as agent-orchestration#47, #48, and a scope-widening
  comment on #42.
