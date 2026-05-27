package ru.itmo.analyser.specification.parser.model.ast;

import java.util.List;

/**
 * Определение конечного автомата
 *
 * @param name      название
 * @param typeName  тип
 * @param states    состояния
 * @param variables переменные
 * @param shifts    переходы
 * @param functions функции при переходе
 * @author andrew
 */
public record AutomatonDeclaration(
        String name,
        String typeName,
        List<StateDeclaration> states,
        List<VariableDeclaration> variables,
        List<ShiftDeclaration> shifts,
        List<FunctionDeclaration> functions
) {
}
