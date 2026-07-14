#!/bin/bash
# ============================================================
# 从 Jaeger 下载指定 trace 的原始 JSON
# 用法:
#   ./download-trace.sh <traceID>           下载一条 trace
#   ./download-trace.sh <traceID> --save    保存到文件
#   ./download-trace.sh --recent            下载最近一条 trace
#   ./download-trace.sh --recent --save     保存最近一条 trace
# ============================================================

JAEGER_API="http://localhost:16686/api"
SERVICE="otel-demo"

save_trace() {
    local trace_id="$1"
    local output_file="trace-${trace_id:0:16}-$(date +%H%M%S).json"
    curl -s "${JAEGER_API}/traces/${trace_id}" | python3 -m json.tool > "$output_file"
    echo "✅ 已保存到: $output_file"
    ls -lh "$output_file"
}

if [ "$1" = "--recent" ]; then
    # 获取最近的 traceID
    TRACE_ID=$(curl -s "${JAEGER_API}/traces?service=${SERVICE}&limit=1" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data['data'][0]['traceID'])" 2>/dev/null)
    if [ -z "$TRACE_ID" ]; then
        echo "❌ 未找到 trace"
        exit 1
    fi
    echo "📋 最近 trace: $TRACE_ID"
else
    TRACE_ID="$1"
    if [ -z "$TRACE_ID" ]; then
        echo "用法:"
        echo "  $0 <traceID>           下载指定 trace 并打印"
        echo "  $0 <traceID> --save    保存到文件"
        echo "  $0 --recent            下载最近一条 trace"
        echo "  $0 --recent --save     保存最近一条 trace"
        exit 1
    fi
fi

case "${2:-}" in
    --save)
        save_trace "$TRACE_ID"
        ;;
    *)
        curl -s "${JAEGER_API}/traces/${TRACE_ID}" | python3 -m json.tool
        ;;
esac
