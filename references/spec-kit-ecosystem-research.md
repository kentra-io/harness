# Spec-Kit Ecosystem Research — SDD Tools vs. Our Target System

*Generated: 2026-06-16 via the `/deep-research` workflow (6 search angles, 23 sources fetched, 114 claims extracted, top 25 adversarially verified → 23 confirmed / 2 refuted, ~106 subagent calls). Companion to [library-analysis.md](./library-analysis.md) and [../planning.md](../planning.md).*

> **Question.** Find Spec-Kit (github/spec-kit) extensions, forks, plugins, or alternative spec-driven-development (SDD) tools that more closely match our target planning system, scored against its **four defining features**:
> 1. **Issue-as-folder, separated staged artifacts** — each issue → one version-controlled folder with distinct functional + non-functional requirements, a separate technical-design doc, and a milestone plan carrying validation contracts; fresh-context staged handoffs with human approval gates.
> 2. **Constitution as a governed *set*** — principles + logical architecture + ADRs (one decision per file), with deviation detection citing a *specific* principle/ADR and a human-approved amendment workflow.
> 3. **Drift / spec-rot detection** — compares code/docs against the constitution/specs, including a continuous/background mode that *files issues*.
> 4. **Living / accumulating system spec** — delta + archive model so docs stay a living source of truth.

---

## Verdict

**No single tool covers all four features — and no combination fully covers features 2 and 3.** The two genuinely novel pieces of our design are confirmed gaps in the entire ecosystem:

- a **governed multi-document constitution with deviation-flagging-by-specific-ADR and a human amendment gate**, and
- a **continuous/background drift scanner that auto-files issues**.

Every drift tool found is **on-demand/command-driven**; every constitution tool is either a single file or a passive ADR store. **These two pieces we build ourselves.**

**Closest practical combination:** Spec Kitty (feature 1) + OpenSpec delta/archive *model* (feature 4) + Mneme HQ ADR-compiled governance (partial feature 2) + spec-kit-architecture-guard / spec-kit-sync (partial feature 3).

---

## Feature-by-feature scorecard

| Target feature | Best existing match | Status |
|---|---|---|
| **1. Issue-as-folder + separated artifacts + human gates** | **Spec Kitty**; also **OpenSpec** | ✅ Covered (but neither splits func vs NFR; both bring lifecycle/worktree machinery that collides with Fabro) |
| **2. Governed multi-doc constitution + amendment gate** | ADR substrate: **adr-tools / Log4brains**; enforcement: **Mneme HQ** | ⚠️ Partial — **multi-doc constitution + amendment gate exist in NO tool** |
| **3. Drift detection + continuous-scan-and-file** | **spec-kit-architecture-guard / spec-kit-sync / docguard / reconcile** | ⚠️ Partial — all on-demand; **continuous background auto-filing exists in NO tool** |
| **4. Living / accumulating system spec** | **OpenSpec** (delta + archive) | ✅ Covered (canonical implementation) |

---

## Candidates

### Spec Kitty — `Priivacy-ai/spec-kitty`
- MIT · ~1.3k⭐ (1333) · Python CLI · agent-agnostic fork of github/spec-kit · v3.2.0 (2026-06-15), 192 releases, **pushed same-day (actively maintained)**.
- **Covers feature 1 strongly:** repository-native mission artifacts under `kitty-specs/`; lifecycle lanes (`planned, in_progress, for_review, approved, done`); isolated git worktrees under `.worktrees/`; workflow `spec → plan → tasks → next → review → accept → merge`, human-in-the-loop by default with `/spec-kitty.review/.accept/.merge`.
- **Lacks:** governed multi-doc constitution (2), living/accumulating spec (4). A claimed CI drift-check (3) was **refuted (1-2)** — it's scoped to an identity-boundary contract, not constitution-vs-code.
- **Why not adopt:** its worktree isolation + lifecycle lanes + review/accept/merge gates duplicate what forked-Fabro + the claudebox executor already own → two systems fighting over orchestration. **Reference for artifact structure, not a dependency.**

### OpenSpec — `Fission-AI/OpenSpec`
- Agent-agnostic (20–30 assistants via slash commands; *not* Claude-Code-native).
- **Canonical implementation of feature 4:** each change lives in `openspec/changes/<name>/` with separated artifacts — `proposal.md` (rationale), `specs/` (requirements + scenarios), `design.md` (technical approach), `tasks.md` (checklist); managed by `/opsx:propose`, `/opsx:apply`, `/opsx:archive`. On completion the change is moved to a **dated archive** folder and its **deltas merge into the living `specs/`**. This is the delta+archive model our feature 4 cites.
- **Covers feature 1's shape** too. **Lacks** governed-set constitution (2) and drift detection (3).
- **Adopted as the *model*** for our spec-folder + living-spec lifecycle (not as a code dependency — we author our own func/NFR/design/plan split).

### Drift-detection extensions (feature 3 — all on-demand)
- **`DyanGalih/spec-kit-architecture-guard`** — 18⭐, Shell/PowerShell, MIT, v1.8.17 (2026-06-05): "continuous architecture governance … validation against architecture constitutions, drift detection, refactor-task generation." Closest to continuous governance (maps to 2+3).
- **`raccioly/docguard`** — 18⭐, JS, v0.26.0 (2026-06-10): enforcement for Canonical-Driven Development; generates `DRIFT-LOG.md`; 24 validators incl. drift checks.
- **`bgervin/spec-kit-sync`** — ~20⭐, MIT: `/speckit.sync.analyze` finds drifted requirements, unspecced features, spec/design conflicts — **command-driven, no daemon/watch**.
- **`stn1slv/spec-kit-reconcile`** — 14⭐, MIT: reconciles drift but **strictly manual** (needs a human-supplied gap report).
- **None** documents a continuous background scanner that **auto-files issues** — that exact sub-requirement of feature 3 is **unmet by all**.

### Constitution / ADR governance (feature 2)
- **Mneme HQ** (`mnemehq.com`) — `mneme-hq` PyPI v0.4.0 (2026-05-27), MIT, active. Compiles `docs/adr/` (one decision per file) into **enforceable, precedence-aware constraints**; install adds a **Claude Code PreToolUse hook** so every Edit/Write is checked against `.mneme/project_memory.json` in strict mode — catching violations *at generation time*, before the PR. Precedence = supersession chains > priority > date recency. **Delivers generation-time deviation prevention (partial feature 2).** A claim it provides a multi-doc constitution / amendment workflow / background scanning was **refuted (1-2)** — it's a precedence engine, not the full governed set. → **Adopted as the *pattern*** for our code-time constitution check.
- **`npryce/adr-tools`** (stable v3.0.0) — CLI managing ADRs as one-decision-per-file numbered Markdown in `doc/adr`; `init/new/supersede/link` only. Passive substrate.
- **`thomvaill/log4brains`** (v1.1.0, Dec 2024, "very long pause") — one-decision-per-file ADRs, slug-based `YYYYMMDD` ids (no merge conflicts) — ideal addressable-document structure, but a passive docs-as-code publication tool; ADRs immutable, zero AI/drift/governance.
- **`me2resh/agent-decision-record` (AgDR)** — extends ADR for AI agents (`docs/agdr/AgDR-0001-….md`, unique ids); no drift detection, governance, living specs, or SDD workflow (negative claim passed 2-1).
- → **adr-tools / Log4brains adopted as the one-file-per-decision *convention*** for our ADR substrate.

### Baseline & academic context
- **Vanilla Spec-Kit's constitution is a SINGLE file** loaded on-demand — maintainer-confirmed (discussion #2476: `.specify/memory/constitution.md`). Confirms the governed *set* is a genuine gap.
- **CSDD — "Constitutional Spec-Driven Development"** (arXiv:2602.02584v1, Jan 2026): proposes a structured multi-attribute constitution (per-principle identifier, CWE ref, MUST/SHOULD/MAY, constraint, pattern, rationale) built *on top of* github/spec-kit via native `/speckit.*` commands. But still a **single versioned security-focused document**, not a governed set of addressable docs + ADRs.

---

## Refuted claims (excluded from findings)

1. *Spec Kitty has a required-CI drift detector covering feature 3* — **1-2**. Its drift check is scoped to an identity-boundary contract, not full constitution-vs-code.
2. *Mneme provides a multi-document constitution / amendment workflow / continuous background scanning* — **1-2**. It's a precedence engine with generation-time enforcement only.

---

## Open questions (from the research)

- Does **any** tool combine a continuous/background drift scanner (CI-scheduled or watch-mode) that **auto-files issues**? (None found — all on-demand.)
- Is there a tool with the **amendment-approval workflow** (human gate before constitution/ADR changes, deviation flags citing a specific ADR id)? (None confirmed.)
- How well do these tools **compose**? Spec Kitty (`kitty-specs/`), OpenSpec (`openspec/changes/`), Mneme (`.mneme/` + `docs/adr/`), architecture-guard each define separate layouts — integration friction unassessed.
- Curated **awesome-spec-kit** lists / official plugin marketplace beyond `github.com/topics/spec-kit`?

---

## Caveats

Star counts, versions, and "actively maintained" status accurate as of **2026-06-16** and will drift. Several drift extensions are early-maturity/small (14–20⭐, few commits) — feature-relevant, not production-proven. Mneme HQ and CSDD descriptions derive partly from vendor site / arXiv (self-described mechanism, not independently benchmarked). The AgDR negative claim passed only 2-1 and its supporting "quote" was a synthesized characterization (underlying absence corroborated by two README reads).

## Key sources
- `github.com/Priivacy-ai/spec-kitty` · `github.com/Fission-AI/OpenSpec` · `mnemehq.com` · `github.com/DyanGalih/spec-kit-architecture-guard` · `github.com/bgervin/spec-kit-sync` · `github.com/stn1slv/spec-kit-reconcile` · `github.com/raccioly/docguard` · `github.com/npryce/adr-tools` · `github.com/thomvaill/log4brains` · `github.com/me2resh/agent-decision-record` · `github.com/github/spec-kit/discussions/2476` · `arxiv.org/html/2602.02584v1`
