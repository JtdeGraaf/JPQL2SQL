package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for JPQL CAST expressions.
 * JPQL uses abstract types: String, Integer, Long, Float, Double, BigDecimal, BigInteger, Date, Time, Timestamp
 * These are mapped to SQL types: VARCHAR, INTEGER, BIGINT, REAL, DOUBLE PRECISION, DECIMAL, NUMERIC, DATE, TIME, TIMESTAMP
 */
class CastSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addUser(myFixture)
    }

    // ═══════════════════════════════════════════════════════
    //  Basic CAST expressions with JPQL types -> SQL types
    // ═══════════════════════════════════════════════════════

    fun testCastToString() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS String) FROM User u
        """.trimIndent())

        println("CAST to String: $sql")
        assertEquals("SELECT CAST(u.id AS VARCHAR) FROM users u", sql)
    }

    fun testCastToInteger() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.name AS Integer) FROM User u
        """.trimIndent())

        println("CAST to Integer: $sql")
        assertEquals("SELECT CAST(u.name AS INTEGER) FROM users u", sql)
    }

    fun testCastToLong() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.name AS Long) FROM User u
        """.trimIndent())

        println("CAST to Long: $sql")
        assertEquals("SELECT CAST(u.name AS BIGINT) FROM users u", sql)
    }

    fun testCastToDouble() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS Double) FROM User u
        """.trimIndent())

        println("CAST to Double: $sql")
        assertEquals("SELECT CAST(u.id AS DOUBLE PRECISION) FROM users u", sql)
    }

    fun testCastToFloat() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS Float) FROM User u
        """.trimIndent())

        println("CAST to Float: $sql")
        assertEquals("SELECT CAST(u.id AS REAL) FROM users u", sql)
    }

    fun testCastToBigDecimal() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS BigDecimal) FROM User u
        """.trimIndent())

        println("CAST to BigDecimal: $sql")
        assertEquals("SELECT CAST(u.id AS DECIMAL) FROM users u", sql)
    }

    fun testCastToBigInteger() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS BigInteger) FROM User u
        """.trimIndent())

        println("CAST to BigInteger: $sql")
        assertEquals("SELECT CAST(u.id AS NUMERIC) FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  CAST in WHERE clause
    // ═══════════════════════════════════════════════════════

    fun testCastInWhere() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE CAST(u.id AS String) = '123'
        """.trimIndent())

        println("CAST in WHERE: $sql")
        assertEquals("SELECT u FROM users u WHERE CAST(u.id AS VARCHAR) = '123'", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  CAST with expressions
    // ═══════════════════════════════════════════════════════

    fun testCastWithLiteral() {
        val sql = convertWithPostgres("""
            SELECT CAST('123' AS Integer) FROM User u
        """.trimIndent())

        println("CAST with literal: $sql")
        assertEquals("SELECT CAST('123' AS INTEGER) FROM users u", sql)
    }

    fun testCastWithParameter() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE CAST(:value AS Integer) > 0
        """.trimIndent())

        println("CAST with parameter: $sql")
        assertEquals("SELECT u FROM users u WHERE CAST(:value AS INTEGER) > 0", sql)
    }

    fun testCastWithFunction() {
        val sql = convertWithPostgres("""
            SELECT CAST(LENGTH(u.name) AS Double) FROM User u
        """.trimIndent())

        println("CAST with function: $sql")
        assertEquals("SELECT CAST(LENGTH(u.name) AS DOUBLE PRECISION) FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  CAST with alias
    // ═══════════════════════════════════════════════════════

    fun testCastWithAlias() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS String) AS idString FROM User u
        """.trimIndent())

        println("CAST with alias: $sql")
        assertEquals("SELECT CAST(u.id AS VARCHAR) AS idString FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  Nested CAST
    // ═══════════════════════════════════════════════════════

    fun testNestedCast() {
        val sql = convertWithPostgres("""
            SELECT CAST(CAST(u.name AS Integer) AS String) FROM User u
        """.trimIndent())

        println("Nested CAST: $sql")
        assertEquals("SELECT CAST(CAST(u.name AS INTEGER) AS VARCHAR) FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  CAST in complex expressions
    // ═══════════════════════════════════════════════════════

    fun testCastInComparison() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE CAST(u.id AS String) LIKE '1%'
        """.trimIndent())

        println("CAST in comparison: $sql")
        assertEquals("SELECT u FROM users u WHERE CAST(u.id AS VARCHAR) LIKE '1%'", sql)
    }

    fun testCastInOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u ORDER BY CAST(u.name AS Integer) ASC
        """.trimIndent())

        println("CAST in ORDER BY: $sql")
        assertEquals("SELECT u FROM users u ORDER BY CAST(u.name AS INTEGER) ASC", sql)
    }

    fun testMultipleCastsInQuery() {
        val sql = convertWithPostgres("""
            SELECT CAST(u.id AS String), CAST(u.name AS Integer) FROM User u
        """.trimIndent())

        println("Multiple CASTs: $sql")
        assertEquals("SELECT CAST(u.id AS VARCHAR), CAST(u.name AS INTEGER) FROM users u", sql)
    }
}
