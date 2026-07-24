# adr-sourced-constitution build — orchestration state

Updated: 2026-07-04. Orchestrator handoff file (survives conversation compaction).

## OPEN at reboot (2026-07-04): skills-visibility investigation

User report: `/skills` shows nothing at harness root AND in a claudebox started from the
submodule root ("No changes" output both times). Investigation state:

- All 4 skills on disk are valid: real files (not symlinks) in
  `adr-sourced-constitution/.claude/skills/{adr-draft,constitution-gov,constitution-init,plan-gate}`,
  frontmatter good, names match dirs.
- **Discovery provably works** from the submodule root: headless `claude -p` there puts
  `adr-draft` + `constitution-gov` in the model's skill_listing (transcripts under
  `~/.claude/projects/-Users-jony-code-kentra-harness-adr-sourced-constitution/`).
  Re-tested with git deliberately broken (`cp -a` to /tmp/subsim → gitdir dangling,
  simulating a submodule-only claudebox mount): still discovered.
- `constitution-init` + `plan-gate` are ABSENT from any model-facing list **by design**
  (`disable-model-invocation: true`) — they exist only as user slash commands.
- Environment facts found: working tree is a real host bind mount; container
  `/home/agent/.claude` → symlink → `/Users/jony/.claude` (host `~/.claude` bind-mounted),
  so host + ALL claudebox containers share one Claude config/transcript store.
- `/skills` "No changes" = panel closed without toggling, NOT "nothing loaded". Binary
  strings: "Plugin skills are managed via /plugin", "No skills found" (CLI 2.1.198).
- Harness root has only `excalidraw-skill` (symlink → `.agents/skills/`, via
  skills-lock.json); the constitution skills are scoped to the submodule by design.
- **PENDING (user test, decisive):** in a claudebox session at the submodule root, type
  `/adr-draft` or `/plan-gate` in the composer — does it autocomplete? If yes: all fine,
  the `/skills` panel display was the whole confusion. If no: report what the panel DID
  list (superpowers plugin skills vs literally nothing) to split discovery-failure vs
  display-filter. Unconfirmed side question: does excalidraw-skill (symlinked) show at
  harness root — if not, symlink-following may be the harness-root issue.

## Where we are

Executing `adr-sourced-constitution/implementation-plan.md` (v1, M0–M7) via subagent-driven development
(superpowers:subagent-driven-development skill: implement → spec review → quality review → fix rounds → merge).

| Milestone | PR | Status |
|---|---|---|
| M0 bootstrap | #1 | merged |
| M1 read path | #2 | merged |
| M2 write path | #3 | merged |
| M3 guard | #4 | merged |
| M4 init/scaffold | #5 | merged; full loop (spec SPEC-COMPLIANT, quality 2 IMPORTANT+3 MINOR fixed first-try, verify MERGE-READY). CLAUDE.md agent notes = PR #6, merged (main @ 9c8101b) |
| M5 skills+dogfood | #7 | merged (main @ 8eceec3); full loop incl. bootstrap-source erratum #8 + active-only adrId tightening. **Spike (b) PASS** (deviation.json cited ADR-0003+ADR-0011 CRITICAL for the plant, validator clean, hash two-pass coherent) **+ (c) PASS** (gov probes answered from inlined constitution; hierarchy conclusion unambiguous). **(a) consent-at-permission-prompt still needs USER interactive session.** Friction log (M7 inputs, non-blocking): plan-gate default `./deviation.json` dirties governed checkout (spec-conformant per §2.9 but footgun); `--out` conflatable with a validator flag; guard folds nothing for prospective violations (reasoning catches them); "don't hand-edit the projection" lives in ADR-0011's tail, not its own ADR; gov hierarchy item #2 wording could waver a careless reader |
| M5.5 rule-bearing projection | — | **IN FLIGHT 2026-07-03.** User review verdict: constitution.md must be a curated rulebook, not all-ADRs projection. Design pinned = plan §2.12 (optional `## Rule` body section; presence=opt-in, content=projection; frozen w/ body, promote/demote=supersede; `--rule` on new/supersede; deviation validate tightens to rule-bearing citations; empty-constitution placeholder; 5-line Rule warning). Committed direct to main: user's interactive reset (676140b — ADR-0001/2/4..11 deprecated via CLI, ADR-0003 sole active, ADR-0012 records the reset, guard-clean) + §2.12 pin (5b18d82). **MERGED (PR #9, main @ d6f4cdb).** Full loop: implementer first-try (CI green, 88% cov); spec review 1 MISSING (constitution-init skill prose vs founding-file semantics) → fixed cf180e9, orchestrator verified by executing the skill's worked example (disclosed); quality review 1 IMPORTANT + 5 MINOR (theme: malformed rule input silently swallowed) → orchestrator ruling "rule = 1–3 lines plain prose, validated on every path" → fixed 47e3256 (blank founding Rule = error; `## Rule` first heading = reserved-word error; duplicate `## Rule` = error; heading lines in rule text = error; §2.12 addendum sentence), orchestrator re-ran all 5 repros by execution (disclosed). Both fixers first-try; failed-fix-attempts still 0. Dogfood constitution.md = placeholder pending user re-seed. NOTE for re-seed: `init` re-run does NOT re-seed founding ADRs — the re-seed is adr-draft/supersede sessions (promoting ADR-0003 via supersede+--rule is the designed promote path AND doubles as the M5(a) consent test) |
| M6 distribution | #8 | build portion merged (main @ 0bf3ca5): goreleaser.yaml (skip_upload auto, 6 targets, ldflags proven), SHA-pinned release.yml (v[0-9]* trigger), goreleaser-check CI gate, releasing.md w/ checksum-verified snippet + recovery runbook. **v0.1.0 RELEASED 2026-07-03** (user set HOMEBREW_TAP_TOKEN and directed cut before re-seed — content-only tradeoff, disclosed). Verified: workflow green 1m12s; 6 assets + checksums; tap got Casks/constitution.rb; linux asset runs on host AND in scratch container; go install @v0.1.0 reports v0.1.0 via ReadBuildInfo. REMAINING: user's `brew install kentra-io/tap/constitution` on Mac. Note: rc tags don't test the tap path (skip_upload auto). Fix-round verify was done by orchestrator directly (disclosed; small config surface) |
| M7 harness acceptance | — | pending (kafka-dq testbed; needs user for /constitution-init interview) |

## User-gated items (all remaining critical path)
1. Founding re-seed after M5.5 merges: user-interactive session, per-ADR approval, distilled `## Rule` sections (doubles as M5(a) consent test + closes the "I didn't approve these ADRs" gap; the 2026-07-03 reset already deprecated the old founding set down to ADR-0003).
2. ~~HOMEBREW_TAP_TOKEN + v0.1.0~~ DONE 2026-07-03. Remaining release check: user's brew install on Mac.
3. M7 kafka-dq adoption pass (interview is interactive).

## Retro (2026-07-03) — apply to all M5+ dispatches
- `tasks/subagent-retro-m0-m3.md` = M0–M3 transcript retrospective. **Every future implementer/fixer dispatch MUST include its "Working rules" boilerplate block** (report §"Suggested dispatch-prompt boilerplate additions"): read-before-write, absolute paths + build-once-to-/tmp/adrc-test, LF-only, gh pr checks --watch, stop-at-scope; reviewer dispatches get the Shell/CI + reviewer-only sections.
- Orchestrator rules from retro: never touch an active subagent's checkout; auto-recover on "Connection closed mid-response"; mutation-heavy milestones need per-verb enumerated crash seams in the plan before dispatch.
- goimports: installed user-space + added to .claudebox/Dockerfile. `.gitattributes` already committed (M0).

## Accepted-risk register (LOW, deliberate)
- Plan §8 "coverage gate enforced in CI" was never wired (no -coverprofile/threshold step in ci.yml; predates M5.5, spec-reviewer NOTE 2026-07-03). Coverage measured manually at reviews (88% on internal/... at M5.5). Backlog: add ratchet job post-v0.1.0.
- init writes constitution.yml before founding-file validation; failed founding init persists config (recover: rm constitution.yml). Verifier-noted, orchestrator-accepted at M4.
- Symlinked CLAUDE.md target is replaced by a regular file (consistent atomicwrite semantics everywhere).
- F1 blank-title txtar depends on a literal trailing space after `##` — fails loudly (not silently) if stripped.

## Process rules in effect (user-set)
- Implementers + code-quality reviews = **Opus**; spec-compliance reviews = Sonnet. (User nudged Opus twice.)
- Escalate to user after 2 failed resolution attempts on the same issue.
- Fix rounds go to FRESH agents, never resumed transcripts (2.5h stall lesson, tasks/lessons.md).
- Post visible progress marker after every merge; metric = failed-fix-attempts per implementer (currently 0).
- Milestone flow: branch → PR → CI green (7 legs) → spec review → quality review → fix rounds w/ re-review → orchestrator merges.

## Environment facts
- Go 1.26.4 + golangci-lint 2.12.2 user-space in claudebox (~/.local/bin symlinks); also added to .claudebox/Dockerfile (needs host `cb build` to bake in).
- gh authenticated as kentra-gh-bot; push access to kentra-io/{adr-sourced-constitution,homebrew-tap} verified.
- Work dir = /Users/jony/code/kentra/harness/adr-sourced-constitution (submodule checkout on main).

## M4 dispatch prompt: key binding points (full text in implementation-plan.md §2.1/§2.2/§4/§5/§6/§7-M4)
- Managed block exact markers (§2.2), interior-only rewrite, .state hash, drift→confirm/--force.
- CLAUDE.md interior = `@constitution/constitution.md`; AGENTS.md = short textual pointer (§2.1).
- regen gains managed-block refresh with warn-don't-block semantics (drift in user file must not block mutating verbs).
- skills/ ×4 stubs only (constitution-init, adr-draft, plan-gate, constitution-gov) — M5 authors bodies; go:embed; fan-out real copies to .claude/.agents/.cursor skills trees; drift-protected like blocks.
- init NOT consent-gated; founding ADRs via --principle/--founding-file, source `bootstrap`; idempotent byte-identical re-run.
- Notable review-carried seams: adr.ParseBytes/ParseBytesUnnamed, manifest netstring canonicalization (docs/manifest-canonicalization.md), guard exit contract 0/1/2.

## Reviewer/implementer agent IDs (resumable for REPORTS only, never new work)
- M3 fix implementer: a1807b7dc38caf592 · M3 fix verifier: a737c6bb62c749569 (merge-ready verdict)
- Older: M2 impl ac11ee21e4e2cdea5 · M2 reviewer aa24d5a7f81a3ec54 · M1 impl afd577a52e5c549b8 · M1 reviewer ae648ef926bcd66a6
