package ru.itmo.analyser.specification.parser;

import ru.itmo.analyser.specification.parser.error.ParseException;
import ru.itmo.analyser.specification.parser.model.tokenizer.Token;
import ru.itmo.analyser.specification.parser.model.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A lexical analyzer (lexer) that converts a LibSL source string
 * into a sequence of {@link Token tokens}.
 *
 * <p>The lexer recognizes keywords defined by the LibSL specification,
 * identifiers (including dot-separated qualified names), integer and
 * string literals, single-character punctuation, the arrow operator
 * ({@code ->}), as well as single-line ({@code //}) and multi-line
 * ({@code /* ... * /}) comments.
 *
 * <p>Usage example:
 * <pre>{@code
 *     Lexer lexer = new Lexer(sourceText);
 *     List<Token> tokens = lexer.tokenize();
 * }</pre>
 *
 * @see Token
 * @see TokenType
 */
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

    /**
     * The source text being tokenized.
     */
    private final String source;

    /**
     * Current position (index) in the source string.
     */
    private int pos;

    /**
     * Current line number (1-based).
     */
    private int line = 1;

    /**
     * Current column number (1-based).
     */
    private int col = 1;

    /**
     * Constructs a new lexer for the given source text.
     *
     * @param source the source text to tokenize; must not be {@code null}
     */
    public Lexer(String source) {
        this.source = source;
    }

    /**
     * Creates a new {@link Token} with the specified type, value, and
     * source location.
     *
     * @param type  the token type
     * @param value the textual value of the token
     * @param line  the line number where the token starts
     * @param col   the column number where the token starts
     * @return a new token instance
     */
    private static Token token(TokenType type, String value, int line, int col) {
        return new Token(type, value, line, col);
    }

    /**
     * Tokenizes the entire source text and returns the resulting list of tokens.
     *
     * <p>The returned list always ends with a token of type
     * {@link TokenType#EOF EOF}.
     *
     * @return an unmodifiable-style list of tokens
     * @throws ParseException if an unexpected character or malformed
     *                        literal is encountered
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < source.length()) {
            skipWhitespaceAndComments();
            if (pos >= source.length()) {
                break;
            }
            tokens.add(readToken());
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    /**
     * Reads and returns the next token starting at the current position.
     *
     * @return the next token
     * @throws ParseException if the current character does not start
     *                        any valid token
     */
    private Token readToken() {
        int startLine = line;
        int startCol = col;
        char c = peek();

        return switch (c) {
            case '{' -> {
                advance();
                yield token(TokenType.LBRACE, "{", startLine, startCol);
            }
            case '}' -> {
                advance();
                yield token(TokenType.RBRACE, "}", startLine, startCol);
            }
            case '(' -> {
                advance();
                yield token(TokenType.LPAREN, "(", startLine, startCol);
            }
            case ')' -> {
                advance();
                yield token(TokenType.RPAREN, ")", startLine, startCol);
            }
            case ';' -> {
                advance();
                yield token(TokenType.SEMICOLON, ";", startLine, startCol);
            }
            case ':' -> {
                advance();
                yield token(TokenType.COLON, ":", startLine, startCol);
            }
            case '=' -> {
                advance();
                yield token(TokenType.EQUALS, "=", startLine, startCol);
            }
            case ',' -> {
                advance();
                yield token(TokenType.COMMA, ",", startLine, startCol);
            }
            case '!' -> {
                advance();
                yield token(TokenType.EXCLAMATION, "!", startLine, startCol);
            }
            case '-' -> readArrow(startLine, startCol);
            case '"' -> readStringLiteral(startLine, startCol);
            default -> {
                if (Character.isLetter(c) || c == '_') {
                    yield readIdentifierOrKeyword(startLine, startCol);
                }
                if (Character.isDigit(c)) {
                    yield readIntLiteral(startLine, startCol);
                }
                throw new ParseException(
                        "Unexpected character: '" + c + "'", startLine, startCol);
            }
        };
    }

    /**
     * Reads an arrow token ({@code ->}).
     *
     * <p>The leading {@code '-'} has already been identified by the caller.
     *
     * @param startLine the line where the token starts
     * @param startCol  the column where the token starts
     * @return the arrow token
     * @throws ParseException if {@code '>'} does not follow {@code '-'}
     */
    private Token readArrow(int startLine, int startCol) {
        advance();
        if (pos < source.length() && peek() == '>') {
            advance();
            return token(TokenType.ARROW, "->", startLine, startCol);
        }
        throw new ParseException("Expected '>' after '-'", startLine, startCol);
    }

    /**
     * Reads a double-quoted string literal, handling escape sequences
     * {@code \n}, {@code \t}, {@code \\}, and {@code \"}.
     *
     * @param startLine the line where the opening quote appears
     * @param startCol  the column where the opening quote appears
     * @return the string literal token (value does not include quotes)
     * @throws ParseException if the string is not properly terminated
     */
    private Token readStringLiteral(int startLine, int startCol) {
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (pos >= source.length()) {
                    throw new ParseException(
                            "Unterminated escape in string", line, col);
                }
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
        if (pos >= source.length()) {
            throw new ParseException(
                    "Unterminated string literal", startLine, startCol);
        }
        advance();
        return token(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol);
    }

    /**
     * Reads an identifier or keyword token.
     *
     * <p>Identifiers may contain letters, digits, underscores, and dots
     * (to support qualified names such as {@code custom.lib.File}).
     * If the resulting text matches a reserved keyword, the
     * corresponding {@link TokenType} is used instead of
     * {@link TokenType#IDENTIFIER}.
     *
     * @param startLine the line where the token starts
     * @param startCol  the column where the token starts
     * @return the identifier or keyword token
     */
    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length()
                && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '.')) {
            sb.append(peek());
            advance();
        }
        String word = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);
        return token(type, word, startLine, startCol);
    }

    /**
     * Reads a non-negative integer literal consisting of consecutive
     * decimal digits.
     *
     * @param startLine the line where the token starts
     * @param startCol  the column where the token starts
     * @return the integer literal token
     */
    private Token readIntLiteral(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && Character.isDigit(peek())) {
            sb.append(peek());
            advance();
        }
        return token(TokenType.INT_LITERAL, sb.toString(), startLine, startCol);
    }

    /**
     * Advances the current position past any whitespace characters and
     * comments (both single-line {@code //} and multi-line
     * {@code /* ... * /}).
     */
    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
            } else if (c == '/' && pos + 1 < source.length()
                    && source.charAt(pos + 1) == '/') {
                skipSingleLineComment();
            } else if (c == '/' && pos + 1 < source.length()
                    && source.charAt(pos + 1) == '*') {
                skipMultiLineComment();
            } else {
                break;
            }
        }
    }

    /**
     * Skips characters until the end of the current line.
     * Assumes the position is at the leading {@code '/'}.
     */
    private void skipSingleLineComment() {
        while (pos < source.length() && peek() != '\n') {
            advance();
        }
    }

    /**
     * Skips characters until the closing {@code * /} sequence is found.
     * Assumes the position is at the leading {@code '/'}.
     */
    private void skipMultiLineComment() {
        advance();
        advance();
        while (pos + 1 < source.length()
                && !(peek() == '*' && source.charAt(pos + 1) == '/')) {
            advance();
        }
        if (pos + 1 < source.length()) {
            advance();
            advance();
        }
    }

    /**
     * Returns the character at the current position without advancing.
     *
     * @return the current character
     */
    private char peek() {
        return source.charAt(pos);
    }

    /**
     * Advances the current position by one character, updating the
     * line and column counters accordingly.
     */
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