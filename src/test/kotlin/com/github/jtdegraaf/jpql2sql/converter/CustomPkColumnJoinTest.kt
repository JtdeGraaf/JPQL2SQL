package com.github.jtdegraaf.jpql2sql.converter

/**
 * Integration tests for JOIN conditions with custom PK column names.
 * Verifies that JOINs use the actual @Column name instead of hardcoded "id".
 */
class CustomPkColumnJoinTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        // Entity with custom PK column name
        myFixture.addClass("""
            package com.example;
            import jakarta.persistence.*;

            @Entity
            @Table(name = "categories")
            public class Category {
                @Id @Column(name = "cat_id")
                private Long categoryId;
                private String name;
            }
        """.trimIndent())

        // Entity with UUID primary key
        myFixture.addClass("""
            package com.example;
            import jakarta.persistence.*;

            @Entity
            @Table(name = "products")
            public class Product {
                @Id @Column(name = "product_uuid")
                private String productUuid;
                private String name;

                @ManyToOne
                @JoinColumn(name = "category_fk")
                private Category category;
            }
        """.trimIndent())

        // Entity that references Product
        myFixture.addClass("""
            package com.example;
            import jakarta.persistence.*;

            @Entity
            @Table(name = "order_items")
            public class OrderItem {
                @Id
                private Long id;

                @ManyToOne
                @JoinColumn(name = "product_id")
                private Product product;
            }
        """.trimIndent())

        // Entity with @OneToMany to test inverse join
        myFixture.addClass("""
            package com.example;
            import jakarta.persistence.*;
            import java.util.List;

            @Entity
            @Table(name = "warehouses")
            public class Warehouse {
                @Id @Column(name = "warehouse_code")
                private String warehouseCode;
                private String name;

                @OneToMany(mappedBy = "warehouse")
                private List<InventoryItem> items;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;
            import jakarta.persistence.*;

            @Entity
            @Table(name = "inventory_items")
            public class InventoryItem {
                @Id
                private Long id;

                @ManyToOne
                @JoinColumn(name = "warehouse_fk")
                private Warehouse warehouse;

                private Integer quantity;
            }
        """.trimIndent())
    }

    // ─────────────── FK optimization with custom PK ─────────────

    fun testFkOptimizationWithCustomPkColumn() {
        // Accessing .categoryId on Product.category should use FK directly
        // without generating a JOIN, because categoryId is the @Id field
        val jpql = "SELECT p FROM Product p WHERE p.category.categoryId = :catId"
        val sql = convertWithPostgres(jpql)

        // Should use FK column directly, no JOIN needed
        assertTrue("Should use FK column directly: $sql",
            sql.contains("p.category_fk = :catId"))
        assertFalse("Should not generate JOIN for FK access: $sql",
            sql.contains("JOIN"))
    }

    // ─────────────── Explicit JOIN with custom PK column ─────────

    fun testExplicitJoinUsesCustomPkColumn() {
        // Explicit JOIN should use the target's actual PK column in the ON clause
        val jpql = "SELECT p FROM Product p JOIN p.category c WHERE c.name = :name"
        val sql = convertWithPostgres(jpql)

        // JOIN should reference category's PK column (cat_id), not hardcoded "id"
        assertTrue("JOIN should use actual PK column cat_id: $sql",
            sql.contains("cat_id"))
    }

    // ─────────────── @OneToMany inverse join ────────────────────

    fun testOneToManyJoinUsesCustomPkColumn() {
        // Warehouse has @OneToMany to InventoryItem
        // JOIN should use warehouse's PK column (warehouse_code)
        val jpql = "SELECT w FROM Warehouse w JOIN w.items i WHERE i.quantity > 0"
        val sql = convertWithPostgres(jpql)

        // The FK column in inventory_items should join to warehouse_code
        assertTrue("Should contain JOIN: $sql", sql.contains("JOIN"))
        assertTrue("JOIN should reference warehouse_code: $sql",
            sql.contains("warehouse_code"))
    }
}
