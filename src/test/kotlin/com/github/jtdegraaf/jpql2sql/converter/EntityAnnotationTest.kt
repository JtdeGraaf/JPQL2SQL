package com.github.jtdegraaf.jpql2sql.converter

/**
 * Tests for @Table and @Column annotation resolution.
 * Uses inline entity definitions to test specific annotation behaviors.
 */
class EntityAnnotationTest : BaseJpaTestCase() {

    fun testTableAnnotationOverridesClassName() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "custom_table_name")
            public class MyEntity {
                @Id
                private Long id;
            }
        """.trimIndent())

        val sql = convertWithPostgres("SELECT e FROM MyEntity e")
        assertTrue(sql.contains("FROM custom_table_name e"))
    }

    fun testColumnAnnotationOverridesFieldName() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "entities")
            public class MyEntity {
                @Id
                private Long id;

                @Column(name = "custom_column")
                private String myField;
            }
        """.trimIndent())

        val sql = convertWithPostgres("SELECT e.myField FROM MyEntity e")
        assertTrue(sql.contains("e.custom_column"))
    }

    fun testManyToOneJoinColumn() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "parents")
            public class Parent {
                @Id
                private Long id;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "children")
            public class Child {
                @Id
                private Long id;

                @ManyToOne
                @JoinColumn(name = "parent_id")
                private Parent parent;
            }
        """.trimIndent())

        val sql = convertWithPostgres("SELECT c FROM Child c WHERE c.parent = :parent")
        assertTrue(sql.contains("c.parent_id = :parent"))
    }
}
