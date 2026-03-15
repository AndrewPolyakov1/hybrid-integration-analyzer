package agent;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import ru.itmo.interceptor.InterceptorAction;
import ru.itmo.interceptor.impl.AsyncJsonFileLoggingInterceptorAction;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class MethodInterceptor {
    public static final InterceptorAction interceptorAction = new AsyncJsonFileLoggingInterceptorAction();
    public static Logger logger = Logger.getLogger(MethodInterceptor.class.getName());

    /**
     * Вызывается ПЕРЕД методом
     */
    @Advice.OnMethodEnter
    public static long onEnter(

            // this (null для static)
            @Advice.This(optional = true)
            Object thiz,

            // все аргументы
            @Advice.AllArguments
            Object[] args,

            // сигнатура метода
            @Advice.Origin Method method,

            // имя метода (быстрее, чем Method)
            @Advice.Origin("#t.#m")
            String methodName,
            @Advice.Origin("#s")
            String source

    ) throws InterruptedException {
        StackTraceElement caller;
        try {
            caller = Thread.currentThread().getStackTrace()[2];
        } catch (Exception e) {
            logger.info("Caller undefined, setting empty");
            caller = new StackTraceElement(MethodInterceptor.class.getName(),
                    "none",
                    null,
                    -1);
        }
        logger.info(
                "Called from " +
                        caller.getClassName() + "." +
                        caller.getMethodName() +
                        ":" + caller.getLineNumber());
        logger.info("MethodInterceptor.onEnter " + methodName + " " + source);
        interceptorAction.executeBefore(thiz, args, method, methodName, caller);
        return System.nanoTime();
    }

    /**
     * Вызывается ПОСЛЕ метода (и при return, и при throw)
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(

            // значение, возвращённое из onEnter
            @Advice.Enter
            long startTime,

            // this
            @Advice.This(optional = true)
            Object thiz,

            // аргументы (уже после выполнения метода)
            @Advice.AllArguments
            Object[] args,

            // return value (null для void)
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC)
            Object returnValue,

            // исключение (если было)
            @Advice.Thrown(readOnly = false)
            Throwable throwable,

            // сигнатура
            @Advice.Origin Method method,

            @Advice.Origin("#t.#m")
            String methodName
    ) throws InterruptedException {
        StackTraceElement caller;
        try {
            caller = Thread.currentThread().getStackTrace()[2];
        } catch (Exception e) {
            logger.info("Caller undefined, setting empty");
            caller = new StackTraceElement(MethodInterceptor.class.getName(),
                    "none",
                    null,
                    -1);
        }

        logger.info(
                "Called exit from " +
                        caller.getClassName() + "." +
                        caller.getMethodName() +
                        ":" + caller.getLineNumber());
        long durationNs = System.nanoTime() - startTime;
        logger.info("MethodInterceptor.onExit " + methodName);
        interceptorAction.executeAfter(startTime, thiz, args, returnValue, throwable, method, methodName, caller);
    }
}
