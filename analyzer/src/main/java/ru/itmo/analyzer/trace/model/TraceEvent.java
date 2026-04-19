package ru.itmo.analyzer.trace.model;

import java.util.List;
import java.util.Map;

/**
 * Представление объекта вызова метода
 *
 * @author - Andrew Polyakov
 */
public record TraceEvent(
        Timestamp timestamp,
        Phase phase,
        ThreadInfo thread,
        ClassInfo classInfo,
        MethodInfo method,
        MethodSignature signature,
        ClassInfo instanceClass,
        List<Argument> arguments,
        ClassInfo callerClass,
        Map<String, Object> callerObject,
        MethodInfo callerMethod,
        Integer lineNumber
) {
    public boolean isEnter() {
        return phase.equals(Phase.ENTER);
    }
}
