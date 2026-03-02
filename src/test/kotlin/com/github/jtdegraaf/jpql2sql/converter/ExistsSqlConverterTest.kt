package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.MatchEntities
import com.github.jtdegraaf.jpql2sql.converter.entities.UserEntities

/**
 * SQL conversion tests for EXISTS and NOT EXISTS queries.
 */
class ExistsSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        UserEntities.addAll(myFixture)
        MatchEntities.addAll(myFixture)

        // Add Department for correlated queries
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "departments")
            public class Department {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "budget")
                private Double budget;
            }
        """.trimIndent())

        // Add Employee with department relationship
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "employees")
            public class Employee {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "salary")
                private Double salary;

                @ManyToOne
                @JoinColumn(name = "department_id")
                private Department department;
            }
        """.trimIndent())
    }

    private fun assertNoUnparsed(sql: String) {
        assertFalse("SQL should not contain UNPARSED fragments", sql.contains("UNPARSED"))
    }

    // ═══════════════════════════════════════════════════════
    //  Basic EXISTS
    // ═══════════════════════════════════════════════════════

    fun testBasicExists() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("Basic EXISTS: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    fun testBasicNotExists() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE NOT EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("Basic NOT EXISTS: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE NOT EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Correlated subqueries with EXISTS
    // ═══════════════════════════════════════════════════════

    fun testExistsWithCorrelatedSubquery() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            WHERE EXISTS (
                SELECT o FROM Order o
                WHERE o.id = u.id
            )
        """.trimIndent())

        println("EXISTS with correlated subquery: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT u FROM users u WHERE EXISTS (SELECT o FROM orders o WHERE o.id = u.id)",
            sql
        )
    }

    fun testExistsWithMultipleCorrelations() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (
                SELECT e FROM Employee e
                WHERE e.department.id = d.id AND e.salary > 50000
            )
        """.trimIndent())

        println("EXISTS with multiple correlations: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > 50000)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS combined with other conditions
    // ═══════════════════════════════════════════════════════

    fun testExistsWithAndCondition() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE d.name = 'Engineering'
            AND EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("EXISTS with AND: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE d.name = 'Engineering' AND EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    fun testExistsWithOrCondition() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE d.budget > 100000
            OR EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary > 100000)
        """.trimIndent())

        println("EXISTS with OR: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE d.budget > 100000 OR EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > 100000)",
            sql
        )
    }

    fun testExistsWithNotAndOther() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE d.name IS NOT NULL
            AND NOT EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("NOT EXISTS with other condition: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE d.name IS NOT NULL AND NOT EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Multiple EXISTS in same query
    // ═══════════════════════════════════════════════════════

    fun testMultipleExists() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary > 50000)
            AND EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary < 30000)
        """.trimIndent())

        println("Multiple EXISTS: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > 50000) AND EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary < 30000)",
            sql
        )
    }

    fun testExistsAndNotExists() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id)
            AND NOT EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary > 100000)
        """.trimIndent())

        println("EXISTS and NOT EXISTS: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id) AND NOT EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > 100000)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS with JOINs in subquery
    // ═══════════════════════════════════════════════════════

    fun testExistsWithJoinInSubquery() {
        val sql = convertWithPostgres("""
            SELECT m FROM Match m
            WHERE EXISTS (
                SELECT p FROM MatchParticipant p
                WHERE p.match.id = m.id
            )
        """.trimIndent())

        println("EXISTS with JOIN in subquery: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT m FROM matches m WHERE EXISTS (SELECT p FROM match_participants p WHERE p.match_id = m.id)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS with aggregates in subquery
    // ═══════════════════════════════════════════════════════

    fun testExistsWithCountInSubquery() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (
                SELECT e.department FROM Employee e
                WHERE e.department.id = d.id
                GROUP BY e.department
                HAVING COUNT(e) > 5
            )
        """.trimIndent())

        println("EXISTS with COUNT in subquery: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e.department_id FROM employees e WHERE e.department_id = d.id GROUP BY e.department_id HAVING COUNT(e) > 5)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS with parameters
    // ═══════════════════════════════════════════════════════

    fun testExistsWithNamedParameter() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (
                SELECT e FROM Employee e
                WHERE e.department.id = d.id AND e.salary > :minSalary
            )
        """.trimIndent())

        println("EXISTS with named parameter: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > :minSalary)",
            sql
        )
    }

    fun testExistsWithPositionalParameter() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (
                SELECT e FROM Employee e
                WHERE e.department.id = d.id AND e.name = ?1
            )
        """.trimIndent())

        println("EXISTS with positional parameter: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.name = ?1)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS in SELECT clause
    // ═══════════════════════════════════════════════════════

    fun testExistsInSelectClause() {
        val sql = convertWithPostgres("""
            SELECT d, EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id) FROM Department d
        """.trimIndent())

        println("EXISTS in SELECT: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d, EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id) FROM departments d",
            sql
        )
    }

    fun testExistsAsOnlySelectProjection() {
        val sql = convertWithPostgres("""
            SELECT EXISTS (SELECT e FROM Employee e WHERE e.department.id = :deptId) FROM Department d
        """.trimIndent())

        println("EXISTS as only projection: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT EXISTS (SELECT e FROM employees e WHERE e.department_id = :deptId) FROM departments d",
            sql
        )
    }

    fun testExistsWithAliasInSelect() {
        val sql = convertWithPostgres("""
            SELECT d.name, EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id) AS hasEmployees FROM Department d
        """.trimIndent())

        println("EXISTS with alias in SELECT: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d.name, EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id) AS hasEmployees FROM departments d",
            sql
        )
    }

    fun testMultipleExistsInSelect() {
        val sql = convertWithPostgres("""
            SELECT d.name,
                   EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary > 50000) AS hasHighEarners,
                   EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id AND e.salary < 30000) AS hasLowEarners
            FROM Department d
        """.trimIndent())

        println("Multiple EXISTS in SELECT: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d.name, EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary > 50000) AS hasHighEarners, EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND e.salary < 30000) AS hasLowEarners FROM departments d",
            sql
        )
    }

    fun testExistsInSelectWithCaseWhen() {
        val sql = convertWithPostgres("""
            SELECT d.name,
                   CASE WHEN EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id) THEN 'Yes' ELSE 'No' END AS hasStaff
            FROM Department d
        """.trimIndent())

        println("EXISTS in CASE WHEN: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d.name, CASE WHEN EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id) THEN 'Yes' ELSE 'No' END AS hasStaff FROM departments d",
            sql
        )
    }

    fun testNotExistsInSelect() {
        val sql = convertWithPostgres("""
            SELECT d.name, NOT EXISTS (SELECT e FROM Employee e WHERE e.department.id = d.id) AS isEmpty FROM Department d
        """.trimIndent())

        println("NOT EXISTS in SELECT: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d.name, NOT EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id) AS isEmpty FROM departments d",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  EXISTS with SELECT 1 pattern (common optimization)
    // ═══════════════════════════════════════════════════════

    fun testExistsWithSelectOne() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (SELECT 1 FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("EXISTS with SELECT 1: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT 1 FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    fun testNotExistsWithSelectOne() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE NOT EXISTS (SELECT 1 FROM Employee e WHERE e.department.id = d.id)
        """.trimIndent())

        println("NOT EXISTS with SELECT 1: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE e.department_id = d.id)",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Nested EXISTS
    // ═══════════════════════════════════════════════════════

    fun testNestedExists() {
        val sql = convertWithPostgres("""
            SELECT d FROM Department d
            WHERE EXISTS (
                SELECT e FROM Employee e
                WHERE e.department.id = d.id
                AND EXISTS (
                    SELECT o FROM Order o WHERE o.id = e.id
                )
            )
        """.trimIndent())

        println("Nested EXISTS: $sql")
        assertNoUnparsed(sql)
        assertEquals(
            "SELECT d FROM departments d WHERE EXISTS (SELECT e FROM employees e WHERE e.department_id = d.id AND EXISTS (SELECT o FROM orders o WHERE o.id = e.id))",
            sql
        )
    }
}
