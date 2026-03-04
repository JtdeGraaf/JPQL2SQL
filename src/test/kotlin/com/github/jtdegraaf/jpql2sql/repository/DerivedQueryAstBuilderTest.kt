package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class DerivedQueryAstBuilderTest {

    private val parser = DerivedQueryParser()
    private val builder = DerivedQueryAstBuilder()

    @Test
    fun testSimpleQuery() {
        val components = parser.parse("findByName", "User")!!
        val ast = builder.build(components)

        assertEquals("User", ast.from.entity.name)
        assertEquals("e", ast.from.alias)
        assertTrue(ast.joins.isEmpty())
        assertNotNull(ast.where)
    }

    @Test
    fun testNestedPropertyGeneratesJoin() {
        val components = parser.parse("findByAddress_City", "User")!!
        val ast = builder.build(components)

        assertEquals(1, ast.joins.size)
        val join = ast.joins[0]
        assertEquals(JoinType.LEFT, join.type)
        assertEquals("e", join.path.parts[0])
        assertEquals("address", join.path.parts[1])
        assertEquals("address_1", join.alias)
    }

    @Test
    fun testDeepNestedPropertyGeneratesMultipleJoins() {
        val components = parser.parse("findByParticipants_Bot_IdAndStatus", "Match")!!
        val ast = builder.build(components)

        // Should have 2 joins: one for participants, one for bot
        assertEquals(2, ast.joins.size)

        val participantsJoin = ast.joins[0]
        assertEquals("e", participantsJoin.path.parts[0])
        assertEquals("participants", participantsJoin.path.parts[1])
        assertEquals("participants_1", participantsJoin.alias)

        val botJoin = ast.joins[1]
        assertEquals("participants_1", botJoin.path.parts[0])
        assertEquals("bot", botJoin.path.parts[1])
        assertEquals("bot_2", botJoin.alias)

        // WHERE clause should reference the join aliases correctly
        assertNotNull(ast.where)
    }

    @Test
    fun testCountQuery() {
        val components = parser.parse("countByStatus", "User")!!
        val ast = builder.build(components)

        assertEquals(1, ast.select.projections.size)
        val projection = ast.select.projections[0]
        assertTrue(projection is AggregateProjection)
        assertEquals(AggregateFunction.COUNT, (projection as AggregateProjection).function)
    }

    @Test
    fun testDistinctQuery() {
        val components = parser.parse("findDistinctByStatus", "User")!!
        val ast = builder.build(components)

        assertTrue(ast.select.distinct)
    }

    @Test
    fun testExistsQuery() {
        val components = parser.parse("existsByEmail", "User")!!
        val ast = builder.build(components)

        val projection = ast.select.projections[0]
        assertTrue(projection is FieldProjection)
        val path = (projection as FieldProjection).path
        assertTrue(path is LiteralExpression)
        assertEquals(1, (path as LiteralExpression).value)
    }

    @Test
    fun testQueryWithLimit() {
        val components = parser.parse("findTop10ByStatus", "User")!!
        val ast = builder.build(components)

        assertNotNull(ast.fetch)
        assertEquals(10, ast.fetch!!.fetchCount)
    }

    @Test
    fun testQueryWithOrderBy() {
        val components = parser.parse("findByStatusOrderByCreatedAtDesc", "User")!!
        val ast = builder.build(components)

        assertNotNull(ast.orderBy)
        assertEquals(1, ast.orderBy!!.items.size)
        assertEquals(OrderDirection.DESC, ast.orderBy!!.items[0].direction)
    }

    @Test
    fun testNestedPropertyInOrderBy() {
        val components = parser.parse("findByStatusOrderByAddress_CityAsc", "User")!!
        val ast = builder.build(components)

        // Should have a join for address
        assertEquals(1, ast.joins.size)
        assertEquals("address", ast.joins[0].path.parts[1])

        // Order by should use the join alias
        assertNotNull(ast.orderBy)
        val orderByPath = ast.orderBy!!.items[0].expression as PathExpression
        assertEquals("address_1", orderByPath.parts[0])
        assertEquals("city", orderByPath.parts[1])
    }
}
