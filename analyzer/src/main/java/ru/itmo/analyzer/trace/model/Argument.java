package ru.itmo.analyzer.trace.model;

/**
 * Аргумент метода
 *
 * @param type  тип
 * @param value значение
 */
public record Argument(
        TypeInfo type,
        ValueInfo value
) {
}
