# Latency Analysis: adr-sourced-constitution M0–M5 build

Source data: main session `f8ce1a30-e333-4d78-a4ad-7ed688a32ca1.jsonl`, 25 subagent
transcripts under `.../f8ce1a30-e333-4d78-a4ad-7ed688a32ca1/subagents/`, GitHub PR/CI
history for `kentra-io/adr-sourced-constitution`. All timestamps are raw ISO-8601 UTC
(`Z`) as logged — no timezone conversion applied anywhere below.

Session span analyzed: **2026-07-02T18:17:48.701Z → 2026-07-03T08:12:35.055Z = 13.91 h
(834.8 min)**, continuous — the main log has no gap that reads as user-idle/overnight;
every gap >10 min corresponds to the orchestrator waiting on exactly one in-flight
subagent (verified below). This was a single unbroken, fully-automated overnight run.

---

## 1. Total wall-clock breakdown

| Bucket | Hours | % of total |
|---|---:|---:|
| Total span (first M0 dispatch → last observed event) | 13.91 h | 100% |
| (a) Nobody working (user-idle / session breaks) | ~0 h | ~0% |
| (b) Known M3 stale-agent stall | 2.55 h | 18.3% |
| (b′) M3 code-quality-review anomaly (long silent turn + API disconnect, see §2) | 2.86 h | 20.6% |
| (c) Productive/structural agent + orchestrator time | ~8.5 h | 61.1% |

There is **no meaningful (a) bucket** — I checked every gap >10 min in the main log
(`f8ce1a30-*.jsonl`) against the subagent transcripts and every one maps to the
orchestrator blocked on a single running subagent (implementer, reviewer, or fixer),
which is expected given the pipeline is strictly sequential per milestone. This
contradicts a "user was asleep for hours" theory — the wall-clock is ~entirely
agent/API/pipeline time, not idle time.

The two large incident buckets (b) and (b′) both live inside **M3** and are analyzed in
detail in §2. Together they account for **5.41 h of the 13.91 h total (38.9%)**.

---

## 2. The M3 incidents, precisely dated

### 2a. Known stale-agent stall — confirmed at 2.55 h (153.2 min)

Evidence, from `agent-a99da0448005ed70f.jsonl` ("Implement M3 guard (Opus)"):

- The implementer finished its real work and reported done at `2026-07-02T22:41:41.565Z`:
  *"Milestone M3 is complete. PR #4 is up with all 7 CI legs green... COMPLETE (PR open,
  CI green, not merged per instructions)"*.
- **`2026-07-03T03:18:59.831Z`** — a stray coordinator message lands on this same
  (already-finished) agent: *"The coordinator sent a message while you were working:
  Quality review returned REQUEST CHANGES on `m3-guard` — one critical verified bypass +
  several false-green paths. Apply all of these, push, CI gr[een]..."* — i.e. the fix
  round for the M3 quality-review findings was misrouted to the **stale, already-done
  implementer session** instead of a fresh "Fix M3" agent.
- The stale agent then grinds inconclusively (confused/misapplied edits, `git.go` /
  `checkgit.go`, repeated "file has not been read yet" errors) until it is manually
  killed: **`2026-07-03T05:52:11.951Z`** — `[Request interrupted by user]`.
- A fresh, correctly-scoped agent, `agent-a1807b7dc38caf592` ("Fix M3 review findings
  (Opus)"), is dispatched **`2026-07-03T05:53:02.599Z`** and finishes the actual fix in
  **14.1 min** (by 06:07:10), followed by a 4.75 min verify (06:07:51→06:12:36), and PR
  #4 merges at **06:13:00**.

**Stall = 03:18:59.831Z → 05:52:11.951Z = 153.2 min = 2.55 h.** This matches the user's
"known ~2.5h stall" description exactly, and is now dated precisely.

### 2b. A second, previously-unflagged M3 anomaly: 2.86 h inside the code-quality review

`agent-a06f4f378bb65f765.jsonl` ("Code-quality review M3 (Opus)") ran
**23:10:44.291Z → 03:18:25.239Z = 247.7 min (4.13 h)** — 6–30x longer than any other
milestone's quality review (M0: 11.2 min, M1: 15.1 min, M2: 39.6 min, M4: 7.6 min, M5:
2.1 min). Within that span:

- **125.9 min silent gap**: `2026-07-03T00:06:47.345Z → 2026-07-03T02:12:39.053Z`. The
  message before is a plain `Read` tool call; the message after is a `thinking` block
  with no visible tool activity in between — a single turn (or an unlogged connectivity
  gap) that consumed over two hours with no observable work product.
- **`2026-07-03T02:28:25.241Z`** — the turn ends with: *"API Error: Connection closed
  mid-response. The response above may be incomplete."*
- **10s later**, the coordinator injects a recovery message: *"Your session was cut off
  by a connection error mid-review. Please pick up where you left off..."*
- **45.6 min recovery gap**: `2026-07-03T02:28:54.524Z → 2026-07-03T03:14:31.239Z`
  before the agent resumes real tool use.

**Anomalous idle time inside this one agent: 125.9 + 45.6 = 171.5 min = 2.86 h**, out of
its 247.7-min total span — i.e. **69% of the M3 review's wall-clock was this stall/API
incident**; real review work (reading tests, running `go build`/`go vet`/`go test`,
writing the findings report) accounts for the remaining ~76 min, which is in line with
M2's 39.6-min review scaled up for guard-package scope.

This is a **distinct** incident from 2a (different agent, earlier in the timeline, ends
before 2a begins) — the two do not overlap and are additive: M3 alone burned
**2.55 + 2.86 = 5.41 h** on two separate stall/infra incidents, on top of its own
structural pipeline time.

---

## 3. Per-milestone active time (implement start → merge)

| Milestone | Span | Duration | PR created → merged |
|---|---|---:|---|
| M0 repo bootstrap | 18:25:28.847Z → 18:48:45Z | **23.3 min** | 18:30:35Z → 18:48:45Z (18.2 min) |
| M1 read path | 18:49:32.243Z → 19:44:33Z | **55.0 min** | 19:07:30Z → 19:44:33Z (37.1 min) |
| M2 write path | 19:45:33.771Z → 21:02:44Z | **77.2 min** | 20:09:12Z → 21:02:44Z (53.5 min) |
| **M3 guard** | 21:03:35.968Z → 06:13:00Z | **549.4 min (9.16 h)** | 22:39:33Z → 06:13:00Z (**7.56 h**) |
| M4 init/scaffold | 06:13:57.285Z → 07:38:05Z | **84.1 min** | 07:00:34Z → 07:38:05Z (37.5 min) |
| (docs housekeeping PR #6, between M4/M5) | 07:38:43Z → 07:40:03Z | 1.3 min | — |
| M5 skills+dogfood *(in flight, not yet merged as of last event)* | 07:41:27.974Z → 08:12:33.452Z (last observed) | **31.1 min so far** | 08:02:36Z → *(open)* |

Sum of milestone spans ≈ **13.67 h**, + ~0.24 h of small inter-milestone turnaround
(docs PR, dispatch gaps) = **13.91 h**, reconciling with the total session span.

**M3 alone is 65.9% of total session wall-clock** (9.16 h of 13.91 h), driven almost
entirely by §2's two incidents (5.41 h) plus a second implement attempt (below) — not
by the review pipeline being inherently slower for M3's scope.

M3 also had **two implementer attempts**: `agent-a97b6ae2cfeeb3207` ("Implement M3
guard", 21:03:35.968Z → 22:18:32.312Z, 74.9 min, ends `[Request interrupted by user]`)
followed 95 sec later by `agent-a99da0448005ed70f` ("Implement M3 guard (Opus)",
22:20:07.728Z → real completion 22:41:41.565Z, 21.6 min productive). This same
kill-and-restart-with-Opus pattern recurs for M4 (`adeaf32c...` Opus attempt 1,
06:13:57Z→06:29:53Z, 16.0 min, interrupted; `adef7403...` attempt 2, 06:35:41Z→07:03:00Z,
27.3 min, continues on the same branch). Both restarts are clean handoffs (new agent
picks up on the existing branch within ~2–6 min) and are minor overhead — **not** the
same failure mode as the M3 stale-fix-routing stall in §2a.

---

## 4. What dominates within the pipeline (structural, excluding the two M3 incidents)

Summed across all 6 milestones by pipeline role (M3's implementer counted as its two
attempts' real work only, 96.5 min; M3's quality-review counted as its ~76-min
non-anomalous portion; M4 implementer as both attempts, 43.3 min):

| Stage | Total time | Share of structural time |
|---|---:|---:|
| **Implementer sessions** | ~5.22 h (313 min) | **48%** |
| **Quality-review sessions** | ~2.53 h (152 min) | 23% |
| **Spec-review sessions** | ~1.00 h (60 min) | 9% |
| **Fix rounds** | ~0.58 h (35 min) | 5% |
| **Verify rounds** | ~0.18 h (11 min) | 2% |
| Orchestrator dispatch turnaround between agents | low — mostly 1–6 min per handoff | ~5–8% (residual) |
| **CI wait** | **never the critical path** (see below) | negligible |

**CI is not a bottleneck at any point.** Per PR, CI run durations (`gh run list`) are
consistently 50s–2min per leg (e.g. M0: 2 min, M1: 1m49s, M2: four runs at 1.3–1.9 min
each across fix pushes, M3: 1.2 min and 0.9 min across two runs total, M4: ~1 min ×2,
M5: 1m57s). Compare to PR open-to-merge durations of 18–84 minutes for the healthy
milestones and **7.56 hours for M3** — CI runtime is 1–3% of PR lifetime everywhere, and
agents actively watch it via `gh pr checks --watch` / `gh run watch`, which returns as
soon as CI finishes (confirmed: a `gh pr checks --watch` call in the M1 implementer took
104s, matching that PR's CI run duration of 1m49s almost exactly — no extra polling
overhead).

**Implementer time dominates structurally** (48%), which is expected — it's the stage
that writes code, runs local tests, and iterates. Quality-review is the second-largest
stage per milestone even after excluding the M3 anomaly, confirming code-quality review
is inherently a heavier stage than spec-review (2.5x spec-review's total time) — it does
its own fresh test/fuzz runs rather than trusting the implementer's word (e.g. M2's
reviewer independently probed a parser/patch divergence with hand-written probe tests
before writing its report).

---

## 5. Within an agent session: model latency vs tool execution

Two representative agents, chosen for being "clean" (no incident, no stray messages):

### Implementer — M1 read path (`agent-afd577a52e5c549b8`, 52.2 min, 387 log lines)
- Naive assistant→user / user→assistant gap split: **tool-exec-shaped gaps 28.0 min,
  model-gen-shaped gaps 18.4 min, other 5.9 min.**
- **Important correction after inspecting content**: 24.05 of those 28.0 "tool-exec"
  minutes are a *single* gap (`2026-07-02T19:09:53.557Z → 19:33:56.838Z`) where the
  agent had already posted "Status: DONE" and was simply **idle, waiting for the
  parallel spec-review + quality-review agents to finish** so the coordinator could
  relay consolidated fix findings back to it (`"Both reviews are in... Apply these,
  push, keep CI g[reen]"` arrives at 19:33:56). That is orchestrator/pipeline-wait time,
  not tool execution.
- **Corrected split**: real tool execution (bash builds/tests/`gh pr create`/`git push`)
  ≈ **4 min**; model generation (thinking + text) ≈ **18.4 min**; idle-waiting-on-
  parallel-review ≈ **24 min**; misc ≈ **5.9 min** — of a 52.2-min total span.
- Individually, the largest genuine tool wait was CI: `gh pr checks 2 --watch` took
  104s, matching that PR's actual CI runtime.

### Reviewer — M2 quality review (`agent-aa24d5a7f81a3ec54`, 39.6 min, 131 log lines)
- Naive split: tool-exec-shaped gaps 29.4 min (74%), model-gen-shaped gaps 8.4 min
  (21%), other 1.8 min.
- Inspecting the two largest individual gaps (18.5 min at 20:28:23Z, 10.5 min at
  20:50:10Z) shows both sit around the agent composing a **long structured review report
  as a single turn** (e.g. *"# Code Review — M2..."*, *"# Re-review — fix commits..."*)
  rather than a discrete tool call — i.e., large single-turn model generation, not idle
  tool-wait, even though my coarse role-alternation heuristic bucketed them as
  "tool-exec-shaped." (Caveat: the transcripts don't cleanly separate multi-block
  assistant turns from true tool waits at this level of automated analysis; treat the
  74/21% split as directionally noisy, not exact.)
- Actual tool calls in this session (build/vet/test/probe scripts) each returned in
  well under 15s.

**Bottom line for §5**: per-turn Anthropic API latency (a single model turn, especially
one producing a long structured report, or "thinking") is measured in **single-digit
minutes** at most in the clean cases — the number of sequential turns (dozens per agent,
hundreds across a milestone's pipeline) compounds this into tens of minutes per agent
and hours per milestone. Raw tool execution (bash, go test, git, gh) is consistently
**sub-15-seconds to ~2 minutes**, never a material contributor. The one place per-turn
model latency clearly blew up to abnormal levels (100+ minutes for a single turn) is the
M3 review incident in §2b, which reads as an infrastructure/connectivity anomaly rather
than expected model latency.

---

## 6. Bottom line — ranked latency sources

| # | Source | Est. hours | Category |
|---|---|---:|---|
| 1 | M3 code-quality-review anomaly (125.9-min silent turn + API disconnect + 45.6-min recovery) | **2.86 h** | Incidental / external (API infra) |
| 2 | M3 stale-agent stall (fix findings misrouted to a finished implementer agent, later killed) | **2.55 h** | Incidental (orchestration bug) |
| 3 | Implementer sessions, all milestones combined | **~5.2 h** | Structural (by design — 6 milestones × real coding work) |
| 4 | Quality-review sessions (non-anomalous portion) | **~2.5 h** | Structural (independent re-verification by design) |
| 5 | Spec-review sessions | **~1.0 h** | Structural |
| 6 | Fix rounds (M1-folded-in, M3, M4) | **~0.6 h** | Structural |
| 7 | Verify rounds | **~0.2 h** | Structural |
| 8 | Orchestrator dispatch turnaround / restart overhead (incl. M3+M4's kill-and-redo-with-Opus implementer restarts) | low, ~0.3–0.6 h | Incidental but minor |
| 9 | CI wait | negligible (~2–3 min aggregate agent-observed wait; runs themselves 50s–2min each) | External, never critical path |
| — | User-idle / overnight gaps | ~0 h | None found |

**So: ~5.4 h (39%) of the 13.9-hour run is two distinct incidents that should not recur**
(a misrouted fix message to a stale agent, and an apparent API/connectivity stall inside
one review turn) — both are process/infra bugs, not inherent to the design. The
remaining **~8.5 h (61%) is the structural cost of a strictly-sequential, 6-stage
review pipeline run 6 times** (implement → spec-review → quality-review → fix → verify →
merge), where implementer time (~5.2 h) and quality-review time (~2.5 h) are the two
real structural cost centers, and CI is never the bottleneck.

Against the user's 30–60 minute expectation: even with *zero* incidents, a straight
sum of the non-M3-anomalous milestone spans (M0 23.3 + M1 55.0 + M2 77.2 + M4 84.1 + M5
31.1-so-far ≈ **4.5 h**, and M3's non-incident portion ≈ **3.75 h**, total **≈8.2 h**)
would still be ~8–16x the estimate — the structural sequential-review-per-milestone
design, not just the incidents, is the primary driver of the gap between expectation and
reality. If low latency matters more than review rigor going forward, the highest-
leverage structural changes would be: (a) parallelizing milestones where dependencies
allow, (b) collapsing spec-review + quality-review into one pass for smaller milestones,
and (c) fixing the fix-message routing bug so stale agents can't receive live work.
