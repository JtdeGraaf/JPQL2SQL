package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.converter.EntityResolver
import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class DerivedQueryAstBuilderTest {

    private val parser = DerivedQueryParser()

    /**
     * A stub EntityResolver that doesn't detect any relationships.
     * Used for testing basic AST building without join generation.
     */
    private val simpleEntityResolver = object : EntityResolver(null) {
        override fun isRelationshipField(entityName: String, fieldName: String) = false
        override fun isEmbeddedField(entityName: String, fieldName: String) = false
        override fun resolveTargetEntityName(parentEntityName: String, fieldName: String): String? = null
    }

    /**
     * A stub EntityResolver that detects specific relationships for testing join generation.
     */
    private val relationshipEntityResolver = object : EntityResolver(null) {
        private val relationships = mapOf(
            "Match" to setOf("participants"),
            "MatchParticipant" to setOf("bot"),
            "User" to setOf("address", "department")
        )

        private val targetEntities = mapOf(
            "Match.participants" to "MatchParticipant",
            "MatchParticipant.bot" to "Bot",
            "User.address" to "Address",
            "User.department" to "Department"
        )

        override fun isRelationshipField(entityName: String, fieldName: String): Boolean {
            return relationships[entityName]?.contains(fieldName) == true
        }

        override fun isEmbeddedField(entityName: String, fieldName: String) = false

        override fun resolveTargetEntityName(parentEntityName: String, fieldName: String): String? {
            return targetEntities["$parentEntityName.$fieldName"]
        }
    }

    private fun builderWithSimpleResolver() = DerivedQueryAstBuilder(simpleEntityResolver)
    private fun builderWithRelationships() = DerivedQueryAstBuilder(relationshipEntityResolver)

    @Test
    fun testSimpleQuery() {
        val components = parser.parse("findByName", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertEquals("User", ast.from.entity.name)
        assertEquals("e", ast.from.alias)
        assertTrue(ast.joins.isEmpty())
        assertNotNull(ast.where)
    }

    @Test
    fun testNestedPropertyWithoutRelationship() {
        // When EntityResolver doesn't detect relationships, paths remain simple
        val components = parser.parse("findByAddress_City", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertTrue(ast.joins.isEmpty())
        assertNotNull(ast.where)
        val condition = ast.where!!.condition as BinaryExpression
        val pathExpr = condition.left as PathExpression
        // Without relationship detection, path stays as-is with root alias
        assertEquals("e", pathExpr.parts[0])
    }

    @Test
    fun testNestedPropertyGeneratesJoin() {
        // With relationship-aware resolver, nested property generates JOIN
        val components = parser.parse("findByAddress_City", "User")!!
        val ast = builderWithRelationships().build(components)

        // Should have a JOIN for address
        assertEquals(1, ast.joins.size)
        val join = ast.joins[0]
        assertEquals(JoinType.LEFT, join.type)
        assertEquals(listOf("e", "address"), join.path.parts)
        assertEquals("address_1", join.alias)

        // Where clause should use join alias
        val condition = ast.where!!.condition as BinaryExpression
        val pathExpr = condition.left as PathExpression
        assertEquals(listOf("address_1", "city"), pathExpr.parts)
    }

    @Test
    fun testDeepNestedPropertyGeneratesMultipleJoins() {
        val components = parser.parse("findByParticipants_Bot_IdAndStatus", "Match")!!
        val ast = builderWithRelationships().build(components)

        // Should have 2 joins: participants and bot
        assertEquals(2, ast.joins.size)

        val participantsJoin = ast.joins[0]
        assertEquals(listOf("e", "participants"), participantsJoin.path.parts)
        assertEquals("participants_1", participantsJoin.alias)

        val botJoin = ast.joins[1]
        assertEquals(listOf("participants_1", "bot"), botJoin.path.parts)
        assertEquals("bot_2", botJoin.alias)

        // WHERE clause should use join aliases correctly
        assertNotNull(ast.where)
        val andExpr = ast.where!!.condition as BinaryExpression

        // First condition uses bot_2.id
        val firstCondition = andExpr.left as BinaryExpression
        val firstPath = firstCondition.left as PathExpression
        assertEquals(listOf("bot_2", "id"), firstPath.parts)

        // Second condition uses e.status (no join needed)
        val secondCondition = andExpr.right as BinaryExpression
        val secondPath = secondCondition.left as PathExpression
        assertEquals(listOf("e", "status"), secondPath.parts)
    }

    @Test
    fun testCountQuery() {
        val components = parser.parse("countByStatus", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertEquals(1, ast.select.projections.size)
        val projection = ast.select.projections[0]
        assertTrue(projection is AggregateProjection)
        assertEquals(AggregateFunction.COUNT, (projection as AggregateProjection).function)
    }

    @Test
    fun testDistinctQuery() {
        val components = parser.parse("findDistinctByStatus", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertTrue(ast.select.distinct)
    }

    @Test
    fun testExistsQuery() {
        val components = parser.parse("existsByEmail", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        val projection = ast.select.projections[0]
        assertTrue(projection is FieldProjection)
        val path = (projection as FieldProjection).path
        assertTrue(path is LiteralExpression)
        assertEquals(1, (path as LiteralExpression).value)
    }

    @Test
    fun testQueryWithLimit() {
        val components = parser.parse("findTop10ByStatus", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertNotNull(ast.fetch)
        assertEquals(10, ast.fetch!!.fetchCount)
    }

    @Test
    fun testQueryWithOrderBy() {
        val components = parser.parse("findByStatusOrderByCreatedAtDesc", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertNotNull(ast.orderBy)
        assertEquals(1, ast.orderBy!!.items.size)
        assertEquals(OrderDirection.DESC, ast.orderBy!!.items[0].direction)

        val orderByPath = ast.orderBy!!.items[0].expression as PathExpression
        assertEquals(listOf("e", "createdAt"), orderByPath.parts)
    }

    @Test
    fun testNestedPropertyInOrderByGeneratesJoin() {
        val components = parser.parse("findByStatusOrderByAddress_CityAsc", "User")!!
        val ast = builderWithRelationships().build(components)

        // Should have a join for address
        assertEquals(1, ast.joins.size)
        assertEquals("address_1", ast.joins[0].alias)

        // Order by should use join alias
        assertNotNull(ast.orderBy)
        val orderByPath = ast.orderBy!!.items[0].expression as PathExpression
        assertEquals(listOf("address_1", "city"), orderByPath.parts)
    }

    @Test
    fun testMultipleConditionsWithDifferentOperators() {
        val components = parser.parse("findByNameContainingAndAgeGreaterThanAndStatusIn", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertNotNull(ast.where)
        val firstAnd = ast.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.AND, firstAnd.operator)
    }

    @Test
    fun testBetweenCondition() {
        val components = parser.parse("findByAgeBetween", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertNotNull(ast.where)
        val condition = ast.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.BETWEEN, condition.operator)

        val betweenExpr = condition.right as BetweenExpression
        assertTrue(betweenExpr.lower is ParameterExpression)
        assertTrue(betweenExpr.upper is ParameterExpression)
    }

    @Test
    fun testIsNullCondition() {
        val components = parser.parse("findByDeletedAtIsNull", "User")!!
        val ast = builderWithSimpleResolver().build(components)

        assertNotNull(ast.where)
        val condition = ast.where!!.condition as BinaryExpression
        assertEquals(BinaryOperator.IS_NULL, condition.operator)
    }
}
