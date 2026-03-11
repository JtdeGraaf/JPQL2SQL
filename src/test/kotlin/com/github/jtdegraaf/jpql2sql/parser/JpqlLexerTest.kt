package com.github.jtdegraaf.jpql2sql.parser

import org.junit.Assert.*
import org.junit.Test

class JpqlLexerTest {

    @Test
    fun testSimpleSelect() {
        val lexer = JpqlLexer("SELECT u FROM User u")
        val tokens = lexer.tokens

        assertEquals(TokenType.SELECT, tokens[0].type)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("u", tokens[1].text)
        assertEquals(TokenType.FROM, tokens[2].type)
        assertEquals(TokenType.IDENTIFIER, tokens[3].type)
        assertEquals("User", tokens[3].text)
        assertEquals(TokenType.IDENTIFIER, tokens[4].type)
        assertEquals("u", tokens[4].text)
        assertEquals(TokenType.END_OF_FILE, tokens[5].type)
    }

    @Test
    fun testStringLiteral() {
        val lexer = JpqlLexer("WHERE name = 'John'")
        val tokens = lexer.tokens

        assertEquals(TokenType.WHERE, tokens[0].type)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals(TokenType.EQUALS, tokens[2].type)
        assertEquals(TokenType.STRING_LITERAL, tokens[3].type)
        assertEquals("John", tokens[3].text)
    }

    @Test
    fun testEscapedStringLiteral() {
        val lexer = JpqlLexer("WHERE name = 'O''Brien'")
        val tokens = lexer.tokens

        assertEquals(TokenType.STRING_LITERAL, tokens[3].type)
        assertEquals("O'Brien", tokens[3].text)
    }

    @Test
    fun testNamedParameter() {
        val lexer = JpqlLexer("WHERE id = :userId")
        val tokens = lexer.tokens

        assertEquals(TokenType.NAMED_PARAM, tokens[3].type)
        assertEquals("userId", tokens[3].text)
    }

    @Test
    fun testPositionalParameter() {
        val lexer = JpqlLexer("WHERE id = ?1")
        val tokens = lexer.tokens

        assertEquals(TokenType.POSITIONAL_PARAM, tokens[3].type)
        assertEquals("1", tokens[3].text)
    }

    @Test
    fun testNumberLiteral() {
        val lexer = JpqlLexer("WHERE age > 18")
        val tokens = lexer.tokens

        assertEquals(TokenType.NUMBER_LITERAL, tokens[3].type)
        assertEquals("18", tokens[3].text)
    }

    @Test
    fun testComparisonOperators() {
        val lexer = JpqlLexer("a = b AND c <> d AND e < f AND g <= h AND i > j AND k >= l")
        val tokens = lexer.tokens

        assertEquals(TokenType.EQUALS, tokens[1].type)
        assertEquals(TokenType.NOT_EQUALS, tokens[5].type)
        assertEquals(TokenType.LESS_THAN, tokens[9].type)
        assertEquals(TokenType.LESS_THAN_OR_EQUAL, tokens[13].type)
        assertEquals(TokenType.GREATER_THAN, tokens[17].type)
        assertEquals(TokenType.GREATER_THAN_OR_EQUAL, tokens[21].type)
    }

    @Test
    fun testJoinKeywords() {
        val lexer = JpqlLexer("INNER JOIN LEFT OUTER JOIN RIGHT JOIN")
        val tokens = lexer.tokens

        assertEquals(TokenType.INNER, tokens[0].type)
        assertEquals(TokenType.JOIN, tokens[1].type)
        assertEquals(TokenType.LEFT, tokens[2].type)
        assertEquals(TokenType.OUTER, tokens[3].type)
        assertEquals(TokenType.JOIN, tokens[4].type)
        assertEquals(TokenType.RIGHT, tokens[5].type)
        assertEquals(TokenType.JOIN, tokens[6].type)
    }

    @Test
    fun testOrderByKeywords() {
        val lexer = JpqlLexer("ORDER BY name ASC NULLS FIRST")
        val tokens = lexer.tokens

        assertEquals(TokenType.ORDER, tokens[0].type)
        assertEquals(TokenType.BY, tokens[1].type)
        assertEquals(TokenType.ASC, tokens[3].type)
        assertEquals(TokenType.NULLS, tokens[4].type)
        assertEquals(TokenType.FIRST, tokens[5].type)
    }

    @Test
    fun testAggregateKeywords() {
        val lexer = JpqlLexer("COUNT SUM AVG MIN MAX")
        val tokens = lexer.tokens

        assertEquals(TokenType.COUNT, tokens[0].type)
        assertEquals(TokenType.SUM, tokens[1].type)
        assertEquals(TokenType.AVG, tokens[2].type)
        assertEquals(TokenType.MIN, tokens[3].type)
        assertEquals(TokenType.MAX, tokens[4].type)
    }

    @Test
    fun testSpelNamedParameter() {
        val lexer = JpqlLexer("WHERE id = :#{#userId}")
        val tokens = lexer.tokens

        assertEquals(TokenType.SPEL_PARAM, tokens[3].type)
        assertEquals(":#{#userId}", tokens[3].text)
    }

    @Test
    fun testSpelPositionalParameter() {
        val lexer = JpqlLexer("WHERE id = ?#{[0]}")
        val tokens = lexer.tokens

        assertEquals(TokenType.SPEL_PARAM, tokens[3].type)
        assertEquals("?#{[0]}", tokens[3].text)
    }

    @Test
    fun testSpelStandaloneParameter() {
        val lexer = JpqlLexer("FROM #{#entityName} e")
        val tokens = lexer.tokens

        assertEquals(TokenType.FROM, tokens[0].type)
        assertEquals(TokenType.SPEL_PARAM, tokens[1].type)
        assertEquals("#{#entityName}", tokens[1].text)
        assertEquals(TokenType.IDENTIFIER, tokens[2].type)
    }

    @Test
    fun testSpelWithPropertyAccessor() {
        val lexer = JpqlLexer("WHERE owner = :#{#authentication.principal.username}")
        val tokens = lexer.tokens

        assertEquals(TokenType.SPEL_PARAM, tokens[3].type)
        assertEquals(":#{#authentication.principal.username}", tokens[3].text)
    }

    @Test
    fun testSpelWithNestedBraces() {
        val lexer = JpqlLexer("WHERE type IN :#{T(com.example.Type).values()}")
        val tokens = lexer.tokens

        assertEquals(TokenType.SPEL_PARAM, tokens[3].type)
        assertEquals(":#{T(com.example.Type).values()}", tokens[3].text)
    }
}
