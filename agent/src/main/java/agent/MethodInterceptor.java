package agent;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import ru.itmo.interceptor.InterceptorAction;
import ru.itmo.interceptor.impl.AsyncJsonFileLoggingInterceptorAction;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Byte Buddy advice that intercepts method execution.
 *
 * <p>Delegates interception logic to an {@link InterceptorAction} implementation.
 * Invoked on method entry and exit (including exceptional completion).
 */
public final class MethodInterceptor {

    public static final InterceptorAction ACTION =
            new AsyncJsonFileLoggingInterceptorAction();

    public static final Logger LOGGER =
            Logger.getLogger(MethodInterceptor.class.getName());

    public MethodInterceptor() {
    }

    /**
     * Invoked before the intercepted method execution.
     *
     * @param thiz       the instance, or {@code null} for static methods
     * @param args       method arguments
     * @param method     method reflection object
     * @param methodName formatted method name
     * @param source     source signature
     * @return a timestamp used to measure execution duration
     * @throws InterruptedException if the interceptor action fails
     */
    @Advice.OnMethodEnter
    public static long onEnter(
            @Advice.This(optional = true) Object thiz,
            @Advice.AllArguments Object[] args,
            @Advice.Origin Method method,
            @Advice.Origin("#t.#m") String methodName,
            @Advice.Origin("#s") String source
    ) throws InterruptedException {

        StackTraceElement caller = resolveCaller();

        LOGGER.info("Enter " + methodName + " from "
                + caller.getClassName() + "."
                + caller.getMethodName() + ":"
                + caller.getLineNumber());

        ACTION.executeBefore(thiz, args, method, methodName, caller);
        return System.nanoTime();
    }

    /**
     * Invoked after the intercepted method execution, including exceptional exit.
     *
     * @param startTime   timestamp returned by {@link #onEnter}
     * @param thiz        the instance, or {@code null} for static methods
     * @param args        method arguments
     * @param returnValue return value, or {@code null} for {@code void}
     * @param throwable   thrown exception, or {@code null} if none
     * @param method      method reflection object
     * @param methodName  formatted method name
     * @throws InterruptedException if the interceptor action fails
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.This(optional = true) Object thiz,
            @Advice.AllArguments Object[] args,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnValue,
            @Advice.Thrown(readOnly = false) Throwable throwable,
            @Advice.Origin Method method,
            @Advice.Origin("#t.#m") String methodName
    ) throws InterruptedException {

        StackTraceElement caller = resolveCaller();
        long durationNs = System.nanoTime() - startTime;

        // Убираем лямбду
        LOGGER.info("Exit " + methodName + " from "
                + caller.getClassName() + "."
                + caller.getMethodName() + ":"
                + caller.getLineNumber()
                + " (" + durationNs + " ns)");

        ACTION.executeAfter(startTime, thiz, args, returnValue, throwable, method, methodName, caller);
    }

    public static StackTraceElement resolveCaller() {
        try {
            return Thread.currentThread().getStackTrace()[2];
        } catch (Exception ex) {
            LOGGER.fine("Caller not available");
            return new StackTraceElement(
                    MethodInterceptor.class.getName(),
                    "unknown",
                    null,
                    -1
            );
        }
    }
}