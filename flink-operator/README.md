# Flink Operator — OTel Span ETL

从 Kafka 消费 OTEL Collector 输出的 OTLP JSON span，写入 PostgreSQL。

## 数据流

```
Kafka (otel-spans, OTLP JSON)
  → flatMap: 解析 JSON，提取每条 span
      → SERVER span → 生成 TraceRecord → trace 表 (UPSERT)
      → 所有 span → 生成 SpanRecord   → span 表  (UPSERT)
```

## 构建

```bash
mvn clean package
# 产物: target/flink-operator-1.0.0.jar
```

## 提交到 Flink

1. 启动所有 Docker 服务：`cd ../docker && docker compose up -d`
2. 打开 Flink Web UI：http://localhost:8081
3. Submit New Job → Add New → 上传 `target/flink-operator-1.0.0.jar`
4. 点击 JAR → 选择 `com.oteldemo.flink.SpanEtlJob` → Submit

## 表结构

trace 表：入口 SERVER span 提升，含 request.body / response.body (JSONB)
span 表：所有 span，自定义属性存入 attributes (JSONB)

DDL 在 `../docker/init.sql`，PostgreSQL 启动时自动执行。
