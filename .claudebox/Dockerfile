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

# --- uv: Python toolchain. cb itself is not baked in: build it on demand
# from the claudebox checkout (`go build -o ~/.local/bin/cb .`).
RUN curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR=/usr/local/bin sh

# --- kentra SDD primitives on PATH ---
# constitution: pinned static release binary, checksum-verified (see
# adr-sourced-constitution/docs/releasing.md). CGO-free single binary — no Go build.
ARG CONSTITUTION_VERSION=0.3.2
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

# milestoned-plan-dag: BUILT FROM SOURCE at a commit pin, so the box can
# dogfood unreleased work; consumers outside the box install the release
# (`brew install kentra-io/tap/milestoned-plan-dag`). kentra-rpir's plan and
# implement stages call it.
# NOTE: keep MPD_REF in sync with the harness's milestoned-plan-dag submodule
# pointer (`git -C milestoned-plan-dag rev-parse HEAD`) whenever it bumps.
ARG MPD_REF=5da7f1780979e622a3bebe5f9cab14bd84502479
RUN set -eux; \
    git clone --filter=blob:none --quiet https://github.com/kentra-io/milestoned-plan-dag /tmp/milestoned-plan-dag; \
    cd /tmp/milestoned-plan-dag; \
    git checkout --quiet "$MPD_REF"; \
    go build -trimpath -o /usr/local/bin/milestoned-plan-dag ./cmd/milestoned-plan-dag; \
    cd /; rm -rf /tmp/milestoned-plan-dag; \
    milestoned-plan-dag --version

USER agent
