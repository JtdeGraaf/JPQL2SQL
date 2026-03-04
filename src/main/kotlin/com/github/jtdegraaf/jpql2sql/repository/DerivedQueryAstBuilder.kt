package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Builds a JpqlQuery AST from parsed derived query components.
 *
 * This enables reuse of the existing SqlConverter for all SQL generation,
 * avoiding duplicate conversion logic.
 *
 * For nested properties (e.g., "participants.bot.id"), this builder generates
 * the necessary JOIN clauses to access the related entities.
 */
class DerivedQueryAstBuilder {

    companion object {
        private const val DEFAULT_ALIAS = "e"
    }

    /**
     * Tracks generated joins: maps path prefix (e.g., "e.participants") to its alias (e.g., "participants_1")
     */
    private val joinAliases = mutableMapOf<String, String>()
    private var aliasCounter = 0

    fun build(components: DerivedQueryComponents): JpqlQuery {
        // Reset state for each build
        joinAliases.clear()
        aliasCounter = 0

        val alias = DEFAULT_ALIAS

        // Collect all property paths that need joins
        val allPaths = mutableListOf<String>()
        for (condition in components.conditions) {
            allPaths.add(condition.property)
        }
        components.orderBy?.forEach { allPaths.add(it.property) }

        // Build joins for all nested properties
        val joins = buildJoinsForPaths(allPaths, alias)

        val select = buildSelectClause(components, alias)
        val from = buildFromClause(components, alias)
        val where = buildWhereClause(components.conditions, alias)
        val orderBy = buildOrderByClause(components.orderBy, alias)
        val fetch = components.limit?.let { FetchClause(offset = null, fetchCount = it) }

        return JpqlQuery(
            select = select,
            from = from,
            joins = joins,
            where = where,
            groupBy = null,
            having = null,
            orderBy = orderBy,
            fetch = fetch
        )
    }

    /**
     * Builds JOIN clauses for all nested property paths.
     *
     * For a path like "participants.bot.id", we need:
     * - JOIN e.participants participants_1
     * - JOIN participants_1.bot bot_1
     *
     * The final property (id) is accessed via the last join alias.
     */
    private fun buildJoinsForPaths(paths: List<String>, rootAlias: String): List<JoinClause> {
        val joins = mutableListOf<JoinClause>()

        for (path in paths) {
            val parts = path.split(".")
            if (parts.size <= 1) continue // No join needed for simple properties

            var currentAlias = rootAlias
            // Process all parts except the last (which is the actual field)
            for (i in 0 until parts.size - 1) {
                val pathPrefix = if (i == 0) {
                    "$rootAlias.${parts[i]}"
                } else {
                    val prevPath = (0 until i).joinToString(".") { parts[it] }
                    "${joinAliases["$rootAlias.$prevPath"]}.${parts[i]}"
                }

                // Check if we already have a join for this path
                if (!joinAliases.containsKey(pathPrefix)) {
                    val joinAlias = generateAlias(parts[i])
                    joinAliases[pathPrefix] = joinAlias

                    joins.add(JoinClause(
                        type = JoinType.LEFT,
                        path = PathExpression(listOf(currentAlias, parts[i])),
                        alias = joinAlias,
                        condition = null
                    ))
                }
                currentAlias = joinAliases[pathPrefix]!!
            }
        }

        return joins
    }

    private fun generateAlias(baseName: String): String {
        aliasCounter++
        return "${baseName}_$aliasCounter"
    }

    /**
     * Resolves a property path to use the appropriate join alias.
     *
     * For "participants.bot.id":
     * - Returns PathExpression(["bot_1", "id"]) assuming bot_1 is the alias for the bot join
     */
    private fun resolvePropertyPath(property: String, rootAlias: String): PathExpression {
        val parts = property.split(".")
        if (parts.size == 1) {
            // Simple property - use root alias
            return PathExpression(listOf(rootAlias, parts[0]))
        }

        // Find the join alias for the path prefix (all parts except the last)
        var currentAlias = rootAlias
        for (i in 0 until parts.size - 1) {
            val pathPrefix = if (i == 0) {
                "$rootAlias.${parts[i]}"
            } else {
                val prevParts = (0..i).map { parts[it] }
                var lookupAlias = rootAlias
                for (j in 0 until i) {
                    val prevPath = "$lookupAlias.${parts[j]}"
                    lookupAlias = joinAliases[prevPath] ?: lookupAlias
                }
                "$lookupAlias.${parts[i]}"
            }
            currentAlias = joinAliases[pathPrefix] ?: currentAlias
        }

        // Return the final alias with the field name
        return PathExpression(listOf(currentAlias, parts.last()))
    }

    private fun buildSelectClause(components: DerivedQueryComponents, alias: String): SelectClause {
        val projection = when (components.prefix) {
            QueryPrefix.FIND -> FieldProjection(
                path = PathExpression(listOf(alias)),
                alias = null
            )
            QueryPrefix.COUNT -> AggregateProjection(
                function = AggregateFunction.COUNT,
                distinct = components.distinct,
                expression = PathExpression(listOf(alias)),
                alias = null
            )
            QueryPrefix.EXISTS -> FieldProjection(
                path = LiteralExpression(1, LiteralType.NUMBER),
                alias = null
            )
            QueryPrefix.DELETE -> FieldProjection(
                path = PathExpression(listOf(alias)),
                alias = null
            )
        }

        // For COUNT, distinct is handled in the aggregate function itself
        val selectDistinct = components.distinct && components.prefix == QueryPrefix.FIND

        return SelectClause(
            distinct = selectDistinct,
            projections = listOf(projection)
        )
    }

    private fun buildFromClause(components: DerivedQueryComponents, alias: String): FromClause {
        return FromClause(
            entity = EntityReference(components.entityName),
            alias = alias
        )
    }

    private fun buildWhereClause(conditions: List<PropertyCondition>, alias: String): WhereClause? {
        if (conditions.isEmpty()) return null

        var result: Expression? = null

        for (condition in conditions) {
            val conditionExpr = buildConditionExpression(condition, alias)

            result = if (result == null) {
                conditionExpr
            } else {
                val operator = when (condition.connector) {
                    Connector.AND -> BinaryOperator.AND
                    Connector.OR -> BinaryOperator.OR
                    null -> BinaryOperator.AND // Default to AND
                }
                BinaryExpression(result, operator, conditionExpr)
            }
        }

        return result?.let { WhereClause(it) }
    }

    private fun buildConditionExpression(condition: PropertyCondition, alias: String): Expression {
        val propertyPath = resolvePropertyPath(condition.property, alias)
        val paramName = condition.property.replace(".", "_")

        return when (condition.operator) {
            ConditionOperator.EQUALS, ConditionOperator.IS ->
                BinaryExpression(propertyPath, BinaryOperator.EQ, ParameterExpression(paramName, null))

            ConditionOperator.IS_NOT ->
                BinaryExpression(propertyPath, BinaryOperator.NE, ParameterExpression(paramName, null))

            ConditionOperator.LIKE ->
                BinaryExpression(propertyPath, BinaryOperator.LIKE, ParameterExpression(paramName, null))

            ConditionOperator.NOT_LIKE ->
                BinaryExpression(propertyPath, BinaryOperator.NOT_LIKE, ParameterExpression(paramName, null))

            ConditionOperator.STARTING_WITH ->
                BinaryExpression(
                    propertyPath,
                    BinaryOperator.LIKE,
                    FunctionCallExpression("CONCAT", listOf(
                        ParameterExpression(paramName, null),
                        LiteralExpression("%", LiteralType.STRING)
                    ))
                )

            ConditionOperator.ENDING_WITH ->
                BinaryExpression(
                    propertyPath,
                    BinaryOperator.LIKE,
                    FunctionCallExpression("CONCAT", listOf(
                        LiteralExpression("%", LiteralType.STRING),
                        ParameterExpression(paramName, null)
                    ))
                )

            ConditionOperator.CONTAINING ->
                BinaryExpression(
                    propertyPath,
                    BinaryOperator.LIKE,
                    FunctionCallExpression("CONCAT", listOf(
                        LiteralExpression("%", LiteralType.STRING),
                        ParameterExpression(paramName, null),
                        LiteralExpression("%", LiteralType.STRING)
                    ))
                )

            ConditionOperator.NOT_CONTAINING ->
                BinaryExpression(
                    propertyPath,
                    BinaryOperator.NOT_LIKE,
                    FunctionCallExpression("CONCAT", listOf(
                        LiteralExpression("%", LiteralType.STRING),
                        ParameterExpression(paramName, null),
                        LiteralExpression("%", LiteralType.STRING)
                    ))
                )

            ConditionOperator.LESS_THAN, ConditionOperator.BEFORE ->
                BinaryExpression(propertyPath, BinaryOperator.LT, ParameterExpression(paramName, null))

            ConditionOperator.LESS_THAN_EQUAL ->
                BinaryExpression(propertyPath, BinaryOperator.LE, ParameterExpression(paramName, null))

            ConditionOperator.GREATER_THAN, ConditionOperator.AFTER ->
                BinaryExpression(propertyPath, BinaryOperator.GT, ParameterExpression(paramName, null))

            ConditionOperator.GREATER_THAN_EQUAL ->
                BinaryExpression(propertyPath, BinaryOperator.GE, ParameterExpression(paramName, null))

            ConditionOperator.BETWEEN ->
                BinaryExpression(
                    propertyPath,
                    BinaryOperator.BETWEEN,
                    BetweenExpression(
                        ParameterExpression("${paramName}Start", null),
                        ParameterExpression("${paramName}End", null)
                    )
                )

            ConditionOperator.IN ->
                BinaryExpression(propertyPath, BinaryOperator.IN, ParameterExpression(paramName, null))

            ConditionOperator.NOT_IN ->
                BinaryExpression(propertyPath, BinaryOperator.NOT_IN, ParameterExpression(paramName, null))

            ConditionOperator.IS_NULL ->
                BinaryExpression(propertyPath, BinaryOperator.IS_NULL, LiteralExpression(null, LiteralType.NULL))

            ConditionOperator.IS_NOT_NULL ->
                BinaryExpression(propertyPath, BinaryOperator.IS_NOT_NULL, LiteralExpression(null, LiteralType.NULL))

            ConditionOperator.IS_TRUE ->
                BinaryExpression(propertyPath, BinaryOperator.EQ, LiteralExpression(true, LiteralType.BOOLEAN))

            ConditionOperator.IS_FALSE ->
                BinaryExpression(propertyPath, BinaryOperator.EQ, LiteralExpression(false, LiteralType.BOOLEAN))

            ConditionOperator.IS_EMPTY ->
                FunctionCallExpression("SIZE", listOf(propertyPath)).let { sizeExpr ->
                    BinaryExpression(sizeExpr, BinaryOperator.EQ, LiteralExpression(0, LiteralType.NUMBER))
                }

            ConditionOperator.IS_NOT_EMPTY ->
                FunctionCallExpression("SIZE", listOf(propertyPath)).let { sizeExpr ->
                    BinaryExpression(sizeExpr, BinaryOperator.GT, LiteralExpression(0, LiteralType.NUMBER))
                }
        }
    }

    private fun buildOrderByClause(orderByParts: List<OrderByPart>?, alias: String): OrderByClause? {
        if (orderByParts.isNullOrEmpty()) return null

        val items = orderByParts.map { part ->
            OrderByItem(
                expression = resolvePropertyPath(part.property, alias),
                direction = when (part.direction) {
                    Direction.ASC -> OrderDirection.ASC
                    Direction.DESC -> OrderDirection.DESC
                },
                nulls = null
            )
        }

        return OrderByClause(items)
    }
}
