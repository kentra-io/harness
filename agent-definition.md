# `agent-definition` — Design Specification

*Version: v1 draft. Generated: 2026-07-06. Status: **DESIGN — pending user review.***

*A standalone, framework-neutral primitive: a tiny declarative schema describing **one AI coding agent** (its persona, its skills, its model) plus a Go loader/compiler that materializes that neutral definition into a concrete runtime agent. It adopts the **Agent Format on-disk envelope** (`.agf.yaml` — `metadata` / `interface` / `execution_policy`; JSON-Schema-validated; official Go parser) and **owns a thin extension** for the two things Agent Format does not cover — `skills` and a runtime-`harness` field — plus **experiment slots** for the Stage-5 A/B controller. Same play as [`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md) with OpenSpec: **conform to a neutral format's shape, own a small Go engine, take no runtime dependency.** The **neutral `.agf.yaml` file is the single source of truth**; a compiler renders it into a Claude Code agent inside claudebox (`.claude/agents/<role>.md`), and a Conductor step drives it — never Conductor's default `claude` provider. Consumed by the kentra harness (Stage 3), but — like the sibling primitives — framework-portable and not harness-bound.*

*Decision provenance: the harness's [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md) (Stage-3 design + locked decisions, 2026-07-04/05), the two `deep-research` passes it cites (agent-runtime/orchestration/auto-eval; agent-schema/UI), and a format-landscape verification pass (2026-07-06) that confirmed Agent Format, Omnigent, A2A AgentCard, Letta `.af`, and Claude Code frontmatter against live primary sources. Sibling to [`adr-sourced-constitution`](./adr-sourced-constitution/) (governance) and [`spec-lifecycle`](./spec-lifecycle/) (planning); this is the **agent-abstraction** leg of Stage 3. Repo/CLI names settled (§11): framework-neutral `agent-definition` / `agentdef`.*

---

## 0. Terminology (locked)

- **Agent definition (`def`)** — one `.agf.yaml` file describing a single agent: its `metadata`, its persona (`instructions`), its `model`, its `skills`, and (deferred) its runtime `harness`. The neutral source of truth.
- **Role** — the human-facing name of a def (`implementer`, `tech-lead`). One role ↔ one file (`agents/<role>.agf.yaml`).
- **Persona** — the materialized behavior of a role: system prompt + skill set + model, rendered into a runtime-specific agent artifact.
- **Materialization / compile** — the deterministic transform `def → runtime agent`. For the claudebox runtime: `def → .claude/agents/<role>.md`, referenced by `claude -p`. The neutral file stays the source of truth; the materialized artifact is a build product.
- **The Agent Format envelope** — the on-disk convention we conform to: the `.agf.yaml` extension and the top-level `schema_version` / `metadata` / `interface` / `execution_policy` structure (Agent Format v1.0, Snap Inc. + Agent Format Authors, Apache-2.0, JSON-Schema-validated, Go parser). We stay envelope-compatible so an Agent Format tool can read our files; the fields it lacks (`skills`, `harness`) ride as **extensions** (§4).
- **Extension** — a key we own that Agent Format does not define (`skills`, `harness`, experiment slots). Carried as OpenAPI-style specification extensions or inside `execution_policy.config`, pending the extension-validation spike (§11).
- **Runtime / harness** — the execution target a def compiles to (`claudebox` in v1). Named after Omnigent's `executor.harness` field, taken as an *idea* now and wired as a real field when a second runtime appears (§3, deferred).
- **Experiment slot** — a def field marked variant-overridable, so the Stage-5 controller can inject per-variant values (`model`, a skill-set, a persona fragment) without rewriting the def (§6).
- **Launch context** — *who* started an agent process: a human-launched interactive session vs. an orchestrator-spawned headless agent. Approval capability binds to launch context, **not** to the def (§7). This is the consent invariant inherited from the Stage-3 handoff.

## 1. Purpose & scope

**What it is.** The minimal, framework-neutral description of an agent, plus the engine that turns it into a running one. It answers exactly one question — *"what is this agent, declaratively?"* — with the smallest field set that lets the harness (a) run heterogeneous agents against one workflow and (b) A/B them in Stage 5.

**What it is NOT (owned elsewhere):**
- **Not orchestration.** No routing, no `when`, no run-state, no scheduling. That is Conductor (Stage 3). A def is an *input* to a Conductor step, never a workflow.
- **Not governance.** Approval, budgets, constraints, and policy are **not** def fields (§7). Governance lives in [`adr-sourced-constitution`](./adr-sourced-constitution/) + [`spec-lifecycle`](./spec-lifecycle/) gates. The card describes *behavior only*.
- **Not a runtime.** It compiles to a runtime's agent shape; it does not execute the agent. Claude Code / claudebox runs it.
- **Not the cast.** The concrete agents a project runs (BA, Tech Lead, Implementer…) are **consuming-project data**, exactly as ADRs are data to the constitution primitive. The primitive ships the schema + engine; kentra's cast is an example (§12), not part of the neutral core.

**The design spine (mirrors the sibling primitives):** *conform to a neutral format · own a tiny deterministic Go engine · minimal fields, everything else deferred · no language-runtime dependency · files are the canonical interface.* Where `spec-lifecycle` conforms to OpenSpec and owns a fold engine, `agent-definition` conforms to Agent Format and owns a compile engine.

## 2. Position in the stack

```
  Conductor (Stage-3 orchestration, external)          ← routes/`when`/for_each; a step is a
      a step names a def + a launch context               THIN driver over a compiled def
  ─────────────────────────────────────────────
  ClaudeboxProvider (harness glue, fork-carried)       ← compiles def → `claude -p` invocation
      NEVER Conductor's default `claude` provider          inside claudebox; NOT the raw API
  ─────────────────────────────────────────────
  agent-definition (THIS) — single static Go binary    ← parse · validate · compile(materialize)
      owns the whole engine over the Agent Format          the neutral def → .claude/agents/<role>.md
      envelope + our thin {skills, harness, exp} ext
  ─────────────────────────────────────────────
  Agent Format on-disk ENVELOPE (convention, NOT a     ← .agf.yaml; schema_version / metadata /
      runtime) — .agf.yaml, JSON-Schema, Go parser        interface / execution_policy
  ─────────────────────────────────────────────
  claudebox / Claude Code (Stage-3 runtime)            ← materialized .claude/agents/<role>.md;
      always bypassPermissions; execs as non-root         `claude -p --agent <role>` (flag = spike)
```

**What "Agent-Format-compatible" means and costs.** We commit to the envelope and reimplement its load in Go (the format ships an official Go parser we can adopt or mirror):

1. **File convention** — `.agf.yaml`, top-level `schema_version` / `metadata` / `interface` / `execution_policy`. We keep the extension and structure so the tree reads as an Agent Format repo and stays re-adoptable (same reasoning as keeping `openspec/` in `spec-lifecycle`).
2. **Envelope semantics** — `metadata` (identity), `execution_policy.config.instructions` (persona), `execution_policy.config.model` (model), `execution_policy.id` (loop strategy). Our fields map onto these (§3.1).
3. **Extensions we own** — `skills` and `harness` are absent from Agent Format; experiment slots are ours entirely. They ride as extensions (§4) and our validator enforces them; stock Agent Format validation ignores them.

**What we drop by not adopting a heavier schema:** Omnigent's runtime-welded `tools`/`policies` (Python import-paths into a runtime we do not run); A2A AgentCard's discovery/communication surface (wrong category — no `system_prompt`/`model`); Letta `.af`'s stateful memory snapshot (a runtime snapshot, not a build-spec). **What we gain:** a JSON-Schema-validated, vendor-neutral, Go-native envelope with open governance, plus a thin owned extension for exactly the two gaps — no more schema than we need. **What we accept:** if Agent Format's grammar evolves, envelope parity is our explicit choice, proven by a small conformance corpus (§11) — the `spec-lifecycle` precedent exactly.

## 3. The schema — fields

### 3.1 Minimal v1 field set (deliberately tiny)

| Logical field | Agent Format home | Meaning |
|---|---|---|
| `id` / `name` / `description` | `metadata.*` | identity; `name` = the role |
| `system_prompt` | `execution_policy.config.instructions` | the persona / role instructions |
| `model` | `execution_policy.config.model` | the model id |
| *loop strategy* | `execution_policy.id` | Agent Format's run strategy (`agf.react`, `agf.sequential`); **pinned to a claudebox-appropriate constant in v1** — Claude Code owns the actual agent loop |
| `skills` | **extension** | which skills this agent has (borrow the A2A `AgentSkill` / Claude Code `skills:` shape) |
| *experiment slots* | **extension** | which of the above are variant-overridable (§6) |

That is the whole v1 schema: identity, persona, model, skills, and the experiment markers. Everything materially behavioral about an agent is one of these five.

### 3.2 Explicitly deferred fields

| Field | Why deferred / how designed to slot in |
|---|---|
| **`tools` / `mcps`** | Not v1. When added they are **references** (ids into a registry), never inline defs — so a def stays small and a tool is defined once. Design v1 so a `tools:` extension can appear without reshaping existing fields. |
| **`harness`** (runtime-portability) | Take the *idea* now (agents are runtime-agnostic), wire the field when a **second runtime** appears. Value vocabulary borrowed from Omnigent's `executor.harness` (`claude-sdk`, `codex`, `cursor`, …). Until then, the runtime is a compile-time argument (`--runtime claudebox`), not a def field. |
| **`interface`** (Agent Format I/O modes) | Present in the envelope; unused in v1 (our agents are driven by Conductor + files, not A2A message modes). Left empty/minimal; available for free later. |
| **`permissionMode` — dropped entirely** | The runtime is **always sandboxed ⇒ always `bypassPermissions`**; it is a **runtime-adapter constant**, not a per-agent knob. (Impl note: `bypassPermissions` is refused as root, so the box execs as the non-root `agent` user.) In Claude Code, bypass-mode and the tool-allowlist are **orthogonal** — capability boundaries live on the allowlist / launch context, never here (§7). |
| **governance-as-data** (approval / budget / constraints as declarative fields) | **Not needed and deliberately absent.** Governance lives in the constitution + `spec-lifecycle` gates + the launch-context consent boundary (§7). The card describes behavior; it never grants authority. |

The deferral list is a feature: the smaller the v1 card, the fewer ways an agent's *identity* and its *authority* can be conflated — which is the whole point of §7.

## 4. On-disk format & conformance

A def is one `.agf.yaml` file per role, under a project-owned `agents/` tree:

```
agents/
  implementer.agf.yaml     ← one role, one file
  tech-lead.agf.yaml
  verifier.agf.yaml
```

Sketch (illustrative — exact envelope keys pinned to Agent Format v1.0 at build):

```yaml
schema_version: "1.0"                    # Agent Format envelope
metadata:
  name: implementer
  description: Executes an approved plan milestone; TDD; repro-first on bugs.
execution_policy:
  id: agf.react                          # loop strategy (envelope); claudebox constant in v1
  config:
    model: claude-opus-4-8               # -> our `model`
    instructions: |                      # -> our `system_prompt`
      You are the Implementer. Work one milestone at a time against its
      validation contract. ...
# --- our extensions (below) ---
x-skills:                                # NOT an Agent Format field — ours
  - test-driven-development
  - systematic-debugging
x-experiment:                            # Stage-5 slots (§6)
  slots: [model, x-skills]
```

**The extension-validation question (the one real conformance decision, §11 spike).** Agent Format is JSON-Schema-validated and governed OpenAPI-Initiative-style, which *usually* implies specification extensions (`x-` keys) are permitted and ignored by stock validators. If confirmed, our `skills` / `harness` / experiment fields ride as `x-`-prefixed extensions and **stock Agent Format validation still passes** — the ideal. If instead the schema is `extra=forbid` (the exact concern `spec-lifecycle` §4 hit with OpenSpec), we **own the schema outright** — still Agent-Format-*shaped* and byte-readable, but validated only by `agentdef` (we lose stock-tool validation, keep the envelope). Either way the neutral file is the source of truth and `agentdef validate` is authoritative. Resolve at build (§11.1).

## 5. Compilation & materialization

The engine's one non-trivial job: turn a neutral def into a runtime agent. **Decided target (v1): materialize into a Claude Code agent inside claudebox**, then reference it by name.

```
implementer.agf.yaml  ──agentdef compile──▶  .claude/agents/implementer.md   ──▶  claude -p --agent implementer
   (neutral, SoT)         (deterministic)        (build product, in-box)            (Conductor step drives it)
```

The mapping is clean because Claude Code subagent frontmatter already carries our fields:

| def (neutral) | `.claude/agents/<role>.md` |
|---|---|
| `metadata.name` | frontmatter `name:` |
| `metadata.description` | frontmatter `description:` |
| `execution_policy.config.model` | frontmatter `model:` |
| `x-skills` | frontmatter `skills:` (**preload — see §5.1**) |
| `execution_policy.config.instructions` | the markdown body (system prompt) |

**Why materialize rather than compile-to-flags.** Two mechanisms were considered: (a) render `.claude/agents/<role>.md` and reference it (**chosen**), (b) translate the def to `claude -p` flags at call time. Both keep the neutral file as source of truth; (a) wins because it uses Claude Code's own first-class agent mechanism (skills auto-provision, one artifact to inspect inside the box) and keeps the invocation trivial. It **requires tuning claudebox** to place materialized personas on the container's `.claude/agents/` path — the one build cost, flagged in the Stage-3 handoff.

**ClaudeboxProvider (the Conductor seam).** A Conductor step must invoke the compiled agent through a custom **ClaudeboxProvider** (~250–400 LOC Python, fork-carried, reconned in `references/conductor-integration-notes.md`) that runs `claude -p` inside the box — **never Conductor's default `claude` provider**, which hits the raw Anthropic API and would never see the materialized agent, its skills, or claudebox. This is the resolution of the two-agent-abstraction collision (Claude Code `.claude/agents` vs Conductor `AgentDef`): our neutral file is the single source of truth; the Conductor `AgentDef` is a thin driver, and its `metadata` carries only correlation keys (one-line Conductor fork patch — its schema is `extra=forbid` — for issue-id / `experiment_id` / `variant`, the Stage-4 telemetry join).

### 5.1 Skill scoping — assignment vs. isolation (verified 2026-07-06)

The neutral card carries **one** skill field — `x-skills` = *"preload these"* — and that is deliberately all it carries. The Claude runtime distinguishes two things the word "skill" hides:

- **Assignment (preload).** `.claude/agents/<role>.md` frontmatter `skills:` **injects each listed skill's content at startup**. Confirmed semantics: it controls what is *preloaded*, **not** an access allow-list. A materialized agent can still invoke *unlisted* skills present in the (shared `~/.claude`) environment via the Skill tool. So `x-skills → skills:` gives assignment, not isolation.
- **Isolation.** Assignment alone is not isolation: preloaded-but-not-listed skills stay *discoverable* via the Skill tool, and a shared `~/.claude` puts the host operator's personal skills/plugins in every container. Three mechanisms were evaluated (research 2026-07-06); ranked by robustness and collateral:

  | Mechanism | Isolation | Collateral | Verdict |
  |---|---|---|---|
  | **Provisioning overlay** — per-agent dirs mounted over `~/.claude/skills` **and** `~/.claude/plugins` | strong (host scope *physically absent*) | none (auth, CLAUDE.md, MCP, worktree skills all intact) | **PRIMARY (default)** |
  | **`--bare` + explicit** — disable all auto-discovery, re-add exactly what's wanted | total (ignores all ambient config) | heavy — also nukes auth (OAuth→needs key), CLAUDE.md, MCP, **and worktree project skills**; skills must arrive as plugin-dirs | minimal-surface special case only |
  | **`disallowedTools: Skill` + preload** — agent can only use its preloaded set | weak (host skills present but unreachable) | none | not true "not-present" isolation; skip |

  **Decision (2026-07-06): provisioning overlay is the default**, made viable by the fact that **claudebox is our own vendored fork** (`claudebox/`) — the overlay seam is a small change to code we own, not a fight with a third-party tool. `CLAUDE_CONFIG_DIR` (relocate the whole user scope) was rejected: undocumented and broken (open upstream bugs; not referenced in our claudebox source). `--bare` is retained only for the rare minimal-surface experiment.

**Two channels feed a headless agent** (and the overlay only touches the first):
1. **Overlay (provisioned, per-agent):** `~/.claude/skills` + `~/.claude/plugins`, both seeded from the persona's `x-skills` (§5.2) and otherwise **empty** — so nothing from the host operator's personal scope leaks. This is the controlled channel.
2. **Worktree (free, per-repo):** the target repo's `.claude/skills` — where `lifecycle/constitution init` install the primitives' *ongoing* stage/governance skills. Rides the existing worktree bind mount, already isolated per-repo, untouched by the overlay. So the primitives' skills reach an agent *for free* via the repo it works on; channel 1 provisions only cross-cutting skills (execution disciplines) + any bootstrap plugin a repo-initializing persona needs.

**The abstraction consequence (unchanged):** isolation is a **compile-and-launch policy owned by the runtime adapter, not a card field** — same discipline as `permissionMode` being a runtime constant (§3.2) and `tools` being launch-context-bound (§7). `x-skills` stays a pure, runtime-agnostic assignment list; the claudebox adapter maps it to overlay dirs, and a second runtime's adapter maps it to *its* mechanism.

### 5.2 Provisioning & resolution — persona → skill/plugin sources

Seeding the overlay means turning `x-skills` *names* into actual skill/plugin *files*. Sources are **loosely coupled** — skills/plugins live in their own repos (spec-lifecycle, adr-sourced-constitution, external Superpowers), indexed by the `kentra-agentic-plugins` catalog — **not** co-located with personas. The shape is a package manager:

- **Personas hold references** (`x-skills: [names]`), never bodies.
- **A resolution step** (`agentdef compile`) reads references → pulls each from its source (catalog / repo) → assembles the per-agent skills+plugins overlay dirs. Optionally pinned by a **lockfile** for byte-reproducible variants (matters for Stage-5 A/B).
- **Not a GUI.** Management stays a **diffable manifest + CLI**, consistent with every other primitive here (file-canonical, no GUI anywhere). A GUI would only ever be a thin later layer over the same manifest, justified only at cast scale we don't have.

**Phasing (v1 = hardcoded, tooling later).** Prove the risky part first: v1 ships a **fixed source map / vendored copies** for the known cast + the claudebox overlay seam, and demonstrates end-to-end isolation. Deferred to a later stage: the general reference→source resolution manifest, lockfile, `agentdef` resolve/provision verbs, and skill-set **bundles** (named groups like `execution` so personas reference a bundle, not five skills). Same discipline spec-lifecycle shipped with — mechanism before ergonomics.

**Flag confirmations (fold of the §11.2 spike):** `claude -p --agent <role>` runs the session *as* that named agent and applies its full frontmatter (`skills`, `tools`/`disallowedTools`, `model`) — materialization path (§5) sound. Residual caveat: `--agent` in headless on *older* CC versions is under-documented — verify against the pinned version at build.

## 6. Experiment slots — the Stage-5 seam

Stage 5 is **online champion–challenger A/B over agent config** (not GEPA text-mutation). The def is where a variant is *addressable*: an agent declares which of its fields the controller may override per variant.

- **Declaration.** `x-experiment.slots` lists variant-overridable fields (e.g. `[model, x-skills]`). A field not listed is fixed across variants.
- **Injection (determinism-preserving).** The Stage-5 controller produces a **variant list**; Conductor `for_each`es over it, and each iteration compiles the def **with the slotted fields overridden** by that variant's values. Explore/exploit is decided **at launch** (not mid-run), so a workflow run stays deterministic — the reproducibility rule the whole stack depends on.
- **Per-variant skill injection = a per-variant overlay dir (§5.1), not `--bare`.** When a variant overrides `x-skills`, compile assembles that variant's skills overlay and mounts it — changing **only** the skill set while holding MCP, CLAUDE.md, governance, and model constant. This is *cleaner than `--bare` for A/B*: `--bare` would strip far more than skills, muddying the comparison unless every stripped thing is re-added identically. The overlay isolates the one variable by construction. (`--bare` + explicit provisioning remains the fallback only for a rare "minimal-surface" experiment.) This corrects the earlier draft, which made variant isolation `--bare`-based before the overlay mechanism (§5.1) was chosen.
- **Telemetry join.** Each compiled variant carries `experiment_id` + `variant` in the Conductor step's correlation metadata (§5), so Stage-4 (LiteLLM + Langfuse) can attribute per-variant cost/quality. Designed here so Stage 5 can *read* what Stage 4 records.

v1 ships only the **declaration + override + provisioning contract** (the def-side seam). The controller, the evaluator (objective for the bug flow, agent-graded for feature milestones), and the champion-promotion policy are **Stage 5**, out of scope here.

## 7. Consent / governance boundary — why authority is not a card field

This is the load-bearing non-obvious rule, inherited verbatim from the Stage-3 handoff (locked 2026-07-05):

**Approval capability binds to the *launch context*, never to the def.** `lifecycle approve` / `archive` are **never** in a Conductor-spawned agent's tool surface — self-approval is *structurally* impossible, not merely prompt-gated. Two approval paths, both human-present:

- **(a) Human-launched interactive session** (host or claudebox) carries the `lifecycle-approve` skill and approves conversationally, writing `approval-state.json` directly — no Conductor round-trip.
- **(b) Headless workflow** approves via Conductor's `human_gate` → a Conductor step runs the verb with `--approve`.

Because authority rides the launch context, the def needs **no** approval/governance field — which is exactly why `permissionMode` and governance-as-data are dropped (§3.2). The def says *what an agent does*; the launch context says *what it may authorize*. Orthogonal by construction.

**Fail-safe reality (from the shipped `spec-lifecycle`).** `spec-lifecycle` shipped its own `internal/approve.ConsentGate`: `consentPolicy: off` → allow; `--approve` → allow; **non-TTY without `--approve` → refuse**; TTY → interactive y/N. A headless `claude -p` agent has no TTY, so it **fails closed** on `lifecycle approve` by default — it cannot silently self-approve even if a skill named the verb. The residual rule is only *"don't teach headless agents to pass `--approve`"*; `--approve` is precisely the flag path (b) uses after `human_gate` releases. The built primitive already fails safe; this boundary is the belt to its suspenders.

## 8. The three layers

Mirrors the sibling primitives' shape.

### 8.1 Layer 1 — CORE: the `agentdef` CLI

Go, single static binary, sibling of `constitution` / `lifecycle`, **no external language runtime**. Reuses the constitution's copied frozen internals where they fit (`atomicwrite`, the managed-block/scaffold engine, the skill fan-out — copied, not shared-lib, so the primitive stays standalone). Deterministic, no LLM. v1 verbs:

| Verb | Does |
|---|---|
| `agentdef init` | Scaffold: create `agents/`, write the schema descriptor, seed `agentdef.yml`, fan out authoring skills, write managed AGENTS.md/CLAUDE.md pointer blocks (constitution-style markers). |
| `agentdef validate <file> [--format json]` | Validate a def: Agent Format envelope (JSON-Schema, adopting/mirroring the official Go parser) **+** our extension rules (`x-skills` shape, `x-experiment.slots` reference existing fields). Read-only, deterministic. Exit 0/1/2. |
| `agentdef compile <file> --runtime claudebox [--variant <json>]` | §5: render the neutral def → `.claude/agents/<role>.md` (or, with `--variant`, the slot-overridden def). The materialization engine. |
| `agentdef list [--format json]` | Enumerate roles in `agents/` and their resolved fields (reads defs only). |

*(No `approve`-class verb exists here by design — this primitive never grants authority, §7.)*

### 8.2 Layer 2 — AGENT SURFACE: skills

Thin. Agent-agnostic (SKILL.md standard), fanned out like the siblings: an **`agentdef-author`** skill (authoring/validating a def: field meanings, the envelope↔extension mapping, running `agentdef validate` before surfacing). This primitive is *consumed by the runtime*, not driven conversationally mid-workflow, so the surface is deliberately smaller than `spec-lifecycle`'s stage skills. Fan-out maps `runtimes:` to trees (`claude-code → .claude/skills/`, etc.), same convention.

### 8.3 Layer 3 — INTEGRATIONS

- **Agent Format** (conformed-to, not run): the `.agf.yaml` envelope + JSON Schema + Go parser. No runtime dependency; envelope pinned to v1.0 and proven by a conformance corpus (§11.1).
- **claudebox / Claude Code** (compile target): materialized `.claude/agents/<role>.md`; requires the claudebox persona-path tuning (§5).
- **Conductor** (consumer, Stage 3): via **ClaudeboxProvider** only; correlation metadata for the Stage-4 join.
- **`spec-lifecycle`** (workflow companion): the cast's roles map onto lifecycle stages (§12); no code dependency.
- **`adr-sourced-constitution`** (governance companion): agents' `x-skills` may include the constitution's plan-gate skill; governance itself is never a def field.
- **Stage 4 (LiteLLM + Langfuse)** / **Stage 5 (controller)**: read the correlation + experiment metadata this schema emits.

## 9. Configuration — `agentdef.yml` (repo root)

```yaml
schemaVersion: 1
agentFormat: { envelope: agent-format, version: "1.0" }   # the on-disk envelope we conform to (NOT a runtime)
runtime: claudebox                                         # the single v1 compile target (harness field is deferred, §3.2)
consentBoundary: launch-context                            # documentation of §7 (defs carry no authority)
runtimes: [claude-code]                                    # skill fan-out targets for the authoring skill
sourceTracking: { type: github-issue, repo: kentra-io/... }  # matches the sibling primitives' vocabulary
```

Versioned like the siblings (`constitution.yml` / `lifecycle.yml`): unknown `schemaVersion` ⇒ refuse; no migration machinery. `agentFormat.version` is a **conformance anchor**, not an installed dependency — there is no Node/npm/runtime pin, mirroring `spec-lifecycle`'s `specFormat.grammar`.

## 10. Deferred — explicitly not in v1

| Item | Why |
|---|---|
| `tools` / `mcps` fields (as references) | Envelope + `{model, instructions, skills}` cover v1; tools slot in as references without reshaping (§3.2) |
| `harness` runtime-portability field | Wire when a **second runtime** appears; until then runtime is a compile arg. Omnigent vocabulary reserved (§3.2) |
| `interface` (A2A I/O modes) | Present in the envelope, unused; our agents are file+Conductor-driven, not A2A-message-driven |
| The Stage-5 controller / evaluator / promotion policy | This spec ships only the def-side experiment *seam* (§6); the loop is Stage 5 |
| A second compile target (codex / cursor materialization) | Claudebox-first; the `harness` field + a second provider is the extension point |
| Def inheritance / mixins (shared persona fragments) | Flat files first; DRY-ing personas is a v2 concern if the cast grows |
| General skill/plugin **resolution manifest + lockfile** (§5.2) | v1 uses a hardcoded source map / vendored copies for the known cast; the reference→source resolver + reproducibility lock come later (mechanism before ergonomics) |
| Skill-set **bundles** (named groups like `execution`) | Personas list individual `x-skills` in v1; bundles are an ergonomics layer for a larger cast |
| Persona-management **GUI** | Off-grain — management stays a diffable manifest + CLI (§5.2); revisit only at cast scale we don't have |

## 11. Open items — build-time spikes (not blockers)

1. **Extension validation (the one real conformance decision, §4).** Confirm whether Agent Format's JSON Schema permits OpenAPI-style `x-` specification extensions (ignored by stock validators) or is `extra=forbid`. If extensions pass → `skills`/`harness`/`x-experiment` ride the stock schema. If forbidden → we own the schema (still envelope-shaped), `agentdef validate` is sole authority. Capture a small **conformance corpus** (real `.agf.yaml` fixtures + expected parse) proving envelope compatibility without a runtime — the `spec-lifecycle` §12.1 pattern.
2. **Exact `claude -p` flags — mostly RESOLVED 2026-07-06** (claude-code-guide verification against current docs). Confirmed: `--agent <role>` runs the session *as* a named agent and applies its full frontmatter; `skills:` is **preload, not an allow-list** (§5.1). **Residual build-time checks only:** (a) `--agent` in headless on the *pinned older* CC version (under-documented — test locally); (b) the claudebox persona-path tuning that puts materialized `.claude/agents/<role>.md` where `claude -p` finds it.
3. **claudebox skills+plugins overlay seam (the isolation mechanism, §5.1).** Add an `extra_mounts` / overlay provisioner to our vendored claudebox fork (`claudebox/`) that mounts per-agent dirs over `~/.claude/skills` **and** `~/.claude/plugins` — targeting the **host path** (`<hostHome>/.claude/...`), since `/home/agent/.claude` is a symlink created *after* container create. Overlay *shadows* (replaces the view), so the per-agent dir must contain everything wanted at that scope. Goes through claudebox's own lifecycle gates (it is a lifecycle-managed project). Rejected alternative: `CLAUDE_CONFIG_DIR` (broken/undocumented, absent from our claudebox source).
4. **`x-skills` resolution source (§5.2).** Where a skill *name* resolves to files: extend the `kentra-agentic-plugins` catalog to index standalone skills (not just plugins), or pull from the primitive repos. v1 hardcodes a source map; this spike is the general resolver.
5. **ClaudeboxProvider.** Build (~250–400 LOC Python, fork-carried; reconned in `references/conductor-integration-notes.md`); must **not** use Conductor's default `claude` provider. Confirm the one-line Conductor fork patch that lets `AgentDef.metadata` carry correlation keys (its schema is `extra=forbid`).
6. **Adopt vs mirror the official Agent Format Go parser.** Agent Format ships a Go parser (Apache-2.0). Decide: import it (dependency, but neutral + Go-native) vs mirror a tiny loader (parity with the siblings' zero-dependency posture). Weigh against the `spec-lifecycle` "own the engine" precedent.
7. **`execution_policy.id` semantics for claudebox.** Agent Format's loop strategies (`agf.react`, `agf.sequential`) describe an agent loop we don't run (Claude Code owns it). Confirm the right constant to pin, or whether the field is inert for a `claude -p` harness.

## 12. Appendix — kentra's initial agent cast (project data, not the primitive)

The concrete cast is **consuming-project data** (like ADRs to the constitution primitive), shown here as the reference example the Stage-3 workflow assumes. Minimal by intent; refine during Stage-3 build.

| Role | Lifecycle stage | Mode |
|---|---|---|
| **Business Analyst** (planner) | refine / requirements | A (interactive) |
| **Tech Lead** (architect) | design / ADR proposals | A (interactive) |
| **Implementer** | execute | B (headless) |
| **Verifier** | verify vs. milestone validation contract | B (headless) |
| **Bug-repro** | repro-first bug flow | B (headless) |
| **Orchestrator** | triage / routing / escalation | B (headless) |

Mode-A agents (BA, Tech Lead) drive the interactive planning half and can hold the `lifecycle-approve` skill by launch context (§7); Mode-B agents run headless under Conductor and structurally cannot approve. Each role is one `agents/<role>.agf.yaml`.

## 13. Research provenance

- Harness [`tasks/orchestration-runtime-handoff.md`](./tasks/orchestration-runtime-handoff.md) (2026-07-04/05): the Stage-3 design + locked decisions this spec formalizes — Conductor engine, claudebox runtime, the neutral-def-as-source-of-truth resolution of the two-agent-abstraction collision, the launch-context consent boundary, the ClaudeboxProvider, the minimal/deferred field split, and the Stage-5 A/B seam.
- Two `deep-research` passes cited therein (agent-runtime / orchestration / auto-eval; agent-schema / UI): Omnigent = reference-only (one idea: `executor.harness`); GEPA dropped; the UI category churns (mine, don't depend).
- Format-landscape verification (2026-07-06, live primary sources): **Agent Format** (agentformat.org — Snap Inc. + Agent Format Authors, Apache-2.0, JSON-Schema v1.0, Go parser) confirmed as the envelope; **Omnigent `executor.harness`** (github.com/omnigent-ai/omnigent, Databricks, Apache-2.0, alpha) confirmed as the harness-vocabulary source; **A2A AgentCard** (Linux Foundation, v1.0) and **Letta `.af`** ruled the wrong category (discovery manifest / stateful snapshot); **Claude Code subagent frontmatter** confirmed as the materialization target with a first-class `skills:` field. Bottom line: no single format has {skills + harness + JSON-Schema}, so conform the Agent Format envelope and own a thin extension for the two gaps.
- Sibling specs whose conventions this deliberately mirrors: [`spec-lifecycle`](./spec-lifecycle/spec-lifecycle.md) (conform-to-format / own-the-engine; three layers; `*.yml` config; no-runtime posture) and [`adr-sourced-constitution`](./adr-sourced-constitution/) (tool-only writes, managed pointer blocks, pure static binary).
