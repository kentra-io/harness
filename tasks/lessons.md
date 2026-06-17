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
