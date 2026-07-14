package com.oteldemo.flink;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * trace 表记录 — 由 SERVER span 提升而来。
 */
public class TraceRecord implements Serializable {

    private String traceId;
    private String serviceName;
    private String spanName;
    private String spanKind;
    private String httpMethod;
    private String urlPath;
    private String requestBody;   // JSON string
    private String responseBody;  // JSON string
    private Timestamp startTime;
    private int durationMs;

    public TraceRecord() {
    }

    // ---- getters / setters ----

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getUrlPath() {
        return urlPath;
    }

    public void setUrlPath(String urlPath) {
        this.urlPath = urlPath;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
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
        return "TraceRecord{traceId=" + traceId + ", spanName=" + spanName + "}";
    }
}
