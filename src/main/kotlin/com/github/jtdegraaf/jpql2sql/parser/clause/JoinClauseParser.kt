package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses JOIN clauses: `[INNER|LEFT|RIGHT] [OUTER] JOIN [FETCH] path [AS] alias [ON condition]`.
 */
class JoinClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): List<JoinClause> {
        val joins = mutableListOf<JoinClause>()

        while (true) {
            val joinType = when {
                ctx.match(TokenType.INNER) -> { ctx.expect(TokenType.JOIN); JoinType.INNER }
                ctx.match(TokenType.LEFT) -> { ctx.match(TokenType.OUTER); ctx.expect(TokenType.JOIN); JoinType.LEFT }
                ctx.match(TokenType.RIGHT) -> { ctx.match(TokenType.OUTER); ctx.expect(TokenType.JOIN); JoinType.RIGHT }
                ctx.match(TokenType.JOIN) -> JoinType.INNER
                else -> break
            }

            @Suppress("UNUSED_VARIABLE")
            val fetch = ctx.match(TokenType.FETCH)
            val path = expr.parsePathExpression()

            val alias = if (ctx.match(TokenType.AS)) {
                ctx.expectIdentifier()
            } else if (ctx.check(TokenType.IDENTIFIER) && !ctx.check(TokenType.ON) && !ctx.check(TokenType.WHERE)) {
                ctx.expectIdentifier()
            } else {
                path.parts.last()
            }

            val condition = if (ctx.match(TokenType.ON)) expr.parseExpression() else null
            joins.add(JoinClause(joinType, path, alias, condition))
        }

        return joins
    }
}

