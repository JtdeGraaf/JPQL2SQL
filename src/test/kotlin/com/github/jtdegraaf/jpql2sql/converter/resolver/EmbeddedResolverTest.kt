package com.github.jtdegraaf.jpql2sql.converter.resolver

class EmbeddedResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: EmbeddedResolver

    override fun setUp() {
        super.setUp()
        resolver = EmbeddedResolver()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Embeddable
            public class Address { private String street; private String city; private String zipCode; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Embeddable
            public class CompositeKey { private String partA; private String partB; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Store { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Customer {
                @Id private Long id;
                private String name;
                @Embedded private Address address;
                @Embedded private Address billingAddress;
                @ManyToOne private Store preferredStore;
            }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class CompositeEntity {
                @EmbeddedId private CompositeKey pk;
            }
        """)
    }

    fun testHandlesEmbeddedFieldWithRemainingPath() {
        val ctx = buildContext("Customer", "address", listOf("street"))
        var chainCalled = false
        val chain = ColumnResolverChain { psiClass, fieldPath, _ ->
            chainCalled = true
            assertNotNull("should receive the embedded class", psiClass)
            assertEquals("Address", psiClass!!.name)
            assertEquals(listOf("street"), fieldPath)
            "street"
        }
        val result = resolver.resolve(ctx, chain)
        assertTrue("chain should be called", chainCalled)
        assertEquals(ColumnResolution.Resolved("street"), result)
    }

    fun testHandlesEmbeddedIdField() {
        val ctx = buildContext("CompositeEntity", "pk", listOf("partA"))
        val chain = ColumnResolverChain { psiClass, _, _ ->
            assertNotNull(psiClass)
            assertEquals("CompositeKey", psiClass!!.name)
            "part_a"
        }
        val result = resolver.resolve(ctx, chain)
        assertEquals(ColumnResolution.Resolved("part_a"), result)
    }

    fun testReturnsSnakeCaseWhenNoRemainingPath() {
        val ctx = buildContext("Customer", "address")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("address"), result)
    }

    fun testReturnsSnakeCaseForCamelCaseEmbeddedName() {
        val ctx = buildContext("Customer", "billingAddress")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Resolved("billing_address"), result)
    }

    fun testSkipsNonEmbeddedField() {
        val ctx = buildContext("Customer", "name")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Unhandled, result)
    }

    fun testSkipsManyToOneField() {
        val ctx = buildContext("Customer", "preferredStore")
        val result = resolver.resolve(ctx, noopChain())
        assertEquals(ColumnResolution.Unhandled, result)
    }

    fun testNavigatesMultiLevelEmbedded() {
        val ctx = buildContext("Customer", "address", listOf("zipCode"))
        val chain = ColumnResolverChain { psiClass, fieldPath, _ ->
            assertEquals("Address", psiClass!!.name)
            assertEquals(listOf("zipCode"), fieldPath)
            "zip_code"
        }
        val result = resolver.resolve(ctx, chain)
        assertEquals(ColumnResolution.Resolved("zip_code"), result)
    }
}
