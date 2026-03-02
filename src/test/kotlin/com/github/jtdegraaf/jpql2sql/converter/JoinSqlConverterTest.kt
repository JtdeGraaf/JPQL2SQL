package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.MatchEntities

/**
 * SQL conversion tests for various JOIN types.
 */
class JoinSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        // Add Department entity for User joins
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
            }
        """.trimIndent())

        // Add User with department relationship
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @ManyToOne
                @JoinColumn(name = "department_id")
                private Department department;
            }
        """.trimIndent())

        // Add entities for OneToMany tests
        MatchEntities.addAll(myFixture)
    }

    // ═══════════════════════════════════════════════════════
    //  Explicit ON condition joins
    // ═══════════════════════════════════════════════════════

    fun testExplicitInnerJoinWithOn() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            INNER JOIN Department d ON u.department.id = d.id
            WHERE d.name = 'Engineering'
        """.trimIndent())

        println("Explicit INNER JOIN: $sql")
        assertEquals(
            "SELECT u FROM users u INNER JOIN departments d ON u.department_id = d.id WHERE d.name = 'Engineering'",
            sql
        )
    }

    fun testExplicitLeftJoinWithOn() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            LEFT JOIN Department d ON u.department.id = d.id
        """.trimIndent())

        println("Explicit LEFT JOIN: $sql")
        assertEquals(
            "SELECT u FROM users u LEFT JOIN departments d ON u.department_id = d.id",
            sql
        )
    }

    fun testExplicitJoinWithComplexCondition() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            JOIN Department d ON u.department.id = d.id AND d.name = :deptName
        """.trimIndent())

        println("Explicit JOIN with complex condition: $sql")
        assertEquals(
            "SELECT u FROM users u INNER JOIN departments d ON u.department_id = d.id AND d.name = :deptName",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Implicit join (path-based, no ON)
    // ═══════════════════════════════════════════════════════

    fun testImplicitJoinManyToOne() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            JOIN u.department d
            WHERE d.name = 'Engineering'
        """.trimIndent())

        println("Implicit ManyToOne JOIN: $sql")
        assertTrue("Should have INNER JOIN departments d", sql.contains("INNER JOIN departments d"))
        assertTrue("Should have ON condition with department_id", sql.contains("u.department_id = d.id"))
        assertTrue("Should have WHERE clause", sql.contains("WHERE d.name = 'Engineering'"))
    }

    fun testImplicitLeftJoin() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            LEFT JOIN u.department d
        """.trimIndent())

        println("Implicit LEFT JOIN: $sql")
        assertTrue("Should have LEFT JOIN departments d", sql.contains("LEFT JOIN departments d"))
        assertTrue("Should have ON condition", sql.contains("ON"))
    }

    // ═══════════════════════════════════════════════════════
    //  OneToMany joins
    // ═══════════════════════════════════════════════════════

    fun testOneToManyJoin() {
        val sql = convertWithPostgres("""
            SELECT m FROM Match m
            JOIN m.participants p
            WHERE p.id IS NOT NULL
        """.trimIndent())

        println("OneToMany JOIN: $sql")
        assertTrue("Should join match_participants table", sql.contains("JOIN match_participants p"))
        assertTrue("Should have ON condition linking back to match", sql.contains("p.match_id = m.id"))
    }

    fun testMultipleOneToManyJoins() {
        val sql = convertWithPostgres("""
            SELECT m FROM Match m
            JOIN m.participants p1
            JOIN m.participants p2
            WHERE p1.id != p2.id
        """.trimIndent())

        println("Multiple OneToMany JOINs: $sql")
        assertTrue("Should have first join to match_participants p1", sql.contains("JOIN match_participants p1"))
        assertTrue("Should have second join to match_participants p2", sql.contains("JOIN match_participants p2"))
        assertTrue("Should have p1 ON condition", sql.contains("p1.match_id = m.id"))
        assertTrue("Should have p2 ON condition", sql.contains("p2.match_id = m.id"))
    }

    // ═══════════════════════════════════════════════════════
    //  ManyToOne path navigation
    // ═══════════════════════════════════════════════════════

    fun testManyToOneIdAccess() {
        val sql = convertWithPostgres("""
            SELECT p FROM MatchParticipant p
            WHERE p.bot.id = :botId
        """.trimIndent())

        println("ManyToOne .id access: $sql")
        assertEquals(
            "SELECT p FROM match_participants p WHERE p.bot_id = :botId",
            sql
        )
    }

    fun testManyToOneDirectAccess() {
        val sql = convertWithPostgres("""
            SELECT p FROM MatchParticipant p
            WHERE p.bot = :bot
        """.trimIndent())

        println("ManyToOne direct access: $sql")
        assertEquals(
            "SELECT p FROM match_participants p WHERE p.bot_id = :bot",
            sql
        )
    }

    fun testManyToOneMatchId() {
        val sql = convertWithPostgres("""
            SELECT p FROM MatchParticipant p
            WHERE p.match.id = :matchId
        """.trimIndent())

        println("ManyToOne match.id access: $sql")
        assertEquals(
            "SELECT p FROM match_participants p WHERE p.match_id = :matchId",
            sql
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Combined scenarios
    // ═══════════════════════════════════════════════════════

    fun testJoinWithManyToOneNavigation() {
        val sql = convertWithPostgres("""
            SELECT m FROM Match m
            JOIN m.participants p
            WHERE p.bot.id = :botId
        """.trimIndent())

        println("JOIN with ManyToOne navigation: $sql")
        assertTrue("Should join match_participants", sql.contains("JOIN match_participants p"))
        assertTrue("Should resolve p.bot.id to p.bot_id", sql.contains("p.bot_id = :botId"))
    }

    fun testMultipleJoinsWithDifferentTypes() {
        val sql = convertWithPostgres("""
            SELECT u FROM User u
            JOIN u.department d
            LEFT JOIN Department d2 ON d2.id = d.id
        """.trimIndent())

        println("Multiple joins with different types: $sql")
        assertTrue("Should have INNER JOIN for implicit join", sql.contains("INNER JOIN departments d"))
        assertTrue("Should have LEFT JOIN for explicit join", sql.contains("LEFT JOIN departments d2 ON d2.id = d.id"))
    }

    // ═══════════════════════════════════════════════════════
    //  Comma-separated (implicit cross join) syntax
    // ═══════════════════════════════════════════════════════

    fun testCommaSeparatedTables() {
        val sql = convertWithPostgres("""
            SELECT u, d FROM User u, Department d
            WHERE u.department.id = d.id
        """.trimIndent())

        println("Comma-separated tables: $sql")
        assertEquals(
            "SELECT u, d FROM users u, departments d WHERE u.department_id = d.id",
            sql
        )
    }

    fun testCommaSeparatedSameTable() {
        val sql = convertWithPostgres("""
            SELECT p1, p2 FROM MatchParticipant p1, MatchParticipant p2
            WHERE p1.match.id = p2.match.id AND p1.id != p2.id
        """.trimIndent())

        println("Comma-separated same table: $sql")
        assertEquals(
            "SELECT p1, p2 FROM match_participants p1, match_participants p2 WHERE p1.match_id = p2.match_id AND p1.id != p2.id",
            sql
        )
    }

    fun testCommaSeparatedThreeTables() {
        val sql = convertWithPostgres("""
            SELECT m, p, b FROM Match m, MatchParticipant p, Bot b
            WHERE p.match.id = m.id AND p.bot.id = b.id
        """.trimIndent())

        println("Three comma-separated tables: $sql")
        assertEquals(
            "SELECT m, p, b FROM matches m, match_participants p, bots b WHERE p.match_id = m.id AND p.bot_id = b.id",
            sql
        )
    }
}
