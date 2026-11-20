# IPP command runner for WSL / Git Bash / Linux. Same commands as ipp.ps1.
SHELL := bash
export COMPOSE_PARALLEL_LIMIT := 2
export DOCKER_BUILDKIT := 1
ALL_PROFILES := --profile core --profile ingest --profile observability
.PHONY: help up down reset logs ps seed verify demo env

help:
	@echo "ipp commands: up | down | seed | verify | demo | logs [s=svc] | ps | reset"
env:
	@[ -f .env ] || cp .env.example .env
up: env
	docker compose --profile core up -d --build --wait --wait-timeout 600
down:
	docker compose $(ALL_PROFILES) down
reset:
	docker compose $(ALL_PROFILES) down -v --remove-orphans
logs:
	docker compose logs -f --tail 200 $(s)
ps:
	docker compose ps
seed: env
	bash scripts/seed/seed.sh
verify: env
	bash scripts/verify.sh
demo: env
	docker compose --profile core --profile ingest up -d --build --wait --wait-timeout 900
	bash scripts/seed/seed.sh
