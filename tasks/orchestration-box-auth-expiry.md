# Incident: agent-box OAuth expiry masquerades as a provider crash

*Hit 2026-07-15 while resuming the kafka-dq `001-e2e-poc` run (`dc07b086`).*

**Status: FIXED 2026-07-23** — both layers: the provider *masking* shipped
in fork `088e35c` (stdout noise tail surfaced in `ProviderError`, so "OAuth
session expired" is visible without a manual `docker exec`), and daemon
resume now pre-flights the box (`health_probe`), auto-attempts ONE
non-interactive `cb login` from the worktree, re-probes, and fails
loud-and-early with the classified cause + remedy (agent-orchestration
PR #19). The manual diagnosis/fix below stays valid for out-of-band cases.

## Symptom

`conductor resume` dies in ~3s, deterministically, with:

```
❌ ProviderError
claude subprocess exited with code 1: (no stderr output)
```

Identical error string to the documented **transient**-API-error killer
(`orchestration-transient-api-error-kills-run.md`), so it is easy to
misdiagnose as that bug and reach for the retry patch. It is not the same
thing — the transient bug hits mid-run after real work; this one hits
**instantly on the very first agent spawn** and reproduces every time.

## Root cause

The `claudebox` **agent box** (`claudebox-agent` image, e.g.
`claudebox-11934eb9d45a`) does **not** bind-mount the host `~/.claude` — it
gets a materialized minimal `~/.claude` with **credentials injected
separately** (see `change.py:222`; memory `conductor-box-artificial-claude`).
That injected `~/.claude/.credentials.json` had **`expiresAt: 0`** (a dead
placeholder), so in-box `claude -p` fails with
`Failed to authenticate: OAuth session expired and could not be refreshed`.

Because `claude -p` writes the auth failure to **stdout**, the
`ClaudeboxProvider`'s "no stderr output" classifier reports it as an empty
crash instead of surfacing the auth message. That masking is what makes the
two failures look identical.

## Diagnosis (do this first, before assuming transient)

```bash
# Real error the provider hides — run claude -p directly in the box:
docker exec <agent-box> claude -p 'reply OK' --model opus 2>&1
#   OAuth session expired ...  → auth problem (this note)
#   API Error: Connection closed mid-response → the transient bug (other note)

# Confirm host token is still good (fix is cheap if so):
claude -p 'reply OK' --model opus < /dev/null          # host; exit 0 = good
security find-generic-password -s "Claude Code-credentials" -w   # keychain JSON
```

## Fix

Host token valid → re-provision it into the running box with the
purpose-built command, run **from the change's worktree** (`cb login`
resolves the target container from cwd):

```bash
cd <repo>/.worktrees/<change-id>
/Users/jony/go/bin/claudebox login < /dev/null      # "Credentials provisioned into container"
docker exec <agent-box> claude -p 'reply OK' --model opus 2>&1   # verify: OK
```

`cb login` checks host auth first; if the host token were **also** expired it
would fall back to interactive `claude auth login` (needs the user — a
subprocess can't complete OAuth). Here host was valid, so it went straight to
`ProvisionKeychainCredentials`. Then just re-run the `conductor resume`.

## Prevention / follow-ups

- **Provider masking is the real bug to fix upstream:** fold `claude -p`'s
  stdout tail into the `ProviderError` diag (same shape as the transient-error
  `noise_lines` fix) so "OAuth session expired" is visible without a manual
  `docker exec`. Add to `agent-orchestration/docs/conductor-fork-patches-pending.md`.
- Box OAuth is time-boxed; a long human-monitored gap between launch and
  resume (here ~19h) will outlive it. A pre-resume health probe
  (`claude -p 'OK'` in the box) belongs in the M8 launcher / a resume wrapper.
- Consider auto-`cb login` as a launcher pre-flight step.
