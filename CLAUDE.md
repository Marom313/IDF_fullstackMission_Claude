# CLAUDE.md – IPP (Israel Permit Program)

Demo system for entry permits, employment licenses, policy rules (DSL), eligibility checks and
sanctions enforcement. **All data is MOCK.** Public repo: never commit secrets or real data.

## Ubiquitous language
Person · Permit (narrow, short) · Employment License (broad, sector quota, workers assigned) ·
Policy/Rule (versioned DSL in Mongo) · Eligibility Check · Crossing Event · Violation → Case → Sanction ·
Closing the loop. Roles: WORKER, DRIVER, EMPLOYER, CROSSING_OFFICER, CLERK, POLICY_ADMIN,
ENFORCEMENT_OFFICER, AUDITOR, SYS_ADMIN. Demo users: `<role>1` (worker1, officer1, ...), password `Passw0rd!`.

## Repo map
```
apps/web                 React + TS + Vite, MVVM (model / viewmodel / view per feature)
services/<name>          Spring Boot 3, Java 21, Hexagonal (domain/application/adapters)
  gateway, registry-service, permit-service, eligibility-service, enforcement-service, audit-service
  policy-service, mock-state-api   ← built by the cloud agent on branch cloud/policy-and-mock
libs/java-common         outbox, inbox, problem-details, PII masking, tracing
contracts/{openapi,events}
infra/{postgres,pgbouncer,keycloak,nifi,nginx,seaweedfs,grafana}
ingest/{inbox,quarantine,archive}   runtime, gitignored
scripts/{verify.sh,offline,seed,perf,ingest}
docs/{SPEC,WORKLOG,GAPS,CLOSED_NETWORK,DEMO_SCRIPT}.md  docs/adr/  docs/perf/  docs/setup/
presentation_pics/ test.py   ← pre-existing, never touch
```

## Commands (`./ipp.ps1 <cmd>` on Windows, `make <cmd>` in WSL/Git Bash)
`up` (core profile, waits healthy) · `down` · `reset` (drops volumes) · `logs [svc]` · `ps` ·
`seed` · `verify` · `demo`. URLs: app http://localhost · Keycloak http://localhost/auth (admin also
on :8180) · kafka-ui http://localhost:8085.

## Machine safety (Windows 10, 32GB, Docker Desktop/WSL2)
- Every compose service has `mem_limit` + `cpus`. Spring: 512m, SerialGC, MaxRAMPercentage=70.
- core profile ≤ 8GB total. NiFi only in `ingest` profile. `COMPOSE_PARALLEL_LIMIT=2`.
- Maven builds run in a container capped at 1.5GB / 2 CPUs. Never run unbounded parallel builds.

## Code rules
- Backend: Hexagonal. `domain` has no Spring imports (ArchUnit). OpenAPI-first per service.
  RFC 7807 errors with business code + traceId. Idempotency-Key on creating POSTs.
  `open-in-view=false`, LAZY associations, `@Version`, DTO projections, keyset pagination.
- Each service owns one Postgres schema + user; never read another service's schema.
  Copies elsewhere are read models fed by Kafka events only.
- Kafka topics `<domain>.<entity>.<event>.v1`, key = personId. Transactional outbox for producing,
  inbox table (`ON CONFLICT DO NOTHING`) for consuming, DLT `<topic>.DLT`.
- Redis: TTL on every key, prefix `v1:`.
- JDBC through PgBouncer (transaction mode) ⇒ `prepareThreshold=0`. Liquibase connects to Postgres directly.
- Frontend: MVVM. `view` never imports `model` (eslint-plugin-boundaries). React Hook Form + Zod,
  TanStack Query, types generated from OpenAPI (orval). SCSS modules, design tokens,
  **CSS logical properties only**. i18n he (default) / en / ru / ar with rtl/ltr switching.
- Config files (`postgresql.conf`, `pgbouncer.ini`, Hikari in `application.yaml`): every changed
  parameter has a `[MANUAL CHANGE – Marom]` comment block (default → new, why). Checked by verify.

## Forbidden
`latest` image tags · CDNs / runtime calls to the internet · editing an applied Liquibase changeset ·
eval / SpEL / JS / Groovy for rules · real people's data or photos · official state/IDF/COGAT emblems ·
secrets in git (only `.env.example` with throwaway dev values) · MinIO (archived; use SeaweedFS).

## Definition of Done
Code + tests · `ipp verify` green · stack comes up healthy · WORKLOG entry (Hebrew explanations of
new concepts + known bugs avoided) · GAPS.md row in the same commit for any mock/shortcut ·
SPEC/ADR/CLAUDE.md updated if a decision changed.

## Commit procedure
- Short feature branches (`feat/...`, `fix/...`), merge to `main` after green verify, tag milestones.
- `git pull --rebase` before every push (a cloud agent pushes in parallel).
- Message: Conventional Commit + version suffix, e.g. `feat(infra): phase 0 skeleton 1.1.3`.
  Versions continue from `First_Commit 1.1.1` (patch bump per commit).
- End message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
