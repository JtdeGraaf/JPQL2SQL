package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/** Parses `HAVING condition`. */
class HavingClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): HavingClause {
        ctx.expect(TokenType.HAVING)
        return HavingClause(expr.parseExpression())
    }
}
