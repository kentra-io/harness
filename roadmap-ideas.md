# Roadmap ideas — deferred enhancements to evaluate

*Not commitments. Each entry is a proposition parked deliberately; evaluate when the owning module is next revised. Add entries with date + owning module + the trigger that should prompt evaluation.*

> The **agent-runtime / orchestration / auto-eval** domain has graduated from a parked idea to an active plan (Stages 3–5) — see [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md). Its earlier sketch here (omnigent-as-runtime, oh-my-openagent personas, GEPA auto-evals) was **evaluated and discarded**; the rows below are the sub-ideas from that evaluation that remain deliberately deferred.

| Added | Owning module | Idea | Why deferred | Evaluate when |
|---|---|---|---|---|
| 2026-07-02 | spec-lifecycle (planning module) | **Async-validation tag for perf/load scenarios** — mark spec scenarios (e.g. "WHEN load is X THEN p99 < Y") as excluded from milestone acceptance, validated by asynchronous benchmark runs instead (per the P2 decision: perf tests live in the codebase, run alongside dev, not per-milestone). | Conform to stock OpenSpec first — zero schema-delta surface for v1; the tag is our only proposed divergence and OpenSpec may grow scenario metadata natively. | First project that adopts a continuous-perf-testing ADR; or if untagged perf scenarios cause milestone-gate confusion in practice. |
| 2026-07-05 | runtime (Stage 3) | **microVM runtime swap (microsandbox) in place of Docker/claudebox.** Stronger-than-container isolation, sub-100ms boots, native on Apple Silicon (libkrun). | Evaluated 2026-07-04: not clearly lower-maintenance than Docker, still beta, and Firecracker is disqualified on Mac — claudebox/Docker stays the runtime. | When per-agent hardware isolation becomes a real requirement (e.g. running untrusted code), or claudebox maintenance burden clearly outweighs the swap cost. |
