package com.github.jtdegraaf.jpql2sql.repository

import org.junit.Assert.*
import org.junit.Test

class DerivedQueryParserTest {

    private val parser = DerivedQueryParser()
    private val entityName = "User"

    @Test
    fun testFindByName() {
        val result = parser.parse("findByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(entityName, result.entityName)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.EQUALS, result.conditions[0].operator)
        assertNull(result.conditions[0].connector)
        assertFalse(result.distinct)
        assertNull(result.limit)
        assertNull(result.orderBy)
    }

    @Test
    fun testFindByNameAndAge() {
        val result = parser.parse("findByNameAndAge", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(2, result.conditions.size)

        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.EQUALS, result.conditions[0].operator)
        assertNull(result.conditions[0].connector)

        assertEquals("age", result.conditions[1].property)
        assertEquals(ConditionOperator.EQUALS, result.conditions[1].operator)
        assertEquals(Connector.AND, result.conditions[1].connector)
    }

    @Test
    fun testFindByNameOrEmail() {
        val result = parser.parse("findByNameOrEmail", entityName)

        assertNotNull(result)
        assertEquals(2, result!!.conditions.size)

        assertEquals("name", result.conditions[0].property)
        assertNull(result.conditions[0].connector)

        assertEquals("email", result.conditions[1].property)
        assertEquals(Connector.OR, result.conditions[1].connector)
    }

    @Test
    fun testFindByAgeGreaterThan() {
        val result = parser.parse("findByAgeGreaterThan", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("age", result.conditions[0].property)
        assertEquals(ConditionOperator.GREATER_THAN, result.conditions[0].operator)
    }

    @Test
    fun testFindByAgeLessThanEqual() {
        val result = parser.parse("findByAgeLessThanEqual", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("age", result.conditions[0].property)
        assertEquals(ConditionOperator.LESS_THAN_EQUAL, result.conditions[0].operator)
    }

    @Test
    fun testFindByNameContaining() {
        val result = parser.parse("findByNameContaining", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.CONTAINING, result.conditions[0].operator)
    }

    @Test
    fun testFindByNameStartingWith() {
        val result = parser.parse("findByNameStartingWith", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.STARTING_WITH, result.conditions[0].operator)
    }

    @Test
    fun testFindByNameEndingWith() {
        val result = parser.parse("findByNameEndingWith", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.ENDING_WITH, result.conditions[0].operator)
    }

    @Test
    fun testFindByNameLike() {
        val result = parser.parse("findByNameLike", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.LIKE, result.conditions[0].operator)
    }

    @Test
    fun testFindByStatusIsNull() {
        val result = parser.parse("findByStatusIsNull", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("status", result.conditions[0].property)
        assertEquals(ConditionOperator.IS_NULL, result.conditions[0].operator)
    }

    @Test
    fun testFindByStatusIsNotNull() {
        val result = parser.parse("findByStatusIsNotNull", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("status", result.conditions[0].property)
        assertEquals(ConditionOperator.IS_NOT_NULL, result.conditions[0].operator)
    }

    @Test
    fun testFindByActiveIsTrue() {
        val result = parser.parse("findByActiveIsTrue", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("active", result.conditions[0].property)
        assertEquals(ConditionOperator.IS_TRUE, result.conditions[0].operator)
    }

    @Test
    fun testFindByActiveIsFalse() {
        val result = parser.parse("findByActiveIsFalse", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("active", result.conditions[0].property)
        assertEquals(ConditionOperator.IS_FALSE, result.conditions[0].operator)
    }

    @Test
    fun testFindByStatusIn() {
        val result = parser.parse("findByStatusIn", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("status", result.conditions[0].property)
        assertEquals(ConditionOperator.IN, result.conditions[0].operator)
    }

    @Test
    fun testFindByStatusNotIn() {
        val result = parser.parse("findByStatusNotIn", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("status", result.conditions[0].property)
        assertEquals(ConditionOperator.NOT_IN, result.conditions[0].operator)
    }

    @Test
    fun testFindByAgeBetween() {
        val result = parser.parse("findByAgeBetween", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("age", result.conditions[0].property)
        assertEquals(ConditionOperator.BETWEEN, result.conditions[0].operator)
    }

    @Test
    fun testFindByCreatedAtBefore() {
        val result = parser.parse("findByCreatedAtBefore", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("createdAt", result.conditions[0].property)
        assertEquals(ConditionOperator.BEFORE, result.conditions[0].operator)
    }

    @Test
    fun testFindByCreatedAtAfter() {
        val result = parser.parse("findByCreatedAtAfter", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("createdAt", result.conditions[0].property)
        assertEquals(ConditionOperator.AFTER, result.conditions[0].operator)
    }

    @Test
    fun testFindTop10ByNameOrderByCreatedAtDesc() {
        val result = parser.parse("findTop10ByNameOrderByCreatedAtDesc", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(10, result.limit)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)

        assertNotNull(result.orderBy)
        assertEquals(1, result.orderBy!!.size)
        assertEquals("createdAt", result.orderBy[0].property)
        assertEquals(Direction.DESC, result.orderBy[0].direction)
    }

    @Test
    fun testFindFirst5ByStatus() {
        val result = parser.parse("findFirst5ByStatus", entityName)

        assertNotNull(result)
        assertEquals(5, result!!.limit)
        assertEquals(1, result.conditions.size)
        assertEquals("status", result.conditions[0].property)
    }

    @Test
    fun testFindTopByOrderByCreatedAtDesc() {
        val result = parser.parse("findTopByOrderByCreatedAtDesc", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.limit)
        assertTrue(result.conditions.isEmpty())
        assertNotNull(result.orderBy)
        assertEquals(1, result.orderBy!!.size)
        assertEquals("createdAt", result.orderBy[0].property)
        assertEquals(Direction.DESC, result.orderBy[0].direction)
    }

    @Test
    fun testCountByStatus() {
        val result = parser.parse("countByStatus", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.COUNT, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("status", result.conditions[0].property)
    }

    @Test
    fun testExistsByEmail() {
        val result = parser.parse("existsByEmail", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.EXISTS, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("email", result.conditions[0].property)
    }

    @Test
    fun testDeleteByStatus() {
        val result = parser.parse("deleteByStatus", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.DELETE, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("status", result.conditions[0].property)
    }

    @Test
    fun testFindDistinctByCategory() {
        val result = parser.parse("findDistinctByCategory", entityName)

        assertNotNull(result)
        assertTrue(result!!.distinct)
        assertEquals(1, result.conditions.size)
        assertEquals("category", result.conditions[0].property)
    }

    @Test
    fun testFindAll() {
        val result = parser.parse("findAll", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertTrue(result.conditions.isEmpty())
        assertNull(result.orderBy)
    }

    @Test
    fun testFindByNameAndAgeGreaterThanAndStatusIn() {
        val result = parser.parse("findByNameAndAgeGreaterThanAndStatusIn", entityName)

        assertNotNull(result)
        assertEquals(3, result!!.conditions.size)

        assertEquals("name", result.conditions[0].property)
        assertEquals(ConditionOperator.EQUALS, result.conditions[0].operator)

        assertEquals("age", result.conditions[1].property)
        assertEquals(ConditionOperator.GREATER_THAN, result.conditions[1].operator)
        assertEquals(Connector.AND, result.conditions[1].connector)

        assertEquals("status", result.conditions[2].property)
        assertEquals(ConditionOperator.IN, result.conditions[2].operator)
        assertEquals(Connector.AND, result.conditions[2].connector)
    }

    @Test
    fun testFindByOrderByNameAsc() {
        val result = parser.parse("findByOrderByNameAsc", entityName)

        assertNotNull(result)
        assertTrue(result!!.conditions.isEmpty())
        assertNotNull(result.orderBy)
        assertEquals(1, result.orderBy!!.size)
        assertEquals("name", result.orderBy[0].property)
        assertEquals(Direction.ASC, result.orderBy[0].direction)
    }

    @Test
    fun testFindByStatusOrderByCreatedAtDescNameAsc() {
        val result = parser.parse("findByStatusOrderByCreatedAtDescNameAsc", entityName)

        assertNotNull(result)
        assertEquals(1, result!!.conditions.size)
        assertEquals("status", result.conditions[0].property)

        assertNotNull(result.orderBy)
        assertEquals(2, result.orderBy!!.size)
        assertEquals("createdAt", result.orderBy[0].property)
        assertEquals(Direction.DESC, result.orderBy[0].direction)
        assertEquals("name", result.orderBy[1].property)
        assertEquals(Direction.ASC, result.orderBy[1].direction)
    }

    @Test
    fun testInvalidMethodNameReturnsNull() {
        val result = parser.parse("someOtherMethod", entityName)
        assertNull(result)
    }

    @Test
    fun testReadByName() {
        val result = parser.parse("readByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
    }

    @Test
    fun testGetByName() {
        val result = parser.parse("getByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
    }

    @Test
    fun testQueryByName() {
        val result = parser.parse("queryByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
    }

    @Test
    fun testSearchByName() {
        val result = parser.parse("searchByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
    }

    @Test
    fun testStreamByName() {
        val result = parser.parse("streamByName", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.FIND, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("name", result.conditions[0].property)
    }

    @Test
    fun testRemoveByStatus() {
        val result = parser.parse("removeByStatus", entityName)

        assertNotNull(result)
        assertEquals(QueryPrefix.DELETE, result!!.prefix)
        assertEquals(1, result.conditions.size)
        assertEquals("status", result.conditions[0].property)
    }
}
