package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.SqlDialect
import com.github.jtdegraaf.jpql2sql.parser.*

/**
 * Converts JPQL AST expressions to SQL strings.
 *
 * Handles paths, binary/unary operators, literals, parameters,
 * function calls, aggregates, CASE, subqueries, IN-lists, and BETWEEN.
 *
 * @param dialect          target SQL dialect for dialect-specific rendering
 * @param entityResolver   resolves entity/field names to table/column names
 * @param aliasToEntity    shared alias→entity mapping built by [SqlConverter]
 * @param subqueryConverter callback for converting subquery ASTs (wired to [SqlConverter.convert])
 */
class ExpressionConverter(
    private val dialect: SqlDialect,
    private val entityResolver: EntityResolver,
    private val aliasToEntity: Map<String, String>,
    private val subqueryConverter: (JpqlQuery) -> String
) {

    fun convert(expr: Expression): String = when (expr) {
        is PathExpression -> convertPath(expr)
        is BinaryExpression -> convertBinary(expr)
        is UnaryExpression -> convertUnary(expr)
        is LiteralExpression -> convertLiteral(expr)
        is ParameterExpression -> convertParameter(expr)
        is FunctionCallExpression -> convertFunctionCall(expr)
        is CaseExpression -> convertCase(expr)
        is SubqueryExpression -> "(${subqueryConverter(expr.query)})"
        is InListExpression -> convertInList(expr)
        is BetweenExpression -> "${convert(expr.lower)} AND ${convert(expr.upper)}"
        is AggregateExpression -> convertAggregate(expr)
        is ExistsExpression -> "EXISTS (${subqueryConverter(expr.subquery)})"
        is CastExpression -> "CAST(${convert(expr.expression)} AS ${dialect.mapJpqlType(expr.targetType)})"
        is UnparsedFragment -> "/* UNPARSED: ${expr.text} */"
    }

    /**
     * Converts an expression, wrapping in parentheses only if the child has lower precedence.
     * OR has lower precedence than AND, so OR needs parens when inside AND.
     */
    private fun convertWithPrecedence(expr: Expression, parentOp: BinaryOperator): String {
        val converted = convert(expr)
        // Only wrap OR in parentheses when the parent is AND
        val needsParens = expr is BinaryExpression &&
            expr.operator == BinaryOperator.OR &&
            parentOp == BinaryOperator.AND
        return if (needsParens) "($converted)" else converted
    }

    fun convertPath(path: PathExpression): String {
        if (path.parts.size == 1 && path.parts[0] == "*") return "*"
        if (path.parts.size == 1) return path.parts[0]

        val alias = path.parts[0]
        val fieldParts = path.parts.drop(1)
        val entityName = aliasToEntity[alias]

        return if (entityName != null && fieldParts.isNotEmpty()) {
            "$alias.${entityResolver.resolveColumnName(entityName, fieldParts)}"
        } else {
            val columnPath = fieldParts.joinToString(".") { EntityResolver.toSnakeCase(it) }
            "$alias.$columnPath"
        }
    }

    private fun convertBinary(expr: BinaryExpression): String {
        val left = convertWithPrecedence(expr.left, expr.operator)
        val right = convertWithPrecedence(expr.right, expr.operator)

        return when (expr.operator) {
            BinaryOperator.AND -> "$left AND $right"
            BinaryOperator.OR -> "$left OR $right"
            BinaryOperator.EQ -> "$left = $right"
            BinaryOperator.NE -> "$left != $right"
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
            BinaryOperator.MEMBER_OF -> "$left IN $right"
            BinaryOperator.NOT_MEMBER_OF -> "$left NOT IN $right"
        }
    }

    private fun convertUnary(expr: UnaryExpression): String {
        val operand = convert(expr.operand)
        return when (expr.operator) {
            UnaryOperator.NOT -> {
                // Only wrap in parentheses if operand is AND/OR expression
                val needsParens = expr.operand is BinaryExpression &&
                    expr.operand.operator in listOf(BinaryOperator.AND, BinaryOperator.OR)
                if (needsParens) "NOT ($operand)" else "NOT $operand"
            }
            UnaryOperator.MINUS -> "-$operand"
        }
    }

    private fun convertLiteral(expr: LiteralExpression): String = when (expr.type) {
        LiteralType.STRING -> "'${expr.value.toString().replace("'", "''")}'"
        LiteralType.NUMBER -> expr.value.toString()
        LiteralType.BOOLEAN -> dialect.booleanLiteral(expr.value as Boolean)
        LiteralType.NULL -> "NULL"
    }

    private fun convertParameter(expr: ParameterExpression): String = when {
        expr.name != null -> ":${expr.name}"
        expr.position != null -> "?${expr.position}"
        else -> "?"
    }

    private fun convertFunctionCall(expr: FunctionCallExpression): String {
        val args = expr.arguments.map { convert(it) }

        return when (expr.name.uppercase()) {
            "CONCAT" -> dialect.concat(args)
            "SUBSTRING", "SUBSTR" -> dialect.substring(
                args.getOrNull(0) ?: "", args.getOrNull(1) ?: "1", args.getOrNull(2)
            )
            "UPPER" -> "UPPER(${args.joinToString(", ")})"
            "LOWER" -> "LOWER(${args.joinToString(", ")})"
            "TRIM" -> if (args.isNotEmpty()) dialect.trim(args[0]) else "TRIM()"
            "LENGTH" -> "LENGTH(${args.joinToString(", ")})"
            "LOCATE" -> if (args.size >= 2) {
                "LOCATE(${args[0]}, ${args[1]}${if (args.size > 2) ", ${args[2]}" else ""})"
            } else {
                "LOCATE(${args.joinToString(", ")})"
            }
            "ABS" -> "ABS(${args.joinToString(", ")})"
            "SQRT" -> "SQRT(${args.joinToString(", ")})"
            "MOD" -> "MOD(${args.joinToString(", ")})"
            "SIZE" -> "COUNT(${args.joinToString(", ")})"
            "CURRENT_DATE" -> dialect.currentDate()
            "CURRENT_TIME" -> dialect.currentTime()
            "CURRENT_TIMESTAMP" -> dialect.currentTimestamp()
            "COALESCE" -> dialect.coalesce(args)
            "NULLIF" -> if (args.size >= 2) dialect.nullif(args[0], args[1]) else "NULLIF(${args.joinToString(", ")})"
            else -> "${expr.name}(${args.joinToString(", ")})"
        }
    }

    private fun convertCase(expr: CaseExpression): String = buildString {
        append("CASE")
        expr.operand?.let { append(" "); append(convert(it)) }
        for (clause in expr.whenClauses) {
            append(" WHEN "); append(convert(clause.condition))
            append(" THEN "); append(convert(clause.result))
        }
        expr.elseExpression?.let { append(" ELSE "); append(convert(it)) }
        append(" END")
    }

    private fun convertInList(expr: InListExpression): String =
        "(${expr.elements.joinToString(", ") { convert(it) }})"

    private fun convertAggregate(expr: AggregateExpression): String {
        val distinct = if (expr.distinct) "DISTINCT " else ""
        return "${expr.function.name}($distinct${convert(expr.argument)})"
    }
}

