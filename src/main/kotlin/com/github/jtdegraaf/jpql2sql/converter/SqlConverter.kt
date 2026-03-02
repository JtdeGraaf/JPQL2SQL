package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.SqlDialect
import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Orchestrates JPQL→SQL conversion.
 *
 * Delegates expression rendering to [ExpressionConverter] and
 * JOIN generation to [JoinConverter].
 */
class SqlConverter(
    private val dialect: SqlDialect,
    private val entityResolver: EntityResolver
) {
    private val aliasToEntity = mutableMapOf<String, String>()

    private lateinit var exprConverter: ExpressionConverter
    private lateinit var joinConverter: JoinConverter

    fun convert(query: JpqlQuery): String {
        aliasToEntity.clear()
        aliasToEntity[query.from.alias] = query.from.entity.name

        // Register additional FROM entities (comma-separated)
        for (entry in query.from.additionalEntities) {
            aliasToEntity[entry.alias] = entry.entity.name
        }

        for (join in query.joins) {
            val resolvedEntity = resolveJoinEntityName(join)
            aliasToEntity[join.alias] = resolvedEntity ?: JoinConverter.inferEntityFromPath(join.path)
        }

        exprConverter = ExpressionConverter(dialect, entityResolver, aliasToEntity, ::convert)
        joinConverter = JoinConverter(entityResolver, aliasToEntity, exprConverter)

        return buildString {
            append(convertSelect(query.select))
            append(" ").append(convertFrom(query.from))
            for (join in query.joins) { append(" ").append(joinConverter.convert(join)) }
            query.where?.let { append(" ").append("WHERE ${exprConverter.convert(it.condition)}") }
            query.groupBy?.let { append(" ").append(convertGroupBy(it)) }
            query.having?.let { append(" ").append("HAVING ${exprConverter.convert(it.condition)}") }
            query.orderBy?.let { append(" ").append(convertOrderBy(it)) }
            for (fragment in query.unparsedFragments) {
                append(" /* UNPARSED: $fragment */")
            }
        }
    }

    private fun convertSelect(select: SelectClause): String {
        val projections = select.projections.joinToString(", ") { convertProjection(it) }
        return if (select.distinct) "SELECT DISTINCT $projections" else "SELECT $projections"
    }

    private fun convertProjection(projection: Projection): String = when (projection) {
        is FieldProjection -> {
            val converted = exprConverter.convert(projection.path)
            if (projection.alias != null) "$converted AS ${projection.alias}" else converted
        }
        is CountAllProjection -> "COUNT(*)"
        is AggregateProjection -> {
            val inner = if (projection.distinct) "DISTINCT ${exprConverter.convert(projection.expression)}"
                        else exprConverter.convert(projection.expression)
            val result = "${projection.function.name}($inner)"
            if (projection.alias != null) "$result AS ${projection.alias}" else result
        }
        is ConstructorProjection -> projection.arguments.joinToString(", ") { exprConverter.convert(it) }
    }

    private fun convertFrom(from: FromClause): String {
        val firstTable = entityResolver.resolveTableName(from.entity.name)
        val tables = mutableListOf("$firstTable ${from.alias}")

        for (entry in from.additionalEntities) {
            val tableName = entityResolver.resolveTableName(entry.entity.name)
            tables.add("$tableName ${entry.alias}")
        }

        return "FROM ${tables.joinToString(", ")}"
    }

    /**
     * Resolve the actual entity name for a JOIN path like `m.participants`.
     * Uses the parent alias to find the parent entity, then resolves the field's target entity.
     */
    private fun resolveJoinEntityName(join: JoinClause): String? {
        if (join.path.parts.size < 2) return null
        val parentAlias = join.path.parts[0]
        val fieldName = join.path.parts[1]
        val parentEntity = aliasToEntity[parentAlias] ?: return null
        return entityResolver.resolveTargetEntityName(parentEntity, fieldName)
    }

    private fun convertGroupBy(groupBy: GroupByClause): String =
        "GROUP BY ${groupBy.expressions.joinToString(", ") { exprConverter.convertPath(it) }}"

    private fun convertOrderBy(orderBy: OrderByClause): String {
        val items = orderBy.items.joinToString(", ") { item ->
            val expr = exprConverter.convert(item.expression)
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
}
