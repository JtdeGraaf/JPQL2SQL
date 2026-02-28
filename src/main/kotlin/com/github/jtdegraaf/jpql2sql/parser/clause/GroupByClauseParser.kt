package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/** Parses `GROUP BY path1, path2, …`. */
class GroupByClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): GroupByClause {
        ctx.expect(TokenType.GROUP)
        ctx.expect(TokenType.BY)
        val expressions = mutableListOf<PathExpression>()
        do {
            expressions.add(expr.parsePathExpression())
        } while (ctx.match(TokenType.COMMA))
        return GroupByClause(expressions)
    }
}
