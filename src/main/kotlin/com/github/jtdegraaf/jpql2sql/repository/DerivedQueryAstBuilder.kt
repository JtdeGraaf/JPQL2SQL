package com.github.jtdegraaf.jpql2sql.repository

import com.github.jtdegraaf.jpql2sql.converter.EntityResolver
import com.github.jtdegraaf.jpql2sql.converter.JoinGenerator
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
    private val joinGenerator = JoinGenerator(entityResolver)

    companion object {
        private const val DEFAULT_ALIAS = "e"

        // Group 1: Simple binary operators (direct mapping from ConditionOperator to BinaryOperator)
        private val SIMPLE_BINARY_OPS = mapOf(
            ConditionOperator.EQUALS to BinaryOperator.EQUALS,
            ConditionOperator.IS to BinaryOperator.EQUALS,
            ConditionOperator.IS_NOT to BinaryOperator.NOT_EQUALS,
            ConditionOperator.LESS_THAN to BinaryOperator.LESS_THAN,
            ConditionOperator.BEFORE to BinaryOperator.LESS_THAN,
            ConditionOperator.LESS_THAN_EQUAL to BinaryOperator.LESS_THAN_OR_EQUAL,
            ConditionOperator.GREATER_THAN to BinaryOperator.GREATER_THAN,
            ConditionOperator.AFTER to BinaryOperator.GREATER_THAN,
            ConditionOperator.GREATER_THAN_EQUAL to BinaryOperator.GREATER_THAN_OR_EQUAL,
            ConditionOperator.LIKE to BinaryOperator.LIKE,
            ConditionOperator.NOT_LIKE to BinaryOperator.NOT_LIKE,
            ConditionOperator.IN to BinaryOperator.IN,
            ConditionOperator.NOT_IN to BinaryOperator.NOT_IN
        )

        // Reusable literal expressions
        private val TRUE_LITERAL = LiteralExpression(true, LiteralType.BOOLEAN)
        private val FALSE_LITERAL = LiteralExpression(false, LiteralType.BOOLEAN)
        private val NULL_LITERAL = LiteralExpression(null, LiteralType.NULL)
        private val ZERO_LITERAL = LiteralExpression(0, LiteralType.NUMBER)
        private val PERCENT_LITERAL = LiteralExpression("%", LiteralType.STRING)
    }

    /**
     * Encapsulates mutable state used during AST building.
     * This includes alias tracking and generated JOIN clauses.
     */
    private class BuilderState {
        val aliasToEntity = mutableMapOf<String, String>()
        val joinPathToAlias = mutableMapOf<String, String>()
        val joins = mutableListOf<JoinClause>()

        fun clear() {
            aliasToEntity.clear()
            joinPathToAlias.clear()
            joins.clear()
        }

        fun registerJoin(joinPath: String, alias: String, entity: String, joinClause: JoinClause) {
            joinPathToAlias[joinPath] = alias
            aliasToEntity[alias] = entity
            joins.add(joinClause)
        }
    }

    private val state = BuilderState()

    fun build(components: DerivedQueryComponents): JpqlQuery {
        // Reset state
        state.clear()
        joinGenerator.resetAliasCounter()

        val alias = DEFAULT_ALIAS
        state.aliasToEntity[alias] = components.entityName

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
            joins = state.joins.toList(),
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
        var currentEntity = state.aliasToEntity[currentAlias] ?: return

        // Process all parts except the last (which is the actual field)
        for (i in 0 until parts.size - 1) {
            val fieldName = parts[i]
            val joinPath = "$currentAlias.$fieldName"

            // Check if we already have a join for this path
            if (state.joinPathToAlias.containsKey(joinPath)) {
                currentAlias = state.joinPathToAlias[joinPath]!!
                currentEntity = state.aliasToEntity[currentAlias] ?: return
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
                    val joinClause = joinGenerator.createJoinClause(currentAlias, fieldName)
                    state.registerJoin(joinPath, joinClause.alias, targetEntity, joinClause)

                    currentAlias = joinClause.alias
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
            val joinAlias = state.joinPathToAlias[joinPath]
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
        val param = ParameterExpression(condition.property.replace(".", "_"), null)

        // Group 1: Simple binary operators (direct mapping)
        SIMPLE_BINARY_OPS[condition.operator]?.let {
            return BinaryExpression(propertyPath, it, param)
        }

        // Group 2: LIKE with pattern
        return when (condition.operator) {
            ConditionOperator.STARTING_WITH -> buildLikeWithPattern(propertyPath, param, suffix = PERCENT_LITERAL)
            ConditionOperator.ENDING_WITH -> buildLikeWithPattern(propertyPath, param, prefix = PERCENT_LITERAL)
            ConditionOperator.CONTAINING -> buildLikeWithPattern(propertyPath, param, PERCENT_LITERAL, PERCENT_LITERAL)
            ConditionOperator.NOT_CONTAINING -> BinaryExpression(
                propertyPath,
                BinaryOperator.NOT_LIKE,
                FunctionCallExpression("CONCAT", listOf(PERCENT_LITERAL, param, PERCENT_LITERAL))
            )

            // Group 3: Boolean/Null checks
            ConditionOperator.IS_TRUE -> BinaryExpression(propertyPath, BinaryOperator.EQUALS, TRUE_LITERAL)
            ConditionOperator.IS_FALSE -> BinaryExpression(propertyPath, BinaryOperator.EQUALS, FALSE_LITERAL)
            ConditionOperator.IS_NULL -> BinaryExpression(propertyPath, BinaryOperator.IS_NULL, NULL_LITERAL)
            ConditionOperator.IS_NOT_NULL -> BinaryExpression(propertyPath, BinaryOperator.IS_NOT_NULL, NULL_LITERAL)

            // Group 4: Collection operations
            ConditionOperator.IS_EMPTY -> BinaryExpression(
                FunctionCallExpression("SIZE", listOf(propertyPath)),
                BinaryOperator.EQUALS,
                ZERO_LITERAL
            )
            ConditionOperator.IS_NOT_EMPTY -> BinaryExpression(
                FunctionCallExpression("SIZE", listOf(propertyPath)),
                BinaryOperator.GREATER_THAN,
                ZERO_LITERAL
            )

            // Group 5: Between
            ConditionOperator.BETWEEN -> buildBetweenExpression(propertyPath, condition.property)

            // Handled by SIMPLE_BINARY_OPS
            else -> throw IllegalArgumentException("Unhandled operator: ${condition.operator}")
        }
    }

    /**
     * Builds a LIKE expression with CONCAT pattern for prefix/suffix matching.
     */
    private fun buildLikeWithPattern(
        propertyPath: PathExpression,
        param: ParameterExpression,
        prefix: LiteralExpression? = null,
        suffix: LiteralExpression? = null
    ): BinaryExpression {
        val args = mutableListOf<Expression>()
        prefix?.let { args.add(it) }
        args.add(param)
        suffix?.let { args.add(it) }
        return BinaryExpression(propertyPath, BinaryOperator.LIKE, FunctionCallExpression("CONCAT", args))
    }

    /**
     * Builds a BETWEEN expression with Start/End parameters.
     */
    private fun buildBetweenExpression(propertyPath: PathExpression, property: String): BinaryExpression {
        val paramName = property.replace(".", "_")
        return BinaryExpression(
            propertyPath,
            BinaryOperator.BETWEEN,
            BetweenExpression(
                ParameterExpression("${paramName}Start", null),
                ParameterExpression("${paramName}End", null)
            )
        )
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
