package ru.itmo.analyzer.specification.parser.model.ast;

import java.util.List;

/**
 * Определение перехода
 *
 * @param fromState начальное состояние
 * @param toState   конечное перехода
 * @param functions функции
 */
public record ShiftDeclaration(
        String fromState,
        String toState,
        List<String> functions
) {
}
