# Spec: `cb run --detach` + launcher adoption

> **STATUS: IMPLEMENTED.** cb run --detach has been the launcher path for
> every orchestrated run since (see tasks/execute-change-box-input-spec.md,
> which already calls it implemented). Kept for the diagnosis writeup.

**Status:** ready to implement (diagnosed 2026-07-14, host session). Spans two repos: `claudebox` (the flag) and `agent-orchestration` (the launcher). Small change; TDD per claudebox CLAUDE.md rules.

## Problem

`orchestration.launch.change` (M8 launcher) runs `cb run` non-interactively (`stdin=DEVNULL`, `change.py:279-288`) to ensure the change box exists. `cb run` does its ensure/create/provision work fine, then **unconditionally** finishes by attaching an interactive Claude session:

- `claudebox/internal/cmd/run.go:183` → `dockerExecInteractive(...)` → `docker exec -it <box> claude --dangerously-skip-permissions` (`cmd.go:25`).
- With non-TTY stdin the docker CLI refuses: `cannot attach stdin to a TTY-enabled container because stdin is not a terminal`, so `cb run` exits 1 **after the box is already up and provisioned**.
- The launcher treats any nonzero exit as fatal (`change.py:288` raises `ChangeLaunchError`), aborting a healthy launch. (Verified live: box `claudebox-11934eb9d45a` for `kafka-dq/.worktrees/001-e2e-poc` was Up when the launcher errored.)

The M6 manual recipe "worked" only because a human tolerated the cosmetic exit-1. Claudebox behavior did not shift (`run.go`/`cmd.go` unchanged since March).

**Why fix claudebox, not just the launcher:** provisioning (credential injection etc.) happens *after* container creation, so "exit 1 + box running" is ambiguous — it could be the cosmetic attach failure or a real provisioning failure. Ignoring the exit code in the launcher would mask the latter; string-matching the docker error is brittle. Claudebox needs a first-class "ensure box, don't attach" verb with a meaningful exit code.

## Part 1 — claudebox: add `--detach` to `cb run`

### Behavior

`cb run --detach` (alias `-d`) does everything `cb run` does **except step 3** (the interactive `docker exec -it ... claude`):

1. Ensure image/network/proxy, create container if needed, provision (unchanged, `run.go` steps 1–2).
2. Skip `dockerExecInteractive`; instead print the container name to stdout and return 0.
3. Exit nonzero iff ensure/create/provision actually failed (existing error paths unchanged).
4. Idempotent: box already running → nothing to create, print name, exit 0.

**Auth edge case (must handle):** in the `!agentRunning` path, `CheckAuthStatus` failure currently launches an *interactive* `claude auth login` (`run.go:76-87`). Under `--detach` that must not happen — fail fast with exit 1 and a clear stderr message (`credentials expired or missing; run 'cb login' or 'claude auth login' interactively, then retry`).

### Touch points

- `main.go:66` — `case "run":` currently ignores trailing args. Parse `args[1:]` for `--detach`/`-d`; unknown flags → usage error, exit 1. Bare `cb` (no args, `main.go:53`) stays interactive.
- `main.go:152 runDefault()` — thread a `detach bool` through to `cmd.RunCommand` (suggested signature: `RunCommand(cmd, cfg, detach, getenv...)` or an options struct — implementer's call, keep the variadic `getenv` convention).
- `internal/cmd/run.go:74` (auth fast-fail) and `run.go:183` (skip exec, print name).
- Help text (`main.go:14`) + `internal/cmd/completion.go` (bash/zsh/fish entries).

### Tests (claudebox rules: TDD, mock only at `Commander`, table-driven where apt)

In `run_test.go` (reuse `newRunCommandMock()` matcher pattern):
- detach + box not running → no `exec -it` call recorded; exit 0; name on stdout.
- detach + box already running → same, and no create/provision calls.
- detach + provisioning failure → exit 1 (not masked).
- detach + `CheckAuthStatus` false → exit 1, **no** `RunInteractive("claude","auth","login")` call.
- non-detach behavior byte-identical to today (regression).

Done = `make test` + `make build` pass; then `make install` (installs to `~/go/bin`).

## Part 2 — agent-orchestration: launcher uses the flag

- `orchestration/launch/change.py:280` — `[cb_path, "run"]` → `[cb_path, "run", "--detach"]`. Keep `stdin=DEVNULL`, keep the strict `returncode != 0` check (it's now meaningful), keep the `docker ps` label resolution as-is.
- Update `start_box`'s docstring (drop the "proven M6 recipe `</dev/null`" framing; the recipe is now `cb run --detach`) and the module docstring at `change.py:9`.
- Check `tests/test_launch_change.py` for any assertion on the `cb run` argv and update.
- Note: requires a rebuilt `cb` ≥ this change; older binaries reject the flag — that failure is loud and self-explanatory, no version sniffing needed.
- Repo is lifecycle-governed — run `lifecycle status` and route per its gates (this is a small bugfix-class change; use judgment on whether it needs a change dir).

## Verification (end-to-end)

From `agent-orchestration`, after `make install` in claudebox:

```bash
uv run python -m orchestration.launch.change '{
  "repo": "/Users/jony/code/kentra/harness/kafka-dq",
  "change_id": "001-e2e-poc",
  "box": {"enabled": true, "cb_bin": "/Users/jony/go/bin/claudebox"},
  "conductor": {"workflow": "/Users/jony/code/kentra/harness/agent-orchestration/workflows/execute-change.yaml"},
  "wait": false
}'
```

Expect exit 0 + launch JSON (pid). The worktree/box from the failed 2026-07-14 attempt already exist (`kafka-dq/.worktrees/001-e2e-poc`, box `claudebox-11934eb9d45a`) — the launcher/`--detach` should treat both as idempotent reuse.

## Environment cleanup (same session, trivial)

- `rm ~/.local/bin/cb` — broken symlink to the old `claude-sandbox` path (shadows nothing; `cb` is a zsh function wrapping `claudebox`).
- `box.cb_bin` must stay an absolute path on this host (`/Users/jony/go/bin/claudebox`) since `cb` is a shell function invisible to subprocesses — already supported and error-messaged by the launcher; no code change.
