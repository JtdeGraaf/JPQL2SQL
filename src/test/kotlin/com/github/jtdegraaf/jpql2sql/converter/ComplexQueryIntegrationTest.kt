package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryAstBuilder
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryParser

/**
 * Complex integration tests that exercise multiple JPQL features simultaneously.
 * Tests implicit joins, aggregates, GROUP BY, HAVING, subqueries, CASE, functions,
 * and various other complex query patterns.
 */
class ComplexQueryIntegrationTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        addComplexEntities()
    }

    private fun addComplexEntities() {
        // Company entity (root of the hierarchy)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "companies")
            public class Company {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @ManyToOne
                @JoinColumn(name = "ceo_id")
                private User ceo;
            }
        """.trimIndent())

        // Department entity
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

                @ManyToOne
                @JoinColumn(name = "company_id")
                private Company company;
            }
        """.trimIndent())

        // User entity
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

                @Column(name = "created_at")
                private LocalDateTime createdAt;

                @ManyToOne
                @JoinColumn(name = "department_id")
                private Department department;
            }
        """.trimIndent())

        // Order entity
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.math.BigDecimal;

            @Entity
            @Table(name = "orders")
            public class Order {
                @Id
                private Long id;

                @Column(name = "status")
                private String status;

                @Column(name = "total")
                private BigDecimal total;

                @ManyToOne
                @JoinColumn(name = "user_id")
                private User user;
            }
        """.trimIndent())

        // UserSuspension entity
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "user_suspensions")
            public class UserSuspension {
                @Id
                private Long id;

                @Column(name = "active")
                private Boolean active;

                @ManyToOne
                @JoinColumn(name = "user_id")
                private User user;
            }
        """.trimIndent())

        // @IdClass composite key entity
        myFixture.addClass("""
            package com.example;

            import java.io.Serializable;

            public class OrderItemId implements Serializable {
                private Long orderId;
                private Long productId;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "order_items")
            @IdClass(OrderItemId.class)
            public class OrderItem {
                @Id
                @Column(name = "order_id")
                private Long orderId;

                @Id
                @Column(name = "product_id")
                private Long productId;

                @Column(name = "quantity")
                private Integer quantity;

                @Column(name = "price")
                private java.math.BigDecimal price;
            }
        """.trimIndent())

        // @Subselect Hibernate entity
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import org.hibernate.annotations.Subselect;

            @Entity
            @Subselect("SELECT id, name, email, status FROM users WHERE status = 'ACTIVE'")
            public class ActiveUser {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "email")
                private String email;

                @Column(name = "status")
                private String status;
            }
        """.trimIndent())
    }

    /**
     * Converts JPQL to SQL using the full pipeline including implicit join transformation.
     */
    private fun convert(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        val transformer = ImplicitJoinTransformer(resolver)
        val transformedAst = transformer.transform(ast)
        return SqlConverter(PostgreSqlDialect, resolver).convert(transformedAst)
    }

    private fun convertDerivedQuery(methodName: String, entityName: String): String {
        val parser = DerivedQueryParser()
        val components = parser.parse(methodName, entityName)!!
        val resolver = EntityResolver(project)
        val ast = DerivedQueryAstBuilder(resolver).build(components)
        return SqlConverter(PostgreSqlDialect, resolver).convert(ast)
    }

    // ============ Test Case 1: Kitchen Sink Query ============

    fun testKitchenSinkQuery() {
        assertEquals(
            "SELECT department_1.name, COUNT(u), SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END), COALESCE(company_2.name, 'Unknown') FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE u.created_at > :startDate AND department_1.id IN (SELECT d.id FROM departments d LEFT JOIN companies company_1 ON d.company_id = company_1.id WHERE company_1.name = :companyName) GROUP BY department_1.name, company_2.name HAVING COUNT(u) > 5 ORDER BY COUNT(u) DESC, department_1.name ASC NULLS LAST",
            convert("""
                SELECT
                    u.department.name,
                    COUNT(u),
                    SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END),
                    COALESCE(u.department.company.name, 'Unknown')
                FROM User u
                WHERE u.createdAt > :startDate
                  AND u.department.id IN (
                      SELECT d.id FROM Department d WHERE d.company.name = :companyName
                  )
                GROUP BY u.department.name, u.department.company.name
                HAVING COUNT(u) > 5
                ORDER BY COUNT(u) DESC, u.department.name ASC NULLS LAST
            """.trimIndent())
        )
    }

    // ============ Test Case 2: Multi-Level Implicit Joins with Aggregates ============

    fun testThreeLevelImplicitJoinWithGroupBy() {
        assertEquals(
            "SELECT company_2.name, AVG(u.age), MIN(u.created_at), MAX(u.created_at) FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name IS NOT NULL GROUP BY company_2.name HAVING AVG(u.age) > 25",
            convert("""
                SELECT
                    u.department.company.name,
                    AVG(u.age),
                    MIN(u.createdAt),
                    MAX(u.createdAt)
                FROM User u
                WHERE u.department.company.name IS NOT NULL
                GROUP BY u.department.company.name
                HAVING AVG(u.age) > 25
            """.trimIndent())
        )
    }

    // ============ Test Case 3: Correlated Subquery with EXISTS ============

    fun testCorrelatedExistsWithImplicitJoins() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id AND o.status = 'COMPLETED' AND o.total > :minTotal) AND company_2.name = :companyName",
            convert("""
                SELECT u FROM User u
                WHERE EXISTS (
                    SELECT 1 FROM Order o
                    WHERE o.user.id = u.id
                      AND o.status = 'COMPLETED'
                      AND o.total > :minTotal
                )
                AND u.department.company.name = :companyName
            """.trimIndent())
        )
    }

    // ============ Test Case 4: Complex CASE with Functions ============

    fun testComplexCaseWithFunctions() {
        assertEquals(
            "SELECT u.name, CASE WHEN LENGTH(u.email) > 50 THEN SUBSTRING(u.email FROM 1 FOR 47) || '...' WHEN u.email IS NULL THEN 'user_' || CAST(u.id AS VARCHAR) ELSE LOWER(u.email) END AS displayEmail, COALESCE(TRIM(u.name), 'Anonymous') FROM users u ORDER BY UPPER(u.name) ASC",
            convert("""
                SELECT
                    u.name,
                    CASE
                        WHEN LENGTH(u.email) > 50 THEN CONCAT(SUBSTRING(u.email, 1, 47), '...')
                        WHEN u.email IS NULL THEN CONCAT('user_', CAST(u.id AS VARCHAR))
                        ELSE LOWER(u.email)
                    END AS displayEmail,
                    COALESCE(TRIM(u.name), 'Anonymous')
                FROM User u
                ORDER BY UPPER(u.name) ASC
            """.trimIndent())
        )
    }

    // ============ Test Case 5: Multiple Subqueries with IN and NOT EXISTS ============

    fun testMultipleSubqueries() {
        assertEquals(
            "SELECT u FROM users u WHERE u.department_id IN (SELECT d.id FROM departments d WHERE d.company_id = :companyId) AND NOT EXISTS (SELECT 1 FROM user_suspensions s WHERE s.user_id = u.id AND s.active = TRUE) AND u.status IN ('ACTIVE', 'PENDING')",
            convert("""
                SELECT u FROM User u
                WHERE u.department.id IN (
                    SELECT d.id FROM Department d
                    WHERE d.company.id = :companyId
                )
                AND NOT EXISTS (
                    SELECT 1 FROM UserSuspension s
                    WHERE s.user.id = u.id AND s.active = true
                )
                AND u.status IN ('ACTIVE', 'PENDING')
            """.trimIndent())
        )
    }

    // ============ Test Case 6: Derived Query with Deep Nesting ============

    fun testDerivedQueryDeepNesting() {
        assertEquals(
            "SELECT e FROM users e LEFT JOIN departments department_1 ON e.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name = :department_company_name AND e.status NOT IN :status ORDER BY department_1.name ASC, e.created_at DESC",
            convertDerivedQuery(
                "findByDepartment_Company_NameAndStatusNotInOrderByDepartment_NameAscCreatedAtDesc",
                "User"
            )
        )
    }

    // ============ Test Case 7: FK Optimization at Different Nesting Levels ============

    fun testFkOptimizationLevel1() {
        // u.department.id -> u.department_id (no join needed for FK)
        assertEquals(
            "SELECT u FROM users u WHERE u.department_id = :deptId",
            convert("SELECT u FROM User u WHERE u.department.id = :deptId")
        )
    }

    fun testFkOptimizationLevel2() {
        // u.department.company.id -> JOIN department, then use department.company_id (1 join)
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id WHERE department_1.company_id = :companyId",
            convert("SELECT u FROM User u WHERE u.department.company.id = :companyId")
        )
    }

    fun testNoFkOptimizationWhenAccessingNonPkField() {
        // u.department.company.name -> 2 JOINs needed (department and company)
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name = :name",
            convert("SELECT u FROM User u WHERE u.department.company.name = :name")
        )
    }

    // ============ Test Case 8: Arithmetic Expressions ============

    fun testArithmeticAddition() {
        assertEquals(
            "SELECT (u.age + 5) FROM users u",
            convert("SELECT u.age + 5 FROM User u")
        )
    }

    fun testArithmeticSubtraction() {
        assertEquals(
            "SELECT (u.age - 1) FROM users u WHERE (u.age - 1) > 0",
            convert("SELECT u.age - 1 FROM User u WHERE u.age - 1 > 0")
        )
    }

    fun testArithmeticMultiplication() {
        assertEquals(
            "SELECT (u.age * 2) FROM users u",
            convert("SELECT u.age * 2 FROM User u")
        )
    }

    fun testArithmeticDivision() {
        assertEquals(
            "SELECT (u.age / 2) FROM users u",
            convert("SELECT u.age / 2 FROM User u")
        )
    }

    fun testComplexArithmeticExpression() {
        assertEquals(
            "SELECT ((u.age + 5) * 2) FROM users u WHERE (u.age / 2) > 10",
            convert("SELECT (u.age + 5) * 2 FROM User u WHERE u.age / 2 > 10")
        )
    }

    // ============ Test Case 9: Mixed Complex Query ============

    fun testMixedComplexQuery() {
        assertEquals(
            "SELECT DISTINCT d.name, COUNT(u) AS cnt FROM users u LEFT JOIN departments d ON u.department_id = d.id LEFT JOIN companies company_1 ON d.company_id = company_1.id WHERE u.status = 'ACTIVE' AND u.age >= 18 AND u.age <= 65 OR company_1.name LIKE :pattern GROUP BY d.name ORDER BY cnt DESC",
            convert("""
                SELECT DISTINCT u.department.name, COUNT(u) as cnt
                FROM User u
                LEFT JOIN u.department d
                WHERE u.status = 'ACTIVE'
                  AND (u.age >= 18 AND u.age <= 65)
                  OR u.department.company.name LIKE :pattern
                GROUP BY u.department.name
                ORDER BY cnt DESC
            """.trimIndent())
        )
    }

    // ============ Test Case 10: Subquery in SELECT Clause ============

    fun testSubqueryInSelect() {
        assertEquals(
            "SELECT u.name, (SELECT COUNT(o) FROM orders o WHERE o.user_id = u.id) AS orderCount FROM users u",
            convert("""
                SELECT u.name, (
                    SELECT COUNT(o) FROM Order o WHERE o.user.id = u.id
                ) AS orderCount
                FROM User u
            """.trimIndent())
        )
    }

    // ============ Test Case 11: BETWEEN with Dates ============

    fun testBetweenWithDates() {
        assertEquals(
            "SELECT u FROM users u WHERE u.created_at BETWEEN :startDate AND :endDate ORDER BY u.created_at DESC",
            convert("""
                SELECT u FROM User u
                WHERE u.createdAt BETWEEN :startDate AND :endDate
                ORDER BY u.createdAt DESC
            """.trimIndent())
        )
    }

    // ============ Test Case 12: NOT LIKE with Multiple Conditions ============

    fun testNotLikeWithMultipleConditions() {
        assertEquals(
            "SELECT u FROM users u WHERE u.email NOT LIKE '%test%' AND u.name NOT LIKE '%admin%' AND u.status = 'ACTIVE'",
            convert("""
                SELECT u FROM User u
                WHERE u.email NOT LIKE '%test%'
                  AND u.name NOT LIKE '%admin%'
                  AND u.status = 'ACTIVE'
            """.trimIndent())
        )
    }

    // ============ Test Case 13: String Concatenation Operator || ============

    fun testConcatOperator() {
        assertEquals(
            "SELECT u.name || ' ' || u.email FROM users u",
            convert("SELECT u.name || ' ' || u.email FROM User u")
        )
    }

    fun testConcatOperatorInWhere() {
        assertEquals(
            "SELECT u FROM users u WHERE u.name || '@' || u.email = :combined",
            convert("SELECT u FROM User u WHERE u.name || '@' || u.email = :combined")
        )
    }

    // ============ Test Case 14: EXTRACT Function ============

    fun testExtractYear() {
        assertEquals(
            "SELECT EXTRACT(YEAR FROM u.created_at) FROM users u",
            convert("SELECT EXTRACT(YEAR FROM u.createdAt) FROM User u")
        )
    }

    fun testExtractMonth() {
        assertEquals(
            "SELECT EXTRACT(MONTH FROM u.created_at) FROM users u",
            convert("SELECT EXTRACT(MONTH FROM u.createdAt) FROM User u")
        )
    }

    fun testExtractInWhere() {
        assertEquals(
            "SELECT u FROM users u WHERE EXTRACT(YEAR FROM u.created_at) = 2024",
            convert("SELECT u FROM User u WHERE EXTRACT(YEAR FROM u.createdAt) = 2024")
        )
    }

    // ============ Test Case 15: Enhanced TRIM Syntax ============

    fun testTrimLeading() {
        assertEquals(
            "SELECT TRIM(LEADING '0' FROM u.status) FROM users u",
            convert("SELECT TRIM(LEADING '0' FROM u.status) FROM User u")
        )
    }

    fun testTrimTrailing() {
        assertEquals(
            "SELECT TRIM(TRAILING ' ' FROM u.name) FROM users u",
            convert("SELECT TRIM(TRAILING ' ' FROM u.name) FROM User u")
        )
    }

    fun testTrimBothWithChar() {
        assertEquals(
            "SELECT TRIM(BOTH 'x' FROM u.name) FROM users u",
            convert("SELECT TRIM(BOTH 'x' FROM u.name) FROM User u")
        )
    }

    fun testTrimBothWithoutChar() {
        // TRIM(BOTH FROM x) simplifies to TRIM(x) as BOTH is the default
        assertEquals(
            "SELECT TRIM(u.name) FROM users u",
            convert("SELECT TRIM(BOTH FROM u.name) FROM User u")
        )
    }

    fun testTrimSimple() {
        assertEquals(
            "SELECT TRIM(u.name) FROM users u",
            convert("SELECT TRIM(u.name) FROM User u")
        )
    }

    // ============ Test Case 16: FULL OUTER JOIN and CROSS JOIN ============

    fun testFullOuterJoin() {
        assertEquals(
            "SELECT u, d.name FROM users u FULL OUTER JOIN departments d ON u.department_id = d.id",
            convert("SELECT u, d.name FROM User u FULL OUTER JOIN u.department d")
        )
    }

    fun testFullJoinWithoutOuter() {
        assertEquals(
            "SELECT u, d.name FROM users u FULL OUTER JOIN departments d ON u.department_id = d.id",
            convert("SELECT u, d.name FROM User u FULL JOIN u.department d")
        )
    }

    fun testCrossJoin() {
        assertEquals(
            "SELECT u, d FROM users u CROSS JOIN departments d",
            convert("SELECT u, d FROM User u CROSS JOIN Department d")
        )
    }

    // ============ Test Case 17: @IdClass Composite Key ============

    fun testIdClassCompositeKey() {
        assertEquals(
            "SELECT oi FROM order_items oi WHERE oi.order_id = :orderId AND oi.product_id = :productId",
            convert("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId AND oi.productId = :productId")
        )
    }

    fun testIdClassWithAggregates() {
        assertEquals(
            "SELECT oi.order_id, SUM(oi.quantity) FROM order_items oi GROUP BY oi.order_id",
            convert("SELECT oi.orderId, SUM(oi.quantity) FROM OrderItem oi GROUP BY oi.orderId")
        )
    }

    // ============ Test Case 18: @Subselect Hibernate Annotation ============

    fun testSubselectEntity() {
        assertEquals(
            "SELECT au FROM (SELECT id, name, email, status FROM users WHERE status = 'ACTIVE') au",
            convert("SELECT au FROM ActiveUser au")
        )
    }

    fun testSubselectWithWhere() {
        assertEquals(
            "SELECT au FROM (SELECT id, name, email, status FROM users WHERE status = 'ACTIVE') au WHERE au.email LIKE :pattern",
            convert("SELECT au FROM ActiveUser au WHERE au.email LIKE :pattern")
        )
    }
}
