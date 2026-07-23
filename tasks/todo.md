# Fix open orchestration issues + wire harness box for orch (2026-07-23)

*Delegated fully autonomous by user (this session). Prior todo.md — spec-lifecycle v1 build tracker, completed 2026-07-06 — replaced; its outcomes live in memory + tasks/planning-module-handoff.md.*

## A. Conductor fork patches (kentra-io/conductor) — DONE
- [x] Patch branch on the fork's `kentra-patches` lineage (tip was 5a80095, the pin)
- [x] Patch 1: `limit=` (64 MiB) on the streaming `create_subprocess_exec` (StreamReader 64 KiB kill)
- [x] Patch 2: classify retryability against stderr + noise_lines tail + `result_error_message` (transient API error kill). Deviation from the stopgap sketch: agent-generated content deliberately EXCLUDED (keyword-matching agent prose → false retryables)
- [x] Regression tests (proven to fail on the unpatched provider); fork suite green (20 pre-existing env failures identical on base)
- [x] Pushed as `kentra-io/conductor@ab0ff4c` (branch `kentra-patches`)
- [x] Pin bumped + `uv sync` on host + hermetic corpus unchanged vs main → PR #18 MERGED, CI green
- [x] docs/conductor-fork-patches-pending.md → patch RECORD, nothing pending (auth-masking had already shipped in fork 088e35c)

## B. Milestone auto-commit + resume auth pre-flight — DONE (PR #19 MERGED)
- [x] `orchestration/launch/milestone_commit.py` (+ ruff-format follow-up on main)
- [x] milestone.yaml `commit` script step on verifier-pass route; inputs change_id/commit_dry_run/commit_paths; max_iterations 20→22
- [x] execute-change.yaml forwarding (dict-`get` access — plans without `contract` must not trip StrictUndefined)
- [x] Launcher: production tier sets `commit_dry_run=false` + passes change_id
- [x] Daemon resume: box health/auth pre-flight + one non-interactive `cb login` heal + classified ResumeError
- [x] 20 new tests; suite 227 passed (same 4 env failures as main: live daemon holds test port range)
- [x] Tag `v0.2.2` → GHCR daemon image published; local daemon image rebuilt (`make daemon-image`)

## C. Harness .claudebox wiring for orch — DONE (recreate + daemon restart completed after user closed box/run)
- [x] config.yaml: ORCHESTRATION_DAEMON_URL + ORCHESTRATION_DAEMON_TOKEN env
- [x] Dockerfile: `orch` baked (uv tool → /usr/local/bin, tool venv + managed python under /opt so the `agent` user can run it)
- [x] Image rebuilt (`cb build` → claudebox-project-0f14d014fb55)
- [x] Verified end-to-end from a throwaway container off the new image: `orch runs` as user `agent` reaches the host daemon with the real token
- [x] Host `orch` CLI upgraded (was pre-#17 — no `daemon env`; now current)
- [x] Box recreated (`cb rm && cb run --detach` with daemon token in shell): new image, env wired, in-box `orch runs`/`orch status` verified against the daemon
- [x] Daemon restarted onto the rebuilt image; verified in-container: patched conductor (`_STREAM_READ_LIMIT` present), `milestone_commit` module present, resume `preflight_box_auth` present

## Wrap-up
- [x] Harness tasks/*.md incident notes → resolution status
- [x] Auto-memory updated
- [x] Review below

## Review

All three open issues fixed and merged to agent-orchestration main (PRs #18, #19; fork commit ab0ff4c; tag v0.2.2 published):
1. **Transient API error kills run** → provider retry classification now sees stdout; engine `retry:` machinery handles the blip.
2. **Milestones never committed** → deterministic paths-confined commit step on the verifier-pass route (production tier only; hermetic stays dry-run).
3. **Box auth expiry masquerade** → masking fixed earlier in fork 088e35c; resume now pre-flights + self-heals via `cb login`, else fails with classified remedy.

Harness box is wired for `orch` and the daemon runs the new image (user closed the box + killed the running 001-dag-plan-primitive run to unblock; that run shows `dead: unknown` in the registry and would need a re-launch under the new code).

**End-to-end verification (2026-07-23, post-restart):**
- Hermetic stub launch `e2e-verify` through the NEW daemon: done/success; event stream shows the `commit` step executing after each verifier pass; both milestones report `commit_status: "dry_run"` (correct hermetic behavior).
- Production-tier launcher argv verified: `commit_dry_run=false` + `change_id` forwarded when box enabled.
- Real-commit path proven through the actual conductor engine: milestone.yaml standalone (stub provider, `commit_dry_run=false`) against a scratch git repo → real commit `M3: Verify real commit (zz-test)`, fallback identity, clean worktree after, `commit_status: "committed"` in workflow output.
- In-box: `orch runs`/`orch status` work from the recreated box (env baked at creation; `orch` at /usr/local/bin).
- Test worktrees/branches/fixtures cleaned; `e2e-verify` kept in the registry as a done record.

Not committed in the harness repo (user's active branch `design/planning-domain` carries in-flight work): `.claudebox/config.yaml`, `.claudebox/Dockerfile`, tasks notes, and the agent-orchestration submodule pointer (submodule checked out at new main `79625fc`).

## 2026-07-23 (evening): shared live credential for all boxes (hands-off session)

Goal (user-locked): ONE shared refreshing OAuth credential — host `~/.claude/.credentials.json` — serving interactive claudebox sessions AND orch agent boxes; no per-box provisioning to think about. Setup-token rejected (static, second credential).

- [ ] M0 spike: determine claude's credentials write pattern (in-place vs rename) from the npm JS bundle; fallback = overnight inode watch (baseline inode=138837378, refresh due 23:58Z)
- [ ] M1 claudebox: provisioning option to live-mount host `.credentials.json` into `claude_dir_source` boxes (skip snapshot injection); Go tests; rebuild cb
- [ ] M2 agent-orchestration: `materialize_box` writes the new option; keep pre-flight probe + auto cb login as fallback; tests (also carries the API-only `orch daemon status` change from earlier today)
- [ ] M3 E2E: real box via launch path — mount present, in-box `claude -p` auths, host↔box propagation, two parallel boxes share one file
- [ ] Report design choices
