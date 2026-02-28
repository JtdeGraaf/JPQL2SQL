package com.github.jtdegraaf.jpql2sql.converter.dialect

object OracleDialect : SqlDialect {
    override val name: String = "Oracle"

    override fun quoteIdentifier(identifier: String): String = "\"$identifier\""

    override fun limitClause(limit: Int?, offset: Int?): String? {
        return when {
            limit != null && offset != null -> "OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY"
            limit != null -> "FETCH FIRST $limit ROWS ONLY"
            offset != null -> "OFFSET $offset ROWS"
            else -> null
        }
    }

    override fun concat(expressions: List<String>): String =
        expressions.joinToString(" || ")

    override fun currentDate(): String = "SYSDATE"
    override fun currentTimestamp(): String = "SYSTIMESTAMP"

    override fun substring(str: String, start: String, length: String?): String =
        if (length != null) "SUBSTR($str, $start, $length)"
        else "SUBSTR($str, $start)"
}
