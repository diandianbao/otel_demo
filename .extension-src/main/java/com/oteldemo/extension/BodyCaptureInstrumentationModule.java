package com.oteldemo.extension;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.Arrays;
import java.util.List;

/**
 * OpenTelemetry Java Agent 扩展模块入口。
 * <p>
 * 负责注册所有自定义的 TypeInstrumentation（拦截规则）。
 * 每个 TypeInstrumentation 定义"拦截哪些类"和"怎么改字节码"。
 */
public class BodyCaptureInstrumentationModule extends InstrumentationModule {

    public BodyCaptureInstrumentationModule() {
        // 这个名称会出现在 otel.javaagent 的日志中
        super("body-capture-extension", "1.0.0");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return Arrays.asList(
                // 入站：拦截 @RestController 方法 → request.body / response.body
                new ControllerBodyCaptureInstrumentation(),

                // 出站：拦截 OpenAiApi.chatCompletionEntity()
                //       → 创建 chat.completion DeepSeek span
                //       → ai.request.body / ai.response.body
                new SpringAiInstrumentation()
        );
    }
}
