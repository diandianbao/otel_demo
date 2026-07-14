#!/bin/bash
# ============================================================
# 下载 OpenTelemetry Java Agent
# 用法: ./download-agent.sh [版本号，默认 2.29.0]
# ============================================================

set -e

VERSION="${1:-2.29.0}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${VERSION}/opentelemetry-javaagent.jar"

echo "⬇️  下载 OpenTelemetry Java Agent v${VERSION}..."
curl -L -o "$SCRIPT_DIR/opentelemetry-javaagent.jar" "$URL"
ls -lh "$SCRIPT_DIR/opentelemetry-javaagent.jar"
echo "✅ 下载完成"
