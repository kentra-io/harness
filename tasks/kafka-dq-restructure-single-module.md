# Thread F (future): refactor kafka-dq from 9 Gradle modules → single-module package-level hexagon

**Status:** captured 2026-07-18, decision made, work deferred. Surfaced during the
skills/design-process working session (A+B thread) after the user chose a
**single-module package-level** shape for the new `java-hexagonal` skill and
confirmed the **9-module split was over-engineering** — an unwanted assumption
introduced during the `001-e2e-poc` design gate, not something the user asked for.

## The decision

- The single-module package-level onion (`domain/{model,logic,port/out,service}`
  + `application/{config,in/<tech>,out/<tech>}`, one Gradle module, ArchUnit
  `onionArchitecture()` enforcing layers across packages) is the shape the user
  actually wanted for kafka-dq.
- kafka-dq's current 9-module layout (`core`, `engine-cel`, `syntax-confluent`,
  `format-avro`, `adapter-kafka`, `adapter-registry`, `schemas`, `app`, `arch-test`)
  is to be treated as over-engineered and refactored toward the single-module shape.
- The `java-hexagonal` skill (thread A) encodes the target shape; referencing it in
  kafka-dq's constitution (thread B) records the intent.

## Why deferred, not now

- The change under way (`001-e2e-poc`) is implemented + verified + committed but
  **NOT archived** — held for the retro. Restructuring the module layout now would
  churn the exact tree the retro is meant to review.
- This is a large mechanical change (Gradle module collapse + package moves +
  ArchUnit rewrite from module-boundary proof to package-level onion + import
  fixups) that deserves its own spec-lifecycle change once the retro closes.

## Ties into

- The retro (`tasks/retro-archive/001-e2e-poc/`) — the "9 modules was
  over-engineering" finding is a **design-process** lesson: the technical-design
  gate accepted a structural assumption the user didn't want. Feeds thread D
  (technical design as a real, top-down, feedback-seeking process).
- `java-hexagonal` skill (thread A) — the target shape.
- Constitution reference to the skills home (thread B) — locks the intent.

## Queued into this thread (verified 2026-07-19)

- **Upgrade Testcontainers 1.20.4 → 2.0.5 and delete the `api.version` pin.** Verified
  empirically in-box (throwaway bump on `app/build.gradle.kts`, fully reverted): 138/138
  tests green through the socket proxy with the pin removed; wire logs show `/v1.44/...`
  throughout. Mechanics: only artifact renames (`junit-jupiter` → `testcontainers-junit-jupiter`,
  `kafka` → `testcontainers-kafka`; core `testcontainers` unchanged), zero source changes —
  kafka-dq only uses `GenericContainer`/`DockerClientFactory`/`Wait`/`Transferable`/
  `DockerImageName` + `@Testcontainers`, none relocated. Note: 2.x does NOT truly negotiate —
  it hardcodes 1.44 with a 1.32 fallback ping (PR #11346) — but that default sits inside
  current daemons' windows, unlike our 1.43 pin which is already below Engine 29's 1.44 floor.
  Keep `TESTCONTAINERS_RYUK_DISABLED=true` (unrelated to negotiation; still required under the
  proxy). Known cosmetic rough edge: benign post-suite NPE in `JVMHookResourceReaper` when Ryuk
  is disabled.

## Not yet decided

- Whether kafka-dq keeps a thin framework module (`app` with Quarkus) as the ONLY
  second module (composition root + framework), i.e. "single-module domain + one
  framework module," or truly collapses to one module. The `java-hexagonal` skill's
  composition-root section (framework confined to the root) should settle this.
- Sequencing vs the other post-retro work.
