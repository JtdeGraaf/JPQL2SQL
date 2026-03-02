package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for User and Order entities.
 */
class UserSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addAll(myFixture)
    }

    fun testSimpleSelect() {
        val sql = convertWithPostgres("SELECT u FROM User u")
        assertEquals("SELECT u FROM users u", sql)
    }

    fun testSelectFields() {
        val sql = convertWithPostgres("SELECT u.id, u.name FROM User u")
        assertEquals("SELECT u.id, u.name FROM users u", sql)
    }

    fun testSelectDistinct() {
        val sql = convertWithPostgres("SELECT DISTINCT u.name FROM User u")
        assertEquals("SELECT DISTINCT u.name FROM users u", sql)
    }

    fun testWhereEquals() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = 1")
        assertEquals("SELECT u FROM users u WHERE u.id = 1", sql)
    }

    fun testWhereNamedParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = :userId")
        assertEquals("SELECT u FROM users u WHERE u.id = :userId", sql)
    }

    fun testWherePositionalParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = ?1")
        assertEquals("SELECT u FROM users u WHERE u.id = ?1", sql)
    }

    fun testWhereIsNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NULL", sql)
    }

    fun testWhereIsNotNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NOT NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NOT NULL", sql)
    }

    fun testWhereIn() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.status IN ('ACTIVE', 'PENDING')")
        assertEquals("SELECT u FROM users u WHERE u.status IN ('ACTIVE', 'PENDING')", sql)
    }

    fun testWhereBetween() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.age BETWEEN 18 AND 65")
        assertEquals("SELECT u FROM users u WHERE u.age BETWEEN 18 AND 65", sql)
    }

    fun testWhereLike() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name LIKE '%John%'")
        assertEquals("SELECT u FROM users u WHERE u.name LIKE '%John%'", sql)
    }

    fun testWhereAnd() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.active = true AND u.age > 18")
        assertEquals("SELECT u FROM users u WHERE u.active = TRUE AND u.age > 18", sql)
    }

    fun testWhereOr() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.admin = true OR u.moderator = true")
        assertEquals("SELECT u FROM users u WHERE u.admin = TRUE OR u.moderator = TRUE", sql)
    }

    fun testOrderByAsc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC", sql)
    }

    fun testOrderByDesc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.createdAt DESC")
        assertEquals("SELECT u FROM users u ORDER BY u.created_at DESC", sql)
    }

    fun testOrderByNullsFirst() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC NULLS FIRST")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC NULLS FIRST", sql)
    }

    fun testGroupBy() {
        val sql = convertWithPostgres("SELECT u.department, COUNT(u) FROM User u GROUP BY u.department")
        assertTrue(sql.contains("GROUP BY u.department"))
    }

    fun testCamelCaseToSnakeCase() {
        val sql = convertWithPostgres("SELECT u.firstName, u.lastName, u.emailAddress FROM User u")
        assertEquals("SELECT u.first_name, u.last_name, u.email_address FROM users u", sql)
    }

    fun testStringLiteral() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name = 'John'")
        assertEquals("SELECT u FROM users u WHERE u.name = 'John'", sql)
    }

    fun testConstructorProjection() {
        val sql = convertWithPostgres("SELECT NEW com.example.UserDto(u.id, u.name) FROM User u")
        assertTrue(sql.contains("u.id"))
        assertTrue(sql.contains("u.name"))
    }

    fun testComplexQuery() {
        val sql = convertWithPostgres("""
            SELECT u.id, u.name
            FROM User u
            WHERE u.active = true AND u.createdAt > :startDate
            ORDER BY u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("SELECT u.id, u.name"))
        assertTrue(sql.contains("FROM users u"))
        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("ORDER BY u.name ASC"))
    }

    fun testMySqlBooleanLiteral() {
        val sqlPg = convertWithPostgres("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlPg.contains("TRUE"))

        val sqlMy = convertWithMySql("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlMy.contains("TRUE"))
    }
}
