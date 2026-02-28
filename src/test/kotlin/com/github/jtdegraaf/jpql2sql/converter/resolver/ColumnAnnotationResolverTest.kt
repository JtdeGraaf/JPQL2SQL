package com.github.jtdegraaf.jpql2sql.converter.resolver

class ColumnAnnotationResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: ColumnAnnotationResolver

    override fun setUp() {
        super.setUp()
        resolver = ColumnAnnotationResolver()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class TestEntity {
                @Id private Long id;
                @Column(name = "elo_rating", nullable = false) private Integer eloRating;
                @Enumerated @Column(nullable = false) private String game;
                private String someField;
                @JoinColumn(name = "legacy_fk_col") private Long legacyFk;
                @Column(name = "my_custom_column") private String customCol;
                @Column(name = "matches_played", nullable = false) private Integer matchesPlayed;
            }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class GetterEntity {
                @Id private Long id;
                @Column(name = "display_name")
                public String getDisplayName() { return ""; }
            }
        """)
    }

    fun testResolvesExplicitColumnName() {
        val ctx = buildContext("TestEntity", "eloRating")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("elo_rating"), result)
    }

    fun testResolvesColumnWithoutNameFallsToSnakeCase() {
        val ctx = buildContext("TestEntity", "game")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("game"), result)
    }

    fun testResolvesColumnNameFromGetter() {
        val ctx = buildContext("GetterEntity", "displayName")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("display_name"), result)
    }

    fun testFallsBackToSnakeCaseForUnannotatedField() {
        val ctx = buildContext("TestEntity", "someField")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("some_field"), result)
    }

    fun testReturnsUnhandledWhenRemainingPathExists() {
        val ctx = buildContext("TestEntity", "eloRating", listOf("nested"))
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Unhandled, result)
    }

    fun testResolvesBarJoinColumnWithoutManyToOne() {
        val ctx = buildContext("TestEntity", "legacyFk")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("legacy_fk_col"), result)
    }

    fun testResolvesExplicitColumnNameForMatchesPlayed() {
        val ctx = buildContext("TestEntity", "matchesPlayed")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("matches_played"), result)
    }

    fun testResolvesCustomColumnName() {
        val ctx = buildContext("TestEntity", "customCol")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("my_custom_column"), result)
    }
}
