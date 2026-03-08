package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses function call expressions including built-in JPQL functions and special syntax functions.
 *
 * Handles:
 * - Standard functions: UPPER(x), LOWER(x), CONCAT(x, y), etc.
 * - Native function syntax: FUNCTION('native_name', arg1, arg2)
 * - CAST(expression AS type)
 * - EXTRACT(field FROM source)
 * - TRIM([LEADING|TRAILING|BOTH] [char] FROM source)
 * - TYPE(entity) for polymorphic queries
 */
class FunctionExpressionParser(
    private val ctx: ParseContext,
    private val parseExpression: () -> Expression
) {
    /**
     * Parses a standard function call: FUNCTION_NAME(arg1, arg2, ...)
     * Used for built-in functions like UPPER, LOWER, CONCAT, COALESCE, etc.
     */
    fun parseFunctionCall(): FunctionCallExpression {
        val name = ctx.current.text.uppercase()
        ctx.advance()
        if (!ctx.check(TokenType.LEFT_PARENTHESES)) return FunctionCallExpression(name, emptyList())
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val args = mutableListOf<Expression>()
        if (!ctx.check(TokenType.RIGHT_PARENTHESES)) {
            do { args.add(parseExpression()) } while (ctx.match(TokenType.COMMA))
        }
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return FunctionCallExpression(name, args)
    }

    /**
     * Parses JPQL FUNCTION('native_name', arg1, arg2, ...) syntax.
     * The first argument is the native function name as a string literal.
     * Returns a FunctionCallExpression with the native name and remaining args.
     */
    fun parseJpqlFunction(): FunctionCallExpression {
        ctx.expect(TokenType.FUNCTION)
        ctx.expect(TokenType.LEFT_PARENTHESES)

        // First argument must be the function name as a string literal
        if (!ctx.check(TokenType.STRING_LITERAL)) {
            throw ctx.parseError("FUNCTION requires a string literal as the first argument (function name)")
        }
        val nativeFunctionName = ctx.current.text
        ctx.advance()

        // Parse remaining arguments
        val args = mutableListOf<Expression>()
        while (ctx.match(TokenType.COMMA)) {
            args.add(parseExpression())
        }
        ctx.expect(TokenType.RIGHT_PARENTHESES)

        return FunctionCallExpression(nativeFunctionName, args)
    }

    /**
     * Parses CAST(expression AS type) expression.
     */
    fun parseCastExpression(): CastExpression {
        ctx.expect(TokenType.CAST)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val expression = parseExpression()
        ctx.expect(TokenType.AS)
        val targetType = ctx.expectIdentifierOrKeyword()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return CastExpression(expression, targetType)
    }

    /**
     * Parses EXTRACT(field FROM source) expression.
     */
    fun parseExtractExpression(): ExtractExpression {
        ctx.expect(TokenType.EXTRACT)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val field = when {
            ctx.match(TokenType.YEAR) -> ExtractField.YEAR
            ctx.match(TokenType.MONTH) -> ExtractField.MONTH
            ctx.match(TokenType.DAY) -> ExtractField.DAY
            ctx.match(TokenType.HOUR) -> ExtractField.HOUR
            ctx.match(TokenType.MINUTE) -> ExtractField.MINUTE
            ctx.match(TokenType.SECOND) -> ExtractField.SECOND
            else -> throw ctx.parseError("Expected date/time field (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND)")
        }
        ctx.expect(TokenType.FROM)
        val source = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return ExtractExpression(field, source)
    }

    /**
     * Parses TRIM([LEADING|TRAILING|BOTH] [char] FROM source) or simple TRIM(source) expression.
     */
    fun parseTrimExpression(): TrimExpression {
        ctx.expect(TokenType.TRIM)
        ctx.expect(TokenType.LEFT_PARENTHESES)

        val mode = when {
            ctx.match(TokenType.LEADING) -> TrimMode.LEADING
            ctx.match(TokenType.TRAILING) -> TrimMode.TRAILING
            ctx.match(TokenType.BOTH) -> TrimMode.BOTH
            else -> null
        }

        if (mode != null) {
            val trimChar = if (ctx.check(TokenType.STRING_LITERAL)) {
                val char = ctx.current.text
                ctx.advance()
                char
            } else null

            ctx.expect(TokenType.FROM)
            val source = parseExpression()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
            return TrimExpression(mode, trimChar, source)
        }

        // Simple TRIM(expr)
        val source = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return TrimExpression(TrimMode.BOTH, null, source)
    }

    /**
     * Parses TYPE(entity) expression for polymorphic queries.
     */
    fun parseTypeExpression(): TypeExpression {
        ctx.expect(TokenType.TYPE)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val alias = ctx.expectIdentifierOrKeyword()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return TypeExpression(alias)
    }
}
