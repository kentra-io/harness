# java-hexagonal skill + kentra-skills distribution rail — Implementation Plan

> **EXECUTED 2026-07-19** — all 12 tasks done (subagent-driven; two-stage review per phase +
> user verification gate after skill authoring). See the design doc's STATUS banner for
> resulting SHAs and the three review-driven deviations. Path note: the repo working copy was
> staged at `harness/.claudebox/tmp/kentra-skills` (the plan's sibling path is unwritable in
> the sandbox); canonical home is the pushed repo + harness submodule.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Author a generic single-module `java-hexagonal` skill, extract `kentra-skills` into its own public GitHub repo distributed as a Claude Code plugin via the `kentra-agentic-plugins` catalog, re-add it to `harness/` as a submodule, and record kafka-dq's adoption in its constitution.

**Architecture:** `kentra-skills` becomes a standalone plugin repo (`.claude-plugin/plugin.json` + `skills/<name>/` multi-file skills). The existing `kentra-agentic-plugins` aggregator catalog lists it by GitHub source. Host sessions consume it via the catalog; headless box (cast-agent) consumption is deferred to thread F. kafka-dq references it in a constitution ADR + committed project settings.

**Tech Stack:** Markdown skills, Claude Code plugin/marketplace JSON, git submodules, `gh` CLI, adr-sourced-constitution CLI.

**Source of truth:** `tasks/java-hexagonal-and-skills-rail-design.md` (design + locked decisions).

**Paths:**
- New repo working copy (scratch, pre-push): `/Users/jony/code/kentra/kentra-skills`
- Harness root: `/Users/jony/code/kentra/harness`
- Existing skill to migrate: `harness/kentra-skills/spring-boot-hexagonal/{SKILL.md,single-use-case.md,multi-use-case.md}`
- Catalog: `harness/kentra-agentic-plugins/.claude-plugin/marketplace.json`
- kafka-dq repo root: `harness/kafka-dq` (constitution at `kafka-dq/constitution/`)

---

## Phase 1 — Scaffold the standalone kentra-skills repo

### Task 1: Create the repo skeleton with the migrated Spring skill

**Files:**
- Create dir: `/Users/jony/code/kentra/kentra-skills/`
- Create: `.claude-plugin/plugin.json`, `LICENSE`, `README.md`, `.gitignore`
- Copy: existing `spring-boot-hexagonal/` → `skills/spring-boot-hexagonal/`

- [ ] **Step 1: Make the directory structure and migrate the existing skill**

```bash
mkdir -p /Users/jony/code/kentra/kentra-skills/.claude-plugin
mkdir -p /Users/jony/code/kentra/kentra-skills/skills
cp -R /Users/jony/code/kentra/harness/kentra-skills/spring-boot-hexagonal \
      /Users/jony/code/kentra/kentra-skills/skills/spring-boot-hexagonal
ls -R /Users/jony/code/kentra/kentra-skills/skills/spring-boot-hexagonal
```
Expected: three files listed (SKILL.md, single-use-case.md, multi-use-case.md).

- [ ] **Step 2: Write `.claude-plugin/plugin.json`**

```json
{
  "name": "kentra-skills",
  "description": "Framework-agnostic architecture skills for JVM services: hexagonal (ports-and-adapters) layering, ArchUnit shape enforcement, and a three-tier test strategy (per-class unit, domain-with-fakes, integration with Testcontainers). Ships java-hexagonal (raw JUnit/Gradle/Testcontainers) and spring-boot-hexagonal.",
  "version": "0.1.0",
  "author": { "name": "Kentra" },
  "homepage": "https://github.com/kentra-io/kentra-skills",
  "repository": "https://github.com/kentra-io/kentra-skills",
  "license": "MIT",
  "keywords": ["hexagonal", "ports-and-adapters", "archunit", "testcontainers", "jvm", "java", "spring-boot"]
}
```

- [ ] **Step 3: Write `LICENSE` (MIT)**

Copy the MIT text from an existing primitive to keep the copyright line consistent:
```bash
cp /Users/jony/code/kentra/harness/adr-sourced-constitution/LICENSE \
   /Users/jony/code/kentra/kentra-skills/LICENSE
head -3 /Users/jony/code/kentra/kentra-skills/LICENSE
```
Expected: MIT license header. If the copyright holder line differs from "Kentra", edit to match the other primitives.

- [ ] **Step 4: Write `README.md`**

Short: what the repo is (Kentra's hand-authored architecture skills, distributed as a Claude Code plugin), how it's consumed (via the `kentra-agentic-plugins` catalog, or `/plugin marketplace add ./kentra-skills` for local dev), the skills it ships, and the neutral-mechanism/branded-catalog note. Model the tone on `harness/kentra-agentic-plugins/README.md`.

- [ ] **Step 5: Write `.gitignore`**

```
.DS_Store
```

- [ ] **Step 6: Verify structure, do NOT git init yet**

```bash
find /Users/jony/code/kentra/kentra-skills -type f -not -path '*/.git/*' | sort
```
Expected: `.claude-plugin/plugin.json`, `.gitignore`, `LICENSE`, `README.md`, and the three `skills/spring-boot-hexagonal/*.md` files.

---

## Phase 2 — Author the `java-hexagonal` skill

Author by adapting `skills/spring-boot-hexagonal/` per the de-Spring diffs in the design doc §"Thread A". Framework-neutral, single-module, package-level.

### Task 2: Author `skills/java-hexagonal/SKILL.md`

**Files:**
- Create: `/Users/jony/code/kentra/kentra-skills/skills/java-hexagonal/SKILL.md`

- [ ] **Step 1: Write the skill**

Start from `spring-boot-hexagonal/SKILL.md` and apply these transforms (design §Thread A):
- Frontmatter `name: java-hexagonal`; description drops Spring/MapStruct-Spring, names **raw JUnit + ArchUnit + Gradle + Testcontainers**, keeps the variant-loading behavior.
- **Layer responsibilities:** keep the neutral onion text; drop "no Spring annotations" → "no framework annotations, no I/O". Keep the ArchUnit whitelist mechanism.
- **Composition root:** replace `@Configuration`/`@Bean` with "a plain framework-free class that constructs the graph with constructors (`new`); if a framework is used (Quarkus/Spring), confine it to that root / a thin framework module that delegates to the framework-free wiring."
- **MapStruct → optional section:** adapter-boundary mapping via MapStruct `componentModel=default` + `Mappers.getMapper()`, or hand-written mappers. No DI container assumed. Keep the mapper-unit-test rule but drop the `@Autowired`/`SPRING` specifics.
- **Test strategy tier 3:** replace `@SpringBootTest`/`@ActiveProfiles`/`@DynamicPropertySource`/`@TestConfiguration` with plain JUnit 5 + Testcontainers + Awaitility; static singleton containers; read the mapped port at runtime and pass via constructor/config; framework-free fixture. Add a **portability sub-section** carrying the hard-won lessons: advertised-listener `host:mappedPort` on the standard-daemon path; pin the Docker `api.version`; disable Ryuk under a socket proxy; **"verify on the DoD's real target environment (standard daemon), not only inside a proxied box."**
- **Outbound ports:** generalize — producers/registry clients/repos are all outbound ports; `Repository` suffix reserved for persistence-store ports when a datastore exists.
- **Anti-patterns:** de-Spring (`@SpringBootTest for unit-testable logic` → "framework integration test for logic testable as a plain unit"; "dynamic container ports in YAML" → "hardcoded ports; read the mapped port at runtime").
- Keep the "Load the relevant variant" section pointing at `single-use-case.md` / `multi-use-case.md`.

- [ ] **Step 2: Verify no Spring leakage**

```bash
grep -n -i "spring\|@Configuration\|@Bean\|@Autowired\|@SpringBootTest\|@DynamicPropertySource\|ConfigurationProperties\|componentModel.*SPRING\|@ActiveProfiles" \
  /Users/jony/code/kentra/kentra-skills/skills/java-hexagonal/SKILL.md
```
Expected: no matches, EXCEPT an allowed contrastive mention (e.g. "if a framework like Spring/Quarkus is used"). Any `@Spring*` annotation reference = must be removed.

### Task 3: Author `skills/java-hexagonal/single-use-case.md`

**Files:**
- Create: `/Users/jony/code/kentra/kentra-skills/skills/java-hexagonal/single-use-case.md`

- [ ] **Step 1: Write the variant**

Adapt `spring-boot-hexagonal/single-use-case.md`: keep the package layout (`domain/{model,logic,port/out,service}`, `application/{config,in/<tech>,out/<tech>}`) and the ArchUnit `onionArchitecture()` block (already neutral). Replace the Composition-Root paragraph's `@Configuration AppConfig` with "a plain `App`/`Main` wiring class." Keep the flat integration-fixture layout. Drop the `persistence/` package from the layout unless a datastore exists (make it "optional `out/persistence/` when a store is used").

- [ ] **Step 2: Verify no Spring leakage** (same grep as Task 2 Step 2, on this file). Expected: no matches.

### Task 4: Author `skills/java-hexagonal/multi-use-case.md`

**Files:**
- Create: `/Users/jony/code/kentra/kentra-skills/skills/java-hexagonal/multi-use-case.md`

- [ ] **Step 1: Write the variant**

Adapt `spring-boot-hexagonal/multi-use-case.md`: keep vertical-slice structure, `shared/` guidance, the per-slice ArchUnit loop, nested fixture layout. De-Spring the naming table (drop "Spring bean name" row or rename to "wiring field name, camelCase"), the composition-root section (`AppConfig`/`<UseCase>Config` become plain wiring classes, not `@Configuration`), and replace `ConcurrentKafkaListenerContainerFactory` / `@ConfigurationProperties` guidance with framework-neutral equivalents ("per-use-case consumer config; a config record read at startup").

- [ ] **Step 2: Verify no Spring leakage** (same grep). Expected: no matches.

### Task 5: Sanity-check the skill set and commit Phases 1–2

- [ ] **Step 1: Confirm final file tree**

```bash
find /Users/jony/code/kentra/kentra-skills -type f -not -path '*/.git/*' | sort
```
Expected: plugin.json, LICENSE, README.md, .gitignore, and both skills' three files each (java-hexagonal + spring-boot-hexagonal).

- [ ] **Step 2: git init + first commit** (local only — no remote yet)

```bash
cd /Users/jony/code/kentra/kentra-skills
git init -b main
git add -A
git commit -m "Initial kentra-skills plugin: java-hexagonal + spring-boot-hexagonal

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git log --oneline
```
Expected: one commit.

---

## Phase 3 — Create the public GitHub repo and push

### Task 6: Create `kentra-io/kentra-skills` (public) and push

**Authorization:** user explicitly approved creating the public repo and pushing.

- [ ] **Step 1: Create the remote and push in one step**

```bash
cd /Users/jony/code/kentra/kentra-skills
gh repo create kentra-io/kentra-skills --public \
  --description "Kentra's hand-authored JVM architecture skills (hexagonal/ports-and-adapters), distributed as a Claude Code plugin." \
  --source . --remote origin --push
```
Expected: repo created, `main` pushed.

- [ ] **Step 2: Verify remote**

```bash
gh repo view kentra-io/kentra-skills --json name,visibility,url -q '{name,visibility,url}'
git -C /Users/jony/code/kentra/kentra-skills remote -v
```
Expected: visibility `PUBLIC`; origin points at `kentra-io/kentra-skills`.

---

## Phase 4 — Swap the harness in-tree dir for a submodule

### Task 7: Replace `harness/kentra-skills/` with the submodule

**Files:**
- Modify: `harness/.gitmodules` (submodule add)
- Remove: in-tree `harness/kentra-skills/` (currently staged content)

- [ ] **Step 1: Unstage + remove the in-tree copy**

```bash
cd /Users/jony/code/kentra/harness
git rm -r --cached kentra-skills
rm -rf kentra-skills
git status --porcelain kentra-skills
```
Expected: the previously-staged `kentra-skills/*` files show as deleted; the directory is gone.

- [ ] **Step 2: Add it back as a submodule** (clones the pushed repo)

```bash
cd /Users/jony/code/kentra/harness
git submodule add git@github.com:kentra-io/kentra-skills.git kentra-skills
grep -A3 '"kentra-skills"' .gitmodules
ls kentra-skills/skills
```
Expected: `.gitmodules` has a `kentra-skills` entry; `skills/` lists `java-hexagonal` and `spring-boot-hexagonal`.

- [ ] **Step 3: Commit the submodule addition in harness**

```bash
cd /Users/jony/code/kentra/harness
git add .gitmodules kentra-skills
git commit -m "Extract kentra-skills to its own repo; consume as submodule (ADR-0001)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
Expected: commit records `.gitmodules` + the gitlink.

---

## Phase 5 — List the plugin in the aggregator catalog

### Task 8: Add the kentra-skills entry to `kentra-agentic-plugins`

**Files:**
- Modify: `harness/kentra-agentic-plugins/.claude-plugin/marketplace.json`

- [ ] **Step 1: Append the plugin entry to the `plugins` array**

Add after the existing `adr-sourced-constitution` object:
```json
{
  "name": "kentra-skills",
  "source": { "source": "github", "repo": "kentra-io/kentra-skills" },
  "skills": ["./skills/java-hexagonal", "./skills/spring-boot-hexagonal"],
  "description": "Framework-agnostic JVM architecture skills: hexagonal layering, ArchUnit shape enforcement, and a three-tier test strategy (unit / domain-with-fakes / Testcontainers). Ships java-hexagonal and spring-boot-hexagonal.",
  "license": "MIT",
  "category": "architecture",
  "keywords": ["hexagonal", "ports-and-adapters", "archunit", "testcontainers", "jvm"]
}
```

- [ ] **Step 2: Validate JSON**

```bash
cd /Users/jony/code/kentra/harness/kentra-agentic-plugins
python3 -m json.tool .claude-plugin/marketplace.json > /dev/null && echo "JSON OK"
```
Expected: `JSON OK`.

- [ ] **Step 3: Commit + push the catalog submodule, then bump the pointer in harness**

```bash
cd /Users/jony/code/kentra/harness/kentra-agentic-plugins
git add .claude-plugin/marketplace.json
git commit -m "Add kentra-skills plugin to the catalog

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git push origin HEAD
cd /Users/jony/code/kentra/harness
git add kentra-agentic-plugins
git commit -m "Bump kentra-agentic-plugins: list kentra-skills plugin

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
Expected: catalog change pushed; harness records the new submodule commit.

---

## Phase 6 — Verify host consumption

### Task 9: Confirm the plugin resolves and java-hexagonal loads

- [ ] **Step 1: List the marketplace via the catalog (local submodule path — no network)**

```bash
claude plugin marketplace add /Users/jony/code/kentra/harness/kentra-agentic-plugins 2>&1 | tail -5 || \
  echo "NOTE: if 'claude plugin' CLI unavailable/interactive, verify via /plugin in an interactive session"
```
Expected: marketplace registered listing `adr-sourced-constitution` and `kentra-skills`.

- [ ] **Step 2: Install + verify skill discovery** (may require an interactive session)

Install `kentra-skills@kentra-agentic-plugins`, then confirm `java-hexagonal` appears as an invocable skill (namespaced `kentra-skills:java-hexagonal`). If the CLI path is interactive-only, note that the user verifies this in an interactive Claude Code session; record the outcome here.

Expected: `kentra-skills:java-hexagonal` invocable; loading it prints the SKILL.md and offers the two variants.

---

## Phase 7 — Record kafka-dq's adoption (LAST — after the skill + repo are built)

### Task 10: Add the constitution ADR to kafka-dq

**Files:**
- Modify (via CLI/log, never the projection by hand): `kafka-dq/constitution/adr/` (+ regenerated `constitution.md`)

**Branch note:** the constitution is repo-wide, independent of the held `001-e2e-poc` change. Add this on kafka-dq's mainline (or a small dedicated branch), NOT inside the `001-e2e-poc` worktree/change that's held for the retro. Confirm the target branch before committing.

- [ ] **Step 1: Draft the ADR via the constitution mechanism**

Use the adr-draft / `constitution` CLI to add an ADR to the log (never hand-edit `constitution.md`). Content:
> **Title:** Single-module package-level hexagonal architecture; java-hexagonal skill authoritative
> **Decision:** kafka-dq follows a single-module, package-level hexagonal (ports-and-adapters) structure. The `java-hexagonal` skill (from the `kentra-skills` plugin) is authoritative for package layout, layer responsibilities, ArchUnit shape, and the three-tier test strategy. The current 9-module split is superseded and to be refactored toward single-module (see harness thread F).

- [ ] **Step 2: Regenerate + verify the projection**

Run the constitution regen; confirm the new ADR appears in `constitution.md` with the next sequential ADR id and a date. Verify replay/guard is clean.
Expected: projection includes the new rule; guard passes.

- [ ] **Step 3: Commit** (on the confirmed kafka-dq branch)

```bash
# in kafka-dq, on the confirmed branch
git add constitution/
git commit -m "Constitution: adopt single-module java-hexagonal; skill authoritative

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 11: Add the committed marketplace/plugin reference to kafka-dq

**Files:**
- Create/Modify: `kafka-dq/.claude/settings.json`

- [ ] **Step 1: Declare the marketplace + enable the plugin**

Merge into `kafka-dq/.claude/settings.json` (create if absent):
```json
{
  "extraKnownMarketplaces": {
    "kentra-agentic-plugins": {
      "source": { "source": "github", "repo": "kentra-io/kentra-agentic-plugins" }
    }
  },
  "enabledPlugins": {
    "kentra-skills@kentra-agentic-plugins": true
  }
}
```
Note: headless box runs will NOT auto-install this (thread F wires seeding); this serves host/interactive sessions. Record that caveat in a comment in the design doc, not the JSON (JSON has no comments).

- [ ] **Step 2: Validate JSON + commit**

```bash
python3 -m json.tool /Users/jony/code/kentra/harness/kafka-dq/.claude/settings.json > /dev/null && echo "JSON OK"
```
Then commit on the same kafka-dq branch as Task 10.
Expected: `JSON OK`; committed.

---

## Phase 8 — Close out the harness notes

### Task 12: Mark the skills-organization question resolved + update links

**Files:**
- Modify: `harness/tasks/custom-skills-organization.md`
- Modify: `harness/tasks/retro-idea-testcontainers-skill.md`
- Modify: `harness/tasks/java-hexagonal-and-skills-rail-design.md`

- [ ] **Step 1: Resolve custom-skills-organization.md**

Add a top RESOLVED banner: home decided = own repo `kentra-io/kentra-skills` (branded, MIT) + harness submodule + listed in the `kentra-agentic-plugins` catalog. Box (headless) consumption via plugin-cache seeding is the remaining open piece → thread F.

- [ ] **Step 2: Cross-link retro-idea-testcontainers-skill.md**

Note that the architecture-level Testcontainers guidance (advertised-listener/api.version/verify-on-real-target) now lives in `java-hexagonal` SKILL.md tier 3; a standalone deep `testcontainers-java` skill remains optional/future.

- [ ] **Step 3: Update the design doc status** to "implemented" with the resulting commit SHAs.

- [ ] **Step 4: Commit the notes in harness**

```bash
cd /Users/jony/code/kentra/harness
git add tasks/custom-skills-organization.md tasks/retro-idea-testcontainers-skill.md tasks/java-hexagonal-and-skills-rail-design.md tasks/java-hexagonal-and-skills-rail-plan.md tasks/kafka-dq-restructure-single-module.md
git commit -m "Notes: resolve custom-skills-organization; capture thread F + skills-rail design/plan

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-review notes (coverage vs design)

- Thread A (author java-hexagonal, single-module, de-Sprung, Testcontainers lessons) → Tasks 2–4. ✓
- Thread B repo/submodule (own repo, MIT, public, submodule) → Tasks 1, 5, 6, 7. ✓
- Thread B catalog entry (aggregator) → Task 8. ✓
- Thread B host consumption → Task 9. ✓
- Thread B constitution reference + kafka-dq settings (last) → Tasks 10, 11. ✓
- Notes/close-out (resolve custom-skills-organization, retro link) → Task 12. ✓
- Explicitly out of scope: box-seeding (thread F), threads C/D/E. ✓

## Known execution caveats

- **Interactive plugin steps (Phase 6, Task 11):** `claude plugin` install + the project-settings trust dialog may be interactive-only; where headless verification isn't possible, the user confirms in an interactive session and the outcome is recorded.
- **kafka-dq branch (Tasks 10–11):** the constitution + settings changes must NOT land inside the held `001-e2e-poc` change/worktree; confirm the target branch first.
- **`gh` auth:** Task 6 needs `gh` authenticated for `kentra-io`. If it fails, the user runs `! gh auth status` / `! gh auth login`.
