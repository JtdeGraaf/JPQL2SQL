package com.github.jtdegraaf.jpql2sql.converter.resolver

/**
 * Utility for JPA naming convention conversions.
 */
object NamingUtils {

    fun toSnakeCase(input: String): String {
        return input
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }
}
