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
 * - TRIM with optional mode (LEADING/TRAILING/BOTH), optional trim character, and FROM source
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
        val args = ctx.parseInParentheses { ctx.parseCommaSeparatedList { parseExpression() } }
        return FunctionCallExpression(name, args)
    }

    /**
     * Parses JPQL FUNCTION('native_name', arg1, arg2, ...) syntax.
     * The first argument is the native function name as a string literal.
     * Returns a FunctionCallExpression with the native name and remaining args.
     */
    fun parseJpqlFunction(): FunctionCallExpression {
        ctx.expect(TokenType.FUNCTION)
        return ctx.parseInParentheses {
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

            FunctionCallExpression(nativeFunctionName, args)
        }
    }

    /**
     * Parses CAST(expression AS type) expression.
     */
    fun parseCastExpression(): CastExpression {
        ctx.expect(TokenType.CAST)
        return ctx.parseInParentheses {
            val expression = parseExpression()
            ctx.expect(TokenType.AS)
            val targetType = ctx.expectIdentifierOrKeyword()
            CastExpression(expression, targetType)
        }
    }

    /**
     * Parses EXTRACT(field FROM source) expression.
     */
    fun parseExtractExpression(): ExtractExpression {
        ctx.expect(TokenType.EXTRACT)
        return ctx.parseInParentheses {
            val field = ctx.parseExtractField()
            ctx.expect(TokenType.FROM)
            val source = parseExpression()
            ExtractExpression(field, source)
        }
    }

    /**
     * Parses TRIM expression with optional mode, trim character, and FROM clause, or simple TRIM(source).
     */
    fun parseTrimExpression(): TrimExpression {
        ctx.expect(TokenType.TRIM)
        return ctx.parseInParentheses {
            // Check if we have extended syntax: TRIM(mode [char] FROM source)
            val hasMode = ctx.check(TokenType.LEADING) || ctx.check(TokenType.TRAILING) || ctx.check(TokenType.BOTH)
            if (hasMode) {
                val mode = ctx.parseTrimMode()
                val trimChar = if (ctx.check(TokenType.STRING_LITERAL)) {
                    val char = ctx.current.text
                    ctx.advance()
                    char
                } else null

                ctx.expect(TokenType.FROM)
                val source = parseExpression()
                TrimExpression(mode, trimChar, source)
            } else {
                // Simple TRIM(expr)
                val source = parseExpression()
                TrimExpression(TrimMode.BOTH, null, source)
            }
        }
    }

    /**
     * Parses TYPE(entity) expression for polymorphic queries.
     */
    fun parseTypeExpression(): TypeExpression {
        ctx.expect(TokenType.TYPE)
        val alias = ctx.parseInParentheses { ctx.expectIdentifierOrKeyword() }
        return TypeExpression(alias)
    }
}
