package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Builds a JpqlQuery AST from parsed derived query components.
 *
 * This enables reuse of the existing SqlConverter for all SQL generation,
 * avoiding duplicate conversion logic.
 */
class DerivedQueryAstBuilder {

    companion object {
        private const val DEFAULT_ALIAS = "e"
    }

    fun build(components: DerivedQueryComponents): JpqlQuery {
        val alias = DEFAULT_ALIAS

        val select = buildSelectClause(components, alias)
        val from = buildFromClause(components, alias)
        val where = buildWhereClause(components.conditions, alias)
        val orderBy = buildOrderByClause(components.orderBy, alias)
        val fetch = components.limit?.let { FetchClause(offset = null, fetchCount = it) }

        return JpqlQuery(
            select = select,
            from = from,
            joins = emptyList(),
            where = where,
            groupBy = null,
            having = null,
            orderBy = orderBy,
            fetch = fetch
        )
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
        val propertyPath = buildPropertyPath(condition.property, alias)
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

    private fun buildPropertyPath(property: String, alias: String): PathExpression {
        // Handle nested properties like "address.city" -> ["e", "address", "city"]
        val parts = property.split(".")
        return PathExpression(listOf(alias) + parts)
    }

    private fun buildOrderByClause(orderByParts: List<OrderByPart>?, alias: String): OrderByClause? {
        if (orderByParts.isNullOrEmpty()) return null

        val items = orderByParts.map { part ->
            OrderByItem(
                expression = buildPropertyPath(part.property, alias),
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
