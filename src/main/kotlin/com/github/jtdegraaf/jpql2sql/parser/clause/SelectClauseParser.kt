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
        return parseConstructorProjection()
            ?: parseAggregateProjection()
            ?: parseFunctionProjection()
            ?: parseCaseProjection()
            ?: parseCastProjection()
            ?: parseExtractProjection()
            ?: parseTrimProjection()
            ?: parseExistsProjection()
            ?: parseLiteralProjection()
            ?: parseSubqueryProjection()
            ?: parsePathProjection()
    }

    /**
     * Parses constructor projection: NEW ClassName(...)
     */
    private fun parseConstructorProjection(): Projection? {
        if (!ctx.check(TokenType.NEW)) return null

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

    /**
     * Parses aggregate projections: COUNT, SUM, AVG, MIN, MAX
     */
    private fun parseAggregateProjection(): Projection? {
        if (!ctx.current.type.isAggregate()) return null

        val aggregateFunc = when (ctx.current.type) {
            TokenType.COUNT -> AggregateFunction.COUNT
            TokenType.SUM -> AggregateFunction.SUM
            TokenType.AVG -> AggregateFunction.AVG
            TokenType.MIN -> AggregateFunction.MIN
            TokenType.MAX -> AggregateFunction.MAX
            else -> return null
        }

        ctx.advance()
        ctx.expect(TokenType.LEFT_PARENTHESES)

        // Handle COUNT(*)
        if (aggregateFunc == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
            ctx.advance()
            ctx.expect(TokenType.RIGHT_PARENTHESES)

            // Check if COUNT(*) is part of a comparison expression like COUNT(*) > 0
            if (ctx.current.type.isComparisonOperator()) {
                val countExpr = AggregateExpression(AggregateFunction.COUNT, false, PathExpression(listOf("*")))
                val fullExpr = parseRemainingComparison(countExpr)
                val alias = ctx.parseOptionalAlias()
                return FieldProjection(fullExpr, alias)
            }

            val alias = ctx.parseOptionalAlias()
            return if (alias != null) {
                AggregateProjection(AggregateFunction.COUNT, false, PathExpression(listOf("*")), alias)
            } else {
                CountAllProjection
            }
        }

        val distinct = ctx.match(TokenType.DISTINCT)
        val expression = expr.parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)

        // Check if aggregate is part of a comparison expression like COUNT(m) > 0
        if (ctx.current.type.isComparisonOperator()) {
            val aggExpr = AggregateExpression(aggregateFunc, distinct, expression)
            val fullExpr = parseRemainingComparison(aggExpr)
            val alias = ctx.parseOptionalAlias()
            return FieldProjection(fullExpr, alias)
        }

        val alias = ctx.parseOptionalAlias()
        return AggregateProjection(aggregateFunc, distinct, expression, alias)
    }

    /**
     * Parses function projections: built-in functions and FUNCTION('name', ...)
     */
    private fun parseFunctionProjection(): Projection? {
        if (!ctx.check(TokenType.FUNCTION) && !ctx.current.type.isFunction()) return null

        val funcExpr = if (ctx.check(TokenType.FUNCTION)) {
            expr.parseExpression()  // parseExpression will handle FUNCTION token
        } else {
            expr.parseFunctionCall()
        }
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(funcExpr, alias)
    }

    /**
     * Parses CASE expression projections.
     */
    private fun parseCaseProjection(): Projection? {
        if (!ctx.check(TokenType.CASE)) return null
        val caseExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(caseExpr, alias)
    }

    /**
     * Parses CAST expression projections.
     */
    private fun parseCastProjection(): Projection? {
        if (!ctx.check(TokenType.CAST)) return null
        val castExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(castExpr, alias)
    }

    /**
     * Parses EXTRACT expression projections.
     */
    private fun parseExtractProjection(): Projection? {
        if (!ctx.check(TokenType.EXTRACT)) return null
        val extractExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(extractExpr, alias)
    }

    /**
     * Parses TRIM expression projections.
     */
    private fun parseTrimProjection(): Projection? {
        if (!ctx.check(TokenType.TRIM)) return null
        val trimExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(trimExpr, alias)
    }

    /**
     * Parses EXISTS and NOT EXISTS expression projections.
     */
    private fun parseExistsProjection(): Projection? {
        if (!ctx.check(TokenType.EXISTS) && !(ctx.check(TokenType.NOT) && ctx.peekNext()?.type == TokenType.EXISTS)) {
            return null
        }
        val existsExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(existsExpr, alias)
    }

    /**
     * Parses literal value projections (e.g., SELECT 1 FROM ...)
     */
    private fun parseLiteralProjection(): Projection? {
        if (!ctx.check(TokenType.NUMBER_LITERAL) && !ctx.check(TokenType.STRING_LITERAL)) return null
        val literalExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(literalExpr, alias)
    }

    /**
     * Parses subquery projections: (SELECT ...)
     */
    private fun parseSubqueryProjection(): Projection? {
        if (!ctx.check(TokenType.LEFT_PARENTHESES)) return null
        val fullExpr = expr.parseExpression()
        val alias = ctx.parseOptionalAlias()
        return FieldProjection(fullExpr, alias)
    }

    /**
     * Parses path expression projections with optional arithmetic or function calls.
     */
    private fun parsePathProjection(): Projection {
        val path = expr.parsePathExpression()

        // Check for parameterless native function call: single identifier followed by ()
        if (path.parts.size == 1 && ctx.check(TokenType.LEFT_PARENTHESES) && ctx.peekNext()?.type == TokenType.RIGHT_PARENTHESES) {
            ctx.advance() // consume (
            ctx.advance() // consume )
            val funcExpr = FunctionCallExpression(path.parts[0].uppercase(), emptyList())
            val alias = ctx.parseOptionalAlias()
            return FieldProjection(funcExpr, alias)
        }

        // Check for arithmetic operators after path expression (e.g., u.age + 5)
        if (ctx.current.type.isArithmeticOperator()) {
            val fullExpr = parseRemainingArithmetic(path)
            val alias = ctx.parseOptionalAlias()
            return FieldProjection(fullExpr, alias)
        }

        val alias = ctx.parseOptionalAlias()
        return FieldProjection(path, alias)
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
            // Parse the right operand - could be a path, literal, or parenthesized expression
            val right = when {
                ctx.check(TokenType.LEFT_PARENTHESES) -> expr.parseExpression()
                ctx.check(TokenType.NUMBER_LITERAL) -> {
                    val v = ctx.current.text.toLongOrNull() ?: ctx.current.text.toDoubleOrNull() ?: ctx.current.text
                    ctx.advance()
                    LiteralExpression(v, LiteralType.NUMBER)
                }
                ctx.check(TokenType.STRING_LITERAL) -> {
                    val v = ctx.current.text
                    ctx.advance()
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
        val right = expr.parseExpression()
        return BinaryExpression(left, op, right)
    }
}


