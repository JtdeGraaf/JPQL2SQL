package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.parser.JoinClause
import com.github.jtdegraaf.jpql2sql.parser.JoinType
import com.github.jtdegraaf.jpql2sql.parser.PathExpression

/**
 * Generates JOIN clauses for relationship traversals.
 *
 * This class centralizes the logic for creating implicit JOINs, shared by
 * [ImplicitJoinTransformer] and [com.github.jtdegraaf.jpql2sql.repository.DerivedQueryAstBuilder].
 *
 * @param entityResolver Used to detect relationship fields and resolve target entities
 */
class JoinGenerator(
    private val entityResolver: EntityResolver
) {
    private var aliasCounter = 0

    /**
     * Resets the alias counter. Call this before processing a new query.
     */
    fun resetAliasCounter() {
        aliasCounter = 0
    }

    /**
     * Gets the current alias counter value. Used for save/restore in nested contexts.
     */
    fun getAliasCounter(): Int = aliasCounter

    /**
     * Sets the alias counter value. Used for save/restore in nested contexts.
     */
    fun setAliasCounter(value: Int) {
        aliasCounter = value
    }

    /**
     * Generates a unique alias for a join based on the field name.
     * Pattern: fieldName_N (e.g., "participants_1", "bot_2")
     *
     * @param baseName The base name for the alias (typically the field name)
     * @return A unique alias string
     */
    private fun generateAlias(baseName: String): String {
        aliasCounter++
        return "${baseName}_$aliasCounter"
    }

    /**
     * Creates a JoinClause for a relationship path.
     *
     * @param parentAlias The alias of the parent entity (e.g., "e")
     * @param fieldName The relationship field name (e.g., "participants")
     * @param joinType The type of join to create (default: LEFT)
     * @return A JoinClause with a generated alias
     */
    fun createJoinClause(
        parentAlias: String,
        fieldName: String,
        joinType: JoinType = JoinType.LEFT
    ): JoinClause {
        val newAlias = generateAlias(fieldName)
        return JoinClause(
            type = joinType,
            path = PathExpression(listOf(parentAlias, fieldName)),
            alias = newAlias,
            condition = null
        )
    }

    /**
     * Infers an entity name from a path expression.
     * Used as a fallback when the entity can't be resolved from metadata.
     *
     * @param path The path expression
     * @return An inferred entity name (capitalized, singularized)
     */
    fun inferEntityFromPath(path: PathExpression): String {
        val lastPart = path.parts.lastOrNull() ?: return "Unknown"
        return lastPart.replaceFirstChar { it.uppercase() }
            .removeSuffix("s")
            .removeSuffix("ie").plus(if (lastPart.endsWith("ies")) "y" else "")
    }
}
