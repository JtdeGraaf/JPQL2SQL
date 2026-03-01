package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.MatchEntities

/**
 * SQL conversion tests for Match and MatchParticipant entities.
 */
class MatchSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        MatchEntities.addAll(myFixture)
    }

    fun testMatchTableName() {
        val sql = convertWithPostgres("SELECT m FROM Match m WHERE m.status = 'FINISHED'")
        assertTrue("Table should be 'matches'", sql.contains("FROM matches m"))
        assertTrue("status column", sql.contains("m.status = 'FINISHED'"))
    }

    fun testMatchColumnMappings() {
        val sql1 = convertWithPostgres("SELECT m.finishedAt FROM Match m")
        assertTrue("finishedAt -> finished_at", sql1.contains("m.finished_at"))

        val sql2 = convertWithPostgres("SELECT m.forfeitReason FROM Match m")
        assertTrue("forfeitReason -> forfeit_reason", sql2.contains("m.forfeit_reason"))
    }

    fun testMatchParticipantWithJoinColumn() {
        val sql = convertWithPostgres("SELECT p FROM MatchParticipant p WHERE p.bot.id = :botId")
        assertTrue("bot.id should resolve to bot_id", sql.contains("p.bot_id = :botId"))
    }

    fun testComplexMatchQuery() {
        val sql = convertWithPostgres("""
            SELECT COUNT(m) > 0
            FROM Match m
            JOIN m.participants p1
            JOIN m.participants p2
            WHERE m.game = :game
            AND m.status = 'FINISHED'
            AND m.finishedAt >= :since
            AND m.id != :excludeMatchId
            AND p1.bot.id = :bot1Id
            AND p2.bot.id = :bot2Id
            AND p1.id != p2.id
        """.trimIndent())

        println("Generated SQL: $sql")

        assertTrue("Table should be 'matches'", sql.contains("FROM matches m"))
        assertTrue("game column", sql.contains("m.game = :game"))
        assertTrue("status column", sql.contains("m.status = 'FINISHED'"))
        assertTrue("finished_at column", sql.contains("m.finished_at >= :since"))
        assertTrue("JOIN to match_participants: $sql", sql.contains("JOIN match_participants"))
        assertTrue("p1.bot.id -> p1.bot_id", sql.contains("p1.bot_id = :bot1Id"))
        assertTrue("p2.bot.id -> p2.bot_id", sql.contains("p2.bot_id = :bot2Id"))
        assertTrue("COUNT comparison in SELECT", sql.contains("COUNT(m) > 0"))
    }
}
