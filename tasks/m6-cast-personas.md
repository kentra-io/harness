# M6 — Cast prompts + live verification (prompt-design risk)

> **STATUS: SHIPPED 2026-07-09.** Personas built, wired, and live-verified
> (default Claude Code toolset for all cast agents, user-locked). Kept as the
> original build spec.

**Module:** `agent-orchestration`, branch `m6-cast-personas` (off M7 tip `3800269`).
**Deps:** M1 (provider seam) ✅, M2 (claudebox isolation) ✅, M4 (harness) ✅ — all met.
**Auth invariant (P11):** subscription OAuth only, never `ANTHROPIC_API_KEY`. Live runs cost nothing beyond normal subscription usage inside claudebox.

## What M6 delivers (spec §6 + §5.2)

Turn the placeholder `model: sonnet` cast in `workflows/milestone.yaml` into the **three real personas** as hand-materialized `.claude/agents/<role>.md` (P9), each encoding its behavioral contract, then prove them live.

### 1. The three personas (the creative core — drafted by Opus)
- `personas/implementer.md` — §6.2 MUST/HALT: work tasks top-to-bottom one milestone at a time; every change traces to a task+requirement or STOP; ambiguity → QUESTION halt (no guessing); deviation → log to `deviation.json` before proceeding; MUST NOT edit spec/tasks content, mark done without evidence, expand scope, or touch out-of-path files. **Tool posture (revised 2026-07-09, user-locked): default Claude Code toolset for ALL cast agents — no per-role tools:/disallowedTools surgery; web constrained in use (never a source of requirements), not in surface; eval runs may restrict at materialization.**
- `personas/verifier.md` — §6.3 / §5.2: fresh agent, never saw Implementer reasoning; coverage matrix (requirement → evidence, no evidence = UNMET); runs L1+L2; grades L3 against anchored rubric → `0.0–1.0` + hard pass/fail; intent-vs-actual diff (undeclared deviation → FAIL; false completion `[x]`-no-change → FAIL; real deviation not in `deviation.json` → FAIL); Read/Grep + may run tests, **no Write**; reports, does not fix.
- `personas/orchestrator.md` — §6.1: stateless resolver; input = Verifier report + Implementer issue + diff + plan; output = next-attempt guidance (re-scope/context/tighten/re-order) or infeasible signal; does NOT decide escalation, spawn agents, or hold state.

**Model assignment (spec §6):** Implementer Opus/medium, Verifier Opus/high, Orchestrator Opus/high. *(Placeholder was `sonnet`; flag to user — easily tuned in frontmatter.)*

### 2. Wire personas into workflows
- `milestone.yaml`: agents reference personas via the provider's `--agent <role>`; models updated per §6; inline `prompt:` stays as per-invocation task context (persona = durable contract).
- Materialization: personas ship in module `personas/`; testbed/launcher copies them into `<worktree>/.claude/agents/`. (Branded layer may override later.)

### 3. Live-tier DoD (fixture testbed, self-skips without env vars)
`tests/test_m6_live_verification.py` (marker `live`), each scenario a real `cb exec … claude -p --agent <role>`:
- **(a) undeclared deviation** — feed Verifier a fixture worktree whose diff touches a file outside the path-set / untraceable → Verifier FAIL.
- **(b) false completion** — a task ticked `[x]` with no corresponding change → Verifier FAIL.
- **(c) clean milestone** — passes all three layers (L1 exit 0, L2 green, L3 pass) → PASS.
- **(d) ambiguity halt** — Implementer given a deliberately under-specified task → emits QUESTION, does not produce a diff.
- ~~(e) no web tools~~ — **DROPPED 2026-07-09 (user-locked):** all cast agents ship the default toolset; no tool-restriction assertion in the DoD.
- **(f) L3 well-formed** — verdict parses to `0.0–1.0` + pass/fail against the rubric.

Verifier scenarios feed **pre-planted** fixture state (don't rely on the Implementer to misbehave) — same discipline as M4's planted-defect catalogue.

**Note:** the M1b live test asserts `cost_usd > 0`; on subscription this is Claude Code's token-based *estimate* field (not a real charge) — still > 0, consistent with P11.

## Build sequencing
1. [Opus] Draft the three personas — the load-bearing quality work.
2. [Sonnet impl] Wire into `milestone.yaml`, add `personas/` materialization, author the fixture testbed + `test_m6_live_verification.py`.
3. [Opus verify] Review personas + wiring + test scaffold against §5/§6 (author≠verifier).
4. [live] Run the DoD against a real box on subscription; capture proof.
5. PR → module `main`; bump harness submodule pointer.

## Open decisions surfaced to user
- Model assignment: follow spec §6 (all Opus) vs. the older memory note ("Sonnet spec-checks"). Recommend §6; tunable in frontmatter.
- Where the neutral personas live (`personas/` in module) vs. branded cast in harness — recommend neutral defaults in module, branded override later (matches §11.2 "AGENT SURFACE: cast contracts" as a module deliverable).
