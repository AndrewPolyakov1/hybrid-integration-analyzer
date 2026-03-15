package ru.itmo.analyzer.trace.model;

import java.util.List;

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
        List<Argument> arguments
) {
    public boolean isEnter() {
        return phase.equals(Phase.ENTER);
    }
}
