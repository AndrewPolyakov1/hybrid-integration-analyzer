package ru.itmo.analyser.specification.parser.model.ast;

/**
 * Определение параметра
 *
 * @param name имя параметра
 * @param type тип параметра
 */
public record Parameter(String name, String type) {
}
