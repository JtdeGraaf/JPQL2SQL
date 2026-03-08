package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses CASE expressions in both simple and searched forms.
 *
 * Simple CASE:
 *   CASE expr WHEN value1 THEN result1 WHEN value2 THEN result2 ELSE default END
 *
 * Searched CASE:
 *   CASE WHEN condition1 THEN result1 WHEN condition2 THEN result2 ELSE default END
 */
class CaseExpressionParser(
    private val ctx: ParseContext,
    private val parseExpression: () -> Expression
) {
    /**
     * Parses a CASE expression.
     */
    fun parseCaseExpression(): CaseExpression {
        ctx.expect(TokenType.CASE)

        // Simple CASE has an operand before WHEN, searched CASE starts with WHEN
        val operand = if (!ctx.check(TokenType.WHEN)) parseExpression() else null

        val whenClauses = mutableListOf<WhenClause>()
        while (ctx.match(TokenType.WHEN)) {
            val condition = parseExpression()
            ctx.expect(TokenType.THEN)
            val result = parseExpression()
            whenClauses.add(WhenClause(condition, result))
        }

        val elseExpr = if (ctx.match(TokenType.ELSE)) parseExpression() else null
        ctx.expect(TokenType.END)

        return CaseExpression(operand, whenClauses, elseExpr)
    }
}
