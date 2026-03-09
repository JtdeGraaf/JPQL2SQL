package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import com.github.jtdegraaf.jpql2sql.parser.expression.*

/**
 * Parses JPQL expressions — the recursive-descent core shared by all clause parsers.
 *
 * Delegates specialized expression parsing to focused parsers:
 * - [FunctionExpressionParser] - UPPER, LOWER, CAST, EXTRACT, TRIM, etc.
 * - [AggregateExpressionParser] - COUNT, SUM, AVG, MIN, MAX
 * - [CaseExpressionParser] - CASE WHEN ... END
 * - [ComparisonExpressionParser] - =, <>, IN, BETWEEN, LIKE, IS NULL, etc.
 * - [SubqueryExpressionParser] - EXISTS, scalar subqueries
 */
class ExpressionParser(
    private val ctx: ParseContext,
    private val subqueryParser: () -> JpqlQuery
) {
    // Delegate parsers for specialized expression types
    private val functionParser = FunctionExpressionParser(ctx, ::parseExpression)
    private val aggregateParser = AggregateExpressionParser(ctx, ::parseExpression)
    private val caseParser = CaseExpressionParser(ctx, ::parseExpression)
    private val comparisonParser = ComparisonExpressionParser(
        ctx, ::parseExpression, ::parseAdditiveExpression, ::parsePathExpression, subqueryParser
    )
    private val subqueryExprParser = SubqueryExpressionParser(ctx, subqueryParser)

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
        return comparisonParser.parseComparison(left) ?: left
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
        // Parenthesized expression or subquery
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

        // Literals and parameters
        ctx.parseLiteralExpression()?.let { return it }
        ctx.parseParameterExpression()?.let { return it }

        // Delegated expression types
        if (ctx.check(TokenType.EXISTS)) return subqueryExprParser.parseExistsExpression()
        if (ctx.check(TokenType.FUNCTION)) return functionParser.parseJpqlFunction()
        if (ctx.check(TokenType.CAST)) return functionParser.parseCastExpression()
        if (ctx.check(TokenType.EXTRACT)) return functionParser.parseExtractExpression()
        if (ctx.check(TokenType.TRIM)) return functionParser.parseTrimExpression()
        if (ctx.check(TokenType.TYPE)) return functionParser.parseTypeExpression()
        if (ctx.current.type.isFunction()) return functionParser.parseFunctionCall()
        if (ctx.current.type.isAggregate()) return aggregateParser.parseAggregate()
        if (ctx.check(TokenType.CASE)) return caseParser.parseCaseExpression()

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

    /**
     * Parses a standard function call - delegates to [FunctionExpressionParser].
     */
    fun parseFunctionCall(): FunctionCallExpression = functionParser.parseFunctionCall()
}
