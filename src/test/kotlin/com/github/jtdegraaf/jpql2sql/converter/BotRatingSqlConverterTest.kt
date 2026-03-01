package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.entities.BotEntities

/**
 * SQL conversion tests for Bot and BotRating entities.
 */
class BotRatingSqlConverterTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        BotEntities.addAll(myFixture)
    }

    fun testBotRatingTableName() {
        val sql = convertWithPostgres("SELECT br FROM BotRating br WHERE br.bot = :bot AND br.game = :game")
        assertTrue("Table should be 'bot_ratings'", sql.contains("FROM bot_ratings br"))
        assertTrue("bot should resolve to bot_id", sql.contains("br.bot_id = :bot"))
    }

    fun testBotRatingColumnMappings() {
        val sql1 = convertWithPostgres("SELECT br FROM BotRating br WHERE br.leaderboardId IS NULL")
        assertTrue("leaderboardId -> leaderboard_id", sql1.contains("br.leaderboard_id IS NULL"))

        val sql2 = convertWithPostgres("SELECT br.eloRating FROM BotRating br")
        assertTrue("eloRating -> elo_rating", sql2.contains("br.elo_rating"))
    }

    fun testBotTableName() {
        val sql = convertWithPostgres("SELECT b FROM Bot b")
        assertEquals("SELECT b FROM bots b", sql)
    }
}
