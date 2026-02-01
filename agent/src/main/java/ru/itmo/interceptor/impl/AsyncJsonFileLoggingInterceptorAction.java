package ru.itmo.interceptor.impl;

import ru.itmo.interceptor.InterceptorAction;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class AsyncJsonFileLoggingInterceptorAction implements InterceptorAction {

    // =========================
    // Config
    // =========================

    private static final String LOG_FILE =
            System.getProperty("agent.log.file", "agent-method-calls.jsonl");

    private static final int QUEUE_SIZE =
            Integer.getInteger("agent.log.queue.size", 8192);

    private static final long FLUSH_INTERVAL_MS =
            Long.getLong("agent.log.flush.ms", 1000);

    // =========================
    // State
    // =========================

    private static final BlockingQueue<String> QUEUE =
            new ArrayBlockingQueue<>(QUEUE_SIZE);

    private static final WriterThread WRITER;

    static {
        WRITER = new WriterThread();
        WRITER.setDaemon(true);
        WRITER.setName("agent-async-writer");
        WRITER.start();

        Runtime.getRuntime().addShutdownHook(new Thread(WRITER::shutdown, "agent-writer-shutdown"));
    }

    // =========================
    // InterceptorAction
    // =========================

    @Override
    public void executeBefore(
            Object thiz,
            Object[] args,
            Method method,
            String methodName
    ) throws InterruptedException {
        enqueue(buildJson(
                "ENTER",
                -1,
                thiz,
                args,
                null,
                null,
                method
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
            String methodName
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
                method
        ));
    }

    // =========================
    // Queue
    // =========================

    private void enqueue(String json) throws InterruptedException {
        // НЕ блокируем приложение
        while (!QUEUE.offer(json)) {
            Thread.sleep(100);// drop silently
        }
    }

    // =========================
    // Writer thread
    // =========================

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

    // =========================
    // JSON
    // =========================

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

    // =========================
    // Value formatting
    // =========================

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        String str;
        try {
            str = value.toString();
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
                        out.append("\\u")
                                .append(String.format("%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static final class WriterThread extends Thread {

        private volatile boolean running = true;

        void shutdown() {
            running = false;
            this.interrupt();
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
                // агент никогда не должен ломать JVM
            }
        }
    }
}