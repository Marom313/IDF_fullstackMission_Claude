#!/usr/bin/env bash
# Seed is performed by each service on startup (deterministic Data Faker, fixed seed) when SEED_ENABLED=true.
# This script triggers / reports it.
set -euo pipefail
echo "seed: services seed themselves on first start (fixed seed=20261120). Nothing else to do yet."
