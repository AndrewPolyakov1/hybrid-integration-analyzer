package ru.itmo.analyzer.specification.parser;

import ru.itmo.analyzer.specification.parser.error.ParseException;
import ru.itmo.analyzer.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Expression;
import ru.itmo.analyzer.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Parameter;
import ru.itmo.analyzer.specification.parser.model.ast.Requirement;
import ru.itmo.analyzer.specification.parser.model.ast.ShiftDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Specification;
import ru.itmo.analyzer.specification.parser.model.ast.StateDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Statement;
import ru.itmo.analyzer.specification.parser.model.ast.TypeDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.VariableDeclaration;
import ru.itmo.analyzer.specification.parser.model.tokenizer.Token;
import ru.itmo.analyzer.specification.parser.model.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A recursive descent parser for the LibSL specification language.
 * <p>
 * This parser consumes a sequence of {@link Token}s and constructs an Abstract Syntax Tree (AST)
 * representing the {@link Specification}. It expects a syntactically valid sequence of tokens
 * and will throw a {@link ParseException} upon encountering invalid grammar.
 *
 * @see Specification
 * @see Lexer
 */
public class LibSlParser {

    private final List<Token> tokens;
    private int pos = 0;

    /**
     * Constructs a new parser with the specified list of tokens.
     *
     * @param tokens the list of tokens to parse
     * @throws NullPointerException if {@code tokens} is null
     */
    public LibSlParser(List<Token> tokens) {
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
    }

    /**
     * Constructs a new parser by tokenizing the provided LibSL specification string.
     *
     * @param libSlSpec the LibSL specification source code
     * @throws NullPointerException if {@code libSlSpec} is null
     */
    public LibSlParser(String libSlSpec) {
        Objects.requireNonNull(libSlSpec, "libSlSpec must not be null");
        var lexer = new Lexer(libSlSpec);
        this.tokens = lexer.tokenize();
    }

    /**
     * Parses the entire token sequence into a {@link Specification}.
     * <p>
     * The specification consists of a LibSL version declaration, a library declaration,
     * an optional types block, and zero or more automaton declarations.
     *
     * @return the parsed AST root representing the specification
     * @throws ParseException if a syntax error is encountered
     */
    public Specification parse() {
        String libslVersion = parseLibslVersion();
        String[] lib = parseLibraryDecl();

        List<TypeDeclaration> types = check(TokenType.TYPES)
                ? parseTypesBlock()
                : List.of();

        var automata = new ArrayList<AutomatonDeclaration>();
        while (!check(TokenType.EOF)) {
            automata.add(parseAutomaton());
        }

        return new Specification(libslVersion, lib[0], lib[1], types, automata);
    }

    /**
     * Parses the LibSL version declaration.
     * Expected format: {@code libsl "x.y.z";}
     *
     * @return the version string
     */
    private String parseLibslVersion() {
        expect(TokenType.LIBSL);
        String ver = expect(TokenType.STRING_LITERAL).value();
        expect(TokenType.SEMICOLON);
        return ver;
    }

    /**
     * Parses the library declaration.
     * Expected format: {@code library Name version "x.y.z";}
     *
     * @return an array of two strings where index 0 is the library name and index 1 is the version
     */
    private String[] parseLibraryDecl() {
        expect(TokenType.LIBRARY);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.VERSION);
        String ver = expect(TokenType.STRING_LITERAL).value();
        expect(TokenType.SEMICOLON);
        return new String[]{name, ver};
    }

    /**
     * Parses the types block containing type mappings.
     * Expected format: {@code types { Name (impl); ... }}
     *
     * @return a list of parsed type declarations
     */
    private List<TypeDeclaration> parseTypesBlock() {
        expect(TokenType.TYPES);
        expect(TokenType.LBRACE);

        var types = new ArrayList<TypeDeclaration>();
        while (!check(TokenType.RBRACE)) {
            String name = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.LPAREN);
            String impl = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.RPAREN);

            if (check(TokenType.SEMICOLON)) {
                advance();
            }

            types.add(new TypeDeclaration(name, impl));
        }
        expect(TokenType.RBRACE);
        return types;
    }

    /**
     * Parses an automaton declaration, including its states, variables, shifts, and functions.
     * Expected format: {@code automaton qualified.Name : TypeName { ... }}
     *
     * @return the parsed automaton declaration
     */
    private AutomatonDeclaration parseAutomaton() {
        expect(TokenType.AUTOMATON);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String typeName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LBRACE);

        var states = new ArrayList<StateDeclaration>();
        var variables = new ArrayList<VariableDeclaration>();
        var shifts = new ArrayList<ShiftDeclaration>();
        var functions = new ArrayList<FunctionDeclaration>();

        while (!check(TokenType.RBRACE)) {
            switch (current().type()) {
                case INITSTATE -> {
                    advance();
                    states.add(new StateDeclaration(expect(TokenType.IDENTIFIER).value(), true));
                    expect(TokenType.SEMICOLON);
                }
                case STATE -> {
                    advance();
                    states.add(new StateDeclaration(expect(TokenType.IDENTIFIER).value(), false));
                    expect(TokenType.SEMICOLON);
                }
                case VAR -> variables.add(parseVariable());
                case SHIFT -> shifts.add(parseShift());
                case FUN -> functions.add(parseFunction());
                default -> throw new ParseException("Unexpected token in automaton body", current());
            }
        }
        expect(TokenType.RBRACE);

        return new AutomatonDeclaration(name, typeName, states, variables, shifts, functions);
    }

    /**
     * Parses a variable declaration.
     * Expected format: {@code var name: type = expr;}
     *
     * @return the parsed variable declaration
     */
    private VariableDeclaration parseVariable() {
        expect(TokenType.VAR);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String type = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.EQUALS);
        Expression init = parseExpression();
        expect(TokenType.SEMICOLON);
        return new VariableDeclaration(name, type, init);
    }

    /**
     * Parses a state shift transition.
     * Expected format: {@code shift From -> To(func1, func2, ...);}
     *
     * @return the parsed shift declaration
     */
    private ShiftDeclaration parseShift() {
        expect(TokenType.SHIFT);
        String from = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.ARROW);
        String to = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LPAREN);

        var funcs = new ArrayList<String>();
        if (!check(TokenType.RPAREN)) {
            funcs.add(expect(TokenType.IDENTIFIER).value());
            while (check(TokenType.COMMA)) {
                advance();
                funcs.add(expect(TokenType.IDENTIFIER).value());
            }
        }
        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new ShiftDeclaration(from, to, funcs);
    }

    /**
     * Parses a function declaration, including its parameters, requirements, and body.
     * Expected format: {@code fun name(params) requires ...: ... { body } | ;}
     *
     * @return the parsed function declaration
     */
    private FunctionDeclaration parseFunction() {
        expect(TokenType.FUN);
        String name = expect(TokenType.IDENTIFIER).value();

        expect(TokenType.LPAREN);
        var params = new ArrayList<Parameter>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseParameter());
            }
        }
        expect(TokenType.RPAREN);

        var requirements = new ArrayList<Requirement>();
        while (check(TokenType.REQUIRES)) {
            advance();
            String reqName = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.COLON);
            Expression cond = parseExpression();
            requirements.add(new Requirement(reqName, cond));
        }

        var body = new ArrayList<Statement>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE)) {
                body.add(parseStatement());
            }
            advance();
        } else {
            expect(TokenType.SEMICOLON);
        }

        return new FunctionDeclaration(name, params, requirements, body);
    }

    /**
     * Parses a single function parameter.
     * Expected format: {@code name: Type}
     *
     * @return the parsed parameter
     */
    private Parameter parseParameter() {
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String type = expect(TokenType.IDENTIFIER).value();
        return new Parameter(name, type);
    }

    /**
     * Parses a single statement. Currently supports assignments.
     * Expected format: {@code variable = expression;}
     *
     * @return the parsed statement
     */
    private Statement parseStatement() {
        String varName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.EQUALS);
        Expression value = parseExpression();
        expect(TokenType.SEMICOLON);
        return new Statement.Assignment(varName, value);
    }

    /**
     * Parses a general expression, resolving top-level unary operations if present.
     *
     * @return the parsed expression
     */
    private Expression parseExpression() {
        if (check(TokenType.EXCLAMATION)) {
            advance();
            return new Expression.Not(parseExpression());
        }
        return parsePrimary();
    }

    /**
     * Parses primary expressions (literals, variable references, or parenthesized expressions).
     *
     * @return the parsed primary expression
     * @throws ParseException if a valid expression cannot be parsed
     */
    private Expression parsePrimary() {
        if (check(TokenType.TRUE)) {
            advance();
            return new Expression.BoolLiteral(true);
        }
        if (check(TokenType.FALSE)) {
            advance();
            return new Expression.BoolLiteral(false);
        }
        if (check(TokenType.STRING_LITERAL)) {
            return new Expression.StringLiteral(advance().value());
        }
        if (check(TokenType.INT_LITERAL)) {
            return new Expression.IntLiteral(Integer.parseInt(advance().value()));
        }
        if (check(TokenType.IDENTIFIER)) {
            return new Expression.VarRef(advance().value());
        }
        if (check(TokenType.LPAREN)) {
            advance();
            Expression expr = parseExpression();
            expect(TokenType.RPAREN);
            return expr;
        }

        throw new ParseException("Expected expression", current());
    }

    /**
     * Returns the token at the current position without consuming it.
     *
     * @return the current token, or the last token if EOF is reached
     */
    private Token current() {
        return pos < tokens.size() ? tokens.get(pos) : tokens.getLast();
    }

    /**
     * Checks if the current token matches the specified type.
     *
     * @param type the expected token type
     * @return {@code true} if the current token matches the given type, {@code false} otherwise
     */
    private boolean check(TokenType type) {
        return current().type() == type;
    }

    /**
     * Consumes and returns the current token, advancing the internal pointer.
     *
     * @return the consumed token
     */
    private Token advance() {
        return tokens.get(pos++);
    }

    /**
     * Consumes the current token if it matches the expected type, otherwise throws an exception.
     *
     * @param type the expected token type
     * @return the consumed token
     * @throws ParseException if the current token does not match the expected type
     */
    private Token expect(TokenType type) {
        if (!check(type)) {
            throw new ParseException("Expected " + type, current());
        }
        return advance();
    }
}