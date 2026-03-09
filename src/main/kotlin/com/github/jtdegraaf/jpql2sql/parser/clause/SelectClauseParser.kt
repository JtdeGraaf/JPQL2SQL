package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

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
            skipGarbageBetweenProjections(projections)
        } while (ctx.match(TokenType.COMMA))
        return SelectClause(distinct, projections)
    }

    private fun parseProjection(): Projection {
        return try {
            parseProjectionInternal()
        } catch (_: JpqlParseException) {
            FieldProjection(UnparsedFragment(collectUntilProjectionEnd()), null)
        }
    }

    private fun skipGarbageBetweenProjections(projections: MutableList<Projection>) {
        if (!ctx.check(TokenType.COMMA) && !ctx.check(TokenType.FROM) && !ctx.check(TokenType.END_OF_FILE)) {
            val garbage = collectUntilProjectionEnd()
            if (garbage.isNotEmpty()) {
                projections.add(FieldProjection(UnparsedFragment(garbage), null))
            }
        }
    }

    private fun collectUntilProjectionEnd(): String {
        val collected = StringBuilder()
        var parenDepth = 0
        while (!ctx.check(TokenType.END_OF_FILE)) {
            if (parenDepth <= 0 && (ctx.check(TokenType.COMMA) || ctx.check(TokenType.FROM))) break
            if (ctx.check(TokenType.LEFT_PARENTHESES)) parenDepth++
            if (ctx.check(TokenType.RIGHT_PARENTHESES)) parenDepth--
            collected.append(ctx.current.text)
            ctx.advance()
        }
        return collected.toString()
    }

    private fun parseProjectionInternal(): Projection {
        // Constructor: NEW ClassName(...)
        if (ctx.check(TokenType.NEW)) return parseConstructorProjection()

        // Aggregates: COUNT, SUM, AVG, MIN, MAX (need special handling)
        if (ctx.current.type.isAggregate()) return parseAggregateProjection()

        // Expression-based projections - parse expression and add optional alias
        if (isExpressionStart()) return parseExpressionWithAlias()

        // Path-based projection (default)
        return parsePathProjection()
    }

    private fun isExpressionStart(): Boolean =
        ctx.current.type.isExpressionStart() ||
            (ctx.check(TokenType.NOT) && ctx.peekNext()?.type == TokenType.EXISTS)

    private fun parseExpressionWithAlias(): FieldProjection {
        val expression = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(expression, alias)
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

    private fun parseAggregateProjection(): Projection {
        val func = when (ctx.current.type) {
            TokenType.COUNT -> AggregateFunction.COUNT
            TokenType.SUM -> AggregateFunction.SUM
            TokenType.AVG -> AggregateFunction.AVG
            TokenType.MIN -> AggregateFunction.MIN
            TokenType.MAX -> AggregateFunction.MAX
            else -> error("Expected aggregate function")
        }

        ctx.advance()
        ctx.expect(TokenType.LEFT_PARENTHESES)

        // COUNT(*)
        if (func == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
            ctx.advance()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
            return finalizeAggregate(AggregateFunction.COUNT, false, PathExpression(listOf("*")))
        }

        val distinct = ctx.match(TokenType.DISTINCT)
        val expression = expr.parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)

        return finalizeAggregate(func, distinct, expression)
    }

    private fun finalizeAggregate(func: AggregateFunction, distinct: Boolean, expression: Expression): Projection {
        // Check if aggregate is part of a comparison (e.g., COUNT(*) > 0)
        if (ctx.current.type.isComparisonOperator()) {
            val aggExpr = AggregateExpression(func, distinct, expression)
            val fullExpr = parseRemainingComparison(aggExpr)
            return FieldProjection(fullExpr, ctx.parseOptionalAlias())
        }

        val alias = ctx.parseOptionalAlias()
        return if (func == AggregateFunction.COUNT && expression is PathExpression && expression.parts == listOf("*") && alias == null) {
            CountAllProjection
        } else {
            AggregateProjection(func, distinct, expression, alias)
        }
    }

    private fun parsePathProjection(): Projection {
        val path = expr.parsePathExpression()

        // Parameterless function call: identifier()
        if (path.parts.size == 1 && ctx.check(TokenType.LEFT_PARENTHESES) && ctx.peekNext()?.type == TokenType.RIGHT_PARENTHESES) {
            ctx.advance() // (
            ctx.advance() // )
            return FieldProjection(FunctionCallExpression(path.parts[0].uppercase(), emptyList()), ctx.parseOptionalAlias())
        }

        // Arithmetic: path + expr
        if (ctx.current.type.isArithmeticOperator()) {
            val fullExpr = parseRemainingArithmetic(path)
            return FieldProjection(fullExpr, ctx.parseOptionalAlias())
        }

        return FieldProjection(path, ctx.parseOptionalAlias())
    }

    private fun parseRemainingArithmetic(left: Expression): Expression {
        var result = left
        while (ctx.current.type.isArithmeticOperator()) {
            val op = when {
                ctx.match(TokenType.PLUS) -> BinaryOperator.ADD
                ctx.match(TokenType.MINUS) -> BinaryOperator.SUBTRACT
                ctx.match(TokenType.STAR) -> BinaryOperator.MULTIPLY
                ctx.match(TokenType.SLASH) -> BinaryOperator.DIVIDE
                ctx.match(TokenType.CONCAT_OP) -> BinaryOperator.CONCAT
                else -> break
            }
            val right = when {
                ctx.check(TokenType.LEFT_PARENTHESES) -> expr.parseExpression()
                ctx.check(TokenType.NUMBER_LITERAL) -> {
                    val v = ctx.current.text.toLongOrNull() ?: ctx.current.text.toDoubleOrNull() ?: ctx.current.text
                    ctx.advance()
                    LiteralExpression(v, LiteralType.NUMBER)
                }
                ctx.check(TokenType.STRING_LITERAL) -> {
                    val v = ctx.current.text; ctx.advance()
                    LiteralExpression(v, LiteralType.STRING)
                }
                else -> expr.parsePathExpression()
            }
            result = BinaryExpression(result, op, right)
        }
        return result
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
        return BinaryExpression(left, op, expr.parseExpression())
    }
}
