package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.MySqlDialect
import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.converter.resolver.JoinInfo
import com.github.jtdegraaf.jpql2sql.converter.resolver.NamingUtils
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import org.junit.Assert.*
import org.junit.Test

class SqlConverterTest {

    private fun convertWithPostgres(jpql: String): String {
        val parser = JpqlParser(jpql)
        val ast = parser.parse()
        val resolver = MockEntityResolver()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        return converter.convert(ast)
    }

    private fun convertWithMySql(jpql: String): String {
        val parser = JpqlParser(jpql)
        val ast = parser.parse()
        val resolver = MockEntityResolver()
        val converter = SqlConverter(MySqlDialect, resolver)
        return converter.convert(ast)
    }

    @Test
    fun testSimpleSelect() {
        val sql = convertWithPostgres("SELECT u FROM User u")
        assertEquals("SELECT u FROM users u", sql)
    }

    @Test
    fun testSelectFields() {
        val sql = convertWithPostgres("SELECT u.id, u.name FROM User u")
        assertEquals("SELECT u.id, u.name FROM users u", sql)
    }

    @Test
    fun testSelectDistinct() {
        val sql = convertWithPostgres("SELECT DISTINCT u.name FROM User u")
        assertEquals("SELECT DISTINCT u.name FROM users u", sql)
    }

    @Test
    fun testWhereEquals() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = 1")
        assertEquals("SELECT u FROM users u WHERE u.id = 1", sql)
    }

    @Test
    fun testWhereNamedParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = :userId")
        assertEquals("SELECT u FROM users u WHERE u.id = :userId", sql)
    }

    @Test
    fun testWherePositionalParameter() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.id = ?1")
        assertEquals("SELECT u FROM users u WHERE u.id = ?1", sql)
    }

    @Test
    fun testWhereIsNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NULL", sql)
    }

    @Test
    fun testWhereIsNotNull() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.deletedAt IS NOT NULL")
        assertEquals("SELECT u FROM users u WHERE u.deleted_at IS NOT NULL", sql)
    }

    @Test
    fun testWhereIn() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.status IN ('ACTIVE', 'PENDING')")
        assertEquals("SELECT u FROM users u WHERE u.status IN ('ACTIVE', 'PENDING')", sql)
    }

    @Test
    fun testWhereBetween() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.age BETWEEN 18 AND 65")
        assertEquals("SELECT u FROM users u WHERE u.age BETWEEN 18 AND 65", sql)
    }

    @Test
    fun testWhereLike() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name LIKE '%John%'")
        assertEquals("SELECT u FROM users u WHERE u.name LIKE '%John%'", sql)
    }

    @Test
    fun testWhereAnd() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.active = true AND u.age > 18")
        assertEquals("SELECT u FROM users u WHERE (u.active = TRUE AND u.age > 18)", sql)
    }

    @Test
    fun testWhereOr() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.admin = true OR u.moderator = true")
        assertEquals("SELECT u FROM users u WHERE (u.admin = TRUE OR u.moderator = TRUE)", sql)
    }

    @Test
    fun testInnerJoin() {
        val sql = convertWithPostgres("SELECT u FROM User u INNER JOIN u.orders o")
        assertTrue(sql.contains("INNER JOIN"))
        assertTrue(sql.contains("orders"))
    }

    @Test
    fun testLeftJoin() {
        val sql = convertWithPostgres("SELECT u FROM User u LEFT JOIN u.orders o")
        assertTrue(sql.contains("LEFT JOIN"))
    }

    @Test
    fun testOrderByAsc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC", sql)
    }

    @Test
    fun testOrderByDesc() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.createdAt DESC")
        assertEquals("SELECT u FROM users u ORDER BY u.created_at DESC", sql)
    }

    @Test
    fun testOrderByNullsFirst() {
        val sql = convertWithPostgres("SELECT u FROM User u ORDER BY u.name ASC NULLS FIRST")
        assertEquals("SELECT u FROM users u ORDER BY u.name ASC NULLS FIRST", sql)
    }

    @Test
    fun testGroupBy() {
        val sql = convertWithPostgres("SELECT u.department, COUNT(u) FROM User u GROUP BY u.department")
        assertTrue(sql.contains("GROUP BY u.department"))
    }

    @Test
    fun testCountAll() {
        val sql = convertWithPostgres("SELECT COUNT(*) FROM User u")
        assertEquals("SELECT COUNT(*) FROM users u", sql)
    }

    @Test
    fun testCountDistinct() {
        val sql = convertWithPostgres("SELECT COUNT(DISTINCT u.department) FROM User u")
        assertEquals("SELECT COUNT(DISTINCT u.department) FROM users u", sql)
    }

    @Test
    fun testSum() {
        val sql = convertWithPostgres("SELECT SUM(o.amount) FROM Order o")
        assertEquals("SELECT SUM(o.amount) FROM orders o", sql)
    }

    @Test
    fun testMySqlBooleanLiteral() {
        val sqlPg = convertWithPostgres("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlPg.contains("TRUE"))

        val sqlMy = convertWithMySql("SELECT u FROM User u WHERE u.active = true")
        assertTrue(sqlMy.contains("TRUE"))
    }

    @Test
    fun testCamelCaseToSnakeCase() {
        val sql = convertWithPostgres("SELECT u.firstName, u.lastName, u.emailAddress FROM User u")
        assertEquals("SELECT u.first_name, u.last_name, u.email_address FROM users u", sql)
    }

    @Test
    fun testStringLiteral() {
        val sql = convertWithPostgres("SELECT u FROM User u WHERE u.name = 'John'")
        assertEquals("SELECT u FROM users u WHERE u.name = 'John'", sql)
    }

    @Test
    fun testConstructorProjection() {
        val sql = convertWithPostgres("SELECT NEW com.example.UserDto(u.id, u.name) FROM User u")
        // Constructor expressions become simple column lists
        assertTrue(sql.contains("u.id"))
        assertTrue(sql.contains("u.name"))
    }

    @Test
    fun testComplexQuery() {
        val sql = convertWithPostgres("""
            SELECT u.id, u.name
            FROM User u
            LEFT JOIN u.orders o
            WHERE u.active = true AND u.createdAt > :startDate
            ORDER BY u.name ASC
        """.trimIndent())

        assertTrue(sql.contains("SELECT u.id, u.name"))
        assertTrue(sql.contains("FROM users u"))
        assertTrue(sql.contains("LEFT JOIN"))
        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("ORDER BY u.name ASC"))
    }

    @Test
    fun testManyToOneJoinColumnResolution() {
        // Simulates: @ManyToOne @JoinColumn(name = "bot_id") private Bot bot;
        val resolver = BotRatingMockResolver()
        val parser = JpqlParser("SELECT br FROM BotRating br WHERE br.bot = :bot AND br.game = :game")
        val ast = parser.parse()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        val sql = converter.convert(ast)
        assertEquals("SELECT br FROM bot_ratings br WHERE (br.bot_id = :bot AND br.game = :game)", sql)
    }

    @Test
    fun testColumnNameAnnotationResolution() {
        // Simulates: @Column(name = "leaderboard_id") private Long leaderboardId;
        val resolver = BotRatingMockResolver()
        val parser = JpqlParser("SELECT br FROM BotRating br WHERE br.leaderboardId IS NULL")
        val ast = parser.parse()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        val sql = converter.convert(ast)
        assertEquals("SELECT br FROM bot_ratings br WHERE br.leaderboard_id IS NULL", sql)
    }

    @Test
    fun testColumnNameWithExplicitNameAnnotation() {
        // Simulates: @Column(name = "elo_rating") private Integer eloRating;
        val resolver = BotRatingMockResolver()
        val parser = JpqlParser("SELECT br.eloRating FROM BotRating br")
        val ast = parser.parse()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        val sql = converter.convert(ast)
        assertEquals("SELECT br.elo_rating FROM bot_ratings br", sql)
    }

    @Test
    fun testEnumeratedColumnResolution() {
        // Simulates: @Enumerated(EnumType.STRING) @Column(nullable = false) private Game game;
        // @Column without name attribute -> fall back to snake_case of field name
        val resolver = BotRatingMockResolver()
        val parser = JpqlParser("SELECT br FROM BotRating br WHERE br.game = :game")
        val ast = parser.parse()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        val sql = converter.convert(ast)
        assertEquals("SELECT br FROM bot_ratings br WHERE br.game = :game", sql)
    }

    @Test
    fun testBotRatingFullQuery() {
        // Full BotRating query from a real-world scenario
        val resolver = BotRatingMockResolver()
        val jpql = """
            SELECT br FROM BotRating br
            WHERE br.bot = :bot
            AND br.game = :game
            AND (:leaderboardId IS NULL AND br.leaderboardId IS NULL
                 OR br.leaderboardId = :leaderboardId)
        """.trimIndent()
        val parser = JpqlParser(jpql)
        val ast = parser.parse()
        val converter = SqlConverter(PostgreSqlDialect, resolver)
        val sql = converter.convert(ast)
        assertTrue("bot field should resolve to bot_id", sql.contains("br.bot_id"))
        assertTrue("game field should resolve to game", sql.contains("br.game"))
        assertTrue("leaderboardId should resolve to leaderboard_id", sql.contains("br.leaderboard_id"))
    }
}

/**
 * Mock entity resolver for testing without IntelliJ project context.
 * Uses simple snake_case conversion for column and table names.
 */
class MockEntityResolver : EntityResolver(null) {
    override fun resolveTableName(entityName: String): String {
        return NamingUtils.toSnakeCase(entityName) + "s"
    }

    override fun resolveColumnName(entityName: String, fieldPath: List<String>): String {
        return NamingUtils.toSnakeCase(fieldPath.last())
    }

    override fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo {
        val targetTable = NamingUtils.toSnakeCase(fieldName) + "s"
        return JoinInfo(
            columnName = NamingUtils.toSnakeCase(fieldName) + "_id",
            referencedColumnName = "id",
            targetTable = targetTable
        )
    }
}

/**
 * Mock entity resolver that simulates JPA annotation-based column resolution
 * matching the BotRating entity with @Column, @JoinColumn, and @Enumerated mappings.
 */
class BotRatingMockResolver : EntityResolver(null) {

    // Simulated entity metadata: entity name -> (field name -> column name)
    private val entityColumns = mapOf(
        "BotRating" to mapOf(
            "id" to "id",
            "bot" to "bot_id",           // @ManyToOne @JoinColumn(name = "bot_id")
            "game" to "game",            // @Enumerated @Column(nullable = false) — no name override
            "leaderboardId" to "leaderboard_id",  // @Column(name = "leaderboard_id")
            "eloRating" to "elo_rating",          // @Column(name = "elo_rating")
            "matchesPlayed" to "matches_played",  // @Column(name = "matches_played")
            "wins" to "wins",
            "losses" to "losses",
            "draws" to "draws"
        ),
        "User" to emptyMap(),
        "Order" to emptyMap()
    )

    private val entityTables = mapOf(
        "BotRating" to "bot_ratings",
        "User" to "users",
        "Order" to "orders",
        "Bot" to "bots",
        "Game" to "games"
    )

    override fun resolveTableName(entityName: String): String {
        return entityTables[entityName] ?: (NamingUtils.toSnakeCase(entityName) + "s")
    }

    override fun resolveColumnName(entityName: String, fieldPath: List<String>): String {
        val columns = entityColumns[entityName]
        if (columns != null && fieldPath.size == 1) {
            val mapped = columns[fieldPath[0]]
            if (mapped != null) return mapped
        }
        return NamingUtils.toSnakeCase(fieldPath.last())
    }

    override fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo {
        val targetTable = NamingUtils.toSnakeCase(fieldName) + "s"
        return JoinInfo(
            columnName = NamingUtils.toSnakeCase(fieldName) + "_id",
            referencedColumnName = "id",
            targetTable = targetTable
        )
    }
}

