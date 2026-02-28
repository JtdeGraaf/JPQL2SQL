package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class OrderByClauseParserTest {

    private fun parse(fragment: String): OrderByClause {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return OrderByClauseParser(ctx, expr).parse()
    }

    // ──────────── Direction ─────────────────────────────

    @Test fun testAsc() = assertEquals(OrderDirection.ASC, parse("ORDER BY u.name ASC").items[0].direction)
    @Test fun testDesc() = assertEquals(OrderDirection.DESC, parse("ORDER BY u.createdAt DESC").items[0].direction)
    @Test fun testDefaultDirectionIsAsc() = assertEquals(OrderDirection.ASC, parse("ORDER BY u.name").items[0].direction)

    // ──────────── NULLS ordering ────────────────────────

    @Test fun testNullsFirst() = assertEquals(NullsOrdering.FIRST, parse("ORDER BY u.name ASC NULLS FIRST").items[0].nulls)
    @Test fun testNullsLast() = assertEquals(NullsOrdering.LAST, parse("ORDER BY u.name DESC NULLS LAST").items[0].nulls)
    @Test fun testNoNullsOrdering() = assertNull(parse("ORDER BY u.name ASC").items[0].nulls)

    // ──────────── Multiple items ────────────────────────

    @Test fun testMultipleItems() = assertEquals(2, parse("ORDER BY u.lastName ASC, u.firstName ASC").items.size)

    @Test
    fun testMultipleMixedDirections() {
        val orderBy = parse("ORDER BY u.name ASC, u.age DESC")
        assertEquals(OrderDirection.ASC, orderBy.items[0].direction)
        assertEquals(OrderDirection.DESC, orderBy.items[1].direction)
    }

    @Test
    fun testThreeItems() {
        val orderBy = parse("ORDER BY u.a ASC, u.b DESC, u.c ASC")
        assertEquals(3, orderBy.items.size)
        assertEquals(OrderDirection.ASC, orderBy.items[0].direction)
        assertEquals(OrderDirection.DESC, orderBy.items[1].direction)
        assertEquals(OrderDirection.ASC, orderBy.items[2].direction)
    }

    // ──────────── Path ──────────────────────────────────

    @Test fun testOrderByPath() = assertEquals(listOf("u", "department", "name"), (parse("ORDER BY u.department.name ASC").items[0].expression as PathExpression).parts)
}
