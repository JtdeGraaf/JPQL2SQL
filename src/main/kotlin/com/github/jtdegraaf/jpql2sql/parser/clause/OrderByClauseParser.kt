package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/** Parses `ORDER BY expr [ASC|DESC] [NULLS FIRST|LAST], …`. */
class OrderByClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): OrderByClause {
        ctx.expect(TokenType.ORDER)
        ctx.expect(TokenType.BY)
        val items = mutableListOf<OrderByItem>()
        do {
            val expression = expr.parseExpression()
            val direction = ctx.parseOrderDirection()
            val nulls = ctx.parseNullsOrdering()
            items.add(OrderByItem(expression, direction, nulls))
        } while (ctx.match(TokenType.COMMA))
        return OrderByClause(items)
    }
}
