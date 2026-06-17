# Library Analysis Report

**Objective:** Produce `references/library-analysis.md` — a deeper-than-README analysis of every position in the reference library + named analysis candidates, with OSS alternatives (>1000⭐) per project. Feeds later harness design.

**Decisions (confirmed with user):**
- Scope: 13 library entries + 5 analysis candidates (DBOS, Taskmaster, claude-swarm, OpenSpec, BMAD). Argo dropped (syntax reference only).
- Output: new standalone doc `references/library-analysis.md`; `technologies.md` stays the clean catalog.
- Alternatives: 2-4 per project, OSS, >1000⭐, each with a mini-overview + how it differs.

## Projects (18), grouped by layer

### Meta-harness / multi-agent orchestration
- [ ] Omnigent (omnigent-ai/omnigent)
- [ ] Ruflo (ruvnet/ruflo)
- [ ] Symphony (openai/symphony)
- [ ] Factory.ai (commercial — OSS alternatives only)
- [ ] claude-swarm (affaan-m/claude-swarm) — candidate

### Workflow definition / orchestration engine
- [ ] Fabro (fabro-sh/fabro)
- [ ] MS Conductor (microsoft/conductor)

### Spec / planning layer
- [ ] Spec-Kit (github/spec-kit)
- [ ] OpenSpec — candidate
- [ ] BMAD (bmad-code-org/BMAD-METHOD) — candidate

### Task DAG / state engine
- [ ] Beads (gastownhall/beads)
- [ ] PlanDB (Agent-Field/plandb)
- [ ] Taskmaster — candidate
- [ ] DBOS (dbos-inc) — candidate

### Agent-team authoring / methodology
- [ ] rf-harness (revfactory/harness)
- [ ] Compound Engineering plugin (EveryInc/compound-engineering-plugin)

### Cross-cutting infra
- [ ] LiteLLM (BerriAI/litellm) — model gateway
- [ ] nono (always-further/nono) — sandboxing

## Steps
1. [ ] Dispatch parallel research agents (one per project)
2. [ ] Dedupe alternatives across sections; flag cross-listings
3. [ ] Assemble `references/library-analysis.md` (intro + summary table + grouped sections)
4. [ ] Self-review for accuracy/consistency; verify star/license claims are sourced
5. [ ] Review section + hand to user

## Review

**Done.** `references/library-analysis.md` written — all 18 projects analyzed via parallel research agents (one per project), assembled into grouped sections + summary table + cross-cutting notes.

**Key outcomes / findings:**
- All 18 library/candidate repos verified as real and current (the "anonymized-looking" names — Omnigent, Ruflo, Fabro — are genuine 2026 projects).
- **Gas Town** (steveyegge/gastown, ~16k⭐) surfaced as the most relevant *missing* library entry: a Claude-Code-native orchestrator built **on Beads** — i.e. our orchestration layer + our DAG engine already assembled. Recommend adding to `technologies.md`.
- **Vibe Kanban** (BloopAI, ~24k⭐) also recurred as a board/worktree runner candidate (now sunsetting).
- Recurring cross-category alternatives: Claude Squad, CrewAI, OpenHands, LangGraph, Backlog.md.
- Notable corrections vs. catalog: Beads now under `gastownhall` org (~24.5k⭐); MS Conductor ~250⭐ (sub-1k); Symphony ~25k.

**Caveats recorded in the report:** several star counts are single-source June-2026 reads (opencode ~175k, Superpowers/GSD figures noisy); Ruflo & rf-harness lean on self-reported benchmarks; license flags noted for productization (AGPL/Commons Clause/source-available tiers).

**Follow-ups completed:**
- ✅ Added **Gas Town** to `technologies.md` (catalog style) + full analysis section in `library-analysis.md` (now 19 projects). Vibe Kanban was briefly added then removed at the user's request — it remains only as an external *alternative* reference under Gas Town / Fabro / Taskmaster, not as a library position.

**Open follow-ups:**
- This completes "Phase 2: research/document" for the current library; design phase still gated on library being declared complete.
