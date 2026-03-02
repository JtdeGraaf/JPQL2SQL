package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * Tests for resilient parsing - handling unexpected/unparsed content gracefully.
 */
class ResilientParsingTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addUser(myFixture)
    }

    // ═══════════════════════════════════════════════════════
    //  Trailing unparsed content
    // ═══════════════════════════════════════════════════════

    fun testTrailingUnparsedContent() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE u.name = 'test'
            SOME_UNKNOWN_KEYWORD stuff here
        """.trimIndent())

        println("Trailing unparsed: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain the unknown content", sql.contains("SOME_UNKNOWN_KEYWORD"))
        assertTrue("Should still have the valid WHERE clause", sql.contains("WHERE u.name = 'test'"))
    }

    fun testTrailingUnparsedAfterOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            ORDER BY u.name ASC
            LIMIT 10
        """.trimIndent())

        println("Trailing after ORDER BY: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain LIMIT", sql.contains("LIMIT"))
        assertTrue("Should have ORDER BY", sql.contains("ORDER BY u.name ASC"))
    }

    fun testMultipleTrailingTokens() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE u.id = 1
            FOR UPDATE NOWAIT
        """.trimIndent())

        println("Multiple trailing tokens: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain FOR", sql.contains("FOR"))
        assertTrue("Should contain UPDATE", sql.contains("UPDATE"))
        assertTrue("Should contain NOWAIT", sql.contains("NOWAIT"))
    }

    // ═══════════════════════════════════════════════════════
    //  Mid-query unparsed content (parse-skip-parse)
    // ═══════════════════════════════════════════════════════

    fun testUnparsedBetweenFromAndWhere() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            UNKNOWN_STUFF here
            WHERE u.name = 'test'
        """.trimIndent())

        println("Unparsed between FROM and WHERE: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain the unknown content", sql.contains("UNKNOWN_STUFF"))
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
        assertTrue("Should have WHERE clause after unparsed", sql.contains("WHERE u.name = 'test'"))
    }

    fun testUnparsedBetweenWhereAndOrderBy() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE u.name = 'test'
            CUSTOM_LOCK_HINT
            ORDER BY u.id
        """.trimIndent())

        println("Unparsed between WHERE and ORDER BY: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain the hint", sql.contains("CUSTOM_LOCK_HINT"))
        assertTrue("Should have WHERE clause", sql.contains("WHERE u.name = 'test'"))
        assertTrue("Should have ORDER BY after unparsed", sql.contains("ORDER BY u.id"))
    }

    fun testMultipleUnparsedFragments() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            HINT1 stuff
            WHERE u.name = 'test'
            HINT2 more stuff
            ORDER BY u.id
        """.trimIndent())

        println("Multiple unparsed fragments: $sql")
        assertTrue("Should contain first UNPARSED comment", sql.contains("HINT1"))
        assertTrue("Should contain second UNPARSED comment", sql.contains("HINT2"))
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
        assertTrue("Should have WHERE clause", sql.contains("WHERE u.name = 'test'"))
        assertTrue("Should have ORDER BY clause", sql.contains("ORDER BY u.id"))
    }

    // ═══════════════════════════════════════════════════════
    //  Valid queries should have no unparsed markers
    // ═══════════════════════════════════════════════════════

    fun testValidQueryNoUnparsedMarkers() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE u.name = 'test'
            ORDER BY u.id ASC
        """.trimIndent())

        println("Valid query: $sql")
        assertFalse("Should NOT contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertEquals(
            "SELECT u FROM users u WHERE u.name = 'test' ORDER BY u.id ASC",
            sql
        )
    }

    fun testSimpleSelectNoUnparsedMarkers() {
        val sql = convertWithPostgres("SELECT u FROM User u")

        println("Simple select: $sql")
        assertFalse("Should NOT contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertEquals("SELECT u FROM users u", sql)
    }
}
