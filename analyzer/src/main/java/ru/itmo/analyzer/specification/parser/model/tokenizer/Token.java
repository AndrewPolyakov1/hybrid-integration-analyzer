package ru.itmo.analyzer.specification.parser.model.tokenizer;

// ── Токен ──────────────────────────────────────────────────────
public record Token(TokenType type, String value, int line, int column) {
    @Override
    public String toString() {
        return "%s('%s') at %d:%d".formatted(type, value, line, column);
    }
}
