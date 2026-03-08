package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Transforms a JpqlQuery AST to add implicit JOINs for relationship traversals.
 *
 * This is a preprocessing step that runs before SqlConverter. It:
 * 1. Analyzes all path expressions in the query
 * 2. Detects paths that traverse relationships (e.g., `e.participants.bot.id`)
 * 3. Generates LEFT JOIN clauses for each relationship traversal
 * 4. Rewrites path expressions to use the join aliases
 *
 * After transformation, the AST is self-contained with all necessary JOINs,
 * and SqlConverter can simply convert without additional analysis.
 *
 * Example:
 * Input:  `SELECT e FROM Match e WHERE e.participants.bot.id = :botId`
 * Output: `SELECT e FROM Match e LEFT JOIN e.participants participants_1 LEFT JOIN participants_1.bot bot_2 WHERE bot_2.id = :botId`
 */
class ImplicitJoinTransformer(
    private val entityResolver: EntityResolver
) {
    private val joinGenerator = JoinGenerator(entityResolver)

    /**
     * Maps alias to entity name for relationship resolution.
     */
    private val aliasToEntity = mutableMapOf<String, String>()

    /**
     * Maps a join path (e.g., "e.participants") to its generated alias (e.g., "participants_1").
     */
    private val joinPathToAlias = mutableMapOf<String, String>()

    /**
     * The generated implicit JOIN clauses.
     */
    private val implicitJoins = mutableListOf<JoinClause>()

    /**
     * Transforms the query by adding implicit JOINs and rewriting path expressions.
     *
     * @param query The original JPQL query AST
     * @return A new query with implicit JOINs added and paths rewritten
     */
    fun transform(query: JpqlQuery): JpqlQuery {
        // Reset state
        aliasToEntity.clear()
        joinPathToAlias.clear()
        implicitJoins.clear()
        joinGenerator.resetAliasCounter()

        return transformInternal(query)
    }

    /**
     * Internal transform that doesn't reset state - used for subqueries.
     */
    private fun transformInternal(query: JpqlQuery): JpqlQuery {
        // Save current state for nested subquery processing
        val savedAliasToEntity = aliasToEntity.toMap()
        val savedJoinPathToAlias = joinPathToAlias.toMap()
        val savedImplicitJoins = implicitJoins.toList()
        val savedAliasCounter = joinGenerator.getAliasCounter()

        // Clear state for this query's context
        aliasToEntity.clear()
        joinPathToAlias.clear()
        implicitJoins.clear()
        joinGenerator.setAliasCounter(0)

        // Build initial alias-to-entity mapping for this query
        aliasToEntity[query.from.alias] = query.from.entity.name
        for (entry in query.from.additionalEntities) {
            aliasToEntity[entry.alias] = entry.entity.name
        }
        for (join in query.joins) {
            val resolvedEntity = resolveJoinEntityName(join)
            aliasToEntity[join.alias] = resolvedEntity ?: inferEntityFromPath(join.path)
            // Register explicit join path so we don't create duplicate implicit joins
            val joinPathStr = join.path.parts.joinToString(".")
            joinPathToAlias[joinPathStr] = join.alias
        }

        // First pass: collect all paths and generate implicit joins
        collectImplicitJoins(query)

        // Second pass: rewrite path expressions to use join aliases
        val transformedQuery = rewritePaths(query)

        // Combine explicit and implicit joins
        val allJoins = query.joins + implicitJoins

        val result = transformedQuery.copy(joins = allJoins)

        // Restore outer query state
        aliasToEntity.clear()
        aliasToEntity.putAll(savedAliasToEntity)
        joinPathToAlias.clear()
        joinPathToAlias.putAll(savedJoinPathToAlias)
        implicitJoins.clear()
        implicitJoins.addAll(savedImplicitJoins)
        joinGenerator.setAliasCounter(savedAliasCounter)

        return result
    }

    /**
     * Collects implicit joins by analyzing all path expressions in the query.
     */
    private fun collectImplicitJoins(query: JpqlQuery) {
        val paths = mutableListOf<PathExpression>()

        // Collect paths from all parts of the query
        collectPathsFromSelect(query.select, paths)
        query.where?.let { collectPathsFromExpression(it.condition, paths) }
        query.orderBy?.let { collectPathsFromOrderBy(it, paths) }
        query.groupBy?.let { paths.addAll(it.expressions) }
        query.having?.let { collectPathsFromExpression(it.condition, paths) }
        query.joins.forEach { it.condition?.let { cond -> collectPathsFromExpression(cond, paths) } }

        // Process each path to detect relationship traversals
        for (path in paths) {
            processPathForJoins(path)
        }
    }

    private fun collectPathsFromSelect(select: SelectClause, paths: MutableList<PathExpression>) {
        for (projection in select.projections) {
            when (projection) {
                is FieldProjection -> collectPathsFromExpression(projection.path, paths)
                is AggregateProjection -> collectPathsFromExpression(projection.expression, paths)
                is ConstructorProjection -> projection.arguments.forEach { collectPathsFromExpression(it, paths) }
                is CountAllProjection -> {}
            }
        }
    }

    private fun collectPathsFromOrderBy(orderBy: OrderByClause, paths: MutableList<PathExpression>) {
        for (item in orderBy.items) {
            collectPathsFromExpression(item.expression, paths)
        }
    }

    private fun collectPathsFromExpression(expr: Expression, paths: MutableList<PathExpression>) {
        paths.addAll(ExpressionTransformer.collectPaths(expr))
    }

    /**
     * Processes a path expression to generate implicit JOINs for relationship traversals.
     *
     * Optimization: When accessing `.id` on a single-valued association (e.g., `u.department.id`),
     * no JOIN is needed because the FK column already contains the ID value.
     */
    private fun processPathForJoins(path: PathExpression) {
        if (path.parts.size < 2) return

        val rootAlias = path.parts[0]
        var currentEntity = aliasToEntity[rootAlias] ?: return
        var currentAlias = rootAlias

        // Process each part of the path (except the last which is the final property)
        for (i in 1 until path.parts.size) {
            val fieldName = path.parts[i]
            val joinPath = "$currentAlias.$fieldName"
            val isLastPart = i == path.parts.size - 1

            // Check if we already have a join for this path
            if (joinPathToAlias.containsKey(joinPath)) {
                currentAlias = joinPathToAlias[joinPath]!!
                currentEntity = aliasToEntity[currentAlias] ?: return
                continue
            }

            // Check if this field is a relationship that needs a JOIN
            if (entityResolver.isRelationshipField(currentEntity, fieldName)) {
                // Optimization: accessing .id on a relationship doesn't need a JOIN
                // The FK column already contains the ID value (e.g., u.department.id -> u.department_id)
                if (isLastPart) {
                    // This is the last part and it's a relationship - no JOIN needed
                    // The column resolver will resolve it to the FK column
                    break
                }

                val targetEntity = entityResolver.resolveTargetEntityName(currentEntity, fieldName)

                // Check if the next part is the primary key of the target entity and it's the final part
                val nextPart = path.parts.getOrNull(i + 1)
                val isNextPartLast = i + 1 == path.parts.size - 1

                if (nextPart != null && isNextPartLast && targetEntity != null) {
                    // Check if nextPart is the @Id field of the target entity
                    if (entityResolver.isPrimaryKeyField(targetEntity, nextPart)) {
                        // Path like u.department.id - no JOIN needed
                        // The column resolver will handle this as u.department_id
                        break
                    }
                }
                if (targetEntity != null) {
                    val joinClause = joinGenerator.createJoinClause(currentAlias, fieldName)
                    joinPathToAlias[joinPath] = joinClause.alias
                    aliasToEntity[joinClause.alias] = targetEntity

                    implicitJoins.add(joinClause)

                    currentAlias = joinClause.alias
                    currentEntity = targetEntity
                }
            } else if (entityResolver.isEmbeddedField(currentEntity, fieldName)) {
                // Embedded fields don't need JOINs
                continue
            } else {
                // Not a relationship - stop processing
                break
            }
        }
    }

    /**
     * Rewrites all path expressions in the query to use join aliases.
     */
    private fun rewritePaths(query: JpqlQuery): JpqlQuery {
        return query.copy(
            select = rewriteSelect(query.select),
            where = query.where?.let { WhereClause(rewriteExpression(it.condition)) },
            orderBy = query.orderBy?.let { rewriteOrderBy(it) },
            groupBy = query.groupBy?.let { GroupByClause(it.expressions.map { expr -> rewritePath(expr) }) },
            having = query.having?.let { HavingClause(rewriteExpression(it.condition)) },
            joins = query.joins.map { rewriteJoin(it) }
        )
    }

    private fun rewriteSelect(select: SelectClause): SelectClause {
        return SelectClause(
            distinct = select.distinct,
            projections = select.projections.map { rewriteProjection(it) }
        )
    }

    private fun rewriteProjection(projection: Projection): Projection {
        return when (projection) {
            is FieldProjection -> FieldProjection(rewriteExpression(projection.path), projection.alias)
            is AggregateProjection -> AggregateProjection(
                projection.function,
                projection.distinct,
                rewriteExpression(projection.expression),
                projection.alias
            )
            is ConstructorProjection -> ConstructorProjection(
                projection.className,
                projection.arguments.map { rewriteExpression(it) }
            )
            is CountAllProjection -> projection
        }
    }

    private fun rewriteOrderBy(orderBy: OrderByClause): OrderByClause {
        return OrderByClause(orderBy.items.map { item ->
            OrderByItem(rewriteExpression(item.expression), item.direction, item.nulls)
        })
    }

    private fun rewriteJoin(join: JoinClause): JoinClause {
        return join.copy(condition = join.condition?.let { rewriteExpression(it) })
    }

    private fun rewriteExpression(expr: Expression): Expression {
        return ExpressionTransformer.transform(expr) { e ->
            when (e) {
                is PathExpression -> rewritePath(e)
                is SubqueryExpression -> SubqueryExpression(transformInternal(e.query))
                is ExistsExpression -> ExistsExpression(transformInternal(e.subquery))
                else -> e
            }
        }
    }

    /**
     * Rewrites a path expression to use join aliases for relationship traversals.
     *
     * Given path `e.participants.bot.id`:
     * - Finds join alias for "e.participants" -> "participants_1"
     * - Finds join alias for "participants_1.bot" -> "bot_2"
     * - Returns path ["bot_2", "id"]
     *
     * FK Optimization: When accessing the primary key of a relationship (e.g., `u.department.id`)
     * and NO join exists for that relationship, don't rewrite - let EntityResolver resolve it
     * to the FK column (e.g., `u.department_id`). If a join exists, use it for consistency.
     */
    private fun rewritePath(path: PathExpression): PathExpression {
        if (path.parts.size < 2) return path

        var currentAlias = path.parts[0]
        var partIndex = 1

        // Walk through the path, following implicit joins
        while (partIndex < path.parts.size) {
            val fieldName = path.parts[partIndex]
            val joinPath = "$currentAlias.$fieldName"

            val joinAlias = joinPathToAlias[joinPath]
            if (joinAlias != null) {
                // Join exists - use it for consistency
                currentAlias = joinAlias
                partIndex++
            } else {
                // No join exists - check for FK optimization
                // If accessing .id on a relationship, don't rewrite - let EntityResolver handle FK
                break
            }
        }

        val remainingParts = path.parts.drop(partIndex)
        return PathExpression(listOf(currentAlias) + remainingParts)
    }

    private fun resolveJoinEntityName(join: JoinClause): String? {
        if (join.path.parts.size < 2) return null
        val parentAlias = join.path.parts[0]
        val fieldName = join.path.parts[1]
        val parentEntity = aliasToEntity[parentAlias] ?: return null
        return entityResolver.resolveTargetEntityName(parentEntity, fieldName)
    }

    private fun inferEntityFromPath(path: PathExpression): String =
        joinGenerator.inferEntityFromPath(path)
}
