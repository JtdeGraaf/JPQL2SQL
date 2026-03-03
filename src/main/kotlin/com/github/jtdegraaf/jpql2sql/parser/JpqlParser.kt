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
        val unparsedFragments = mutableListOf<String>()

        val select = selectParser.parse()
        collectUnparsedBetweenClauses(unparsedFragments)

        val from = fromParser.parse()
        collectUnparsedBetweenClauses(unparsedFragments)

        val joins = joinParser.parse()
        collectUnparsedBetweenClauses(unparsedFragments)

        val where = if (ctx.check(TokenType.WHERE)) {
            whereParser.parse().also { collectUnparsedBetweenClauses(unparsedFragments) }
        } else null

        val groupBy = if (ctx.check(TokenType.GROUP)) {
            groupByParser.parse().also { collectUnparsedBetweenClauses(unparsedFragments) }
        } else null

        val having = if (ctx.check(TokenType.HAVING)) {
            havingParser.parse().also { collectUnparsedBetweenClauses(unparsedFragments) }
        } else null

        val orderBy = if (ctx.check(TokenType.ORDER)) {
            orderByParser.parse().also { collectUnparsedBetweenClauses(unparsedFragments) }
        } else null

        // Collect any trailing unparsed content
        collectUnparsedBetweenClauses(unparsedFragments)

        return JpqlQuery(select, from, joins, where, groupBy, having, orderBy, unparsedFragments)
    }

    /**
     * Collects any unparsed tokens until the next clause keyword and adds to the fragments list.
     */
    private fun collectUnparsedBetweenClauses(fragments: MutableList<String>) {
        if (!ctx.check(TokenType.END_OF_FILE) && !ctx.isClauseKeyword(ctx.current.type)) {
            val unparsed = ctx.collectUntilClauseKeyword()
            if (unparsed.isNotBlank()) {
                fragments.add(unparsed)
            }
        }
    }
}

class JpqlParseException(message: String) : RuntimeException(message)
