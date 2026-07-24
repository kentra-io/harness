# Finding: kafka-dq `001-e2e-poc` integration tests were box-only green (failed `clean test` on a standard Docker host) — FIXED 2026-07-15

> **RESOLVED (commit `926a39b`).** Both bugs below are fixed and verified green
> on a standard unix-socket Docker host via `./gradlew clean build` (the exact
> CI command): app e2e 9/9, adapter-registry 23/23, 138 tests total, 0 failures,
> from a clean state with no init-script crutch. Bug 1 → `api.version=1.43`
> pinned unconditionally in both test tasks. Bug 2 → `E2eEnvironment` now uses a
> delayed-start `MappedPortKafka` that advertises `PLAINTEXT://<host>:<mappedPort>`
> on the mapped-port path (proxied path unchanged). **Still open for the retro:**
> the process gap (§"Process implications") — `full_healthcheck` defaulting to
> `exit 0` is what let a box-only-green change reach "complete"; that is a harness
> fix, not fixed here. Also open: the Testcontainers-skill idea
> (`tasks/retro-idea-testcontainers-skill.md`).
> kafka-dq declared irrelevant 2026-07-19 (will be rebuilt from scratch);
> follow-ups here are pattern-reference only.

*Surfaced 2026-07-15 when the user ran `./gradlew clean test` in the worktree
and it failed — after I had reported the change "9/9 e2e green / complete".
I had NOT run `clean test`; I trusted the incremental JUnit XML left in
`build/` by the in-box milestone run + the verifier agent's report. Hollow
verification. This corrects the record.*

## The claim that was wrong

Reported (memory `kafka-dq-e2e-poc`, my session summary): "EndToEndValidationIT
9/9 green, adapter-registry 23/23, CI runs the identical ladder." **True only
inside the agent box's `tcp://` socket-proxy environment.** On a standard
unix-socket Docker host (fresh clone, teammate laptop, CI ubuntu-latest),
`./gradlew clean test` FAILS. Two independent portability bugs:

## Bug 1 — Docker API-version pin is mis-gated (blocks all ITs on a unix socket)

`app/build.gradle.kts` + `adapter-registry/build.gradle.kts` `test` task:
```kotlin
if (System.getenv("DOCKER_HOST")?.startsWith("tcp://") == true) {
    systemProperty("api.version", "1.43")
}
```
On a plain unix socket there is no `DOCKER_HOST`, so the pin is skipped.
docker-java (bundled in Testcontainers 1.20.4) then negotiates its default API
version against this host's Docker Desktop daemon (**API v1.55**) and gets
`400 BadRequestException` → `IllegalStateException: Could not find a valid
Docker environment`. Every IT dies at `@BeforeAll` before a container starts.
`clean test` never reaches M7 — it fails first at M5 `ApicurioRegistryAdapterIT`.

**Proven fix:** applying `api.version=1.43` unconditionally (via an init script
forcing it on all `Test` tasks) makes `:adapter-registry:test` BUILD
SUCCESSFUL. So the pin should not be gated on `tcp://` — pin it whenever the
ITs run (or key it off a portable signal, not the proxy).

## Bug 2 — Kafka advertised listener hardcoded to the container-internal IP (breaks the mapped-port / CI path)

`app/src/test/java/.../E2eEnvironment.java` launches the broker with a fixed
entrypoint (`SELF_ADVERTISE`, applied unconditionally):
```
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$IP:9092   # $IP = container's in-Docker IP
```
- **Proxied path** (box): `bootstrapServers = kafkaIp:9092` — matches the
  advertised listener; the in-network test JVM connects. Works.
- **Mapped-port path** (standard daemon / CI): `bootstrapServers =
  localhost:<mappedPort>`. Client bootstraps, receives metadata advertising
  the unreachable internal `$IP:9092`, and every produce/consume round-trip
  **times out** (`org.apache.kafka.common.errors.TimeoutException`, at
  `EndToEndValidationIT.java:294`). **6 of 9 app e2e tests fail**; the 3 that
  pass are the startup-failure tests (they assert `start()` throws and never
  do a Kafka round-trip).

The `else` branch's own javadoc claims "standard daemon (unix socket, e.g. CI)
mapped ports on getHost() are used" — but the advertised-listener scheme was
never switched for it, so that path was **never actually run green**.

**Fix (standard Testcontainers dual-listener pattern):** for the non-proxied
path advertise a host-reachable listener. Two listeners — an internal
`PLAINTEXT://` for in-network use and an external one advertised as
`<host>:<mappedPort>` — with the external advertised address set *after* the
port mapping is known (the Testcontainers `KafkaContainer` trick: a startup
script that rewrites `advertised.listeners` post-start, or use the maintained
`org.testcontainers:kafka` `KafkaContainer` which handles this). Then verify
`clean test` goes green on THIS host (standard daemon), which is the portable
proof that was never done.

## Process implications (for the retro)

- **Box-green ≠ portable-green.** The milestone ladder's verifier only ever
  saw the in-box (`tcp://`) path. Nothing checked the standard-daemon path the
  DoD explicitly names ("CI runs the full suite green"). The DoD's CI item was
  asserted, not verified — `tasks.md` admits no push/run happened.
- **Verification should run the DoD's actual target environment.** A milestone
  whose DoD says "green on CI/standard daemon" must be verified there (or in an
  environment equivalent to it), not only in the box. Candidate fix to the
  orchestration: the change-level `full_healthcheck` should run the real
  `./gradlew clean build`/`test` in a standard-daemon context, not a trivial
  `exit 0` default — here the healthcheck passed because it was the always-pass
  default (see `execute-change.yaml` `healthcheck_command` default `exit 0`).
- I propagated the unverified "green" into memory + my summary. Corrected now.

## State
- HEAD `0a08039` (M7 committed). The failing tests are committed as-is.
- Related: `tasks/orchestration-does-not-commit-milestones.md`,
  `tasks/execute-change-box-input-spec.md`. Retro material preserved at
  `tasks/retro-archive/001-e2e-poc/` (box `claudebox-11934eb9d45a` still Up).
