package ru.itmo.interceptor.impl;

import ru.itmo.interceptor.InterceptorAction;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class LoggingInterceptorAction implements InterceptorAction {
    Logger logger = Logger.getLogger(LoggingInterceptorAction.class.getName());

    @Override
    public void executeBefore(Object thiz,
                              Object[] args,
                              Method method,
                              String methodName,
                              StackTraceElement caller) {
        logger.info(String.format("=== MethodInterceptor.onBefore " + methodName));
    }

    @Override
    public void executeAfter(long startTime, Object thiz, Object[] args, Object returnValue, Throwable throwable,
                             Method method, String methodName, StackTraceElement caller) {
        logger.info(String.format("=== MethodInterceptor.onAfter " + methodName));
    }
}
