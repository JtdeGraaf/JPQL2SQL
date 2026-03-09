package com.github.jtdegraaf.jpql2sql.parser

class JpqlLexer(private val input: String) {
    private var pos = 0
    private var current: Token? = null

    val tokens: List<Token> by lazy { tokenizeAll() }

    private fun tokenizeAll(): List<Token> {
        val result = mutableListOf<Token>()
        while (pos < input.length) {
            skipWhitespace()
            if (pos >= input.length) break
            result.add(readToken())
        }
        result.add(Token(TokenType.END_OF_FILE, "", pos))
        return result
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }

    private fun readToken(): Token {
        val start = pos
        val c = input[pos]

        return when {
            c == '\'' -> readStringLiteral()
            c == ':' -> readNamedParameter()
            c == '?' -> readPositionalParameter()
            c.isDigit() || (c == '-' && pos + 1 < input.length && input[pos + 1].isDigit()) -> readNumber()
            c.isLetter() || c == '_' -> readIdentifierOrKeyword()
            else -> readOperator()
        }
    }

    private fun readStringLiteral(): Token {
        val start = pos
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < input.length) {
            val c = input[pos]
            if (c == '\'') {
                if (pos + 1 < input.length && input[pos + 1] == '\'') {
                    sb.append('\'')
                    pos += 2
                } else {
                    pos++
                    break
                }
            } else {
                sb.append(c)
                pos++
            }
        }
        return Token(TokenType.STRING_LITERAL, sb.toString(), start)
    }

    private fun readNamedParameter(): Token {
        val start = pos
        pos++ // skip ':'
        val name = buildString {
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                append(input[pos++])
            }
        }
        return Token(TokenType.NAMED_PARAM, name, start)
    }

    private fun readPositionalParameter(): Token {
        val start = pos
        pos++ // skip '?'
        val num = buildString {
            while (pos < input.length && input[pos].isDigit()) {
                append(input[pos++])
            }
        }
        return Token(TokenType.POSITIONAL_PARAM, num, start)
    }

    private fun readNumber(): Token {
        val start = pos
        val sb = StringBuilder()
        if (input[pos] == '-') {
            sb.append('-')
            pos++
        }
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            sb.append(input[pos++])
        }
        return Token(TokenType.NUMBER_LITERAL, sb.toString(), start)
    }

    private fun readIdentifierOrKeyword(): Token {
        val start = pos
        val sb = StringBuilder()
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
            sb.append(input[pos++])
        }
        val text = sb.toString()
        val upper = text.uppercase()

        val type = KEYWORDS[upper] ?: TokenType.IDENTIFIER
        return Token(type, text, start)
    }

    private fun readOperator(): Token {
        val start = pos
        val c = input[pos]

        val twoChar = if (pos + 1 < input.length) input.substring(pos, pos + 2) else ""

        return when {
            twoChar == "<>" || twoChar == "!=" -> {
                pos += 2
                Token(TokenType.NOT_EQUALS, twoChar, start)
            }
            twoChar == "<=" -> {
                pos += 2
                Token(TokenType.LESS_THAN_OR_EQUAL, twoChar, start)
            }
            twoChar == ">=" -> {
                pos += 2
                Token(TokenType.GREATER_THAN_OR_EQUAL, twoChar, start)
            }
            twoChar == "||" -> {
                pos += 2
                Token(TokenType.CONCAT_OP, "||", start)
            }
            c == '=' -> {
                pos++
                Token(TokenType.EQUALS, "=", start)
            }
            c == '<' -> {
                pos++
                Token(TokenType.LESS_THAN, "<", start)
            }
            c == '>' -> {
                pos++
                Token(TokenType.GREATER_THAN, ">", start)
            }
            c == '(' -> {
                pos++
                Token(TokenType.LEFT_PARENTHESES, "(", start)
            }
            c == ')' -> {
                pos++
                Token(TokenType.RIGHT_PARENTHESES, ")", start)
            }
            c == ',' -> {
                pos++
                Token(TokenType.COMMA, ",", start)
            }
            c == '.' -> {
                pos++
                Token(TokenType.DOT, ".", start)
            }
            c == '+' -> {
                pos++
                Token(TokenType.PLUS, "+", start)
            }
            c == '-' -> {
                pos++
                Token(TokenType.MINUS, "-", start)
            }
            c == '*' -> {
                pos++
                Token(TokenType.STAR, "*", start)
            }
            c == '/' -> {
                pos++
                Token(TokenType.SLASH, "/", start)
            }
            else -> {
                pos++
                Token(TokenType.UNKNOWN, c.toString(), start)
            }
        }
    }

    companion object {
        private val KEYWORDS = mapOf(
            "SELECT" to TokenType.SELECT,
            "DISTINCT" to TokenType.DISTINCT,
            "FROM" to TokenType.FROM,
            "WHERE" to TokenType.WHERE,
            "AND" to TokenType.AND,
            "OR" to TokenType.OR,
            "NOT" to TokenType.NOT,
            "IN" to TokenType.IN,
            "BETWEEN" to TokenType.BETWEEN,
            "LIKE" to TokenType.LIKE,
            "IS" to TokenType.IS,
            "NULL" to TokenType.NULL,
            "TRUE" to TokenType.TRUE,
            "FALSE" to TokenType.FALSE,
            "JOIN" to TokenType.JOIN,
            "INNER" to TokenType.INNER,
            "LEFT" to TokenType.LEFT,
            "RIGHT" to TokenType.RIGHT,
            "OUTER" to TokenType.OUTER,
            "FULL" to TokenType.FULL,
            "CROSS" to TokenType.CROSS,
            "ON" to TokenType.ON,
            "ORDER" to TokenType.ORDER,
            "BY" to TokenType.BY,
            "ASC" to TokenType.ASC,
            "DESC" to TokenType.DESC,
            "NULLS" to TokenType.NULLS,
            "FIRST" to TokenType.FIRST,
            "LAST" to TokenType.LAST,
            "GROUP" to TokenType.GROUP,
            "HAVING" to TokenType.HAVING,
            "UNION" to TokenType.UNION,
            "INTERSECT" to TokenType.INTERSECT,
            "EXCEPT" to TokenType.EXCEPT,
            "ALL" to TokenType.ALL,
            "COUNT" to TokenType.COUNT,
            "SUM" to TokenType.SUM,
            "AVG" to TokenType.AVG,
            "MIN" to TokenType.MIN,
            "MAX" to TokenType.MAX,
            "NEW" to TokenType.NEW,
            "AS" to TokenType.AS,
            "CASE" to TokenType.CASE,
            "WHEN" to TokenType.WHEN,
            "THEN" to TokenType.THEN,
            "ELSE" to TokenType.ELSE,
            "END" to TokenType.END,
            "MEMBER" to TokenType.MEMBER,
            "OF" to TokenType.OF,
            "FETCH" to TokenType.FETCH,
            "NEXT" to TokenType.NEXT,
            "ROW" to TokenType.ROW,
            "ROWS" to TokenType.ROWS,
            "ONLY" to TokenType.ONLY,
            "OFFSET" to TokenType.OFFSET,
            "ESCAPE" to TokenType.ESCAPE,
            "UPPER" to TokenType.UPPER,
            "LOWER" to TokenType.LOWER,
            "TRIM" to TokenType.TRIM,
            "LENGTH" to TokenType.LENGTH,
            "CONCAT" to TokenType.CONCAT,
            "SUBSTRING" to TokenType.SUBSTRING,
            "LOCATE" to TokenType.LOCATE,
            "ABS" to TokenType.ABS,
            "SQRT" to TokenType.SQRT,
            "MOD" to TokenType.MOD,
            "SIZE" to TokenType.SIZE,
            "INDEX" to TokenType.INDEX,
            "CURRENT_DATE" to TokenType.CURRENT_DATE,
            "CURRENT_TIME" to TokenType.CURRENT_TIME,
            "CURRENT_TIMESTAMP" to TokenType.CURRENT_TIMESTAMP,
            "COALESCE" to TokenType.COALESCE,
            "NULLIF" to TokenType.NULLIF,
            "TREAT" to TokenType.TREAT,
            "EMPTY" to TokenType.EMPTY,
            "EXISTS" to TokenType.EXISTS,
            "FUNCTION" to TokenType.FUNCTION,
            "CAST" to TokenType.CAST,
            "TYPE" to TokenType.TYPE,
            "EXTRACT" to TokenType.EXTRACT,
            "YEAR" to TokenType.YEAR,
            "MONTH" to TokenType.MONTH,
            "DAY" to TokenType.DAY,
            "HOUR" to TokenType.HOUR,
            "MINUTE" to TokenType.MINUTE,
            "SECOND" to TokenType.SECOND,
            "LEADING" to TokenType.LEADING,
            "TRAILING" to TokenType.TRAILING,
            "BOTH" to TokenType.BOTH
        )
    }
}

data class Token(
    val type: TokenType,
    val text: String,
    val position: Int
)

/**
 * Classification categories for token types.
 */
enum class TokenCategory {
    KEYWORD,
    FUNCTION,
    AGGREGATE,
    COMPARISON_OPERATOR,
    ARITHMETIC_OPERATOR,
    PUNCTUATION,
    LITERAL,
    SPECIAL
}

enum class TokenType(val category: TokenCategory) {
    // Keywords
    SELECT(TokenCategory.KEYWORD),
    DISTINCT(TokenCategory.KEYWORD),
    FROM(TokenCategory.KEYWORD),
    WHERE(TokenCategory.KEYWORD),
    AND(TokenCategory.KEYWORD),
    OR(TokenCategory.KEYWORD),
    NOT(TokenCategory.KEYWORD),
    IN(TokenCategory.KEYWORD),
    BETWEEN(TokenCategory.KEYWORD),
    LIKE(TokenCategory.KEYWORD),
    IS(TokenCategory.KEYWORD),
    NULL(TokenCategory.KEYWORD),
    TRUE(TokenCategory.KEYWORD),
    FALSE(TokenCategory.KEYWORD),
    JOIN(TokenCategory.KEYWORD),
    INNER(TokenCategory.KEYWORD),
    LEFT(TokenCategory.KEYWORD),
    RIGHT(TokenCategory.KEYWORD),
    OUTER(TokenCategory.KEYWORD),
    FULL(TokenCategory.KEYWORD),
    CROSS(TokenCategory.KEYWORD),
    ON(TokenCategory.KEYWORD),
    FETCH(TokenCategory.KEYWORD),
    NEXT(TokenCategory.KEYWORD),
    ROW(TokenCategory.KEYWORD),
    ROWS(TokenCategory.KEYWORD),
    ONLY(TokenCategory.KEYWORD),
    OFFSET(TokenCategory.KEYWORD),
    ORDER(TokenCategory.KEYWORD),
    BY(TokenCategory.KEYWORD),
    ASC(TokenCategory.KEYWORD),
    DESC(TokenCategory.KEYWORD),
    NULLS(TokenCategory.KEYWORD),
    FIRST(TokenCategory.KEYWORD),
    LAST(TokenCategory.KEYWORD),
    GROUP(TokenCategory.KEYWORD),
    HAVING(TokenCategory.KEYWORD),
    UNION(TokenCategory.KEYWORD),
    INTERSECT(TokenCategory.KEYWORD),
    EXCEPT(TokenCategory.KEYWORD),
    ALL(TokenCategory.KEYWORD),
    NEW(TokenCategory.KEYWORD),
    AS(TokenCategory.KEYWORD),
    CASE(TokenCategory.KEYWORD),
    WHEN(TokenCategory.KEYWORD),
    THEN(TokenCategory.KEYWORD),
    ELSE(TokenCategory.KEYWORD),
    END(TokenCategory.KEYWORD),
    MEMBER(TokenCategory.KEYWORD),
    OF(TokenCategory.KEYWORD),
    ESCAPE(TokenCategory.KEYWORD),
    EMPTY(TokenCategory.KEYWORD),
    EXISTS(TokenCategory.KEYWORD),
    FUNCTION(TokenCategory.KEYWORD),
    CAST(TokenCategory.KEYWORD),
    TYPE(TokenCategory.KEYWORD),
    EXTRACT(TokenCategory.KEYWORD),
    YEAR(TokenCategory.KEYWORD),
    MONTH(TokenCategory.KEYWORD),
    DAY(TokenCategory.KEYWORD),
    HOUR(TokenCategory.KEYWORD),
    MINUTE(TokenCategory.KEYWORD),
    SECOND(TokenCategory.KEYWORD),
    LEADING(TokenCategory.KEYWORD),
    TRAILING(TokenCategory.KEYWORD),
    BOTH(TokenCategory.KEYWORD),

    // Aggregate functions
    COUNT(TokenCategory.AGGREGATE),
    SUM(TokenCategory.AGGREGATE),
    AVG(TokenCategory.AGGREGATE),
    MIN(TokenCategory.AGGREGATE),
    MAX(TokenCategory.AGGREGATE),

    // Built-in functions (standard function call syntax)
    UPPER(TokenCategory.FUNCTION),
    LOWER(TokenCategory.FUNCTION),
    LENGTH(TokenCategory.FUNCTION),
    CONCAT(TokenCategory.FUNCTION),
    SUBSTRING(TokenCategory.FUNCTION),
    LOCATE(TokenCategory.FUNCTION),
    ABS(TokenCategory.FUNCTION),
    SQRT(TokenCategory.FUNCTION),
    MOD(TokenCategory.FUNCTION),
    SIZE(TokenCategory.FUNCTION),
    INDEX(TokenCategory.FUNCTION),
    CURRENT_DATE(TokenCategory.FUNCTION),
    CURRENT_TIME(TokenCategory.FUNCTION),
    CURRENT_TIMESTAMP(TokenCategory.FUNCTION),
    COALESCE(TokenCategory.FUNCTION),
    NULLIF(TokenCategory.FUNCTION),
    TREAT(TokenCategory.FUNCTION),

    // Special syntax functions (not standard function call syntax - handled specially)
    TRIM(TokenCategory.KEYWORD),  // TRIM(LEADING 'x' FROM expr) - special syntax

    // Comparison operators
    EQUALS(TokenCategory.COMPARISON_OPERATOR),
    NOT_EQUALS(TokenCategory.COMPARISON_OPERATOR),
    LESS_THAN(TokenCategory.COMPARISON_OPERATOR),
    LESS_THAN_OR_EQUAL(TokenCategory.COMPARISON_OPERATOR),
    GREATER_THAN(TokenCategory.COMPARISON_OPERATOR),
    GREATER_THAN_OR_EQUAL(TokenCategory.COMPARISON_OPERATOR),

    // Arithmetic operators
    PLUS(TokenCategory.ARITHMETIC_OPERATOR),
    MINUS(TokenCategory.ARITHMETIC_OPERATOR),
    STAR(TokenCategory.ARITHMETIC_OPERATOR),
    SLASH(TokenCategory.ARITHMETIC_OPERATOR),
    CONCAT_OP(TokenCategory.ARITHMETIC_OPERATOR),

    // Punctuation
    LEFT_PARENTHESES(TokenCategory.PUNCTUATION),
    RIGHT_PARENTHESES(TokenCategory.PUNCTUATION),
    COMMA(TokenCategory.PUNCTUATION),
    DOT(TokenCategory.PUNCTUATION),

    // Literals and identifiers
    IDENTIFIER(TokenCategory.LITERAL),
    STRING_LITERAL(TokenCategory.LITERAL),
    NUMBER_LITERAL(TokenCategory.LITERAL),
    NAMED_PARAM(TokenCategory.LITERAL),
    POSITIONAL_PARAM(TokenCategory.LITERAL),

    // Special
    END_OF_FILE(TokenCategory.SPECIAL),
    UNKNOWN(TokenCategory.SPECIAL);

    /**
     * Returns true if this token type is a keyword or function name that could
     * also be used as an entity name or field name (e.g. "Order", "Group", "Index").
     */
    fun isKeyword(): Boolean = category == TokenCategory.KEYWORD ||
            category == TokenCategory.FUNCTION ||
            category == TokenCategory.AGGREGATE

    /**
     * Returns true if this is a built-in JPQL function token.
     */
    fun isFunction(): Boolean = category == TokenCategory.FUNCTION

    /**
     * Returns true if this is an aggregate function token (COUNT, SUM, AVG, MIN, MAX).
     */
    fun isAggregate(): Boolean = category == TokenCategory.AGGREGATE

    /**
     * Returns true if this is a comparison operator (=, <>, <, <=, >, >=).
     */
    fun isComparisonOperator(): Boolean = category == TokenCategory.COMPARISON_OPERATOR

    /**
     * Returns true if this is an arithmetic operator (+, -, *, /, ||).
     */
    fun isArithmeticOperator(): Boolean = category == TokenCategory.ARITHMETIC_OPERATOR

    /**
     * Returns true if this token can start an expression in a SELECT projection.
     * This includes: CASE, CAST, EXTRACT, TRIM, EXISTS, FUNCTION, literals,
     * parentheses, and built-in functions.
     */
    fun isExpressionStart(): Boolean = this in EXPRESSION_START_TOKENS || isFunction()

    /**
     * Returns true if this is a set operation keyword (UNION, INTERSECT, EXCEPT).
     */
    fun isSetOperator(): Boolean = this in SET_OPERATORS

    /**
     * Returns true if this is ROW or ROWS keyword.
     */
    fun isRowKeyword(): Boolean = this == ROW || this == ROWS

    /**
     * Returns true if this is a value literal (string, number, boolean, null).
     */
    fun isValueLiteral(): Boolean = this in VALUE_LITERALS

    /**
     * Returns true if this is a parameter token (named or positional).
     */
    fun isParameter(): Boolean = this == NAMED_PARAM || this == POSITIONAL_PARAM

    /**
     * Returns true if this is a structural JPQL clause keyword that should not
     * be consumed as an alias or entity name in ambiguous positions.
     */
    fun isClauseKeyword(): Boolean = this in CLAUSE_KEYWORDS

    /**
     * Returns true if this token marks the end of a projection in SELECT clause.
     * Used to detect projection boundaries for error recovery.
     */
    fun isProjectionEnd(): Boolean = this == COMMA || this == FROM || this == END_OF_FILE

    companion object {
        private val EXPRESSION_START_TOKENS = setOf(
            CASE, CAST, EXTRACT, TRIM, EXISTS, FUNCTION,
            NUMBER_LITERAL, STRING_LITERAL, LEFT_PARENTHESES,
            MINUS  // Unary minus: SELECT -1, SELECT -u.amount
        )

        private val SET_OPERATORS = setOf(UNION, INTERSECT, EXCEPT)

        private val VALUE_LITERALS = setOf(
            STRING_LITERAL, NUMBER_LITERAL, TRUE, FALSE, NULL
        )

        /** Structural keywords that start JPQL clauses and should not be consumed as aliases. */
        private val CLAUSE_KEYWORDS = setOf(
            SELECT, FROM, WHERE,
            JOIN, INNER, LEFT, RIGHT, FULL, CROSS,
            ORDER, GROUP, HAVING,
            ON, FETCH, OFFSET,
            UNION, INTERSECT, EXCEPT,
            END_OF_FILE, RIGHT_PARENTHESES
        )
    }
}
