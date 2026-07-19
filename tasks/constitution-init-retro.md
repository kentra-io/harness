# constitution-init retrospective — kafka-dq founding (2026-07-10 → 07-13)

**Status: acted on 2026-07-19 — direction approved and planned in
`adr-sourced-constitution/docs/proposal-v0.2-next-iteration.md` (draft/seal
phase, multi-category rules, goal-statement preamble, staged interactive init,
skill fixes). This file remains the evidence record.**
Collected 2026-07-13 from three transcript-summarization passes over the founding
session (`f3b54a2f`, 2026-07-10) and the stack-decision session (`124424c2`,
2026-07-10 → 07-13), plus direct experience executing the second reset.

## Headline finding

The kafka-dq constitution was **wiped and regenerated twice in its first three
days** — for two different proximate reasons with one shared root cause: the
tooling models founding as a one-shot event, but founding is a **phase**. Both
resets were the user manually authorizing what the tool has no concept for.

## What happened

### Wipe #1 — projection-model collision (2026-07-10, founding session)

- The init flow drafted a few broad ADRs; the tool maps one ADR → one category
  → one constitution section. Two ADRs landed under `purpose`, other sections
  stayed empty. User rejected hard ("Delete the constitution… This is shit.
  We need to talk before we continue").
- Log hand-deleted (uncommitted, no data loss), re-seeded as 5 one-per-section
  ADRs via 5 × `adr new`.
- The agent had noticed the constraint ("this leaves the other sections empty")
  but steered around it instead of surfacing it as a blocker.
- Design debt filed then: `adr-sourced-constitution/docs/proposal-multi-section-adrs.md`.

### The seed of wipe #2 — unvalidated bets welded into a structural rule

- The founding brain-dump mixed durable structure (mission, hexagonal, testing
  discipline) with **unresearched tech bets** (Janino engine, Iceberg/S3 state
  store, UI in v1, AutoMQ/Tansu brokers).
- The Iceberg/S3 + UI clause was drafted **inside ADR-0002's rule** (the
  hexagonal charter) — a tech bet hitched to unrelated durable content.
  Nothing in the interview asked "is this validated, or an assumption research
  could kill?"

### Wipe #2 — history-less reset after research killed the bets (2026-07-13)

- Research outcomes (docs/research/ in kafka-dq): Janino archived July 2026;
  AutoMQ frozen on Kafka 3.9.1 with no 4.x plan; every broker-native-Iceberg
  option failed an OSS/4.x bar; MinIO abandoned; user then simplified v1 to
  violations→Kafka-topic, dropping Iceberg/S3 entirely.
- A proper supersession of ADR-0002 was fully drafted and accepted — then the
  user overrode the mechanism: *"we're still initializing, we don't need proper
  history"* → regenerate the whole initial ADR set instead.
- Mechanics of the reset (all workaround, no first-class support):
  1. Manual `rm` of `constitution/adr/*.md`, `.manifest.sha256`,
     `constitution.md` — violating the tool's own never-hand-touch invariant.
  2. 9 × `constitution adr new` to re-seed (4 bodies carried forward verbatim,
     1 rewritten, 4 new stack ADRs).
  3. First `adr new` failed: under `consent: strict` the CLI's interactive
     `[y/N]` cannot be answered in a non-TTY agent shell. `--approve` exists
     for exactly this ("required under the strict consent policy when stdin is
     not a terminal") but no skill documents it; discovered via `--help` after
     the failure.
  4. `constitution guard` reported 6 violations mid-reset (frozen `date`
     changed, body changed vs HEAD) — correct behavior, but there is no way to
     express "authorized re-baseline"; it only went clean after the commit
     moved HEAD.
  5. Rule-length warnings (ADR-0002: 18 lines, ADR-0004: 10 lines vs 5-line
     guideline) repeated on every subsequent `adr new` — noisy, ignorable,
     ambiguous whether action is expected.

### Adjacent skill finding (adr-draft, not init)

Mid-session the agent folded a half-formed user sketch (BFF/SQLite/checkpoint)
straight into an ADR draft; user: *"I didn't want you to draft what I wrote. I
wanted you to brainstorm with me on how do we solve it."* adr-draft has no
settledness gate — nothing requires confirming a decision is actually settled
before formalizing it. (Also captured in `tasks/lessons.md` 2026-07-13.)

## Recommendations (proposals to evaluate — NOT adopted)

1. **Init phase as a first-class CLI concept** (feature; needs spec work in
   `adr-sourced-constitution`). `constitution.yml` gains `phase: draft|sealed`
   (or equivalent). In draft phase: a `constitution reinit`-style regenerate is
   legal and guard-aware (no supersession chain, no manual `rm`, dates/IDs
   handled deliberately); `constitution seal` flips to true append-only.
   The init skill stops selling finality: it should end by telling the user the
   constitution stays in draft until sealed — expected after the first
   research/spec pass validates the founding bets. Both wipes were users doing
   this by hand because the concept doesn't exist.
2. **Interview triages rules vs bets** (skill-text change, cheap). Per founding
   principle, a third question beyond rule-vs-record: *"validated, or an
   assumption research could invalidate?"* Unvalidated tech choices become
   record-only ADRs or an explicit parked list — never clauses inside a
   rule-bearing structural ADR. Enforce "one decision per ADR" at founding too.
3. **Front-load the projection model** (skill-text change). Before category
   selection, explain: sections = categories; each ADR lives in exactly one;
   a section renders the concatenation of its rule-bearing ADRs. Wipe #1 was
   purely this expectation gap. (Related: multi-section-ADR proposal already
   filed in the primitive.)
4. **Document agent-shell consent mechanics** (skill-text change, init +
   adr-draft + gov). Under `strict` in a non-TTY shell, `[y/N]` always fails;
   `--approve` is the sanctioned path with the harness permission prompt as the
   human consent gate. Say so explicitly instead of letting every agent
   discover it via a failed write.
5. **adr-draft settledness gate** (skill-text change). One line: if the
   decision emerged from brainstorming/a sketch, get explicit confirmation it
   is settled before drafting. Signal words for *not settled*: "brainstorm",
   "thoughts?", "how could we", "something like".
6. Minor: rule-length warning should fire once per offending ADR at creation,
   not repeat on every subsequent write; guard could distinguish "working tree
   ahead of HEAD mid-operation" from genuine tamper when reporting.

## Where changes would land

- Items 2–5: `adr-sourced-constitution/skills/*/SKILL.md` (fan-out via the
  managed skill trees; regeneration command is part of any edit).
- Item 1 (+6): CLI features — through the primitive's own spec/plan process;
  precedent: `docs/proposal-multi-section-adrs.md`.

## Continued dogfood notes — user, 2026-07-14 (verbatim capture, not adopted)

Fresh learnings from continuing the kafka-dq dogfood. Both sharpen findings
already above; recorded in the user's own framing so intent isn't lost.

1. **Init should be an explicit staged process, and the skill should say so.**
   The user wants `constitution-init` to stop presenting founding as one shot
   and instead walk a named sequence:
   1. establish **purpose + very-high-level design** (a first ADR pass), then
   2. **research the technologies**, then
   3. a **second ADR covering the technical architecture** informed by that
      research.
   This is the concrete shape behind retro recommendation #1 ("init is a
   phase") and #2 ("triage rules vs bets"): the unvalidated tech bets don't get
   welded in up front because the technical-architecture ADR comes *after* the
   research step, by design. Action lands in
   `adr-sourced-constitution/skills/constitution-init/SKILL.md` (skill-text) —
   make the staged process explicit; likely also a CLI init-phase concept
   (retro #1). Open design question the user flagged: this needs a brainstorm
   on how the process is shaped (skills vs CLI vs both).

2. **One ADR must be able to carry rules from MORE THAN ONE category.**
   Re-raised by the user as **"very important, we need to change it here."**
   Today one ADR → one category → one constitution section; this was the direct
   cause of Wipe #1 (see above). Already filed as design debt:
   `adr-sourced-constitution/docs/proposal-multi-section-adrs.md`. This note
   records the user re-prioritizing it from "filed" to "must-change."

3. **Lifecycle `refine` finding** — different primitive; captured separately in
   [`tasks/lifecycle-refine-retro.md`](./lifecycle-refine-retro.md).

## Continued notes — user, 2026-07-19 (verbatim capture, not adopted)

Thought-dump ahead of tuning the constitution-creation process; not decisions.

1. **Init must be interactive and multi-turn by design.** The initialization
   process should be an interactive process that might take a few turns — the
   skill should be designed *for* that, not treat it as a one-shot interview
   feeding a single `constitution init`.
2. **Richer, opinionated category suggestions.** The skill should explicitly
   suggest constitution categories framed as concrete project decisions, e.g.:
   - How do we track issues?
   - What agents do we build for? (drives AGENTS.md vs CLAUDE.md vs both)
   - Do we go spec-driven development, and with which framework?
   - How do we manage our spec?
   - What's the project structure?
   These should be discussed with the user while writing the constitution
   draft — an opinionated question catalog, not just the generic starter
   category list (`architecture, code-style, process, testing, security, data`).
3. **Draft → seal lifecycle.** constitution-init should create a *mutable
   constitution draft*; only after drafting is done does it become immutable.
   (User: "Seems like the right idea." Converges independently with retro
   recommendation #1 — `phase: draft|sealed` + `constitution seal`.)

## Source pointers

- Founding session transcript: `~/.claude/projects/-Users-jony-code-kentra-harness/f3b54a2f-….jsonl`
- Stack/reset session: same dir, `124424c2-….jsonl`
- kafka-dq research reports: `kafka-dq/docs/research/` (commit `33843d0`)
- Reset commit: kafka-dq `6b1ee6a` (9-ADR regenerated constitution, guard clean)
