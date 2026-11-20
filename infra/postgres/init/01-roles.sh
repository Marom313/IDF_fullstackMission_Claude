#!/bin/sh
# Runs once on first DB init. One schema + one least-privilege user per service.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE ROLE registry_svc    LOGIN PASSWORD '${REGISTRY_DB_PASSWORD}';
CREATE ROLE permits_svc     LOGIN PASSWORD '${PERMITS_DB_PASSWORD}';
CREATE ROLE eligibility_svc LOGIN PASSWORD '${ELIGIBILITY_DB_PASSWORD}';
CREATE ROLE enforcement_svc LOGIN PASSWORD '${ENFORCEMENT_DB_PASSWORD}';

CREATE SCHEMA registry    AUTHORIZATION registry_svc;
CREATE SCHEMA permits     AUTHORIZATION permits_svc;
CREATE SCHEMA eligibility AUTHORIZATION eligibility_svc;
CREATE SCHEMA enforcement AUTHORIZATION enforcement_svc;

-- each service sees only its own schema
ALTER ROLE registry_svc    SET search_path = registry;
ALTER ROLE permits_svc     SET search_path = permits;
ALTER ROLE eligibility_svc SET search_path = eligibility;
ALTER ROLE enforcement_svc SET search_path = enforcement;

-- [MANUAL CHANGE – Marom] statement_timeout per web role: 30s (global) → 5s
ALTER ROLE registry_svc    SET statement_timeout = '5s';
ALTER ROLE permits_svc     SET statement_timeout = '5s';
ALTER ROLE eligibility_svc SET statement_timeout = '5s';
ALTER ROLE enforcement_svc SET statement_timeout = '5s';

-- [MANUAL CHANGE – Marom] idle_session_timeout for interactive humans only: 0 → 10min
CREATE ROLE ipp_readonly_human LOGIN PASSWORD '${POSTGRES_PASSWORD}';
ALTER ROLE ipp_readonly_human SET idle_session_timeout = '10min';
SQL
