package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class GroupByClauseParserTest {

    private fun parse(fragment: String): GroupByClause {
        val ctx = ParseContext(fragment)
        val expr = ExpressionParser(ctx) { throw UnsupportedOperationException() }
        return GroupByClauseParser(ctx, expr).parse()
    }

    @Test
    fun testSingleGroupBy() {
        val groupBy = parse("GROUP BY u.department")
        assertEquals(1, groupBy.expressions.size)
        assertEquals(listOf("u", "department"), groupBy.expressions[0].parts)
    }

    @Test
    fun testMultipleGroupBy() {
        val groupBy = parse("GROUP BY u.department, u.city")
        assertEquals(2, groupBy.expressions.size)
        assertEquals(listOf("u", "department"), groupBy.expressions[0].parts)
        assertEquals(listOf("u", "city"), groupBy.expressions[1].parts)
    }

    @Test
    fun testGroupByThreeFields() {
        val groupBy = parse("GROUP BY u.a, u.b, u.c")
        assertEquals(3, groupBy.expressions.size)
    }

    @Test
    fun testGroupBySinglePart() {
        val groupBy = parse("GROUP BY department")
        assertEquals(1, groupBy.expressions.size)
        assertEquals(listOf("department"), groupBy.expressions[0].parts)
    }
}

