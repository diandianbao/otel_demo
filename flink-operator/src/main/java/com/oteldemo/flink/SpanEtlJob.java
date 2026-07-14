package com.oteldemo.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Span ETL Job：从 Kafka 消费 OTLP JSON span → 写入 PostgreSQL。
 *
 * <pre>
 * Kafka (otel-spans)
 *   → flatMap: 解析 OTLP JSON，提取所有 span
 *       → SERVER span: 生成 TraceRecord → trace 表
 *       → 所有 span:   生成 SpanRecord  → span 表
 * </pre>
 */
public class SpanEtlJob {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        // ===== Kafka Source =====
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("otel-spans")
                .setGroupId("flink-span-etl")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(
                        new org.apache.flink.api.common.serialization.SimpleStringSchema())
                .build();

        DataStream<String> kafkaStream = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "kafka-source");

        // ===== 解析 OTLP JSON =====
        DataStream<SpanBundle> parsed = kafkaStream.flatMap(new OtlpJsonParser());

        // ===== 写入 trace 表（SERVER span） =====
        DataStream<TraceRecord> traces = parsed.filter(
                b -> b.traceRecord != null).map(b -> b.traceRecord);

        traces.addSink(JdbcSink.sink(
                "INSERT INTO trace(trace_id, service_name, span_name, span_kind, " +
                        "http_method, url_path, request_body, response_body, " +
                        "start_time, duration_ms) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) " +
                        "ON CONFLICT (trace_id) DO UPDATE SET " +
                        "response_body = EXCLUDED.response_body, " +
                        "duration_ms = EXCLUDED.duration_ms",
                (stmt, record) -> {
                    stmt.setString(1, record.getTraceId());
                    stmt.setString(2, record.getServiceName());
                    stmt.setString(3, record.getSpanName());
                    stmt.setString(4, record.getSpanKind());
                    stmt.setString(5, record.getHttpMethod());
                    stmt.setString(6, record.getUrlPath());
                    stmt.setString(7, record.getRequestBody());
                    stmt.setString(8, record.getResponseBody());
                    stmt.setTimestamp(9, record.getStartTime());
                    stmt.setInt(10, record.getDurationMs());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(50)
                        .withBatchIntervalMs(2000)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:postgresql://postgres:5432/otel")
                        .withUsername("otel")
                        .withPassword("otel123")
                        .build()))
                .name("trace-sink");

        // ===== 写入 span 表 =====
        DataStream<SpanRecord> spans = parsed.flatMap(
                (SpanBundle b, Collector<SpanRecord> out) -> {
                    for (SpanRecord s : b.spans) {
                        out.collect(s);
                    }
                }).returns(SpanRecord.class);

        spans.addSink(JdbcSink.sink(
                "INSERT INTO span(span_id, trace_id, parent_span_id, span_name, span_kind, " +
                        "service_name, http_method, http_status, http_url, attributes, " +
                        "start_time, duration_ms) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) " +
                        "ON CONFLICT (span_id) DO UPDATE SET " +
                        "duration_ms = EXCLUDED.duration_ms",
                (stmt, record) -> {
                    stmt.setString(1, record.getSpanId());
                    stmt.setString(2, record.getTraceId());
                    stmt.setString(3, record.getParentSpanId());
                    stmt.setString(4, record.getSpanName());
                    stmt.setString(5, record.getSpanKind());
                    stmt.setString(6, record.getServiceName());
                    stmt.setString(7, record.getHttpMethod());
                    if (record.getHttpStatus() != null) {
                        stmt.setInt(8, record.getHttpStatus());
                    } else {
                        stmt.setNull(8, java.sql.Types.INTEGER);
                    }
                    stmt.setString(9, record.getHttpUrl());
                    stmt.setString(10, record.getAttributes());
                    stmt.setTimestamp(11, record.getStartTime());
                    stmt.setInt(12, record.getDurationMs());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(50)
                        .withBatchIntervalMs(2000)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:postgresql://postgres:5432/otel")
                        .withUsername("otel")
                        .withPassword("otel123")
                        .build()))
                .name("span-sink");

        env.execute("OTel Span ETL");
    }

    // ================================================================
    // 内部类
    // ================================================================

    /** 一次 Kafka 消息解析出的 span 集合 */
    public static class SpanBundle {
        TraceRecord traceRecord;     // null if no SERVER span
        List<SpanRecord> spans = new ArrayList<>();
    }

    /** 解析 OTLP JSON → SpanBundle */
    public static class OtlpJsonParser implements FlatMapFunction<String, SpanBundle> {

        @Override
        public void flatMap(String rawJson, Collector<SpanBundle> out) throws Exception {
            try {
                JsonNode root = MAPPER.readTree(rawJson);
                JsonNode resourceSpans = root.get("resourceSpans");
                if (resourceSpans == null || !resourceSpans.isArray()) {
                    return;
                }

                for (JsonNode rs : resourceSpans) {
                    // 拿 service.name
                    String serviceName = "otel-demo";
                    JsonNode resAttrs = rs.at("/resource/attributes");
                    if (resAttrs != null && resAttrs.isArray()) {
                        serviceName = getAttr(resAttrs, "service.name", serviceName);
                    }

                    JsonNode scopeSpans = rs.get("scopeSpans");
                    if (scopeSpans == null || !scopeSpans.isArray()) {
                        continue;
                    }

                    for (JsonNode ss : scopeSpans) {
                        JsonNode spans = ss.get("spans");
                        if (spans == null || !spans.isArray()) {
                            continue;
                        }

                        SpanBundle bundle = new SpanBundle();

                        for (JsonNode s : spans) {
                            JsonNode attrs = s.get("attributes");
                            String spanName = s.get("name").asText("");
                            int kind = s.get("kind").asInt(0);

                            // 计算时间
                            long startNano = Long.parseLong(
                                    s.get("startTimeUnixNano").asText("0"));
                            long endNano = Long.parseLong(
                                    s.get("endTimeUnixNano").asText("0"));
                            Timestamp startTime = new Timestamp(startNano / 1_000_000);
                            int durationMs = (int) ((endNano - startNano) / 1_000_000);

                            // ---- SpanRecord ----
                            SpanRecord sr = new SpanRecord();
                            sr.setSpanId(s.get("spanId").asText(""));
                            sr.setTraceId(s.get("traceId").asText(""));
                            // parentSpanId: "" 表示根 span → 转 null
                            String pid = s.get("parentSpanId").asText("");
                            sr.setParentSpanId(pid.isEmpty() ? null : pid);
                            sr.setSpanName(spanName);
                            sr.setSpanKind(kindName(kind));
                            sr.setServiceName(serviceName);
                            sr.setStartTime(startTime);
                            sr.setDurationMs(durationMs);

                            if (attrs != null) {
                                // HTTP 元数据（CLIENT span）
                                sr.setHttpMethod(getAttr(attrs, "http.request.method"));
                                String status = getAttr(attrs, "http.response.status_code");
                                if (status != null) {
                                    sr.setHttpStatus(Integer.parseInt(status));
                                }
                                sr.setHttpUrl(getAttr(attrs, "url.full"));

                                // 自定义属性存 JSONB
                                sr.setAttributes(buildAttributesJson(attrs, spanName, kind));
                            }

                            bundle.spans.add(sr);

                            // ---- TraceRecord（仅 SERVER span） ----
                            if (kind == 2 && bundle.traceRecord == null) { // SPAN_KIND_SERVER
                                TraceRecord tr = new TraceRecord();
                                tr.setTraceId(sr.getTraceId());
                                tr.setServiceName(serviceName);
                                tr.setSpanName(spanName);
                                tr.setSpanKind("SERVER");
                                tr.setStartTime(startTime);
                                tr.setDurationMs(durationMs);

                                if (attrs != null) {
                                    tr.setHttpMethod(getAttr(attrs, "http.request.method"));
                                    tr.setUrlPath(getAttr(attrs, "url.path"));
                                    tr.setRequestBody(getAttr(attrs, "request.body"));
                                    tr.setResponseBody(getAttr(attrs, "response.body"));
                                }

                                bundle.traceRecord = tr;
                            }
                        }

                        out.collect(bundle);
                    }
                }
            } catch (Exception e) {
                // 解析失败跳过，不丢消息
                System.err.println("[span-etl] parse error: " + e.getMessage());
            }
        }

        // ---- 内联工具方法 ----

        static String kindName(int kind) {
            switch (kind) {
                case 1:
                    return "INTERNAL";
                case 2:
                    return "SERVER";
                case 3:
                    return "CLIENT";
                case 4:
                    return "PRODUCER";
                case 5:
                    return "CONSUMER";
                default:
                    return "UNSPECIFIED";
            }
        }

        static String getAttr(JsonNode attrs, String key) {
            return getAttr(attrs, key, null);
        }

        static String getAttr(JsonNode attrs, String key, String defaultVal) {
            if (attrs == null || !attrs.isArray()) return defaultVal;
            for (JsonNode a : attrs) {
                if (key.equals(a.get("key").asText(""))) {
                    JsonNode val = a.get("value");
                    if (val == null) return defaultVal;
                    if (val.has("stringValue")) return val.get("stringValue").asText();
                    if (val.has("intValue")) return val.get("intValue").asText();
                    if (val.has("boolValue")) return val.get("boolValue").asText();
                    if (val.has("doubleValue")) return val.get("doubleValue").asText();
                    return defaultVal;
                }
            }
            return defaultVal;
        }

        /** 收集非 HTTP、非 request/response body 的自定义属性为 JSON 字符串 */
        static String buildAttributesJson(JsonNode attrs, String spanName, int kind) {
            if (attrs == null || !attrs.isArray()) return null;

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;

            // 预定义的标准属性 key，收集到 attributes JSONB 时排除
            for (JsonNode a : attrs) {
                String key = a.get("key").asText("");
                // Server span: 排除 request.body, response.body（已提升到 trace 表列）
                // 其他 span 所有自定义属性都保留
                if (kind == 2 &&
                        ("request.body".equals(key) || "response.body".equals(key))) {
                    continue;
                }
                // AI span: ai.request.body, ai.response.body 保留在 attributes
                // 排除标准 HTTP 属性
                if (key.startsWith("http.")
                        || key.startsWith("network.")
                        || key.startsWith("server.")
                        || key.startsWith("client.")
                        || key.startsWith("thread.")
                        || key.startsWith("user_agent.")
                        || key.startsWith("url.")
                        || key.startsWith("otel.")
                        || key.startsWith("span.")) {
                    continue;
                }

                if (!first) sb.append(",");
                first = false;

                String valStr = getAttrValueStr(a.get("value"));
                sb.append("\"").append(jsonEscape(key)).append("\":")
                        .append(valStr != null ? "\"" + jsonEscape(valStr) + "\"" : "null");
            }

            sb.append("}");
            return sb.length() > 2 ? sb.toString() : null;
        }

        static String getAttrValueStr(JsonNode val) {
            if (val == null) return null;
            if (val.has("stringValue")) return val.get("stringValue").asText();
            if (val.has("intValue")) return val.get("intValue").asText();
            if (val.has("boolValue")) return val.get("boolValue").asText();
            if (val.has("doubleValue")) return val.get("doubleValue").asText();
            if (val.has("arrayValue")) return val.get("arrayValue").toString();
            return null;
        }

        static String jsonEscape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
