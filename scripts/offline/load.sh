#!/usr/bin/env bash
# Loads the bundle on the CLOSED-network side. Verifies integrity first.
set -euo pipefail
cd "$(dirname "$0")/../.."
IN=${1:-.offline-bundle}
( cd "$IN" && sha256sum -c SHA256SUMS )
docker load -i "$IN/images.tar"
mkdir -p "$HOME/.m2" && tar -C "$HOME" -xf "$IN/m2.tar" --transform 's#^m2#.m2#'
mkdir -p "$HOME/.npm-offline" && tar -C "$HOME/.npm-offline" -xf "$IN/npm-cache.tar"
echo "✔ loaded. Set DOCKER_REGISTRY / NPM_REGISTRY / MAVEN_MIRROR_URL in .env to the internal mirrors, then: make up"
