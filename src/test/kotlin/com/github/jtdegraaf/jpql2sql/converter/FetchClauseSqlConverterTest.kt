package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for FETCH FIRST / OFFSET clause.
 */
class FetchClauseSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addUser(myFixture)
    }

    // ═══════════════════════════════════════════════════════
    //  FETCH FIRST n ROWS ONLY - PostgreSQL
    // ═══════════════════════════════════════════════════════

    fun testFetchFirstRowsOnly() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("FETCH FIRST ROWS ONLY: $sql")
        assertEquals("SELECT u FROM users u LIMIT 10", sql)
    }

    fun testFetchFirstRowOnly() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u FETCH FIRST 1 ROW ONLY
        """.trimIndent())

        println("FETCH FIRST ROW ONLY: $sql")
        assertEquals("SELECT u FROM users u LIMIT 1", sql)
    }

    fun testFetchNextRowsOnly() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u FETCH NEXT 5 ROWS ONLY
        """.trimIndent())

        println("FETCH NEXT ROWS ONLY: $sql")
        assertEquals("SELECT u FROM users u LIMIT 5", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  OFFSET with FETCH - PostgreSQL
    // ═══════════════════════════════════════════════════════

    fun testOffsetWithFetch() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("OFFSET with FETCH: $sql")
        assertEquals("SELECT u FROM users u LIMIT 10 OFFSET 20", sql)
    }

    fun testOffsetWithoutRowsKeyword() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u OFFSET 5 FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("OFFSET without ROWS: $sql")
        assertEquals("SELECT u FROM users u LIMIT 10 OFFSET 5", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  With ORDER BY - PostgreSQL
    // ═══════════════════════════════════════════════════════

    fun testFetchWithOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u ORDER BY u.name ASC FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("FETCH with ORDER BY: $sql")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC LIMIT 10", sql)
    }

    fun testOffsetFetchWithOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u ORDER BY u.id DESC OFFSET 100 ROWS FETCH FIRST 25 ROWS ONLY
        """.trimIndent())

        println("OFFSET FETCH with ORDER BY: $sql")
        assertEquals("SELECT u FROM users u ORDER BY u.id DESC LIMIT 25 OFFSET 100", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  With WHERE clause - PostgreSQL
    // ═══════════════════════════════════════════════════════

    fun testFetchWithWhere() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE u.name = 'test' FETCH FIRST 5 ROWS ONLY
        """.trimIndent())

        println("FETCH with WHERE: $sql")
        assertEquals("SELECT u FROM users u WHERE u.name = 'test' LIMIT 5", sql)
    }

    fun testFetchWithWhereAndOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE u.id > 100 ORDER BY u.name FETCH FIRST 20 ROWS ONLY
        """.trimIndent())

        println("FETCH with WHERE and ORDER BY: $sql")
        assertEquals("SELECT u FROM users u WHERE u.id > 100 ORDER BY u.name ASC LIMIT 20", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  Oracle dialect
    // ═══════════════════════════════════════════════════════

    fun testOracleFetchFirst() {
        val sql = convertWithOracle("""
            SELECT u FROM User u FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("Oracle FETCH FIRST: $sql")
        assertEquals("SELECT u FROM users u FETCH FIRST 10 ROWS ONLY", sql)
    }

    fun testOracleOffsetFetch() {
        val sql = convertWithOracle("""
            SELECT u FROM User u OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("Oracle OFFSET FETCH: $sql")
        assertEquals("SELECT u FROM users u OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY", sql)
    }

    fun testOracleFetchWithOrderBy() {
        val sql = convertWithOracle("""
            SELECT u FROM User u ORDER BY u.id DESC FETCH FIRST 5 ROWS ONLY
        """.trimIndent())

        println("Oracle FETCH with ORDER BY: $sql")
        assertEquals("SELECT u FROM users u ORDER BY u.id DESC FETCH FIRST 5 ROWS ONLY", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  MySQL dialect
    // ═══════════════════════════════════════════════════════

    fun testMySqlFetchFirst() {
        val sql = convertWithMySql("""
            SELECT u FROM User u FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("MySQL FETCH FIRST: $sql")
        assertEquals("SELECT u FROM users u LIMIT 10", sql)
    }

    fun testMySqlOffsetFetch() {
        val sql = convertWithMySql("""
            SELECT u FROM User u OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY
        """.trimIndent())

        println("MySQL OFFSET FETCH: $sql")
        // MySQL uses LIMIT offset, count syntax
        assertEquals("SELECT u FROM users u LIMIT 20, 10", sql)
    }
}
