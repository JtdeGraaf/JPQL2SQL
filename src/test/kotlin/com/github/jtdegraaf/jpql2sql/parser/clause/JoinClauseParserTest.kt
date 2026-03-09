package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class JoinClauseParserTest {

    private fun parse(fragment: String): List<JoinClause> {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return JoinClauseParser(ctx, expr).parse()
    }

    // ──────────── Join types ────────────────────────────

    @Test fun testInnerJoin() = assertEquals(JoinType.INNER, parse("INNER JOIN u.orders o")[0].type)
    @Test fun testPlainJoinDefaultsToInner() = assertEquals(JoinType.INNER, parse("JOIN u.orders o")[0].type)
    @Test fun testLeftJoin() = assertEquals(JoinType.LEFT, parse("LEFT JOIN u.orders o")[0].type)
    @Test fun testLeftOuterJoin() = assertEquals(JoinType.LEFT, parse("LEFT OUTER JOIN u.orders o")[0].type)
    @Test fun testRightJoin() = assertEquals(JoinType.RIGHT, parse("RIGHT JOIN u.orders o")[0].type)
    @Test fun testRightOuterJoin() = assertEquals(JoinType.RIGHT, parse("RIGHT OUTER JOIN u.orders o")[0].type)

    // ──────────── Path and alias ────────────────────────

    @Test fun testJoinPath() = assertEquals(listOf("u", "orders"), parse("JOIN u.orders o")[0].path.parts)
    @Test fun testJoinAlias() = assertEquals("o", parse("JOIN u.orders o")[0].alias)
    @Test fun testJoinWithAsAlias() = assertEquals("o", parse("JOIN u.orders AS o")[0].alias)
    @Test fun testJoinDefaultAliasWhenOmitted() = assertEquals("orders", parse("JOIN u.orders")[0].alias)

    // ──────────── ON condition ──────────────────────────

    @Test fun testJoinWithOnCondition() { val j = parse("JOIN Order o ON o.userId = u.id"); assertNotNull(j[0].condition); assertEquals(BinaryOperator.EQUALS, (j[0].condition as BinaryExpression).operator) }
    @Test fun testJoinWithoutOnCondition() = assertNull(parse("JOIN u.orders o")[0].condition)

    // ──────────── Keyword entity names ──────────────────

    @Test fun testJoinEntityNamedOrder() = assertEquals(listOf("Order"), parse("JOIN Order o ON o.userId = u.id")[0].path.parts)

    // ──────────── Multiple joins ────────────────────────

    @Test
    fun testMultipleJoins() {
        val joins = parse("LEFT JOIN u.orders o INNER JOIN u.address a")
        assertEquals(2, joins.size)
        assertEquals(JoinType.LEFT, joins[0].type)
        assertEquals(JoinType.INNER, joins[1].type)
    }

    @Test fun testNoJoins() = assertTrue(parse("").isEmpty())
}
