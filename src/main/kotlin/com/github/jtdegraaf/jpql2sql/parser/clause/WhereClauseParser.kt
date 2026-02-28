package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/** Parses `WHERE condition`. */
class WhereClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): WhereClause {
        ctx.expect(TokenType.WHERE)
        return WhereClause(expr.parseExpression())
    }
}
