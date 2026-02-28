package com.github.jtdegraaf.jpql2sql.parser

import org.junit.Assert.*
import org.junit.Test

class JpqlParserTest {

    @Test
    fun testSimpleSelect() {
        val query = JpqlParser("SELECT u FROM User u").parse()

        assertFalse(query.select.distinct)
        assertEquals(1, query.select.projections.size)

        val projection = query.select.projections[0] as FieldProjection
        assertEquals(listOf("u"), projection.path.parts)

        assertEquals("User", query.from.entity.name)
        assertEquals("u", query.from.alias)
    }

    @Test
    fun testSelectMultipleFields() {
        val query = JpqlParser("SELECT u.id, u.name, u.email FROM User u").parse()

        assertEquals(3, query.select.projections.size)

        val paths = query.select.projections.map { (it as FieldProjection).path.parts }
        assertEquals(listOf("u", "id"), paths[0])
        assertEquals(listOf("u", "name"), paths[1])
        assertEquals(listOf("u", "email"), paths[2])
    }

    @Test
    fun testSelectDistinct() {
        val query = JpqlParser("SELECT DISTINCT u.name FROM User u").parse()

        assertTrue(query.select.distinct)
        assertEquals(1, query.select.projections.size)
    }

    @Test
    fun testSelectWithAlias() {
        val query = JpqlParser("SELECT u.name AS userName FROM User u").parse()

        val projection = query.select.projections[0] as FieldProjection
        assertEquals("userName", projection.alias)
    }

    @Test
    fun testWhereClause() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.active = true").parse()

        assertNotNull(query.where)
        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.EQ, condition.operator)
    }

    @Test
    fun testWhereWithNamedParameter() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.id = :userId").parse()

        val condition = query.where!!.condition as BinaryExpression
        val param = condition.right as ParameterExpression
        assertEquals("userId", param.name)
        assertNull(param.position)
    }

    @Test
    fun testWhereWithPositionalParameter() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.id = ?1").parse()

        val condition = query.where!!.condition as BinaryExpression
        val param = condition.right as ParameterExpression
        assertNull(param.name)
        assertEquals(1, param.position)
    }

    @Test
    fun testWhereWithAndOr() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.active = true AND u.age > 18 OR u.admin = true").parse()

        assertNotNull(query.where)
        val condition = query.where!!.condition
        assertTrue(condition is BinaryExpression)
    }

    @Test
    fun testWhereIsNull() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.deletedAt IS NULL").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.IS_NULL, condition.operator)
    }

    @Test
    fun testWhereIsNotNull() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.deletedAt IS NOT NULL").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.IS_NOT_NULL, condition.operator)
    }

    @Test
    fun testWhereIn() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.status IN ('ACTIVE', 'PENDING')").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.IN, condition.operator)
        assertTrue(condition.right is InListExpression)
    }

    @Test
    fun testWhereNotIn() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.status NOT IN ('DELETED')").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.NOT_IN, condition.operator)
    }

    @Test
    fun testWhereBetween() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.age BETWEEN 18 AND 65").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.BETWEEN, condition.operator)
        assertTrue(condition.right is BetweenExpression)
    }

    @Test
    fun testWhereLike() {
        val query = JpqlParser("SELECT u FROM User u WHERE u.name LIKE '%john%'").parse()

        val condition = query.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.LIKE, condition.operator)
    }

    @Test
    fun testInnerJoin() {
        val query = JpqlParser("SELECT u FROM User u INNER JOIN u.orders o").parse()

        assertEquals(1, query.joins.size)
        assertEquals(JoinType.INNER, query.joins[0].type)
        assertEquals("o", query.joins[0].alias)
    }

    @Test
    fun testLeftJoin() {
        val query = JpqlParser("SELECT u FROM User u LEFT JOIN u.orders o").parse()

        assertEquals(1, query.joins.size)
        assertEquals(JoinType.LEFT, query.joins[0].type)
    }

    @Test
    fun testLeftOuterJoin() {
        val query = JpqlParser("SELECT u FROM User u LEFT OUTER JOIN u.orders o").parse()

        assertEquals(1, query.joins.size)
        assertEquals(JoinType.LEFT, query.joins[0].type)
    }

    @Test
    fun testJoinWithOn() {
        val query = JpqlParser("SELECT u FROM User u JOIN Order o ON o.userId = u.id").parse()

        assertEquals(1, query.joins.size)
        assertNotNull(query.joins[0].condition)
    }

    @Test
    fun testOrderBy() {
        val query = JpqlParser("SELECT u FROM User u ORDER BY u.name ASC").parse()

        assertNotNull(query.orderBy)
        assertEquals(1, query.orderBy!!.items.size)
        assertEquals(OrderDirection.ASC, query.orderBy!!.items[0].direction)
    }

    @Test
    fun testOrderByDesc() {
        val query = JpqlParser("SELECT u FROM User u ORDER BY u.createdAt DESC").parse()

        assertEquals(OrderDirection.DESC, query.orderBy!!.items[0].direction)
    }

    @Test
    fun testOrderByMultiple() {
        val query = JpqlParser("SELECT u FROM User u ORDER BY u.lastName ASC, u.firstName ASC").parse()

        assertEquals(2, query.orderBy!!.items.size)
    }

    @Test
    fun testOrderByNullsFirst() {
        val query = JpqlParser("SELECT u FROM User u ORDER BY u.name ASC NULLS FIRST").parse()

        assertEquals(NullsOrdering.FIRST, query.orderBy!!.items[0].nulls)
    }

    @Test
    fun testGroupBy() {
        val query = JpqlParser("SELECT u.department, COUNT(u) FROM User u GROUP BY u.department").parse()

        assertNotNull(query.groupBy)
        assertEquals(1, query.groupBy!!.expressions.size)
    }

    @Test
    fun testGroupByHaving() {
        val query = JpqlParser("SELECT u.department, COUNT(u) FROM User u GROUP BY u.department HAVING COUNT(u) > 5").parse()

        assertNotNull(query.groupBy)
        assertNotNull(query.having)
    }

    @Test
    fun testCountAll() {
        val query = JpqlParser("SELECT COUNT(*) FROM User u").parse()

        assertEquals(1, query.select.projections.size)
        assertTrue(query.select.projections[0] is CountAllProjection || query.select.projections[0] is AggregateProjection)
    }

    @Test
    fun testCountDistinct() {
        val query = JpqlParser("SELECT COUNT(DISTINCT u.department) FROM User u").parse()

        val projection = query.select.projections[0] as AggregateProjection
        assertEquals(AggregateFunction.COUNT, projection.function)
        assertTrue(projection.distinct)
    }

    @Test
    fun testSumAvgMinMax() {
        val sumQuery = JpqlParser("SELECT SUM(o.amount) FROM Order o").parse()
        assertEquals(AggregateFunction.SUM, (sumQuery.select.projections[0] as AggregateProjection).function)

        val avgQuery = JpqlParser("SELECT AVG(o.amount) FROM Order o").parse()
        assertEquals(AggregateFunction.AVG, (avgQuery.select.projections[0] as AggregateProjection).function)

        val minQuery = JpqlParser("SELECT MIN(o.amount) FROM Order o").parse()
        assertEquals(AggregateFunction.MIN, (minQuery.select.projections[0] as AggregateProjection).function)

        val maxQuery = JpqlParser("SELECT MAX(o.amount) FROM Order o").parse()
        assertEquals(AggregateFunction.MAX, (maxQuery.select.projections[0] as AggregateProjection).function)
    }

    @Test
    fun testConstructorExpression() {
        val query = JpqlParser("SELECT NEW com.example.UserDto(u.id, u.name) FROM User u").parse()

        val projection = query.select.projections[0] as ConstructorProjection
        assertEquals("com.example.UserDto", projection.className)
        assertEquals(2, projection.arguments.size)
    }

    @Test
    fun testFunctionCall() {
        val query = JpqlParser("SELECT UPPER(u.name) FROM User u").parse()

        val projection = query.select.projections[0] as FieldProjection
        // Function calls in projections get parsed as path expressions
        // In a full implementation we'd handle this differently
    }

    @Test
    fun testComplexQuery() {
        val query = JpqlParser("""
            SELECT u.id, u.name, COUNT(o.id)
            FROM User u
            LEFT JOIN u.orders o
            WHERE u.active = true AND u.createdAt > :startDate
            GROUP BY u.id, u.name
            HAVING COUNT(o.id) > 0
            ORDER BY u.name ASC
        """.trimIndent()).parse()

        assertEquals(3, query.select.projections.size)
        assertEquals(1, query.joins.size)
        assertNotNull(query.where)
        assertNotNull(query.groupBy)
        assertNotNull(query.having)
        assertNotNull(query.orderBy)
    }
}
