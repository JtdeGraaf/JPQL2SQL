package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.FetchClause
import com.github.jtdegraaf.jpql2sql.parser.ParseContext
import com.github.jtdegraaf.jpql2sql.parser.TokenType

/**
 * Parses OFFSET and FETCH FIRST/NEXT clauses.
 *
 * Supports:
 * - FETCH FIRST n ROWS ONLY
 * - FETCH NEXT n ROW ONLY
 * - OFFSET m ROWS FETCH FIRST n ROWS ONLY
 * - OFFSET m FETCH FIRST n ROWS ONLY
 */
class FetchClauseParser(private val ctx: ParseContext) {

    fun parse(): FetchClause? {
        var offset: Int? = null

        // Parse optional OFFSET clause
        if (ctx.match(TokenType.OFFSET)) {
            if (ctx.check(TokenType.NUMBER_LITERAL)) {
                offset = ctx.current.text.toIntOrNull()
                ctx.advance()
            }
            // Skip optional ROWS/ROW keyword
            if (ctx.check(TokenType.ROWS) || ctx.check(TokenType.ROW)) {
                ctx.advance()
            }
        }

        // Parse FETCH clause
        if (!ctx.match(TokenType.FETCH)) {
            // If we had OFFSET but no FETCH, we still have a valid clause
            return if (offset != null) FetchClause(offset, Int.MAX_VALUE) else null
        }

        // FIRST or NEXT
        if (!ctx.match(TokenType.FIRST) && !ctx.match(TokenType.NEXT)) {
            return if (offset != null) FetchClause(offset, Int.MAX_VALUE) else null
        }

        // The count
        val fetchCount = if (ctx.check(TokenType.NUMBER_LITERAL)) {
            val count = ctx.current.text.toIntOrNull() ?: 1
            ctx.advance()
            count
        } else {
            1 // Default to 1 if no number specified
        }

        // ROWS or ROW
        if (ctx.check(TokenType.ROWS) || ctx.check(TokenType.ROW)) {
            ctx.advance()
        }

        // ONLY (optional)
        ctx.match(TokenType.ONLY)

        return FetchClause(offset, fetchCount)
    }
}
