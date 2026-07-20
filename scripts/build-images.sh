#!/usr/bin/env bash
# 构建 GiRisk Console 镜像（本地 / 推送前置）
# 对齐 giso/scripts/build-images.sh · gido CI GHCR 命名
#
# 决策 Engine 不在本仓（内部仓库 + gido 提交 jar）。
#
#   bash scripts/build-images.sh
#   GIRISK_IMAGE_TAG=dev bash scripts/build-images.sh
#   GIRISK_PUSH=1 GIRISK_IMAGE_REGISTRY=ghcr.io/cloud-gido/girisk bash scripts/build-images.sh
#
# 国内可覆盖基础镜像:
#   MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 \
#   JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine \
#   NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine \
#   bash scripts/build-images.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REGISTRY="${GIRISK_IMAGE_REGISTRY:-girisk}"
TAG="${GIRISK_IMAGE_TAG:-local}"
PLATFORM="${GIRISK_BUILD_PLATFORM:-}"
PUSH="${GIRISK_PUSH:-0}"
BUILD_CONSOLE="${GIRISK_BUILD_CONSOLE:-1}"

build_one() {
  local name="$1" dockerfile="$2"
  shift 2
  local full="${REGISTRY}/${name}:${TAG}"
  local -a args=()
  while [ $# -gt 0 ]; do args+=("$1"); shift; done

  echo "[build] ${full}"
  if [ -n "$PLATFORM" ]; then
    local load_or_push=(--load)
    if [ "$PUSH" = "1" ]; then load_or_push=(--push); fi
    docker buildx build --platform "$PLATFORM" --provenance=false --sbom=false \
      "${load_or_push[@]}" \
      -f "$dockerfile" "${args[@]}" -t "$full" "$ROOT"
  else
    docker build -f "$dockerfile" "${args[@]}" -t "$full" "$ROOT"
    if [ "$PUSH" = "1" ]; then docker push "$full"; fi
  fi
}

if [ "$BUILD_CONSOLE" = "1" ]; then
  build_one girisk-console deploy/Dockerfile.console \
    --build-arg "NODE_IMAGE=${NODE_IMAGE:-node:20-alpine}" \
    --build-arg "MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}" \
    --build-arg "JRE_IMAGE=${JRE_IMAGE:-eclipse-temurin:21-jre-alpine}"
fi

echo "[build] done — ${REGISTRY}/girisk-console:${TAG}"

