package com.oteldemo.extension;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * 拦截 @RestController 的方法调用，捕获入参和返回值。
 */
public class ControllerBodyCaptureInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return isAnnotatedWith(
                named("org.springframework.web.bind.annotation.RestController")
        );
    }

    @Override
    public void transform(TypeTransformer transformer) {
        transformer.applyAdviceToMethod(
                // 只拦截 public 方法 + 排除构造器 + 排除 Object 方法
                isPublic()
                        .and(not(isConstructor()))
                        .and(not(isStatic()))
                        .and(not(isDeclaredBy(Object.class))),
                this.getClass().getName() + "$CaptureAdvice"
        );
    }

    @SuppressWarnings("unused")
    public static class CaptureAdvice {
        // ★ 代码会被 Byte Buddy "复制粘贴"到目标 Controller 类中。
        // 不能调用本类的任何其他方法（包括静态方法），否则 NoClassDefFoundError。
        // 所有逻辑必须直接内联写在这两个方法体内。

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.AllArguments Object[] args) {
            Span span = Span.current();
            if (span == null || !span.isRecording() || args == null) {
                return;
            }
            String body = null;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                String name = arg.getClass().getName();
                if (name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("jakarta.servlet.")
                        || name.startsWith("org.springframework.")
                        || name.startsWith("org.apache.")) {
                    continue;
                }
                String s = arg.toString();
                if (s.length() > 8192) {
                    s = s.substring(0, 8192) + "...";
                }
                body = (body == null) ? s : (body + ", " + s);
            }
            if (body != null) {
                span.setAttribute("request.body", body);
            }
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.Return Object returnValue) {
            if (returnValue == null) {
                return;
            }
            String name = returnValue.getClass().getName();
            if (name.startsWith("java.") || name.startsWith("javax.")
                    || name.startsWith("jakarta.servlet.")
                    || name.startsWith("org.springframework.")
                    || name.startsWith("org.apache.")) {
                return;
            }
            Span span = Span.current();
            if (span == null || !span.isRecording()) {
                return;
            }
            String str = returnValue.toString();
            if (str.length() > 8192) {
                str = str.substring(0, 8192) + "...";
            }
            span.setAttribute("response.body", str);
        }
    }
}
