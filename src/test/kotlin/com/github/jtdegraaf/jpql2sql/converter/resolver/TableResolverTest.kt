package com.github.jtdegraaf.jpql2sql.converter.resolver

class TableResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: TableResolver

    override fun setUp() {
        super.setUp()
        resolver = TableResolver()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "app_users")
            public class UserEntity { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity(name = "custom_name")
            public class NamedEntity { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class BareEntity { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity(name = "entity_name") @Table(name = "table_wins")
            public class BothAnnotated { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Product { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class OrderLineItem { @Id private Long id; }
        """)
    }

    fun testResolvesExplicitTableName() {
        assertEquals("app_users", resolver.resolve(findClass("UserEntity")))
    }

    fun testResolvesEntityNameWhenNoTable() {
        assertEquals("custom_name", resolver.resolve(findClass("NamedEntity")))
    }

    fun testFallsBackToSnakeCaseOfClassName() {
        assertEquals("bare_entity", resolver.resolve(findClass("BareEntity")))
    }

    fun testTableAnnotationTakesPrecedenceOverEntityName() {
        assertEquals("table_wins", resolver.resolve(findClass("BothAnnotated")))
    }

    fun testSingleWordClassName() {
        assertEquals("product", resolver.resolve(findClass("Product")))
    }

    fun testPascalCaseClassName() {
        assertEquals("order_line_item", resolver.resolve(findClass("OrderLineItem")))
    }
}
