# tasks/todo.md

Working tracker. Completed session trackers are removed once their outcome lives
in git history, auto-memory, or a filed issue — see `tasks/retro-archive/` for
retros worth keeping.

## Open

### In flight

- **kentra-rpir** — design `kentra-rpir.md`, plan
  `docs/superpowers/plans/2026-10-11-kentra-rpir.md`. Replaces `spec-lifecycle` and
  `agent-orchestration`.
- **Parked:** kafka-dq `002-rule-engine` plan-audit fixes (unanswered at the
  2026-10-11 switch to kentra-rpir; kafka-dq is to be rebuilt).

### Resolved 2026-08-17

- **`HOMEBREW_TAP_TOKEN` now covers `milestoned-plan-dag`.** Its first v0.1.0
  release published the GitHub Release but failed the cask push with
  `401 Bad credentials` — the org secret is scoped to selected repositories and
  the new repo was not on the list. The owner granted access; v0.1.0 was then
  torn down and re-cut per `docs/releasing.md`, and the release succeeded
  end-to-end with the cask pushed by `goreleaserbot`. Verified by
  `brew reinstall`, which validates the archive checksums on download.

  Worth knowing for next time: the re-cut produced **different archive
  checksums** for the same commit. `mod_timestamp` pins the binaries' mtimes but
  not those of the extra files bundled into the archives (`LICENSE`, `README.md`,
  `schema/*.json`), so the tarballs are not bit-reproducible across runs. A
  hand-written cask therefore goes stale the moment a release is re-cut — always
  let the pipeline regenerate it rather than leaving a hand-made one in place.

### Open issues by repo (as of 2026-09-10)

- `kentra-rpir` (formerly `spec-lifecycle`) — **#12 (`lifecycle-refine`
  elicitation-first rewrite)** — obsolete — retired 2026-10-11, **#13
  (`lifecycle-design` elicitation-first rewrite)** — obsolete — retired
  2026-10-11, **#14 (constitution handling across the stage skills)** —
  obsolete — retired 2026-10-11, #11 (no way to suppress the AGENTS.md pointer
  block) — obsolete — retired 2026-10-11, #6 (remove OpenSpec references; ~half
  done) — obsolete — retired 2026-10-11, #5 (stable identifiers; slug half
  shipped, stored-ID and scenario-ops halves open) — obsolete — retired
  2026-10-11.
- `harness` — **#4 (domain glossary + grill-with-docs)**, **#5 (full track
  record: session + plan + implementation, benchmarkable)**, #2 (stale box bind
  mount; box reuse lacks a mount-liveness check).
- `adr-sourced-constitution` — #31 (`adr edit --body-file` can silently drop
  every rule in an ADR), #29 (draft phase should not require `--source` on
  `adr new`), #28 (`constitution.md`'s introduction is a hardcoded Go constant
  describing the tool, not the project), #27 (`config set` cannot repair a
  config that fails validation).
- `milestoned-plan-dag` — **#5 (plan provenance: pointer to the planning
  session)**, #1 (per-milestone `checkpoint: true` + plan-level `healthcheck`;
  raised as the now-retired agent-orchestration#47).
- `claudebox` — #11 (`cb login` still provisions credential files against an
  `env_auth` box).
- `kentra-skills`, `kentra-agentic-plugins` — none open.

Bold = filed 2026-09-10 from the kafka-dq ideation pass (see below).

### Ideation filed 2026-09-10 (kafka-dq session)

Four concepts were captured as issues; none is refined, and no scope is
approved. They form one cluster — the **planning leg's elicitation and its
record**:

- **Elicit before formalizing.** (formerly `spec-lifecycle`#12/#13 — obsolete, retired 2026-10-11: those issues rewrote `refine`/`design` as collaborative, question-driven stages, a shape kentra-rpir's interactive spec stage now supersedes.) Upstream pinned 2026-09-25: `mattpocock/skills` (MIT, `c55ee46`) — `grill-me`
  and `grill-with-docs` are trampolines over `grilling` (design tree, frontier,
  batched rounds) and `domain-modeling` (glossary + ADRs written during the
  interview); both transcribed into the issues.
  Continuation of the 2026-08-11 skills-rewrite pilot (which covered
  `/lifecycle-plan` only) *plus* the elicitation phase the pilot did not
  design.
- **Constitution in the loop.** (formerly `spec-lifecycle`#14 — obsolete, retired 2026-10-11.) Consult it (via
  `constitution-gov`'s force-inline rather than a pointer), gate on it, and
  route ADR proposals, consistently across every stage skill. **Decided
  2026-09-25:** the two primitives are an intended pairing, and a repo with no
  `constitution/` is simply ignored — no warning, no degraded-mode branch.
- **Glossary + grill-with-docs.** harness#4 — ubiquitous language as a
  first-class artifact; placement across the primitives is undecided.
- **Track record + benchmarking.** harness#5 + milestoned-plan-dag#5 — attach
  the planning session to the plan so session + plan + implementation is one
  unit, making the *planning* leg benchmarkable alongside the execution leg
  Stage 5 already covers.

### Unfiled, tracked only in this directory

- **`.claudebox/tmp/` convention** — `tasks/claudebox-tmp-convention.md`. The
  gitignore half shipped (harness `d4b9e76`); no rule in
  `AGENTS.md`/`CLAUDE.md` and no hook.
- **`openspec/` directory rename** — obsolete, retired 2026-10-11 (the `spec-lifecycle` OpenSpec-format tree it referred to no longer exists).

Previously listed here and now filed: **refine elicitation** (→ kentra-rpir#12/#13, formerly `spec-lifecycle`#12/#13 — obsolete, retired 2026-10-11) and **change → plan → conversation traceability** (→ harness#5, milestoned-plan-dag#5).

The **Stage 4 / Stage 5 designs** (LiteLLM + Langfuse; A/B experiment controller) and the `agent-definition` decisions entry that used to live here — obsolete, retired 2026-10-11: its sole home, `tasks/orchestration-runtime-handoff.md`, was deleted with those designs — see git history.

### Retired docs (2026-09-25) — where their content went

The planning-leg backlog is fully dispersed into issues, so both docs were
deleted rather than left to rot. Recover either from git history if needed.

- `tasks/lifecycle-refine-retro.md` → **spec-lifecycle#12** (and #13). The
  kafka-dq dogfood finding — refine converges before it elicits — is restated
  there in full. Note two shipped-history docs in `adr-sourced-constitution`
  (`docs/proposal-v0.2-next-iteration.md`, `docs/plans/2026-07-23-…`) cite this
  path as provenance; those citations now point here.
- `tasks/spec-lifecycle-backlog.md` → §2 to spec-lifecycle#12/#13, §3 to
  spec-lifecycle#5 (a verbatim superset, assessment included), §4 to harness#5
  + milestoned-plan-dag#5. **§1 (structured contracts) needed no issue**: by
  the doc's own source notes both halves are resolved — `lifecycle init` is
  idempotent with a drift policy (`cmd/lifecycle/init.go:52-58`), the
  `tasks.md` template half went moot with the 007 YAML flip, and the residual
  quality concern is owned by `tasks/lifecycle-skills-rewrite-design.md`.
  Its one live fact — `agent-orchestration#9` shipped **without** a commit
  trailer (`orchestration/launch/milestone_commit.py` `build_message()` emits a
  subject line only; no `Change-Id:`/`Session:` anywhere) — was moved to
  harness#5 as a comment.

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
