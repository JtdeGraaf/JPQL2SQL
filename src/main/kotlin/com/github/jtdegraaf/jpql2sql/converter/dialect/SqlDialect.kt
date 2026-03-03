package com.github.jtdegraaf.jpql2sql.converter.dialect

interface SqlDialect {
    val name: String

    fun quoteIdentifier(identifier: String): String

    fun booleanLiteral(value: Boolean): String = if (value) "TRUE" else "FALSE"

    fun limitClause(limit: Int?, offset: Int?): String?

    fun concat(expressions: List<String>): String

    fun currentDate(): String = "CURRENT_DATE"
    fun currentTime(): String = "CURRENT_TIME"
    fun currentTimestamp(): String = "CURRENT_TIMESTAMP"

    fun substring(str: String, start: String, length: String?): String

    fun trim(str: String): String = "TRIM($str)"

    fun coalesce(expressions: List<String>): String = "COALESCE(${expressions.joinToString(", ")})"

    fun nullif(expr1: String, expr2: String): String = "NULLIF($expr1, $expr2)"

    /**
     * Converts a parameterless native function call.
     * Most databases use FUNC() syntax, but Oracle uses FUNC without parentheses for some functions.
     */
    fun parameterlessFunction(name: String): String = "$name()"

    /**
     * Maps JPQL abstract types to SQL types for CAST expressions.
     * JPQL types: String, Integer, Long, Float, Double, BigDecimal, BigInteger, Date, Time, Timestamp
     */
    fun mapJpqlType(jpqlType: String): String = when (jpqlType.lowercase()) {
        "string" -> "VARCHAR"
        "integer", "int" -> "INTEGER"
        "long" -> "BIGINT"
        "float" -> "REAL"
        "double" -> "DOUBLE PRECISION"
        "bigdecimal" -> "DECIMAL"
        "biginteger" -> "NUMERIC"
        "date" -> "DATE"
        "time" -> "TIME"
        "timestamp" -> "TIMESTAMP"
        "boolean" -> "BOOLEAN"
        else -> jpqlType // Pass through unknown types as-is
    }
}

enum class SqlDialectType {
    POSTGRESQL,
    MYSQL,
    ORACLE,
    SQLSERVER,
    H2
}

fun getSqlDialect(type: SqlDialectType): SqlDialect = when (type) {
    SqlDialectType.POSTGRESQL -> PostgreSqlDialect
    SqlDialectType.MYSQL -> MySqlDialect
    SqlDialectType.ORACLE -> OracleDialect
    SqlDialectType.SQLSERVER -> SqlServerDialect
    SqlDialectType.H2 -> H2Dialect
}
