package com.github.jtdegraaf.jpql2sql.converter.resolver

class JoinInfoResolverTest : BaseResolverTestCase() {

    private lateinit var resolver: JoinInfoResolver

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "departments")
            public class Department { @Id private Long id; private String name; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Team { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Project { @Id private Long id; }
        """)
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class Employee {
                @Id private Long id;
                @ManyToOne @JoinColumn(name = "dept_id") private Department department;
                @ManyToOne @JoinColumn(name = "mgr_id", referencedColumnName = "mgr_code") private Employee manager;
                @ManyToOne private Team team;
                @ManyToMany @JoinTable(name = "employee_projects", joinColumns = @JoinColumn(name = "emp_id"), inverseJoinColumns = @JoinColumn(name = "proj_id")) private java.util.Set<Project> projects;
            }
        """)
        val finder = EntityFinder(project)
        val tableResolver = TableResolver()
        resolver = JoinInfoResolver(project, finder, tableResolver)
    }

    // ─────────────── @JoinColumn resolution ─────────────

    fun testResolvesJoinColumnName() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("dept_id", info!!.columnName)
    }

    fun testResolvesReferencedColumnName() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("id", info!!.referencedColumnName)
    }

    fun testResolvesCustomReferencedColumnName() {
        val info = resolver.resolve("Employee", "manager")
        assertNotNull(info)
        assertEquals("mgr_code", info!!.referencedColumnName)
    }

    fun testResolvesTargetTableFromTargetEntity() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertEquals("departments", info!!.targetTable)
    }

    fun testNoJoinTableForManyToOne() {
        val info = resolver.resolve("Employee", "department")
        assertNotNull(info)
        assertNull(info!!.joinTable)
        assertNull(info.inverseColumnName)
    }

    // ──────────── Default when no @JoinColumn ───────────

    fun testDefaultsToFieldNameIdWhenNoJoinColumn() {
        val info = resolver.resolve("Employee", "team")
        assertNotNull(info)
        assertEquals("team_id", info!!.columnName)
        assertEquals("id", info.referencedColumnName)
    }

    // ──────────────── @JoinTable resolution ──────────────

    fun testResolvesJoinTableName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("employee_projects", info!!.joinTable)
    }

    fun testResolvesJoinTableJoinColumnName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("emp_id", info!!.columnName)
    }

    fun testResolvesJoinTableInverseColumnName() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("proj_id", info!!.inverseColumnName)
    }

    fun testResolvesJoinTableTargetTable() {
        val info = resolver.resolve("Employee", "projects")
        assertNotNull(info)
        assertEquals("project", info!!.targetTable)
    }

    // ──────────────── Edge cases ─────────────────────────

    fun testReturnsNullForUnknownEntity() {
        assertNull(resolver.resolve("NonExistent", "field"))
    }

    fun testReturnsNullForUnknownField() {
        assertNull(resolver.resolve("Employee", "nonExistentField"))
    }
}

/**
 * Tests for custom PK column resolution in JoinInfoResolver.
 */
class JoinInfoResolverCustomPkTest : BaseResolverTestCase() {

    private lateinit var resolver: JoinInfoResolver

    override fun setUp() {
        super.setUp()
        addJpaStubs()

        // Entity with custom PK column name via @Column
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "categories")
            public class Category {
                @Id @Column(name = "category_id")
                private Long id;
                private String name;
            }
        """)

        // Entity with PK field not named "id"
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "products")
            public class Product {
                @Id @Column(name = "product_uuid")
                private String productId;
                private String name;
                @ManyToOne private Category category;
            }
        """)

        // Entity referencing custom PK entities
        addClass("""
            import jakarta.persistence.*;
            @Entity @Table(name = "orders")
            public class Order {
                @Id private Long id;
                @ManyToOne @JoinColumn(name = "product_fk") private Product product;
                @ManyToOne private Category category;
            }
        """)

        val finder = EntityFinder(project)
        val tableResolver = TableResolver()
        // Pass a pkColumnResolver that actually resolves PK columns
        resolver = JoinInfoResolver(project, finder, tableResolver) { entityName ->
            when (entityName) {
                "Category" -> "category_id"
                "Product" -> "product_uuid"
                else -> "id"
            }
        }
    }

    fun testResolvesCustomPkColumnForTargetEntity() {
        // Order.category -> Category has @Column(name = "category_id")
        val info = resolver.resolve("Order", "category")
        assertNotNull(info)
        assertEquals("category_id", info!!.referencedColumnName)
    }

    fun testResolvesCustomPkColumnForProductEntity() {
        // Order.product -> Product has @Column(name = "product_uuid")
        val info = resolver.resolve("Order", "product")
        assertNotNull(info)
        assertEquals("product_uuid", info!!.referencedColumnName)
    }

    fun testExplicitReferencedColumnNameOverridesDefault() {
        // Even with custom PK resolver, explicit @JoinColumn(referencedColumnName) takes precedence
        addClass("""
            import jakarta.persistence.*;
            @Entity
            public class OrderItem {
                @Id private Long id;
                @ManyToOne @JoinColumn(name = "cat_id", referencedColumnName = "legacy_id")
                private Category category;
            }
        """)
        val info = resolver.resolve("OrderItem", "category")
        assertNotNull(info)
        assertEquals("legacy_id", info!!.referencedColumnName)
    }
}
