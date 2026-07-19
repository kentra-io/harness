# lifecycle-refine retrospective — kafka-dq dogfood (2026-07-14)

**Status: intelligence only. No decisions taken; nothing here is adopted.**
Captured from the user while dogfooding `spec-lifecycle` on kafka-dq. Notes
for later; the user explicitly flagged this needs a **brainstorm on how to
design the process** before any change.

## Headline finding

`refine` converges before it elicits. The user wrote an issue description,
created the change, and ran the `refine` step **expecting a question-driven
interview** that would move the issue `initialized → refined` — lots of
questions surfacing and pinning down requirements — with a similar
elicitation step later to arrive at a plan. Instead **the spec draft
(`proposal.md` + spec delta) was produced with zero input from the user.**
The user's verdict: "really bad."

## Why it happens (grounded in the current skill)

`spec-lifecycle/skills/lifecycle-refine/SKILL.md` describes the stage as:
"Draft `proposal.md` and every touched capability's spec delta" → `validate`
→ surface to human → wait for approval → `approve`. The human touchpoint is a
**review-and-approve gate at the end**, not a **requirements interview at the
start**. There is no elicitation phase in the skill — nothing tells the agent
to interview the user to build the requirements *with* them before drafting.
So a capable agent does exactly what the text says: it drafts the whole
artifact solo, then asks for a thumbs-up.

## This is the same systemic pattern we've already hit twice

The primitives' skills **formalize before they elicit** — converge to an
artifact instead of exploring the option/requirement space with the user
first. Prior instances:

- `adr-draft` folding a half-formed sketch straight into an ADR — see
  `tasks/lessons.md` "Any thoughts? means brainstorm, not draft" (2026-07-13)
  and the adr-draft settledness-gate proposal in `constitution-init-retro.md`.
- `constitution-init` drafting founding ADRs from a brain-dump with no
  rule-vs-bet triage — `constitution-init-retro.md`.

`refine` is the third face of one root cause: **no first-class "brainstorm /
elicit requirements with the human" phase before the artifact is drafted.**

## To brainstorm later (NOT decisions)

- Where does elicitation live — a distinct pre-refine interview step/skill, or
  a mandatory opening phase *inside* `lifecycle-refine` that must run before
  any draft touches disk?
- What forces it? The `adr-draft` fix was signal-word + settledness-gate text.
  Refine may need something stronger: a stated contract that the agent MUST
  interview to elicit requirements, and MUST NOT write `proposal.md`/spec
  deltas until the requirements are confirmed with the user.
- Does the same elicitation belong at the `design` → `plan` transition the
  user also expected to be question-driven? (Design's input is the approved
  refine artifacts; if refine now elicits properly, does design still need its
  own interview, or only plan?)
- Skill-text change vs CLI/state support (mirrors the constitution "init is a
  phase" question) — can this be pure skill guidance, or does the lifecycle
  state model need an explicit "eliciting" sub-state?

## Where changes would land

- Primary: `spec-lifecycle/skills/lifecycle-refine/SKILL.md` (fan-out via the
  managed skill trees — `.claude/`, `.cursor/`, `.agents/`; regeneration is
  part of any edit, per `tasks/lessons.md` 2026-07-06).
- Possibly `lifecycle-design` / `lifecycle-plan` skills if elicitation is
  needed at those transitions too.
- Possibly the primitive's own spec/plan if a new sub-state is warranted.

## Source pointers

- Current skill: `spec-lifecycle/skills/lifecycle-refine/SKILL.md`
- Sibling finding (constitution): `tasks/constitution-init-retro.md`
- Systemic-pattern lesson: `tasks/lessons.md` (2026-07-13)
