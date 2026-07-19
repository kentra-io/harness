# Retro idea: a Testcontainers-Java skill to prevent trivial container-portability bugs

*Raised by the user 2026-07-15 after `kafka-dq/001-e2e-poc` shipped "green" but
failed `./gradlew clean test` on a standard Docker host — two textbook
Testcontainers mistakes (see `tasks/kafka-dq-e2e-box-only-green.md`).*

> **Partially resolved 2026-07-19:** the architecture-level Testcontainers guidance now lives
> in the `java-hexagonal` skill's tier-3 test-strategy section (`kentra-io/kentra-skills`):
> advertised-listener `host:mappedPort`, the API-version window mechanism (no Java client truly
> negotiates — prefer Testcontainers 2.x whose default 1.44 sits in current daemons' windows;
> pin explicitly only if trapped on 1.x, as a dated workaround), Ryuk-under-proxy, and the
> "verify on the DoD's real target environment, not only the proxied box" rule. A standalone
> deep `testcontainers-java` skill remains optional/future. A TC 1.20.4 → 2.0.5 bump was
> empirically verified viable on kafka-dq (138/138 green in-box, pin deleted) — queued as a
> thread-F item in `tasks/kafka-dq-restructure-single-module.md`.

## The problem it would prevent

Both failures were **well-known Testcontainers-Java footguns**, not novel
issues:
1. Docker environment/API-version resolution differs between a `tcp://` socket
   proxy and a plain unix socket (+ modern Docker Desktop API) — the pin/gate
   was wrong for the unix-socket case.
2. Kafka **advertised listeners** must be host-reachable on the mapped port for
   the standard-daemon path; hardcoding the container-internal IP works only
   in-network. This is THE most common Testcontainers-Kafka mistake.

An agent implementing (or verifying) integration tests should have these
patterns on tap so it never re-derives them wrong.

## The idea (decide at retro — find one or create one)

Adopt or author a **`testcontainers-java` skill** carrying the canonical
patterns + checklist, e.g.:
- Kafka/Redpanda advertised-listener setup that works on BOTH a standard daemon
  (mapped ports, `getHost():getMappedPort()`) AND in-network/proxied setups —
  the dual-listener pattern, or "use the maintained `org.testcontainers:kafka`
  `KafkaContainer` which handles this."
- Docker environment resolution: don't gate config on `DOCKER_HOST` scheme;
  Ryuk/api-version handling that is portable across unix-socket + tcp-proxy.
- A "verify on the DoD's real target" rule: an IT whose DoD says "green on CI /
  standard daemon" must be run there, never only in a bespoke box.
- Awaitility over sleeps (this repo already does this well — keep it).

## Options to weigh at retro
- **Find:** search for an existing community/Anthropic Testcontainers skill and
  adopt it (cheapest if one exists and is good).
- **Create:** author a kentra skill (agent-agnostic, like the other primitives)
  distilled from the two bugs here + the maintained Testcontainers docs.
- **Where it plugs in:** the cast personas (implementer + verifier) should load
  it whenever a change touches integration tests. Also relevant to the harness
  process fix — `full_healthcheck` running a real `clean build` on a standard
  daemon would have caught both bugs regardless of the skill.

## Related
- `tasks/kafka-dq-e2e-box-only-green.md` (the two bugs + the healthcheck-default
  process gap), `tasks/orchestration-does-not-commit-milestones.md`.
