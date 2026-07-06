# Lessons

Patterns captured after user corrections, to prevent repeats.

## Runtime-agnostic is a first-class v1 requirement (2026-06-16)
**Correction:** When asked, the user said the planning module must be **runtime-agnostic** — "no good reason it should be Claude-specific" — and the target scenario is **running multiple coding agents**. This reverses planning.md §0.1's "claudebox-first, defer multi-runtime" lean.
**Rule:** Do NOT assume Claude-Code-native is acceptable for v1. For any layer where agent-agnostic tooling exists (e.g. OpenSpec/Spec-Kit generate artifact commands for ~30 agents), weight multi-agent support as a present requirement, not deferred. Re-check planning.md §0.1/§6 against this before recommending a Claude-specific build.

## Tool liveness & governance is a first-class selection criterion (2026-06-17)
**Context:** `observability.md`'s 2026-06-16 draft picked TensorZero as the strongest all-in-one alternative on verified *features*. Within a day it was invalidated — TensorZero reportedly unmaintained, and Helicone had been acquired by Mintlify into maintenance mode (no roadmap, cloud users migrated off). Meanwhile Langfuse had been acquired by ClickHouse (active, MIT commitment).
**Rule:** When evaluating any tool for adoption, check **maintenance status, ownership/acquisition, release cadence, and roadmap** alongside features — and weight it as a gating criterion, not a footnote. A feature-complete but frozen/abandoned tool loses to a maintained one. Re-verify liveness at decision time (it changes fast), and record acquisition/governance facts in memory since they invalidate prior research.

## Verify framework capabilities against primary sources before concluding (2026-06-16)
**Context:** Prior `references/spec-kit-ecosystem-research.md` logged OpenSpec only as a delta/archive *model* and **missed its schema system** (declarative, forkless artifact-set redefinition). That omission would have wrongly pushed the planning layer toward a custom build.
**Rule:** Before settling "custom vs adopt," re-verify each candidate's *current* extension/customization mechanism against its live docs — research files can be stale or incomplete. Schema/extension/preset systems materially change the build-vs-adopt math.

## Surface orchestration progress; track implementer mistake-rate explicitly (2026-07-02)
**Correction:** During the adr-sourced-constitution build the user interrupted asking "are you stuck? I didn't see much progress" — three PRs were already merged, but all activity lived inside subagents and terse status notes.
**Rule:** When orchestrating subagents on long runs: (a) post a visible progress marker after every merge/milestone (PR link, CI status, what changed), not only when asked; (b) maintain and report the *failed-fix-attempt* count per implementer as the mistake-rate metric (fix requested → fix landed correctly first try = 0; anything else counts); (c) when the user nudges toward a stronger model twice, switch — don't re-defend the split. Model split in effect from M3: Opus implements, Sonnet does spec-compliance checks, Opus does code-quality review.

## Dispatch fix rounds to fresh subagents, not resumed transcripts (2026-07-03)
**Context:** A fix batch sent via SendMessage to the M3 implementer's existing (very large) transcript ran 2.5h without landing a commit; the user pinged "seems like you got stuck." A fresh agent given only the branch + findings list completed all 12 fixes in 14 minutes.
**Rule:** Resume an existing agent only for delivering a report or answering questions about work it already did. Any new work — fix rounds included — goes to a freshly spawned agent with tightly curated context (branch, findings, verification requirements). Also: monitor background agents by PR commits + transcript mtime, and intervene when active-but-not-landing exceeds ~30 min.

## Watchdog must be mechanical, not intentional (2026-07-03)
Latency retro found a SECOND M3 incident I never noticed: the quality reviewer hung silently for 2.1h mid-turn (then "Connection closed mid-response", +45min recovery). I had already written the "intervene at ~30min" lesson — and didn't apply it, because nothing prompted me to look. Rule: when dispatching any subagent expected to run >15min, immediately schedule a wakeup/check at 40min; on wakeup, check transcript mtime/PR commits; if stalled, TaskStop + fresh dispatch. A lesson that relies on remembering to check is not a lesson; encode it as a timer.

## Windows CI is the recurring blind spot for Linux-sandboxed agents (2026-07-04)
**Context:** During the spec-lifecycle build, three consecutive milestones failed Windows CI legs after passing local (Linux) verification and adversarial review: (1) CRLF checkout mangling byte-exact fixtures — fixed with `.gitattributes * -text`; (2) chmod-based error-path tests (dir write bits / 0o000 files not enforced on Windows); (3) ENOTDIR-provoked errors that Windows classifies as not-exist. Verifiers checked "portability" but only for patterns already seen.
**Rule:** In a repo with a Windows CI leg but Linux-only local execution: (a) byte-canonical repos get `.gitattributes * -text` at bootstrap (M0), not after the first failure; (b) any test provoking filesystem errors goes through a shared `testutil` skip-guard package created at bootstrap — bare `runtime.GOOS`/`Geteuid` checks in test bodies are a review finding; (c) implementer + verifier prompts must name the *class* (permission bits, error classification, path separators, EOL) not just past instances; (d) treat "verified locally" as "verified on Linux" until CI proves otherwise — check CI after every push, before the next milestone builds on it.
