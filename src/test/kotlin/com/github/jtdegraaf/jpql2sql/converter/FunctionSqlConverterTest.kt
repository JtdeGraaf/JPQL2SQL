package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for JPQL FUNCTION calls (native database functions).
 */
class FunctionSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addUser(myFixture)
    }

    // ═══════════════════════════════════════════════════════
    //  FUNCTION() - native database function calls
    // ═══════════════════════════════════════════════════════

    fun testFunctionWithNoArgs() {
        val sql = convertWithPostgres("""
            SELECT FUNCTION('now') FROM User u
        """.trimIndent())

        println("FUNCTION with no args: $sql")
        assertEquals("SELECT now() FROM users u", sql)
    }

    fun testFunctionWithSingleArg() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE FUNCTION('date_trunc', 'day', u.createdAt) = :date
        """.trimIndent())

        println("FUNCTION with single arg: $sql")
        assertTrue("Should have date_trunc function", sql.contains("date_trunc('day', u.created_at)"))
    }

    fun testFunctionWithMultipleArgs() {
        val sql = convertWithPostgres("""
            SELECT FUNCTION('regexp_replace', u.name, 'pattern', 'replacement') FROM User u
        """.trimIndent())

        println("FUNCTION with multiple args: $sql")
        assertEquals("SELECT regexp_replace(u.name, 'pattern', 'replacement') FROM users u", sql)
    }

    fun testFunctionInWhereClause() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE FUNCTION('jsonb_extract_path_text', u.name, 'key') = 'value'
        """.trimIndent())

        println("FUNCTION in WHERE: $sql")
        assertTrue("Should have jsonb function", sql.contains("jsonb_extract_path_text(u.name, 'key')"))
        assertTrue("Should have WHERE clause", sql.contains("WHERE"))
    }

    fun testFunctionInSelectWithAlias() {
        val sql = convertWithPostgres("""
            SELECT FUNCTION('array_length', u.name, 1) AS len FROM User u
        """.trimIndent())

        println("FUNCTION with alias: $sql")
        assertEquals("SELECT array_length(u.name, 1) AS len FROM users u", sql)
    }

    fun testNestedFunction() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE FUNCTION('pg_upper', FUNCTION('pg_trim', u.name)) = 'TEST'
        """.trimIndent())

        println("Nested FUNCTION: $sql")
        assertTrue("Should have nested functions", sql.contains("pg_upper(pg_trim(u.name))"))
    }

    fun testStandardNestedFunctions() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE UPPER(TRIM(u.name)) = 'TEST'
        """.trimIndent())

        println("Standard nested functions: $sql")
        assertTrue("Should have nested UPPER(TRIM())", sql.contains("UPPER(TRIM(u.name))"))
    }

    fun testFunctionWithParameter() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE FUNCTION('similarity', u.name, :searchTerm) > 0.5
        """.trimIndent())

        println("FUNCTION with parameter: $sql")
        assertTrue("Should have similarity function", sql.contains("similarity(u.name, :searchTerm)"))
    }

    // ═══════════════════════════════════════════════════════
    //  Standard JPQL functions (should still work)
    // ═══════════════════════════════════════════════════════

    fun testStandardUpperFunction() {
        val sql = convertWithPostgres("""
            SELECT UPPER(u.name) FROM User u
        """.trimIndent())

        println("Standard UPPER: $sql")
        assertEquals("SELECT UPPER(u.name) FROM users u", sql)
    }

    fun testStandardCoalesceFunction() {
        val sql = convertWithPostgres("""
            SELECT COALESCE(u.name, 'Unknown') FROM User u
        """.trimIndent())

        println("Standard COALESCE: $sql")
        assertEquals("SELECT COALESCE(u.name, 'Unknown') FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  Parameterless native function calls - e.g., SYSDATE()
    // ═══════════════════════════════════════════════════════

    fun testParameterlessNativeFunctionInSelect() {
        val sql = convertWithPostgres("""
            SELECT SYSDATE() FROM User u
        """.trimIndent())

        println("Parameterless native function in SELECT: $sql")
        assertEquals("SELECT SYSDATE() FROM users u", sql)
    }

    fun testParameterlessNativeFunctionInWhere() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u WHERE u.createdAt < SYSDATE()
        """.trimIndent())

        println("Parameterless native function in WHERE: $sql")
        assertTrue("Should have SYSDATE()", sql.contains("SYSDATE()"))
        assertTrue("Should have comparison", sql.contains("u.created_at < SYSDATE()"))
    }

    fun testParameterlessNativeFunctionWithAlias() {
        val sql = convertWithPostgres("""
            SELECT SYSDATE() AS currentDate FROM User u
        """.trimIndent())

        println("Parameterless native function with alias: $sql")
        assertEquals("SELECT SYSDATE() AS currentDate FROM users u", sql)
    }

    fun testMultipleParameterlessNativeFunctions() {
        val sql = convertWithPostgres("""
            SELECT SYSDATE(), NOW(), GETDATE() FROM User u
        """.trimIndent())

        println("Multiple parameterless native functions: $sql")
        assertTrue("Should have SYSDATE()", sql.contains("SYSDATE()"))
        assertTrue("Should have NOW()", sql.contains("NOW()"))
        assertTrue("Should have GETDATE()", sql.contains("GETDATE()"))
    }

    fun testParameterlessNativeFunctionMixedWithFields() {
        val sql = convertWithPostgres("""
            SELECT u.id, SYSDATE(), u.name FROM User u
        """.trimIndent())

        println("Parameterless native function mixed with fields: $sql")
        assertEquals("SELECT u.id, SYSDATE(), u.name FROM users u", sql)
    }

    // ═══════════════════════════════════════════════════════
    //  Oracle-specific: SYSDATE without parentheses
    // ═══════════════════════════════════════════════════════

    fun testOracleSysdateNoParens() {
        val sql = convertWithOracle("""
            SELECT SYSDATE() FROM User u
        """.trimIndent())

        println("Oracle SYSDATE (no parens): $sql")
        assertEquals("SELECT SYSDATE FROM users u", sql)
    }

    fun testOracleSystimestampNoParens() {
        val sql = convertWithOracle("""
            SELECT SYSTIMESTAMP() FROM User u
        """.trimIndent())

        println("Oracle SYSTIMESTAMP (no parens): $sql")
        assertEquals("SELECT SYSTIMESTAMP FROM users u", sql)
    }

    fun testOracleUserNoParens() {
        val sql = convertWithOracle("""
            SELECT USER() FROM User u
        """.trimIndent())

        println("Oracle USER (no parens): $sql")
        assertEquals("SELECT USER FROM users u", sql)
    }

    fun testOracleRegularFunctionKeepsParens() {
        val sql = convertWithOracle("""
            SELECT CUSTOM_FUNC() FROM User u
        """.trimIndent())

        println("Oracle regular function (with parens): $sql")
        assertEquals("SELECT CUSTOM_FUNC() FROM users u", sql)
    }
}
