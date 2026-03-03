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

    // ═══════════════════════════════════════════════════════
    //  Unparsed content in SELECT clause projections
    // ═══════════════════════════════════════════════════════

    fun testUnparsedProjectionWithUnknownSyntax() {
        val sql = convertWithPostgres("""
            SELECT u.name, SOME_UNKNOWN_FUNC(u.id, @special) FROM User u
        """.trimIndent())

        println("Unparsed projection with unknown syntax: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain the unknown function", sql.contains("SOME_UNKNOWN_FUNC"))
        assertTrue("Should have u.name projection", sql.contains("u.name"))
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
    }

    fun testUnparsedProjectionInMiddle() {
        val sql = convertWithPostgres("""
            SELECT u.id, WEIRD :: SYNTAX(here), u.name FROM User u
        """.trimIndent())

        println("Unparsed projection in middle: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should have u.id", sql.contains("u.id"))
        assertTrue("Should have u.name", sql.contains("u.name"))
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
    }

    fun testUnparsedProjectionWithSpecialCharacters() {
        val sql = convertWithPostgres("""
            SELECT u.name, u.id#special FROM User u
        """.trimIndent())

        println("Unparsed projection with special chars: $sql")
        // The parser may handle this differently - just verify it doesn't crash
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
        assertTrue("Should have u.name", sql.contains("u.name"))
    }

    fun testMixedValidAndUnparsedProjections() {
        // Garbage @@ should be captured as unparsed, but u.name after the comma should still parse
        val sql = convertWithPostgres("""
            SELECT u.id, COUNT(u), UNKNOWN_EXPRESSION@@, u.name FROM User u
        """.trimIndent())

        println("Mixed valid and unparsed projections: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should have u.id", sql.contains("u.id"))
        assertTrue("Should have COUNT", sql.contains("COUNT"))
        assertTrue("Should have UNKNOWN_EXPRESSION", sql.contains("UNKNOWN_EXPRESSION"))
        assertTrue("Should have u.name (parsed after garbage)", sql.contains("u.name"))
    }

    fun testUnparsedInLastProjection() {
        // Unparsed content at the end, other projections should be fine
        val sql = convertWithPostgres("""
            SELECT u.id, u.name, @@weird FROM User u
        """.trimIndent())

        println("Unparsed in last projection: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain @@weird (concatenated)", sql.contains("@@weird"))
        assertTrue("Should have u.id", sql.contains("u.id"))
        assertTrue("Should have u.name", sql.contains("u.name"))
    }

    fun testGarbageBetweenProjectionsCaptured() {
        // Test that garbage between projections is captured and subsequent projections still parse
        val sql = convertWithPostgres("""
            SELECT u.id @@garbage, u.name FROM User u
        """.trimIndent())

        println("Garbage between projections: $sql")
        assertTrue("Should contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should contain @@garbage (concatenated)", sql.contains("@@garbage"))
        assertTrue("Should have u.id", sql.contains("u.id"))
        assertTrue("Should have u.name (after garbage)", sql.contains("u.name"))
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
    }

    fun testUnparsedConstructorProjection() {
        val sql = convertWithPostgres("""
            SELECT NEW com.example.Dto(u.id, WEIRD!SYNTAX) FROM User u
        """.trimIndent())

        println("Unparsed constructor projection: $sql")
        // Constructor with weird syntax should be captured as unparsed
        assertTrue("Should have FROM clause", sql.contains("FROM users u"))
    }

    fun testValidProjectionsNoUnparsedMarkers() {
        val sql = convertWithPostgres("""
            SELECT u.id, u.name, COUNT(u.id), MAX(u.id) FROM User u GROUP BY u.id, u.name
        """.trimIndent())

        println("Valid projections: $sql")
        assertFalse("Should NOT contain UNPARSED comment", sql.contains("/* UNPARSED:"))
        assertTrue("Should have u.id", sql.contains("u.id"))
        assertTrue("Should have u.name", sql.contains("u.name"))
        assertTrue("Should have COUNT", sql.contains("COUNT"))
        assertTrue("Should have MAX", sql.contains("MAX"))
    }
}
