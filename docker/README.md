# OpenTelemetry + Jaeger + Kafka Demo

使用 OTEL Collector 作为数据入口，同时将 trace 发送到 Jaeger（UI 查询）和 Kafka（流式处理）。

## 架构

```
                              ┌─> Jaeger all-in-one ──> UI (:16686)
Java 应用 ──(OTLP)──> OTEL Collector ──|
                              └─> Kafka (:29092) ──> 下游消费者
```

## 组件

| 组件 | 端口 | 用途 |
|---|---|---|
| **otel-collector** | `4317` (gRPC), `4318` (HTTP) | OTLP 数据入口，fan-out 到多个后端 |
| **Jaeger** | `16686` | Trace 查询 UI |
| **Kafka** | `29092` (宿主机), `9092` (容器内) | Span 消息队列 |
| **Zookeeper** | `2181` | Kafka 依赖 |

## 启动

```bash
docker compose up -d
```

首次启动会拉取镜像，等待所有服务 healthy：

```bash
docker compose ps
# 确认 otel-collector、jaeger、kafka、zookeeper 都在 running
```

## 停止

```bash
docker compose down
```

## Java 应用接入

Java 应用的 OTLP endpoint 指向 **Collector** 而非 Jaeger：

```bash
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.service.name=my-service \
  -Dotel.traces.exporter=otlp \
  -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
  -jar your-app.jar
```

## 验证

### 1. Jaeger UI

打开 http://localhost:16686，搜索 service name 即可看到 trace。

### 2. Kafka — 消费 span

span 会写到 topic `otel-spans`，使用 OTLP protobuf 编码。可以用 kafka-console-consumer 快速验证：

```bash
# 查看消息（二进制，能看到有数据写入即可）
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic otel-spans \
  --from-beginning \
  --max-messages 5
```

或者从宿主机消费：

```bash
# 需要本地安装 kafka CLI，或用 kcat
kcat -b localhost:29092 -t otel-spans -C -c 5
```

### 3. 下游消费者解析 span

Kafka 中的消息是 OTLP protobuf 格式。在你的消费者代码中：

```java
// 使用 opentelemetry-proto 解析
TracesData tracesData = TraceService.exportRequestOf(
    ExportTraceServiceRequest.parseFrom(kafkaMessageBytes)
);
```

相关依赖：
```xml
<dependency>
    <groupId>io.opentelemetry.proto</groupId>
    <artifactId>opentelemetry-proto</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Kafka 编码格式

`otelcol-config.yaml` 中 `kafka` exporter 的 `encoding` 可选值：

| encoding | 说明 |
|---|---|
| `otlp_proto` | OTLP protobuf（默认，推荐） |
| `otlp_json` | OTLP JSON，人类可读 |
| `jaeger_proto` | Jaeger protobuf |

如果只是想快速预览数据，可以改为 `otlp_json`。
