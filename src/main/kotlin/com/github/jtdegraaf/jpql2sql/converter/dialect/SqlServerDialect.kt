package com.github.jtdegraaf.jpql2sql.converter.dialect

object SqlServerDialect : SqlDialect {
    override val name: String = "SQL Server"

    override fun quoteIdentifier(identifier: String): String = "[$identifier]"

    override fun limitClause(limit: Int?, offset: Int?): String? {
        return when {
            limit != null && offset != null -> "OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY"
            limit != null -> "OFFSET 0 ROWS FETCH NEXT $limit ROWS ONLY"
            offset != null -> "OFFSET $offset ROWS"
            else -> null
        }
    }

    override fun concat(expressions: List<String>): String =
        "CONCAT(${expressions.joinToString(", ")})"

    override fun currentDate(): String = "CAST(GETDATE() AS DATE)"
    override fun currentTime(): String = "CAST(GETDATE() AS TIME)"
    override fun currentTimestamp(): String = "GETDATE()"

    override fun substring(str: String, start: String, length: String?): String =
        if (length != null) "SUBSTRING($str, $start, $length)"
        else "SUBSTRING($str, $start, LEN($str))"

    override fun booleanLiteral(value: Boolean): String = if (value) "1" else "0"
}
