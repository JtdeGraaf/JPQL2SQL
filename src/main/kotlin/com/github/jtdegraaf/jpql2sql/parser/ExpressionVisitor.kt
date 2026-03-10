package com.github.jtdegraaf.jpql2sql.parser

/**
 * Visitor pattern interface for traversing [Expression] types.
 *
 * This provides a type-safe way to handle all expression types without
 * large `when` expressions scattered throughout the codebase.
 *
 * @param T The return type of the visitor methods
 */
interface ExpressionVisitor<T> {

    /**
     * Entry point for visiting any expression. Dispatches to the appropriate
     * typed visit method based on the expression's runtime type.
     */
    fun visit(expr: Expression): T = when (expr) {
        is PathExpression -> visitPath(expr)
        is BinaryExpression -> visitBinary(expr)
        is UnaryExpression -> visitUnary(expr)
        is LiteralExpression -> visitLiteral(expr)
        is ParameterExpression -> visitParameter(expr)
        is FunctionCallExpression -> visitFunctionCall(expr)
        is CaseExpression -> visitCase(expr)
        is SubqueryExpression -> visitSubquery(expr)
        is InListExpression -> visitInList(expr)
        is BetweenExpression -> visitBetween(expr)
        is AggregateExpression -> visitAggregate(expr)
        is ExistsExpression -> visitExists(expr)
        is CastExpression -> visitCast(expr)
        is ExtractExpression -> visitExtract(expr)
        is TrimExpression -> visitTrim(expr)
        is TypeExpression -> visitType(expr)
        is UnparsedFragment -> visitUnparsed(expr)
    }

    fun visitPath(expr: PathExpression): T
    fun visitBinary(expr: BinaryExpression): T
    fun visitUnary(expr: UnaryExpression): T
    fun visitLiteral(expr: LiteralExpression): T
    fun visitParameter(expr: ParameterExpression): T
    fun visitFunctionCall(expr: FunctionCallExpression): T
    fun visitCase(expr: CaseExpression): T
    fun visitSubquery(expr: SubqueryExpression): T
    fun visitInList(expr: InListExpression): T
    fun visitBetween(expr: BetweenExpression): T
    fun visitAggregate(expr: AggregateExpression): T
    fun visitExists(expr: ExistsExpression): T
    fun visitCast(expr: CastExpression): T
    fun visitExtract(expr: ExtractExpression): T
    fun visitTrim(expr: TrimExpression): T
    fun visitType(expr: TypeExpression): T
    fun visitUnparsed(expr: UnparsedFragment): T
}
/**
 * Visitor for collecting expressions into a mutable list.
 * Override methods to add expressions to the [collected] list.
 */
abstract class CollectingExpressionVisitor : ExpressionVisitor<Unit> {
    val collected = mutableListOf<Expression>()

    override fun visitPath(expr: PathExpression) {}
    override fun visitBinary(expr: BinaryExpression) {
        visit(expr.left)
        visit(expr.right)
    }
    override fun visitUnary(expr: UnaryExpression) {
        visit(expr.operand)
    }
    override fun visitLiteral(expr: LiteralExpression) {}
    override fun visitParameter(expr: ParameterExpression) {}
    override fun visitFunctionCall(expr: FunctionCallExpression) {
        expr.arguments.forEach { visit(it) }
    }
    override fun visitCase(expr: CaseExpression) {
        expr.operand?.let { visit(it) }
        expr.whenClauses.forEach {
            visit(it.condition)
            visit(it.result)
        }
        expr.elseExpression?.let { visit(it) }
    }
    override fun visitSubquery(expr: SubqueryExpression) {}
    override fun visitInList(expr: InListExpression) {
        expr.elements.forEach { visit(it) }
    }
    override fun visitBetween(expr: BetweenExpression) {
        visit(expr.lower)
        visit(expr.upper)
    }
    override fun visitAggregate(expr: AggregateExpression) {
        visit(expr.argument)
    }
    override fun visitExists(expr: ExistsExpression) {}
    override fun visitCast(expr: CastExpression) {
        visit(expr.expression)
    }
    override fun visitExtract(expr: ExtractExpression) {
        visit(expr.source)
    }
    override fun visitTrim(expr: TrimExpression) {
        visit(expr.source)
    }
    override fun visitType(expr: TypeExpression) {}
    override fun visitUnparsed(expr: UnparsedFragment) {}
}
