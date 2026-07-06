# SDD-Framework Research — P0 decision input (2026-07-02)

*Four parallel research agents (Superpowers, OpenSpec, Spec-Kit re-eval, bespoke-baseline + field scan), all scoring the same rubric R1–R9 derived from [tasks/planning-module-handoff.md](../tasks/planning-module-handoff.md). All claims verified against live primary sources 2026-07-02. Supersedes [spec-kit-ecosystem-research.md](./spec-kit-ecosystem-research.md) for the P0 question.*

**Rubric:** R1 staged artifacts + human gates · R2 folder-per-issue + file-record interface · R3 living-spec rollup + archive/compaction · R4 runtime-agnostic · R5 clean seam to `adr-sourced-constitution` · R6 extend-without-fork + upgrade path · R7 does not own orchestration (Conductor-compatible) · R8 maturity mid-2026 · R9 validation contracts / plan granularity / bug repro-first.

## Consolidated scorecard

| R | Superpowers (v6.1.0) | OpenSpec (v1.5.0) | Spec-Kit (v0.12.4) |
|---|---|---|---|
| R1 gates | ⚠️ hard gate at design only; plan gate soft; no requirements stage | ⚠️ artifact chain real; philosophy *rejects* gates ("enablers, not gates") | ⚠️ soft run-next-command convention |
| R2 folder+records | ❌ flat dated files; zero record concept | ✅ `changes/<name>/` exactly folder-per-unit; our records coexist cleanly | ⚠️ folders yes, records no |
| R3 living-spec+archive | ❌ nothing | ✅ **best-fit feature** — deterministic single-change archive-merge into `specs/<capability>/`; cross-change conflict = LLM-judgment | ❌ absent; maintainers still debating it (Discussion #152) |
| R4 runtime-agnostic | ✅ 9 harnesses, harness-agnostic skill bodies (Gemini dropped — Google EOL'd Gemini CLI 2026-06-18) | ✅ 30 tools, adapter-per-tool | ✅ 30+, strongest |
| R5 constitution seam | ✅ skill loads constitution.md + gate = sanctioned shape | ✅ config.yaml context injection works today; custom schema = maintainers' own endorsed ADR path (#557) | ⚠️ fights authored-mutable UX; route-around only |
| R6 extend-no-fork | ⚠️ companion plugin sanctioned (core refuses skill PRs); ~monthly breaking releases → pin | ✅ schema system real, project-owned, production existence proof (#536); but only ~5 months old, open edge-case bugs | ❌ `--force` clobbers customizations AND non-force blocks upstream fixes (#2319 open) — no safe path |
| R7 no orchestration | ✅ intra-session task loops only | ✅ tasks.md = checklist artifact; no scheduler | ❌ **core now ships a resumable YAML workflow engine with gates/branching/run-state** (v0.10+) |
| R8 maturity | ✅ 244k★, funded, active; thin bus factor (essentially obra) | ✅ 58k★, MIT, monthly+ releases, engaged maintainers | ✅ 117k★, ~daily releases, GitHub+MS |
| R9 contracts/bug-flow | ⚠️ `systematic-debugging` = strong repro-first match; no milestone-contract object | ❌ tasks.md unstructured checkboxes | ⚠️ nothing native |

## Cross-cutting findings

1. **Nobody supplies the novel spine.** R1 hard gates, R9 milestone validation contracts, the bug repro-first flow, and file gate-records are net-new in *every* scenario. The frameworks differ only in which *plumbing* they contribute. (Baseline agent's verdict: "a framework is not load-bearing.")
2. **Spec-Kit is eliminated as a base.** New core `workflows` orchestration engine = direct Conductor collision (the Spec-Kitty problem, now in core); upgrade path actively worsened (#2319); living-spec unresolved upstream. Demoted to pattern-mine (`/speckit.analyze` design). This *validates* the 2026-07-02 retraction. Steel-man (narrow command-generator use) buildable but requires permanent discipline for little gain.
3. **Superpowers and OpenSpec are complementary, not competing.** Superpowers = execution-phase disciplines (TDD, root-cause debugging w/ repro-first, subagent dispatch/review, worktrees) + cross-runtime skill delivery; explicitly refuses core contributions → forced clean companion-plugin shape. OpenSpec = artifact/lifecycle plumbing (change-folders, delta format, deterministic archive-merge, 30-tool command generation). Both R7-clean.
4. **Bespoke is viable:** distribution solved (SKILL.md open standard ~40 platforms + the constitution primitive's already-built fan-out); records trivial (formats already designed); scaffolding trivial; **living-spec rollup is the one large piece** — OpenSpec's algorithm portable as a pattern.
5. **P6 design insight (significant):** OpenSpec proves the living-spec can be a **deterministic projection** — if feature-specs are written as *structured deltas* (ADDED/MODIFIED/REMOVED requirement blocks), folding into the living spec is mechanical, not LLM-synthesis. That would align the functional side with the constitution's event-sourcing philosophy (notes.md: "shared element: event sourcing docs") and eliminate the "agent-synthesized projection needs review/governance" problem for the common case. Cross-change conflicts stay judgment-based even in OpenSpec.
6. **Field scan: nothing displaces the candidates.** BMAD-METHOD (~49k★, staged pipeline) = closest new entrant but gates are agent-self-enforced (inverse of our files+engine split) → pattern-mine (frontmatter resumable state). Kiro → mine EARS notation. GSD → mine STATE.md resumability. Tessl/Jira/Antigravity/Cosmos → not candidates. Antigravity notably *builds on* Spec-Kit (its artifact shape is becoming the industry reference shape).
7. **Hygiene:** Gemini CLI EOL'd by Google 2026-06-18 → drop from runtime-target list. Superpowers' "SDD" = *subagent*-driven development — acronym collision with our spec-driven usage; glossary line needed. Before generalizing skill fan-out, check if Cursor's new `.agents/skills/` support makes `.cursor/skills/` redundant.

## Recommendation (2026-07-02)

**Composite, narrow roles:**
- **OpenSpec = artifact runtime** for what it's unambiguously good at today: custom `openspec/schemas/<ours>/` encoding our vocabulary, its CLI for command generation (30 tools) + validate + deterministic single-change archive/living-spec merge. Pin the version (LiteLLM precedent). **Ejection stays cheap because files are canonical** — if the young schema subsystem disappoints, port the archive algorithm into a bespoke `lifecycle` CLI and keep the same folders/records.
- **Superpowers = co-installed companion** for execution disciplines (TDD, debugging/repro-first, subagent patterns); our planning module ships as our own plugin/skills alongside; pin version.
- **Ours regardless (the actual planning module):** stage definitions + gates, `approval-state.json`/`deviation.json` records, milestone validation contracts, bug repro-first workflow, constitution adapter, Conductor integration.
- **Spec-Kit / BMAD / Kiro / GSD:** pattern-mines only.

*Decision status: **ACCEPTED by the user 2026-07-02** (all three: OpenSpec runtime / structured-delta living-spec / Superpowers companion), with one amendment — full stock-OpenSpec conformance: the async-validation scenario tag was deferred to [roadmap-ideas.md](../roadmap-ideas.md). Implemented in the [`spec-lifecycle`](../spec-lifecycle/spec-lifecycle.md) primitive spec.*
