package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import com.github.jtdegraaf.jpql2sql.parser.clause.ExpressionParser.Companion.isFunctionToken

/**
 * Parses the SELECT clause including projections, aggregates, constructor expressions,
 * and function calls.
 */
class SelectClauseParser(
    private val ctx: ParseContext,
    private val expr: ExpressionParser
) {
    fun parse(): SelectClause {
        ctx.expect(TokenType.SELECT)
        val distinct = ctx.match(TokenType.DISTINCT)
        val projections = mutableListOf<Projection>()
        do {
            projections.add(parseProjection())
        } while (ctx.match(TokenType.COMMA))
        return SelectClause(distinct, projections)
    }

    private fun parseProjection(): Projection {
        if (ctx.check(TokenType.NEW)) return parseConstructorProjection()

        val aggregateFunc = when {
            ctx.check(TokenType.COUNT) -> AggregateFunction.COUNT
            ctx.check(TokenType.SUM) -> AggregateFunction.SUM
            ctx.check(TokenType.AVG) -> AggregateFunction.AVG
            ctx.check(TokenType.MIN) -> AggregateFunction.MIN
            ctx.check(TokenType.MAX) -> AggregateFunction.MAX
            else -> null
        }

        if (aggregateFunc != null) {
            ctx.advance()
            ctx.expect(TokenType.LPAREN)

            if (aggregateFunc == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
                ctx.advance()
                ctx.expect(TokenType.RPAREN)

                // Check if this COUNT(*) is part of a comparison expression like COUNT(*) > 0
                if (isComparisonOperator(ctx.current.type)) {
                    val countExpr = AggregateExpression(AggregateFunction.COUNT, false, PathExpression(listOf("*")))
                    val fullExpr = parseRemainingComparison(countExpr)
                    val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
                    return FieldProjection(fullExpr, alias)
                }

                val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
                return if (alias != null) {
                    AggregateProjection(AggregateFunction.COUNT, false, PathExpression(listOf("*")), alias)
                } else {
                    CountAllProjection
                }
            }

            val distinct = ctx.match(TokenType.DISTINCT)
            val expression = expr.parseExpression()
            ctx.expect(TokenType.RPAREN)

            // Check if this aggregate is part of a comparison expression like COUNT(m) > 0
            if (isComparisonOperator(ctx.current.type)) {
                val aggExpr = AggregateExpression(aggregateFunc, distinct, expression)
                val fullExpr = parseRemainingComparison(aggExpr)
                val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
                return FieldProjection(fullExpr, alias)
            }

            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return AggregateProjection(aggregateFunc, distinct, expression, alias)
        }

        if (ctx.check(TokenType.FUNCTION) || isFunctionToken(ctx.current.type)) {
            val funcExpr = if (ctx.check(TokenType.FUNCTION)) {
                expr.parseExpression()  // parseExpression will handle FUNCTION token
            } else {
                expr.parseFunctionCall()
            }
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(funcExpr, alias)
        }

        if (ctx.check(TokenType.CASE)) {
            val caseExpr = expr.parseExpression()
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(caseExpr, alias)
        }

        // Handle EXISTS and NOT EXISTS in SELECT clause
        if (ctx.check(TokenType.EXISTS) || (ctx.check(TokenType.NOT) && ctx.peekNext()?.type == TokenType.EXISTS)) {
            val existsExpr = expr.parseExpression()
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(existsExpr, alias)
        }

        // Handle literal values (e.g., SELECT 1 FROM ... for EXISTS subqueries)
        if (ctx.check(TokenType.NUMBER_LITERAL) || ctx.check(TokenType.STRING_LITERAL)) {
            val literalExpr = expr.parseExpression()
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(literalExpr, alias)
        }

        val path = expr.parsePathExpression()
        val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
        return FieldProjection(path, alias)
    }

    private fun isComparisonOperator(type: TokenType): Boolean {
        return type in setOf(TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)
    }

    private fun parseRemainingComparison(left: Expression): Expression {
        val op = when {
            ctx.match(TokenType.EQ) -> BinaryOperator.EQ
            ctx.match(TokenType.NE) -> BinaryOperator.NE
            ctx.match(TokenType.LT) -> BinaryOperator.LT
            ctx.match(TokenType.LE) -> BinaryOperator.LE
            ctx.match(TokenType.GT) -> BinaryOperator.GT
            ctx.match(TokenType.GE) -> BinaryOperator.GE
            else -> return left
        }
        val right = expr.parseExpression()
        return BinaryExpression(left, op, right)
    }

    private fun parseConstructorProjection(): ConstructorProjection {
        ctx.expect(TokenType.NEW)
        val className = expr.parseQualifiedName()
        ctx.expect(TokenType.LPAREN)
        val args = mutableListOf<Expression>()
        if (!ctx.check(TokenType.RPAREN)) {
            do { args.add(expr.parseExpression()) } while (ctx.match(TokenType.COMMA))
        }
        ctx.expect(TokenType.RPAREN)
        return ConstructorProjection(className, args)
    }
}


