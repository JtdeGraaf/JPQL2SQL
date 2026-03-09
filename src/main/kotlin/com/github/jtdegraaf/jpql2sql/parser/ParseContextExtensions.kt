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

/**
 * Parses an aggregate function keyword (COUNT, SUM, AVG, MIN, MAX).
 * Advances the token stream if successful.
 * @return The parsed [AggregateFunction]
 * @throws JpqlParseException if current token is not an aggregate function
 */
fun ParseContext.parseAggregateFunction(): AggregateFunction {
    val func = when (current.type) {
        TokenType.COUNT -> AggregateFunction.COUNT
        TokenType.SUM -> AggregateFunction.SUM
        TokenType.AVG -> AggregateFunction.AVG
        TokenType.MIN -> AggregateFunction.MIN
        TokenType.MAX -> AggregateFunction.MAX
        else -> throw parseError("Expected aggregate function")
    }
    advance()
    return func
}

/**
 * Parses a comma-separated list of items until the end token is reached.
 * If the end token is found immediately, returns an empty list.
 * @param endToken The token type that marks the end of the list (default: RIGHT_PARENTHESES)
 * @param parser Lambda to parse each individual item
 * @return List of parsed items
 */
inline fun <T> ParseContext.parseCommaSeparatedList(
    endToken: TokenType = TokenType.RIGHT_PARENTHESES,
    parser: () -> T
): List<T> {
    val items = mutableListOf<T>()
    if (!check(endToken)) {
        do { items.add(parser()) } while (match(TokenType.COMMA))
    }
    return items
}

/**
 * Attempts to parse an arithmetic operator (+, -, *, /, ||).
 * Advances the token stream if successful.
 * @return The parsed [BinaryOperator] or null if no operator found
 */
fun ParseContext.tryParseArithmeticOperator(): BinaryOperator? = when {
    match(TokenType.PLUS) -> BinaryOperator.ADD
    match(TokenType.MINUS) -> BinaryOperator.SUBTRACT
    match(TokenType.STAR) -> BinaryOperator.MULTIPLY
    match(TokenType.SLASH) -> BinaryOperator.DIVIDE
    match(TokenType.CONCAT_OP) -> BinaryOperator.CONCAT
    else -> null
}

/**
 * Attempts to parse a comparison operator (=, <>, <, <=, >, >=).
 * Advances the token stream if successful.
 * @return The parsed [BinaryOperator] or null if no operator found
 */
fun ParseContext.tryParseComparisonOperator(): BinaryOperator? = when {
    match(TokenType.EQUALS) -> BinaryOperator.EQ
    match(TokenType.NOT_EQUALS) -> BinaryOperator.NE
    match(TokenType.LESS_THAN) -> BinaryOperator.LT
    match(TokenType.LESS_THAN_OR_EQUAL) -> BinaryOperator.LE
    match(TokenType.GREATER_THAN) -> BinaryOperator.GT
    match(TokenType.GREATER_THAN_OR_EQUAL) -> BinaryOperator.GE
    else -> null
}

/**
 * Parses an alias with a fallback to a default value.
 * Pattern: [AS identifier] | identifier (if not a clause keyword)
 * @param allowKeywords If true, also accepts non-clause keywords as aliases
 * @param defaultAlias Lambda that provides the default alias if none is specified
 * @return The parsed alias or the default
 */
inline fun ParseContext.parseAliasOrDefault(
    allowKeywords: Boolean = false,
    defaultAlias: () -> String
): String {
    if (match(TokenType.AS)) {
        return if (allowKeywords) expectIdentifierOrKeyword() else expectIdentifier()
    }
    val canBeAlias = check(TokenType.IDENTIFIER) ||
        (allowKeywords && current.type.isKeyword() && !current.type.isClauseKeyword())
    if (canBeAlias) {
        return if (allowKeywords) expectIdentifierOrKeyword() else expectIdentifier()
    }
    return defaultAlias()
}

/**
 * Parses an EXTRACT field (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND).
 * @return The parsed [ExtractField]
 * @throws JpqlParseException if current token is not a valid extract field
 */
fun ParseContext.parseExtractField(): ExtractField = when {
    match(TokenType.YEAR) -> ExtractField.YEAR
    match(TokenType.MONTH) -> ExtractField.MONTH
    match(TokenType.DAY) -> ExtractField.DAY
    match(TokenType.HOUR) -> ExtractField.HOUR
    match(TokenType.MINUTE) -> ExtractField.MINUTE
    match(TokenType.SECOND) -> ExtractField.SECOND
    else -> throw parseError("Expected date/time field (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND)")
}

/**
 * Parses an optional TRIM mode (LEADING, TRAILING, BOTH).
 * @return The parsed [TrimMode] or BOTH as default
 */
fun ParseContext.parseTrimMode(): TrimMode = when {
    match(TokenType.LEADING) -> TrimMode.LEADING
    match(TokenType.TRAILING) -> TrimMode.TRAILING
    match(TokenType.BOTH) -> TrimMode.BOTH
    else -> TrimMode.BOTH
}
