# spec-lifecycle backlog — grouped for a dedicated session

> Note (2026-07-24): change 007-yaml-source-of-truth (in flight) supersedes
> ADR-0001/0002 (OpenSpec-format conformance) referenced below, and its
> approved scope implements issue #5's Option-2 slugs — re-check items #1 and
> #3 against the new YAML/JSON-Schema model before picking this up.

**Status: intelligence + design directions, grouped 2026-07-19 for a separate
agent session.** These are the **planning-leg** remarks collected while
dogfooding the `spec-lifecycle` primitive (mostly on the kafka-dq testbed).
Almost none have been implemented — several are explicitly "intelligence only,
needs a brainstorm before any change." This doc consolidates them so one
session can pick up the whole bucket.

**Where fixes land:** the `spec-lifecycle` repo
(`/Users/jony/code/kentra/harness/spec-lifecycle`) — its `skills/`,
`internal/schema/templates/`, and the Go fold/validate engine — unless noted.
That repo is governed by its own constitution (ADR-0001/0002: it conforms to
the OpenSpec on-disk **format** and proves it with a static **conformance
corpus** — any change that touches the format must survive that corpus). Run
`lifecycle status` and route through its gates. Each item below is likely its
own lifecycle change; #3 needs a brainstorm first (real tension).

---

## 1. Structured contracts — root cause still owed

**Symptom (fixed) / cause (open).** The first real `/lifecycle-plan` run
produced a `tasks.md` with prose-only contracts — no ` ```contract ` blocks
(`check`/`criteria`/`paths`) and no `[ ]` checkboxes — so `lifecycle apply`
returned `contract: null` for every milestone and the execution engine's L1 /
diff-paths / archive gates would have run **vacuously**. Symptom was remediated
in kafka-dq on 2026-07-14; the **root cause is unaddressed.**

**Two owed fixes (both in `spec-lifecycle`):**
- **Template.** `internal/schema/templates/tasks.md` (and its projection into
  every repo's `openspec/schemas/kentra-spec-lifecycle/templates/tasks.md`)
  still shows the bare four-label shape — no contract block, no checkboxes. The
  `lifecycle-plan` skill *claims* "the template shows both"; make that true.
- **Skill-refresh mechanism.** Installed skill copies (`.claude/skills/…` in
  consuming repos) are copied once at `lifecycle init` and **drift silently** as
  the primitive evolves — the plan session ran on a stale copy predating the
  §5.5 grammar. Owed: a `lifecycle init --refresh-skills` verb (or a documented
  re-copy-on-version-bump convention). This is the general "installed artifact
  drifts from source" problem, not specific to contracts.

**Policy (user-locked 2026-07-14):** contracts are **mandatory for us** — every
milestone carries a ` ```contract ` block (executable `check`, plain-language
`criteria`, allowed `paths`) + checkbox-tracked steps, authored **at planning
time** (planning owns the contract; execution never re-authors it). `paths`
must include the change's own `tasks.md` and the repo-root `deviation.json`
(the Implementer writes both). The grammar stays opt-in for OpenSpec
back-compat, but our skills/templates must make it the default output.

**Rule of thumb learned:** a machine-consumed grammar addition must land in the
authoring surfaces (template + skill + fan-outs) in the **same change** that
adds the parser.

**Source:** `tasks/plan-structured-contracts-gap.md`.

---

## 2. Refine converges before it elicits (systemic "formalize-before-elicit")

**Problem.** `lifecycle-refine` drafts `proposal.md` + the spec delta **solo**,
then asks for a thumbs-up. The user expected a **question-driven requirements
interview** moving the issue `initialized → refined`. Verdict: "really bad."
The skill's only human touchpoint is a review-and-approve gate at the *end* —
there is no elicitation phase at the *start*.

**This is one face of a systemic pattern** — the primitives' skills *formalize
before they elicit*. Same root cause showed up three times:
- `lifecycle-refine` (this item).
- `constitution-init` drafting founding ADRs from a brain-dump with no
  rule-vs-bet triage (`tasks/constitution-init-retro.md`).
- `adr-draft` folding a half-formed sketch straight into an ADR (lesson: "Any
  thoughts? means brainstorm, not draft").

**Owed:** a first-class "elicit requirements with the human" phase before any
artifact touches disk. To brainstorm: is it a distinct pre-refine step, or a
mandatory opening phase *inside* `lifecycle-refine`? What forces it — signal
words + a settledness gate (as adr-draft got), or something stronger (a stated
contract that the agent MUST interview and MUST NOT write artifacts until
requirements are confirmed)? Does the same belong at design→plan? Pure skill
text vs. an explicit "eliciting" sub-state in the lifecycle model?

**Cross-ref:** `adr-sourced-constitution`'s v0.2 proposal already names this
pattern and is addressing its own instance (founding-as-a-phase: `phase:
draft|sealed`, `adr edit/rm/seal`, interactive multi-stage init) — approved
2026-07-19, not yet built. Whatever elicitation shape we pick for refine should
stay consistent with that.

**Where it lands:** primarily `spec-lifecycle/skills/lifecycle-refine/SKILL.md`
(fan out to `.claude/`, `.cursor/`, `.agents/` — regeneration is part of any
edit); possibly `lifecycle-design`/`lifecycle-plan`; possibly a new sub-state
in the primitive's spec.

**Source:** `tasks/lifecycle-refine-retro.md`, `tasks/constitution-init-retro.md`.

---

## 3. Stable identifiers for requirements / scenarios (and capabilities)

**Problem.** Requirements are keyed by their **free-text header name**;
scenarios have **no key at all** (a `MODIFIED` requirement is a *wholesale
replacement* of the requirement and all its scenarios — you cannot target one
scenario). So modifying/deleting a requirement or scenario is neither
straightforward nor fully deterministic, and scenario-grain diffs /
conflict-detection / traceability are impossible.

**The tension (why this isn't just "add IDs").** Name-as-key is **inherent to
the OpenSpec format we deliberately conform to** (ADR-0001/0002 + the
conformance corpus). Any identifier scheme is a potential divergence and must
survive the corpus.

**User proposal (2026-07-19):** derive an ID = **kebab-case slug of the header
name**, and **validate uniqueness within a scope** (project / spec /
capability).

**Assessment (record this — it's the key insight):**
- **Cheap and format-compatible.** A slug *derived* from the header is
  computed, not stored — invisible to a stock OpenSpec parser, so it stays
  corpus-compatible. This is the low-cost end of the option space.
- **What it buys:** (a) a deterministic, URL/anchor-friendly **handle** for
  cross-artifact traceability links (feeds #4); (b) **uniqueness validation** —
  catches the "two same-named requirements" ambiguity (spec §12) and casing/
  whitespace typos; (c) normalization.
- **What it does NOT buy: rename-stability.** Because the slug is *derived from
  the name*, renaming the header changes the slug — the **same failure mode as
  name-as-key**. A truly stable ID must be **independent of the name** (e.g. an
  invisible stored ID in an HTML comment / trailing annotation — the note's
  "Option 2"), which the Go fold keys on when present and name otherwise.
- **Scenario *targeting* still needs more.** A scenario slug gives an
  addressable handle, but the delta grammar still replaces the whole
  requirement — scenario-grain edits need **scenario-level delta ops** (the
  note's "Option 3", the biggest divergence, touches the deferred
  sub-requirement-grain fold, spec §11).

**So:** slug + uniqueness-validation is a **good, low-risk first step** for
validation and anchoring — adopt it for those. But be explicit that it is *not*
the stable-ID that rename-survival and scenario-grain deltas require; those are
a separate, larger decision:
1. Derived slug (cheap; no rename-stability) — the user's proposal.
2. Stored invisible ID (rename-stable; needs a corpus-tolerance check).
3. Scenario-level delta ops (most power; most divergence).
Possibly a mix (IDs at requirement grain only; scenarios still wholesale).
Re-check against the conformance corpus (ADR-0002) whichever way.

**Scope for uniqueness:** within a spec/capability is the natural grain
(OpenSpec keys requirements within a capability; two same-named requirements
across capabilities is a distinct §12 edge case).

**Where a decision lands:** a proposal in the primitive
(`spec-lifecycle/docs/proposal-*.md`); touches delta grammar (§2), fold
`buildUpdatedSpec` (§6.1), the determinism edge cases (§12), and the delta
template.

**Source:** `tasks/lifecycle-identifiers-note.md`.

---

## 4. Traceability: change → plan → conversation

**The idea.** Make every code change walkable back to **the plan** that
authorized it and that plan back to **the conversation** (archived planning
session) that produced it: `commit/diff → spec-lifecycle change (plan + gates)
→ archived planning session (the reasoning)`. Answer "why does this line
exist?" by walking merge → approved plan delta → gate approvals → the actual
decision conversation.

**Connected to #3 and to the orchestration milestone-commit.**
- Stable IDs (#3) are the **anchors** this walk needs at the spec grain.
- The **commit trailer** decision in `agent-orchestration` issue #9
  (milestone-commit) is exactly where the `change → commit` link would be
  written (`Change-Id:` / `Milestone:` / possibly `Session:`). Decide the
  trailer there and here together.

**Weak link = plan → conversation:** archiving the planning session as a
first-class, referenceable artifact and stamping its id into the change. Most
of the substrate exists (spec-lifecycle plans+gates; orchestration's
diff-confined change→plan link; constitution's sourced decisions) — this is
about *linking layers that already exist*.

**Open:** where the link lives (commit trailer / change metadata / both); what
"archived session" is (raw transcript vs distilled decision record vs both —
privacy/size); stable ids surviving rebase/squash; who writes the link
(orchestrator at merge time / a hook / the lifecycle CLI). Adjacent to the
parked `kentra-sdlc` deferred concerns (TODO capture + documentation).

**Source:** `tasks/traceability-change-to-plan-to-conversation.md`.

---

## Cross-cutting notes for the session

- **#3 → #4:** identifiers are the substrate; traceability is the goal built on
  them. They can move independently (identifiers carry the format tension;
  traceability is more a linking convention), but decide the **commit-trailer**
  shape once, shared with `agent-orchestration` #9.
- **#2 spans two primitives** (spec-lifecycle refine + adr-sourced-constitution
  founding) — keep the elicitation shape consistent across both.
- **#1's skill-refresh** is a general primitive-hygiene fix, not
  contracts-specific — it prevents the whole class of "installed skill drifted
  from source."
- **Sequencing suggestion:** #1 (mechanical, unblocks correct plans) and the
  slug-half of #3 (cheap validation win) are the low-risk quick wins; #2 and
  the stored-ID/scenario-ops half of #3 need real brainstorms; #4 is the
  largest and depends on #3's anchor decision.

## Source notes (all in `harness/tasks/`)
- `plan-structured-contracts-gap.md`
- `lifecycle-refine-retro.md`
- `constitution-init-retro.md` (systemic-pattern sibling)
- `lifecycle-identifiers-note.md`
- `traceability-change-to-plan-to-conversation.md`
- Related orchestration issue: `kentra-io/agent-orchestration#9` (milestone-commit trailer).
