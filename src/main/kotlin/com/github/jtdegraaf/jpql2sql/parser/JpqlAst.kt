package com.github.jtdegraaf.jpql2sql.parser

sealed class JpqlNode

data class JpqlQuery(
    val select: SelectClause,
    val from: FromClause,
    val joins: List<JoinClause> = emptyList(),
    val where: WhereClause? = null,
    val groupBy: GroupByClause? = null,
    val having: HavingClause? = null,
    val orderBy: OrderByClause? = null,
    val fetch: FetchClause? = null,
    val unparsedFragments: List<String> = emptyList()  // Any unparsed content between/after clauses
) : JpqlNode()

data class SelectClause(
    val distinct: Boolean = false,
    val projections: List<Projection>
) : JpqlNode()

sealed class Projection : JpqlNode()

data class FieldProjection(
    val path: Expression,
    val alias: String? = null
) : Projection()

data class ConstructorProjection(
    val className: String,
    val arguments: List<Expression>
) : Projection()

object CountAllProjection : Projection()

data class AggregateProjection(
    val function: AggregateFunction,
    val distinct: Boolean = false,
    val expression: Expression,
    val alias: String? = null
) : Projection()

enum class AggregateFunction {
    COUNT, SUM, AVG, MIN, MAX
}

data class FromClause(
    val entity: EntityReference,
    val alias: String,
    val additionalEntities: List<FromEntry> = emptyList()
) : JpqlNode()

data class FromEntry(
    val entity: EntityReference,
    val alias: String
) : JpqlNode()

data class EntityReference(
    val name: String
) : JpqlNode()

data class JoinClause(
    val type: JoinType,
    val path: PathExpression,
    val alias: String,
    val condition: Expression? = null
) : JpqlNode()

enum class JoinType {
    INNER, LEFT, RIGHT
}

data class WhereClause(
    val condition: Expression
) : JpqlNode()

data class GroupByClause(
    val expressions: List<PathExpression>
) : JpqlNode()

data class HavingClause(
    val condition: Expression
) : JpqlNode()

data class OrderByClause(
    val items: List<OrderByItem>
) : JpqlNode()

data class OrderByItem(
    val expression: Expression,
    val direction: OrderDirection = OrderDirection.ASC,
    val nulls: NullsOrdering? = null
) : JpqlNode()

enum class OrderDirection {
    ASC, DESC
}

enum class NullsOrdering {
    FIRST, LAST
}

/**
 * Represents OFFSET m ROWS FETCH FIRST n ROWS ONLY clause.
 */
data class FetchClause(
    val offset: Int? = null,
    val fetchCount: Int
) : JpqlNode()

sealed class Expression : JpqlNode()

data class PathExpression(
    val parts: List<String>
) : Expression() {
    val root: String get() = parts.first()
    val path: String get() = parts.joinToString(".")
}

data class BinaryExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression
) : Expression()

enum class BinaryOperator {
    AND, OR,
    EQ, NE, LT, LE, GT, GE,
    LIKE, NOT_LIKE,
    IN, NOT_IN,
    BETWEEN, NOT_BETWEEN,
    IS_NULL, IS_NOT_NULL,
    MEMBER_OF, NOT_MEMBER_OF,
    ADD, SUBTRACT, MULTIPLY, DIVIDE
}

data class UnaryExpression(
    val operator: UnaryOperator,
    val operand: Expression
) : Expression()

enum class UnaryOperator {
    NOT, MINUS
}

data class LiteralExpression(
    val value: Any?,
    val type: LiteralType
) : Expression()

enum class LiteralType {
    STRING, NUMBER, BOOLEAN, NULL
}

data class ParameterExpression(
    val name: String?,
    val position: Int?
) : Expression()

data class FunctionCallExpression(
    val name: String,
    val arguments: List<Expression>
) : Expression()

data class CaseExpression(
    val operand: Expression?,
    val whenClauses: List<WhenClause>,
    val elseExpression: Expression?
) : Expression()

data class WhenClause(
    val condition: Expression,
    val result: Expression
) : JpqlNode()

data class SubqueryExpression(
    val query: JpqlQuery
) : Expression()

data class InListExpression(
    val elements: List<Expression>
) : Expression()

data class BetweenExpression(
    val lower: Expression,
    val upper: Expression
) : Expression()

data class AggregateExpression(
    val function: AggregateFunction,
    val distinct: Boolean,
    val argument: Expression
) : Expression()

/**
 * Represents an EXISTS (subquery) expression.
 */
data class ExistsExpression(
    val subquery: JpqlQuery
) : Expression()

/**
 * Represents a CAST(expression AS type) expression.
 */
data class CastExpression(
    val expression: Expression,
    val targetType: String
) : Expression()

/**
 * Represents unparsed/unrecognized content that should be passed through as-is.
 * Used for resilient parsing when the parser encounters unexpected syntax.
 */
data class UnparsedFragment(
    val text: String
) : Expression()

