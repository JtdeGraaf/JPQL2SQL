package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses subquery-related expressions.
 *
 * Handles:
 * - EXISTS (SELECT ...) - existence check
 * - (SELECT ...) - scalar subquery
 */
class SubqueryExpressionParser(
    private val ctx: ParseContext,
    private val parseSubquery: () -> JpqlQuery
) {
    /**
     * Parses EXISTS (SELECT ...) expression.
     */
    fun parseExistsExpression(): ExistsExpression {
        ctx.expect(TokenType.EXISTS)
        val subquery = ctx.parseInParentheses { parseSubquery() }
        return ExistsExpression(subquery)
    }

    /**
     * Parses a parenthesized subquery expression: (SELECT ...)
     * Returns the SubqueryExpression or null if not a subquery.
     */
    fun parseSubqueryExpression(): SubqueryExpression? {
        if (!ctx.check(TokenType.LEFT_PARENTHESES)) return null
        if (ctx.peekNext()?.type != TokenType.SELECT) return null

        val subquery = ctx.parseInParentheses { parseSubquery() }
        return SubqueryExpression(subquery)
    }
}
