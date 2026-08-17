FROM claudebox-agent:latest

# --- GitHub CLI + git identity for the kentra-gh-bot service account ---
# gh reads GH_TOKEN/GITHUB_TOKEN from the environment (injected via config.yaml),
# so no `gh auth login` is needed at runtime.
USER root

# Official GitHub CLI apt repository (stable, latest). Supports arm64 + amd64.
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget ca-certificates gnupg \
 && mkdir -p -m 755 /etc/apt/keyrings \
 && wget -nv -O /etc/apt/keyrings/githubcli-archive-keyring.gpg https://cli.github.com/packages/githubcli-archive-keyring.gpg \
 && chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
      > /etc/apt/sources.list.d/github-cli.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends gh \
 && rm -rf /var/lib/apt/lists/*

# System-wide git identity fallback for the bot. Repo-local identities (e.g. the
# harness repo's committed user.name/user.email) still take precedence, so this
# only applies to fresh clones the agent makes on the bot's behalf.
RUN git config --system user.name  "kentra-gh-bot" \
 && git config --system user.email "299105882+kentra-gh-bot@users.noreply.github.com" \
 && git config --system --add safe.directory '*' \
 # Wire git-over-HTTPS to the gh token so `git clone/fetch/push` authenticate
 # with no `gh auth setup-git` step (the token is resolved at use time from env).
 && git config --system credential.https://github.com.helper '!gh auth git-credential' \
 # The container has no SSH keys, only the token; rewrite github SSH remotes
 # (e.g. the adr-sourced-constitution submodule) to HTTPS so they use the token.
 && git config --system url."https://github.com/".insteadOf "git@github.com:"

# --- Go toolchain + golangci-lint (adr-sourced-constitution build) ---
# arm64/amd64 aware; version matches the project's CI ceiling (go.mod floor 1.25).
RUN ARCH=$(dpkg --print-architecture) \
 && wget -nv -O /tmp/go.tgz "https://go.dev/dl/go1.26.4.linux-${ARCH}.tar.gz" \
 && tar -C /usr/local -xzf /tmp/go.tgz \
 && rm /tmp/go.tgz \
 && ln -s /usr/local/go/bin/go /usr/local/go/bin/gofmt /usr/local/bin/ \
 && curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/HEAD/install.sh \
      | sh -s -- -b /usr/local/bin v2.12.2 \
 && GOBIN=/usr/local/bin /usr/local/go/bin/go install golang.org/x/tools/cmd/goimports@latest

# --- uv: Python toolchain for the agent-orchestration module (Conductor is
# Python; the module core is uv-managed — see harness orchestration-implementation-plan.md P2).
# cb itself is NOT baked in: it is built on demand from the claudebox submodule
# (`go build -o ~/.local/bin/cb .`), which version-tracks the checked-out fork —
# exactly what M2 (overlay seam) development needs.
RUN curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR=/usr/local/bin sh

# --- orch: the agent-orchestration CLI (in-box query surface) ---
# In-box `orch runs`/`orch status` reach the HOST daemon via
# ORCHESTRATION_DAEMON_URL/ORCHESTRATION_DAEMON_TOKEN (config.yaml env);
# launches stay host-side. Installed system-wide (tool venv + managed
# python under /opt, launcher in /usr/local/bin) so the runtime `agent`
# user can run what root installed.
#
# PINNED to an explicit commit (like LIFECYCLE_REF below). A bare git URL both
# (a) resolves main HEAD non-deterministically and (b) produces a byte-identical
# RUN line, so Docker's layer cache reuses the stale install forever on rebuild.
# Bumping ORCH_REF changes the cache key -> the layer re-runs -> orch refreshes.
# NOTE: keep ORCH_REF in sync with the harness's agent-orchestration submodule
# pointer (`git -C agent-orchestration rev-parse HEAD`) whenever the submodule bumps.
ARG ORCH_REF=59d35ce47da4ee0ac923c5da263a84cf4cbfe402
RUN env UV_PYTHON_INSTALL_DIR=/opt/uv/python \
        UV_TOOL_DIR=/opt/uv/tools \
        UV_TOOL_BIN_DIR=/usr/local/bin \
    uv tool install --reinstall --python 3.12 "git+https://github.com/kentra-io/agent-orchestration@${ORCH_REF}" \
 && orch --help >/dev/null

# --- kentra SDD primitives on PATH ---
# constitution: pinned static release binary, checksum-verified (see
# spec-lifecycle/docs/releasing.md). CGO-free single binary — no Go build.
ARG CONSTITUTION_VERSION=0.2.0
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in amd64|arm64) ;; *) echo "unsupported arch: $arch" >&2; exit 1 ;; esac; \
    cd /tmp; \
    bin=constitution; repo=adr-sourced-constitution; ver="${CONSTITUTION_VERSION}"; \
    base="https://github.com/kentra-io/${repo}/releases/download/v${ver}"; \
    asset="${bin}_${ver}_linux_${arch}.tar.gz"; \
    curl -fsSL "$base/$asset" -o "$asset"; \
    curl -fsSL "$base/checksums.txt" -o checksums.txt; \
    sha256sum -c --ignore-missing checksums.txt; \
    tar -xzf "$asset" -C /usr/local/bin "$bin"; \
    rm "$asset" checksums.txt; \
    "$bin" --version

# lifecycle (spec-lifecycle CLI): BUILT FROM SOURCE, not a release binary.
# A current release now exists (v0.2.0, with a `lifecycle` cask in
# kentra-io/homebrew-tap) — consumers should install that. The box stays on a
# from-source build DELIBERATELY: a commit pin tracks main between releases, so
# the box can dogfood unreleased work, and it is stricter than a version pin.
# NOTE: keep LIFECYCLE_REF in sync with the harness's spec-lifecycle submodule
# pointer (`git -C spec-lifecycle rev-parse HEAD`) whenever the submodule bumps.
ARG LIFECYCLE_REF=10ad5e20623a18b9ca55825d24ec7d2069f349ab
RUN set -eux; \
    git clone --filter=blob:none --quiet https://github.com/kentra-io/spec-lifecycle /tmp/spec-lifecycle; \
    cd /tmp/spec-lifecycle; \
    git checkout --quiet "$LIFECYCLE_REF"; \
    go build -trimpath -o /usr/local/bin/lifecycle ./cmd/lifecycle; \
    cd /; rm -rf /tmp/spec-lifecycle; \
    lifecycle --version

# milestoned-plan-dag: BUILT FROM SOURCE, for the same reason as `lifecycle`
# above (commit pin > version pin for a dogfooding box). It now publishes
# releases and a cask too, so consumers outside the box install it with
# `brew install kentra-io/tap/milestoned-plan-dag`.
# `lifecycle` shells out to this binary for the plan-stage gate
# (`lifecycle validate --stage plan`) and the archive step-completion gate, and
# warns at `lifecycle init` when it is absent, so the box needs both or neither.
# NOTE: keep MPD_REF in sync with the harness's milestoned-plan-dag submodule
# pointer (`git -C milestoned-plan-dag rev-parse HEAD`) whenever it bumps.
ARG MPD_REF=21c020e4f0e9591668a5db8ecc909d8489dc6f03
RUN set -eux; \
    git clone --filter=blob:none --quiet https://github.com/kentra-io/milestoned-plan-dag /tmp/milestoned-plan-dag; \
    cd /tmp/milestoned-plan-dag; \
    git checkout --quiet "$MPD_REF"; \
    go build -trimpath -o /usr/local/bin/milestoned-plan-dag ./cmd/milestoned-plan-dag; \
    cd /; rm -rf /tmp/milestoned-plan-dag; \
    milestoned-plan-dag --version

USER agent
