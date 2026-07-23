# Upstream ticket draft — conductor `--web-bg` never exits when no client ever connects

Ready to paste into a `microsoft/conductor` issue. Found 2026-07-20 during
agent-orchestration's observability live bring-up; fixed in our fork at
`kentra-io/conductor@5a80095` (branch `kentra-patches`). Line references below are
against upstream `main@5ce3353`.

---

**Title:** `--web-bg` run never exits if no dashboard client ever connects (auto-shutdown grace timer is only armed on WebSocket disconnect)

## Summary

In `--web-bg` mode (or with `CONDUCTOR_WEB_BG=1`), the process is supposed to
auto-shutdown after "workflow completes + all clients disconnect + 30s grace"
(`_BG_GRACE_SECONDS`, `src/conductor/web/server.py:42`). But the grace timer is
**only ever armed from WebSocket-disconnect code paths**. If no dashboard client
ever opens the page, no disconnect ever fires, the timer is never started, and the
process blocks forever in `wait_for_clients_disconnect()` — after the workflow has
already finished.

This makes `--web-bg` unusable for headless/programmatic supervision: any run
nobody happens to watch becomes a zombie that holds its port and never returns.

## Reproduction

```bash
conductor run <any-trivial-workflow>.yaml --web-bg
# do NOT open the printed dashboard URL
# → workflow completes; the detached child process lingers forever
#   (visible via ps; port stays bound; PID file stays)
```

Equivalently, spawn `conductor run <wf> --web --web-port N` with
`CONDUCTOR_WEB_BG=1` in the env and `wait()` on it: the wait never returns.

Conversely, briefly opening the dashboard and closing it *after* completion
releases the process ~30s later — which confirms the timer logic itself works;
only its arming is incomplete.

## Root cause

`WebDashboard._maybe_start_grace_timer()` (`src/conductor/web/server.py:854`)
correctly checks all conditions (bg mode, workflow completed, zero connections,
no timer yet). But it is invoked from only two places:

1. the WebSocket endpoint's `finally` block on client disconnect
   (`src/conductor/web/server.py:345`), and
2. the broadcaster's failed-send cleanup (`src/conductor/web/server.py:741`).

The workflow-completion path (`_on_event`, `src/conductor/web/server.py:375-376`)
only sets `self._workflow_completed = True` — it never calls
`_maybe_start_grace_timer()`. So with zero connections for the whole run, all the
auto-shutdown preconditions become true but nothing ever evaluates them, and
`wait_for_clients_disconnect()` (`src/conductor/web/server.py:874`, awaited from
`src/conductor/cli/run.py:1536`/`2140`) awaits `_bg_event` forever.

## Related latent bug in the same line

`_on_event` sets `_workflow_completed = True` on **any** `workflow_completed` /
`workflow_failed` event — including those emitted by *nested sub-workflow* runs
(they carry `subworkflow_path` in `data`). So a client disconnecting mid-run,
after any sub-workflow finished, arms the 30s timer while the root workflow is
still executing; `_bg_event` is then set prematurely, and the post-run
`wait_for_clients_disconnect()` returns immediately even if a viewer reconnected.
Today that only shortens the post-run dashboard window; combined with the fix
below it would become an early process exit — so the completion check must be
gated on the **root** terminal event.

## Suggested fix

In `_on_event`, on the root workflow's terminal event, arm the timer (it already
no-ops while clients are connected, so watched runs keep the current behavior):

```python
if event.type in ("workflow_completed", "workflow_failed") and not event_dict.get(
    "data", {}
).get("subworkflow_path"):
    self._workflow_completed = True
    try:
        self._maybe_start_grace_timer()   # no-op while clients are connected
    except RuntimeError:
        pass  # no running event loop (sync emit outside the server loop)
```

The `RuntimeError` guard covers `_on_event` being called synchronously with no
running loop (this happens in the existing sync unit tests that `emit()` without
a server).

Working patch with tests: `kentra-io/conductor@5a80095` — adds
`test_unwatched_run_arms_grace_timer_on_completion` and
`test_subworkflow_completion_does_not_set_flag` to
`tests/test_web/test_server.py`; full `tests/test_web` + `tests/test_providers` +
`tests/test_cli/test_web_flags.py` pass (769 passed, 7 skipped).

## Environment

- conductor v0.1.20; root cause confirmed by inspection on `main@5ce3353`
  (identical code) and reproduced live on a `7aaa589`-based fork.
- Observed on macOS host + linux/arm64 container (python:3.12-slim-bookworm),
  Python 3.12.

## Impact

Any headless orchestration that supervises `conductor run` children with the web
dashboard enabled: `wait()`/blocking callers hang indefinitely; unwatched runs
leak processes and dashboard ports.
