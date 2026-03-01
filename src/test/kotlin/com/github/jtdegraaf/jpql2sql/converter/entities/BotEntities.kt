package com.github.jtdegraaf.jpql2sql.converter.entities

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * Bot and BotRating entity definitions for testing.
 */
object BotEntities {

    fun addAll(fixture: JavaCodeInsightTestFixture) {
        addBot(fixture)
        addBotRating(fixture)
    }

    fun addBot(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "bots")
            public class Bot {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;
            }
        """.trimIndent())
    }

    fun addBotRating(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "bot_ratings")
            public class BotRating {
                @Id
                private Long id;

                @ManyToOne
                @JoinColumn(name = "bot_id")
                private Bot bot;

                @Column(name = "game")
                private String game;

                @Column(name = "leaderboard_id")
                private Long leaderboardId;

                @Column(name = "elo_rating")
                private Integer eloRating;
            }
        """.trimIndent())
    }
}
