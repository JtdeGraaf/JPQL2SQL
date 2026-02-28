package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.MySqlDialect
import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import org.junit.Assert.*
import org.junit.Test

class SqlConverterTest {

    private fun convertWithPostgres(jpql: String): String {
        val parser = JpqlParser(jpql)
        val ast = parser.parse()
        val resolver = MockEntityResolver()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        return converter.convert(ast)
    }

    private fun convertWithMySql(jpql: String): String {
        val parser = JpqlParser(jpql)
        val ast = parser.parse()
        val resolver = MockEntityResolver()
        val converter = SqlConverter(MySqlDialect, resolver)
        return converter.convert(ast)
    }

    @Test
    fun testSimpleSelect() {
        val sql = convertWithPostgres("SELECT u FROM User u")
        assertEquals("SELECT u FROM users u", sql)
    }

    @Test
    fun testSelectFields() {
        val sql = convertWithPostgres("SELECT u.id, u.name FROM User u")
        assertEquals("SELECT u.id, u.name FROM users u", sql)
    }

    @Test
    fun testSelectDistinct() {
        val sql = convertWithPostgres("SELECT DISTINCT u.name FROM User u")
        assertEquals("SELECT DISTINCT u.name FROM users u", sql)
    }

    @Test
    fun testWhereEquals() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = 1")
        assertEquals("SELECT u FROM users u WHERE u.id = 1", sql)
    }

    @Test
    fun testWhereNamedParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = :userId")
        assertEquals("SELECT u FROM users u WHERE u.id = :userId", sql)
    }

    @Test
    fun testWherePositionalParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = ?1")
        assertEquals("SELECT u FROM users u WHERE u.id = ?1", sql)
    }

    @Test
    fun testWhereIsNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NULL", sql)
    }

    @Test
    fun testWhereIsNotNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NOT NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NOT NULL", sql)
    }

    @Test
    fun testWhereIn() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.status IN ('ACTIVE', 'PENDING')")
        assertEquals("SELECT u FROM users u WHERE u.status IN ('ACTIVE', 'PENDING')", sql)
    }

    @Test
    fun testWhereBetween() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.age BETWEEN 18 AND 65")
        assertEquals("SELECT u FROM users u WHERE u.age BETWEEN 18 AND 65", sql)
    }

    @Test
    fun testWhereLike() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name LIKE '%John%'")
        assertEquals("SELECT u FROM users u WHERE u.name LIKE '%John%'", sql)
    }

    @Test
    fun testWhereAnd() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.active = true AND u.age > 18")
        assertEquals("SELECT u FROM users u WHERE (u.active = TRUE AND u.age > 18)", sql)
    }

    @Test
    fun testWhereOr() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.admin = true OR u.moderator = true")
        assertEquals("SELECT u FROM users u WHERE (u.admin = TRUE OR u.moderator = TRUE)", sql)
    }

    @Test
    fun testInnerJoin() {
        val sql = convertWithPostgres("SELECT u FROM User u INNER JOIN u.orders o")
        assertTrue(sql.contains("INNER JOIN"))
        assertTrue(sql.contains("orders"))
    }

    @Test
    fun testLeftJoin() {
        val sql = convertWithPostgres("SELECT u FROM User u LEFT JOIN u.orders o")
        assertTrue(sql.contains("LEFT JOIN"))
    }

    @Test
    fun testOrderByAsc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC", sql)
    }

    @Test
    fun testOrderByDesc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.createdAt DESC")
        assertEquals("SELECT u FROM users u ORDER BY u.created_at DESC", sql)
    }

    @Test
    fun testOrderByNullsFirst() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC NULLS FIRST")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC NULLS FIRST", sql)
    }

    @Test
    fun testGroupBy() {
        val sql = convertWithPostgres("SELECT u.department, COUNT(u) FROM User u GROUP BY u.department")
        assertTrue(sql.contains("GROUP BY u.department"))
    }

    @Test
    fun testCountAll() {
        val sql = convertWithPostgres("SELECT COUNT(*) FROM User u")
        assertEquals("SELECT COUNT(*) FROM users u", sql)
    }

    @Test
    fun testCountDistinct() {
        val sql = convertWithPostgres("SELECT COUNT(DISTINCT u.department) FROM User u")
        assertEquals("SELECT COUNT(DISTINCT u.department) FROM users u", sql)
    }

    @Test
    fun testSum() {
        val sql = convertWithPostgres("SELECT SUM(o.amount) FROM Order o")
        assertEquals("SELECT SUM(o.amount) FROM orders o", sql)
    }

    @Test
    fun testMySqlBooleanLiteral() {
        val sqlPg = convertWithPostgres("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlPg.contains("TRUE"))

        val sqlMy = convertWithMySql("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlMy.contains("TRUE"))
    }

    @Test
    fun testCamelCaseToSnakeCase() {
        val sql = convertWithPostgres("SELECT u.firstName, u.lastName, u.emailAddress FROM User u")
        assertEquals("SELECT u.first_name, u.last_name, u.email_address FROM users u", sql)
    }

    @Test
    fun testStringLiteral() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name = 'John'")
        assertEquals("SELECT u FROM users u WHERE u.name = 'John'", sql)
    }

    @Test
    fun testConstructorProjection() {
        val sql = convertWithPostgres("SELECT NEW com.example.UserDto(u.id, u.name) FROM User u")
        // Constructor expressions become simple column lists
        assertTrue(sql.contains("u.id"))
        assertTrue(sql.contains("u.name"))
    }

    @Test
    fun testComplexQuery() {
        val sql = convertWithPostgres("""
            SELECT u.id, u.name
            FROM User u
            LEFT JOIN u.orders o
            WHERE u.active = true AND u.createdAt > :startDate
            ORDER BY u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("SELECT u.id, u.name"))
        assertTrue(sql.contains("FROM users u"))
        assertTrue(sql.contains("LEFT JOIN"))
        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("ORDER BY u.name ASC"))
    }
}

/**
 * Mock entity resolver for testing without IntelliJ project context
 */
class MockEntityResolver : EntityResolver(null!!) {
    override fun resolveTableName(entityName: String): String {
        return toSnakeCase(entityName) + "s" // Simple pluralization
    }

    override fun resolveColumnName(entityName: String, fieldPath: List<String>): String {
        return toSnakeCase(fieldPath.last())
    }

    override fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo {
        val targetTable = toSnakeCase(fieldName) + "s"
        return JoinInfo(
            columnName = toSnakeCase(fieldName) + "_id",
            referencedColumnName = "id",
            targetTable = targetTable
        )
    }

    companion object {
        fun toSnakeCase(input: String): String {
            return input.replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
                .lowercase()
        }
    }
}
