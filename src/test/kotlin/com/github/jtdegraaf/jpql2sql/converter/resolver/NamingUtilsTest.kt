package com.github.jtdegraaf.jpql2sql.converter.resolver

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingUtilsTest {

    @Test
    fun testSimpleLowercase() {
        assertEquals("name", NamingUtils.toSnakeCase("name"))
    }

    @Test
    fun testSingleWordCapitalized() {
        assertEquals("user", NamingUtils.toSnakeCase("User"))
    }

    @Test
    fun testCamelCaseTwoWords() {
        assertEquals("first_name", NamingUtils.toSnakeCase("firstName"))
    }

    @Test
    fun testCamelCaseThreeWords() {
        assertEquals("email_address_type", NamingUtils.toSnakeCase("emailAddressType"))
    }

    @Test
    fun testPascalCase() {
        assertEquals("bot_rating", NamingUtils.toSnakeCase("BotRating"))
    }

    @Test
    fun testConsecutiveUppercase() {
        assertEquals("html_parser", NamingUtils.toSnakeCase("HTMLParser"))
    }

    @Test
    fun testAlreadySnakeCase() {
        assertEquals("already_snake", NamingUtils.toSnakeCase("already_snake"))
    }

    @Test
    fun testSingleCharacter() {
        assertEquals("u", NamingUtils.toSnakeCase("u"))
    }

    @Test
    fun testAllUppercase() {
        assertEquals("id", NamingUtils.toSnakeCase("ID"))
    }

    @Test
    fun testTrailingUppercase() {
        assertEquals("leaderboard_id", NamingUtils.toSnakeCase("leaderboardId"))
    }
}

