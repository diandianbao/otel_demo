package com.oteldemo.flink;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * span 表记录 — 所有 span 都存。
 */
public class SpanRecord implements Serializable {

    private String spanId;
    private String traceId;
    private String parentSpanId;   // null for root span
    private String spanName;
    private String spanKind;
    private String serviceName;
    private String httpMethod;
    private Integer httpStatus;
    private String httpUrl;
    private String attributes;     // JSON string for JSONB column
    private Timestamp startTime;
    private int durationMs;

    public SpanRecord() {
    }

    // ---- getters / setters ----

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getSpanName() {
        return spanName;
    }

    public void setSpanName(String spanName) {
        this.spanName = spanName;
    }

    public String getSpanKind() {
        return spanKind;
    }

    public void setSpanKind(String spanKind) {
        this.spanKind = spanKind;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "SpanRecord{spanId=" + spanId + ", traceId=" + traceId + ", name=" + spanName + "}";
    }
}
