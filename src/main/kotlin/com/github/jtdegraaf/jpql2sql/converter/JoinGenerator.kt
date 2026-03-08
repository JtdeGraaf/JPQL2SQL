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
    fun generateAlias(baseName: String): String {
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
     * Checks if accessing fieldName on an entity requires a JOIN.
     *
     * Returns false for:
     * - Embedded fields (they're part of the same table)
     * - FK ID access optimization (e.g., u.department.id -> u.department_id)
     *
     * @param entityName The entity containing the field
     * @param fieldName The field being accessed
     * @param isIdAccess True if accessing .id on the relationship (FK optimization)
     * @return True if a JOIN is required
     */
    fun requiresJoin(entityName: String, fieldName: String, isIdAccess: Boolean): Boolean {
        // Embedded fields don't need JOINs - they're part of the same table
        if (entityResolver.isEmbeddedField(entityName, fieldName)) return false

        // Check if this is a relationship field
        if (!entityResolver.isRelationshipField(entityName, fieldName)) return false

        // FK optimization: accessing .id on a single-valued association doesn't need a JOIN
        // because the FK column already contains the ID value
        if (isIdAccess) {
            val targetEntity = entityResolver.resolveTargetEntityName(entityName, fieldName)
            if (targetEntity != null && entityResolver.isPrimaryKeyField(targetEntity, "id")) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if the given field is the last part of a path and accessing it
     * as .id would allow FK optimization (no JOIN needed).
     *
     * @param entityName The current entity
     * @param fieldName The relationship field
     * @param nextField The next field in the path (should be "id" for optimization)
     * @param isNextFieldLast True if nextField is the last part of the path
     * @return True if FK optimization applies
     */
    fun canOptimizeFkAccess(
        entityName: String,
        fieldName: String,
        nextField: String?,
        isNextFieldLast: Boolean
    ): Boolean {
        if (nextField == null || !isNextFieldLast) return false
        if (!entityResolver.isRelationshipField(entityName, fieldName)) return false

        val targetEntity = entityResolver.resolveTargetEntityName(entityName, fieldName) ?: return false
        return entityResolver.isPrimaryKeyField(targetEntity, nextField)
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
