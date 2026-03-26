package ru.itmo.analyzer.specification.parser.model.tokenizer;

/**
 * Token from the parsed text
 *
 * @param type   type
 * @param value  value
 * @param line   line number
 * @param column column
 */
public record Token(TokenType type, String value, int line, int column) {
    @Override
    public String toString() {
        return "%s('%s') at %d:%d".formatted(type, value, line, column);
    }
}
