package com.github.jtdegraaf.jpql2sql.parser

class JpqlParser(input: String) {
    private val tokens = JpqlLexer(input).tokens
    private var pos = 0

    private val current: Token get() = tokens[pos]

    fun parse(): JpqlQuery {
        val select = parseSelectClause()
        val from = parseFromClause()
        val joins = parseJoinClauses()
        val where = if (check(TokenType.WHERE)) parseWhereClause() else null
        val groupBy = if (check(TokenType.GROUP)) parseGroupByClause() else null
        val having = if (check(TokenType.HAVING)) parseHavingClause() else null
        val orderBy = if (check(TokenType.ORDER)) parseOrderByClause() else null

        return JpqlQuery(select, from, joins, where, groupBy, having, orderBy)
    }

    private fun parseSelectClause(): SelectClause {
        expect(TokenType.SELECT)
        val distinct = match(TokenType.DISTINCT)
        val projections = mutableListOf<Projection>()

        do {
            projections.add(parseProjection())
        } while (match(TokenType.COMMA))

        return SelectClause(distinct, projections)
    }

    private fun parseProjection(): Projection {
        // Check for constructor expression: NEW com.example.Dto(...)
        if (check(TokenType.NEW)) {
            return parseConstructorProjection()
        }

        // Check for aggregate functions
        val aggregateFunc = when {
            check(TokenType.COUNT) -> AggregateFunction.COUNT
            check(TokenType.SUM) -> AggregateFunction.SUM
            check(TokenType.AVG) -> AggregateFunction.AVG
            check(TokenType.MIN) -> AggregateFunction.MIN
            check(TokenType.MAX) -> AggregateFunction.MAX
            else -> null
        }

        if (aggregateFunc != null) {
            advance()
            expect(TokenType.LPAREN)

            // COUNT(*) special case
            if (aggregateFunc == AggregateFunction.COUNT && check(TokenType.STAR)) {
                advance()
                expect(TokenType.RPAREN)
                val alias = if (match(TokenType.AS)) {
                    expectIdentifier()
                } else null
                return if (alias != null) {
                    AggregateProjection(AggregateFunction.COUNT, false, PathExpression(listOf("*")), alias)
                } else {
                    CountAllProjection
                }
            }

            val distinct = match(TokenType.DISTINCT)
            val expr = parseExpression()
            expect(TokenType.RPAREN)
            val alias = if (match(TokenType.AS)) expectIdentifier() else null

            return AggregateProjection(aggregateFunc, distinct, expr, alias)
        }

        // Regular field projection
        val path = parsePathExpression()
        val alias = if (match(TokenType.AS)) expectIdentifier() else null

        return FieldProjection(path, alias)
    }

    private fun parseConstructorProjection(): ConstructorProjection {
        expect(TokenType.NEW)
        val className = parseQualifiedName()
        expect(TokenType.LPAREN)
        val args = mutableListOf<Expression>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN)
        return ConstructorProjection(className, args)
    }

    private fun parseFromClause(): FromClause {
        expect(TokenType.FROM)
        val entityName = expectIdentifier()
        val alias = if (match(TokenType.AS)) {
            expectIdentifier()
        } else if (check(TokenType.IDENTIFIER)) {
            expectIdentifier()
        } else {
            entityName.lowercase()
        }
        return FromClause(EntityReference(entityName), alias)
    }

    private fun parseJoinClauses(): List<JoinClause> {
        val joins = mutableListOf<JoinClause>()

        while (true) {
            val joinType = when {
                match(TokenType.INNER) -> {
                    expect(TokenType.JOIN)
                    JoinType.INNER
                }
                match(TokenType.LEFT) -> {
                    match(TokenType.OUTER)
                    expect(TokenType.JOIN)
                    JoinType.LEFT
                }
                match(TokenType.RIGHT) -> {
                    match(TokenType.OUTER)
                    expect(TokenType.JOIN)
                    JoinType.RIGHT
                }
                match(TokenType.JOIN) -> JoinType.INNER
                else -> break
            }

            val fetch = match(TokenType.FETCH)
            val path = parsePathExpression()

            val alias = if (match(TokenType.AS)) {
                expectIdentifier()
            } else if (check(TokenType.IDENTIFIER) && !check(TokenType.ON) && !check(TokenType.WHERE)) {
                expectIdentifier()
            } else {
                path.parts.last()
            }

            val condition = if (match(TokenType.ON)) {
                parseExpression()
            } else null

            joins.add(JoinClause(joinType, path, alias, condition))
        }

        return joins
    }

    private fun parseWhereClause(): WhereClause {
        expect(TokenType.WHERE)
        return WhereClause(parseExpression())
    }

    private fun parseGroupByClause(): GroupByClause {
        expect(TokenType.GROUP)
        expect(TokenType.BY)
        val expressions = mutableListOf<PathExpression>()
        do {
            expressions.add(parsePathExpression())
        } while (match(TokenType.COMMA))
        return GroupByClause(expressions)
    }

    private fun parseHavingClause(): HavingClause {
        expect(TokenType.HAVING)
        return HavingClause(parseExpression())
    }

    private fun parseOrderByClause(): OrderByClause {
        expect(TokenType.ORDER)
        expect(TokenType.BY)
        val items = mutableListOf<OrderByItem>()
        do {
            val expr = parseExpression()
            val direction = when {
                match(TokenType.ASC) -> OrderDirection.ASC
                match(TokenType.DESC) -> OrderDirection.DESC
                else -> OrderDirection.ASC
            }
            val nulls = if (match(TokenType.NULLS)) {
                when {
                    match(TokenType.FIRST) -> NullsOrdering.FIRST
                    match(TokenType.LAST) -> NullsOrdering.LAST
                    else -> throw parseError("Expected FIRST or LAST after NULLS")
                }
            } else null
            items.add(OrderByItem(expr, direction, nulls))
        } while (match(TokenType.COMMA))
        return OrderByClause(items)
    }

    private fun parseExpression(): Expression = parseOrExpression()

    private fun parseOrExpression(): Expression {
        var left = parseAndExpression()
        while (match(TokenType.OR)) {
            val right = parseAndExpression()
            left = BinaryExpression(left, BinaryOperator.OR, right)
        }
        return left
    }

    private fun parseAndExpression(): Expression {
        var left = parseNotExpression()
        while (match(TokenType.AND)) {
            val right = parseNotExpression()
            left = BinaryExpression(left, BinaryOperator.AND, right)
        }
        return left
    }

    private fun parseNotExpression(): Expression {
        return if (match(TokenType.NOT)) {
            UnaryExpression(UnaryOperator.NOT, parseNotExpression())
        } else {
            parseComparisonExpression()
        }
    }

    private fun parseComparisonExpression(): Expression {
        val left = parseAdditiveExpression()

        // IS [NOT] NULL / IS [NOT] EMPTY
        if (match(TokenType.IS)) {
            val not = match(TokenType.NOT)
            return when {
                match(TokenType.NULL) -> BinaryExpression(
                    left,
                    if (not) BinaryOperator.IS_NOT_NULL else BinaryOperator.IS_NULL,
                    LiteralExpression(null, LiteralType.NULL)
                )
                match(TokenType.EMPTY) -> BinaryExpression(
                    left,
                    if (not) BinaryOperator.IS_NOT_NULL else BinaryOperator.IS_NULL,
                    LiteralExpression(null, LiteralType.NULL)
                )
                else -> throw parseError("Expected NULL or EMPTY after IS")
            }
        }

        // [NOT] IN
        val notIn = current.type == TokenType.NOT && peekNext()?.type == TokenType.IN
        if (notIn) {
            advance()
        }
        if (match(TokenType.IN)) {
            expect(TokenType.LPAREN)
            val elements = mutableListOf<Expression>()
            do {
                elements.add(parseExpression())
            } while (match(TokenType.COMMA))
            expect(TokenType.RPAREN)
            return BinaryExpression(
                left,
                if (notIn) BinaryOperator.NOT_IN else BinaryOperator.IN,
                InListExpression(elements)
            )
        }

        // [NOT] BETWEEN
        val notBetween = current.type == TokenType.NOT && peekNext()?.type == TokenType.BETWEEN
        if (notBetween) {
            advance()
        }
        if (match(TokenType.BETWEEN)) {
            val lower = parseAdditiveExpression()
            expect(TokenType.AND)
            val upper = parseAdditiveExpression()
            return BinaryExpression(
                left,
                if (notBetween) BinaryOperator.NOT_BETWEEN else BinaryOperator.BETWEEN,
                BetweenExpression(lower, upper)
            )
        }

        // [NOT] LIKE
        val notLike = current.type == TokenType.NOT && peekNext()?.type == TokenType.LIKE
        if (notLike) {
            advance()
        }
        if (match(TokenType.LIKE)) {
            val pattern = parseAdditiveExpression()
            return BinaryExpression(
                left,
                if (notLike) BinaryOperator.NOT_LIKE else BinaryOperator.LIKE,
                pattern
            )
        }

        // [NOT] MEMBER [OF]
        val notMember = current.type == TokenType.NOT && peekNext()?.type == TokenType.MEMBER
        if (notMember) {
            advance()
        }
        if (match(TokenType.MEMBER)) {
            match(TokenType.OF)
            val collection = parsePathExpression()
            return BinaryExpression(
                left,
                if (notMember) BinaryOperator.NOT_MEMBER_OF else BinaryOperator.MEMBER_OF,
                collection
            )
        }

        // Comparison operators
        val op = when {
            match(TokenType.EQ) -> BinaryOperator.EQ
            match(TokenType.NE) -> BinaryOperator.NE
            match(TokenType.LT) -> BinaryOperator.LT
            match(TokenType.LE) -> BinaryOperator.LE
            match(TokenType.GT) -> BinaryOperator.GT
            match(TokenType.GE) -> BinaryOperator.GE
            else -> return left
        }

        val right = parseAdditiveExpression()
        return BinaryExpression(left, op, right)
    }

    private fun parseAdditiveExpression(): Expression {
        var left = parseMultiplicativeExpression()
        while (true) {
            left = when {
                match(TokenType.PLUS) -> BinaryExpression(left, BinaryOperator.EQ, parseMultiplicativeExpression()) // simplified
                match(TokenType.MINUS) -> BinaryExpression(left, BinaryOperator.EQ, parseMultiplicativeExpression()) // simplified
                else -> break
            }
        }
        return left
    }

    private fun parseMultiplicativeExpression(): Expression {
        var left = parseUnaryExpression()
        while (true) {
            left = when {
                match(TokenType.STAR) -> BinaryExpression(left, BinaryOperator.EQ, parseUnaryExpression()) // simplified
                match(TokenType.SLASH) -> BinaryExpression(left, BinaryOperator.EQ, parseUnaryExpression()) // simplified
                else -> break
            }
        }
        return left
    }

    private fun parseUnaryExpression(): Expression {
        return if (match(TokenType.MINUS)) {
            UnaryExpression(UnaryOperator.MINUS, parseUnaryExpression())
        } else {
            parsePrimaryExpression()
        }
    }

    private fun parsePrimaryExpression(): Expression {
        // Parenthesized expression or subquery
        if (match(TokenType.LPAREN)) {
            if (check(TokenType.SELECT)) {
                val subquery = parse()
                expect(TokenType.RPAREN)
                return SubqueryExpression(subquery)
            }
            val expr = parseExpression()
            expect(TokenType.RPAREN)
            return expr
        }

        // Literals
        if (check(TokenType.STRING_LITERAL)) {
            val value = current.text
            advance()
            return LiteralExpression(value, LiteralType.STRING)
        }

        if (check(TokenType.NUMBER_LITERAL)) {
            val value = current.text.toDoubleOrNull() ?: current.text.toLongOrNull() ?: current.text
            advance()
            return LiteralExpression(value, LiteralType.NUMBER)
        }

        if (match(TokenType.TRUE)) {
            return LiteralExpression(true, LiteralType.BOOLEAN)
        }

        if (match(TokenType.FALSE)) {
            return LiteralExpression(false, LiteralType.BOOLEAN)
        }

        if (match(TokenType.NULL)) {
            return LiteralExpression(null, LiteralType.NULL)
        }

        // Parameters
        if (check(TokenType.NAMED_PARAM)) {
            val name = current.text
            advance()
            return ParameterExpression(name, null)
        }

        if (check(TokenType.POSITIONAL_PARAM)) {
            val position = current.text.toIntOrNull() ?: 0
            advance()
            return ParameterExpression(null, position)
        }

        // Functions
        if (isFunctionToken(current.type)) {
            return parseFunctionCall()
        }

        // CASE expression
        if (check(TokenType.CASE)) {
            return parseCaseExpression()
        }

        // Path expression (identifier or qualified path)
        return parsePathExpression()
    }

    private fun parseFunctionCall(): FunctionCallExpression {
        val name = current.text.uppercase()
        advance()

        // Handle functions without parentheses (CURRENT_DATE, etc.)
        if (!check(TokenType.LPAREN)) {
            return FunctionCallExpression(name, emptyList())
        }

        expect(TokenType.LPAREN)
        val args = mutableListOf<Expression>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN)
        return FunctionCallExpression(name, args)
    }

    private fun parseCaseExpression(): CaseExpression {
        expect(TokenType.CASE)

        val operand = if (!check(TokenType.WHEN)) {
            parseExpression()
        } else null

        val whenClauses = mutableListOf<WhenClause>()
        while (match(TokenType.WHEN)) {
            val condition = parseExpression()
            expect(TokenType.THEN)
            val result = parseExpression()
            whenClauses.add(WhenClause(condition, result))
        }

        val elseExpr = if (match(TokenType.ELSE)) {
            parseExpression()
        } else null

        expect(TokenType.END)
        return CaseExpression(operand, whenClauses, elseExpr)
    }

    private fun parsePathExpression(): PathExpression {
        val parts = mutableListOf<String>()

        // First part - must be identifier
        parts.add(expectIdentifier())

        // Additional parts separated by dots
        while (match(TokenType.DOT)) {
            if (check(TokenType.IDENTIFIER)) {
                parts.add(expectIdentifier())
            } else {
                // Handle keywords that can appear as field names
                parts.add(current.text)
                advance()
            }
        }

        return PathExpression(parts)
    }

    private fun parseQualifiedName(): String {
        val parts = mutableListOf<String>()
        parts.add(expectIdentifier())
        while (match(TokenType.DOT)) {
            parts.add(expectIdentifier())
        }
        return parts.joinToString(".")
    }

    private fun isFunctionToken(type: TokenType): Boolean = type in listOf(
        TokenType.UPPER, TokenType.LOWER, TokenType.TRIM, TokenType.LENGTH,
        TokenType.CONCAT, TokenType.SUBSTRING, TokenType.LOCATE,
        TokenType.ABS, TokenType.SQRT, TokenType.MOD, TokenType.SIZE, TokenType.INDEX,
        TokenType.CURRENT_DATE, TokenType.CURRENT_TIME, TokenType.CURRENT_TIMESTAMP,
        TokenType.COALESCE, TokenType.NULLIF, TokenType.TREAT
    )

    private fun check(type: TokenType): Boolean = current.type == type

    private fun match(type: TokenType): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    private fun advance(): Token {
        val token = current
        if (pos < tokens.size - 1) pos++
        return token
    }

    private fun expect(type: TokenType): Token {
        if (!check(type)) {
            throw parseError("Expected $type but found ${current.type}")
        }
        return advance()
    }

    private fun expectIdentifier(): String {
        if (!check(TokenType.IDENTIFIER)) {
            throw parseError("Expected identifier but found ${current.type}")
        }
        return advance().text
    }

    private fun peekNext(): Token? = if (pos + 1 < tokens.size) tokens[pos + 1] else null

    private fun parseError(message: String): JpqlParseException =
        JpqlParseException("$message at position ${current.position}")
}

class JpqlParseException(message: String) : RuntimeException(message)
