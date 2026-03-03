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
            // Handle unexpected tokens between projections (e.g., "expr@@, nextExpr")
            // Collect garbage until we find a comma or FROM
            if (!ctx.check(TokenType.COMMA) && !ctx.check(TokenType.FROM) && !ctx.check(TokenType.END_OF_FILE)) {
                val garbage = collectUntilProjectionEnd()
                if (garbage.isNotEmpty()) {
                    projections.add(FieldProjection(UnparsedFragment(garbage), null))
                }
            }
        } while (ctx.match(TokenType.COMMA))
        return SelectClause(distinct, projections)
    }

    private fun parseProjection(): Projection {
        return try {
            parseProjectionInternal()
        } catch (_: JpqlParseException) {
            // Resilient parsing: capture unparsed content instead of failing
            val unparsed = collectUntilProjectionEnd()
            FieldProjection(UnparsedFragment(unparsed), null)
        }
    }

    private fun collectUntilProjectionEnd(): String {
        val collected = StringBuilder()
        var parenDepth = 0
        while (!ctx.check(TokenType.END_OF_FILE)) {
            // Stop at comma (next projection) or FROM (end of SELECT) at depth 0 or less
            // We use <= 0 because if we started mid-expression after an exception,
            // we might encounter closing parens that drive depth negative
            if (parenDepth <= 0 && (ctx.check(TokenType.COMMA) || ctx.check(TokenType.FROM))) {
                break
            }
            if (ctx.check(TokenType.LEFT_PARENTHESES)) parenDepth++
            if (ctx.check(TokenType.RIGHT_PARENTHESES)) parenDepth--
            collected.append(ctx.current.text)
            ctx.advance()
        }
        return collected.toString()
    }

    private fun parseProjectionInternal(): Projection {
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
            ctx.expect(TokenType.LEFT_PARENTHESES)

            if (aggregateFunc == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
                ctx.advance()
                ctx.expect(TokenType.RIGHT_PARENTHESES)

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
            ctx.expect(TokenType.RIGHT_PARENTHESES)

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

        // Handle CAST expressions in SELECT clause
        if (ctx.check(TokenType.CAST)) {
            val castExpr = expr.parseExpression()
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(castExpr, alias)
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

        // Check for parameterless native function call: single identifier followed by ()
        // e.g., SYSDATE() for Oracle - the () signals it's a function, not a field
        if (path.parts.size == 1 && ctx.check(TokenType.LEFT_PARENTHESES) && ctx.peekNext()?.type == TokenType.RIGHT_PARENTHESES) {
            ctx.advance() // consume (
            ctx.advance() // consume )
            val funcExpr = FunctionCallExpression(path.parts[0].uppercase(), emptyList())
            val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
            return FieldProjection(funcExpr, alias)
        }

        val alias = if (ctx.match(TokenType.AS)) ctx.expectIdentifier() else null
        return FieldProjection(path, alias)
    }

    private fun isComparisonOperator(type: TokenType): Boolean {
        return type in setOf(TokenType.EQUALS, TokenType.NOT_EQUALS, TokenType.LESS_THAN, TokenType.LESS_THAN_OR_EQUAL, TokenType.GREATER_THAN, TokenType.GREATER_THAN_OR_EQUAL)
    }

    private fun parseRemainingComparison(left: Expression): Expression {
        val op = when {
            ctx.match(TokenType.EQUALS) -> BinaryOperator.EQ
            ctx.match(TokenType.NOT_EQUALS) -> BinaryOperator.NE
            ctx.match(TokenType.LESS_THAN) -> BinaryOperator.LT
            ctx.match(TokenType.LESS_THAN_OR_EQUAL) -> BinaryOperator.LE
            ctx.match(TokenType.GREATER_THAN) -> BinaryOperator.GT
            ctx.match(TokenType.GREATER_THAN_OR_EQUAL) -> BinaryOperator.GE
            else -> return left
        }
        val right = expr.parseExpression()
        return BinaryExpression(left, op, right)
    }

    private fun parseConstructorProjection(): ConstructorProjection {
        ctx.expect(TokenType.NEW)
        val className = expr.parseQualifiedName()
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val args = mutableListOf<Expression>()
        if (!ctx.check(TokenType.RIGHT_PARENTHESES)) {
            do { args.add(expr.parseExpression()) } while (ctx.match(TokenType.COMMA))
        }
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return ConstructorProjection(className, args)
    }
}


