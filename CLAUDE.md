@AGENTS.md

<!-- BEGIN spec-lifecycle v1 (managed — do not edit by hand; `lifecycle init` updates it) -->
This project uses `lifecycle` (spec-lifecycle) for staged, gated planning. Before touching related code, read the relevant `openspec/changes/<change>/` artifacts and run `lifecycle status` to see gate state; approve gates only via `lifecycle approve`, never by hand-editing `approval-state.json`.
<!-- END spec-lifecycle v1 -->

## Claudebox binary freshness (pin every from-source CLI)

Every CLI the box installs from source or git in `.claudebox/Dockerfile` **must be
pinned to an explicit `ARG …_REF`** (a commit SHA), and that ARG **bumps in lockstep
with the matching submodule pointer** — ideally in the same commit as the
`git submodule update`.

- `lifecycle` → `LIFECYCLE_REF` = `spec-lifecycle` pin
- `orch` → `ORCH_REF` = `agent-orchestration` pin
- `constitution` → `CONSTITUTION_VERSION` (release-pinned; only advances on a new tag)

**Why:** a bare git URL (`uv tool install git+https://…` / `go install …@latest`)
both resolves `main` HEAD non-deterministically **and** produces a byte-identical
`RUN` line, so Docker's layer cache reuses the stale install forever — `cb build`
looks like it ran but the binary never refreshes. Bumping the `_REF` changes the
layer's cache key, forcing the install to re-run against the pinned commit. Use
`uv tool install --reinstall …` so a prior layer's install doesn't get skipped.

**Never** rely on `cb build` alone to pick up new upstream commits for an unpinned
dependency. To verify freshness after a rebuild, compare the installed commit
(e.g. `cat /opt/uv/tools/agent-orchestration/*/direct_url.json` for `orch`,
`lifecycle --version`) against `git -C <submodule> rev-parse HEAD`.
