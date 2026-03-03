package com.github.jtdegraaf.jpql2sql.parser

import org.junit.Assert.*
import org.junit.Test

class ParseContextTest {

    @Test
    fun testCheckMatchesCurrentToken() {
        val ctx = ParseContext("SELECT u")
        assertTrue(ctx.check(TokenType.SELECT))
        assertFalse(ctx.check(TokenType.FROM))
    }

    @Test
    fun testMatchConsumesToken() {
        val ctx = ParseContext("SELECT u")
        assertTrue(ctx.match(TokenType.SELECT))
        // After matching SELECT, current should be 'u' (IDENTIFIER)
        assertTrue(ctx.check(TokenType.IDENTIFIER))
    }

    @Test
    fun testMatchReturnsFalseAndDoesNotAdvance() {
        val ctx = ParseContext("SELECT u")
        assertFalse(ctx.match(TokenType.FROM))
        assertTrue(ctx.check(TokenType.SELECT))
    }

    @Test
    fun testAdvance() {
        val ctx = ParseContext("SELECT u FROM")
        val token = ctx.advance()
        assertEquals(TokenType.SELECT, token.type)
        assertEquals("u", ctx.current.text)
    }

    @Test
    fun testExpectSuccess() {
        val ctx = ParseContext("FROM User")
        val token = ctx.expect(TokenType.FROM)
        assertEquals(TokenType.FROM, token.type)
        assertEquals("User", ctx.current.text)
    }

    @Test(expected = JpqlParseException::class)
    fun testExpectFailure() {
        val ctx = ParseContext("SELECT u")
        ctx.expect(TokenType.FROM)
    }

    @Test
    fun testExpectIdentifier() {
        val ctx = ParseContext("username")
        val name = ctx.expectIdentifier()
        assertEquals("username", name)
    }

    @Test(expected = JpqlParseException::class)
    fun testExpectIdentifierFailsOnKeyword() {
        val ctx = ParseContext("SELECT")
        ctx.expectIdentifier()
    }

    @Test
    fun testExpectIdentifierOrKeywordAcceptsIdentifier() {
        val ctx = ParseContext("username")
        assertEquals("username", ctx.expectIdentifierOrKeyword())
    }

    @Test
    fun testExpectIdentifierOrKeywordAcceptsKeyword() {
        val ctx = ParseContext("Order")
        assertEquals("Order", ctx.expectIdentifierOrKeyword())
    }

    @Test
    fun testExpectIdentifierOrKeywordAcceptsGroupKeyword() {
        val ctx = ParseContext("Group")
        assertEquals("Group", ctx.expectIdentifierOrKeyword())
    }

    @Test(expected = JpqlParseException::class)
    fun testExpectIdentifierOrKeywordFailsOnOperator() {
        val ctx = ParseContext("=")
        ctx.expectIdentifierOrKeyword()
    }

    @Test
    fun testPeekNext() {
        val ctx = ParseContext("SELECT u FROM")
        val next = ctx.peekNext()
        assertNotNull(next)
        assertEquals(TokenType.IDENTIFIER, next!!.type)
        assertEquals("u", next.text)
        // current should NOT have advanced
        assertTrue(ctx.check(TokenType.SELECT))
    }

    @Test
    fun testPeekNextAtEnd() {
        val ctx = ParseContext("u")
        ctx.advance() // now at EOF
        val next = ctx.peekNext()
        assertNull(next)
    }

    @Test
    fun testIsClauseKeyword() {
        val ctx = ParseContext("")
        assertTrue(ctx.isClauseKeyword(TokenType.SELECT))
        assertTrue(ctx.isClauseKeyword(TokenType.FROM))
        assertTrue(ctx.isClauseKeyword(TokenType.WHERE))
        assertTrue(ctx.isClauseKeyword(TokenType.ORDER))
        assertTrue(ctx.isClauseKeyword(TokenType.GROUP))
        assertTrue(ctx.isClauseKeyword(TokenType.HAVING))
        assertTrue(ctx.isClauseKeyword(TokenType.JOIN))
        assertTrue(ctx.isClauseKeyword(TokenType.END_OF_FILE))
        // Non-clause keywords
        assertFalse(ctx.isClauseKeyword(TokenType.COUNT))
        assertFalse(ctx.isClauseKeyword(TokenType.UPPER))
        assertFalse(ctx.isClauseKeyword(TokenType.IDENTIFIER))
    }

    @Test
    fun testParseError() {
        val ctx = ParseContext("SELECT u")
        val ex = ctx.parseError("something went wrong")
        assertTrue(ex.message!!.contains("something went wrong"))
        assertTrue(ex.message!!.contains("position"))
    }
}

