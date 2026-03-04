package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.converter.entities.MatchEntities
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser

/**
 * Integration tests for implicit JOIN generation in @Query JPQL.
 * Tests the full flow: JPQL string -> parser -> transformer -> SQL converter -> SQL string
 */
class ImplicitJoinIntegrationTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        MatchEntities.addAll(myFixture)
        addUserWithDepartment()
    }

    private fun addUserWithDepartment() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;

                private String name;

                @ManyToOne
                @JoinColumn(name = "department_id")
                private Department department;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "departments")
            public class Department {
                @Id
                private Long id;

                private String name;

                @ManyToOne
                @JoinColumn(name = "company_id")
                private Company company;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "companies")
            public class Company {
                @Id
                private Long id;

                private String name;
            }
        """.trimIndent())
    }

    /**
     * Converts JPQL to SQL using the full pipeline including implicit join transformation.
     */
    private fun convertWithImplicitJoins(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        val transformer = ImplicitJoinTransformer(resolver)
        val transformedAst = transformer.transform(ast)
        return SqlConverter(PostgreSqlDialect, resolver).convert(transformedAst)
    }

    // ============ Single-level implicit joins ============

    fun testImplicitJoinInWhere() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id WHERE department_1.name = :deptName",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.department.name = :deptName")
        )
    }

    fun testImplicitJoinInSelect() {
        assertEquals(
            "SELECT department_1.name FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id",
            convertWithImplicitJoins("SELECT u.department.name FROM User u")
        )
    }

    fun testImplicitJoinInOrderBy() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id ORDER BY department_1.name ASC",
            convertWithImplicitJoins("SELECT u FROM User u ORDER BY u.department.name ASC")
        )
    }

    // ============ Multi-level implicit joins ============

    fun testTwoLevelImplicitJoin() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name = :companyName",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.department.company.name = :companyName")
        )
    }

    // ============ Match entity tests (FK optimization) ============

    fun testMatchParticipantsBotIdUsesFkOptimization() {
        assertEquals(
            "SELECT m FROM matches m LEFT JOIN match_participants participants_1 ON participants_1.match_id = m.id WHERE participants_1.bot_id = :botId",
            convertWithImplicitJoins("SELECT m FROM Match m WHERE m.participants.bot.id = :botId")
        )
    }

    fun testMatchParticipantsBotNameRequiresJoin() {
        assertEquals(
            "SELECT m FROM matches m LEFT JOIN match_participants participants_1 ON participants_1.match_id = m.id LEFT JOIN bots bot_2 ON participants_1.bot_id = bot_2.id WHERE bot_2.name = :botName",
            convertWithImplicitJoins("SELECT m FROM Match m WHERE m.participants.bot.name = :botName")
        )
    }

    // ============ Mixed explicit and implicit joins ============

    fun testExplicitJoinWithImplicitPath() {
        assertEquals(
            "SELECT u FROM users u INNER JOIN departments d ON u.department_id = d.id LEFT JOIN companies company_1 ON d.company_id = company_1.id WHERE company_1.name = :name",
            convertWithImplicitJoins("SELECT u FROM User u JOIN u.department d WHERE d.company.name = :name")
        )
    }

    // ============ No implicit join needed ============

    fun testNoImplicitJoinForSimplePath() {
        assertEquals(
            "SELECT u FROM users u WHERE u.name = :name",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.name = :name")
        )
    }

    fun testExplicitJoinNoImplicit() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments d ON u.department_id = d.id WHERE d.name = :name",
            convertWithImplicitJoins("SELECT u FROM User u LEFT JOIN u.department d WHERE d.name = :name")
        )
    }

    // ============ Multiple paths to same relationship ============

    fun testSameRelationshipMultiplePathsWithJoin() {
        // When accessing .name, we need a JOIN. When accessing .id on same relationship, reuse the join.
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id WHERE department_1.name = :name AND department_1.id = :id",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.department.name = :name AND u.department.id = :id")
        )
    }

    // ============ Complex queries ============

    fun testComplexQueryWithMultipleImplicitJoins() {
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name = :companyName AND department_1.name = :deptName ORDER BY department_1.name ASC",
            convertWithImplicitJoins("""
                SELECT u
                FROM User u
                WHERE u.department.company.name = :companyName
                  AND u.department.name = :deptName
                ORDER BY u.department.name ASC
            """.trimIndent())
        )
    }

    // ============ Accessing FK directly (no join needed for .id) ============

    fun testAccessingForeignKeyIdDirectly() {
        // Accessing .id on a @ManyToOne uses the FK column directly without a join
        assertEquals(
            "SELECT u FROM users u WHERE u.department_id = :deptId",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.department.id = :deptId")
        )
    }

    fun testAccessingNestedForeignKeyIdDirectly() {
        // u.department.company.id - needs JOIN for department, but company.id uses FK
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments department_1 ON u.department_id = department_1.id WHERE department_1.company_id = :companyId",
            convertWithImplicitJoins("SELECT u FROM User u WHERE u.department.company.id = :companyId")
        )
    }
}
