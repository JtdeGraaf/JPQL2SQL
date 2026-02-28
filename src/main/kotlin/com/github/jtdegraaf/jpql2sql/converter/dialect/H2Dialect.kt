package com.github.jtdegraaf.jpql2sql.converter.dialect

object H2Dialect : SqlDialect {
    override val name: String = "H2"

    override fun quoteIdentifier(identifier: String): String = "\"$identifier\""

    override fun limitClause(limit: Int?, offset: Int?): String? {
        return buildString {
            if (limit != null) append("LIMIT $limit")
            if (offset != null) {
                if (limit != null) append(" ")
                append("OFFSET $offset")
            }
        }.ifEmpty { null }
    }

    override fun concat(expressions: List<String>): String =
        "CONCAT(${expressions.joinToString(", ")})"

    override fun substring(str: String, start: String, length: String?): String =
        if (length != null) "SUBSTRING($str, $start, $length)"
        else "SUBSTRING($str, $start)"
}
