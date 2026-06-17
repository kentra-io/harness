# Observability & LLM-Proxy Plane — Decision Record

*Generated: 2026-06-16 | **Revised & DECIDED: 2026-06-17.** Status: **DECIDED — LiteLLM (gateway) + Langfuse (observability / eval / persistence)**, with Claude Code native OpenTelemetry on Claude nodes. This settles [planning.md §12a](./planning.md) (the observability plane) and resolves [§15 open decision #8](./planning.md). Companion to [planning.md](./planning.md), [fabro-vs-conductor-evaluation.md](./fabro-vs-conductor-evaluation.md), [references/technologies.md](./references/technologies.md).*

> **What this document is.** The decision record for the harness's **observability / LLM-proxy plane** — the engine-agnostic layer *beneath* the runtime ([planning.md §0.2](./planning.md)) that captures cost/tokens/traces, allocates cost per issue, and supports evaluating different harness configurations. It records the chosen stack, the alternatives evaluated and **why they were rejected**, what each stated goal maps to, and the build-time items to verify.

---

## 0. Decision

**Adopt `LiteLLM` (pinned) as the gateway + `Langfuse` (self-hosted) as the observability / eval / persistence layer.** Claude Code's native OpenTelemetry (`CLAUDE_CODE_ENABLE_TELEMETRY=1`) ships span-level depth from Claude nodes into the same backend. Every model call routes through LiteLLM via `ANTHROPIC_BASE_URL`; LiteLLM's `langfuse_otel` callback forwards traces to Langfuse.

**Why this stack:**
- **LiteLLM** — mature pure gateway: 100+ providers (fits the runtime-agnostic v1 requirement, [§0.4](./planning.md)), native virtual keys / budgets / access control, native Anthropic `/v1/messages` passthrough (so Claude Code points at it unmodified), and rich per-request cost telemetry (`gen_ai.cost.*` attributes + token-usage histograms) exported over vendor-neutral OTel.
- **Langfuse** — the deepest open-source observability + **eval** layer: OTLP backend, native LiteLLM callback, sessions/traces hierarchy suited to multi-step agentic runs, and a mature eval framework (LLM-as-judge + code scorers + human annotation + **datasets + experiments**) — which is exactly the "eval different configs / continuous eval" half of the goal set. MIT-licensed, self-hostable, and now **backed by ClickHouse** (acquired Langfuse 2026-01-16) with a public commitment to keep it MIT/OSS/self-hostable; actively developed (releasing multiple times/week).

**Two goals remain custom builds regardless of tool** (no product offers them natively): **cost-mapped-to-issues** (cheap — inject the issue-ID tag on every call, already designed in [§4](./planning.md); the store does the rollup) and **"rough complexity estimation"** (a bespoke analysis over persisted traces — tokens/turns/retries/escalations per issue).

---

## 1. The constraint frame — how *this* harness shaped the choice

| Constraint (source) | Consequence |
|---|---|
| **Agent node = a full Claude Code session** pointed at the gateway via `ANTHROPIC_BASE_URL` ([D2, §12a](./planning.md)) | Gateway must accept the **Anthropic Messages format** unmodified. LiteLLM does (native `/v1/messages`). *This is what eliminated Helicone's gateway (§3).* |
| **The engine never sees model calls** — telemetry lives *below* the runtime ([§0.2](./planning.md)) | The plane is independent of Conductor; swappable later without touching the engine. |
| **Runtime-agnostic is first-class v1** ([§0.4](./planning.md); [lessons.md](./tasks/lessons.md)) | Favours LiteLLM's 100+ providers over any ~20-provider gateway. |
| **Every run is tagged with its issue number** ([§4](./planning.md)) | Cost-per-issue = inject issue ID as metadata/tag on every call; works with any store that supports custom dimensions. Langfuse `sessionId`/tags/metadata cover it and tie cost into the trace tree. |
| **Local-first development is the priority** (user, 2026-06-17) | Weighs *against* cloud-SaaS/enterprise-first tools. Reinforces rejecting Helicone; accepted tradeoff for Langfuse is that it's still a multi-service stack (see §4). |

**Framing point that drives the eval half:** the harness's "continuous learning" is **eval-of-configurations** (which workflow/skill/model combo produces better outcomes per issue), **not model fine-tuning**. This is Langfuse's shape (datasets + experiments + eval-over-traces), and was the deciding reason a fine-tuning-oriented all-in-one was unnecessary.

---

## 2. The decided architecture

```
  Claude Code session (Anthropic Messages API)
        │  ANTHROPIC_BASE_URL + ANTHROPIC_AUTH_TOKEN
        ▼
  LiteLLM proxy (PINNED)  ── gateway: routing, virtual keys, budgets, access control
        │  • native /v1/messages passthrough  • gen_ai.cost.* + token histograms
        │  • callbacks=['langfuse_otel']
        ▼
  Langfuse (self-hosted, MIT)  ── traces / sessions / scores / datasets / experiments
        │  • persistence: Postgres + ClickHouse (+ Redis + S3/MinIO)
        │  • cost rollups per issue (via injected issue-ID tag/sessionId)
        ▼
  Custom analyses on top (harness-owned):
   • cost-per-issue rollups        (tag → aggregate)
   • rough complexity estimation   (tokens/turns/retries/escalations per issue)
   • config eval (workflow/skill/model) via Langfuse datasets + experiments

  Parallel: Claude Code native OTel (CLAUDE_CODE_ENABLE_TELEMETRY=1) → same backend
            for span-level depth on Claude nodes.
```

**Join by issue:** every run is tagged (LiteLLM metadata / `X-Claude-Code-Session-Id` / OTel resource attr = issue number), so cost/traces are queryable per issue ([§4, §12a](./planning.md)).

---

## 3. Alternatives evaluated and rejected

Two acquisitions in 2026 reshaped the field and are the dominant facts here (high confidence, primary sources):

| Tool | Status | Verdict |
|---|---|---|
| **TensorZero** | Reportedly **unmaintained** (user-flagged 2026-06-17; not independently re-verified — the verification research was stopped once this decision was made, so treat as reported, not confirmed). | **Rejected.** Was the strongest all-in-one candidate (gateway+obs+eval+experiment+optimization, ClickHouse), but already carried three frictions for this harness: ~20 providers + no native budgets, **unverified Anthropic-format ingress for Claude Code**, and a gateway-coupled optimization flywheel that's *function/prompt-shaped, not agentic-session-shaped*. Maintenance doubt removes it entirely. |
| **Helicone** | **Acquired by Mintlify 2026-03-03 → maintenance mode** (own + Mintlify posts: only security/new-models/bugfixes; **no active feature development**; cloud customers being migrated off). | **Rejected.** Three independent reasons: (1) **maintenance mode / no roadmap** — wrong to build a long-lived harness on a frozen platform; (2) the **only Claude-Code-fitting path is its *legacy* `anthropic.helicone.ai` proxy, explicitly "no longer actively developed"** — its new Rust gateway is OpenAI-format-only and doesn't natively ingest `/v1/messages`; (3) **cloud-SaaS / enterprise-first** (production-grade Helm gated behind enterprise contact) — counter to the local-first priority. Its eval/experiment surface is also shallow/uncertain (active deprecation notice on Experiments). Only genuine edge was `Helicone-Property-*` header ergonomics for cost tagging — not worth the rest. *(License note, now moot: sources conflicted on the `ai-gateway` repo — README badge says Apache-2.0, one direct LICENSE-file read returned GPL-3.0; unresolved, but irrelevant given rejection.)* |
| **OpenLIT** | Active, Apache-2.0. | **Not selected.** OTel-native SDK instrumentation but **explicitly not a gateway** (doesn't route) — a complement, not a LiteLLM or Langfuse replacement. Useful reference for custom-model cost tracking (`pricing_json`) if ever needed. |

> **Lesson reinforced:** a verified feature pick (TensorZero, yesterday's draft) was invalidated within a day by maintenance/ownership status. Tool *liveness and governance* (maintained? acquired? roadmap?) is a first-class selection criterion, checked alongside features. See [lessons.md](./tasks/lessons.md).

---

## 4. Accepted tradeoffs & build-time verification

The decision is made; these are the things to confirm during the build, not blockers:

1. **Local-dev footprint.** Langfuse is **not featherweight** — self-host is Postgres + ClickHouse (+ Redis + S3/MinIO), same class of stack as the rejected tools. *Verify a minimal local-first compose profile* (single-node, no enterprise scaling) is comfortable on a dev machine; document the slim setup in the harness. This is the main concession to the local-first priority.
2. **License durability.** Our recommendation rests on Langfuse staying MIT + active under ClickHouse. Confirm the eval/dataset/experiment features are in the **MIT core, not the `/ee` tree**, at our pinned version, and watch for relicensing signals (the MySQL/Redis/Elastic pattern is a real tail risk; ClickHouse's Apache-2.0 DNA de-risks it somewhat). Post-acquisition OSS commitments are promises, not guarantees.
3. **End-to-end cost attribution.** Validate that a full Claude Code session (many `/v1/messages` calls) groups under one `sessionId`, that **LiteLLM-computed Anthropic cost** (incl. cache-write/cache-read line items) flows into Langfuse, and that it aggregates per issue/tag with **no double-counting** (decide where cost is authoritatively computed — LiteLLM vs Langfuse).
4. **LiteLLM supply-chain & passthrough.** Pin the version + verify image digest (**1.82.7 / 1.82.8 shipped credential-stealing malware**); confirm spend tracking fires for native Anthropic `/v1/messages` passthrough (reported 2026 gap) by **defining each model explicitly in `config.yaml`**; set `CLAUDE_CODE_ATTRIBUTION_HEADER=0` if caching at the proxy.
5. **Eval target shape.** Confirm Langfuse datasets/experiments cleanly represent **agentic multi-step harness runs** (a full feature/bug workflow), not just single prompts — the unit of "config eval" is a Conductor-driven run, not one call.

---

## 5. How each stated goal is delivered

| Stated goal | Mechanism | Status |
|---|---|---|
| **Token-spend tracking** | LiteLLM `gen_ai.*` OTel telemetry → Langfuse store | ✅ off-the-shelf |
| **Cost allocation to issues/tasks** | Inject issue-ID tag/`sessionId` per call ([§4](./planning.md)) → Langfuse rollup | ⚙️ thin custom (tagging) |
| **Efficiency analysis** | Cost/latency/token rollups per issue/model/step | ✅ once telemetry + tags land |
| **Eval different configs (workflows/skills/models)** | Langfuse **datasets + experiments** + LLM-judge/code scorers | ✅ Langfuse (verify agentic-run shape, §4.5) |
| **Continuous evals / learning** | Langfuse eval-over-traces + dataset experiments (eval-of-configs, not fine-tuning) | ✅ Langfuse |
| **Persistence layer** | Langfuse: Postgres + ClickHouse | ✅ off-the-shelf |
| **Rough complexity estimation** | Derived metric (tokens/turns/retries/escalations per issue, regressed vs outcome) over persisted traces | 🔨 full custom (no tool offers this) |

---

## 6. Caveats on the evidence

- **Acquisition facts** (both, high confidence): Helicone→Mintlify 2026-03-03 (maintenance mode); Langfuse→ClickHouse 2026-01-16 (active, MIT commitment). These are acquisition *announcements* — the OSS-continuity claims are commitments, not guarantees (§4.2).
- **TensorZero maintenance status is reported, not confirmed** — the verifying research was stopped once this decision rendered it moot. Don't cite it as established fact.
- **Existence ≠ proven-at-our-scale.** Langfuse's evals/datasets/experiments are documented and the framework is mature *in general*; "proven at our agentic-coding scale" (full Claude Code sessions, many calls/issue, continuous config-eval) is a spike item (§4.3, §4.5), not yet demonstrated here.
- **Helicone `ai-gateway` license** is unresolved in sources (Apache badge vs GPL-3.0 LICENSE read) — moot given rejection, flagged for honesty.
- **Time-sensitivity.** OTel GenAI semantic conventions still "Development"; all version/feature details accurate mid-2026 and may drift. Re-verify against live docs before building ([lessons.md](./tasks/lessons.md)).

---

## 7. Follow-ups beyond this doc

- **Sync [planning.md](./planning.md):** §12a's "Langfuse vs Phoenix vs Helicone — confirm at build" lean and §15 open-decision #8 are now **settled** by this record — update both to point here and mark #8 resolved. *(Not done in this doc; flagged for the next planning.md edit.)*

---

## 8. Research provenance

- **Deep-research run (2026-06-16):** 5 angles → 21 sources → 97 claims → 25 adversarially verified → 21 confirmed / 4 refuted → 9 findings. Run ID `wf_1c303415-ff8`. *(Established LiteLLM/Langfuse/TensorZero/Helicone/OpenLIT baseline.)*
- **Helicone vs Langfuse focused research (2026-06-17):** two parallel primary-source agents — surfaced both acquisitions, the Helicone Claude Code legacy-proxy/Rust-gateway-format issue, Langfuse's active ClickHouse-backed status, and license/self-host detail. Primary sources: [ClickHouse acquires Langfuse](https://clickhouse.com/blog/clickhouse-acquires-langfuse-open-source-llm-observability), [Helicone joining Mintlify](https://www.helicone.ai/blog/joining-mintlify), [Mintlify acquires Helicone](https://www.mintlify.com/blog/mintlify-acquires-helicone), [Langfuse GitHub](https://github.com/langfuse/langfuse), [Helicone Claude Code integration](https://docs.helicone.ai/integrations/anthropic/claude-code), [Helicone AI Gateway](https://docs.helicone.ai/gateway/overview), [Langfuse self-host/license](https://langfuse.com/self-hosting/license-key), [LiteLLM virtual keys](https://docs.litellm.ai/docs/proxy/virtual_keys), [LiteLLM OTel](https://docs.litellm.ai/docs/observability/opentelemetry_integration).
- **TensorZero-alternatives research (2026-06-17):** launched, then **stopped** when this decision rendered it moot (run ID `wf_dfefca79-6a7`).
- **Design context:** [planning.md §0.2, §4, §12a, §15](./planning.md); [references/technologies.md](./references/technologies.md).
