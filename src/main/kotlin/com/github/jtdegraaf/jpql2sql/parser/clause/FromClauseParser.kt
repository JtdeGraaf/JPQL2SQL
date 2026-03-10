package com.github.jtdegraaf.jpql2sql.parser.clause

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Parses the FROM clause with entity name, optional AS keyword, alias, and comma-separated additional entities.
 * Supports comma-separated multiple entities for cross joins.
 */
class FromClauseParser(
    private val ctx: ParseContext
) {
    fun parse(): FromClause {
        ctx.expect(TokenType.FROM)

        // Parse first entity
        val (firstEntity, firstAlias) = parseEntityWithAlias()

        // Parse additional comma-separated entities
        val additionalEntities = mutableListOf<FromEntry>()
        while (ctx.match(TokenType.COMMA)) {
            val (entity, alias) = parseEntityWithAlias()
            additionalEntities.add(FromEntry(EntityReference(entity), alias))
        }

        return FromClause(EntityReference(firstEntity), firstAlias, additionalEntities)
    }

    private fun parseEntityWithAlias(): Pair<String, String> {
        val entityName = ctx.expectIdentifierOrKeyword()
        val alias = ctx.parseAliasOrDefault(allowKeywords = true) { entityName.lowercase() }
        return entityName to alias
    }
}

