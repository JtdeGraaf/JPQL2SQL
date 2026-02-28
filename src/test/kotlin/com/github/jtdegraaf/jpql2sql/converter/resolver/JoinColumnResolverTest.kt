package com.github.jtdegraaf.jpql2sql.converter.resolver

class JoinColumnResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: JoinColumnResolver

    override fun setUp() {
        super.setUp()
        resolver = JoinColumnResolver()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Parent { @Id private Long id; private String name; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Profile { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Category { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Child {
                @Id private Long id;
                private String name;
                @ManyToOne @JoinColumn(name = "parent_id") private Parent parent;
                @OneToOne @JoinColumn(name = "profile_id") private Profile profile;
                @ManyToOne private Category category;
                @ManyToOne @JoinColumn(name = "other_parent_id", referencedColumnName = "other_id") private Parent otherParent;
            }
        """)
    }

    // ─────────────────── resolve() ──────────────────────

    fun testResolvesManyToOneWithJoinColumn() {
        val ctx = buildContext("Child", "parent")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("parent_id"), result)
    }

    fun testResolvesOneToOneWithJoinColumn() {
        val ctx = buildContext("Child", "profile")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("profile_id"), result)
    }

    fun testDefaultsToFieldNameIdWhenNoJoinColumn() {
        val ctx = buildContext("Child", "category")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("category_id"), result)
    }

    fun testSkipsNonAssociationField() {
        val ctx = buildContext("Child", "name")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Unhandled, result)
    }

    fun testNavigatesIntoDeeperPathViaChain() {
        val ctx = buildContext("Child", "parent", listOf("name"))
        var chainCalled = false
        val chain = ColumnResolverChain { psiClass, fieldPath, _ ->
            chainCalled = true
            assertNotNull("chain should receive a resolved class", psiClass)
            assertEquals(listOf("name"), fieldPath)
            "resolved_name"
        }
        val result = resolver.resolve(ctx, chain)
        assertTrue("chain should have been called", chainCalled)
        assertEquals(ColumnResolution.Resolved("resolved_name"), result)
    }

    fun testNavigatesMultiSegmentPath() {
        val ctx = buildContext("Child", "parent", listOf("nested", "deep"))
        val chain = ColumnResolverChain { _, fieldPath, _ -> fieldPath.joinToString("_") }
        val result = resolver.resolve(ctx, chain)
        assertEquals(ColumnResolution.Resolved("nested_deep"), result)
    }

    // ───────────── companion object helpers ──────────────

    fun testFindJoinColumnNameExtractsName() {
        val psiClass = findClass("Child")
        val members = PsiUtils.findAnnotatedMembers(psiClass, "parent")
        assertEquals("parent_id", JoinColumnResolver.findJoinColumnName(members))
    }

    fun testFindJoinColumnNameReturnsNullWhenAbsent() {
        val psiClass = findClass("Child")
        val members = PsiUtils.findAnnotatedMembers(psiClass, "category")
        assertNull(JoinColumnResolver.findJoinColumnName(members))
    }

    fun testFindReferencedColumnNameExtractsValue() {
        val psiClass = findClass("Child")
        val members = PsiUtils.findAnnotatedMembers(psiClass, "otherParent")
        assertEquals("other_id", JoinColumnResolver.findReferencedColumnName(members))
    }

    fun testFindReferencedColumnNameReturnsNullWhenAbsent() {
        val psiClass = findClass("Child")
        val members = PsiUtils.findAnnotatedMembers(psiClass, "parent")
        assertNull(JoinColumnResolver.findReferencedColumnName(members))
    }
}
