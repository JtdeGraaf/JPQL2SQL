package com.github.jtdegraaf.jpql2sql.parser.expression

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses comparison expressions including special JPQL operators.
 *
 * Handles:
 * - Simple comparisons: =, <>, <, <=, >, >=
 * - IS NULL / IS NOT NULL / IS EMPTY
 * - IN / NOT IN (with lists, parameters, and subqueries)
 * - BETWEEN / NOT BETWEEN
 * - LIKE / NOT LIKE
 * - MEMBER OF / NOT MEMBER OF
 */
class ComparisonExpressionParser(
    private val ctx: ParseContext,
    private val parseExpression: () -> Expression,
    private val parseAdditiveExpression: () -> Expression,
    private val parsePathExpression: () -> PathExpression,
    private val parseSubquery: () -> JpqlQuery
) {
    /**
     * Attempts to parse a comparison expression given the left operand.
     * Returns null if no comparison operator is found.
     */
    fun parseComparison(left: Expression): Expression? {
        return parseIsExpression(left)
            ?: parseInExpression(left)
            ?: parseBetweenExpression(left)
            ?: parseLikeExpression(left)
            ?: parseMemberExpression(left)
            ?: parseSimpleComparison(left)
    }

    /**
     * Parses IS NULL / IS NOT NULL / IS EMPTY expressions.
     */
    private fun parseIsExpression(left: Expression): Expression? {
        if (!ctx.match(TokenType.IS)) return null

        val not = ctx.match(TokenType.NOT)
        return when {
            ctx.match(TokenType.NULL) -> BinaryExpression(
                left, if (not) BinaryOperator.IS_NOT_NULL else BinaryOperator.IS_NULL,
                LiteralExpression(null, LiteralType.NULL)
            )
            ctx.match(TokenType.EMPTY) -> BinaryExpression(
                left, if (not) BinaryOperator.IS_NOT_NULL else BinaryOperator.IS_NULL,
                LiteralExpression(null, LiteralType.NULL)
            )
            else -> throw ctx.parseError("Expected NULL or EMPTY after IS")
        }
    }

    /**
     * Parses IN / NOT IN expressions including subqueries and parameters.
     */
    private fun parseInExpression(left: Expression): Expression? {
        val notIn = ctx.checkNotFollowedBy(TokenType.IN)
        if (notIn) ctx.advance()
        if (!ctx.match(TokenType.IN)) return null

        val operator = if (notIn) BinaryOperator.NOT_IN else BinaryOperator.IN

        // Collection-valued parameter: IN :param or IN ?1 (without parentheses)
        ctx.parseParameterExpression()?.let { return BinaryExpression(left, operator, it) }

        ctx.expect(TokenType.LEFT_PARENTHESES)
        if (ctx.check(TokenType.SELECT)) {
            val subquery = parseSubquery()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
            return BinaryExpression(left, operator, SubqueryExpression(subquery))
        }
        val elements = ctx.parseCommaSeparatedList { parseExpression() }
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return BinaryExpression(left, operator, InListExpression(elements))
    }

    /**
     * Parses BETWEEN / NOT BETWEEN expressions.
     */
    private fun parseBetweenExpression(left: Expression): Expression? {
        val notBetween = ctx.checkNotFollowedBy(TokenType.BETWEEN)
        if (notBetween) ctx.advance()
        if (!ctx.match(TokenType.BETWEEN)) return null

        val lower = parseAdditiveExpression()
        ctx.expect(TokenType.AND)
        val upper = parseAdditiveExpression()
        return BinaryExpression(
            left,
            if (notBetween) BinaryOperator.NOT_BETWEEN else BinaryOperator.BETWEEN,
            BetweenExpression(lower, upper)
        )
    }

    /**
     * Parses LIKE / NOT LIKE expressions.
     */
    private fun parseLikeExpression(left: Expression): Expression? {
        val notLike = ctx.checkNotFollowedBy(TokenType.LIKE)
        if (notLike) ctx.advance()
        if (!ctx.match(TokenType.LIKE)) return null

        return BinaryExpression(
            left,
            if (notLike) BinaryOperator.NOT_LIKE else BinaryOperator.LIKE,
            parseAdditiveExpression()
        )
    }

    /**
     * Parses MEMBER OF / NOT MEMBER OF expressions.
     */
    private fun parseMemberExpression(left: Expression): Expression? {
        val notMember = ctx.checkNotFollowedBy(TokenType.MEMBER)
        if (notMember) ctx.advance()
        if (!ctx.match(TokenType.MEMBER)) return null

        ctx.match(TokenType.OF)
        return BinaryExpression(
            left,
            if (notMember) BinaryOperator.NOT_MEMBER_OF else BinaryOperator.MEMBER_OF,
            parsePathExpression()
        )
    }

    /**
     * Parses simple comparison operators: =, <>, <, <=, >, >=
     */
    private fun parseSimpleComparison(left: Expression): Expression? {
        val op = when {
            ctx.match(TokenType.EQUALS) -> BinaryOperator.EQ
            ctx.match(TokenType.NOT_EQUALS) -> BinaryOperator.NE
            ctx.match(TokenType.LESS_THAN) -> BinaryOperator.LT
            ctx.match(TokenType.LESS_THAN_OR_EQUAL) -> BinaryOperator.LE
            ctx.match(TokenType.GREATER_THAN) -> BinaryOperator.GT
            ctx.match(TokenType.GREATER_THAN_OR_EQUAL) -> BinaryOperator.GE
            else -> return null
        }
        return BinaryExpression(left, op, parseAdditiveExpression())
    }
}
