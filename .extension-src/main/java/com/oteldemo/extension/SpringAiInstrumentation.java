package com.oteldemo.extension;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * 拦截 OpenAiApi.chatCompletionEntity()，为每次 AI 调用创建独立子 span。
 *
 * <p>span 层级：
 * <pre>
 * POST /api/time-range/parse       (SERVER, Tomcat 创建)
 *  ├─ request.body                (Controller 入参)
 *  ├─ response.body               (Controller 返回值)
 *  └─ chat.completion DeepSeek     (CLIENT, 本类创建)
 *       ├─ ai.request.body         (完整 prompt)
 *       └─ ai.response.body        (完整 completion)
 * </pre>
 */
public class SpringAiInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return named("org.springframework.ai.openai.api.OpenAiApi");
    }

    @Override
    public void transform(TypeTransformer transformer) {
        transformer.applyAdviceToMethod(
                named("chatCompletionEntity"),
                this.getClass().getName() + "$ChatAdvice"
        );
    }

    @SuppressWarnings("unused")
    public static class ChatAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Object onEnter(@Advice.Argument(0) Object chatRequest) {
            if (chatRequest == null) {
                return null;
            }

            Tracer tracer = GlobalOpenTelemetry.get().getTracer("body-capture");
            Span span = tracer.spanBuilder("chat.completion DeepSeek")
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();

            String str = chatRequest.toString();
            if (str.length() > 8192) {
                str = str.substring(0, 8192) + "...";
            }
            span.setAttribute("ai.request.body", str);

            return span;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(
                @Advice.Return Object response,
                @Advice.Enter Object spanObj) {

            if (spanObj == null) {
                return;
            }
            Span span = (Span) spanObj;

            if (response != null) {
                try {
                    Object body = response.getClass().getMethod("getBody").invoke(response);
                    if (body != null) {
                        String str = body.toString();
                        if (str.length() > 8192) {
                            str = str.substring(0, 8192) + "...";
                        }
                        span.setAttribute("ai.response.body", str);
                    }
                } catch (Exception ignore) {
                }
            }

            span.end();
        }
    }
}
