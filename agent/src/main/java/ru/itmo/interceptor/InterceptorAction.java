package ru.itmo.interceptor;

import java.lang.reflect.Method;

/**
 * Implies action, that happens before and after the call of the methods
 *
 * @author andrew
 */
public interface InterceptorAction {
    /**
     * Executes before every method
     *
     * @param thiz       current object
     * @param args       arguments
     * @param method     method signature
     * @param methodName method name
     * @param caller     caller of the method
     */
    void executeBefore(Object thiz, Object[] args, Method method, String methodName, StackTraceElement caller) throws InterruptedException;

    ;

    /**
     * Executes after every method
     *
     * @param startTime   start time
     * @param throwable   exception
     * @param args        arguments
     * @param returnValue return value of the method
     * @param thiz        current object
     * @param methodName  name of the method
     * @param method      method signature
     */
    void executeAfter(long startTime, Object thiz, Object[] args, Object returnValue, Throwable throwable,
                      Method method, String methodName, StackTraceElement caller) throws InterruptedException;
}
