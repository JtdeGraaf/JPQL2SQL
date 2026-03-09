package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses aggregate function expressions: COUNT, SUM, AVG, MIN, MAX.
 *
 * Handles:
 * - COUNT(*) - count all rows
 * - COUNT(expr) - count non-null values
 * - COUNT(DISTINCT expr) - count distinct values
 * - SUM(expr), AVG(expr), MIN(expr), MAX(expr)
 */
class AggregateExpressionParser(
    private val ctx: ParseContext,
    private val parseExpression: () -> Expression
) {
    /**
     * Parses an aggregate expression when used inside other expressions
     * (e.g., in HAVING clause: HAVING COUNT(m) > 5).
     */
    fun parseAggregate(): AggregateExpression {
        val func = ctx.parseAggregateFunction()
        ctx.expect(TokenType.LEFT_PARENTHESES)

        // Handle COUNT(*)
        if (func == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
            ctx.advance()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
            return AggregateExpression(func, false, PathExpression(listOf("*")))
        }

        val distinct = ctx.match(TokenType.DISTINCT)
        val expr = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return AggregateExpression(func, distinct, expr)
    }
}
