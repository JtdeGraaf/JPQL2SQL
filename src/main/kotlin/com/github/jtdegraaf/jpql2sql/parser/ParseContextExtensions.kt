package com.github.jtdegraaf.jpql2sql.parser

/**
 * Extension functions for [ParseContext] that provide common parsing utilities
 * shared across different clause parsers.
 */

/**
 * Parses an optional alias after AS keyword.
 * Pattern: [AS identifier]
 * @return The alias if present, null otherwise
 */
fun ParseContext.parseOptionalAlias(): String? =
    if (match(TokenType.AS)) expectIdentifier() else null

/**
 * Checks if the current token is NOT followed by a specific token type.
 * Used for compound operators like NOT IN, NOT LIKE, NOT BETWEEN, NOT MEMBER.
 * @param type The token type to check for after NOT
 * @return true if current is NOT and next is the specified type
 */
fun ParseContext.checkNotFollowedBy(type: TokenType): Boolean =
    current.type == TokenType.NOT && peekNext()?.type == type

/**
 * Parses a literal expression (string, number, boolean, or null).
 * @return The parsed [LiteralExpression] or null if current token is not a literal
 */
fun ParseContext.parseLiteralExpression(): LiteralExpression? = when {
    check(TokenType.STRING_LITERAL) -> {
        val value = current.text
        advance()
        LiteralExpression(value, LiteralType.STRING)
    }
    check(TokenType.NUMBER_LITERAL) -> {
        val value = current.text.toLongOrNull() ?: current.text.toDoubleOrNull() ?: current.text
        advance()
        LiteralExpression(value, LiteralType.NUMBER)
    }
    match(TokenType.TRUE) -> LiteralExpression(true, LiteralType.BOOLEAN)
    match(TokenType.FALSE) -> LiteralExpression(false, LiteralType.BOOLEAN)
    match(TokenType.NULL) -> LiteralExpression(null, LiteralType.NULL)
    else -> null
}

/**
 * Parses a parameter expression (named :param or positional ?1).
 * @return The parsed [ParameterExpression] or null if current token is not a parameter
 */
fun ParseContext.parseParameterExpression(): ParameterExpression? = when {
    check(TokenType.NAMED_PARAM) -> {
        val name = current.text
        advance()
        ParameterExpression(name, null)
    }
    check(TokenType.POSITIONAL_PARAM) -> {
        val position = current.text.toIntOrNull() ?: 0
        advance()
        ParameterExpression(null, position)
    }
    else -> null
}
