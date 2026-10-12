# RPI / RPIR workflows for coding agents

Research date: 2026-10-11. Scope: the industry Research→Plan→Implement (RPI) pattern and its review-stage variants, read for designing **kentra-rpir** (spec → plan → implement → review, where review covers both the code change and a transcript-based retro, `spec-retro.md`).

## Facts table

| Item | Vendor / author | Current version / date | License | Status |
|---|---|---|---|---|
| RPI (Research-Plan-Implement), "frequent intentional compaction" (ACE-FCA doc) | HumanLayer (Dex Horthy) | ACE-FCA doc 2025; "No Vibes Allowed" talk (AI Engineer) | ace repo: not stated (unverified) | Verified [1][2] |
| HumanLayer `.claude/commands` (research_codebase, create_plan, iterate_plan, implement_plan, validate_plan, local_review, create_handoff/resume_handoff, describe_pr…) | HumanLayer | repo pushed 2026-06-19 | GitHub reports `NOASSERTION` (no recognised SPDX license) | Verified [3][4] |
| QRSPI / "CRISPY" (Questions, Research, Design, Structure, Plan, Worktree/Work, Implement, PR) | HumanLayer (Dex Horthy) | 2026; follow-up talk "Everything We Got Wrong About RPI", March 2026 (secondary report) | n/a (method) | Secondary sources only [5][6] |
| RPI = "Research, Plan, Implement, Review" (`/rpi-research`, `/rpi-plan`, `/rpi-implement`, `/rpi-review`) | Microsoft HVE Core (for GitHub Copilot) | latest release `hve-core-v3.2.2`, 2026-03-23; docs page dated 2026-10-05 | MIT | Verified [7][8] |
| "Research, Plan, Implement, Review" personal workflow | Tyler Burleigh (blog) | 2026-02-22 | n/a | Verified [9] |
| agent-retro (post-session retro skill for Claude Code) | giannimassi | small (5 commits); requires Claude Code ≥ 2.1.59 | MIT | Verified [10] |
| `retro` skill | Matt Pocock (`mattpocock/skills`, `skills/engineering/retro`) | 2026 | MIT | Verified [11][12] |

## Findings by question

### 1. Is "RPIR" an established name, and who uses it?

**No. It is not an established standard name.** "RPI" is established. Dex Horthy at HumanLayer coined and popularised it, and Tyler Burleigh credits him explicitly [9]. The fourth "R" (Review) shows up in several places, but no single source or standards body owns "RPIR":
- Microsoft HVE Core keeps the **acronym RPI** but defines it as "Research, Plan, Implement, Review" (the page even renders "Review, Review"). It ships a dedicated `/rpi-review` [7].
- Tyler Burleigh titles his workflow "Research, Plan, Implement, Review" but doesn't use "RPIR" as a label [9].
- The literal string "RPIR" appears only in scattered places, such as one GitHub issue ("RPIR workflow comparison", edwardearle/claude-setup#25) [13]. **(unverified that any vendor uses "RPIR" as a product name.)**
- Related variants have different names: RPIV (…→Validate, markbahnman) [14] and QRSPI/CRISPY (HumanLayer's own successor) [5][6].

**Implication:** "kentra-rpir, the branded flavour of RPIR" is fair as long as it's framed as "RPI plus an explicit Review stage". Don't claim RPIR is a recognised industry term. HumanLayer itself has moved away from plain RPI (see §5).

### 2. What does each stage produce, and how do stages hand off?

All verified sources agree that **each stage writes a durable markdown artifact, and that artifact is the only input to the next stage**. Context is deliberately discarded between stages.

| Stage | HumanLayer ACE-FCA [1][3] | Microsoft HVE Core [7] | Burleigh [9] |
|---|---|---|---|
| Research | codebase research doc (files, flow) via subagents | `.copilot-tracking/research/<date>/<slug>-research.md`. Runs **only when evidence is inadequate** | `RESEARCH.md` |
| Plan | plan doc: exact files, edits, verification steps, split into phases | `.copilot-tracking/plans/<date>/<slug>-plan.md` with stable IDs `P##` / `P##-T##` plus a critique disposition | `PLAN.md` + `PLAN-CHECKLIST.md` |
| Implement | executes phase by phase; **status written back into the plan file** | `.copilot-tracking/changes/<date>/<slug>-changes.md`. If the plan has to change, it updates the plan and **pauses dependent work** | phase by phase against the checklist |
| Review | `validate_plan`: git log/diff, runs build/test/lint, compares code to plan, lists manual checks [4] | `.copilot-tracking/reviews/logs/<date>/<slug>-review.md`. Findings are severity-graded and **routed** | fresh-session review against PLAN.md, "as a senior engineer" |

Frequent intentional compaction (HumanLayer): build the whole workflow around keeping context use at about **40–60%**. At natural breakpoints, write the progress into a markdown file and continue in a fresh window [1]. Secondary sources describe a "dumb zone" past about 40% (unverified as a measured threshold; it's a practitioner heuristic).

**Fit with kentra-rpir:** the spec maps to research+design, and the plan maps to a `milestoned-plan-dag` YAML. `impl-report.md` plays the same role as HVE's `changes.md` and HumanLayer's write-back of status into the plan. HVE's stable task IDs match milestone IDs, so the retro can cite findings per milestone.

### 3. How is Review done in existing variants?

There are two different kinds of review, and kentra-rpir needs both:

**(a) Acceptance review of the change** (is the code right?)
- HumanLayer `validate_plan` [4] produces sections for implementation status per phase, automated verification results, code-review findings (matches and deviations), manual testing requirements, and recommendations.
- HVE `/rpi-review` [7] puts requirements, acceptance criteria, plan and task completion, critique dispositions, plan updates made during implementation, changes, and validation evidence into one record. It **keeps execution status separate from the acceptance outcome** and **routes each finding**: defects → implement, decision gaps → plan, evidence gaps → research, residual work → follow-up items. This routing table is the most directly reusable pattern for spec-retro.md.
- Burleigh [9]: "Let AI review before you do". The agent self-reviews in a fresh session first, so the human only looks at what's left. HumanLayer's 2026 position is that humans must read the code again: "we tried not reading the code for like six months, it did not end well" [5] (secondary quote, unverified against the talk).

**(b) Process retro** (what should change in skills or process?). None of the RPI vendors ships this as a stage. It exists only as standalone skills:
- **agent-retro** [10] reads Claude Code JSONL transcripts (`~/.claude/projects/<cwd>/<session>.jsonl`) and produces: the conversation arc, per-agent token attribution, waste detection (large tool results that were never used), friction analysis (user corrections, redirects, abandoned approaches, each traced to a root cause), and **concrete edit proposals with the actual text to change** in skills, rules or config. The user approves or defers each one interactively.
- **Matt Pocock `retro`** [11][12] is read-only. It ranks improvements by severity across navigation, automated checks, coding standards, steering files (AGENTS.md), tool economy, no-op instructions, and information access. It prefers **automated checks over prose rules**.
- From SRE practice [15]: run postmortems blamelessly and look for contributing systemic factors rather than fault, and write them up within days. Allspaw's "no root cause / each necessary, only jointly sufficient" [16] argues for listing contributing factors instead of forcing a single root cause.

### 4. Design implications for the kentra-rpir retro

These are synthesis, not claims from any source:
1. **Two inputs, two questions.** `impl-report.md` says *what happened* (per-milestone outcome, deviations, retries). The transcripts show *why*. Use the report to find the milestones worth reading closely, then read those transcripts selectively. Reading every transcript in full won't fit one context window, so fan out one analyst subagent per milestone/session and reduce afterwards, as the workflow fan-out/reduce pattern does.
2. **Classify every finding by destination** (adapted from HVE routing): spec defect, plan/DAG defect, skill defect (name the skill and give the proposed edit text), missing automated check, environment/tooling, model limitation (no action). Drop or park findings without a destination. That's how retros turn into concrete improvements.
3. **Proposals must be diffs, not advice** (agent-retro). The user's review stage approves or defers each one, which matches "Review = user reviews the change and the retro".
4. **Prefer automated checks over new prose rules** (Pocock). This also guards against instruction-budget bloat: HumanLayer reports models reliably follow only about 150–200 instructions [5] (secondary, unverified against the primary talk).
5. **Author ≠ retro analyst.** Run the retro as a fresh workflow, separate from the implementing agents (Burleigh's fresh-session review; consistent with the existing author≠verifier spine). Consider an adversarial verify pass that tries to refute each finding before it reaches spec-retro.md.
6. **Blameless, contributing factors** [15][16]: ask "what in the spec, plan, skill or environment made this behaviour likely?", not "the agent was wrong".
7. **Track recurrence.** A finding that repeats across changes is the strongest signal for a skill edit. Keep retros under `docs/changes/<change>/spec-retro.md` so they're greppable, like TeamRetro's `docs/ai-retros/entries/` convention (secondary [17]).

### 5. Known pitfalls of RPI

- **Plan review doesn't scale.** HumanLayer found engineers had to review plans of about 1000 lines that still diverged from the implementation. The planning prompt alone had about 85 instructions, close to the reliable instruction budget. The agent **skipped plan steps silently**. Their fix was more, smaller stages (QRSPI/CRISPY) with earlier checkpoints on short design/structure docs [5][6]. (Secondary reporting of a talk. Direction verified across two sources, details unverified.)
- **Research that's too broad**: a single research prompt used about 40% of context just to get oriented [6] (secondary).
- **Not reading code**: HumanLayer reversed its "review plans, not code" stance for production systems [5].
- **Plan drift during implementation**: HVE handles it by requiring plan updates and pausing dependent work [7]. The retro should flag any deviation that wasn't recorded.
- **Research when evidence already exists** wastes effort. HVE makes research conditional [7].

## Risks and open questions

- **Naming risk:** "RPIR" is not recognised, and HumanLayer, the originator, now promotes QRSPI/CRISPY. Readers may see kentra-rpir as following an older pattern. The mitigation is the explicit spec stage, which is where QRSPI's added stages (Questions/Design/Structure) live.
- **No established retro practice for agent transcripts.** The only evidence is two small community skills [10][11] plus SRE postmortem doctrine. Expect to tune.
- **Transcript access in Claude Code workflows:** whether subagent and workflow-agent transcripts are saved as separate JSONL files, and where, needs checking against the installed Claude Code version. **(unverified)**
- **Retro cost:** reading full transcripts is expensive. Fan-out per milestone helps; the right sampling or depth policy is still open.
- **Closing the loop:** nothing found measures whether retro-driven skill edits actually reduce recurrence. Consider tagging findings with a stable class so recurrence can be counted across changes.
- **License:** the HumanLayer commands repo shows no recognised license (NOASSERTION) [3]. Borrow ideas, not text. HVE Core, agent-retro and mattpocock/skills are MIT.

## Sources

1. HumanLayer, "Advanced Context Engineering for Coding Agents" (ace-fca.md). https://github.com/humanlayer/advanced-context-engineering-for-coding-agents/blob/main/ace-fca.md (primary)
2. AI Engineer, "No Vibes Allowed: Solving Hard Problems in Complex Codebases" (Dex Horthy). https://ai.engineer/talks/context-engineering-for-complex-codebases (primary, not fetched)
3. HumanLayer repo metadata and `.claude/commands` listing. https://github.com/humanlayer/humanlayer/tree/main/.claude/commands, https://api.github.com/repos/humanlayer/humanlayer (primary)
4. HumanLayer `validate_plan.md`. https://raw.githubusercontent.com/humanlayer/humanlayer/main/.claude/commands/validate_plan.md (primary)
5. ZenML LLMOps DB, "HumanLayer: Evolution from RPI to CRISPY". https://www.zenml.io/llmops-database/evolution-from-rpi-to-crispy-multi-stage-workflow-for-production-coding-agents (secondary summary of a talk)
6. Better Questions, "The Necessary Evolution of Research, Plan, Implement…". https://betterquestions.ai/the-necessary-evolution-of-research-plan-implement-as-an-agentic-practice-in-2026/ (secondary)
7. Microsoft HVE Core, "Understanding the RPI Workflow". https://microsoft.github.io/hve-core/docs/rpi/ (primary, page dated 2026-10-05)
8. Microsoft HVE Core repo and latest release (v3.2.2, 2026-03-23, MIT). https://github.com/microsoft/hve-core (primary)
9. Tyler Burleigh, "Research, Plan, Implement, Review: My Agentic Engineering Workflow", 2026-02-22. https://tylerburleigh.com/blog/2026/02/22/ (secondary/practitioner)
10. giannimassi/agent-retro. https://github.com/giannimassi/agent-retro (primary, MIT)
11. Matt Pocock skills repo (`skills/engineering/retro`). https://github.com/mattpocock/skills (primary, MIT)
12. SkillRepo listing of mattpocock/retro. https://skillrepo.dev/skills/mattpocock/retro (secondary)
13. edwardearle/claude-setup issue #25 ("RPIR workflow comparison"). https://github.com/edwardearle/claude-setup/issues/25 (search result only, not fetched)
14. markbahnman/rpiv-workflow. https://github.com/markbahnman/rpiv-workflow (from collectors, not fetched)
15. Google SRE Workbook, "Postmortem Culture". https://sre.google/workbook/postmortem-culture/ (primary, from collectors)
16. John Allspaw, "Each necessary, but only jointly sufficient" (2012). https://www.kitchensoap.com/2012/02/10/each-necessary-but-only-jointly-sufficient/ (secondary)
17. TeamRetro, "Gather feedback from AI agents". https://www.teamretro.com/blog/gather-feedback-from-ai-agents/ (secondary, from collectors, not fetched)
