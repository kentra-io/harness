@AGENTS.md

## Claudebox binary freshness (pin every from-source CLI)

Every CLI the box installs from source or git in `.claudebox/Dockerfile` **must be
pinned to an explicit `ARG …_REF`** (a commit SHA), and that ARG **bumps in lockstep
with the matching submodule pointer** — ideally in the same commit as the
`git submodule update`.

- `milestoned-plan-dag` → `MPD_REF` = `milestoned-plan-dag` pin
- `constitution` → `CONSTITUTION_VERSION` (release-pinned; only advances on a new tag)

**Why:** a bare git URL (`uv tool install git+https://…` / `go install …@latest`)
both resolves `main` HEAD non-deterministically **and** produces a byte-identical
`RUN` line, so Docker's layer cache reuses the stale install forever — `cb build`
looks like it ran but the binary never refreshes. Bumping the `_REF` changes the
layer's cache key, forcing the install to re-run against the pinned commit.
Build from-source CLIs in a fresh clone at the pinned ref, so a prior layer's
install is never reused.

**Never** rely on `cb build` alone to pick up new upstream commits for an unpinned
dependency. To verify freshness after a rebuild, compare `MPD_REF` in the
Dockerfile against `git -C milestoned-plan-dag rev-parse HEAD`, and
`constitution --version` in the box against `CONSTITUTION_VERSION`.
