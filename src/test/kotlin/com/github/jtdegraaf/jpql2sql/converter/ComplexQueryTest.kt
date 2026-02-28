package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end tests for complex, real-world JPQL queries.
 *
 * Each test parses a multi-clause JPQL string and converts it to native SQL,
 * verifying the full pipeline: lexer → parser → converter.
 */
class ComplexQueryTest {

    private fun convert(jpql: String, resolver: EntityResolver = MockEntityResolver()): String {
        val ast = JpqlParser(jpql).parse()
        return SqlConverter(PostgreSqlDialect, resolver).convert(ast)
    }

    // ═══════════════════════════════════════════════════════
    //  Multi-join queries
    // ═══════════════════════════════════════════════════════

    @Test
    fun testMultipleJoinsWithWhere() {
        val sql = convert("""
            SELECT o FROM Order o
            INNER JOIN o.customer c
            LEFT JOIN o.items i
            WHERE c.active = true AND i.quantity > 0
        """.trimIndent())

        assertTrue(sql.contains("INNER JOIN"))
        assertTrue(sql.contains("LEFT JOIN"))
        assertTrue(sql.contains("c.active = TRUE"))
        assertTrue(sql.contains("i.quantity > 0"))
    }

    @Test
    fun testThreeWayJoin() {
        val sql = convert("""
            SELECT p FROM Product p
            INNER JOIN p.category c
            INNER JOIN p.supplier s
            LEFT JOIN p.reviews r
            WHERE c.name = :category AND s.country = :country
            ORDER BY p.name ASC
        """.trimIndent())

        assertTrue(sql.contains("FROM products p"))
        assertEquals(3, sql.split("JOIN").size - 1)
        assertTrue(sql.contains("ORDER BY p.name ASC"))
    }

    // ═══════════════════════════════════════════════════════
    //  GROUP BY + HAVING + ORDER BY
    // ═══════════════════════════════════════════════════════

    @Test
    fun testGroupByHavingOrderBy() {
        val sql = convert("""
            SELECT u.department, COUNT(u.id), AVG(u.salary)
            FROM User u
            WHERE u.active = true
            GROUP BY u.department
            HAVING COUNT(u.id) > 5
            ORDER BY u.department ASC
        """.trimIndent())

        assertTrue(sql.contains("SELECT u.department, COUNT(u.id), AVG(u.salary)"))
        assertTrue(sql.contains("GROUP BY u.department"))
        assertTrue(sql.contains("HAVING COUNT(u.id) > 5"))
        assertTrue(sql.contains("ORDER BY u.department ASC"))
    }

    @Test
    fun testMultipleGroupByFields() {
        val sql = convert("""
            SELECT u.department, u.role, COUNT(u.id)
            FROM User u
            GROUP BY u.department, u.role
            HAVING COUNT(u.id) >= 3
        """.trimIndent())

        assertTrue(sql.contains("GROUP BY u.department, u.role"))
        assertTrue(sql.contains("HAVING COUNT(u.id) >= 3"))
    }

    // ═══════════════════════════════════════════════════════
    //  Nested boolean logic
    // ═══════════════════════════════════════════════════════

    @Test
    fun testNestedAndOrConditions() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE (u.role = 'ADMIN' OR u.role = 'MODERATOR')
            AND u.active = true
            AND (u.department = :dept OR u.department IS NULL)
        """.trimIndent())

        assertTrue(sql.contains("FROM users u"))
        assertTrue(sql.contains("u.role = 'ADMIN'"))
        assertTrue(sql.contains("u.role = 'MODERATOR'"))
        assertTrue(sql.contains("u.active = TRUE"))
        assertTrue(sql.contains("u.department IS NULL"))
    }

    @Test
    fun testNotWithParentheses() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE NOT (u.status = 'BANNED' OR u.status = 'SUSPENDED')
        """.trimIndent())

        assertTrue(sql.contains("NOT"))
        assertTrue(sql.contains("u.status = 'BANNED'"))
        assertTrue(sql.contains("u.status = 'SUSPENDED'"))
    }

    // ═══════════════════════════════════════════════════════
    //  Subqueries
    // ═══════════════════════════════════════════════════════

    @Test
    fun testWhereInSubquery() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.id IN (SELECT o.userId FROM Order o WHERE o.total > 100)
        """.trimIndent())

        assertTrue(sql.contains("u.id IN"))
        assertTrue(sql.contains("SELECT o.user_id FROM orders o"))
        assertTrue(sql.contains("o.total > 100"))
    }

    @Test
    fun testExistsStyleSubquery() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.orderCount > (SELECT AVG(u2.orderCount) FROM User u2)
        """.trimIndent())

        assertTrue(sql.contains("u.order_count >"))
        assertTrue(sql.contains("SELECT AVG(u2.order_count) FROM users u2"))
    }

    // ═══════════════════════════════════════════════════════
    //  CASE expressions
    // ═══════════════════════════════════════════════════════

    @Test
    fun testCaseInSelect() {
        val sql = convert("""
            SELECT u.name,
                   CASE WHEN u.role = 'ADMIN' THEN 'Administrator'
                        WHEN u.role = 'USER' THEN 'Regular User'
                        ELSE 'Unknown'
                   END
            FROM User u
        """.trimIndent())

        assertTrue(sql.contains("CASE"))
        assertTrue(sql.contains("WHEN u.role = 'ADMIN' THEN 'Administrator'"))
        assertTrue(sql.contains("WHEN u.role = 'USER' THEN 'Regular User'"))
        assertTrue(sql.contains("ELSE 'Unknown'"))
        assertTrue(sql.contains("END"))
    }

    @Test
    fun testCaseInOrderBy() {
        val sql = convert("""
            SELECT u FROM User u
            ORDER BY CASE WHEN u.role = 'ADMIN' THEN 1 ELSE 2 END ASC
        """.trimIndent())

        assertTrue(sql.contains("ORDER BY CASE"))
        assertTrue(sql.contains("END ASC"))
    }

    // ═══════════════════════════════════════════════════════
    //  Function calls in various positions
    // ═══════════════════════════════════════════════════════

    @Test
    fun testFunctionInWhere() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE LOWER(u.email) = :email
        """.trimIndent())

        assertTrue(sql.contains("LOWER(u.email)"))
    }

    @Test
    fun testCoalesceInSelect() {
        val sql = convert("""
            SELECT COALESCE(u.nickname, u.name) FROM User u
        """.trimIndent())

        assertTrue(sql.contains("COALESCE(u.nickname, u.name)"))
    }

    @Test
    fun testConcatInSelect() {
        val sql = convert("""
            SELECT CONCAT(u.firstName, ' ', u.lastName) FROM User u
        """.trimIndent())

        // PostgreSQL uses || for concat
        assertTrue(sql.contains("u.first_name || ' ' || u.last_name"))
    }

    @Test
    fun testNestedFunctionCalls() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE UPPER(TRIM(u.name)) = :name
        """.trimIndent())

        assertTrue(sql.contains("UPPER(TRIM(u.name))"))
    }

    // ═══════════════════════════════════════════════════════
    //  Aggregate combinations
    // ═══════════════════════════════════════════════════════

    @Test
    fun testMultipleAggregatesInSelect() {
        val sql = convert("""
            SELECT COUNT(o.id), SUM(o.total), AVG(o.total), MIN(o.total), MAX(o.total)
            FROM Order o
        """.trimIndent())

        assertTrue(sql.contains("COUNT(o.id)"))
        assertTrue(sql.contains("SUM(o.total)"))
        assertTrue(sql.contains("AVG(o.total)"))
        assertTrue(sql.contains("MIN(o.total)"))
        assertTrue(sql.contains("MAX(o.total)"))
    }

    @Test
    fun testCountDistinctWithGroupBy() {
        val sql = convert("""
            SELECT u.department, COUNT(DISTINCT u.role)
            FROM User u
            GROUP BY u.department
        """.trimIndent())

        assertTrue(sql.contains("COUNT(DISTINCT u.role)"))
        assertTrue(sql.contains("GROUP BY u.department"))
    }

    // ═══════════════════════════════════════════════════════
    //  Combined IN / BETWEEN / LIKE / IS NULL
    // ═══════════════════════════════════════════════════════

    @Test
    fun testMultiplePredicateTypes() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.status IN ('ACTIVE', 'PENDING')
            AND u.age BETWEEN 18 AND 65
            AND u.name LIKE '%john%'
            AND u.deletedAt IS NULL
        """.trimIndent())

        assertTrue(sql.contains("IN ('ACTIVE', 'PENDING')"))
        assertTrue(sql.contains("BETWEEN 18 AND 65"))
        assertTrue(sql.contains("LIKE '%john%'"))
        assertTrue(sql.contains("u.deleted_at IS NULL"))
    }

    @Test
    fun testNotInAndNotLike() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.role NOT IN ('BANNED', 'SUSPENDED')
            AND u.email NOT LIKE '%@test.com'
        """.trimIndent())

        assertTrue(sql.contains("NOT IN ('BANNED', 'SUSPENDED')"))
        assertTrue(sql.contains("NOT LIKE '%@test.com'"))
    }

    // ═══════════════════════════════════════════════════════
    //  Constructor expressions
    // ═══════════════════════════════════════════════════════

    @Test
    fun testConstructorWithMultipleFields() {
        val sql = convert("""
            SELECT NEW com.example.dto.UserSummary(u.id, u.name, u.email, COUNT(o.id))
            FROM User u
            LEFT JOIN u.orders o
            GROUP BY u.id, u.name, u.email
        """.trimIndent())

        assertTrue(sql.contains("u.id"))
        assertTrue(sql.contains("u.name"))
        assertTrue(sql.contains("u.email"))
        assertTrue(sql.contains("COUNT(o.id)"))
        assertTrue(sql.contains("GROUP BY u.id, u.name, u.email"))
    }

    // ═══════════════════════════════════════════════════════
    //  ORDER BY with NULLS FIRST/LAST
    // ═══════════════════════════════════════════════════════

    @Test
    fun testOrderByMultipleWithNulls() {
        val sql = convert("""
            SELECT u FROM User u
            ORDER BY u.lastName ASC NULLS LAST, u.firstName ASC NULLS FIRST
        """.trimIndent())

        assertTrue(sql.contains("u.last_name ASC NULLS LAST"))
        assertTrue(sql.contains("u.first_name ASC NULLS FIRST"))
    }

    @Test
    fun testOrderByMixedDirectionsAndNulls() {
        val sql = convert("""
            SELECT u FROM User u
            ORDER BY u.createdAt DESC NULLS LAST, u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("u.created_at DESC NULLS LAST"))
        assertTrue(sql.contains("u.name ASC"))
    }

    // ═══════════════════════════════════════════════════════
    //  DISTINCT + JOIN + WHERE + ORDER BY
    // ═══════════════════════════════════════════════════════

    @Test
    fun testDistinctWithJoinAndOrderBy() {
        val sql = convert("""
            SELECT DISTINCT u.name
            FROM User u
            LEFT JOIN u.orders o
            WHERE o.total > 50
            ORDER BY u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("SELECT DISTINCT u.name"))
        assertTrue(sql.contains("LEFT JOIN"))
        assertTrue(sql.contains("o.total > 50"))
        assertTrue(sql.contains("ORDER BY u.name ASC"))
    }

    // ═══════════════════════════════════════════════════════
    //  Parameter-heavy queries
    // ═══════════════════════════════════════════════════════

    @Test
    fun testMixedNamedParameters() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.department = :dept
            AND u.role = :role
            AND u.createdAt > :startDate
            AND u.createdAt < :endDate
            AND u.active = :active
        """.trimIndent())

        assertTrue(sql.contains(":dept"))
        assertTrue(sql.contains(":role"))
        assertTrue(sql.contains(":startDate"))
        assertTrue(sql.contains(":endDate"))
        assertTrue(sql.contains(":active"))
    }

    @Test
    fun testPositionalParameters() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE u.name = ?1 AND u.email = ?2 AND u.age > ?3
        """.trimIndent())

        assertTrue(sql.contains("?1"))
        assertTrue(sql.contains("?2"))
        assertTrue(sql.contains("?3"))
    }

    // ═══════════════════════════════════════════════════════
    //  Real-world Spring Data JPA patterns
    // ═══════════════════════════════════════════════════════

    @Test
    fun testSpringDataFindActiveUsersWithOrders() {
        val sql = convert("""
            SELECT u FROM User u
            LEFT JOIN u.orders o
            WHERE u.active = true
            AND o.createdAt > :since
            GROUP BY u.id
            HAVING COUNT(o.id) > 0
            ORDER BY u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("FROM users u"))
        assertTrue(sql.contains("LEFT JOIN"))
        assertTrue(sql.contains("u.active = TRUE"))
        assertTrue(sql.contains("GROUP BY u.id"))
        assertTrue(sql.contains("HAVING COUNT(o.id) > 0"))
        assertTrue(sql.contains("ORDER BY u.name ASC"))
    }

    @Test
    fun testSpringDataSearchWithOptionalFilters() {
        val sql = convert("""
            SELECT u FROM User u
            WHERE (:name IS NULL OR u.name LIKE :name)
            AND (:email IS NULL OR u.email = :email)
            AND (:role IS NULL OR u.role = :role)
            ORDER BY u.createdAt DESC
        """.trimIndent())

        assertTrue(sql.contains(":name IS NULL"))
        assertTrue(sql.contains("u.name LIKE :name"))
        assertTrue(sql.contains(":email IS NULL"))
        assertTrue(sql.contains(":role IS NULL"))
        assertTrue(sql.contains("ORDER BY u.created_at DESC"))
    }

    @Test
    fun testBotRatingFullQueryEndToEnd() {
        val resolver = BotRatingMockResolver()
        val sql = convert("""
            SELECT br FROM BotRating br
            WHERE br.bot = :bot
            AND br.game = :game
            AND (:leaderboardId IS NULL AND br.leaderboardId IS NULL
                 OR br.leaderboardId = :leaderboardId)
        """.trimIndent(), resolver)

        assertEquals(
            "SELECT br FROM bot_ratings br WHERE ((br.bot_id = :bot AND br.game = :game) AND ((:leaderboardId IS NULL AND br.leaderboard_id IS NULL) OR br.leaderboard_id = :leaderboardId))",
            sql
        )
    }

    @Test
    fun testDashboardStatsQuery() {
        val sql = convert("""
            SELECT u.department,
                   COUNT(u.id),
                   AVG(u.salary),
                   MIN(u.hireDate),
                   MAX(u.salary)
            FROM User u
            WHERE u.active = true
            AND u.hireDate BETWEEN :startDate AND :endDate
            GROUP BY u.department
            HAVING COUNT(u.id) >= 5
            ORDER BY COUNT(u.id) DESC
        """.trimIndent())

        assertTrue(sql.contains("COUNT(u.id)"))
        assertTrue(sql.contains("AVG(u.salary)"))
        assertTrue(sql.contains("MIN(u.hire_date)"))
        assertTrue(sql.contains("MAX(u.salary)"))
        assertTrue(sql.contains("u.active = TRUE"))
        assertTrue(sql.contains("BETWEEN :startDate AND :endDate"))
        assertTrue(sql.contains("GROUP BY u.department"))
        assertTrue(sql.contains("HAVING COUNT(u.id) >= 5"))
        assertTrue(sql.contains("ORDER BY COUNT(u.id) DESC"))
    }

    @Test
    fun testJoinWithExplicitOnAndWhere() {
        val sql = convert("""
            SELECT o FROM Order o
            JOIN Customer c ON c.id = o.customerId
            WHERE c.country = :country
            AND o.status IN ('SHIPPED', 'DELIVERED')
            ORDER BY o.createdAt DESC
        """.trimIndent())

        assertTrue(sql.contains("JOIN"))
        assertTrue(sql.contains("ON c.id = o.customer_id"))
        assertTrue(sql.contains("c.country = :country"))
        assertTrue(sql.contains("IN ('SHIPPED', 'DELIVERED')"))
        assertTrue(sql.contains("ORDER BY o.created_at DESC"))
    }

    @Test
    fun testSelectWithAliasesAndJoin() {
        val sql = convert("""
            SELECT u.name AS userName, COUNT(o.id) AS orderCount
            FROM User u
            LEFT JOIN u.orders o
            GROUP BY u.name
            ORDER BY COUNT(o.id) DESC
        """.trimIndent())

        assertTrue(sql.contains("u.name AS userName"))
        assertTrue(sql.contains("COUNT(o.id) AS orderCount"))
        assertTrue(sql.contains("GROUP BY u.name"))
        assertTrue(sql.contains("ORDER BY COUNT(o.id) DESC"))
    }
}


