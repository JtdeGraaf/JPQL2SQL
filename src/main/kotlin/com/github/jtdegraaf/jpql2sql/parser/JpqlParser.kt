package com.github.jtdegraaf.jpql2sql.parser

import com.github.jtdegraaf.jpql2sql.parser.clause.*

/**
 * Entry-point for JPQL parsing.
 *
 * Creates a shared [ParseContext] and delegates to specialised clause parsers:
 * [SelectClauseParser], [FromClauseParser], [JoinClauseParser],
 * [WhereClauseParser], [GroupByClauseParser], [HavingClauseParser],
 * [OrderByClauseParser], and [ExpressionParser].
 */
class JpqlParser(input: String) {

    private val ctx = ParseContext(input)
    private val expr = ExpressionParser(ctx, ::parse)

    private val selectParser = SelectClauseParser(ctx, expr)
    private val fromParser = FromClauseParser(ctx)
    private val joinParser = JoinClauseParser(ctx, expr)
    private val whereParser = WhereClauseParser(ctx, expr)
    private val groupByParser = GroupByClauseParser(ctx, expr)
    private val havingParser = HavingClauseParser(ctx, expr)
    private val orderByParser = OrderByClauseParser(ctx, expr)

    fun parse(): JpqlQuery {
        val select = selectParser.parse()
        val from = fromParser.parse()
        val joins = joinParser.parse()
        val where = if (ctx.check(TokenType.WHERE)) whereParser.parse() else null
        val groupBy = if (ctx.check(TokenType.GROUP)) groupByParser.parse() else null
        val having = if (ctx.check(TokenType.HAVING)) havingParser.parse() else null
        val orderBy = if (ctx.check(TokenType.ORDER)) orderByParser.parse() else null

        return JpqlQuery(select, from, joins, where, groupBy, having, orderBy)
    }
}

class JpqlParseException(message: String) : RuntimeException(message)
