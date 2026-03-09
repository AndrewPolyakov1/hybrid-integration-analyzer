package ru.itmo.analyzer.specification.parser;

import ru.itmo.analyzer.specification.parser.error.ParseException;
import ru.itmo.analyzer.specification.parser.model.tokenizer.Token;
import ru.itmo.analyzer.specification.parser.model.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ── Лексер ─────────────────────────────────────────────────────
public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("libsl", TokenType.LIBSL),
            Map.entry("library", TokenType.LIBRARY),
            Map.entry("version", TokenType.VERSION),
            Map.entry("types", TokenType.TYPES),
            Map.entry("automaton", TokenType.AUTOMATON),
            Map.entry("initstate", TokenType.INITSTATE),
            Map.entry("state", TokenType.STATE),
            Map.entry("var", TokenType.VAR),
            Map.entry("shift", TokenType.SHIFT),
            Map.entry("fun", TokenType.FUN),
            Map.entry("requires", TokenType.REQUIRES),
            Map.entry("true", TokenType.TRUE),
            Map.entry("false", TokenType.FALSE)
    );

    private final String source;
    private int pos;
    private int line = 1;
    private int col = 1;

    public Lexer(String source) {
        this.source = source;
    }

    private static Token tok(TokenType t, String v, int l, int c) {
        return new Token(t, v, l, c);
    }

    // ── Основной метод ─────────────────────────────────────────
    public List<Token> tokenize() {
        var tokens = new ArrayList<Token>();
        while (pos < source.length()) {
            skipWhitespaceAndComments();
            if (pos >= source.length()) break;
            tokens.add(readToken());
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    // ── Чтение одного токена ───────────────────────────────────
    private Token readToken() {
        int sl = line, sc = col;
        char c = peek();

        return switch (c) {
            case '{' -> {
                advance();
                yield tok(TokenType.LBRACE, "{", sl, sc);
            }
            case '}' -> {
                advance();
                yield tok(TokenType.RBRACE, "}", sl, sc);
            }
            case '(' -> {
                advance();
                yield tok(TokenType.LPAREN, "(", sl, sc);
            }
            case ')' -> {
                advance();
                yield tok(TokenType.RPAREN, ")", sl, sc);
            }
            case ';' -> {
                advance();
                yield tok(TokenType.SEMICOLON, ";", sl, sc);
            }
            case ':' -> {
                advance();
                yield tok(TokenType.COLON, ":", sl, sc);
            }
            case '=' -> {
                advance();
                yield tok(TokenType.EQUALS, "=", sl, sc);
            }
            case ',' -> {
                advance();
                yield tok(TokenType.COMMA, ",", sl, sc);
            }
            case '!' -> {
                advance();
                yield tok(TokenType.EXCLAMATION, "!", sl, sc);
            }
            case '-' -> readArrow(sl, sc);
            case '"' -> readString(sl, sc);
            default -> {
                if (Character.isLetter(c) || c == '_')
                    yield readIdentifier(sl, sc);
                if (Character.isDigit(c))
                    yield readNumber(sl, sc);
                throw new ParseException("Unexpected character: '" + c + "'", sl, sc);
            }
        };
    }

    // ── Стрелка -> ─────────────────────────────────────────────
    private Token readArrow(int sl, int sc) {
        advance(); // '-'
        if (pos < source.length() && peek() == '>') {
            advance(); // '>'
            return tok(TokenType.ARROW, "->", sl, sc);
        }
        throw new ParseException("Expected '>' after '-'", sl, sc);
    }

    // ── Строковый литерал ──────────────────────────────────────
    private Token readString(int sl, int sc) {
        advance(); // opening "
        var sb = new StringBuilder();
        while (pos < source.length() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (pos >= source.length())
                    throw new ParseException("Unterminated escape in string", line, col);
                sb.append(switch (peek()) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '"' -> '"';
                    default -> peek();
                });
            } else {
                sb.append(peek());
            }
            advance();
        }
        if (pos >= source.length())
            throw new ParseException("Unterminated string literal", sl, sc);
        advance(); // closing "
        return tok(TokenType.STRING_LITERAL, sb.toString(), sl, sc);
    }

    // ── Идентификатор / ключевое слово ─────────────────────────
    // Идентификаторы могут содержать точки (qualified names: custom.lib.File)
    private Token readIdentifier(int sl, int sc) {
        var sb = new StringBuilder();
        while (pos < source.length()
                && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '.')) {
            sb.append(peek());
            advance();
        }
        String word = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);
        return tok(type, word, sl, sc);
    }

    // ── Целочисленный литерал ──────────────────────────────────
    private Token readNumber(int sl, int sc) {
        var sb = new StringBuilder();
        while (pos < source.length() && Character.isDigit(peek())) {
            sb.append(peek());
            advance();
        }
        return tok(TokenType.INT_LITERAL, sb.toString(), sl, sc);
    }

    // ── Пропуск пробелов и комментариев ────────────────────────
    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                // однострочный комментарий
                while (pos < source.length() && peek() != '\n') advance();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                // многострочный комментарий
                advance();
                advance();
                while (pos + 1 < source.length()
                        && !(peek() == '*' && source.charAt(pos + 1) == '/'))
                    advance();
                if (pos + 1 < source.length()) {
                    advance();
                    advance();
                }
            } else {
                break;
            }
        }
    }

    // ── Вспомогательные методы ─────────────────────────────────
    private char peek() {
        return source.charAt(pos);
    }

    private void advance() {
        if (pos < source.length()) {
            if (source.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }
}