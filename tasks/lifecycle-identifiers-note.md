# spec-lifecycle — requirement/scenario identifiers (2026-07-14)

**Status: intelligence only. No decision taken; not adopted.** Design
consideration raised by the user while dogfooding kafka-dq. Needs a brainstorm
before any change — there's a real tension (below), so this is NOT a
slam-dunk "add IDs."

## The observation (confirmed against the engine)

Requirements and scenarios are plain markdown with **no stable identifiers**.
Verified in `spec-lifecycle.md` and the delta template:

- **Requirements are keyed by their free-text header name.** Fold applies ops
  "keyed by requirement name" (§6.1, §6.4; op order RENAMED→REMOVED→MODIFIED→
  ADDED). The `### Requirement: <name>` text *is* the key.
- **Scenarios have no key whatsoever.** `MODIFIED Requirements` instructs:
  "paste the requirement's ENTIRE existing block … and edit it; a partial
  block loses detail at archive time." So you cannot target one scenario —
  every requirement edit is a **wholesale replacement** of the requirement and
  all its scenarios.
- `REMOVED` = bare `### Requirement: <name>` header (name is the key);
  `RENAMED` = `FROM:`/`TO:` text pairs (renaming = an explicit delta op
  precisely because the name is load-bearing).

So the user is right on both points: modifying or deleting a requirement or
scenario is **not straightforward and not fully deterministic** without an
identity separate from the prose.

## Why it bites

- **Name-as-key is brittle.** A typo in the target name is a
  `MODIFIED`-of-nonexistent (already a flagged edge case, §12); two same-named
  requirements across capabilities are ambiguous (also §12); any wording
  change to a requirement's title needs a `RENAMED` op or the fold silently
  treats it as a different requirement.
- **Scenarios are un-addressable.** No way to say "modify scenario 3" or
  "delete this scenario" — you restate the whole requirement. That makes
  scenario-grain diffs, conflict-detection, and traceability coarse (the whole
  requirement shows as touched even for a one-line scenario tweak).
- Ties into the traceability thread already parked in
  `tasks/traceability-change-to-plan-to-conversation.md` — stable IDs are what
  you'd anchor cross-artifact traceability on.

## The tension (why this needs a real decision, not just "add IDs")

Identifier-free, name-keyed matching is **inherent to the OpenSpec on-disk
format we deliberately conform to** — see `spec-lifecycle/constitution`
ADR-0001/0002 ("reimplement the OpenSpec format", "prove compatibility with a
static conformance corpus") and spec §2. OpenSpec itself is name-keyed.
Introducing stable IDs is a **divergence from the format** and could weaken
the conformance-corpus bet. So the question is framed like the
`agent-definition` "conform-to-format / own-a-thin-extension" pattern: is
identity a thin extension we own on top of the format, or does it break the
conform-to-format stance?

## Options to brainstorm (NOT decisions)

1. **Status quo** — name-as-key + lean on `RENAMED`. Cheapest; keeps pure
   format conformance; accepts scenario-wholesale and brittle name matching.
2. **Invisible stable IDs, format-compatible** — carry an ID in an HTML
   comment / trailing annotation on each `### Requirement:` (and optionally
   `#### Scenario:`) line. Invisible to a stock OpenSpec parser (stays corpus-
   compatible) but our Go fold keys on the ID when present, name otherwise.
   Buys deterministic MODIFIED/REMOVED targeting without a visible format
   change. Needs: does the conformance corpus tolerate the annotation?
3. **Scenario-level delta ops** — a real grammar extension (`MODIFIED
   Scenarios` or per-scenario ops). Most power, biggest divergence from the
   format; hits §11's deferred "sub-requirement-grain fold" territory.
4. Some mix — IDs at requirement grain only (cheapest useful step), scenarios
   still wholesale.

## Where a decision/change would land

- Eventual home = a proposal in the primitive: `spec-lifecycle/docs/
  proposal-*.md` (mirrors `adr-sourced-constitution/docs/proposal-multi-
  section-adrs.md`). This note is the harness-side capture until then.
- Touches: delta grammar (§2), fold `buildUpdatedSpec` (§6.1), the
  determinism edge cases already listed (§12), and the delta template.
- If pursued, re-check against the conformance corpus (ADR-0002) first — that
  bet is what any identifier scheme has to survive.

## Source pointers

- `spec-lifecycle/spec-lifecycle.md` §2, §6.1, §6.4, §11, §12
- Delta template: `spec-lifecycle/…/templates/spec.md`
- `spec-lifecycle/constitution/constitution.md` ADR-0001/0002
- Sibling dogfood notes: `tasks/lifecycle-refine-retro.md`,
  `tasks/traceability-change-to-plan-to-conversation.md`
