package com.github.jtdegraaf.jpql2sql.parser

/**
 * Shared mutable token-navigation state used by all clause parsers.
 *
 * Holds the token list produced by [JpqlLexer] and provides utility
 * methods for matching, advancing, and expecting tokens.
 */
class ParseContext(input: String) {

    val tokens: List<Token> = JpqlLexer(input).tokens
    var pos: Int = 0
        private set

    val current: Token get() = tokens[pos]

    // ─────────────── Token navigation ───────────────────

    fun check(type: TokenType): Boolean = current.type == type

    fun match(type: TokenType): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    fun advance(): Token {
        val token = current
        if (pos < tokens.size - 1) pos++
        return token
    }

    fun expect(type: TokenType): Token {
        if (!check(type)) {
            throw parseError("Expected $type but found ${current.type}")
        }
        return advance()
    }

    fun peekNext(): Token? = if (pos + 1 < tokens.size) tokens[pos + 1] else null

    // ──────────── Identifier helpers ────────────────────

    fun expectIdentifier(): String {
        if (!check(TokenType.IDENTIFIER)) {
            throw parseError("Expected identifier but found ${current.type}")
        }
        return advance().text
    }

    /**
     * Like [expectIdentifier] but also accepts keyword tokens as identifiers.
     * Used where entity names, aliases, or field names may collide with JPQL
     * keywords (e.g. entity named "Order" lexes as ORDER token).
     */
    fun expectIdentifierOrKeyword(): String {
        if (check(TokenType.IDENTIFIER) || current.type.isKeyword()) {
            return advance().text
        }
        throw parseError("Expected identifier but found ${current.type}")
    }

    // ────────────── Keyword classification ──────────────

    /**
     * Returns true if the token type is a structural JPQL clause keyword that
     * should not be consumed as an alias or entity name in ambiguous positions.
     */
    fun isClauseKeyword(type: TokenType): Boolean = type in CLAUSE_KEYWORDS

    // ────────────── Error handling & recovery ──────────────────────

    fun parseError(message: String): JpqlParseException =
        JpqlParseException("$message at position ${current.position}")

    /**
     * Collects all tokens from the current position until a clause keyword is found.
     * Returns the collected tokens as a single string (preserving original text).
     * Used for resilient parsing when unexpected content is encountered.
     */
    fun collectUntilClauseKeyword(): String {
        val collected = StringBuilder()
        while (!check(TokenType.END_OF_FILE) && !isClauseKeyword(current.type)) {
            if (collected.isNotEmpty()) collected.append(" ")
            collected.append(current.text)
            advance()
        }
        return collected.toString()
    }

    companion object {
        /** Structural keywords that start JPQL clauses and should not be consumed as aliases.
         *  RPAREN is included to properly terminate subquery parsing. */
        private val CLAUSE_KEYWORDS = setOf(
            TokenType.SELECT, TokenType.FROM, TokenType.WHERE,
            TokenType.JOIN, TokenType.INNER, TokenType.LEFT, TokenType.RIGHT,
            TokenType.ORDER, TokenType.GROUP, TokenType.HAVING,
            TokenType.ON, TokenType.FETCH, TokenType.OFFSET,
            TokenType.END_OF_FILE, TokenType.RIGHT_PARENTHESES
        )
    }
}

