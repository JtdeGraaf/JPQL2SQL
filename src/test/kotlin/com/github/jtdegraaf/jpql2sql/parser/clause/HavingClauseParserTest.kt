package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class HavingClauseParserTest {

    private fun parse(fragment: String): HavingClause {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return HavingClauseParser(ctx, expr).parse()
    }

    @Test
    fun testHavingWithAggregate() {
        val having = parse("HAVING COUNT(u) > 5")
        val cond = having.condition as BinaryExpression
        assertEquals(BinaryOperator.GREATER_THAN, cond.operator)
        assertTrue(cond.left is AggregateExpression)
    }

    @Test
    fun testHavingCountStar() {
        val having = parse("HAVING COUNT(*) >= 10")
        val cond = having.condition as BinaryExpression
        assertEquals(BinaryOperator.GREATER_THAN_OR_EQUAL, cond.operator)
        val agg = cond.left as AggregateExpression
        assertEquals(AggregateFunction.COUNT, agg.function)
    }

    @Test
    fun testHavingSum() {
        val having = parse("HAVING SUM(o.amount) > 100")
        val cond = having.condition as BinaryExpression
        val agg = cond.left as AggregateExpression
        assertEquals(AggregateFunction.SUM, agg.function)
    }

    @Test
    fun testHavingWithAnd() {
        val having = parse("HAVING COUNT(u) > 0 AND SUM(u.amount) < 1000")
        val cond = having.condition as BinaryExpression
        assertEquals(BinaryOperator.AND, cond.operator)
    }

    @Test
    fun testHavingSimpleComparison() {
        val having = parse("HAVING u.total > 50")
        val cond = having.condition as BinaryExpression
        assertEquals(BinaryOperator.GREATER_THAN, cond.operator)
    }
}

