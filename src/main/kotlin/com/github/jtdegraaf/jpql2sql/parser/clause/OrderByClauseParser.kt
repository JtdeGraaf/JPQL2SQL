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
            val direction = when {
                ctx.match(TokenType.ASC) -> OrderDirection.ASC
                ctx.match(TokenType.DESC) -> OrderDirection.DESC
                else -> OrderDirection.ASC
            }
            val nulls = if (ctx.match(TokenType.NULLS)) {
                when {
                    ctx.match(TokenType.FIRST) -> NullsOrdering.FIRST
                    ctx.match(TokenType.LAST) -> NullsOrdering.LAST
                    else -> throw ctx.parseError("Expected FIRST or LAST after NULLS")
                }
            } else null
            items.add(OrderByItem(expression, direction, nulls))
        } while (ctx.match(TokenType.COMMA))
        return OrderByClause(items)
    }
}
