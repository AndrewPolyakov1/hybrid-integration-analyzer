package ru.itmo.interceptor.impl;

import ru.itmo.interceptor.InterceptorAction;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous {@link InterceptorAction} that writes method events
 * to a JSON Lines file.
 *
 * <p>Events are enqueued and written by a background thread to minimize
 * impact on application threads.
 */
public final class AsyncJsonFileLoggingInterceptorAction implements InterceptorAction {

    private static final String LOG_FILE =
            System.getProperty("agent.log.file", "agent-method-calls.jsonl");

    private static final int QUEUE_SIZE =
            Integer.getInteger("agent.log.queue.size", 8192);

    private static final long FLUSH_INTERVAL_MS =
            Long.getLong("agent.log.flush.ms", 1000);

    private static final BlockingQueue<String> QUEUE =
            new ArrayBlockingQueue<>(QUEUE_SIZE);

    private static final WriterThread WRITER;

    static {
        WRITER = new WriterThread();
        WRITER.setDaemon(true);
        WRITER.setName("agent-async-writer");
        WRITER.start();
        Runtime.getRuntime().addShutdownHook(
                new Thread(WRITER::shutdown, "agent-writer-shutdown"));
    }

    public static String toJson(Object obj,
                                Map<Object, Boolean> visited,
                                int depth) throws IllegalAccessException {

        if (obj == null) return "null";

        // защита от циклов
        if (visited.containsKey(obj)) {
            return "\"<cycle>\"";
        }

        // ограничение глубины (очень важно!)
        if (depth > 3) {
            return "\"<max-depth>\"";
        }

        Class<?> clazz = obj.getClass();

        // примитивы + обёртки + строки
        if (clazz.isPrimitive() ||
                obj instanceof Number ||
                obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }

        if (clazz.isEnum()) {
            return "\"" + obj.toString() + "\"";
        }

        // массивы
        if (clazz.isArray()) {
            int len = Array.getLength(obj);
            StringBuilder sb = new StringBuilder();
            sb.append("[");

            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(Array.get(obj, i), visited, depth + 1));
            }

            sb.append("]");
            return sb.toString();
        }

        // помечаем как посещённый
        visited.put(obj, true);

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;

        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                if (!first) sb.append(",");
                first = false;

                sb.append("\"").append(field.getName()).append("\":");

                Object value = field.get(obj);
                sb.append(toJson(value, visited, depth + 1));
            }

            clazz = clazz.getSuperclass();
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                if (c < 32) {
                    out.append("\\u")
                            .append(String.format("%04x", (int) c));
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    @Override
    public void executeBefore(
            Object thiz,
            Object[] args,
            Method method,
            String methodName,
            StackTraceElement caller
    ) throws InterruptedException {

        enqueue(buildJson(
                "ENTER",
                -1,
                thiz,
                args,
                null,
                null,
                method,
                caller
        ));
    }

    @Override
    public void executeAfter(
            long startTime,
            Object thiz,
            Object[] args,
            Object returnValue,
            Throwable throwable,
            Method method,
            String methodName,
            StackTraceElement caller
    ) throws InterruptedException {

        long durationNs = startTime > 0
                ? System.nanoTime() - startTime
                : -1;

        enqueue(buildJson(
                "EXIT",
                durationNs,
                thiz,
                args,
                returnValue,
                throwable,
                method,
                caller
        ));
    }

    private void enqueue(String json) throws InterruptedException {
        while (!QUEUE.offer(json)) {
            Thread.sleep(100);
        }
    }

    private String buildJson(
            String phase,
            long durationNs,
            Object thiz,
            Object[] args,
            Object returnValue,
            Throwable throwable,
            Method method,
            StackTraceElement caller
    ) {
        StringBuilder json = new StringBuilder(512);

        String jsonCallerObj = null;
        if (thiz != null) {
            try {
                jsonCallerObj = toJson(thiz, new IdentityHashMap<>(), 0);
                System.out.println(jsonCallerObj);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

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
        field(json, "callerClass", caller.getClassName());
        comma(json);
        field(json, "callerMethod", caller.getMethodName());
        comma(json);
        json.append("\"callerObject\":");
        if (jsonCallerObj == null) {
            json.append("null");
        } else {
            json.append(jsonCallerObj); // ← без кавычек
        }
        comma(json);
        field(json, "line", String.valueOf(caller.getLineNumber()));
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

        String str;
        try {
            str = String.valueOf(value);
        } catch (Throwable t) {
            str = "<toString failed>";
        }

        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "type", value.getClass().getName());
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

    /**
     * Background writer thread that drains the queue and writes to file.
     */
    private static final class WriterThread extends Thread {

        private volatile boolean running = true;

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            File file = new File(LOG_FILE);

            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {

                long lastFlush = System.currentTimeMillis();

                while (running || !QUEUE.isEmpty()) {
                    String record = QUEUE.poll(500, TimeUnit.MILLISECONDS);
                    if (record != null) {
                        out.println(record);
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastFlush >= FLUSH_INTERVAL_MS) {
                        out.flush();
                        lastFlush = now;
                    }
                }

                out.flush();

            } catch (Throwable ignored) {
                // must not affect the host application
            }
        }
    }
}