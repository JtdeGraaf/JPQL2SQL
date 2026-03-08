package com.github.jtdegraaf.jpql2sql.converter.resolver

/**
 * Utility object for generating foreign key column names and join conditions.
 * Centralizes FK naming conventions used by JoinInfoResolver and JoinConverter.
 */
object FkNamingUtils {

    /**
     * Generates the default FK column name for a relationship field.
     * Convention: fieldName_id (e.g., "department" -> "department_id")
     *
     * @param fieldName The relationship field name in camelCase
     * @return The FK column name in snake_case with "_id" suffix
     */
    fun defaultFkColumnName(fieldName: String): String =
        "${NamingUtils.toSnakeCase(fieldName)}_id"

    /**
     * Generates the default FK column name for an entity reference.
     * Convention: entityName_id (e.g., "User" -> "user_id")
     *
     * @param entityName The entity name
     * @return The FK column name in snake_case with "_id" suffix
     */
    fun entityFkColumnName(entityName: String): String =
        "${NamingUtils.toSnakeCase(entityName)}_id"

    /**
     * Generates a default join condition string for FK-based joins.
     * Pattern: parentAlias.fkColumn = targetAlias.referencedColumn
     *
     * @param parentAlias The alias of the parent table
     * @param fkColumn The FK column name on the parent table
     * @param targetAlias The alias of the target/joined table
     * @param referencedColumn The referenced column on the target table (default: "id")
     * @return The join condition string
     */
    fun defaultJoinCondition(
        parentAlias: String,
        fkColumn: String,
        targetAlias: String,
        referencedColumn: String = "id"
    ): String = "$parentAlias.$fkColumn = $targetAlias.$referencedColumn"

    /**
     * Default referenced column name for FK relationships.
     */
    const val DEFAULT_REFERENCED_COLUMN = "id"
}
