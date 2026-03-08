package com.github.jtdegraaf.jpql2sql.parser

/**
 * Utility object for recursively transforming expressions.
 *
 * Handles child traversal automatically, allowing transformation functions
 * to focus on individual expression types without worrying about recursion.
 */
object ExpressionTransformer {

    /**
     * Recursively transforms an expression tree.
     *
     * The transform function is called on each expression. If it returns a different
     * expression, that expression's children are then recursively transformed.
     *
     * @param expr The expression to transform
     * @param transform A function that transforms individual expressions
     * @return The transformed expression tree
     */
    fun transform(expr: Expression, transform: (Expression) -> Expression): Expression {
        val transformed = transform(expr)
        return transformChildren(transformed, transform)
    }

    /**
     * Transforms only the children of an expression, not the expression itself.
     */
    private fun transformChildren(expr: Expression, transform: (Expression) -> Expression): Expression {
        return when (expr) {
            is BinaryExpression -> expr.copy(
                left = transform(expr.left, transform),
                right = transform(expr.right, transform)
            )
            is UnaryExpression -> expr.copy(
                operand = transform(expr.operand, transform)
            )
            is FunctionCallExpression -> expr.copy(
                arguments = expr.arguments.map { transform(it, transform) }
            )
            is CaseExpression -> expr.copy(
                operand = expr.operand?.let { transform(it, transform) },
                whenClauses = expr.whenClauses.map { clause ->
                    WhenClause(
                        transform(clause.condition, transform),
                        transform(clause.result, transform)
                    )
                },
                elseExpression = expr.elseExpression?.let { transform(it, transform) }
            )
            is InListExpression -> expr.copy(
                elements = expr.elements.map { transform(it, transform) }
            )
            is BetweenExpression -> expr.copy(
                lower = transform(expr.lower, transform),
                upper = transform(expr.upper, transform)
            )
            is AggregateExpression -> expr.copy(
                argument = transform(expr.argument, transform)
            )
            is CastExpression -> expr.copy(
                expression = transform(expr.expression, transform)
            )
            is ExtractExpression -> expr.copy(
                source = transform(expr.source, transform)
            )
            is TrimExpression -> expr.copy(
                source = transform(expr.source, transform)
            )
            // Leaf nodes - no children to transform
            is PathExpression,
            is LiteralExpression,
            is ParameterExpression,
            is SubqueryExpression,
            is ExistsExpression,
            is TypeExpression,
            is UnparsedFragment -> expr
        }
    }

    /**
     * Collects all expressions matching a predicate from an expression tree.
     *
     * @param expr The root expression to search
     * @param predicate A function that returns true for expressions to collect
     * @return List of matching expressions
     */
    fun collect(expr: Expression, predicate: (Expression) -> Boolean): List<Expression> {
        val result = mutableListOf<Expression>()
        collectInternal(expr, predicate, result)
        return result
    }

    private fun collectInternal(
        expr: Expression,
        predicate: (Expression) -> Boolean,
        result: MutableList<Expression>
    ) {
        if (predicate(expr)) {
            result.add(expr)
        }

        when (expr) {
            is BinaryExpression -> {
                collectInternal(expr.left, predicate, result)
                collectInternal(expr.right, predicate, result)
            }
            is UnaryExpression -> collectInternal(expr.operand, predicate, result)
            is FunctionCallExpression -> expr.arguments.forEach { collectInternal(it, predicate, result) }
            is CaseExpression -> {
                expr.operand?.let { collectInternal(it, predicate, result) }
                expr.whenClauses.forEach {
                    collectInternal(it.condition, predicate, result)
                    collectInternal(it.result, predicate, result)
                }
                expr.elseExpression?.let { collectInternal(it, predicate, result) }
            }
            is InListExpression -> expr.elements.forEach { collectInternal(it, predicate, result) }
            is BetweenExpression -> {
                collectInternal(expr.lower, predicate, result)
                collectInternal(expr.upper, predicate, result)
            }
            is AggregateExpression -> collectInternal(expr.argument, predicate, result)
            is CastExpression -> collectInternal(expr.expression, predicate, result)
            is ExtractExpression -> collectInternal(expr.source, predicate, result)
            is TrimExpression -> collectInternal(expr.source, predicate, result)
            // Leaf nodes - no children to collect from
            is PathExpression,
            is LiteralExpression,
            is ParameterExpression,
            is SubqueryExpression,
            is ExistsExpression,
            is TypeExpression,
            is UnparsedFragment -> {}
        }
    }

    /**
     * Collects all PathExpressions from an expression tree.
     *
     * @param expr The root expression to search
     * @return List of all PathExpressions found
     */
    fun collectPaths(expr: Expression): List<PathExpression> {
        return collect(expr) { it is PathExpression }.filterIsInstance<PathExpression>()
    }
}
