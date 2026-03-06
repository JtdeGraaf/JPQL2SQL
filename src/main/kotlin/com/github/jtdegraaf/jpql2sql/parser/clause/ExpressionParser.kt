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
            ctx.expect(TokenType.LEFT_PARENTHESES)
            val operator = if (notIn) BinaryOperator.NOT_IN else BinaryOperator.IN
            if (ctx.check(TokenType.SELECT)) {
                val subquery = subqueryParser()
                ctx.expect(TokenType.RIGHT_PARENTHESES)
                return BinaryExpression(left, operator, SubqueryExpression(subquery))
            }
            val elements = mutableListOf<Expression>()
            do { elements.add(parseExpression()) } while (ctx.match(TokenType.COMMA))
            ctx.expect(TokenType.RIGHT_PARENTHESES)
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
            ctx.match(TokenType.EQUALS) -> BinaryOperator.EQ
            ctx.match(TokenType.NOT_EQUALS) -> BinaryOperator.NE
            ctx.match(TokenType.LESS_THAN) -> BinaryOperator.LT
            ctx.match(TokenType.LESS_THAN_OR_EQUAL) -> BinaryOperator.LE
            ctx.match(TokenType.GREATER_THAN) -> BinaryOperator.GT
            ctx.match(TokenType.GREATER_THAN_OR_EQUAL) -> BinaryOperator.GE
            else -> return left
        }
        return BinaryExpression(left, op, parseAdditiveExpression())
    }

    private fun parseAdditiveExpression(): Expression {
        var left = parseMultiplicativeExpression()
        while (true) {
            left = when {
                ctx.match(TokenType.PLUS) -> BinaryExpression(left, BinaryOperator.ADD, parseMultiplicativeExpression())
                ctx.match(TokenType.MINUS) -> BinaryExpression(left, BinaryOperator.SUBTRACT, parseMultiplicativeExpression())
                ctx.match(TokenType.CONCAT_OP) -> BinaryExpression(left, BinaryOperator.CONCAT, parseMultiplicativeExpression())
                else -> break
            }
        }
        return left
    }

    private fun parseMultiplicativeExpression(): Expression {
        var left = parseUnaryExpression()
        while (true) {
            left = when {
                ctx.match(TokenType.STAR) -> BinaryExpression(left, BinaryOperator.MULTIPLY, parseUnaryExpression())
                ctx.match(TokenType.SLASH) -> BinaryExpression(left, BinaryOperator.DIVIDE, parseUnaryExpression())
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
        if (ctx.match(TokenType.LEFT_PARENTHESES)) {
            if (ctx.check(TokenType.SELECT)) {
                val subquery = subqueryParser()
                ctx.expect(TokenType.RIGHT_PARENTHESES)
                return SubqueryExpression(subquery)
            }
            val expr = parseExpression()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
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

        if (ctx.check(TokenType.EXISTS)) return parseExistsExpression()
        if (ctx.check(TokenType.FUNCTION)) return parseJpqlFunction()
        if (ctx.check(TokenType.CAST)) return parseCastExpression()
        if (ctx.check(TokenType.EXTRACT)) return parseExtractExpression()
        if (ctx.check(TokenType.TRIM)) return parseTrimExpression()
        if (ctx.check(TokenType.TYPE)) return parseTypeExpression()
        if (isFunctionToken(ctx.current.type)) return parseFunctionCall()
        if (isAggregateToken(ctx.current.type)) return parseAggregateInExpression()
        if (ctx.check(TokenType.CASE)) return parseCaseExpression()

        val path = parsePathExpression()

        // Check if this is a parameterless native function call: single identifier followed by ()
        // e.g., SYSDATE() for Oracle's SYSDATE
        // For native functions WITH arguments, use FUNCTION('name', args...) syntax instead
        if (path.parts.size == 1 && ctx.check(TokenType.LEFT_PARENTHESES) && ctx.peekNext()?.type == TokenType.RIGHT_PARENTHESES) {
            ctx.advance() // consume (
            ctx.advance() // consume )
            return FunctionCallExpression(path.parts[0].uppercase(), emptyList())
        }

        return path
    }

    fun parseFunctionCall(): FunctionCallExpression {
        val name = ctx.current.text.uppercase()
        ctx.advance()
        if (!ctx.check(TokenType.LEFT_PARENTHESES)) return FunctionCallExpression(name, emptyList())
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val args = mutableListOf<Expression>()
        if (!ctx.check(TokenType.RIGHT_PARENTHESES)) {
            do { args.add(parseExpression()) } while (ctx.match(TokenType.COMMA))
        }
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return FunctionCallExpression(name, args)
    }

    /**
     * Parses EXISTS (SELECT ...) expression.
     */
    private fun parseExistsExpression(): ExistsExpression {
        ctx.expect(TokenType.EXISTS)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val subquery = subqueryParser()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return ExistsExpression(subquery)
    }

    /**
     * Parses JPQL FUNCTION('native_name', arg1, arg2, ...) syntax.
     * The first argument is the native function name as a string literal.
     * Returns a FunctionCallExpression with the native name and remaining args.
     */
    private fun parseJpqlFunction(): FunctionCallExpression {
        ctx.expect(TokenType.FUNCTION)
        ctx.expect(TokenType.LEFT_PARENTHESES)

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
        ctx.expect(TokenType.RIGHT_PARENTHESES)

        return FunctionCallExpression(nativeFunctionName, args)
    }

    /**
     * Parses CAST(expression AS type) expression.
     */
    private fun parseCastExpression(): CastExpression {
        ctx.expect(TokenType.CAST)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val expression = parseExpression()
        ctx.expect(TokenType.AS)
        val targetType = ctx.expectIdentifierOrKeyword()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return CastExpression(expression, targetType)
    }

    /**
     * Parses EXTRACT(field FROM source) expression.
     */
    private fun parseExtractExpression(): ExtractExpression {
        ctx.expect(TokenType.EXTRACT)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val field = when {
            ctx.match(TokenType.YEAR) -> ExtractField.YEAR
            ctx.match(TokenType.MONTH) -> ExtractField.MONTH
            ctx.match(TokenType.DAY) -> ExtractField.DAY
            ctx.match(TokenType.HOUR) -> ExtractField.HOUR
            ctx.match(TokenType.MINUTE) -> ExtractField.MINUTE
            ctx.match(TokenType.SECOND) -> ExtractField.SECOND
            else -> throw ctx.parseError("Expected date/time field (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND)")
        }
        ctx.expect(TokenType.FROM)
        val source = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return ExtractExpression(field, source)
    }

    /**
     * Parses TRIM([LEADING|TRAILING|BOTH] [char] FROM source) or simple TRIM(source) expression.
     */
    private fun parseTrimExpression(): TrimExpression {
        ctx.expect(TokenType.TRIM)
        ctx.expect(TokenType.LEFT_PARENTHESES)

        val mode = when {
            ctx.match(TokenType.LEADING) -> TrimMode.LEADING
            ctx.match(TokenType.TRAILING) -> TrimMode.TRAILING
            ctx.match(TokenType.BOTH) -> TrimMode.BOTH
            else -> null
        }

        if (mode != null) {
            val trimChar = if (ctx.check(TokenType.STRING_LITERAL)) {
                val char = ctx.current.text
                ctx.advance()
                char
            } else null

            ctx.expect(TokenType.FROM)
            val source = parseExpression()
            ctx.expect(TokenType.RIGHT_PARENTHESES)
            return TrimExpression(mode, trimChar, source)
        }

        // Simple TRIM(expr)
        val source = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return TrimExpression(TrimMode.BOTH, null, source)
    }

    /**
     * Parses TYPE(entity) expression for polymorphic queries.
     */
    private fun parseTypeExpression(): TypeExpression {
        ctx.expect(TokenType.TYPE)
        ctx.expect(TokenType.LEFT_PARENTHESES)
        val alias = ctx.expectIdentifierOrKeyword()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
        return TypeExpression(alias)
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
        ctx.expect(TokenType.LEFT_PARENTHESES)
        if (func == AggregateFunction.COUNT && ctx.check(TokenType.STAR)) {
            ctx.advance(); ctx.expect(TokenType.RIGHT_PARENTHESES)
            return AggregateExpression(func, false, PathExpression(listOf("*")))
        }
        val distinct = ctx.match(TokenType.DISTINCT)
        val expr = parseExpression()
        ctx.expect(TokenType.RIGHT_PARENTHESES)
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
            TokenType.UPPER, TokenType.LOWER, TokenType.LENGTH,
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
