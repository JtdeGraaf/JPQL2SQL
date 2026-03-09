package com.github.jtdegraaf.jpql2sql.parser

import com.github.jtdegraaf.jpql2sql.parser.clause.*

/**
 * Entry-point for JPQL parsing.
 *
 * Creates a shared [ParseContext] and delegates to specialised clause parsers:
 * [SelectClauseParser], [FromClauseParser], [JoinClauseParser],
 * [WhereClauseParser], [GroupByClauseParser], [HavingClauseParser],
 * [OrderByClauseParser], [FetchClauseParser], and [ExpressionParser].
 */
class JpqlParser(input: String) {

    private val ctx = ParseContext(input)
    private val expr = ExpressionParser(ctx, ::parseSingleQuery)

    private val selectParser = SelectClauseParser(ctx, expr)
    private val fromParser = FromClauseParser(ctx)
    private val joinParser = JoinClauseParser(ctx, expr)
    private val whereParser = WhereClauseParser(ctx, expr)
    private val groupByParser = GroupByClauseParser(ctx, expr)
    private val havingParser = HavingClauseParser(ctx, expr)
    private val orderByParser = OrderByClauseParser(ctx, expr)
    private val fetchParser = FetchClauseParser(ctx)

    /**
     * Parses a JPQL query, which may be a simple query or a compound query with UNION/INTERSECT/EXCEPT.
     * Returns JpqlQuery for simple queries, or the first query for compound queries
     * (compound queries are handled via recursion and stored in CompoundQuery).
     */
    fun parse(): JpqlQuery = parseSingleQuery()

    /**
     * Parses a potentially compound query (with UNION/INTERSECT/EXCEPT).
     * Returns either a JpqlQuery or a CompoundQuery wrapped appropriately.
     */
    fun parseCompound(): JpqlNode {
        val left = parseSingleQuery()

        // Check for set operations
        val operation = parseSetOperation() ?: return left

        val right = parseCompound()
        return when (right) {
            is JpqlQuery -> CompoundQuery(left, operation, right)
            is CompoundQuery -> CompoundQuery(left, operation, right.left).let {
                // Chain: (left op right.left) op right.right
                CompoundQuery(it.left, it.operation, right.left)
            }
            else -> left
        }
    }

    private fun parseSetOperation(): SetOperation? {
        return when {
            ctx.match(TokenType.UNION) -> {
                if (ctx.match(TokenType.ALL)) SetOperation.UNION_ALL else SetOperation.UNION
            }
            ctx.match(TokenType.INTERSECT) -> {
                if (ctx.match(TokenType.ALL)) SetOperation.INTERSECT_ALL else SetOperation.INTERSECT
            }
            ctx.match(TokenType.EXCEPT) -> {
                if (ctx.match(TokenType.ALL)) SetOperation.EXCEPT_ALL else SetOperation.EXCEPT
            }
            else -> null
        }
    }

    private fun parseSingleQuery(): JpqlQuery {
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

        val fetch = fetchParser.parse()
        if (fetch != null) collectUnparsedBetweenClauses(unparsedFragments)

        // Collect any trailing unparsed content (but stop at set operation keywords)
        collectUnparsedBetweenClauses(unparsedFragments)

        return JpqlQuery(select, from, joins, where, groupBy, having, orderBy, fetch, unparsedFragments)
    }

    /**
     * Collects any unparsed tokens until the next clause keyword and adds to the fragments list.
     */
    private fun collectUnparsedBetweenClauses(fragments: MutableList<String>) {
        if (!ctx.check(TokenType.END_OF_FILE) && !ctx.isClauseKeyword(ctx.current.type) && !isSetOperationKeyword()) {
            val unparsed = ctx.collectUntilClauseKeyword()
            if (unparsed.isNotBlank()) {
                fragments.add(unparsed)
            }
        }
    }

    private fun isSetOperationKeyword(): Boolean = ctx.current.type.isSetOperator()
}

class JpqlParseException(message: String) : RuntimeException(message)
