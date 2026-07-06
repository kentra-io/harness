# Interactive Excalidraw canvas for planning (optional / experimental)

> **Status:** opt-in experiment, not a committed dependency. Nothing here runs
> unless you (a) start the host-side canvas and (b) keep `.mcp.json`. Everything
> is removable in one step — see [Opting out](#opting-out). This doc exists so
> the option is documented and reproducible, whether or not we adopt it.

A live [Excalidraw](https://excalidraw.com) whiteboard that the agent and you
**co-edit in real time** during planning: the agent draws shapes/diagrams on a
canvas open in your browser, you drag things around by hand, and the agent reads
your changes back to keep iterating. Built on
[`yctimlin/mcp_excalidraw`](https://github.com/yctimlin/mcp_excalidraw).

---

## What it gives you

- **Agent-drawn diagrams** on a real canvas (architecture, flows, mind maps),
  via 26 tools: element CRUD, `batch_create_elements`, `create_from_mermaid`,
  align/distribute/group, snapshots, export/import.
- **Two-way loop:** the agent can call `describe_scene` (structured element
  list) and `get_canvas_screenshot` (rendered PNG) to *see your manual edits*
  and respond to them. (Demonstrated: the agent detected a roof being dragged
  off a house purely from reading canvas state.)
- **Live view** in your browser at `http://localhost:3000`.

---

## Architecture — and why it's split

```
  YOUR HOST (macOS)                    │   CLAUDEBOX CONTAINER
                                       │
  browser ──► localhost:3000           │   Claude Code
                  ▲                    │      │ spawns via .mcp.json (stdio)
                  │ websocket sync      │      ▼
            Canvas web server  ◄────────┼── MCP server  (mcp-excalidraw-server)
            (Docker, port 3000)         │   EXPRESS_SERVER_URL=
            in-memory element store     │     http://host.docker.internal:3000
                                       │
                                       │   …or the agent can hit the same
                                       │   server directly over REST (curl).
```

**The claudebox constraint that forces this shape:** this repo runs inside a
**claudebox** sandbox where **all `-p` host port bindings are stripped**
(`network.expose_host_ports: []`, `docker.testcontainers: false`). A container
port can therefore *never* be reached by your host browser.

The resolution:

| Piece | Where it runs | Why there |
|---|---|---|
| **Canvas web UI** (port 3000) | **Your host**, outside claudebox | So your browser can open `localhost:3000`. claudebox can't publish a container port to the host. |
| **MCP server** (stdio) | **Inside** the claudebox container | Spawned by Claude Code from `.mcp.json`. It only makes *outbound* connections — allowed. |
| **Bridge** | env var | The in-container MCP server reaches the host canvas via `host.docker.internal:3000` (→ `192.168.65.254`, confirmed routable), instead of the upstream default `127.0.0.1:3000`. |

That single URL override is the **entire** claudebox adaptation. See the project
memory note `claudebox-host-served-ui` — this same pattern (run viewable servers
on the host, point in-container processes at `host.docker.internal`) applies to
**any** browser-facing service in this harness, not just Excalidraw.

---

## Components in this repo

| Path | Role | Git |
|---|---|---|
| `.mcp.json` | Registers the `excalidraw` MCP server (project scope), pointed at `host.docker.internal:3000`. **This is the core deliverable.** | untracked (decide whether to commit) |
| `.claude/skills/excalidraw-skill` → `.agents/skills/excalidraw-skill` | The agent-side driver skill (layout best-practices, anti-patterns, MCP+REST cheatsheet). Symlinked for Claude Code. | untracked |
| `skills-lock.json` | Lockfile written by `npx skills add`. | untracked |
| `EXCALIDRAW.md` | This document. | untracked |

> Nothing is committed yet — `git status` shows all of the above as untracked.
> If we adopt this, commit `.mcp.json`, `EXCALIDRAW.md`, `skills-lock.json`, and
> the skill folder so the team shares one config.

---

## Setup from scratch (reproduce)

Prereqs already satisfied here: Node 22 in-container, Docker Desktop on host.

1. **MCP server config** — `.mcp.json` (already present):
   ```json
   {
     "mcpServers": {
       "excalidraw": {
         "command": "npx",
         "args": ["-y", "mcp-excalidraw-server@1.0.7"],
         "env": {
           "EXPRESS_SERVER_URL": "http://host.docker.internal:3000",
           "ENABLE_CANVAS_SYNC": "true"
         }
       }
     }
   }
   ```

2. **Agent skill** (already installed):
   ```bash
   npx skills add yctimlin/mcp_excalidraw@excalidraw-skill -y
   ```

3. **Start the canvas ON YOUR HOST** (a normal terminal on macOS — *not* inside
   claudebox, which would strip the `-p`):
   ```bash
   docker run -d --name excalidraw-canvas -p 3000:3000 \
     ghcr.io/yctimlin/mcp_excalidraw-canvas:latest
   ```
   `-p 3000:3000` binds `0.0.0.0` on the host, so the canvas is reachable both
   from your browser (`localhost:3000`) and from the container
   (`host.docker.internal:3000`).

4. **Open** <http://localhost:3000>.

5. **Restart Claude Code** so it loads `.mcp.json` *and* the new skill (both are
   read at session start). Approve the `excalidraw` server when prompted.

---

## Two ways the agent drives it

The agent prefers **MCP mode** and falls back to **REST mode** — both talk to the
same canvas server.

### MCP mode (preferred)
Tools appear as `mcp__excalidraw__*` (e.g. `batch_create_elements`,
`get_canvas_screenshot`). Cleaner payloads (labels via `"text"`, arrows via
`startElementId`/`endElementId`), plus screenshot/viewport/URL export.

> **Gotcha observed:** after a Claude Code restart the MCP server reconnects but
> its tools can take a few moments to register as callable. If they're not ready,
> the agent uses REST instead — no need to wait.

### REST mode (fallback, always works once the canvas is up)
Direct HTTP to `http://host.docker.internal:3000`. Verified endpoints:

| Action | Call |
|---|---|
| Reachability | `GET /` → 200 |
| Create elements | `POST /api/elements/batch` (body `{"elements":[…]}`) |
| List all | `GET /api/elements` |
| Update one | `PUT /api/elements/:id` |
| Delete one | `DELETE /api/elements/:id` |
| Clear | `DELETE /api/elements/clear` |
| Screenshot | `POST /api/export/image` body `{"format":"png"}` |

**REST format differences vs MCP:** labels use `"label":{"text":"…"}` (not
`"text"`); arrows use `"start":{"id":"…"}` / `"end":{"id":"…"}`; `fontFamily`
must be a string or omitted.

**Screenshot quirk:** `POST /api/export/image` returns **JSON**, not a raw image
— the PNG is base64 in the `.data` field. Decode it:
```bash
curl -s -X POST http://host.docker.internal:3000/api/export/image \
  -H 'Content-Type: application/json' -d '{"format":"png"}' -o /tmp/exp.json
node -e 'const fs=require("fs");const j=JSON.parse(fs.readFileSync("/tmp/exp.json","utf8"));
  fs.writeFileSync("/tmp/canvas.png",Buffer.from((j.data||"").replace(/^data:image\/\w+;base64,/,""),"base64"))'
```

---

## The two-way loop (reading your edits)

This is what makes it useful for planning rather than just rendering:

1. Agent draws (`batch_create_elements` / `POST /api/elements/batch`).
2. You edit on the canvas (drag, resize, recolor, add).
3. Agent reads back:
   - **`describe_scene`** / `GET /api/elements` → element IDs, positions, labels
     → diff against what it drew to detect adds/moves/deletes.
   - **`get_canvas_screenshot`** / `POST /api/export/image` → rendered PNG for
     visual verification.
4. Agent reacts and iterates with you.

---

## Verify it's working (smoke test)

```bash
# from inside the container:
curl -s -m5 -o /dev/null -w '%{http_code}\n' http://host.docker.internal:3000        # expect 200
curl -s http://host.docker.internal:3000/api/elements                                  # current scene JSON
```

---

## Troubleshooting

- **`http_code=000` / connection refused:** the host canvas container isn't
  running. `docker ps | grep excalidraw-canvas` on the **host**; start it (step 3).
- **MCP tools missing after restart:** give them a moment to register, or just
  use REST. Confirm the server loaded: it's listed in `.mcp.json` and should
  appear in Claude Code's MCP server list.
- **`host.docker.internal` not resolving:** requires Docker Desktop (it provides
  the host gateway, here `192.168.65.254`). Confirm with
  `getent hosts host.docker.internal`.
- **Elements drawn but not visible:** they may be off-screen — `set_viewport`
  with `scrollToContent:true`, or scroll/zoom-to-fit in the browser.
- **Duplicate text elements after clear+redraw:** the frontend auto-syncs the
  full scene back and Excalidraw re-injects bound texts. Avoid putting labels on
  big background rectangles; use free-standing `text` elements instead.

---

## Notes & caveats

- **Persistence:** the canvas stores elements **in memory** — restarting the
  container clears it. Use `export_scene` / `import_scene` (or `GET /api/elements`
  saved to a file) to persist work.
- **Security:** the canvas binds the host's `0.0.0.0:3000`. Fine for local dev;
  don't run it on a shared/untrusted network without restricting the bind.
- **Provenance:** server `mcp-excalidraw-server@1.0.7` (npm) and skill
  `yctimlin/mcp_excalidraw@excalidraw-skill`; canvas image
  `ghcr.io/yctimlin/mcp_excalidraw-canvas:latest`. Installer security scan at
  install time: Gen Safe · Socket 0 alerts · Snyk Low Risk.

---

## Opting out

If we decide not to keep this:

```bash
# on the host:
docker stop excalidraw-canvas && docker rm excalidraw-canvas

# in the repo:
rm .mcp.json EXCALIDRAW.md skills-lock.json
rm -rf .agents/skills/excalidraw-skill .claude/skills/excalidraw-skill
```

Then restart Claude Code. (Leaving `.mcp.json` in place but never starting the
canvas is also harmless — the MCP server just can't reach a canvas.)
