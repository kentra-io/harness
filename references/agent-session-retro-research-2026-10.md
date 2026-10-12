# Retrospectives on AI coding agent implementation sessions

*Research for kentra-rpir: the review stage's retro workflow reads `impl-report.md` and the implement-session transcripts, and writes `spec-retro.md`. Checked 2026-10-11.*

## Facts table

| Item | Version / date | License | Vendor / author | Status |
|---|---|---|---|---|
| Claude Code (the runtime that produces the transcripts) | 2.1.296 (installed locally, 2026-10-11) | Proprietary | Anthropic | Verified locally |
| Claude Code transcript format | JSONL: `~/.claude/projects/<slug>/<session>.jsonl`, plus subagent transcripts at `<session>/subagents/agent-*.jsonl`. Record types seen: `user`, `assistant`, `attachment`, `system`, `file-history-snapshot`, `mode`, … | n/a | Anthropic | Verified locally and in the hooks docs. The docs say `transcript_path` "is written asynchronously and may lag" [S14]. The subagent directory layout is not documented; seen on disk only. |
| Claude Code `/insights` | Built-in command. Builds an HTML report of recent sessions: "where things go wrong, and features to try" | Proprietary | Anthropic | Verified in the official commands reference [S15]. The friction categories listed are from secondary blogs only **(unverified)**. |
| MAST taxonomy (multi-agent failures) | arXiv 2503.13657 v3, 2025-10-26. 14 modes in 3 categories, κ=0.88 | CC BY-NC-ND 4.0 | Cemri et al., UC Berkeley | Verified [S1] |
| "Failure as a Process" (CLI coding-agent trajectories) | arXiv 2607.09510, 2026-07-10 | arXiv | Zhao, Barr, Sarro, Ye et al. (UCL et al.) | Verified [S2] |
| "Model or Harness?" interaction taxonomy | arXiv 2607.28802, 2026-07-30. 41 modes, judge κ=0.76 | arXiv | Raj, Gupta, … He (Scale AI authors) | Verified [S3]. Author affiliation **(unverified)**. |
| "How Coding Agents Fail Their Users" (20,574 sessions) | arXiv 2605.29442 v2, 2026-08-31 | arXiv | Tang, … T. J.-J. Li (Notre Dame et al.) | Verified [S4] |
| AgentLens ("lucky pass") | arXiv 2605.12925, revised 2026-10-05 | arXiv | Sahoo et al. | Verified [S5] |
| "Agent Skills Can Be Harmful" / SkillTriage | arXiv 2608.11888, 2026-08-12 | arXiv | Dong, Gao, Li, Xu, Hua, Yang | Verified from the abstract [S6]. Percentages in the collector's notes **(unverified)**. |
| Anthropic, "Demystifying evals for AI agents" | 2026-01-09 | Blog | Anthropic | Verified [S7] |
| Incident Analysis for AI Agents | arXiv 2508.14231, AIES 2025 | arXiv | Ezell, Roberts-Gaal, Chan | Verified [S8] |

## Findings by question

### 1. How to analyse a trajectory, and which failure taxonomy to use

- **The decisive error comes early and shows up late.** A study of 1,184 failed CLI coding-agent trajectories found the following [S2]:
  - The median decisive error happens at step 7, in failed runs with a median length of 27 steps (mean 42).
  - Observable failure signals appear about 10 steps after the decisive error.
  - Root causes split as epistemic 57.9%, competence 32.8% and environment 9.4%.
  - "False premises", where the agent acts on assumptions it never verified, are the largest single trigger at 30.7%.
  - **Implication:** the retro should not start at the visible failure, such as a red test or an escalation. It should walk backwards from the first observable signal to the earliest step where the agent committed to a wrong belief, and record that step as the *decisive point*.
- **Separate model, harness and environment by assigning each fault to an edge.** "Model or Harness?" [S3] attributes each failure to an *interaction between two components* and to a *fault side* that says where the repair belongs:
  - Its components: Model, Owner, Grader, Third party, Context, Memory, Tool, Local env, External env.
  - Model-side faults lead to post-training.
  - Harness-side faults lead to scaffolding or tool fixes.
  - Environment or grader faults lead to redesigning the evaluation.
  - This is the best primary basis for the user's question: "skill/prompt/process gap vs model execution gap vs environment gap".
  - In kentra terms, only harness, skill, process and environment fixes are actionable. A solo founder cannot post-train the model. Model-side findings become "route differently or add a guardrail" items, such as a model choice or a check.
- **A user-facing taxonomy from real Claude-Code-style sessions** [S4] (20,574 sessions; one episode can carry several labels):

  | Pattern | Share |
  |---|---|
  | Developer Constraint Violation | 38.33% |
  | Misread Developer Intent | 26.95% |
  | Inaccurate Self-Reporting | 22.58% |
  | Faulty Implementation | 17.82% |
  | Wrong Project Diagnosis | 11.56% |
  | Self-Initiated Overreach | 10.20% |
  | Operational Execution Error | 2.87% |

  The paper also reports that constraint violations and inaccurate self-reporting *grow* in share over time. **Inaccurate self-reporting at 22.58% is the direct argument for never trusting `impl-report.md` as evidence.**
- **Multi-agent failures** [S1]: MAST has 14 modes in 3 categories: system design, inter-agent misalignment and task verification. It fits the parallel-milestone setup in kentra-rpir, with handoffs between orchestrator and milestone agents, premature termination, and verification that is missing or incomplete.
- **A pass can still be a finding.** AgentLens [S5]:
  - 10.7% of *passing* trajectories were "lucky passes": regression cycles, blind retries, missing verification, or exploration, implementation and verification done out of order.
  - The rate ranged from 0.5% to 23.2% across model backends.
  - The retro should therefore review green milestones too, at least by sampling, not only red ones.
- **Skills cause failures.** [S6] studied 307 skill-induced failures. Skills often made agents implement required elements incorrectly or leave them out, and turned optional verification or construction steps into mandatory ones; excessive verification and heavy pipelines accounted for 97 regression cases. The retro taxonomy therefore needs a "skill caused this" bucket next to "skill was missing". The fix is sometimes to *delete* text from a skill.

**Recommended kentra-rpir taxonomy** (a small, closed set synthesised from S2, S3, S4 and S6):

| Code | Meaning |
|---|---|
| `SPEC` | The spec or plan was ambiguous or wrong (misread intent traces back to the artefact) |
| `SKILL` | A skill or prompt was missing, wrong, or harmful (over-procedure) |
| `PROCESS` | Workflow or gate design: handoff, verification step, ordering |
| `ENV` | Tool, sandbox, dependency, or flaky infrastructure |
| `MODEL` | The model failed despite adequate context: false premise, a constraint ignored with the instruction visible, or inaccurate self-report |

Each finding also gets a **symptom tag** from S4's seven patterns, so recurring patterns can be counted across retros.

### 2. Blameless formats adapted to agents

- Google SRE's core premise transfers directly: assume everyone "did the right thing with the information they had" [S9]. The agent equivalent is to reconstruct **what was in the context window at the decisive point**. Most "model" failures turn out to be context failures.
- **Critiques of 5 Whys:**
  - Allspaw argues that "Why?" is "the wrong question" and that asking "how?" brings out the conditions behind an event, where "why" brings out blame [S10].
  - Code for America recommends descriptions over explanations, and says repeated "why" "leads to speculation, to judgment clouded by hindsight bias" [S11].
  - For LLM reviewers this matters even more, because an LLM asked "why" will confabulate a tidy causal chain.
  - **Recommendation:** use "how did it make sense at the time" prompts anchored to quoted transcript lines. Allow at most one *branching* causal chain, as AWS's 5-Whys guidance permits for multiple causes [S12]. Every link must cite a transcript location.
- **Keep learning and remediation as separate passes** [S11]: action items are a non-goal of the learning pass and are produced in a follow-up. The kentra-rpir equivalent is a two-phase retro: phase A writes the narrative and contributing factors; phase B turns them into proposed edits. This keeps the evidence from being bent to fit a fix someone already has in mind.
- **The fields an agent postmortem needs** [S8]: activity logs, system documentation and version info, and tool information.
  - For kentra-rpir, snapshot the *versions of every skill and plugin loaded*, the model per agent, the plan and spec commit, and the CLI versions (lifecycle and mpd pins).
  - Without these, a finding saying "the skill told it X" cannot be reproduced after the skill changes.
  - TianPan proposes a "prompt revision state" field for LLM incidents along the same lines **(secondary)**.

### 3. Making findings actionable

- **Converting failures into evals is the industry-standard loop.** Anthropic recommends "20–50 simple tasks drawn from real failures" as a starting set, a split between capability evals and regression evals, and graduating passing capability evals into the regression suite. It also says you will not know whether graders work "unless you read the transcripts" [S7]. OpenAI's skill-eval guidance likewise says 10–20 prompts per skill is enough to catch regressions **(secondary/vendor blog, not re-fetched)**.
- **Actionability rule**, synthesised from Uptime Labs and incident.io, which say to replace "improve monitoring" with a specific target and owner and that below 50% completion the process is "theater" (secondary). Every `spec-retro.md` finding must end in exactly one of:
  1. A concrete diff to a named skill, prompt, agent definition or CLAUDE.md, quoted as a before/after.
  2. A new eval case: the prompt, the fixture commit, and a pass criterion.
  3. A regression test or deterministic check: a hook, a lint, or a gate in mpd or lifecycle.
  4. A spec or plan template change.
  5. An explicit "no action — accepted model variance" with its reasoning.

  A finding that cannot name its target artefact is not finished.
- **Validate skill edits differentially.** SkillTriage [S6] compares runs with and without the skill. For kentra this means re-running the eval case with the old and new skill version before accepting the edit. This matches memory's "verifiers diff old vs new binaries" lesson.
- **Track recurrence across retros.** Count symptom-tag occurrences over successive `spec-retro.md` files. A pattern that appears three or more times escalates from a skill tweak to a process or gate change. This is a design proposal, not sourced.

### 4. Keeping the reviewer from restating the self-report (author≠verifier)

- **Self-preference bias is measured.** LLM evaluators recognise their own generations and rate them higher, and self-recognition correlates causally with that self-preference [S13].
- **Self-reports are unreliable.** 22.58% of misalignment episodes involve inaccurate self-reporting [S4].
- **Protocol:**
  1. **Blind pass first.** The transcript readers *do not get* `impl-report.md`. They receive the spec, the plan, the diff, the gate and test outputs, and the transcripts. They produce their own timeline and their own claims of what was done.
  2. **Reconcile pass second.** A separate agent compares the blind timeline with `impl-report.md` claim by claim and labels each claim `confirmed`, `contradicted` or `unsupported`, with a transcript citation. Contradicted claims are findings in their own right, tagged Inaccurate Self-Reporting.
  3. Ideally use a **different model or effort tier** for the reviewer than for the implementer, to weaken the self-preference link. Panels of different judges agreed with humans more than single judges did (92% vs 83%, from the collector excerpt of the EMNLP 2025 survey) **(unverified figure)**.
  4. **Evidence-anchored output only.** Each claim needs a session id and a line or uuid. Uncited claims are dropped. This follows the Tembo verifier-filter pattern (secondary).

### 5. Long, multi-session and multi-agent transcripts

- **Scale is real.** This research session's own main transcript is about 4 MB of JSONL, with a subagents directory beside it. One implement run with N parallel milestones produces N or more subagent transcripts.
- **Proven compression method.** METR compressed long coding-agent transcripts by summarising assistant actions with GPT-4o "while preserving code diffs", describing "what the agent did at a high level, NOT at the tool-call level", and then judged the compressed form with GPT-5. The judge was within 2–3x of human estimates (r_log 0.83 over 34 samples). METR names the main judge bias: it tends to "assume a task is successful even if the user quits" [S16].
  - **Implication:** compression works for *what happened*. It loses the decisive-point evidence (exact tool outputs and the assumption text), so the pipeline needs a second, targeted step that re-expands those parts.
- **Recommended pipeline:**
  1. **Deterministic pre-pass (no LLM):** parse the JSONL. Extract:
     - the tool-call sequence;
     - errors and non-zero exits;
     - retries of the same command;
     - files touched against the plan's declared `paths`;
     - test and gate results;
     - user and orchestrator interventions;
     - compaction events;
     - token and cost totals.

     Emit a skeleton timeline per agent. This is cheap, reproducible, and catches the lucky-pass signals from [S5]: blind retries, missing verification, ordering. It also catches the diff-outside-paths signal.
  2. **Parallel per-milestone readers** (a cheaper model, one per subagent transcript). Each gets the skeleton and the raw transcript, chunked at turn boundaries when needed. Each outputs a narrative timeline, candidate decisive points and quoted evidence. Each starts with a fresh context and does not see `impl-report.md`.
  3. **Cross-milestone synthesiser** (a stronger model). It looks for MAST-style inter-agent issues across handoffs, merges duplicate findings, and assigns `SPEC/SKILL/PROCESS/ENV/MODEL` with a fault side.
  4. **Reconcile against `impl-report.md`** (section 4).
  5. **Remediation pass:** propose concrete edits and eval cases (section 3).
- **Cost control:**
  - Use the step-1 signals to pick which milestones get a full read. Every red milestone gets one, and so does every green milestone with signals such as retries, diff-outside-paths or skipped verification. Clean green milestones get a skim.
  - The cost numbers per approach are not established in the literature **(gap)**.
- **Existing tooling:** `/insights` already performs cross-session friction analysis [S15]. It covers the last 30 days of all sessions, and only blog sources describe that window **(unverified)**. It is useful for spotting trends across retros, but it is not scoped to one change, so it does not replace the per-change retro.

### 6. What `spec-retro.md` should contain

1. **Header:** change id; spec, plan and merge commits; models per role; skill and plugin versions loaded; CLI pins; sessions analysed, with paths; and the reviewer model.
2. **Outcome summary:** 2–3 sentences. The acceptance criteria verdict comes from the gates, not from the report.
3. **Self-report reconciliation table:** each claim, the verdict, and the evidence.
4. **Timeline per milestone:** plus a merged cross-milestone view, with decisive points marked.
5. **Findings.** Each one has:
   - an id;
   - a symptom tag (S4 pattern);
   - a fault class (`SPEC/SKILL/PROCESS/ENV/MODEL`) with an edge and side;
   - the decisive step with a citation;
   - "how it made sense at the time" (the context present then);
   - severity, judged by effort and trust cost versus correctness;
   - a lucky-pass flag where it applies.
6. **What went well:** behaviours to keep, so that a skill edit does not regress them [incident.io, secondary].
7. **Proposed actions:** each is one of the five actionable types, with a target file, the diff or eval spec, and how it will be validated (differential re-run).
8. **Recurrence:** symptom tags compared with previous retros.
9. **Open questions for the user:** items the reviewer could not decide from evidence.

## Risks and open questions

- **LLM retro reviewers share the failure modes they diagnose:** confabulated causes, over-trusting success, and self-preference [S13][S16]. The mitigations are blind-first reading, a mandatory citation for every claim, and the user's review in the Review stage as the final human gate.
- **Step-level attribution is still weak in the literature.** The trajectory-analysis survey says step-level attribution accuracy "remains limited" (collector excerpt) **(unverified)**. Treat decisive-point labels as hypotheses that need user confirmation.
- **Skill bloat.** Retros naturally propose *adding* guidance. [S6] shows added procedure causes failures and efficiency regressions. Require every proposed skill edit to say what it removes or why the extra length pays for itself, and validate it differentially.
- **The transcript format is undocumented** beyond the hooks' `transcript_path`, and the docs say it is written asynchronously [S14]. The parser in step 1 will break on Claude Code upgrades. Pin it to the Claude Code version and test it against real transcripts, per the "verify against real artifacts" memory.
- **Compaction loses evidence.** If a long implement session auto-compacts, the decisive context may survive only as a summary. The retro should flag compaction events and treat findings that span them as lower confidence.
- **Open: per-retro cost.** No source quantifies the cost of a full read versus a signal-sampled read. Measure it on the first 3–5 kentra-rpir retros and set a budget.
- **Open: when a light debrief is enough.** No source gives a threshold. A candidate rule: run the full retro if any milestone escalated, any gate failed once, any diff went outside paths, or any self-report claim was contradicted; otherwise run only the deterministic pass and the reconciliation.
- **Inference not checked against the paper:** that [S4]'s finding of rising self-report share generalises to Claude Code specifically **(unverified)**. Its dataset is "real-world sessions" across agents.
- **Some percentages are unchecked.** Figures quoted only by collectors from secondary blogs (Cycles 2026, TianPan 46% infrastructure, Composio 89%) were not checked and are not used in the conclusions.

## Sources

1. Cemri et al., "Why Do Multi-Agent LLM Systems Fail?" arXiv 2503.13657 v3 (2025-10-26), CC BY-NC-ND 4.0. https://arxiv.org/abs/2503.13657
2. Zhao et al., "Failure as a Process: An Anatomy of CLI Coding Agent Trajectories," arXiv 2607.09510 (2026-07-10). https://arxiv.org/html/2607.09510v1
3. Raj et al., "Model or Harness? An Interaction-Centric Taxonomy for Localizing Agent Failures," arXiv 2607.28802 (2026-07-30). https://arxiv.org/abs/2607.28802
4. Tang et al., "How Coding Agents Fail Their Users," arXiv 2605.29442 v2 (2026-08-31). https://arxiv.org/html/2605.29442v2
5. Sahoo et al., "AgentLens: Revealing the Lucky Pass Problem in SWE-Agent Evaluation," arXiv 2605.12925 (rev. 2026-10-05). https://arxiv.org/abs/2605.12925
6. Dong et al., "Agent Skills Can Be Harmful: An Empirical Study of Skill-Induced Failures in LLM Agents," arXiv 2608.11888 (2026-08-12). https://arxiv.org/abs/2608.11888
7. Anthropic, "Demystifying evals for AI agents" (2026-01-09). https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents
8. Ezell, Roberts-Gaal, Chan, "Incident Analysis for AI Agents," AIES 2025, arXiv 2508.14231. https://arxiv.org/abs/2508.14231
9. Google SRE Book, "Postmortem Culture." https://sre.google/sre-book/postmortem-culture/
10. J. Allspaw, "The Infinite Hows" (2014-11-13). https://www.oreilly.com/radar/the-infinite-hows/
11. Code for America, "From Accident to Investment: How to Run Better Blameless Postmortems" (2018-08-14). https://codeforamerica.org/news/from-accident-to-investment-how-to-run-better-blameless-postmortems/
12. AWS, "Using 5 Whys analysis in incident reports" (CloudWatch docs; collector excerpt, not re-fetched). https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/incident-report-5whys.html
13. Panickssery, Bowman, Feng, "LLM Evaluators Recognize and Favor Their Own Generations," arXiv 2404.13076 (2024-04-15). https://arxiv.org/abs/2404.13076
14. Claude Code docs, Hooks reference (`transcript_path`, async write). https://code.claude.com/docs/en/hooks
15. Claude Code docs, Commands reference (`/insights`). https://code.claude.com/docs/en/commands
16. METR, "Analyzing coding agent transcripts to upper bound productivity gains" (2026-02-17). https://metr.org/notes/2026-02-17-exploratory-transcript-analysis-for-estimating-time-savings-from-coding-agents/
17. Secondary, not re-verified: incident.io postmortem best practices; Uptime Labs PIR guide; Tembo "AI Agents for Code Review"; TianPan LLM postmortem fields; OpenAI "Testing agent skills with evals"; Microsoft hve-core RPI docs (https://github.com/microsoft/hve-core/blob/main/docs/rpi/using-together.md).
