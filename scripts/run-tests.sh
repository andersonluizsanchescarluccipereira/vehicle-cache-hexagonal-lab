#!/usr/bin/env bash
set -euo pipefail
# Rodar testes sem instalar Maven localmente. Requer Docker.
docker run --rm -v "$PWD":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace \
  maven:3.9-eclipse-temurin-21 mvn -B test
