package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*
import org.junit.Assert.*
import org.junit.Test

class FromClauseParserTest {

    private fun parse(fragment: String): FromClause {
        val ctx = ParseContext(fragment)
        return FromClauseParser(ctx).parse()
    }

    @Test
    fun testSimpleEntity() {
        val from = parse("FROM User u")
        assertEquals("User", from.entity.name)
        assertEquals("u", from.alias)
    }

    @Test
    fun testEntityWithAsAlias() {
        val from = parse("FROM User AS u")
        assertEquals("User", from.entity.name)
        assertEquals("u", from.alias)
    }

    @Test
    fun testDefaultAliasWhenOmitted() {
        val from = parse("FROM User")
        assertEquals("User", from.entity.name)
        assertEquals("user", from.alias)
    }

    @Test
    fun testLongEntityName() {
        val from = parse("FROM BotRating br")
        assertEquals("BotRating", from.entity.name)
        assertEquals("br", from.alias)
    }

    // ──────────── Keyword entity names ──────────────────

    @Test
    fun testEntityNamedOrder() {
        val from = parse("FROM Order o")
        assertEquals("Order", from.entity.name)
        assertEquals("o", from.alias)
    }

    @Test
    fun testEntityNamedGroup() {
        val from = parse("FROM Group g")
        assertEquals("Group", from.entity.name)
        assertEquals("g", from.alias)
    }

    @Test
    fun testEntityNamedIndex() {
        val from = parse("FROM Index idx")
        assertEquals("Index", from.entity.name)
        assertEquals("idx", from.alias)
    }

    @Test
    fun testEntityNamedSize() {
        val from = parse("FROM Size s")
        assertEquals("Size", from.entity.name)
        assertEquals("s", from.alias)
    }

    @Test
    fun testEntityNamedCount() {
        val from = parse("FROM Count c")
        assertEquals("Count", from.entity.name)
        assertEquals("c", from.alias)
    }

    @Test
    fun testEntityNamedSum() {
        val from = parse("FROM Sum s")
        assertEquals("Sum", from.entity.name)
        assertEquals("s", from.alias)
    }

    @Test
    fun testEntityNamedMin() {
        val from = parse("FROM Min m")
        assertEquals("Min", from.entity.name)
        assertEquals("m", from.alias)
    }

    @Test
    fun testEntityNamedMax() {
        val from = parse("FROM Max mx")
        assertEquals("Max", from.entity.name)
        assertEquals("mx", from.alias)
    }

    @Test
    fun testEntityNamedAvg() {
        val from = parse("FROM Avg a")
        assertEquals("Avg", from.entity.name)
        assertEquals("a", from.alias)
    }
}
