package com.github.jtdegraaf.jpql2sql.converter.entities

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * User and Order entity definitions for testing.
 */
object UserEntities {

    fun addAll(fixture: JavaCodeInsightTestFixture) {
        addUser(fixture)
        addOrder(fixture)
    }

    fun addUser(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.time.LocalDateTime;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "first_name")
                private String firstName;

                @Column(name = "last_name")
                private String lastName;

                @Column(name = "email_address")
                private String emailAddress;

                @Column(name = "deleted_at")
                private LocalDateTime deletedAt;

                @Column(name = "created_at")
                private LocalDateTime createdAt;

                @Column(name = "status")
                private String status;

                @Column(name = "age")
                private Integer age;

                @Column(name = "active")
                private Boolean active;

                @Column(name = "admin")
                private Boolean admin;

                @Column(name = "moderator")
                private Boolean moderator;

                @Column(name = "department")
                private String department;
            }
        """.trimIndent())
    }

    fun addOrder(fixture: JavaCodeInsightTestFixture) {
        fixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "orders")
            public class Order {
                @Id
                private Long id;

                @Column(name = "amount")
                private Double amount;
            }
        """.trimIndent())
    }
}
