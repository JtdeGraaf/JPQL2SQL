package com.github.jtdegraaf.jpql2sql.converter.dialect

object PostgreSqlDialect : SqlDialect {
    override val name: String = "PostgreSQL"

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
        expressions.joinToString(" || ")

    override fun substring(str: String, start: String, length: String?): String =
        if (length != null) "SUBSTRING($str FROM $start FOR $length)"
        else "SUBSTRING($str FROM $start)"
}
