# Design: `java-hexagonal` skill + the `kentra-skills` distribution rail (threads A + B)

**Status: IMPLEMENTED 2026-07-19** (subagent-driven, per `java-hexagonal-and-skills-rail-plan.md`).
Resulting commits: `kentra-io/kentra-skills@c938925` (initial, both skills), harness `adbbd12`
(submodule swap) + `94fcadd` (catalog pointer bump), `kentra-agentic-plugins@708a68e` (catalog
entry, pushed), kafka-dq main `a810ac4` (constitution ADR-0011) + `400bf3e` (settings.json).
Host consumption verified headless (marketplace add + install from the public repo).
Notable deviations from this design, all review-driven: (1) a bare plugin repo is NOT a
marketplace — the "§Thread B `/plugin marketplace add ./kentra-skills`" local-dev path was wrong;
local dev registers the aggregator catalog instead (single-rail decision). (2) The ArchUnit
`applicationServices` layer was widened to `"<root>.application.."` + a containment check in
BOTH skills after an empirical probe showed domain→unclassified-application-subpackage deps
passed silently. (3) The tier-3 `api.version` guidance teaches the version-window mechanism
instead of a bare pin; TC 1.20.4→2.0.5 verified viable on kafka-dq (queued → thread F).
Box/cast-agent seeding remains deferred to thread F, as designed.

Original header: design, ready for review. Authored 2026-07-18 during the skills/design-process
working session. Covers threads **A** (author `java-hexagonal`) and **B** (make it reach
consumers via a real distribution rail + record adoption in the constitution). Threads C
(constitution-init discusses project structure), D (technical design as a real process), E
(transparency / agent system prompts), and F (refactor kafka-dq to single-module) are **out
of scope here** — captured separately.

## Problem

1. A hand-authored `spring-boot-hexagonal` skill exists in `harness/kentra-skills/` but was
   **never on any discovery path** the `001-e2e-poc` cast agents could see — it sat in the
   harness and was copied nowhere. The agents built all 7 milestones without it. (It's also
   Spring-specific; kafka-dq is Quarkus + raw Kafka/Avro, so it wouldn't have fit as-is.)
2. There is **no distribution rail** for hand-authored skills. `lifecycle`/`constitution`
   skills reach a repo via a Go `init` fan-out; `kentra-skills/` has nothing — it's copied by
   hand (`tasks/custom-skills-organization.md`), which drifts and doesn't scale.
3. The user wants a **generic, non-Spring** hexagonal skill (raw JUnit + ArchUnit + Gradle +
   Testcontainers), available whenever working on kafka-dq, **referenced in the constitution**.

## Decisions locked (this session)

| Decision | Choice | Rationale |
|---|---|---|
| Physical shape of `java-hexagonal` | **Single-module, package-level** onion | User's call; the 9-module kafka-dq split was over-engineering (→ thread F) |
| Relationship to kafka-dq | 9 modules was **unwanted**; skill encodes the target shape | Feeds thread F (kafka-dq restructure) + the retro's design-process lesson |
| `kentra-skills` home | **Own independent git repo**, added to `harness/` as a **submodule** | ADR-0001 (standalone primitives as submodules) |
| Repo brand | **Branded `kentra-skills`** (kentra name on the repo) | User's call (over neutral-named). License defaults to MIT — see Open items |
| Distribution | **Listed in the `kentra-agentic-plugins` aggregator catalog** as a plugin | Matches the established plugin-distribution convention |
| Cast-agent (box) consumption | **Deferred to thread F** | Headless boxes can't auto-install marketplace plugins (see Constraint); kafka-dq won't re-run orchestration until post-retro |

## Hard constraint discovered (drives the staging)

Confirmed via Claude Code plugin docs: **project-committed `.claude/settings.json`
(`extraKnownMarketplaces` + `enabledPlugins`) works for interactive host sessions but a
headless `claude -p` run does NOT auto-install** an uninstalled marketplace plugin — in a
fresh/empty box `~/.claude` the plugin silently fails to load. Reaching the Mode-B cast agents
(which run headless with a materialized, empty `~/.claude`) requires **pre-seeding**
(`CLAUDE_CODE_PLUGIN_SEED_DIR` / a pre-populated plugin cache wired into `materialize_box`) or
copying skills into the box's `~/.claude/skills/`. Since kafka-dq's orchestration is complete
and won't re-run until after the retro + thread-F restructure, **the host needs the skill now;
the box does not** — so box-seeding is deferred to thread F as a hard prerequisite for the next
orchestration run.

## Thread A — the `java-hexagonal` skill

Three files mirroring the Spring skill's shape, single-module, framework-neutral:
`skills/java-hexagonal/{SKILL.md, single-use-case.md, multi-use-case.md}`.

**Kept (already neutral):** inward dependency rule; domain purity + ArchUnit whitelist; the
single-vs-multi *use-case* variant split; the three-tier test philosophy; `onionArchitecture()`.

**De-Sprung — specific diffs:**
1. **Composition root:** `@Configuration`/`@Bean` → one plain framework-free class wiring the
   graph with constructors (`new`). If a framework is used, confine it to the root / one thin
   framework module that delegates to the framework-free wiring (kafka-dq's
   `KafkaDqApplication`-behind-Quarkus pattern; this also settles thread F's "one module vs
   one-domain-module-plus-a-framework-module" question).
2. **MapStruct → optional, not mandated:** drop `componentModel=SPRING`. Adapter-boundary
   mapping via MapStruct `componentModel=default` + `Mappers.getMapper()`, or plain hand-written
   mappers. No DI container assumed.
3. **Test tier 3:** `@SpringBootTest`/`@ActiveProfiles`/`@DynamicPropertySource`/
   `@TestConfiguration` → plain JUnit 5 + Testcontainers + Awaitility. Static singleton
   containers; read the mapped port at runtime and pass it via constructor/config (not
   `@DynamicPropertySource`); a framework-free fixture (kafka-dq's `E2eEnvironment`).
4. **Tier 3 absorbs the hard-won portability lessons** — advertised-listener must be
   `host:mappedPort` on the standard-daemon path; pin `api.version`; disable Ryuk under a socket
   proxy; **"verify on the DoD's real target (standard daemon), not only the box."** This is the
   concrete answer to `tasks/retro-idea-testcontainers-skill.md`: the knowledge that would have
   prevented box-only-green lives here. (A standalone deep `testcontainers-java` skill can still
   come later; this covers the architecture-level need.)
5. **Outbound ports generalized:** Kafka producers, registry clients, and DB repos are all
   outbound ports; the `Repository` suffix is reserved for persistence-store ports *when a
   datastore exists* — conditional, not universal.
6. **Anti-patterns + variant files de-Sprung:** `@SpringBootTest for unit-testable logic` →
   "framework integration test for logic testable as a plain unit"; "dynamic container ports in
   YAML" → "hardcoded ports; read the mapped port"; bean naming /
   `ConcurrentKafkaListenerContainerFactory` / `@ConfigurationProperties` / `schema.sql` → plain
   consumer config, a config record read at startup, DDL only if a datastore exists.

**Accepted tradeoff:** `java-hexagonal` and `spring-boot-hexagonal` share ~half their prose. For
skills that's fine — loaded standalone at runtime, self-contained beats DRY-with-indirection.
The Spring skill stays untouched for Spring projects. Both move into the new `kentra-skills` repo.

## Thread B — the distribution rail

**1. `kentra-skills` becomes an independent plugin repo** (`kentra-io/kentra-skills`):
```
kentra-skills/
├── .claude-plugin/
│   └── plugin.json           # name: kentra-skills, MIT, keywords, repo/homepage
├── skills/
│   ├── java-hexagonal/{SKILL.md, single-use-case.md, multi-use-case.md}
│   └── spring-boot-hexagonal/{SKILL.md, single-use-case.md, multi-use-case.md}
├── LICENSE (MIT)
└── README.md
```
Added back to `harness/` as a git submodule (replacing the current in-tree `kentra-skills/`).

**2. Aggregator catalog entry** — append to `kentra-agentic-plugins/.claude-plugin/marketplace.json`:
```json
{
  "name": "kentra-skills",
  "source": { "source": "github", "repo": "kentra-io/kentra-skills" },
  "skills": ["./skills/java-hexagonal", "./skills/spring-boot-hexagonal"],
  "description": "Framework-agnostic architecture skills (hexagonal/ports-and-adapters) for JVM services.",
  "license": "MIT",
  "category": "architecture",
  "keywords": ["hexagonal", "ports-and-adapters", "archunit", "testcontainers", "jvm"]
}
```

**3. Host consumption now:**
- Dev/host: register the marketplace by **local submodule path** (`/plugin marketplace add
  ./kentra-skills`) — no network — and enable the plugin; OR
- Committed project settings: kafka-dq's `.claude/settings.json` declares the aggregator
  marketplace + `kentra-skills@kentra-agentic-plugins` enabled (interactive trust on first open).

**4. Constitution reference (kafka-dq):** a new ADR added via the ADR log (never hand-editing
the projection): *"kafka-dq follows single-module package-level hexagonal architecture; the
`java-hexagonal` skill is authoritative for structure, layering, and test strategy."* This is
the "part of the constitution" ask, and it records the thread-F single-module intent.

**5. Box-seeding (deferred → thread F):** wire `CLAUDE_CODE_PLUGIN_SEED_DIR` into
`agent-orchestration`'s `materialize_box` so headless cast agents receive the plugin cache. Hard
prerequisite before the next orchestration run. Converges with the known "materialized
`skills/` is empty / channel-1 seeding deferred to `agentdef compile`" gap.

## Change inventory (what this thread will touch)

- **New repo `kentra-io/kentra-skills`** — plugin layout, both skills, LICENSE, README.
- **`harness/`** — remove in-tree `kentra-skills/`, add it back as a submodule (`.gitmodules`).
- **`kentra-agentic-plugins`** (submodule) — add the catalog entry; push.
- **`kafka-dq`** — constitution ADR (adopt java-hexagonal + single-module intent);
  `.claude/settings.json` marketplace + plugin enablement (host consumption).
- **`harness/tasks/`** — update `custom-skills-organization.md` (resolved: own repo + submodule
  + aggregator entry) and this doc; the `retro-idea-testcontainers-skill.md` link (folded into
  java-hexagonal tier 3).

## Out of scope (captured elsewhere)

- **Thread F** — refactor kafka-dq 9-module → single-module: `tasks/kafka-dq-restructure-single-module.md`.
- **Thread C** — constitution-init must discuss project structure (adr-sourced-constitution skill).
- **Thread D** — technical design as a real top-down, feedback-seeking process (spec-lifecycle).
- **Thread E** — transparency / agent system prompts (deferred by the user).
- **Box-seeding implementation** — part of thread F's prerequisites.

## Open items — RESOLVED (2026-07-18)

- **License = MIT.** ✓ Confirmed.
- **Repo name = `kentra-io/kentra-skills`, PUBLIC.** ✓ Confirmed.
- **GitHub remote:** create the public repo (`gh repo create`) and **push once the code is
  done**. ✓ Confirmed.
- **kafka-dq reference:** add the skills-repo reference to kafka-dq (committed
  `.claude/settings.json` marketplace/plugin declaration + the constitution ADR) **at the end,
  after the skill + repo are built.** ✓ Confirmed — so the kafka-dq reference IS in scope for
  this thread, sequenced last (not deferred to thread F). Box-seeding for headless cast agents
  remains thread-F work; the committed kafka-dq settings serve host/interactive consumption.
