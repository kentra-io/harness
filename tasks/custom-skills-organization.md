# TODO: architect how we organize custom-authored skills

> **RESOLVED 2026-07-19.** Home decided and shipped: hand-authored skills live in their own
> public repo **`kentra-io/kentra-skills`** (branded name, MIT), consumed by the harness as a
> **submodule** (harness `adbbd12`) and distributed as a **Claude Code plugin listed in the
> `kentra-agentic-plugins` aggregator catalog** (catalog `708a68e`). Ships `java-hexagonal`
> (framework-neutral) + `spring-boot-hexagonal` (initial commit `c938925`). Host consumption
> verified headless (marketplace add + plugin install from the public repo). kafka-dq records
> adoption via constitution ADR-0011 + committed `.claude/settings.json` (kafka-dq `a810ac4`,
> `400bf3e`). Remaining open piece: **box (headless cast-agent) consumption via plugin-cache
> seeding → thread F** (`tasks/kafka-dq-restructure-single-module.md` prerequisites). Design +
> plan: `tasks/java-hexagonal-and-skills-rail-{design,plan}.md`.

**Status:** captured 2026-07-10, idea only. Raised by the user after adding the
first hand-authored skill.

## What exists now (the stopgap)

- New folder `kentra-skills/` in the harness holds hand-authored skills.
- First skill: `spring-boot-hexagonal/` (SKILL.md + `single-use-case.md` /
  `multi-use-case.md` variant sub-files — a real, multi-file skill, not a one-liner).
- **For now these get copied around** (into `.claude/skills/`, into claudebox,
  wherever they're needed). That's the explicit interim; it doesn't scale — copies
  drift, there's no single source of truth, and no versioning.

## Why this needs proper architecture

We already have strong conventions for *reusable primitives* and for *distribution*
that this should probably slot into rather than invent fresh:

- **Primitives as submodules** (AGENTS.md / constitution ADR-0001): every
  standalone reusable thing lives in its own repo, consumed via submodule — not
  absorbed into a harness-internal dir. `kentra-skills/` currently violates that.
- **Plugin distribution** (memory `plugin-distribution`): primitives ship as
  Claude Code plugins via the branded aggregator catalog
  `kentra-io/kentra-agentic-plugins` (public/MIT submodule); neutral per-primitive
  `plugin.json`, catalog lists by repo. Skills bundle *inside* plugins — so a
  custom-skills home likely wants to ride this same rail.
- **Shared `~/.claude`** (memory `claudebox-shared-claude-config`): host `~/.claude`
  is symlink-mounted into all claudeboxes, so skill discovery already works
  container-wide once a skill lands in the right place. That changes what "install"
  even means here.

## Options to evaluate when we pick this up

- **Own repo + submodule** (e.g. `kentra-skills` as a standalone MIT repo), consumed
  by the harness and by claudebox the same way other primitives are. Matches the
  ADR; makes it versionable and reusable beyond this repo.
- **Ship as a plugin** via the `kentra-agentic-plugins` catalog so skills install
  through the existing plugin mechanism instead of manual copying. Possibly the
  same thing as above (repo = plugin).
- **Branded vs neutral cut.** Some custom skills are kentra-methodology-specific
  (branded), some are framework-neutral and reusable (e.g. `spring-boot-hexagonal`
  is arguably neutral — generic Spring Boot hex architecture). Decide whether
  they split across a neutral repo and a branded one, mirroring the
  "neutral mechanism, branded methodology" split (ADR-0002).
- **Discovery/install path in claudebox.** Given the shared `~/.claude` mount,
  decide the canonical location skills live so every box (and Mode-B cast agent)
  sees them without copying.

## Open questions

- Is `spring-boot-hexagonal` **neutral** (belongs in a reusable skills repo) or
  **branded** (kentra-opinionated)? Its content looks neutral — that decision sets
  the license and the repo it lands in.
- One skills repo, or per-domain repos? (A single `kentra-skills` repo is simpler;
  per-primitive skills may instead belong *with* their primitive.)
- Relationship to the existing per-primitive `skills/` dirs (adr-sourced-constitution,
  spec-lifecycle already ship agent-agnostic skills in-repo). Are hand-authored
  general skills a *different* category, or the same rail?

Until this is architected, keep copies to a minimum and note where each one lives.
