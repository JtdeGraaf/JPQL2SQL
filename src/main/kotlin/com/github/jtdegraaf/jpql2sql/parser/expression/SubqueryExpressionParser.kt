package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses subquery-related expressions.
 *
 * Handles:
 * - EXISTS (SELECT ...) - existence check
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
}
