package com.github.jtdegraaf.jpql2sql.converter.entities

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * Match and MatchParticipant entity definitions for testing.
 */
object MatchEntities {

    fun addAll(fixture: JavaCodeInsightTestFixture) {
        BotEntities.addBot(fixture)
        addMatchParticipant(fixture)
        addMatch(fixture)
    }

    fun addMatch(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.time.LocalDateTime;
            import java.util.List;

            @Entity
            @Table(name = "matches")
            public class Match {
                @Id
                private Long id;

                @Column(name = "game")
                private String game;

                @Column(name = "status")
                private String status;

                @Column(name = "started_at")
                private LocalDateTime startedAt;

                @Column(name = "finished_at")
                private LocalDateTime finishedAt;

                @Column(name = "forfeit_reason")
                private String forfeitReason;

                @OneToMany(mappedBy = "match")
                private List<MatchParticipant> participants;
            }
        """.trimIndent())
    }

    fun addMatchParticipant(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "match_participants")
            public class MatchParticipant {
                @Id
                private Long id;

                @ManyToOne
                @JoinColumn(name = "bot_id")
                private Bot bot;

                @ManyToOne
                @JoinColumn(name = "match_id")
                private Match match;
            }
        """.trimIndent())
    }

    /**
     * Adds a simpler Match entity without the participants relationship.
     */
    fun addSimpleMatch(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.time.LocalDateTime;

            @Entity
            @Table(name = "matches")
            public class Match {
                @Id
                private Long id;

                @Column(name = "game")
                private String game;

                @Column(name = "status")
                private String status;

                @Column(name = "started_at")
                private LocalDateTime startedAt;

                @Column(name = "finished_at")
                private LocalDateTime finishedAt;

                @Column(name = "forfeit_reason")
                private String forfeitReason;
            }
        """.trimIndent())
    }
}
