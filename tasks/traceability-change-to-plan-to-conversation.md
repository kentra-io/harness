# TODO: end-to-end traceability — code change → plan → archived planning session

**Status:** captured 2026-07-10, idea only. Raised by the user.

## The idea

If every code change can be traced back to **the plan** that authorized it, and
that plan back to **the conversation (archived planning session)** that produced
it, we get a continuous audit chain over the whole codebase:

```
commit / diff  →  spec-lifecycle change (plan + gates)  →  archived planning session (the reasoning)
```

Ask "why does this line exist?" and you can walk: the merge → the approved plan
delta → the gate approvals → the actual conversation where the decision was
argued and consented to. That's real **auditability and traceability** — not just
"who changed it" (git blame) but "what plan sanctioned it and what reasoning led
to that plan."

## Why this is cool / worth doing here specifically

We're already building most of the substrate; this is about **linking the layers
that already exist** rather than net-new machinery:

- `spec-lifecycle` already produces the plan artifacts + gate records (the
  consent boundary). That's the middle link.
- `orchestration` already drives an approved plan → merged code through the cast,
  with diff-confined-to-declared-paths. That's the change→plan link, and it's
  enforced, not aspirational.
- `adr-sourced-constitution` already event-sources the standing decisions.
- The missing/weak link is **plan → conversation**: archiving the planning
  session (the brainstorm/design transcript) as a first-class, referenceable
  artifact, and stamping its id into the change so the trail is navigable.

## Things to work out when we pick this up

- **Where the link lives.** Commit trailer (e.g. `Change-Id:` / `Plan:` /
  `Session:`)? Field in the spec-lifecycle change metadata? Both, so it's
  reconstructable from either end?
- **What "archived planning session" means concretely.** A stored transcript
  (Claude Code session export)? A distilled decision record? Raw + distilled?
  Privacy/size: full transcripts are large and may contain noise/secrets.
- **Stable ids.** Sessions, plans, and changes each need durable ids that survive
  rebases/squash-merges so the chain doesn't break on history rewrite.
- **Who writes the link.** The orchestrator at merge time is the natural point
  (it already owns the change and the diff). For human-authored changes, a hook
  or the lifecycle CLI.
- **Overlap with existing concerns.** This is adjacent to the parked
  `kentra-sdlc` deferred concerns (TODO capture + documentation) and to the
  constitution's "reasoning is sourced" ethos — decide whether it's its own
  primitive concern or a thin cross-cutting convention.

## Open question

Is the valuable artifact the **full conversation** (maximal fidelity, messy) or a
**distilled decision record derived from it** (clean, but a lossy projection)?
That framing decides whether this is mostly a storage/linking problem or also a
summarization/curation problem.
