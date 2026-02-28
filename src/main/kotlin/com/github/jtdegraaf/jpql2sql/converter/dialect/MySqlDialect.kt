package com.github.jtdegraaf.jpql2sql.converter.dialect

object MySqlDialect : SqlDialect {
    override val name: String = "MySQL"

    override fun quoteIdentifier(identifier: String): String = "`$identifier`"

    override fun limitClause(limit: Int?, offset: Int?): String? {
        return when {
            limit != null && offset != null -> "LIMIT $offset, $limit"
            limit != null -> "LIMIT $limit"
            offset != null -> "LIMIT 18446744073709551615 OFFSET $offset"
            else -> null
        }
    }

    override fun concat(expressions: List<String>): String =
        "CONCAT(${expressions.joinToString(", ")})"

    override fun substring(str: String, start: String, length: String?): String =
        if (length != null) "SUBSTRING($str, $start, $length)"
        else "SUBSTRING($str, $start)"
}
