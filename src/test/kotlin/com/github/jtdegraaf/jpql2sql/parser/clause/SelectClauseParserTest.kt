package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class SelectClauseParserTest {

    private fun parse(jpql: String): SelectClause {
        val ctx = ParseContext(jpql)
        val expr = ExpressionParser(ctx) { JpqlParser(jpql).parse() }
        return SelectClauseParser(ctx, expr).parse()
    }

    @Test fun testSingleAlias() { val s = parse("SELECT u"); assertEquals(1, s.projections.size); assertEquals(listOf("u"), ((s.projections[0] as FieldProjection).path as PathExpression).parts) }
    @Test fun testMultipleFields() { val s = parse("SELECT u.id, u.name, u.email"); assertEquals(3, s.projections.size) }
    @Test fun testDistinct() = assertTrue(parse("SELECT DISTINCT u.name").distinct)
    @Test fun testNotDistinct() = assertFalse(parse("SELECT u.name").distinct)
    @Test fun testFieldAlias() = assertEquals("userName", (parse("SELECT u.name AS userName").projections[0] as FieldProjection).alias)
    @Test fun testFieldNoAlias() = assertNull((parse("SELECT u.name").projections[0] as FieldProjection).alias)

    // ──────────── Aggregates ────────────────────────────

    @Test fun testCountStar() = assertTrue(parse("SELECT COUNT(*)").projections[0] is CountAllProjection)
    @Test fun testCountStarWithAlias() { val p = parse("SELECT COUNT(*) AS total").projections[0] as AggregateProjection; assertEquals(AggregateFunction.COUNT, p.function); assertEquals("total", p.alias) }
    @Test fun testCountDistinct() = assertTrue((parse("SELECT COUNT(DISTINCT u.department)").projections[0] as AggregateProjection).distinct)
    @Test fun testSum() = assertEquals(AggregateFunction.SUM, (parse("SELECT SUM(o.amount)").projections[0] as AggregateProjection).function)
    @Test fun testAvg() = assertEquals(AggregateFunction.AVG, (parse("SELECT AVG(o.amount)").projections[0] as AggregateProjection).function)
    @Test fun testMin() = assertEquals(AggregateFunction.MIN, (parse("SELECT MIN(o.amount)").projections[0] as AggregateProjection).function)
    @Test fun testMax() = assertEquals(AggregateFunction.MAX, (parse("SELECT MAX(o.amount)").projections[0] as AggregateProjection).function)
    @Test fun testAggregateWithAlias() = assertEquals("total", (parse("SELECT SUM(o.amount) AS total").projections[0] as AggregateProjection).alias)

    // ──────────── Constructor expressions ───────────────

    @Test fun testConstructorExpression() { val p = parse("SELECT NEW com.example.UserDto(u.id, u.name)").projections[0] as ConstructorProjection; assertEquals("com.example.UserDto", p.className); assertEquals(2, p.arguments.size) }
    @Test fun testConstructorSingleArg() = assertEquals(1, (parse("SELECT NEW com.example.IdDto(u.id)").projections[0] as ConstructorProjection).arguments.size)

    // ──────────── Function projections ──────────────────

    @Test fun testFunctionCallProjection() = assertTrue((parse("SELECT UPPER(u.name)").projections[0] as FieldProjection).path is FunctionCallExpression)
    @Test fun testFunctionCallWithAlias() = assertEquals("upperName", (parse("SELECT UPPER(u.name) AS upperName").projections[0] as FieldProjection).alias)

    // ──────────── Mixed projections ─────────────────────

    @Test
    fun testFieldAndAggregate() {
        val s = parse("SELECT u.department, COUNT(u)")
        assertEquals(2, s.projections.size)
        assertTrue(s.projections[0] is FieldProjection)
        assertTrue(s.projections[1] is AggregateProjection)
    }
}
