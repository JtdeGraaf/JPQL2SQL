package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.SqlDialect
import com.github.jtdegraaf.jpql2sql.parser.*

class SqlConverter(
    private val dialect: SqlDialect,
    private val entityResolver: EntityResolver
) {
    private val aliasToEntity = mutableMapOf<String, String>()

    fun convert(query: JpqlQuery): String {
        aliasToEntity.clear()

        // Build alias to entity mapping
        aliasToEntity[query.from.alias] = query.from.entity.name
        for (join in query.joins) {
            aliasToEntity[join.alias] = inferEntityFromPath(join.path)
        }

        return buildString {
            append(convertSelect(query.select))
            append(" ")
            append(convertFrom(query.from))

            for (join in query.joins) {
                append(" ")
                append(convertJoin(join))
            }

            query.where?.let {
                append(" ")
                append(convertWhere(it))
            }

            query.groupBy?.let {
                append(" ")
                append(convertGroupBy(it))
            }

            query.having?.let {
                append(" ")
                append(convertHaving(it))
            }

            query.orderBy?.let {
                append(" ")
                append(convertOrderBy(it))
            }
        }
    }

    private fun convertSelect(select: SelectClause): String {
        val projections = select.projections.joinToString(", ") { convertProjection(it) }
        return if (select.distinct) {
            "SELECT DISTINCT $projections"
        } else {
            "SELECT $projections"
        }
    }

    private fun convertProjection(projection: Projection): String {
        return when (projection) {
            is FieldProjection -> {
                val converted = convertPath(projection.path)
                if (projection.alias != null) {
                    "$converted AS ${projection.alias}"
                } else {
                    converted
                }
            }
            is CountAllProjection -> "COUNT(*)"
            is AggregateProjection -> {
                val func = projection.function.name
                val expr = convertExpression(projection.expression)
                val inner = if (projection.distinct) "DISTINCT $expr" else expr
                val result = "$func($inner)"
                if (projection.alias != null) {
                    "$result AS ${projection.alias}"
                } else {
                    result
                }
            }
            is ConstructorProjection -> {
                // Constructor expressions become simple column list
                projection.arguments.joinToString(", ") { convertExpression(it) }
            }
        }
    }

    private fun convertFrom(from: FromClause): String {
        val tableName = entityResolver.resolveTableName(from.entity.name)
        return "FROM $tableName ${from.alias}"
    }

    private fun convertJoin(join: JoinClause): String {
        val joinType = when (join.type) {
            JoinType.INNER -> "INNER JOIN"
            JoinType.LEFT -> "LEFT JOIN"
            JoinType.RIGHT -> "RIGHT JOIN"
        }

        // Resolve join target table
        val entityName = inferEntityFromPath(join.path)
        val tableName = entityResolver.resolveTableName(entityName)

        // Build join condition
        val condition = if (join.condition != null) {
            convertExpression(join.condition)
        } else {
            // Default join condition based on path
            buildDefaultJoinCondition(join.path, join.alias)
        }

        return "$joinType $tableName ${join.alias} ON $condition"
    }

    private fun buildDefaultJoinCondition(path: PathExpression, alias: String): String {
        if (path.parts.size >= 2) {
            val parentAlias = path.parts[0]
            val fieldName = path.parts[1]
            val parentEntity = aliasToEntity[parentAlias] ?: return "$parentAlias.id = $alias.id"

            val joinInfo = entityResolver.resolveJoinTable(parentEntity, fieldName)
            if (joinInfo != null) {
                return "$parentAlias.${joinInfo.columnName} = $alias.${joinInfo.referencedColumnName}"
            }

            // Default: assume FK pattern entity_id
            val columnName = EntityResolver.toSnakeCase(fieldName) + "_id"
            return "$parentAlias.$columnName = $alias.id"
        }
        return "$alias.id = ${path.parts.firstOrNull() ?: "unknown"}.id"
    }

    private fun convertWhere(where: WhereClause): String {
        return "WHERE ${convertExpression(where.condition)}"
    }

    private fun convertGroupBy(groupBy: GroupByClause): String {
        val expressions = groupBy.expressions.joinToString(", ") { convertPath(it) }
        return "GROUP BY $expressions"
    }

    private fun convertHaving(having: HavingClause): String {
        return "HAVING ${convertExpression(having.condition)}"
    }

    private fun convertOrderBy(orderBy: OrderByClause): String {
        val items = orderBy.items.joinToString(", ") { item ->
            val expr = convertExpression(item.expression)
            val dir = if (item.direction == OrderDirection.DESC) " DESC" else " ASC"
            val nulls = when (item.nulls) {
                NullsOrdering.FIRST -> " NULLS FIRST"
                NullsOrdering.LAST -> " NULLS LAST"
                null -> ""
            }
            "$expr$dir$nulls"
        }
        return "ORDER BY $items"
    }

    private fun convertExpression(expr: Expression): String {
        return when (expr) {
            is PathExpression -> convertPath(expr)
            is BinaryExpression -> convertBinaryExpression(expr)
            is UnaryExpression -> convertUnaryExpression(expr)
            is LiteralExpression -> convertLiteral(expr)
            is ParameterExpression -> convertParameter(expr)
            is FunctionCallExpression -> convertFunctionCall(expr)
            is CaseExpression -> convertCaseExpression(expr)
            is SubqueryExpression -> "(${convert(expr.query)})"
            is InListExpression -> convertInList(expr)
            is BetweenExpression -> "${convertExpression(expr.lower)} AND ${convertExpression(expr.upper)}"
        }
    }

    private fun convertPath(path: PathExpression): String {
        if (path.parts.size == 1 && path.parts[0] == "*") {
            return "*"
        }

        if (path.parts.size == 1) {
            // Could be just an alias reference
            return path.parts[0]
        }

        val alias = path.parts[0]
        val fieldParts = path.parts.drop(1)

        // Resolve column name through entity resolver
        val entityName = aliasToEntity[alias]
        return if (entityName != null && fieldParts.isNotEmpty()) {
            val columnName = entityResolver.resolveColumnName(entityName, fieldParts)
            "$alias.$columnName"
        } else {
            // Fallback: convert to snake_case
            val columnPath = fieldParts.joinToString(".") { EntityResolver.toSnakeCase(it) }
            "$alias.$columnPath"
        }
    }

    private fun convertBinaryExpression(expr: BinaryExpression): String {
        val left = convertExpression(expr.left)
        val right = convertExpression(expr.right)

        return when (expr.operator) {
            BinaryOperator.AND -> "($left AND $right)"
            BinaryOperator.OR -> "($left OR $right)"
            BinaryOperator.EQ -> "$left = $right"
            BinaryOperator.NE -> "$left <> $right"
            BinaryOperator.LT -> "$left < $right"
            BinaryOperator.LE -> "$left <= $right"
            BinaryOperator.GT -> "$left > $right"
            BinaryOperator.GE -> "$left >= $right"
            BinaryOperator.LIKE -> "$left LIKE $right"
            BinaryOperator.NOT_LIKE -> "$left NOT LIKE $right"
            BinaryOperator.IN -> "$left IN $right"
            BinaryOperator.NOT_IN -> "$left NOT IN $right"
            BinaryOperator.BETWEEN -> "$left BETWEEN $right"
            BinaryOperator.NOT_BETWEEN -> "$left NOT BETWEEN $right"
            BinaryOperator.IS_NULL -> "$left IS NULL"
            BinaryOperator.IS_NOT_NULL -> "$left IS NOT NULL"
            BinaryOperator.MEMBER_OF -> "$left IN $right" // Simplified
            BinaryOperator.NOT_MEMBER_OF -> "$left NOT IN $right" // Simplified
        }
    }

    private fun convertUnaryExpression(expr: UnaryExpression): String {
        val operand = convertExpression(expr.operand)
        return when (expr.operator) {
            UnaryOperator.NOT -> "NOT ($operand)"
            UnaryOperator.MINUS -> "-$operand"
        }
    }

    private fun convertLiteral(expr: LiteralExpression): String {
        return when (expr.type) {
            LiteralType.STRING -> "'${expr.value.toString().replace("'", "''")}'"
            LiteralType.NUMBER -> expr.value.toString()
            LiteralType.BOOLEAN -> dialect.booleanLiteral(expr.value as Boolean)
            LiteralType.NULL -> "NULL"
        }
    }

    private fun convertParameter(expr: ParameterExpression): String {
        return when {
            expr.name != null -> ":${expr.name}"
            expr.position != null -> "?${expr.position}"
            else -> "?"
        }
    }

    private fun convertFunctionCall(expr: FunctionCallExpression): String {
        val args = expr.arguments.map { convertExpression(it) }

        return when (expr.name.uppercase()) {
            "CONCAT" -> dialect.concat(args)
            "SUBSTRING", "SUBSTR" -> {
                val str = args.getOrNull(0) ?: ""
                val start = args.getOrNull(1) ?: "1"
                val length = args.getOrNull(2)
                dialect.substring(str, start, length)
            }
            "UPPER" -> "UPPER(${args.joinToString(", ")})"
            "LOWER" -> "LOWER(${args.joinToString(", ")})"
            "TRIM" -> if (args.isNotEmpty()) dialect.trim(args[0]) else "TRIM()"
            "LENGTH" -> "LENGTH(${args.joinToString(", ")})"
            "LOCATE" -> {
                // JPQL: LOCATE(search, string, start?) -> SQL varies
                if (args.size >= 2) {
                    "LOCATE(${args[0]}, ${args[1]}${if (args.size > 2) ", ${args[2]}" else ""})"
                } else {
                    "LOCATE(${args.joinToString(", ")})"
                }
            }
            "ABS" -> "ABS(${args.joinToString(", ")})"
            "SQRT" -> "SQRT(${args.joinToString(", ")})"
            "MOD" -> "MOD(${args.joinToString(", ")})"
            "SIZE" -> "COUNT(${args.joinToString(", ")})" // Approximation
            "CURRENT_DATE" -> dialect.currentDate()
            "CURRENT_TIME" -> dialect.currentTime()
            "CURRENT_TIMESTAMP" -> dialect.currentTimestamp()
            "COALESCE" -> dialect.coalesce(args)
            "NULLIF" -> if (args.size >= 2) dialect.nullif(args[0], args[1]) else "NULLIF(${args.joinToString(", ")})"
            else -> "${expr.name}(${args.joinToString(", ")})"
        }
    }

    private fun convertCaseExpression(expr: CaseExpression): String {
        return buildString {
            append("CASE")
            if (expr.operand != null) {
                append(" ")
                append(convertExpression(expr.operand))
            }
            for (whenClause in expr.whenClauses) {
                append(" WHEN ")
                append(convertExpression(whenClause.condition))
                append(" THEN ")
                append(convertExpression(whenClause.result))
            }
            if (expr.elseExpression != null) {
                append(" ELSE ")
                append(convertExpression(expr.elseExpression))
            }
            append(" END")
        }
    }

    private fun convertInList(expr: InListExpression): String {
        val elements = expr.elements.joinToString(", ") { convertExpression(it) }
        return "($elements)"
    }

    private fun inferEntityFromPath(path: PathExpression): String {
        // If path is like "u.orders", infer entity from the field type
        // For simplicity, capitalize and singularize the last part
        val lastPart = path.parts.lastOrNull() ?: return "Unknown"
        return lastPart.replaceFirstChar { it.uppercase() }
            .removeSuffix("s") // Simple singularization
            .removeSuffix("ie").plus(if (lastPart.endsWith("ies")) "y" else "")
    }
}
