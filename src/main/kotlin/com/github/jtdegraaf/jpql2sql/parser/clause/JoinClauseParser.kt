package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses JOIN clauses with optional join type, OUTER keyword, FETCH, alias, and ON condition.
 */
class JoinClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): List<JoinClause> {
        val joins = mutableListOf<JoinClause>()

        while (true) {
            val joinType = ctx.tryParseJoinType() ?: break

            @Suppress("UNUSED_VARIABLE")
            val fetch = ctx.match(TokenType.FETCH)
            val path = expr.parsePathExpression()

            val alias = ctx.parseAliasOrDefault { path.parts.last() }

            val condition = if (ctx.match(TokenType.ON)) expr.parseExpression() else null
            joins.add(JoinClause(joinType, path, alias, condition))
        }

        return joins
    }
}

