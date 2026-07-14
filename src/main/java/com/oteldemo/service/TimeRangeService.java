package com.oteldemo.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oteldemo.model.TimeRangeRequest;
import com.oteldemo.model.TimeRangeResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class TimeRangeService {

    private final ChatClient chatClient;

    public TimeRangeService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    private static final String SYSTEM_PROMPT = """
            你是一个时间范围解析器。用户会用自然语言描述一个时间范围，你需要将其转换为标准格式。

            规则：
            1. 输出必须是合法的 JSON，包含以下字段：
               - startTime: 起始时间，ISO 8601 格式字符串（如 "2026-07-01T00:00:00+08:00"）
               - endTime: 结束时间，ISO 8601 格式字符串
               - description: 用中文简要解释这个时间范围
            2. 时区使用中国标准时间（UTC+8）
            3. 如果用户输入无法解析为时间范围，startTime和endTime设为null，description说明原因
            4. 仅输出 JSON，不要输出任何其他内容（不要用 markdown 代码块包裹）

            常见自然语言时间表达示例：
            - "今天" -> 当天 00:00:00 到 23:59:59
            - "昨天" -> 昨天 00:00:00 到 23:59:59
            - "最近一周" / "过去7天" -> 7天前 00:00:00 到 今天 23:59:59
            - "最近一个月" / "过去30天" -> 30天前 00:00:00 到 今天 23:59:59
            - "上个月" -> 上个月1号 00:00:00 到 上个月最后一天 23:59:59
            - "今年" -> 今年1月1日 00:00:00 到 今天 23:59:59
            - "今年上半年" -> 今年1月1日 00:00:00 到 6月30日 23:59:59
            - "本周" -> 本周一 00:00:00 到 今天 23:59:59
            - "本月" -> 本月1日 00:00:00 到 今天 23:59:59
            - "上季度" -> 上季度第一天 00:00:00 到 上季度最后一天 23:59:59
            """;

    public TimeRangeResponse parse(TimeRangeRequest request) {
        String now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String userMessage = String.format("当前时间是 %s（北京时间）。请解析以下时间表达：%s", now, request.input());

        ParsedResult result = this.chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .entity(ParsedResult.class);

        return new TimeRangeResponse(
                request.input(),
                result != null ? result.startTime() : null,
                result != null ? result.endTime() : null,
                result != null ? result.description() : "解析失败"
        );
    }

    /**
     * LLM 返回的 JSON 映射类
     */
    public record ParsedResult(
            @JsonProperty("startTime") String startTime,
            @JsonProperty("endTime") String endTime,
            @JsonProperty("description") String description
    ) {
    }
}
