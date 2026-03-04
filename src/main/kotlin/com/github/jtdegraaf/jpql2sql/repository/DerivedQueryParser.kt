package com.github.jtdegraaf.jpql2sql.repository

/**
 * Parses Spring Data JPA derived query method names into structured components.
 *
 * Supports method patterns like:
 * - findByName
 * - findByNameAndAgeGreaterThan
 * - findDistinctByStatusOrderByCreatedAtDesc
 * - countByStatus
 * - existsByEmail
 * - deleteByStatus
 * - findTop10ByNameOrderByCreatedAtDesc
 */
class DerivedQueryParser {

    companion object {
        // Prefixes in order of preference (longer first to avoid partial matches)
        private val FIND_PREFIXES = listOf(
            "findAllBy", "findBy",
            "readAllBy", "readBy",
            "getBy",
            "queryAllBy", "queryBy",
            "searchAllBy", "searchBy",
            "streamAllBy", "streamBy"
        )

        private val COUNT_PREFIXES = listOf("countBy")
        private val EXISTS_PREFIXES = listOf("existsBy")
        private val DELETE_PREFIXES = listOf("deleteBy", "removeBy")

        // Special prefix patterns that come before "By"
        private val FIND_ALL_NO_BY = listOf("findAll", "readAll", "getAll", "queryAll", "searchAll", "streamAll")

        // Operators in order of match priority (longest first)
        private val OPERATOR_SUFFIXES = listOf(
            "GreaterThanEqual" to ConditionOperator.GREATER_THAN_EQUAL,
            "LessThanEqual" to ConditionOperator.LESS_THAN_EQUAL,
            "IsNotNull" to ConditionOperator.IS_NOT_NULL,
            "IsNotEmpty" to ConditionOperator.IS_NOT_EMPTY,
            "NotContaining" to ConditionOperator.NOT_CONTAINING,
            "StartingWith" to ConditionOperator.STARTING_WITH,
            "EndingWith" to ConditionOperator.ENDING_WITH,
            "Containing" to ConditionOperator.CONTAINING,
            "IsNotEmpty" to ConditionOperator.IS_NOT_EMPTY,
            "IsEmpty" to ConditionOperator.IS_EMPTY,
            "NotLike" to ConditionOperator.NOT_LIKE,
            "IsNull" to ConditionOperator.IS_NULL,
            "IsTrue" to ConditionOperator.IS_TRUE,
            "IsFalse" to ConditionOperator.IS_FALSE,
            "GreaterThan" to ConditionOperator.GREATER_THAN,
            "LessThan" to ConditionOperator.LESS_THAN,
            "Between" to ConditionOperator.BETWEEN,
            "NotIn" to ConditionOperator.NOT_IN,
            "IsNot" to ConditionOperator.IS_NOT,
            "Before" to ConditionOperator.BEFORE,
            "After" to ConditionOperator.AFTER,
            "Like" to ConditionOperator.LIKE,
            "Is" to ConditionOperator.IS,
            "In" to ConditionOperator.IN,
            "Not" to ConditionOperator.IS_NOT
        )
    }

    fun parse(methodName: String, entityName: String): DerivedQueryComponents? {
        var remaining = methodName
        var prefix: QueryPrefix? = null
        var distinct = false
        var limit: Int? = null

        // Check for findAll without conditions
        for (findAllPrefix in FIND_ALL_NO_BY) {
            if (remaining == findAllPrefix) {
                return DerivedQueryComponents(
                    prefix = QueryPrefix.FIND,
                    entityName = entityName,
                    conditions = emptyList(),
                    orderBy = null,
                    distinct = false,
                    limit = null
                )
            }
        }

        // Match prefix
        for (p in FIND_PREFIXES) {
            if (remaining.startsWith(p)) {
                prefix = QueryPrefix.FIND
                remaining = remaining.removePrefix(p)
                break
            }
        }
        if (prefix == null) {
            for (p in COUNT_PREFIXES) {
                if (remaining.startsWith(p)) {
                    prefix = QueryPrefix.COUNT
                    remaining = remaining.removePrefix(p)
                    break
                }
            }
        }
        if (prefix == null) {
            for (p in EXISTS_PREFIXES) {
                if (remaining.startsWith(p)) {
                    prefix = QueryPrefix.EXISTS
                    remaining = remaining.removePrefix(p)
                    break
                }
            }
        }
        if (prefix == null) {
            for (p in DELETE_PREFIXES) {
                if (remaining.startsWith(p)) {
                    prefix = QueryPrefix.DELETE
                    remaining = remaining.removePrefix(p)
                    break
                }
            }
        }

        // Handle find/read/get/query/search/stream without explicit "By" but with Distinct/Top/First
        if (prefix == null) {
            val simplePrefixMatch = Regex("^(find|read|get|query|search|stream)(Distinct)?(Top|First)?(\\d+)?(.*)$")
                .matchEntire(methodName)
            if (simplePrefixMatch != null) {
                prefix = QueryPrefix.FIND
                distinct = simplePrefixMatch.groupValues[2] == "Distinct"
                val topFirst = simplePrefixMatch.groupValues[3]
                val number = simplePrefixMatch.groupValues[4]
                remaining = simplePrefixMatch.groupValues[5]

                if (topFirst.isNotEmpty()) {
                    limit = if (number.isNotEmpty()) number.toInt() else 1
                }

                // Remove "By" if present
                if (remaining.startsWith("By")) {
                    remaining = remaining.removePrefix("By")
                }
            }
        }

        if (prefix == null) {
            return null
        }

        // Check for Distinct after prefix
        if (remaining.startsWith("Distinct")) {
            distinct = true
            remaining = remaining.removePrefix("Distinct")
        }

        // Check for Top/First N
        val topFirstMatch = Regex("^(Top|First)(\\d+)?(.*)$").matchEntire(remaining)
        if (topFirstMatch != null) {
            val number = topFirstMatch.groupValues[2]
            limit = if (number.isNotEmpty()) number.toInt() else 1
            remaining = topFirstMatch.groupValues[3]
        }

        // Split by OrderBy to separate conditions from ordering
        val orderByIndex = remaining.indexOf("OrderBy")
        val conditionsPart: String
        val orderByPart: String?

        if (orderByIndex >= 0) {
            conditionsPart = remaining.substring(0, orderByIndex)
            orderByPart = remaining.substring(orderByIndex + "OrderBy".length)
        } else {
            conditionsPart = remaining
            orderByPart = null
        }

        // Parse conditions
        val conditions = parseConditions(conditionsPart)

        // Parse order by
        val orderBy = orderByPart?.let { parseOrderBy(it) }

        return DerivedQueryComponents(
            prefix = prefix,
            entityName = entityName,
            conditions = conditions,
            orderBy = orderBy,
            distinct = distinct,
            limit = limit
        )
    }

    private fun parseConditions(conditionsPart: String): List<PropertyCondition> {
        if (conditionsPart.isEmpty()) {
            return emptyList()
        }

        val conditions = mutableListOf<PropertyCondition>()
        var remaining = conditionsPart
        var isFirst = true

        while (remaining.isNotEmpty()) {
            val connector: Connector?

            if (isFirst) {
                connector = null
                isFirst = false
            } else {
                // Look for And/Or connector
                when {
                    remaining.startsWith("And") -> {
                        connector = Connector.AND
                        remaining = remaining.removePrefix("And")
                    }
                    remaining.startsWith("Or") -> {
                        connector = Connector.OR
                        remaining = remaining.removePrefix("Or")
                    }
                    else -> {
                        // No more connectors found, this shouldn't happen in valid method names
                        break
                    }
                }
            }

            // Find next And/Or to determine the end of this condition
            val nextAndIndex = findConnectorIndex(remaining, "And")
            val nextOrIndex = findConnectorIndex(remaining, "Or")

            val endIndex = when {
                nextAndIndex < 0 && nextOrIndex < 0 -> remaining.length
                nextAndIndex < 0 -> nextOrIndex
                nextOrIndex < 0 -> nextAndIndex
                else -> minOf(nextAndIndex, nextOrIndex)
            }

            val conditionStr = remaining.substring(0, endIndex)
            remaining = remaining.substring(endIndex)

            val condition = parseCondition(conditionStr, connector)
            if (condition != null) {
                conditions.add(condition)
            }
        }

        return conditions
    }

    /**
     * Find the index of a connector (And/Or) that's at a camelCase boundary.
     * This avoids matching "And" in "Anderson" or "Or" in "Order".
     */
    private fun findConnectorIndex(str: String, connector: String): Int {
        var index = 0
        while (index < str.length) {
            val foundIndex = str.indexOf(connector, index)
            if (foundIndex < 0) return -1

            // Check if this is at a camelCase boundary
            // The character before should be lowercase, and connector starts with uppercase
            if (foundIndex > 0) {
                val charBefore = str[foundIndex - 1]
                if (charBefore.isLowerCase() || charBefore.isDigit()) {
                    // Check if after the connector is uppercase or end of string
                    val afterConnector = foundIndex + connector.length
                    if (afterConnector >= str.length || str[afterConnector].isUpperCase()) {
                        return foundIndex
                    }
                }
            }
            index = foundIndex + 1
        }
        return -1
    }

    private fun parseCondition(conditionStr: String, connector: Connector?): PropertyCondition? {
        if (conditionStr.isEmpty()) return null

        // Try to match operator suffixes (longest first)
        for ((suffix, operator) in OPERATOR_SUFFIXES) {
            if (conditionStr.endsWith(suffix)) {
                val property = conditionStr.removeSuffix(suffix)
                if (property.isNotEmpty()) {
                    return PropertyCondition(
                        property = decapitalize(property),
                        operator = operator,
                        connector = connector
                    )
                }
            }
        }

        // No operator suffix found - default to EQUALS
        return PropertyCondition(
            property = decapitalize(conditionStr),
            operator = ConditionOperator.EQUALS,
            connector = connector
        )
    }

    private fun parseOrderBy(orderByPart: String): List<OrderByPart> {
        if (orderByPart.isEmpty()) {
            return emptyList()
        }

        val parts = mutableListOf<OrderByPart>()
        var remaining = orderByPart

        while (remaining.isNotEmpty()) {
            // Find direction suffix
            val direction: Direction
            val propertyEnd: Int

            when {
                remaining.endsWith("Desc") -> {
                    direction = Direction.DESC
                    // Find where this Desc's property starts
                    val descIndex = remaining.lastIndexOf("Desc")
                    // Look for Asc before it as the separator for the previous property
                    val ascBeforeDesc = remaining.lastIndexOf("Asc", descIndex - 1)
                    val descBeforeDesc = remaining.lastIndexOf("Desc", descIndex - 1)
                    val prevEnd = maxOf(
                        if (ascBeforeDesc >= 0) ascBeforeDesc + 3 else 0,
                        if (descBeforeDesc >= 0) descBeforeDesc + 4 else 0
                    )
                    val property = remaining.substring(prevEnd, descIndex)
                    if (property.isNotEmpty()) {
                        parts.add(OrderByPart(decapitalize(property), direction))
                    }
                    remaining = remaining.substring(0, prevEnd)
                }
                remaining.endsWith("Asc") -> {
                    direction = Direction.ASC
                    val ascIndex = remaining.lastIndexOf("Asc")
                    val ascBeforeAsc = remaining.lastIndexOf("Asc", ascIndex - 1)
                    val descBeforeAsc = remaining.lastIndexOf("Desc", ascIndex - 1)
                    val prevEnd = maxOf(
                        if (ascBeforeAsc >= 0) ascBeforeAsc + 3 else 0,
                        if (descBeforeAsc >= 0) descBeforeAsc + 4 else 0
                    )
                    val property = remaining.substring(prevEnd, ascIndex)
                    if (property.isNotEmpty()) {
                        parts.add(OrderByPart(decapitalize(property), direction))
                    }
                    remaining = remaining.substring(0, prevEnd)
                }
                else -> {
                    // No direction suffix - default to ASC
                    // Find the last Asc or Desc to determine where this property starts
                    val lastAsc = remaining.lastIndexOf("Asc")
                    val lastDesc = remaining.lastIndexOf("Desc")
                    val prevEnd = maxOf(
                        if (lastAsc >= 0) lastAsc + 3 else 0,
                        if (lastDesc >= 0) lastDesc + 4 else 0
                    )
                    val property = remaining.substring(prevEnd)
                    if (property.isNotEmpty()) {
                        parts.add(OrderByPart(decapitalize(property), Direction.ASC))
                    }
                    remaining = remaining.substring(0, prevEnd)
                }
            }
        }

        return parts.reversed()
    }

    private fun decapitalize(str: String): String {
        if (str.isEmpty()) return str
        return str[0].lowercaseChar() + str.substring(1)
    }
}
