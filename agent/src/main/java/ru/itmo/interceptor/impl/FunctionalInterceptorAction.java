package ru.itmo.interceptor.impl;


import ru.itmo.interceptor.InterceptorAction;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.time.Instant;

public final class FunctionalInterceptorAction implements InterceptorAction {

    private static final String LOG_FILE =
            System.getProperty("agent.log.file", "agent-method-calls.jsonl");

    private static final Object LOCK = new Object();

    @Override
    public void executeBefore(
            Object thiz,
            Object[] args,
            Method method,
            String methodName,
            StackTraceElement caller
    ) {
        writeEvent(
                "ENTER",
                -1,
                thiz,
                args,
                null,
                null,
                method
        );
    }

    @Override
    public void executeAfter(
            long startTime,
            Object thiz,
            Object[] args,
            Object returnValue,
            Throwable throwable,
            Method method,
            String methodName, StackTraceElement caller
    ) {
        long durationNs = startTime > 0
                ? System.nanoTime() - startTime
                : -1;

        writeEvent(
                "EXIT",
                durationNs,
                thiz,
                args,
                returnValue,
                throwable,
                method
        );
    }

    // =========================================================
    // Internal
    // =========================================================

    private void writeEvent(
            String phase,
            long durationNs,
            Object thiz,
            Object[] args,
            Object returnValue,
            Throwable throwable,
            Method method
    ) {
        synchronized (LOCK) {
            try (PrintWriter out = new PrintWriter(new FileWriter(getLogFile(), true))) {
                out.println(buildJson(
                        phase,
                        durationNs,
                        thiz,
                        args,
                        returnValue,
                        throwable,
                        method
                ));
                out.flush();
            } catch (Throwable ignored) {
                // агент НИКОГДА не должен ломать приложение
            }
        }
    }

    private File getLogFile() {
        return new File(LOG_FILE);
    }

    // =========================================================
    // JSON
    // =========================================================

    private String buildJson(
            String phase,
            long durationNs,
            Object thiz,
            Object[] args,
            Object returnValue,
            Throwable throwable,
            Method method
    ) {
        StringBuilder json = new StringBuilder(512);

        json.append('{');

        field(json, "timestamp", Instant.now().toString());
        comma(json);
        field(json, "phase", phase);
        comma(json);
        field(json, "thread", Thread.currentThread().getName());
        comma(json);
        field(json, "class", method.getDeclaringClass().getName());
        comma(json);
        field(json, "method", method.getName());
        comma(json);
        field(json, "signature", method.toGenericString());
        comma(json);
        field(json, "instanceClass",
                thiz != null ? thiz.getClass().getName() : null);
        comma(json);
        json.append("\"arguments\":").append(formatArguments(args));

        if ("EXIT".equals(phase)) {
            comma(json);
            json.append("\"returnValue\":").append(formatValue(returnValue));
            comma(json);
            json.append("\"exception\":").append(formatThrowable(throwable));
            comma(json);
            json.append("\"durationNs\":").append(durationNs);
        }

        json.append('}');
        return json.toString();
    }

    private void field(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(escape(value)).append('"');
        }
    }

    private void comma(StringBuilder json) {
        json.append(',');
    }

    // =========================================================
    // Value formatting
    // =========================================================

    private String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringBuilder arr = new StringBuilder();
        arr.append('[');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) arr.append(',');
            arr.append(formatValue(args[i]));
        }
        arr.append(']');
        return arr.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> type = value.getClass();

        String str;
        try {
            str = value.toString();
        } catch (Throwable t) {
            str = "<toString failed>";
        }

        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "type", type.getName());
        comma(json);
        field(json, "value", str);
        json.append('}');
        return json.toString();
    }

    private String formatThrowable(Throwable t) {
        if (t == null) {
            return "null";
        }

        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "type", t.getClass().getName());
        comma(json);
        field(json, "message", t.getMessage());
        json.append('}');
        return json.toString();
    }

    private String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        out.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
