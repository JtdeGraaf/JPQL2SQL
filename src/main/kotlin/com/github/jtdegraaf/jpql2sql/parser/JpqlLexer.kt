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

enum class TokenType {
    // Keywords
    SELECT, DISTINCT, FROM, WHERE, AND, OR, NOT,
    IN, BETWEEN, LIKE, IS, NULL, TRUE, FALSE,
    JOIN, INNER, LEFT, RIGHT, OUTER, FULL, CROSS, ON, FETCH, NEXT, ROW, ROWS, ONLY, OFFSET,
    ORDER, BY, ASC, DESC, NULLS, FIRST, LAST,
    GROUP, HAVING,
    COUNT, SUM, AVG, MIN, MAX,
    NEW, AS, CASE, WHEN, THEN, ELSE, END,
    MEMBER, OF, ESCAPE,

    // Functions
    UPPER, LOWER, TRIM, LENGTH, CONCAT, SUBSTRING, LOCATE,
    ABS, SQRT, MOD, SIZE, INDEX,
    CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP,
    COALESCE, NULLIF, TREAT, EMPTY, EXISTS, FUNCTION, CAST,
    EXTRACT, YEAR, MONTH, DAY, HOUR, MINUTE, SECOND,
    LEADING, TRAILING, BOTH,

    // Operators
    EQUALS, NOT_EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
    PLUS, MINUS, STAR, SLASH, CONCAT_OP,

    // Punctuation
    LEFT_PARENTHESES, RIGHT_PARENTHESES, COMMA, DOT,

    // Literals and identifiers
    IDENTIFIER, STRING_LITERAL, NUMBER_LITERAL,
    NAMED_PARAM, POSITIONAL_PARAM,

    // Special
    END_OF_FILE, UNKNOWN;

    /**
     * Returns true if this token type is a keyword or function name that could
     * also be used as an entity name or field name (e.g. "Order", "Group", "Index").
     */
    fun isKeyword(): Boolean = this !in NON_KEYWORD_TOKENS

    companion object {
        private val NON_KEYWORD_TOKENS = setOf(
            // Operators
            EQUALS, NOT_EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, PLUS, MINUS, STAR, SLASH, CONCAT_OP,
            // Punctuation
            LEFT_PARENTHESES, RIGHT_PARENTHESES, COMMA, DOT,
            // Literals and identifiers
            IDENTIFIER, STRING_LITERAL, NUMBER_LITERAL,
            NAMED_PARAM, POSITIONAL_PARAM,
            // Special
            END_OF_FILE, UNKNOWN
        )
    }
}
