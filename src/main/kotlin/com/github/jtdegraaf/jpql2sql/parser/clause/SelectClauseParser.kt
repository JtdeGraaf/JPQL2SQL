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
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return AggregateProjection(aggregateFunc, distinct, expression, alias)
        }

        if (isFunctionToken(ctx.current.type)) {
            val funcExpr = expr.parseFunctionCall()
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(funcExpr, alias)
        }

        val path = expr.parsePathExpression()
        val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
        return FieldProjection(path, alias)
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


