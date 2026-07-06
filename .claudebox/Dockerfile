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

USER agent
