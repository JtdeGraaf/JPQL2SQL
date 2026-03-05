package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.*
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser

/**
 * Tests that verify correct SQL generation across all supported dialects.
 * Validates dialect-specific differences in LIMIT/OFFSET, CONCAT, boolean literals,
 * date functions, SUBSTRING, and other dialect-specific features.
 */
class DialectComparisonTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        addUserEntity()
    }

    private fun addUserEntity() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.time.LocalDateTime;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "email")
                private String email;

                @Column(name = "status")
                private String status;

                @Column(name = "age")
                private Integer age;

                @Column(name = "active")
                private Boolean active;

                @Column(name = "created_at")
                private LocalDateTime createdAt;
            }
        """.trimIndent())
    }

    private fun convertWith(jpql: String, dialect: SqlDialect): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        val transformer = ImplicitJoinTransformer(resolver)
        val transformedAst = transformer.transform(ast)
        return SqlConverter(dialect, resolver).convert(transformedAst)
    }

    // ============ LIMIT/OFFSET Syntax Tests ============

    fun testLimitOnlyPostgres() {
        val sql = convertWith("SELECT u FROM User u FETCH FIRST 10 ROWS ONLY", PostgreSqlDialect)
        assertTrue("PostgreSQL uses LIMIT", sql.contains("LIMIT 10"))
        assertFalse("PostgreSQL should not use FETCH", sql.contains("FETCH"))
    }

    fun testLimitOnlyMySql() {
        val sql = convertWith("SELECT u FROM User u FETCH FIRST 10 ROWS ONLY", MySqlDialect)
        assertTrue("MySQL uses LIMIT", sql.contains("LIMIT 10"))
    }

    fun testLimitOnlyOracle() {
        val sql = convertWith("SELECT u FROM User u FETCH FIRST 10 ROWS ONLY", OracleDialect)
        assertTrue("Oracle uses FETCH FIRST n ROWS ONLY", sql.contains("FETCH FIRST 10 ROWS ONLY"))
    }

    fun testLimitOnlySqlServer() {
        val sql = convertWith("SELECT u FROM User u FETCH FIRST 10 ROWS ONLY", SqlServerDialect)
        assertTrue("SQL Server uses OFFSET 0 ROWS FETCH NEXT", sql.contains("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY"))
    }

    fun testLimitOnlyH2() {
        val sql = convertWith("SELECT u FROM User u FETCH FIRST 10 ROWS ONLY", H2Dialect)
        assertTrue("H2 uses LIMIT", sql.contains("LIMIT 10"))
    }

    fun testOffsetAndLimitPostgres() {
        val sql = convertWith("SELECT u FROM User u OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY", PostgreSqlDialect)
        assertTrue("PostgreSQL uses LIMIT n OFFSET m", sql.contains("LIMIT 10 OFFSET 5"))
    }

    fun testOffsetAndLimitMySql() {
        val sql = convertWith("SELECT u FROM User u OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY", MySqlDialect)
        assertTrue("MySQL uses LIMIT offset, count", sql.contains("LIMIT 5, 10"))
    }

    fun testOffsetAndLimitOracle() {
        val sql = convertWith("SELECT u FROM User u OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY", OracleDialect)
        assertTrue("Oracle uses OFFSET n ROWS FETCH NEXT m ROWS ONLY", sql.contains("OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY"))
    }

    fun testOffsetAndLimitSqlServer() {
        val sql = convertWith("SELECT u FROM User u OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY", SqlServerDialect)
        assertTrue("SQL Server uses OFFSET n ROWS FETCH NEXT m ROWS ONLY", sql.contains("OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY"))
    }

    // ============ CONCAT Function Tests ============

    fun testConcatPostgres() {
        val sql = convertWith("SELECT CONCAT(u.name, ' ', u.email) FROM User u", PostgreSqlDialect)
        assertTrue("PostgreSQL uses || for concat", sql.contains("u.name || ' ' || u.email"))
    }

    fun testConcatMySql() {
        val sql = convertWith("SELECT CONCAT(u.name, ' ', u.email) FROM User u", MySqlDialect)
        assertTrue("MySQL uses CONCAT function", sql.contains("CONCAT(u.name, ' ', u.email)"))
    }

    fun testConcatOracle() {
        val sql = convertWith("SELECT CONCAT(u.name, ' ', u.email) FROM User u", OracleDialect)
        assertTrue("Oracle uses || for concat", sql.contains("u.name || ' ' || u.email"))
    }

    fun testConcatSqlServer() {
        val sql = convertWith("SELECT CONCAT(u.name, ' ', u.email) FROM User u", SqlServerDialect)
        assertTrue("SQL Server uses CONCAT function", sql.contains("CONCAT(u.name, ' ', u.email)"))
    }

    fun testConcatH2() {
        val sql = convertWith("SELECT CONCAT(u.name, ' ', u.email) FROM User u", H2Dialect)
        assertTrue("H2 uses CONCAT function", sql.contains("CONCAT(u.name, ' ', u.email)"))
    }

    // ============ Boolean Literal Tests ============

    fun testBooleanLiteralPostgres() {
        val sql = convertWith("SELECT u FROM User u WHERE u.active = true", PostgreSqlDialect)
        assertTrue("PostgreSQL uses TRUE", sql.contains("= TRUE"))
    }

    fun testBooleanLiteralMySql() {
        val sql = convertWith("SELECT u FROM User u WHERE u.active = true", MySqlDialect)
        assertTrue("MySQL uses TRUE", sql.contains("= TRUE"))
    }

    fun testBooleanLiteralSqlServer() {
        val sql = convertWith("SELECT u FROM User u WHERE u.active = true", SqlServerDialect)
        assertTrue("SQL Server uses 1 for true", sql.contains("= 1"))
    }

    // ============ Current Date/Time Tests ============

    fun testCurrentDatePostgres() {
        val sql = convertWith("SELECT CURRENT_DATE FROM User u", PostgreSqlDialect)
        assertTrue("PostgreSQL uses CURRENT_DATE", sql.contains("CURRENT_DATE"))
    }

    fun testCurrentDateOracle() {
        val sql = convertWith("SELECT CURRENT_DATE FROM User u", OracleDialect)
        assertTrue("Oracle uses SYSDATE", sql.contains("SYSDATE"))
    }

    fun testCurrentDateSqlServer() {
        val sql = convertWith("SELECT CURRENT_DATE FROM User u", SqlServerDialect)
        assertTrue("SQL Server uses CAST(GETDATE() AS DATE)", sql.contains("CAST(GETDATE() AS DATE)"))
    }

    fun testCurrentTimestampPostgres() {
        val sql = convertWith("SELECT CURRENT_TIMESTAMP FROM User u", PostgreSqlDialect)
        assertTrue("PostgreSQL uses CURRENT_TIMESTAMP", sql.contains("CURRENT_TIMESTAMP"))
    }

    fun testCurrentTimestampOracle() {
        val sql = convertWith("SELECT CURRENT_TIMESTAMP FROM User u", OracleDialect)
        assertTrue("Oracle uses SYSTIMESTAMP", sql.contains("SYSTIMESTAMP"))
    }

    fun testCurrentTimestampSqlServer() {
        val sql = convertWith("SELECT CURRENT_TIMESTAMP FROM User u", SqlServerDialect)
        assertTrue("SQL Server uses GETDATE()", sql.contains("GETDATE()"))
    }

    // ============ SUBSTRING Function Tests ============

    fun testSubstringPostgres() {
        val sql = convertWith("SELECT SUBSTRING(u.name, 1, 5) FROM User u", PostgreSqlDialect)
        assertTrue("PostgreSQL uses SUBSTRING(x FROM y FOR z)", sql.contains("SUBSTRING(u.name FROM 1 FOR 5)"))
    }

    fun testSubstringMySql() {
        val sql = convertWith("SELECT SUBSTRING(u.name, 1, 5) FROM User u", MySqlDialect)
        assertTrue("MySQL uses SUBSTRING(x, y, z)", sql.contains("SUBSTRING(u.name, 1, 5)"))
    }

    fun testSubstringOracle() {
        val sql = convertWith("SELECT SUBSTRING(u.name, 1, 5) FROM User u", OracleDialect)
        assertTrue("Oracle uses SUBSTR(x, y, z)", sql.contains("SUBSTR(u.name, 1, 5)"))
    }

    fun testSubstringSqlServer() {
        val sql = convertWith("SELECT SUBSTRING(u.name, 1, 5) FROM User u", SqlServerDialect)
        assertTrue("SQL Server uses SUBSTRING(x, y, z)", sql.contains("SUBSTRING(u.name, 1, 5)"))
    }

    // ============ Complex Query All Dialects ============

    fun testComplexQueryAllDialects() {
        val jpql = """
            SELECT u FROM User u
            WHERE u.name LIKE :pattern
              AND u.active = true
            ORDER BY u.createdAt DESC
            FETCH FIRST 10 ROWS ONLY
        """.trimIndent()

        val postgresResult = convertWith(jpql, PostgreSqlDialect)
        val mysqlResult = convertWith(jpql, MySqlDialect)
        val oracleResult = convertWith(jpql, OracleDialect)
        val sqlServerResult = convertWith(jpql, SqlServerDialect)
        val h2Result = convertWith(jpql, H2Dialect)

        // All should have the common parts
        listOf(postgresResult, mysqlResult, oracleResult, sqlServerResult, h2Result).forEach { sql ->
            assertTrue("Should have SELECT u", sql.contains("SELECT u"))
            assertTrue("Should have FROM users u", sql.contains("FROM users u"))
            assertTrue("Should have LIKE :pattern", sql.contains("LIKE :pattern"))
            assertTrue("Should have ORDER BY", sql.contains("ORDER BY"))
            assertTrue("Should have DESC", sql.contains("DESC"))
        }

        // PostgreSQL/H2: LIMIT 10
        assertTrue(postgresResult.contains("LIMIT 10"))
        assertTrue(h2Result.contains("LIMIT 10"))

        // MySQL: LIMIT 10
        assertTrue(mysqlResult.contains("LIMIT 10"))

        // Oracle: FETCH FIRST 10 ROWS ONLY
        assertTrue(oracleResult.contains("FETCH FIRST 10 ROWS ONLY"))

        // SQL Server: OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY
        assertTrue(sqlServerResult.contains("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY"))

        // Boolean differences
        assertTrue(postgresResult.contains("= TRUE"))
        assertTrue(mysqlResult.contains("= TRUE"))
        assertTrue(sqlServerResult.contains("= 1"))  // SQL Server uses 1 for true
    }

    // ============ CAST Type Mapping Tests ============

    fun testCastToStringPostgres() {
        val sql = convertWith("SELECT CAST(u.id AS String) FROM User u", PostgreSqlDialect)
        assertTrue("Should cast to VARCHAR", sql.contains("CAST(u.id AS VARCHAR)"))
    }

    fun testCastToIntegerAllDialects() {
        val jpql = "SELECT CAST(u.name AS Integer) FROM User u"

        listOf(PostgreSqlDialect, MySqlDialect, OracleDialect, SqlServerDialect, H2Dialect).forEach { dialect ->
            val sql = convertWith(jpql, dialect)
            assertTrue("${dialect.name} should cast to INTEGER", sql.contains("CAST(u.name AS INTEGER)"))
        }
    }

    // ============ COALESCE and NULLIF Tests ============

    fun testCoalesceAllDialects() {
        val jpql = "SELECT COALESCE(u.name, 'Unknown') FROM User u"

        listOf(PostgreSqlDialect, MySqlDialect, OracleDialect, SqlServerDialect, H2Dialect).forEach { dialect ->
            val sql = convertWith(jpql, dialect)
            assertTrue("${dialect.name} should have COALESCE", sql.contains("COALESCE(u.name, 'Unknown')"))
        }
    }

    fun testNullifAllDialects() {
        val jpql = "SELECT NULLIF(u.status, 'UNKNOWN') FROM User u"

        listOf(PostgreSqlDialect, MySqlDialect, OracleDialect, SqlServerDialect, H2Dialect).forEach { dialect ->
            val sql = convertWith(jpql, dialect)
            assertTrue("${dialect.name} should have NULLIF", sql.contains("NULLIF(u.status, 'UNKNOWN')"))
        }
    }

    // ============ Complex Query with Multiple Dialect-Specific Features ============

    fun testQueryWithConcatAndSubstringAndLimit() {
        val jpql = """
            SELECT CONCAT(u.name, SUBSTRING(u.email, 1, 5)) FROM User u
            WHERE u.createdAt > CURRENT_DATE
            FETCH FIRST 20 ROWS ONLY
        """.trimIndent()

        // PostgreSQL: || concat, SUBSTRING FROM FOR, CURRENT_DATE, LIMIT
        val postgres = convertWith(jpql, PostgreSqlDialect)
        assertTrue(postgres.contains("||"))
        assertTrue(postgres.contains("SUBSTRING(u.email FROM 1 FOR 5)"))
        assertTrue(postgres.contains("CURRENT_DATE"))
        assertTrue(postgres.contains("LIMIT 20"))

        // MySQL: CONCAT(), SUBSTRING(,,), CURRENT_DATE, LIMIT
        val mysql = convertWith(jpql, MySqlDialect)
        assertTrue(mysql.contains("CONCAT("))
        assertTrue(mysql.contains("SUBSTRING(u.email, 1, 5)"))
        assertTrue(mysql.contains("LIMIT 20"))

        // Oracle: || concat, SUBSTR(), SYSDATE, FETCH FIRST
        val oracle = convertWith(jpql, OracleDialect)
        assertTrue(oracle.contains("||"))
        assertTrue(oracle.contains("SUBSTR(u.email, 1, 5)"))
        assertTrue(oracle.contains("SYSDATE"))
        assertTrue(oracle.contains("FETCH FIRST 20 ROWS ONLY"))

        // SQL Server: CONCAT(), SUBSTRING(,,), CAST(GETDATE() AS DATE), OFFSET FETCH
        val sqlServer = convertWith(jpql, SqlServerDialect)
        assertTrue(sqlServer.contains("CONCAT("))
        assertTrue(sqlServer.contains("SUBSTRING(u.email, 1, 5)"))
        assertTrue(sqlServer.contains("CAST(GETDATE() AS DATE)"))
        assertTrue(sqlServer.contains("OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY"))
    }

    // ============ TRIM Function Tests ============

    fun testTrimAllDialects() {
        val jpql = "SELECT TRIM(u.name) FROM User u"

        listOf(PostgreSqlDialect, MySqlDialect, OracleDialect, SqlServerDialect, H2Dialect).forEach { dialect ->
            val sql = convertWith(jpql, dialect)
            assertTrue("${dialect.name} should have TRIM", sql.contains("TRIM(u.name)"))
        }
    }

    // ============ Arithmetic Operators All Dialects ============

    fun testArithmeticAllDialects() {
        val jpql = "SELECT u.age + 5, u.age - 1, u.age * 2, u.age / 2 FROM User u"

        listOf(PostgreSqlDialect, MySqlDialect, OracleDialect, SqlServerDialect, H2Dialect).forEach { dialect ->
            val sql = convertWith(jpql, dialect)
            assertTrue("${dialect.name} should have addition", sql.contains("(u.age + 5)"))
            assertTrue("${dialect.name} should have subtraction", sql.contains("(u.age - 1)"))
            assertTrue("${dialect.name} should have multiplication", sql.contains("(u.age * 2)"))
            assertTrue("${dialect.name} should have division", sql.contains("(u.age / 2)"))
        }
    }
}
