package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.resolver.BaseResolverTestCase

/**
 * Tests for EntityResolver.resolvePrimaryKeyColumn() which resolves
 * the actual PK column name from @Id and @Column annotations.
 */
class EntityResolverPkColumnTest : BaseResolverTestCase() {

    private lateinit var resolver: EntityResolver

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        resolver = EntityResolver(project)
    }

    // ─────────────── Basic @Id resolution ─────────────────

    fun testResolvesDefaultIdColumn() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class User {
                @Id
                private Long id;
            }
        """)
        assertEquals("id", resolver.resolvePrimaryKeyColumn("User"))
    }

    fun testResolvesIdWithColumnAnnotation() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Category {
                @Id @Column(name = "category_id")
                private Long id;
            }
        """)
        assertEquals("category_id", resolver.resolvePrimaryKeyColumn("Category"))
    }

    fun testResolvesNonStandardIdFieldName() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Product {
                @Id
                private String productUuid;
            }
        """)
        assertEquals("product_uuid", resolver.resolvePrimaryKeyColumn("Product"))
    }

    fun testResolvesNonStandardIdFieldNameWithColumn() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id @Column(name = "order_number")
                private String orderNum;
            }
        """)
        assertEquals("order_number", resolver.resolvePrimaryKeyColumn("Order"))
    }

    // ─────────────── Getter-based @Id ─────────────────────

    fun testResolvesIdOnGetter() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Account {
                private Long accountId;

                @Id
                public Long getAccountId() { return accountId; }
            }
        """)
        assertEquals("account_id", resolver.resolvePrimaryKeyColumn("Account"))
    }

    fun testResolvesIdOnGetterWithColumn() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Invoice {
                private String invoiceNo;

                @Id @Column(name = "invoice_number")
                public String getInvoiceNo() { return invoiceNo; }
            }
        """)
        assertEquals("invoice_number", resolver.resolvePrimaryKeyColumn("Invoice"))
    }

    // ─────────────── Fallback cases ───────────────────────

    fun testFallsBackToIdForUnknownEntity() {
        assertEquals("id", resolver.resolvePrimaryKeyColumn("NonExistentEntity"))
    }

    fun testFallsBackToIdWhenNoIdAnnotation() {
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Legacy {
                private Long legacyId;
            }
        """)
        // No @Id annotation, falls back to "id"
        assertEquals("id", resolver.resolvePrimaryKeyColumn("Legacy"))
    }
}
