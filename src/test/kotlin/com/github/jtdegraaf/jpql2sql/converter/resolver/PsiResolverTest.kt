package com.github.jtdegraaf.jpql2sql.converter.resolver

/**
 * Integration test that verifies the full EntityResolver facade works end-to-end
 * with real PSI, exercising all resolvers through the chain.
 */
class PsiResolverTest : BaseResolverTestCase() {

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Bot { @Id private Long id; private String name; @ManyToOne private Category category; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Category { @Id private Long id; private String name; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "bot_ratings")
            public class BotRating {
                @Id private Long id;
                @ManyToOne @JoinColumn(name = "bot_id", nullable = false) private Bot bot;
                @Enumerated @Column(nullable = false) private String game;
                @Column(name = "leaderboard_id") private Long leaderboardId;
                @Column(name = "elo_rating", nullable = false) private Integer eloRating;
                @Column(name = "matches_played", nullable = false) private Integer matchesPlayed;
                @Column(nullable = false) private Integer wins;
            }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Embeddable
            public class Address { private String street; private String city; private String zipCode; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                @Embedded private Address shippingAddress;
            }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class WithGetter {
                @Id private Long id;
                @Column(name = "display_name")
                public String getDisplayName() { return ""; }
            }
        """)
    }

    // ───────────── Full EntityResolver chain ─────────────

    fun testFullChainResolvesColumnAnnotation() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("elo_rating", entityResolver.resolveColumnName("BotRating", listOf("eloRating")))
    }

    fun testFullChainResolvesJoinColumn() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("bot_id", entityResolver.resolveColumnName("BotRating", listOf("bot")))
    }

    fun testFullChainResolvesPlainField() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("game", entityResolver.resolveColumnName("BotRating", listOf("game")))
    }

    fun testFullChainResolvesTableName() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("bot_ratings", entityResolver.resolveTableName("BotRating"))
    }

    fun testFullChainFallsBackToSnakeCaseForUnknownEntity() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("some_field", entityResolver.resolveColumnName("Unknown", listOf("someField")))
    }

    fun testFullChainResolvesLeaderboardId() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("leaderboard_id", entityResolver.resolveColumnName("BotRating", listOf("leaderboardId")))
    }

    fun testFullChainResolvesMatchesPlayed() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("matches_played", entityResolver.resolveColumnName("BotRating", listOf("matchesPlayed")))
    }

    fun testFullChainResolvesPlainWins() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("wins", entityResolver.resolveColumnName("BotRating", listOf("wins")))
    }

    fun testFullChainResolvesColumnOnGetter() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("display_name", entityResolver.resolveColumnName("WithGetter", listOf("displayName")))
    }

    fun testFullChainResolvesTableWithoutAnnotation() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("bot", entityResolver.resolveTableName("Bot"))
    }

    fun testFullChainReturnsEmptyForEmptyPath() {
        val entityResolver = com.github.jtdegraaf.jpql2sql.converter.EntityResolver(project)
        assertEquals("", entityResolver.resolveColumnName("BotRating", emptyList()))
    }
}
