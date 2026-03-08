package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.resolver.FkNamingUtils
import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Converts JPQL JOIN clauses to SQL, handling:
 * - explicit ON conditions
 * - `@JoinTable` (many-to-many) intermediate tables
 * - default FK-based join conditions
 *
 * @param entityResolver   resolves entity/field names to table/column names
 * @param aliasToEntity    shared alias→entity mapping built by [SqlConverter]
 * @param exprConverter    converts expressions within ON conditions
 */
class JoinConverter(
    private val entityResolver: EntityResolver,
    private val aliasToEntity: Map<String, String>,
    private val exprConverter: ExpressionConverter
) {

    fun convert(join: JoinClause): String {
        val keyword = when (join.type) {
            JoinType.INNER -> "INNER JOIN"
            JoinType.LEFT -> "LEFT JOIN"
            JoinType.RIGHT -> "RIGHT JOIN"
            JoinType.FULL -> "FULL OUTER JOIN"
            JoinType.CROSS -> "CROSS JOIN"
        }

        // Try to resolve join info from the relationship annotation
        var tableName: String? = null
        var joinInfo: com.github.jtdegraaf.jpql2sql.converter.resolver.JoinInfo? = null

        if (join.path.parts.size >= 2) {
            val parentAlias = join.path.parts[0]
            val fieldName = join.path.parts[1]
            val parentEntity = aliasToEntity[parentAlias]

            if (parentEntity != null) {
                joinInfo = entityResolver.resolveJoinTable(parentEntity, fieldName)
                if (joinInfo != null) {
                    tableName = joinInfo.targetTable
                }
            }
        }

        // Fallback to inferred entity name
        if (tableName == null) {
            val entityName = inferEntityFromPath(join.path)
            tableName = entityResolver.resolveTableName(entityName)
        }

        // CROSS JOIN has no ON condition
        if (join.type == JoinType.CROSS) {
            return "$keyword $tableName ${join.alias}"
        }

        // Explicit ON condition
        if (join.condition != null) {
            return "$keyword $tableName ${join.alias} ON ${exprConverter.convert(join.condition)}"
        }

        // @JoinTable (many-to-many)
        if (joinInfo != null && joinInfo.joinTable != null) {
            val parentAlias = join.path.parts[0]
            val jtAlias = "${join.alias}_jt"
            return buildString {
                append("$keyword ${joinInfo.joinTable} $jtAlias")
                append(" ON $parentAlias.id = $jtAlias.${joinInfo.columnName}")
                append(" $keyword $tableName ${join.alias}")
                append(" ON $jtAlias.${joinInfo.inverseColumnName} = ${join.alias}.${joinInfo.referencedColumnName}")
            }
        }

        // @OneToMany - join from child table back to parent
        if (joinInfo != null && joinInfo.isOneToMany) {
            val parentAlias = join.path.parts[0]
            return "$keyword $tableName ${join.alias} ON ${join.alias}.${joinInfo.columnName} = $parentAlias.id"
        }

        // Default FK-based condition
        val condition = buildDefaultCondition(join.path, join.alias)
        return "$keyword $tableName ${join.alias} ON $condition"
    }

    private fun buildDefaultCondition(path: PathExpression, alias: String): String {
        if (path.parts.size >= 2) {
            val parentAlias = path.parts[0]
            val fieldName = path.parts[1]
            val parentEntity = aliasToEntity[parentAlias]
                ?: return FkNamingUtils.defaultJoinCondition(parentAlias, "id", alias, "id")

            val joinInfo = entityResolver.resolveJoinTable(parentEntity, fieldName)
            if (joinInfo != null) {
                return FkNamingUtils.defaultJoinCondition(
                    parentAlias, joinInfo.columnName, alias, joinInfo.referencedColumnName
                )
            }

            val fkColumn = FkNamingUtils.defaultFkColumnName(fieldName)
            return FkNamingUtils.defaultJoinCondition(parentAlias, fkColumn, alias)
        }
        return FkNamingUtils.defaultJoinCondition(
            alias, "id", path.parts.firstOrNull() ?: "unknown", "id"
        )
    }

    companion object {
        fun inferEntityFromPath(path: PathExpression): String {
            val lastPart = path.parts.lastOrNull() ?: return "Unknown"
            return lastPart.replaceFirstChar { it.uppercase() }
                .removeSuffix("s")
                .removeSuffix("ie").plus(if (lastPart.endsWith("ies")) "y" else "")
        }
    }
}

