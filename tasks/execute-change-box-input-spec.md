# Spec: thread `box`/`worktree` inputs into the execute-change workflow templates

> **STATUS: IMPLEMENTED** — box/worktree input threading has been in
> production use in agent-orchestration's execute-change/milestone workflows
> for all live runs (kafka-dq 001-e2e-poc through 015-github-mirror). Kept
> for the original diagnosis.

**Status:** ready to implement (diagnosed 2026-07-14, host session, during first live run of `kafka-dq/001-e2e-poc`). Scope: `agent-orchestration` only (two workflow YAMLs, one nested-workflow declaration). Small, mechanical. The repo is lifecycle-governed — run `lifecycle status` and route per its gates.

## Problem

First live launch of the orchestration got the M8 launcher fully working (box started via `cb run --detach`, `conductor run` spawned), then `conductor` errored immediately when the first claudebox-provider agent ran:

```
❌ ProviderError
ClaudeboxProvider requires a 'box' key in the workflow context (the claudebox
box/container id to `cb exec` into), or a 'box' workflow input. ...
```

The launcher **does** pass the box (confirmed in the emitted `conductor_argv`):
`conductor ... run execute-change.yaml --input plan_fixture_path=... --input worktree=<wt> --input box=claudebox-11934eb9d45a`.

But Conductor only threads **declared** inputs into a workflow's context. `workflows/execute-change.yaml`'s `input:` block declares `plan_fixture_path`, `attempt_threshold`, `gates_l1_command`, `notify_dry_run`, `change_id`, `healthcheck_command`, `archive_dry_run` — **not** `box` or `worktree`. So the `--input box=…`/`--input worktree=…` values are discarded, and the provider finds nothing.

### Root cause (verified in provider source)

`conductor/providers/claudebox.py` `execute()`:
```python
box = context.get("box") or self._workflow_input(context).get("box")
if not box: raise ProviderError(...)      # non-retryable
worktree = context.get("worktree") or self._workflow_input(context).get("worktree")
```
The provider's own "M1b resolution" docstring is explicit: the CLI-compatible path is to **declare `workflow.input.box` / `workflow.input.worktree` in the YAML** and pass them with `--input`; the provider then reads `context["workflow"]["input"]["box"]`.

### Why it reaches the *nested* workflow, not the root

`execute-change.yaml` has **no** claudebox-provider agents at root (its steps are `read_plan`=script, `cursor`=set, `milestone_step`=workflow, `full_healthcheck`=script, `healthcheck_failed`=set, `archive_handoff`=script). All three cast agents (implementer/verifier/orchestrator) live in the **nested** `workflows/milestone.yaml`, invoked via `milestone_step` (`type: workflow`). A nested `type: workflow` child's `workflow.input` is populated **only** from the parent's `input_mapping` — it does **not** inherit the root's `workflow.input`. So `box`/`worktree` must be (a) accepted at the root, (b) forwarded through `milestone_step.input_mapping`, and (c) declared by `milestone.yaml`.

### Why the hermetic tests didn't catch it

`tests/test_workflows_stub.py` / `test_workflows_flatten.py` run `--provider stub`, which never requires a `box`. The M8 launcher was built to pass `box`/`worktree` as `--input`, but the M7-flattened templates were never updated to declare/forward them. The stub tier structurally cannot exercise the claudebox provider's box requirement — this is the class of gap the first live run exists to surface.

## Fix (3 edits, two files)

### 1. `workflows/execute-change.yaml` — declare the inputs

In the `input:` block (alongside `plan_fixture_path`, ~line 60), add:

```yaml
    box:
      type: string
      required: true
      description: >-
        The claudebox box/container id the ClaudeboxProvider `cb exec`s into.
        Supplied by the M8 launcher (`orchestration.launch.change`) as
        `--input box=<id>`; forwarded to milestone.yaml via milestone_step's
        input_mapping. Read by the provider as workflow.input.box. (Not needed
        in the stub tier — override the provider with `--provider stub`.)
    worktree:
      type: string
      required: false
      default: ""
      description: >-
        Absolute path of the box's worktree, forwarded by the provider as
        `cb exec --workdir <worktree>`. Optional; launcher supplies it.
```

Note on `required: true` for `box`: a stub-tier run (`--provider stub`) does not use the provider, but Conductor still validates declared required inputs. If any shipped stub/hermetic invocation of `execute-change.yaml` would now fail input-validation, either (a) keep `box` `required: true` and add `--input box=stub` to those invocations, or (b) make `box` `required: false, default: ""` and rely on the provider's own clear ProviderError at run time for the live tier. **Prefer (a)** — a required box matches the live contract and keeps the failure at launch (loud) rather than mid-run; check `tests/test_workflows_stub.py` + `tests/m6_testbed.py` for invocations to update.

### 2. `workflows/execute-change.yaml` — forward through `milestone_step.input_mapping`

In `milestone_step.input_mapping` (~line 160, next to `milestone_id`/`milestone_summary`/`attempt_threshold`), add:

```yaml
      box: "{{ workflow.input.box }}"
      worktree: "{{ workflow.input.worktree }}"
```

### 3. `workflows/milestone.yaml` — declare the inputs it now receives

In milestone.yaml's `input:` block (~line 50, alongside `milestone_id`), add the same `box` (required) + `worktree` (optional, default `""`) declarations as edit 1. This is what makes `context["workflow"]["input"]["box"]` populated for the implementer/verifier/orchestrator agents that actually call the provider.

## Tests

- **Regression (new):** a hermetic assertion that `execute-change.yaml` declares `box`/`worktree` inputs AND `milestone_step.input_mapping` forwards both, AND `milestone.yaml` declares both — i.e. the box path is unbroken end-to-end at the template level. This is the check the stub tier was missing; without it the same gap can silently return.
- **Consent invariant (verify still green):** `tests/test_consent_invariant.py` scans agent-type steps for consent verbs and pins `archive_handoff` as `type=script`. These edits add only inputs + a nested-workflow `type: workflow` step's mappings — no new agent step, no consent verb — so it must stay green. Confirm, don't modify.
- **Existing stub/flatten tests:** update any invocation that now trips required-input validation per the edit-1 note; do **not** weaken an assertion to paper over a real validation failure.

## Verification (end-to-end, the real goal)

Re-run the exact launch (host env already prepared this session — `cb` symlink repointed, `lifecycle` built to `~/.local/bin`):

```bash
cd /Users/jony/code/kentra/harness/agent-orchestration
uv run python -m orchestration.launch.change '{
  "repo": "/Users/jony/code/kentra/harness/kafka-dq",
  "change_id": "001-e2e-poc",
  "box": {"enabled": true},
  "conductor": {"workflow": "/Users/jony/code/kentra/harness/agent-orchestration/workflows/execute-change.yaml"},
  "wait": false
}'
```

Then watch the child (pid in the launch JSON) and its logs under
`<worktree>/.conductor-tmp/conductor.std{err,out}.log`. **Success at this layer** = the run advances **past** the `ClaudeboxProvider requires a 'box'` error into an actual `cb exec <box> claude -p ... --agent implementer` invocation (the first real cast-agent turn). Expect the *next* gap to surface there (real Claude-in-box execution) — that's forward progress, and a separate diagnosis.

## Context / provenance

- Diagnosed while running the first live change (`kafka-dq` `001-e2e-poc`). The worktree + box from the successful launch already exist: `kafka-dq/.worktrees/001-e2e-poc`, box `claudebox-11934eb9d45a` — reused idempotently on re-launch.
- Prerequisite host fixes done in the same session (not part of this spec): `~/.local/bin/cb` repointed to `~/go/bin/claudebox`; `lifecycle` built from spec-lifecycle HEAD `4d1f002` (the v0.1.0 tag predates `lifecycle apply`, which the launcher requires). See also `tasks/cb-run-detach-spec.md` (the `cb run --detach` change, already implemented).
