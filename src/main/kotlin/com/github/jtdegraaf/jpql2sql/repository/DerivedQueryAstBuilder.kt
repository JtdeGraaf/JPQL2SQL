package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.converter.EntityResolver
import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Builds a complete JpqlQuery AST from parsed derived query components.
 *
 * This builder generates all necessary JOINs for relationship traversals directly,
 * producing a complete AST ready for SqlConverter without needing transformation.
 *
 * @param entityResolver Used to detect relationship fields and resolve target entities
 */
class DerivedQueryAstBuilder(
    private val entityResolver: EntityResolver
) {

    companion object {
        private const val DEFAULT_ALIAS = "e"
    }

    private val aliasToEntity = mutableMapOf<String, String>()
    private val joinPathToAlias = mutableMapOf<String, String>()
    private val joins = mutableListOf<JoinClause>()
    private var aliasCounter = 0

    fun build(components: DerivedQueryComponents): JpqlQuery {
        // Reset state
        aliasToEntity.clear()
        joinPathToAlias.clear()
        joins.clear()
        aliasCounter = 0

        val alias = DEFAULT_ALIAS
        aliasToEntity[alias] = components.entityName

        // Collect all property paths that may need joins
        val allPaths = mutableListOf<String>()
        for (condition in components.conditions) {
            allPaths.add(condition.property)
        }
        components.orderBy?.forEach { allPaths.add(it.property) }

        // Generate joins for all nested property paths
        for (path in allPaths) {
            generateJoinsForPath(path, alias)
        }

        val select = buildSelectClause(components, alias)
        val from = buildFromClause(components, alias)
        val where = buildWhereClause(components.conditions, alias)
        val orderBy = buildOrderByClause(components.orderBy, alias)
        val fetch = components.limit?.let { FetchClause(offset = null, fetchCount = it) }

        return JpqlQuery(
            select = select,
            from = from,
            joins = joins.toList(),
            where = where,
            groupBy = null,
            having = null,
            orderBy = orderBy,
            fetch = fetch
        )
    }

    /**
     * Generates JOIN clauses for a property path that may traverse relationships.
     *
     * For a path like "participants.bot.name":
     * - Checks if "participants" is a relationship -> generates JOIN
     * - Checks if "bot" is a relationship on the joined entity -> generates JOIN
     * - "name" is a simple property, no JOIN needed
     *
     * Optimization: For paths like "participants.bot.id" where the last part is the
     * primary key of the relationship target, no JOIN is needed for "bot" because
     * the FK column already contains the ID value.
     */
    private fun generateJoinsForPath(propertyPath: String, rootAlias: String) {
        val parts = propertyPath.split(".")
        if (parts.size <= 1) return // No nested path, no joins needed

        var currentAlias = rootAlias
        var currentEntity = aliasToEntity[currentAlias] ?: return

        // Process all parts except the last (which is the actual field)
        for (i in 0 until parts.size - 1) {
            val fieldName = parts[i]
            val joinPath = "$currentAlias.$fieldName"

            // Check if we already have a join for this path
            if (joinPathToAlias.containsKey(joinPath)) {
                currentAlias = joinPathToAlias[joinPath]!!
                currentEntity = aliasToEntity[currentAlias] ?: return
                continue
            }

            // Check if this field is a relationship that needs a JOIN
            if (entityResolver.isRelationshipField(currentEntity, fieldName)) {
                val targetEntity = entityResolver.resolveTargetEntityName(currentEntity, fieldName)

                // Optimization: if next part is the primary key of target entity and it's the last part,
                // no JOIN is needed - the FK column already contains the ID value
                val nextPart = parts.getOrNull(i + 1)
                val isNextPartLast = i + 1 == parts.size - 1

                if (nextPart != null && isNextPartLast && targetEntity != null) {
                    if (entityResolver.isPrimaryKeyField(targetEntity, nextPart)) {
                        // Skip this JOIN - FK column will be used directly
                        break
                    }
                }

                if (targetEntity != null) {
                    val newAlias = generateAlias(fieldName)
                    joinPathToAlias[joinPath] = newAlias
                    aliasToEntity[newAlias] = targetEntity

                    joins.add(JoinClause(
                        type = JoinType.LEFT,
                        path = PathExpression(listOf(currentAlias, fieldName)),
                        alias = newAlias,
                        condition = null
                    ))

                    currentAlias = newAlias
                    currentEntity = targetEntity
                }
            } else if (entityResolver.isEmbeddedField(currentEntity, fieldName)) {
                // Embedded fields don't need JOINs - continue with same entity
                continue
            } else {
                // Unknown field type - stop processing
                break
            }
        }
    }

    /**
     * Resolves a property path to use the appropriate join alias.
     *
     * For "participants.bot.name" with generated joins:
     * - Returns PathExpression(["bot_2", "name"])
     *
     * For "participants.bot.id" with FK optimization (no bot join):
     * - Returns PathExpression(["participants_1", "bot", "id"])
     *   The SqlConverter will resolve "bot.id" to the FK column "bot_id"
     */
    private fun resolvePropertyPath(property: String, rootAlias: String): PathExpression {
        val parts = property.split(".")
        if (parts.size == 1) {
            return PathExpression(listOf(rootAlias, parts[0]))
        }

        var currentAlias = rootAlias
        var partIndex = 0

        // Walk through the path following joins
        while (partIndex < parts.size - 1) {
            val fieldName = parts[partIndex]
            val joinPath = "$currentAlias.$fieldName"
            val joinAlias = joinPathToAlias[joinPath]
            if (joinAlias != null) {
                currentAlias = joinAlias
                partIndex++
            } else {
                // No join found for this path - stop here and include remaining parts
                break
            }
        }

        // Include all remaining parts from where we stopped
        val remainingParts = parts.drop(partIndex)
        return PathExpression(listOf(currentAlias) + remainingParts)
    }

    private fun generateAlias(baseName: String): String {
        aliasCounter++
        return "${baseName}_$aliasCounter"
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
                    null -> BinaryOperator.AND
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
