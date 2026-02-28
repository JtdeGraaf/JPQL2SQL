package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses the FROM clause: `FROM EntityName [AS] alias`.
 */
class FromClauseParser(
    private val ctx: ParseContext
) {
    fun parse(): FromClause {
        ctx.expect(TokenType.FROM)
        val entityName = ctx.expectIdentifierOrKeyword()
        val alias = if (ctx.match(TokenType.AS)) {
            ctx.expectIdentifierOrKeyword()
        } else if (ctx.check(TokenType.IDENTIFIER) || (ctx.current.type.isKeyword() && !ctx.isClauseKeyword(ctx.current.type))) {
            ctx.expectIdentifierOrKeyword()
        } else {
            entityName.lowercase()
        }
        return FromClause(EntityReference(entityName), alias)
    }
}

