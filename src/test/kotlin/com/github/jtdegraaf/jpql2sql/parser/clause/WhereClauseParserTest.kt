package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class WhereClauseParserTest {

    private fun parse(fragment: String): WhereClause {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return WhereClauseParser(ctx, expr).parse()
    }

    // ──────────── Basic conditions ──────────────────────

    @Test fun testSimpleEquals() = assertEquals(BinaryOperator.EQ, (parse("WHERE u.active = true").condition as BinaryExpression).operator)
    @Test fun testNamedParameter() = assertEquals("userId", (((parse("WHERE u.id = :userId").condition as BinaryExpression).right) as ParameterExpression).name)
    @Test fun testPositionalParameter() = assertEquals(1, (((parse("WHERE u.id = ?1").condition as BinaryExpression).right) as ParameterExpression).position)

    // ──────────── IS NULL / IS NOT NULL ─────────────────

    @Test fun testIsNull() = assertEquals(BinaryOperator.IS_NULL, (parse("WHERE u.deletedAt IS NULL").condition as BinaryExpression).operator)
    @Test fun testIsNotNull() = assertEquals(BinaryOperator.IS_NOT_NULL, (parse("WHERE u.deletedAt IS NOT NULL").condition as BinaryExpression).operator)

    // ──────────── IN / NOT IN ───────────────────────────

    @Test
    fun testIn() {
        val cond = parse("WHERE u.status IN ('ACTIVE', 'PENDING')").condition as BinaryExpression
        assertEquals(BinaryOperator.IN, cond.operator)
        assertEquals(2, (cond.right as InListExpression).elements.size)
    }

    @Test fun testNotIn() = assertEquals(BinaryOperator.NOT_IN, (parse("WHERE u.status NOT IN ('DELETED')").condition as BinaryExpression).operator)

    // ──────────── BETWEEN ───────────────────────────────

    @Test
    fun testBetween() {
        val cond = parse("WHERE u.age BETWEEN 18 AND 65").condition as BinaryExpression
        assertEquals(BinaryOperator.BETWEEN, cond.operator)
        assertTrue(cond.right is BetweenExpression)
    }

    @Test fun testNotBetween() = assertEquals(BinaryOperator.NOT_BETWEEN, (parse("WHERE u.age NOT BETWEEN 0 AND 17").condition as BinaryExpression).operator)

    // ──────────── LIKE ──────────────────────────────────

    @Test fun testLike() = assertEquals(BinaryOperator.LIKE, (parse("WHERE u.name LIKE '%john%'").condition as BinaryExpression).operator)
    @Test fun testNotLike() = assertEquals(BinaryOperator.NOT_LIKE, (parse("WHERE u.name NOT LIKE '%spam%'").condition as BinaryExpression).operator)

    // ──────────── Logical operators ─────────────────────

    @Test fun testAndCondition() = assertEquals(BinaryOperator.AND, (parse("WHERE u.active = true AND u.age > 18").condition as BinaryExpression).operator)
    @Test fun testOrCondition() = assertEquals(BinaryOperator.OR, (parse("WHERE u.active = true OR u.admin = true").condition as BinaryExpression).operator)

    @Test
    fun testAndOrPrecedence() {
        val or = parse("WHERE u.a = 1 OR u.b = 2 AND u.c = 3").condition as BinaryExpression
        assertEquals(BinaryOperator.OR, or.operator)
        assertEquals(BinaryOperator.AND, (or.right as BinaryExpression).operator)
    }

    // ──────────── Comparison operators ──────────────────

    @Test fun testEq() = assertEquals(BinaryOperator.EQ, (parse("WHERE u.x = 1").condition as BinaryExpression).operator)
    @Test fun testNe() = assertEquals(BinaryOperator.NE, (parse("WHERE u.x <> 1").condition as BinaryExpression).operator)
    @Test fun testLt() = assertEquals(BinaryOperator.LT, (parse("WHERE u.x < 1").condition as BinaryExpression).operator)
    @Test fun testLe() = assertEquals(BinaryOperator.LE, (parse("WHERE u.x <= 1").condition as BinaryExpression).operator)
    @Test fun testGt() = assertEquals(BinaryOperator.GT, (parse("WHERE u.x > 1").condition as BinaryExpression).operator)
    @Test fun testGe() = assertEquals(BinaryOperator.GE, (parse("WHERE u.x >= 1").condition as BinaryExpression).operator)
}
