package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses JPQL expressions — the recursive-descent core shared by all clause parsers.
 */
class ExpressionParser(
    private val ctx: ParseContext,
    private val subqueryParser: () -> JpqlQuery
) {

    fun parseExpression(): Expression = parseOrExpression()

    fun parsePathExpression(): PathExpression {
        val parts = mutableListOf<String>()
        parts.add(ctx.expectIdentifierOrKeyword())
        while (ctx.match(TokenType.DOT)) {
            if (ctx.check(TokenType.IDENTIFIER)) {
                parts.add(ctx.expectIdentifier())
            } else {
                parts.add(ctx.current.text)
                ctx.advance()
            }
        }
        return PathExpression(parts)
    }

    fun parseQualifiedName(): String {
        val parts = mutableListOf<String>()
        parts.add(ctx.expectIdentifierOrKeyword())
        while (ctx.match(TokenType.DOT)) {
            parts.add(ctx.expectIdentifierOrKeyword())
        }
        return parts.joinToString(".")
    }

    private fun parseOrExpression(): Expression {
        var left = parseAndExpression()
        while (ctx.match(TokenType.OR)) {
            left = BinaryExpression(left, BinaryOperator.OR, parseAndExpression())
        }
        return left
    }

    private fun parseAndExpression(): Expression {
        var left = parseNotExpression()
        while (ctx.match(TokenType.AND)) {
            left = BinaryExpression(left, BinaryOperator.AND, parseNotExpression())
        }
        return left
    }

    private fun parseNotExpression(): Expression {
        return if (ctx.match(TokenType.NOT)) {
            UnaryExpression(UnaryOperator.NOT, parseNotExpression())
        } else {
            parseComparisonExpression()
        }
    }

    private fun parseComparisonExpression(): Expression {
        val left = parseAdditiveExpression()

        if (ctx.match(TokenType.IS)) {
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

        val notIn = ctx.current.type == TokenType.NOT && ctx.peekNext()?.type == TokenType.IN
        if (notIn) ctx.advance()
        if (ctx.match(TokenType.IN)) {
            ctx.expect(TokenType.LPAREN)
            val operator = if (notIn) BinaryOperator.NOT_IN else BinaryOperator.IN
            if (ctx.check(TokenType.SELECT)) {
                val subquery = subqueryParser()
                ctx.expect(TokenType.RPAREN)
                return BinaryExpression(left, operator, SubqueryExpression(subquery))
            }
            val elements = mutableListOf<Expression>()
            do { elements.add(parseExpression()) } while (ctx.match(TokenType.COMMA))
            ctx.expect(TokenType.RPAREN)
            return BinaryExpression(left, operator, InListExpression(elements))
        }

        val notBetween = ctx.current.type == TokenType.NOT && ctx.peekNext()?.type == TokenType.BETWEEN
        if (notBetween) ctx.advance()
        if (ctx.match(TokenType.BETWEEN)) {
            val lower = parseAdditiveExpression()
            ctx.expect(TokenType.AND)
            val upper = parseAdditiveExpression()
            return BinaryExpression(left, if (notBetween) BinaryOperator.NOT_BETWEEN else BinaryOperator.BETWEEN, BetweenExpression(lower, upper))
        }

        val notLike = ctx.current.type == TokenType.NOT && ctx.peekNext()?.type == TokenType.LIKE
        if (notLike) ctx.advance()
        if (ctx.match(TokenType.LIKE)) {
            return BinaryExpression(left, if (notLike) BinaryOperator.NOT_LIKE else BinaryOperator.LIKE, parseAdditiveExpression())
        }

        val notMember = ctx.current.type == TokenType.NOT && ctx.peekNext()?.type == TokenType.MEMBER
        if (notMember) ctx.advance()
        if (ctx.match(TokenType.MEMBER)) {
            ctx.match(TokenType.OF)
            return BinaryExpression(left, if (notMember) BinaryOperator.NOT_MEMBER_OF else BinaryOperator.MEMBER_OF, parsePathExpression())
        }

        val op = when {
            ctx.match(TokenType.EQ) -> BinaryOperator.EQ
            ctx.match(TokenType.NE) -> BinaryOperator.NE
            ctx.match(TokenType.LT) -> BinaryOperator.LT
            ctx.match(TokenType.LE) -> BinaryOperator.LE
            ctx.match(TokenType.GT) -> BinaryOperator.GT
            ctx.match(TokenType.GE) -> BinaryOperator.GE
            else -> return left
        }
        return BinaryExpression(left, op, parseAdditiveExpression())
    }

    private fun parseAdditiveExpression(): Expression {
        var left = parseMultiplicativeExpression()
        while (true) {
            left = when {
                ctx.match(TokenType.PLUS) -> BinaryExpression(left, BinaryOperator.EQ, parseMultiplicativeExpression())
                ctx.match(TokenType.MINUS) -> BinaryExpression(left, BinaryOperator.EQ, parseMultiplicativeExpression())
                else -> break
            }
        }
        return left
    }

    private fun parseMultiplicativeExpression(): Expression {
        var left = parseUnaryExpression()
        while (true) {
            left = when {
                ctx.match(TokenType.STAR) -> BinaryExpression(left, BinaryOperator.EQ, parseUnaryExpression())
                ctx.match(TokenType.SLASH) -> BinaryExpression(left, BinaryOperator.EQ, parseUnaryExpression())
                else -> break
            }
        }
        return left
    }

    private fun parseUnaryExpression(): Expression {
        return if (ctx.match(TokenType.MINUS)) {
            UnaryExpression(UnaryOperator.MINUS, parseUnaryExpression())
        } else {
            parsePrimaryExpression()
        }
    }

    private fun parsePrimaryExpression(): Expression {
        if (ctx.match(TokenType.LPAREN)) {
            if (ctx.check(TokenType.SELECT)) {
                val subquery = subqueryParser()
                ctx.expect(TokenType.RPAREN)
                return SubqueryExpression(subquery)
            }
            val expr = parseExpression()
            ctx.expect(TokenType.RPAREN)
            return expr
        }

        if (ctx.check(TokenType.STRING_LITERAL)) { val v = ctx.current.text; ctx.advance(); return LiteralExpression(v, LiteralType.STRING) }
        if (ctx.check(TokenType.NUMBER_LITERAL)) {
            val v = ctx.current.text.toLongOrNull() ?: ctx.current.text.toDoubleOrNull() ?: ctx.current.text
            ctx.advance()
            return LiteralExpression(v, LiteralType.NUMBER)
        }
        if (ctx.match(TokenType.TRUE)) return LiteralExpression(true, LiteralType.BOOLEAN)
        if (ctx.match(TokenType.FALSE)) return LiteralExpression(false, LiteralType.BOOLEAN)
        if (ctx.match(TokenType.NULL)) return LiteralExpression(null, LiteralType.NULL)

        if (ctx.check(TokenType.NAMED_PARAM)) { val n = ctx.current.text; ctx.advance(); return ParameterExpression(n, null) }
        if (ctx.check(TokenType.POSITIONAL_PARAM)) { val p = ctx.current.text.toIntOrNull() ?: 0; ctx.advance(); return ParameterExpression(null, p) }

        if (ctx.check(TokenType.FUNCTION)) return parseJpqlFunction()
        if (isFunctionToken(ctx.current.type)) return parseFunctionCall()
        if (isAggregateToken(ctx.current.type)) return parseAggregateInExpression()
        if (ctx.check(TokenType.CASE)) return parseCaseExpression()

        return parsePathExpression()
    }

    fun parseFunctionCall(): FunctionCallExpression {
        val name = ctx.current.text.uppercase()
        ctx.advance()
        if (!ctx.check(TokenType.LPAREN)) return FunctionCallExpression(name, emptyList())
        ctx.expect(TokenType.LPAREN)
        val args = mutableListOf<Expression>()
        if (!ctx.check(TokenType.RPAREN)) {
            do { args.add(parseExpression()) } while (ctx.match(TokenType.COMMA))
        }
        ctx.expect(TokenType.RPAREN)
        return FunctionCallExpression(name, args)
    }

    /**
     * Parses JPQL FUNCTION('native_name', arg1, arg2, ...) syntax.
     * The first argument is the native function name as a string literal.
     * Returns a FunctionCallExpression with the native name and remaining args.
     */
    private fun parseJpqlFunction(): FunctionCallExpression {
        ctx.expect(TokenType.FUNCTION)
        ctx.expect(TokenType.LPAREN)

        // First argument must be the function name as a string literal
        if (!ctx.check(TokenType.STRING_LITERAL)) {
            throw ctx.parseError("FUNCTION requires a string literal as the first argument (function name)")
        }
        val nativeFunctionName = ctx.current.text
        ctx.advance()

        // Parse remaining arguments
        val args = mutableListOf<Expression>()
        while (ctx.match(TokenType.COMMA)) {
            args.add(parseExpression())
        }
        ctx.expect(TokenType.RPAREN)

        return FunctionCallExpression(nativeFunctionName, args)
    }

    private fun parseAggregateInExpression(): Expression {
        val func = when (ctx.current.type) {
            TokenType.COUNT -> AggregateFunction.COUNT
            TokenType.SUM -> AggregateFunction.SUM
            TokenType.AVG -> AggregateFunction.AVG
            TokenType.MIN -> AggregateFunction.MIN
            TokenType.MAX -> AggregateFunction.MAX
            else -> throw ctx.parseError("Expected aggregate function")
        }
        ctx.advance()
        ctx.expect(TokenType.LPAREN)
        if (func == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
            ctx.advance(); ctx.expect(TokenType.RPAREN)
            return AggregateExpression(func, false, PathExpression(listOf("*")))
        }
        val distinct = ctx.match(TokenType.DISTINCT)
        val expr = parseExpression()
        ctx.expect(TokenType.RPAREN)
        return AggregateExpression(func, distinct, expr)
    }

    private fun parseCaseExpression(): CaseExpression {
        ctx.expect(TokenType.CASE)
        val operand = if (!ctx.check(TokenType.WHEN)) parseExpression() else null
        val whenClauses = mutableListOf<WhenClause>()
        while (ctx.match(TokenType.WHEN)) {
            val c = parseExpression(); ctx.expect(TokenType.THEN)
            whenClauses.add(WhenClause(c, parseExpression()))
        }
        val elseExpr = if (ctx.match(TokenType.ELSE)) parseExpression() else null
        ctx.expect(TokenType.END)
        return CaseExpression(operand, whenClauses, elseExpr)
    }

    companion object {
        fun isFunctionToken(type: TokenType): Boolean = type in setOf(
            TokenType.UPPER, TokenType.LOWER, TokenType.TRIM, TokenType.LENGTH,
            TokenType.CONCAT, TokenType.SUBSTRING, TokenType.LOCATE,
            TokenType.ABS, TokenType.SQRT, TokenType.MOD, TokenType.SIZE, TokenType.INDEX,
            TokenType.CURRENT_DATE, TokenType.CURRENT_TIME, TokenType.CURRENT_TIMESTAMP,
            TokenType.COALESCE, TokenType.NULLIF, TokenType.TREAT
        )

        fun isAggregateToken(type: TokenType): Boolean = type in setOf(
            TokenType.COUNT, TokenType.SUM, TokenType.AVG, TokenType.MIN, TokenType.MAX
        )
    }
}
