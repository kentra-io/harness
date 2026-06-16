# Conductor Integration Notes (implementation reference)

*Generated: 2026-06-16. Source-level reconnaissance of [conductor](./conductor) (`references/conductor`) for building the harness on top of it. Companion to [../planning.md](../planning.md) (§0, §12a, §13) and [../fabro-vs-conductor-evaluation.md](../fabro-vs-conductor-evaluation.md). All file paths are under `references/conductor/src/conductor/` unless noted; cited line numbers are approximate and may drift across Conductor versions — re-verify against the pinned version.*

> **Why this doc exists.** `planning.md` records the *design/approach*; this records the *granular build recon* so we don't re-explore the source. Three build pieces: (1) a `ClaudeboxProvider`, (2) the multi-run dashboard aggregator, (3) `feature.yaml`/`bug.yaml` lifecycles. Everything here is **additive** to Conductor (no core edits).

---

## 1. `ClaudeboxProvider` — drive `claude -p` inside a `cb` box

### The `AgentProvider` ABC (the seam) — `providers/base.py`
A new provider must implement three methods + one classvar:
- `async execute(agent, context, rendered_prompt, tools, interrupt_signal, event_callback) -> AgentOutput`
- `async validate_connection() -> bool`
- `async close() -> None`
- `CAPABILITIES: ProviderCapabilities` (classvar; enforced at import via `__init_subclass__` — missing it raises `TypeError`)

`execute()` receives:
- `agent: AgentDef` — full step config (model, system_prompt, output schema, retry, …)
- `context: dict` — accumulated workflow context (all prior step outputs)
- `rendered_prompt: str` — already Jinja2-rendered by the executor (`executor/agent.py:158`)
- `tools: list[str] | None`
- `interrupt_signal: asyncio.Event | None`
- `event_callback: EventCallback | None` — `Callable[[str, dict], None]` (`base.py:22`)

`AgentOutput` shape (`base.py:65-108`, dataclass):
- `content: dict` (required) — structured output matching the declared schema, or `{"response": "<text>"}` when no schema
- `raw_response: Any` (required)
- `tokens_used / input_tokens / output_tokens / cache_read_tokens / cache_write_tokens: int | None`
- `model: str | None`, `partial: bool = False`

Streaming events to emit via `event_callback` (so the dashboard/JSONL see them):
- `event_callback("agent_turn_start", {"turn": "awaiting_model"})` before the subprocess
- `event_callback("agent_message", {"content": text})` for text
- `event_callback("agent_tool_start", {...})` / `("agent_tool_complete", {...})` for tools

### The invocation
Cleanest seam = a **new provider class** (not a PATH wrapper, not monkeypatch). In `execute()`:
```python
proc = await asyncio.create_subprocess_exec(
    "cb", "exec", container_id,
    "env", f"ANTHROPIC_BASE_URL={litellm_url}", f"ANTHROPIC_AUTH_TOKEN={key}",
    "claude", "--bare", "-p", rendered_prompt,
    "--model", agent.model,                       # opus | sonnet | haiku
    "--permission-mode", "bypassPermissions",     # non-interactive; refused as root → keep `agent` user
    "--output-format", "stream-json", "--verbose",
    stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
)
# read stdout line-by-line; each line is a JSON event (see stream-json schema below);
# extract the terminal {"type":"result","subtype":"success","result":...,"usage":{...}}
```
- **~140–160 LOC** total, modeled on `providers/claude_agent_sdk.py` (~130-line `execute()`).
- Register in `providers/factory.py` (`create_provider` match block) + `providers/capabilities.py` (`_PROVIDER_CLASS_PATHS`).
- `validate_connection()` → check `cb` + container reachable (mirror `claude_agent_sdk.py:396-430`).
- Box lifecycle: `cb run` (spawn box+worktree per issue) happens at run start (in the embedding service, §2) or in a `script` step; `execute()` just `cb exec`s into the existing box.

### `claude` headless facts (verified against Claude docs)
- Headless: `claude -p --output-format stream-json --verbose` (also `text`/`json`); stdin pipes a prompt; 10 MB stdin cap.
- `--bare` = recommended for scripted/SDK calls (skips auto-discovery of hooks/skills/plugins/MCP/CLAUDE.md unless explicitly provided → reproducible). **Auth in `--bare` must be `ANTHROPIC_API_KEY` / `apiKeyHelper`** (no OAuth/keychain).
- Non-interactive permissions: `--permission-mode bypassPermissions` (== `--dangerously-skip-permissions`; **refused when running as root**, so keep the `agent` user) or `--permission-mode dontAsk --allowedTools "Bash,Read,Edit"`.
- Per-invocation model: `--model {opus|sonnet|haiku}` or full id (`claude-opus-4-8`, `claude-sonnet-4-6`); each `claude -p` is a **fresh session** (no carryover except on-disk files/CLAUDE.md) unless `--continue`/`--resume`.
- `stream-json` line types: `system/init`, `system/api_retry`, `stream_event` (token deltas: `.event.delta.type=="text_delta"` → `.event.delta.text`), terminal `result` with `total_cost_usd` + per-model breakdown.
- **No ACP mode** in Claude Code (this is why Fabro's ACP backend was a dead end).
- `docker exec -i` / `cb exec` preserves stdin/stdout streaming — works for the stdio pipe.

### Fresh-context-per-stage = native
`claude_agent_sdk` provider sets `CAPABILITIES.concurrent_safe=True`, `checkpoint_resume=False` — "independent subprocess per query()". Each step = fresh `claude` subprocess; **no session carryover**. This *is* "the artifact is the interface": steps share the issue's worktree, so the impl step's files are visible to the verify step. The `instructions_preamble` (workspace CLAUDE.md) is prepended to every step's prompt — use it to tell every step the shared worktree path.

---

## 2. Embed Conductor as a library + the multi-run dashboard

Conductor is **fully embeddable** (not CLI-only). `WorkflowEngine` (`engine/workflow.py`) is plain, stateless-across-instances.

### Constructor (`engine/workflow.py:291-346`)
```python
engine = WorkflowEngine(
    config: WorkflowConfig,
    registry: ProviderRegistry | None,          # preferred multi-provider
    provider: AgentProvider | None,             # legacy single
    skip_gates: bool = False,
    workflow_path: Path | None = None,
    interrupt_event: asyncio.Event | None = None,
    event_emitter: WorkflowEventEmitter | None = None,   # ← our subscriber hook
    keyboard_listener=None, web_dashboard=None,
    run_context: RunContext | None = None,      # ← inject run_id/log_file
    instructions_preamble: str | None = None,
)
result = await engine.run(inputs: dict)
```
The CLI (`cli/run.py:1288 run_workflow_async`) is just this wiring — use it as the reference pattern.

### N concurrent runs in one process (the aggregator)
```python
async def launch_run(workflow_path, inputs, issue_number, repo):
    config = load_config(workflow_path)
    config.workflow.metadata.update({"issue": str(issue_number), "repo": repo})  # correlation (§4)
    emitter = WorkflowEventEmitter()
    log = EventLogSubscriber(config.workflow.name)        # JSONL writer; .run_id, .path
    emitter.subscribe(log.on_event)                       # durable JSONL
    emitter.subscribe(lambda e: store.ingest(log.run_id, issue_number, e))  # live board
    async with ProviderRegistry(config) as registry:
        engine = WorkflowEngine(config, registry=registry, event_emitter=emitter,
            run_context=RunContext(run_id=log.run_id, log_file=str(log.path)))
        return await engine.run(inputs)

results = await asyncio.gather(*[launch_run(...) for issue in issues])
```
No Conductor-side concurrency cap; only provider rate limits + event-loop capacity. Each run needs its own emitter + `EventLogSubscriber` + interrupt `asyncio.Event`.

### Event bus — `events.py`
`WorkflowEvent(type: str, timestamp: float, data: dict)` (frozen). `WorkflowEventEmitter` is synchronous, thread-safe pub/sub: `.subscribe(cb)`, `.unsubscribe(cb)`, `.emit(event)` (subscriber exceptions logged + skipped).

**Event type catalog** (canonical: `web/frontend/src/types/events.ts:9-53`; payloads via `data`):
- Lifecycle: `workflow_started` (carries `run_id`, `metadata`, full `yaml_source`, `system{pid,run_id,...}`), `workflow_completed` (`elapsed,output`), `workflow_failed` (`error_type,message,agent_name`), `checkpoint_saved` (`path,agent_name`)
- Agent: `agent_started` (`agent_name,iteration,agent_type,context_window_max`), `agent_completed` (`agent_name,elapsed,model,tokens,input_tokens,output_tokens,cost_usd,output,output_keys,context_window_used/max`), `agent_failed`, `agent_timeout`, `agent_paused`, `agent_resumed`, `agent_turn_start`, `agent_message`, `agent_reasoning`, `agent_tool_start`, `agent_tool_complete`, `agent_prompt_rendered`
- Gates: `gate_presented` (`agent_name,prompt,options[],option_details[]`), `gate_resolved` (`selected_option,route,additional_input`)
- Routing: `route_taken` (`from_agent,to_agent`)
- Script/set/wait: `script_started/completed/failed`, `set_*`, `wait_*`
- Parallel/for_each: `parallel_started/…/completed`, `for_each_started/item_started/item_completed/item_failed/completed`
- Subworkflow: `subworkflow_started/completed/failed` (events stamped with `data.subworkflow_path: list[str]`)
- Limits/dialog: `iteration_limit_reached/resolved`, `dialog_started/message/completed`

### Durable JSONL log — `engine/event_log.py`
- Path: `$TMPDIR/conductor/conductor-<workflow_name>-<YYYYMMDD-HHMMSS>-<run_id>.events.jsonl`
- `<run_id>` = `secrets.token_hex(4)` (8 hex) or `CONDUCTOR_RUN_ID` env (validated `[0-9a-fA-F]{1,32}`)
- Write mode for fresh runs, **append** for resumed (`event_log.py:113`); **flushed after every line** (`:173`) → safe to `tail -F` / inotify
- Each line = `WorkflowEvent.to_dict()` = `{"type","timestamp","data"}`. Complete enough to reconstruct run state (the dashboard's `replay_events_from_jsonl()` proves it, `web/server.py:444-503`).
- Wired unconditionally in `cli/run.py:1387` (`EventLogSubscriber(name)` → `emitter.subscribe(.on_event)`).

### Built-in single-run web view (reuse as per-issue drill-down) — `web/server.py`
One `WebDashboard` per run (in-memory `_event_history`, FastAPI+uvicorn, **React + `@xyflow/react`** frontend — *not* Cytoscape). Endpoints:
- `GET /` (index), `GET /api/state` (full event array — replay buffer), `GET /api/info` (`{run_id,workflow_name,started_at,metadata,conductor_version}`), `GET /api/logs`, `GET /api/files/{path}`
- `POST /api/stop` (graceful), `POST /api/kill`, `POST /api/resume`
- `GET /ws` (WebSocket: server pushes each event `{type,timestamp,data}`; client sends `gate_response`/`dialog_message`/`iteration_limit_response`)
Frontend always `GET /api/state` first (replay), then upgrades to `/ws`. For multi-run, prefer the **custom-subscriber** path (skip `WebDashboard`, subscribe our own callback to each run's emitter) over one-dashboard-per-port.

---

## 3. Workflow YAML — lifecycle, routing, retry, gates

Top-level (`config/schema.py WorkflowConfig`): `workflow{name,entry_point,runtime{provider,default_model,mcp_servers,…},input,context{mode},limits{max_iterations 1-500},cost{pricing},hooks,metadata}`, `tools`, `agents[]`, `parallel[]`, `for_each[]`, `output`.

`AgentDef` covers all step types via `type: agent|human_gate|script|set|wait|workflow|terminate`. Key fields: `name, type, model, provider, system_prompt, prompt, input[], tools[], output{schema}, routes[], reasoning{effort}, retry{max_attempts,backoff,delay_seconds,retry_on}, timeout_seconds`. Script-only: `command,args,env,working_dir`. **`AgentDef` has `extra="forbid"`** (`schema.py:495`) → arbitrary per-step metadata needs a **1-line add**: `metadata: dict[str, Any] = {}`.

### Bounded retry → escalate → human gate (the §10 ladder)
```yaml
- name: implementer
  type: agent
  provider: claudebox
  model: sonnet
  retry: { max_attempts: 3, backoff: exponential, delay_seconds: 2 }   # first + 2 retries
- name: verifier
  type: agent
  model: haiku
  output: { passed: { type: boolean } }
  routes:
    - to: implementer        # loop back on fail (bounded by limits.max_iterations)
      when: "{{ verifier.output.passed == false }}"
    - to: orchestrator
      when: "{{ verifier.output.error_count > 2 }}"
    - to: "$end"
- name: orchestrator         # Opus triage
  type: agent
  provider: claudebox
  model: opus
  routes:
    - to: human_escalation
      when: "{{ orchestrator.output.needs_human == true }}"
    - to: implementer
      when: "{{ orchestrator.output.action == 'retry' }}"
- name: human_escalation
  type: human_gate
  prompt: "Impl failed repeatedly. Choose:"
  options:
    - { label: "Revise plan", value: revise, route: "$end" }
    - { label: "Abandon",     value: abandon, route: "$end" }
```
- Routing: `engine/router.py` evaluates `routes[].when` first-match, with `output` in eval context (Jinja2 `{{ }}` preferred; simpleeval flattens `output.passed`→`output_passed`). `to:` may name any step, `"$end"`, or a `human_gate`.
- Retry: `RetryPolicy.max_attempts` (1-10). For `claude-agent-sdk`/our provider, retry is workflow-level (provider doesn't self-retry).
- HITL: `human_gate` handled at `workflow.py:~2554` — renders prompt, emits `gate_presented`, waits for selection (terminal Rich prompt OR web `POST`), stores `{selected,additional_input}`, follows the option's `route`. `--skip-gates` auto-picks first option (CI).

### Context / working dir
- Context modes: `accumulate` (default), `last_only`, `explicit`. Steps reference `{{ prior_step.output.field }}`.
- Agent steps inherit the **process cwd** for the spawned `claude` (no per-agent `working_dir`; that's script-only). So set the embedding service's cwd to the issue worktree (or pass abs path in the prompt). Files written by one step are visible to the next.

### Checkpoint/resume
`CheckpointManager.save_checkpoint()` on **failure** (not success) → `$TMPDIR/conductor/checkpoints/<name>-<ts>-<hex>.json` (full context, iteration count, inputs). `conductor resume workflow.yaml` restores from latest. **`checkpoint_resume=False` for claude-CLI** (CLI session not portable) → durable resume unit is the **artifact/stage**, re-run the stage (aligns with §6).

---

## 4. Correlation / tagging by issue
- `run_id` originates in `EventLogSubscriber` (`event_log.py:130-141`), flows via `RunContext` into `WorkflowEngine`, appears in `workflow_started.data.run_id` + `.data.system.run_id`. **Not re-stamped per event** → join later events by the opening `workflow_started`, or by JSONL filename (contains `run_id`) + a `run_id→issue` side table.
- `workflow.metadata` (`schema.py:1604`) is `dict[str,Any]`, surfaced in `workflow_started.data.metadata`. Inject at runtime: `config.workflow.metadata["issue"] = str(n)`; CLI also has `--metadata key=value`. **This is the issue-number hook.**
- For the telemetry plane, also tag LiteLLM (metadata / virtual key) and rely on Claude Code's `X-Claude-Code-Session-Id` / `X-Claude-Code-Agent-Id` headers for per-session cost attribution.

---

## 5. Provider factory & registration
- `providers/factory.py:create_provider(provider_type, …)` — match block; add `claudebox`.
- `providers/capabilities.py:_PROVIDER_CLASS_PATHS` — add the dotted path.
- `providers/registry.py:ProviderRegistry` — async context manager; resolves per-step `provider`/`model`. Different steps in one run can use different providers (claudebox now; codexbox/api later — `planning.md §0.1`).

## 6. Key files
| Concern | File |
|---|---|
| Provider ABC | `providers/base.py` |
| Reference provider (claude CLI) | `providers/claude_agent_sdk.py` |
| Factory / capabilities / registry | `providers/factory.py`, `providers/capabilities.py`, `providers/registry.py` |
| Engine (run loop, RunContext, `_emit`) | `engine/workflow.py` |
| Event bus | `events.py` |
| JSONL log | `engine/event_log.py` |
| Router (routes/when) | `engine/router.py` |
| Schema (AgentDef, RetryPolicy, metadata) | `config/schema.py` |
| Usage / pricing | `engine/usage.py`, `engine/pricing.py` |
| Human gate | `gates/human.py` |
| Web dashboard | `web/server.py`, `web/static/index.html`, `web/frontend/src/types/events.ts` |
| CLI reference wiring | `cli/run.py:1288` |
