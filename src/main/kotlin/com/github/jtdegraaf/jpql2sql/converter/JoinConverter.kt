package com.github.jtdegraaf.jpql2sql.converter

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
        }

        val entityName = inferEntityFromPath(join.path)
        val tableName = entityResolver.resolveTableName(entityName)

        // Explicit ON condition
        if (join.condition != null) {
            return "$keyword $tableName ${join.alias} ON ${exprConverter.convert(join.condition)}"
        }

        // @JoinTable (many-to-many)
        if (join.path.parts.size >= 2) {
            val parentAlias = join.path.parts[0]
            val fieldName = join.path.parts[1]
            val parentEntity = aliasToEntity[parentAlias]

            if (parentEntity != null) {
                val joinInfo = entityResolver.resolveJoinTable(parentEntity, fieldName)
                if (joinInfo != null && joinInfo.joinTable != null) {
                    val jtAlias = "${join.alias}_jt"
                    return buildString {
                        append("$keyword ${joinInfo.joinTable} $jtAlias")
                        append(" ON $parentAlias.id = $jtAlias.${joinInfo.columnName}")
                        append(" $keyword $tableName ${join.alias}")
                        append(" ON $jtAlias.${joinInfo.inverseColumnName} = ${join.alias}.${joinInfo.referencedColumnName}")
                    }
                }
            }
        }

        // Default FK-based condition
        val condition = buildDefaultCondition(join.path, join.alias)
        return "$keyword $tableName ${join.alias} ON $condition"
    }

    private fun buildDefaultCondition(path: PathExpression, alias: String): String {
        if (path.parts.size >= 2) {
            val parentAlias = path.parts[0]
            val fieldName = path.parts[1]
            val parentEntity = aliasToEntity[parentAlias] ?: return "$parentAlias.id = $alias.id"

            val joinInfo = entityResolver.resolveJoinTable(parentEntity, fieldName)
            if (joinInfo != null) {
                return "$parentAlias.${joinInfo.columnName} = $alias.${joinInfo.referencedColumnName}"
            }

            val columnName = EntityResolver.toSnakeCase(fieldName) + "_id"
            return "$parentAlias.$columnName = $alias.id"
        }
        return "$alias.id = ${path.parts.firstOrNull() ?: "unknown"}.id"
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

