-- ============================================================
-- OTel Demo — trace / span 表结构
-- ============================================================

-- trace 表：每次请求一条记录，由入口 SERVER span 提升而来
CREATE TABLE IF NOT EXISTS trace (
    trace_id        VARCHAR(32)   PRIMARY KEY,             -- OTel traceId（hex, 32 chars）
    service_name    VARCHAR(128)  NOT NULL DEFAULT 'otel-demo',
    span_name       VARCHAR(256)  NOT NULL,                -- POST /api/time-range/parse
    span_kind       VARCHAR(16)   NOT NULL DEFAULT 'SERVER',

    -- HTTP 元数据（精简）
    http_method     VARCHAR(8),                            -- POST / GET
    url_path        VARCHAR(512),                          -- /api/time-range/parse

    -- 业务关键数据
    request_body    JSONB,                                 -- request.body（用户输入）
    response_body   JSONB,                                 -- response.body（返回结果）

    -- 时间
    start_time      TIMESTAMPTZ   NOT NULL,                -- span 开始时间
    duration_ms     INTEGER       NOT NULL,                -- 耗时（毫秒）

    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trace_start_time ON trace(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trace_service_name ON trace(service_name);
CREATE INDEX IF NOT EXISTS idx_trace_http_route ON trace(url_path);

-- span 表：所有 span（SERVER + CLIENT）
CREATE TABLE IF NOT EXISTS span (
    span_id         VARCHAR(16)   PRIMARY KEY,             -- OTel spanId（hex, 16 chars）
    trace_id        VARCHAR(32)   NOT NULL REFERENCES trace(trace_id),
    parent_span_id  VARCHAR(16),                           -- null = 根 span

    span_name       VARCHAR(256)  NOT NULL,                -- POST /api/time-range/parse
    span_kind       VARCHAR(16)   NOT NULL,                -- SERVER / CLIENT
    service_name    VARCHAR(128)  NOT NULL DEFAULT 'otel-demo',

    -- HTTP（出站 CLIENT span 才有）
    http_method     VARCHAR(8),
    http_status     SMALLINT,
    http_url        VARCHAR(1024),                         -- api.deepseek.com/v1/...

    -- 自定义属性
    attributes      JSONB,                                 -- {"ai.request.body": "...", "ai.response.body": "..."}

    -- 时间
    start_time      TIMESTAMPTZ   NOT NULL,
    duration_ms     INTEGER       NOT NULL,

    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_span_trace_id       ON span(trace_id);
CREATE INDEX IF NOT EXISTS idx_span_name           ON span(span_name);
CREATE INDEX IF NOT EXISTS idx_span_start_time     ON span(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_span_attributes_gin ON span USING GIN (attributes);
