# TODO: establish + enforce the `.claudebox/tmp` scratch convention

**Status:** captured 2026-07-10, not yet tackled. Raised by the user mid-M9.

## The rule (already in effect for the agent)

Never write scratch / "review-these-bytes" files to `/tmp` inside claudebox —
`/tmp` is container-ephemeral and invisible to the operator on the host, so it
defeats the purpose of showing something for review. Use `<project>/.claudebox/tmp/`
instead (it's in the bind-mounted working tree → host-visible), and gitignore it.

This is a **general** claudebox rule, not kafka-dq-specific.

## Why this is a #todo (the part to come back to)

The behavioral rule is captured in agent memory, but it isn't *enforced* — nothing
stops an agent (or a spawned Mode-B cast agent) from writing to `/tmp`. Options to
evaluate when we pick this up:

- A convention doc / line in the primitive + harness `AGENTS.md` so it's loaded
  context, not just per-agent memory.
- A claudebox-level default: pre-create `.claudebox/tmp/`, and/or point `TMPDIR`
  at it for *interactive* boxes (careful: the orchestrator's P4 checkpoint
  relocation already owns `TMPDIR` per-change for Mode-B runs — don't collide).
- A hook (PreToolUse on Write/Bash) that rejects writes under `/tmp` and suggests
  `.claudebox/tmp/`.
- Decide whether the folder name/location should be standardized across all
  primitive repos (kafka-dq, agent-orchestration, claudebox itself).

## Open question

Is this purely a visibility/hygiene rule, or also security (keeping agent scratch
inside the audited mount)? That framing decides whether enforcement is advisory
(lint/hook) or a hard boundary.
