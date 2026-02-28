package com.github.jtdegraaf.jpql2sql.converter.resolver

class EntityFinderTest : BaseResolverTestCase() {

    private lateinit var finder: EntityFinder

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class SimpleEntity { @Id private Long id; }
        """)
        addClass("""
            package com.example;
            import jakarta.persistence.*;
            @Entity
            public class PackagedEntity { @Id private Long id; }
        """)
        addClass("public class NotAnEntity { private Long id; }")
        addClass("""
            import jakarta.persistence.*;
            @Embeddable
            public class EmbeddableValue { private String val1; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity(name = "CustomName")
            public class RenamedEntity { @Id private Long id; }
        """)
        finder = EntityFinder(project)
    }

    fun testFindsBySimpleClassName() {
        val psiClass = finder.findEntityClass("SimpleEntity")
        assertNotNull(psiClass)
        assertEquals("SimpleEntity", psiClass!!.name)
    }

    fun testFindsByFullyQualifiedName() {
        val psiClass = finder.findEntityClass("com.example.PackagedEntity")
        assertNotNull(psiClass)
        assertEquals("PackagedEntity", psiClass!!.name)
    }

    fun testReturnsNullForNonEntityClass() {
        assertNull("Plain class should not be found", finder.findEntityClass("NotAnEntity"))
    }

    fun testReturnsNullForEmbeddableClass() {
        assertNull("@Embeddable is not @Entity", finder.findEntityClass("EmbeddableValue"))
    }

    fun testReturnsNullForUnknownName() {
        assertNull(finder.findEntityClass("DoesNotExist"))
    }

    fun testFindsByCustomEntityName() {
        val psiClass = finder.findEntityClass("CustomName")
        assertNotNull("should find by @Entity(name)", psiClass)
        assertEquals("RenamedEntity", psiClass!!.name)
    }

    fun testIsEntityPositive() {
        assertTrue(EntityFinder.isEntity(findClass("SimpleEntity")))
    }

    fun testIsEntityNegative() {
        assertFalse(EntityFinder.isEntity(findClass("NotAnEntity")))
    }
}
