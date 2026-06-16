# Fabro vs. Conductor — Engine Evaluation for the Planning Domain

*Generated: 2026-06-16 | Companion to [planning.md](./planning.md). Source-level analysis of the two cloned reference engines under [references/fabro](./references/fabro) and [references/conductor](./references/conductor), evaluated against the workflow in [planning.md](./planning.md).*

> **What this document is.** A genuine head-to-head: how much customization each engine needs to run the planning-domain workflow, *given three decisions made at the start of this evaluation* (below). It is not a confirmation of the `planning.md` "Fabro adopted" lean — that lean is re-opened here and the conclusion shifts.

---

## 0. The three decisions that frame everything

This evaluation was scoped by three explicit choices. They are load-bearing — change any one and the verdict can move.

| # | Decision | Consequence |
|---|---|---|
| **D1** | **Genuine head-to-head** (re-open the Fabro lean) | Equal-depth analysis; a clear recommendation that may contradict `planning.md`. |
| **D2** | **An "agent node" = a full Claude Code session** (`claude` CLI) inside the sandbox | The engine does **not** supply the agent runtime. We get skills, hooks, MCP, constitution-loading rules, the whole harness — but the engine's own LLM client and tool loop are **bypassed**. |
| **D3** | **Sandboxes are spawned by reusing the existing `cb`/claudebox tooling** | The engine does **not** own sandbox lifecycle either. `cb run` makes the container+worktree per issue; the engine just orchestrates around it. |

### What D2 + D3 do to the engine's role

Together they **demote both engines to the same job**: a *control plane* that

1. sequences nodes through a per-issue lifecycle graph,
2. shells out to `cb` to create a claudebox + worktree per issue,
3. drives `claude -p` sessions inside it (capturing `stream-json` events),
4. surfaces human-in-the-loop gates and a live view.

Everything that makes Fabro a *heavy* system — its `fabro-llm` API client, its `fabro-agent` session/tool harness, its `bollard` `DockerSandbox`, its Daytona integration — is **not used**. Under these decisions we'd run roughly **half of Fabro**, and the half we keep (DOT engine + dashboard + event store) is the half that's also the cheapest to replicate. That is the central tension of this whole evaluation.

**Topology clarification (resolves an apparent contradiction in the brief).** "Everything runs in claudebox" and "fabro/conductor may run on the host" are consistent: the **engine control-plane runs on the host**; **every *agent invocation* — including the Opus orchestrator, planning, design, implementation, verification — runs as a `claude` session inside a `cb`-spawned claudebox.** "Orchestration runs in claudebox" refers to the *Opus orchestrator agent node*, not the engine's next-node bookkeeping. Because the engine is on the host, there is **no nested-Docker-through-the-socket-proxy problem** — `cb run` talks to the host daemon directly.

---

## 1. Verdict

> **Updated after two clarifications** (see §7): (a) **the dashboard is *not* a blocker** — you'll vibe-code your own or bolt on an OSS execution-tracker; and (b) the future is **multi-model, not just Claude** (claudebox is only for `claude`), with a **LiteLLM-style proxy as the observability plane**. Both push the same direction.

**Under D1–D3, Conductor is the better-fitting engine, and the case is now decisive — the dashboard clarification removes Fabro's one remaining advantage.** What's left for the engine to do is a *small* control plane, and the choice is between Conductor (additive, Python, already a claude-CLI driver) and a thin custom engine. **Fabro is effectively out** under these decisions.

- **Conductor's customizations are additive and low-drift** (a new provider class, a 1-line schema field, GitHub via `gh` script steps). It is **already shaped to drive the `claude` CLI**, its fresh-subprocess-per-step model maps exactly onto "the artifact is the interface," and its **provider abstraction is the natural home for heterogeneous runtimes** (claudebox now, other model CLIs / raw-API agents later — §7). Python, same ecosystem as the Claude Agent SDK, cheap to fork. **Directly serves your goal of minimizing modifications to the external system.**
- **Fabro's customizations are invasive and high-drift** under D2/D3: **no claude-CLI backend** (and Claude Code has **no ACP mode** — §3), so adoption means a third agent backend *inside its core router*; bypassing its sandbox (D3) creates a **checkpoint/diff impedance**; and its per-runtime backend model is narrower than Conductor's providers for the multi-model future. Its standout asset — the multi-run dashboard — **no longer counts**, because the dashboard is a non-blocker you'll own separately.
- **A thin custom control plane is now a serious contender, not just a fallback.** With the dashboard externalized and observability pushed into a proxy/OTel plane (§7), the engine shrinks to "sequence nodes + launch a runtime box + drive an agent CLI + parse its stream + surface gates." Conductor still earns its keep on the *non-trivial* parts — bounded retry, conditional routing, HITL gates, resume, cost — but if Conductor's additive surface ever turns invasive, build-your-own is the off-ramp (`planning.md` §15(7)).

**Recommendation:** Adopt **Conductor** as the execution engine. Make **observability a separate plane** (LiteLLM proxy and/or Claude Code's native OTel — §7), independent of the engine. Treat the **dashboard as an independent build/adopt** (your call — not a selection criterion). Keep **thin-custom** as the live off-ramp. **Do not adopt Fabro** unless a decision in D1–D3 reverses.

---

## 2. The decisive findings (verified at source / against Claude docs)

Three findings move the needle; each was checked specifically rather than assumed.

### F1 — Claude Code has no ACP mode; Fabro has no claude-CLI backend
Fabro ships exactly two agent backends: **`api`** (direct Anthropic/OpenAI calls via `fabro-llm`) and **`acp`** (Agent Client Protocol over stdio; `lib/crates/fabro-workflow/src/handler/llm/acp.rs`). Under D2 we want neither — we want `claude -p --output-format stream-json`. Claude Code's docs confirm **no `--acp` flag and no documented ACP support**; the official headless interface is `claude -p` + `stream-json`. So driving a real Claude Code session from Fabro requires either:
- an **ACP↔claude shim** process (a community/third-party adapter Fabro spawns via `acp.command="cb exec <ctr> <shim>"`), an extra moving part with its own drift, **or**
- a **new third backend** in Fabro (`backend="claude-cli"`) — a new handler that spawns `claude -p` and parses `stream-json`, plus enum variants threaded through `BackendRouter` (`handler/llm/router.rs`) and the type system. This is an **invasive core change**.

> The good news for Fabro: `acp.command` *is* an arbitrary argv that Fabro spawns over stdio (`fabro-acp` `AcpProcessSpec::from_attrs`), and there's **no assumption the binary is local** — so `cb exec <container> …` is a valid command. The blocker is purely that the *other end* must speak ACP, and `claude` doesn't.

### F2 — Conductor is already a claude-CLI driver with a clean, additive seam
Conductor's `claude-agent-sdk` provider already spawns the `claude` CLI (`src/conductor/providers/claude_agent_sdk.py`), and the `AgentProvider` ABC (`providers/base.py`) is a clean extension point: implement `execute()`, `validate_connection()`, `close()`, and a `CAPABILITIES` classvar. A **`ClaudeboxProvider`** that runs `cb exec <ctr> claude -p --output-format stream-json`, parses the line-delimited events, and emits them via the existing `event_callback` is **~140–160 LOC and requires no core changes** — register it in `providers/factory.py` + `capabilities.py`. Fresh subprocess per step is the documented default (`CAPABILITIES.concurrent_safe`, no session carryover) — i.e. **"fresh thread per stage" is the native behavior, not something to engineer**.

### F3 — Multi-run dashboard: Fabro yes, Conductor no
- **Fabro** has a real cross-run UI: `apps/fabro-web/app/routes/runs.tsx` renders a **Kanban board** (lanes: pending/runnable/running/**blocked**/succeeded/failed/…) and a sortable **list view**. The **`blocked` lane surfaces the pending interview question with an "Answer Question" button** — this *is* the "needs attention across all issues" surface the design wants, off the shelf. Gate state is durable in the event log (`pending_interviews`), answered via `POST /runs/{id}/questions/{qid}/answer`.
- **Conductor** is **single-run, in-memory, ephemeral** (`web/server.py`: one `WebDashboard` per `conductor run`, `_event_history` in memory, 30s grace then gone). No cross-run backlog, no persistent state. The only durable artifact is a per-run JSONL event log.

> Mitigant: `planning.md` §4 *already* designates **GitHub Projects** as the durable "where is every issue" board and the engine dashboard as merely the *live* view. So Conductor's gap is "no unified *live* multi-run pane," not "no backlog." That gap is a thin aggregator (tail the per-run JSONL logs + GitHub), not a system of record.

---

## 3. Capability-by-capability, mapped to `planning.md`

Legend: ✅ off-the-shelf · 🟡 light config/additive · 🟠 moderate custom · 🔴 invasive/build · ⚫ bypassed by D2/D3

| `planning.md` need | Fabro | Conductor | Notes |
|---|---|---|---|
| **Lifecycle state machine** (feature/bug, DOT) §2,§11 | ✅ DOT-native (`.fabro`), conditional edges, `max_retries` | 🟡 YAML DAG + `routes`/`when` + `retry.max_attempts`; loops via back-edges | Both express bounded-retry→escalate→human-gate. DOT is visually a state machine; YAML routing is equivalent in power. |
| **Per-node k-v metadata** §13 | ✅ `HashMap<String,AttrValue>` — any attr, zero code | 🟡 per-step `model`/`provider`/`reasoning` exist; arbitrary k-v blocked by `extra="forbid"` → **1-line schema add** | Fabro wins on raw flexibility; Conductor is a trivial fix. |
| **Per-node model** (Opus/Sonnet/Haiku) §13 | ✅ `model=`/`provider=` per node | ✅ `agent.model`/`agent.provider` per step | `claude --model opus|sonnet|haiku` per invocation (confirmed in Claude docs). |
| **Agent runtime = Claude Code session** (D2) | 🔴 **no claude-CLI backend**; needs new backend or ACP shim (F1) | 🟠 **`ClaudeboxProvider` ~150 LOC, additive** (F2) | The pivotal row. Fabro = invasive core change; Conductor = clean add-on. |
| **Sandbox via `cb`** (D3) | ⚫🟠 `Sandbox` trait bypassed; use `LocalSandbox`+`cb exec` in the backend cmd, but **checkpoints/diff run on the host sandbox** and won't see in-container work → shared-volume workaround | ⚫🟢 no sandbox concept to fight; provider just calls `cb exec`; steps share the worktree naturally | Conductor's *lack* of a sandbox abstraction is an asset here. |
| **HITL gates** §6,§8 | ✅ `hexagon` node → interview dock; durable; resumable | ✅ `type: human_gate`, terminal + web; `--skip-gates` for CI | Both solid. Fabro's gate is surfaced in the *cross-run* board (F3). |
| **Multi-run "needs attention" dashboard** §4,§15(1) | ✅ **Kanban + "Answer Question" lane** (F3) | 🔴 single-run only; **build a live aggregator** (GitHub Projects covers the durable board) | Fabro's standout asset. |
| **Git-checkpoint resume** §3 | ✅ native — *but* tied to its sandbox (impedance under D3) | 🟡 checkpoint on failure; `resume`; **off for claude-cli** (CLI session state isn't portable) | Under D2/D3 neither gives you "resume mid-Claude-session"; the durable resume unit is the per-stage **artifact**, which both support. |
| **Observability / event stream** §13 | ✅ rich event enum + SSE | ✅ `event_callback` + JSONL event log | Both adequate. |
| **GitHub Issues adapter** §5,§13 | 🔴 only App-auth/PR/webhook plumbing; issue→run is a build | 🔴 registry only; do it via `gh` in `type: script` steps or a helper | Custom for both, as `planning.md` predicted. Conductor's script-step path is lighter. |
| **Fresh thread per stage / artifact-as-interface** §6 | 🟡 fresh ACP/agent session per node | ✅ **native** — fresh subprocess per step, files in shared worktree are the handoff | Conductor matches the principle exactly with no effort. |
| **Per-issue cost tracking** §13 | 🟡 events carry usage; no built-in cost UI | ✅ per-node cost dashboard + custom pricing | Conductor wins; though under D2 cost also comes back in `claude`'s own `stream-json` `result.total_cost_usd`. |
| **Language / fork-maintenance** | 🟠 Rust workspace — high fork cost, steep contributor ramp | 🟢 Python — low fork cost, same ecosystem as Claude Agent SDK | Directly relevant to your "limit modifications / avoid drift" goal. |

---

## 4. Customization inventory & drift classification

Your explicit constraint: *minimize modifications to the external engine, because forks drift.* The right lens is therefore **additive (new files/classes/config) vs. invasive (edits to core control/dispatch code)**.

### Conductor — mostly additive
| Work item | Effort | Drift |
|---|---|---|
| `ClaudeboxProvider` (`cb exec … claude -p`, parse `stream-json`, emit events) | ~150 LOC, new file | **Additive** — register in factory; no core edits |
| Per-step `metadata: dict` field on `AgentDef` | 1 line | Minimal — single schema field |
| GitHub Issues adapter (claim/transition/create) via `gh` in `type: script` steps | small | **Additive** — lives in YAML, not the engine |
| Lifecycle workflows (feature.yaml, bug.yaml) | authoring | **None** — these are *content*, not forks |
| Unified live multi-run pane | moderate, **separate service** | **Zero engine drift** — built beside Conductor, reads JSONL + GitHub |
| **Net:** Conductor's source stays close to upstream; almost everything is add-on or content. |

### Fabro — mostly invasive
| Work item | Effort | Drift |
|---|---|---|
| Claude Code session backend (no ACP in `claude`, F1): new `claude-cli` backend **or** ACP shim | moderate–high | **Invasive** — edits `BackendRouter`/handlers/types, or a separate shim process to maintain |
| Sandbox/checkpoint impedance under D3: shared volume between `LocalSandbox` path and `cb` container, or forgo native checkpoints/diff | moderate | **Invasive-adjacent** — fights the engine's core assumption that it owns the workspace |
| GitHub Issues adapter (webhook→`operations::create`, new provenance) | high | Additive-ish but in Rust |
| Workflow viz / k-v metadata | ~0 | None (already supports it) |
| **Net:** the two must-haves (claude backend, sandbox impedance) both cut into Fabro's core — the opposite of your drift goal. You also carry a Rust toolchain you otherwise don't need. |

---

## 5. Concrete integration shape (the recommended path)

**Engine on host, every agent in a `cb` claudebox:**

```
 GitHub Issue ──(claim: assign+transition)──► Conductor run on HOST
                                               │  reads issue type → picks feature.yaml | bug.yaml
   per issue:  cb run  ──────────────────────► claudebox container + worktree  (D3)
                                               │
   each agent step:  ClaudeboxProvider ───────► cb exec <ctr> \
                                                  env ANTHROPIC_BASE_URL=http://litellm:4000 \   # observability plane (§7)
                                                      ANTHROPIC_AUTH_TOKEN=<litellm-virtual-key> \
                                                  claude --bare -p "<rendered prompt>" \
                                                    --model {opus|sonnet|haiku} \
                                                    --permission-mode bypassPermissions \
                                                    --output-format stream-json --verbose
                                               │  ◄── parse stream-json → event_callback → tracker/JSONL
   telemetry:       all model traffic ───────► LiteLLM proxy (cost/tokens/latency, per-session via
                                               X-Claude-Code-Session-Id) — engine-agnostic (§7)
   stage handoff:    artifact files written in the worktree (shared across steps)  ── §6
   HITL gate:        type: human_gate  ── terminal or web approval  ── §8 HARD RULE
   lifecycle state:  GitHub Issues + Projects  (claim mutex, coarse status)  ── §4
```

Notes that make this clean:
- **`--bare` + `ANTHROPIC_API_KEY` + `bypassPermissions`** is the documented container-headless recipe (Claude refuses `bypassPermissions` as root, so keep the `agent` user). `--bare` gives reproducible behavior; you then *explicitly* provide the constitution skill/rules per `planning.md` §7.
- **Fresh session per step is automatic** — no `--continue`. The worktree files are the interface (F2), matching §6 exactly.
- **Different models in one issue, same worktree** is supported (`--model` per invocation; confirmed) — Opus orchestrator → Sonnet impl → Haiku verify, sequentially, in one container.
- **Bounded retry → escalate → human** maps to `retry.max_attempts: 3` + `routes:[{when: "{{ verifier.output.passed == false }}", to: orchestrator}]` + a `human_gate` target — i.e. §10's escalation ladder is expressible without engine changes.

---

## 6. Risks & open items

1. **Dashboard — resolved, non-blocker.** You'll vibe-code your own or adopt an OSS execution-tracker, so the live multi-run UI is no longer a selection criterion. Practical inputs to whatever you build: Conductor's per-run JSONL event logs, the engine's run/gate state, GitHub Projects (durable backlog), and the LiteLLM/OTel telemetry plane (cost/latency). Candidate OSS trackers to evaluate rather than build: a Grafana/Prometheus board over Claude Code OTel metrics; LiteLLM's own admin UI for spend; or a small SSE-fed web app. *(Supersedes `planning.md` §15(1).)*
2. **No portable mid-session resume under D2.** Neither engine can resume *inside* a half-finished Claude session — the CLI's session state isn't portable across processes. Durable resume is at the **artifact/stage** boundary (re-run the stage from the last approved artifact). Confirm that's acceptable (it aligns with §6's "the artifact is the interface").
3. **Auth into the box.** `ANTHROPIC_API_KEY` (or a gateway token / `apiKeyHelper`) must reach each `cb` container; interactive OAuth won't work headless. Out of scope for `planning.md` today — flag it.
4. **`cb` from the host vs. from inside a box.** This evaluation assumes the engine runs on the **host** (your stated allowance), so `cb run` hits the host daemon directly. If you ever move the engine *inside* a claudebox, the socket-proxy strips published ports and namespaces containers per worktree — revisit then.
5. **Fork-depth tripwire (both engines).** `planning.md` §15(7) already names this. Under D1–D3 the residual value of *either* framework over a thin custom control plane is modest; if Conductor's additive surface starts turning invasive, the build-your-own option is the off-ramp, not a deeper fork.
6. **Claude version coupling.** `--bare`, model aliases, and `stream-json` shapes evolve with Claude Code releases; pin a version in the `.claudebox/Dockerfile` and test the `stream-json` parser against it.
7. **LiteLLM caveats (verify at build time).** (a) A reported 2026 gap: spend/budget tracking may **not** fire for native Anthropic `/v1/messages` passthrough (only OpenAI-format `/chat/completions`) — mitigate by defining each model explicitly in `config.yaml`, confirming on your pinned version, or routing non-Claude runtimes through the OpenAI format. (b) **Security advisory:** LiteLLM **1.82.7 / 1.82.8 shipped credential-stealing malware** — pin away from those releases and verify the image digest. (c) Claude Code prepends an attribution block to the system prompt that can break a proxy-side prompt cache — set `CLAUDE_CODE_ATTRIBUTION_HEADER=0` if you cache at the proxy.
8. **Runtime sprawl (multi-model).** Each non-Claude runtime is a *new* `*box` image + a *new* provider. Keep the provider interface narrow (`spawn box → exec agent CLI → stream events`) so adding Codex/Gemini/raw-API runtimes stays additive; don't let runtime-specific quirks leak into the engine core.

---

## 7. Addendum — multi-model runtimes & the observability plane (new inputs)

Two clarifications arrived after the head-to-head was drafted. Neither reverses the verdict; both reinforce it.

### 7.1 Multi-model future: claudebox is just the first "agentbox"

`claudebox` is Claude-specific. The design should anticipate a milestone being implemented/verified by **a different model or runtime** (e.g. a Codex CLI box, a Gemini CLI box, or a plain API-only agent). This generalizes the D2/D3 pattern rather than breaking it:

- **Runtime = (box image) + (agent CLI invocation).** "claudebox + `claude -p`" becomes one member of a family: "codexbox + `codex …`", "geminibox + `gemini …`", "apibox + a thin API caller". `cb`-style spawning generalizes to "spawn the box for this runtime type."
- **This is exactly what a provider abstraction is for.** Conductor's `AgentProvider` ABC + per-step `provider`/`model` selection means each runtime is **one additive provider class**, chosen per node. `feature.yaml` can say `implementer → sonnet-in-claudebox`, `verifier → some-cheaper-model-in-its-box`, with no engine changes. **This is a fresh, concrete point in Conductor's favor** and a point against Fabro, whose `api`/`acp` backend pair would need per-runtime backends wired invasively into its router.
- **Keep the seam narrow** (Risk §6.8): every provider does only `spawn box → exec agent CLI → stream events → return artifact-or-output`. Runtime quirks stay in the provider, never the engine.

### 7.2 Observability as a *separate plane*, below the engine

Under D2, the engine never sees the model calls (the agent CLI makes them). So telemetry should **not** be an engine feature at all — it belongs in a plane *beneath* the runtime, which makes it **engine-agnostic** (identical whether you run Conductor, Fabro, or thin-custom). This is the right reason to *not* value Fabro's built-in observability or Conductor's cost dashboard as differentiators. Two complementary mechanisms, both verified against Claude docs:

**(A) LiteLLM proxy — the cross-provider gateway (your instinct is correct).**
Point each runtime's base URL at LiteLLM and it becomes the single chokepoint for cost, tokens, latency, request/response logging, routing, fallbacks, budgets, and guardrails — *across all providers*, which is precisely what the multi-model future needs.
- Claude Code: **officially supported** — `ANTHROPIC_BASE_URL=http://litellm:4000` + `ANTHROPIC_AUTH_TOKEN=<virtual-key>`; works headless and in `--bare`. Claude Code even sends `X-Claude-Code-Session-Id` / `X-Claude-Code-Agent-Id`, so the proxy can attribute spend **per agent session** (≈ per issue/per node) with no body parsing.
- Other runtimes (OpenAI/Codex/Gemini) route through the same proxy in their native or OpenAI format — one unified telemetry store.
- **Caveats live in Risk §6.7** (native-`/v1/messages` spend-tracking gap → define models in config; the 1.82.7/1.82.8 malware advisory → pin; attribution-header cache interaction). Verify these on your pinned version before relying on budget enforcement.

**(B) Claude Code native OpenTelemetry — richer, but Claude-only.**
`CLAUDE_CODE_ENABLE_TELEMETRY=1` + OTLP env vars export `claude_code.cost`, `claude_code.tokens`, session counts, and (beta) per-request spans with TTFT/duration/cache metrics — straight into any OTLP backend (Prometheus/Grafana/Honeycomb/Datadog). No proxy required. Per-call cost is also in `--output-format json` (`total_cost_usd`).

**Recommendation:** Use **both, layered.** LiteLLM proxy as the **cross-provider cost/governance gateway** (the unifying plane for the multi-model future and the natural metering/budget point); Claude Code OTel as **richer Claude-native depth** where you want span-level traces. Wire both via container env vars (§5) — neither touches the engine, so this decision is fully decoupled from Conductor-vs-anything.

> **Sources (observability):** Claude Code [LLM gateway](https://code.claude.com/docs/en/llm-gateway.md), [monitoring/usage (OTel)](https://code.claude.com/docs/en/monitoring-usage.md), [headless](https://code.claude.com/docs/en/headless.md); LiteLLM [Anthropic passthrough](https://docs.litellm.ai/docs/pass_through/anthropic_completion) and [proxy/gateway](https://github.com/BerriAI/litellm); spend-tracking gap [issue #24204](https://github.com/BerriAI/litellm/issues/24204).

---

## 8. One-paragraph bottom line

Under the three framing decisions the engine is a thin control plane, not a runtime — and once the **dashboard is something you'll own separately** and **observability lives in a proxy/OTel plane beneath the runtime** (§7), Fabro's advantages evaporate while its costs remain: it has **no way to drive a real Claude Code session** (no claude-CLI backend; `claude` has no ACP), and **reusing `cb` fights its sandbox-owns-the-workspace design** — i.e. *invasive* edits to an external Rust codebase you'd otherwise never touch, the opposite of your drift goal. **Conductor**, by contrast, is *already* a claude-CLI driver with a clean *additive* provider seam — one that doubles as the natural home for future **non-Claude runtimes** — plus fresh-session-per-step semantics that give you "the artifact is the interface" for free, and Python ergonomics. **Adopt Conductor; keep a thin custom engine as the live off-ramp; treat the dashboard and the LiteLLM/OTel observability plane as independent, engine-agnostic builds; do not adopt Fabro unless one of D1–D3 reverses.**
