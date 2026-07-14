#!/bin/bash
# ============================================================
# 使用 OpenTelemetry Java Agent (2.29.0) 启动 Spring Boot 应用
# OTLP HTTP/Protobuf → localhost:4318
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT_JAR="$SCRIPT_DIR/opentelemetry-javaagent.jar"
EXTENSION_JAR="$HOME/myprojects/java_otel_agent/target/otel-javaagent-extension-1.0.0.jar"
APP_JAR="$SCRIPT_DIR/target/otel-demo-0.0.1-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "❌ 找不到 opentelemetry-javaagent.jar"
    exit 1
fi

if [ ! -f "$APP_JAR" ]; then
    echo "📦 编译应用..."
    cd "$SCRIPT_DIR" && mvn clean package -DskipTests
fi

EXTENSION_ARGS=""
if [ -f "$EXTENSION_JAR" ]; then
    EXTENSION_ARGS="-Dotel.javaagent.extensions=$EXTENSION_JAR"
    echo "✅ 加载扩展: $EXTENSION_JAR"
fi

echo "========================================"
echo "  otel-demo (OTLP HTTP/Protobuf → :4318)"
echo "========================================"
echo "  Agent:    2.29.0"
echo "  Agent:    2.29.0"
echo "  Format:   HTTP/Protobuf (注: Agent 不支持 http/json)"
echo "  Endpoint: http://localhost:4318"
echo "  Jaeger:   http://localhost:16686"
echo "  App:      http://localhost:8080"
echo "  Env:      agent_code=${AGENT_CODE:-default}"
echo "========================================"

java \
  -javaagent:"$AGENT_JAR" \
  $EXTENSION_ARGS \
  -Dotel.service.name=otel-demo \
  -Dotel.resource.attributes=agent_code=${AGENT_CODE:-default} \
  -Dotel.traces.exporter=otlp \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.instrumentation.java-http-client.enabled=false \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none \
  -jar "$APP_JAR"
