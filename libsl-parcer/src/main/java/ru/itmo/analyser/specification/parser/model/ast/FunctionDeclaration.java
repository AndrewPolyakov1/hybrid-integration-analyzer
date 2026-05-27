package ru.itmo.analyser.specification.parser.model.ast;

import java.util.List;

/**
 * Определение функции
 *
 * @param name         название функции
 * @param parameters   параметры функции
 * @param requirements требования к функции
 * @param body         тело функции
 * @author andrew
 */
public record FunctionDeclaration(
        String name,
        List<Parameter> parameters,
        List<Requirement> requirements,
        List<Statement> body
) {
}
