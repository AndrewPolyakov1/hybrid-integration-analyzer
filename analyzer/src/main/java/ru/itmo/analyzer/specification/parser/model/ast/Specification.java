package ru.itmo.analyzer.specification.parser.model.ast;

import java.util.List;

/**
 * Определение параметров спецификации
 *
 * @param libslVersion   версия libSl
 * @param libraryName    название библиотеки
 * @param libraryVersion версия библиотеки
 * @param types          типы, определяемые в библиотеке
 * @param automata       автоматы
 */
public record Specification(
        String libslVersion,
        String libraryName,
        String libraryVersion,
        List<TypeDeclaration> types,
        List<AutomatonDeclaration> automata
) {
}
