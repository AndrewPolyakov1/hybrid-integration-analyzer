package ru.itmo.analyser.specification.parser.error;

import ru.itmo.analyser.specification.parser.model.tokenizer.Token;

/**
 * Ошибка парсинга спецификации LibSL
 *
 * @author andrew
 */
public class ParseException extends RuntimeException {
    private final int line;
    private final int column;

    public ParseException(String message, int line, int column) {
        super("Error parsing specification: [%d:%d] %s".formatted(line, column, message));
        this.line = line;
        this.column = column;
    }

    public ParseException(String message, Token token) {
        this("%s, got %s('%s')".formatted(message, token.type(), token.value()),
                token.line(), token.column());
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}