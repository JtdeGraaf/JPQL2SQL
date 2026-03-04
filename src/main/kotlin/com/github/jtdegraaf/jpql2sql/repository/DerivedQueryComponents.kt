package com.github.jtdegraaf.jpql2sql.repository

/**
 * Data classes for parsed Spring Data JPA derived query method components.
 */

data class DerivedQueryComponents(
    val prefix: QueryPrefix,
    val entityName: String,
    val conditions: List<PropertyCondition>,
    val orderBy: List<OrderByPart>?,
    val distinct: Boolean,
    val limit: Int?
)

enum class QueryPrefix {
    FIND, COUNT, EXISTS, DELETE
}

data class PropertyCondition(
    val property: String,
    val operator: ConditionOperator,
    val connector: Connector?
)

enum class ConditionOperator {
    EQUALS,
    IS, IS_NOT,
    LIKE, NOT_LIKE,
    STARTING_WITH,
    ENDING_WITH,
    CONTAINING, NOT_CONTAINING,
    LESS_THAN, LESS_THAN_EQUAL,
    GREATER_THAN, GREATER_THAN_EQUAL,
    BETWEEN,
    IN, NOT_IN,
    IS_NULL, IS_NOT_NULL,
    IS_TRUE, IS_FALSE,
    BEFORE, AFTER,
    IS_EMPTY, IS_NOT_EMPTY
}

enum class Connector {
    AND, OR
}

data class OrderByPart(
    val property: String,
    val direction: Direction
)

enum class Direction {
    ASC, DESC
}
