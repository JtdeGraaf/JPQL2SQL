package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.converter.entities.MatchEntities
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryAstBuilder
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryParser

/**
 * Integration tests for derived query method conversion.
 * Tests the full flow: method name -> parser -> AST builder -> SQL converter -> SQL string
 */
class DerivedQueryIntegrationTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        MatchEntities.addAll(myFixture)
        addUserWithAddress()
    }

    private fun addUserWithAddress() {
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Embeddable
            public class Address {
                private String street;
                private String city;
                @Column(name = "zip_code")
                private String zipCode;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;

                private String name;

                private String email;

                private Integer age;

                private String status;

                @Column(name = "created_at")
                private java.time.LocalDateTime createdAt;

                @Column(name = "deleted_at")
                private java.time.LocalDateTime deletedAt;

                @Embedded
                private Address address;

                @ManyToOne
                @JoinColumn(name = "department_id")
                private Department department;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "departments")
            public class Department {
                @Id
                private Long id;

                private String name;

                @ManyToOne
                @JoinColumn(name = "company_id")
                private Company company;
            }
        """.trimIndent())

        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "companies")
            public class Company {
                @Id
                private Long id;

                private String name;
            }
        """.trimIndent())
    }

    private fun convertDerivedQuery(methodName: String, entityName: String): String {
        val parser = DerivedQueryParser()
        val components = parser.parse(methodName, entityName)!!
        val resolver = EntityResolver(project)
        val ast = DerivedQueryAstBuilder(resolver).build(components)
        return SqlConverter(PostgreSqlDialect, resolver).convert(ast)
    }

    // ============ Simple queries ============

    fun testFindByName() {
        val sql = convertDerivedQuery("findByName", "User")
        assertEquals("SELECT e FROM users e WHERE e.name = :name", sql)
    }

    fun testFindByEmail() {
        val sql = convertDerivedQuery("findByEmail", "User")
        assertEquals("SELECT e FROM users e WHERE e.email = :email", sql)
    }

    fun testFindByStatus() {
        val sql = convertDerivedQuery("findByStatus", "Match")
        assertEquals("SELECT e FROM matches e WHERE e.status = :status", sql)
    }

    // ============ Multiple conditions ============

    fun testFindByNameAndAge() {
        val sql = convertDerivedQuery("findByNameAndAge", "User")
        assertEquals("SELECT e FROM users e WHERE e.name = :name AND e.age = :age", sql)
    }

    fun testFindByNameOrEmail() {
        val sql = convertDerivedQuery("findByNameOrEmail", "User")
        assertEquals("SELECT e FROM users e WHERE e.name = :name OR e.email = :email", sql)
    }

    fun testFindByNameAndAgeAndStatus() {
        val sql = convertDerivedQuery("findByNameAndAgeAndStatus", "User")
        assertEquals("SELECT e FROM users e WHERE e.name = :name AND e.age = :age AND e.status = :status", sql)
    }

    // ============ Comparison operators ============

    fun testFindByAgeGreaterThan() {
        val sql = convertDerivedQuery("findByAgeGreaterThan", "User")
        assertEquals("SELECT e FROM users e WHERE e.age > :age", sql)
    }

    fun testFindByAgeLessThan() {
        val sql = convertDerivedQuery("findByAgeLessThan", "User")
        assertEquals("SELECT e FROM users e WHERE e.age < :age", sql)
    }

    fun testFindByAgeGreaterThanEqual() {
        val sql = convertDerivedQuery("findByAgeGreaterThanEqual", "User")
        assertEquals("SELECT e FROM users e WHERE e.age >= :age", sql)
    }

    fun testFindByAgeLessThanEqual() {
        val sql = convertDerivedQuery("findByAgeLessThanEqual", "User")
        assertEquals("SELECT e FROM users e WHERE e.age <= :age", sql)
    }

    fun testFindByAgeBetween() {
        val sql = convertDerivedQuery("findByAgeBetween", "User")
        assertEquals("SELECT e FROM users e WHERE e.age BETWEEN :ageStart AND :ageEnd", sql)
    }

    // ============ String operators ============

    fun testFindByNameLike() {
        val sql = convertDerivedQuery("findByNameLike", "User")
        assertEquals("SELECT e FROM users e WHERE e.name LIKE :name", sql)
    }

    fun testFindByNameNotLike() {
        val sql = convertDerivedQuery("findByNameNotLike", "User")
        assertEquals("SELECT e FROM users e WHERE e.name NOT LIKE :name", sql)
    }

    fun testFindByNameContaining() {
        val sql = convertDerivedQuery("findByNameContaining", "User")
        assertEquals("SELECT e FROM users e WHERE e.name LIKE '%' || :name || '%'", sql)
    }

    fun testFindByNameStartingWith() {
        val sql = convertDerivedQuery("findByNameStartingWith", "User")
        assertEquals("SELECT e FROM users e WHERE e.name LIKE :name || '%'", sql)
    }

    fun testFindByNameEndingWith() {
        val sql = convertDerivedQuery("findByNameEndingWith", "User")
        assertEquals("SELECT e FROM users e WHERE e.name LIKE '%' || :name", sql)
    }

    // ============ Null checks ============

    fun testFindByDeletedAtIsNull() {
        val sql = convertDerivedQuery("findByDeletedAtIsNull", "User")
        assertEquals("SELECT e FROM users e WHERE e.deleted_at IS NULL", sql)
    }

    fun testFindByDeletedAtIsNotNull() {
        val sql = convertDerivedQuery("findByDeletedAtIsNotNull", "User")
        assertEquals("SELECT e FROM users e WHERE e.deleted_at IS NOT NULL", sql)
    }

    // ============ In/NotIn ============

    fun testFindByStatusIn() {
        val sql = convertDerivedQuery("findByStatusIn", "User")
        assertEquals("SELECT e FROM users e WHERE e.status IN :status", sql)
    }

    fun testFindByStatusNotIn() {
        val sql = convertDerivedQuery("findByStatusNotIn", "User")
        assertEquals("SELECT e FROM users e WHERE e.status NOT IN :status", sql)
    }

    // ============ Order by ============

    fun testFindByStatusOrderByNameAsc() {
        val sql = convertDerivedQuery("findByStatusOrderByNameAsc", "User")
        assertEquals("SELECT e FROM users e WHERE e.status = :status ORDER BY e.name ASC", sql)
    }

    fun testFindByStatusOrderByCreatedAtDesc() {
        val sql = convertDerivedQuery("findByStatusOrderByCreatedAtDesc", "User")
        assertEquals("SELECT e FROM users e WHERE e.status = :status ORDER BY e.created_at DESC", sql)
    }

    fun testFindByOrderByNameAsc() {
        val sql = convertDerivedQuery("findByOrderByNameAsc", "User")
        assertEquals("SELECT e FROM users e ORDER BY e.name ASC", sql)
    }

    // ============ Limit (Top/First) ============

    fun testFindTop10ByStatus() {
        val sql = convertDerivedQuery("findTop10ByStatus", "User")
        assertEquals("SELECT e FROM users e WHERE e.status = :status LIMIT 10", sql)
    }

    fun testFindFirst5ByOrderByCreatedAtDesc() {
        val sql = convertDerivedQuery("findFirst5ByOrderByCreatedAtDesc", "User")
        assertEquals("SELECT e FROM users e ORDER BY e.created_at DESC LIMIT 5", sql)
    }

    fun testFindFirstByOrderByCreatedAtDesc() {
        val sql = convertDerivedQuery("findFirstByOrderByCreatedAtDesc", "User")
        assertEquals("SELECT e FROM users e ORDER BY e.created_at DESC LIMIT 1", sql)
    }

    // ============ Distinct ============

    fun testFindDistinctByStatus() {
        val sql = convertDerivedQuery("findDistinctByStatus", "User")
        assertEquals("SELECT DISTINCT e FROM users e WHERE e.status = :status", sql)
    }

    // ============ Count/Exists ============

    fun testCountByStatus() {
        val sql = convertDerivedQuery("countByStatus", "User")
        assertEquals("SELECT COUNT(e) FROM users e WHERE e.status = :status", sql)
    }

    fun testExistsByEmail() {
        val sql = convertDerivedQuery("existsByEmail", "User")
        assertEquals("SELECT 1 FROM users e WHERE e.email = :email", sql)
    }

    // ============ Nested properties with relationships ============

    fun testFindByDepartment_Name() {
        val sql = convertDerivedQuery("findByDepartment_Name", "User")
        assertEquals(
            "SELECT e FROM users e LEFT JOIN departments department_1 ON e.department_id = department_1.id WHERE department_1.name = :department_name",
            sql
        )
    }

    fun testFindByDepartment_Company_Name() {
        val sql = convertDerivedQuery("findByDepartment_Company_Name", "User")
        assertEquals(
            "SELECT e FROM users e LEFT JOIN departments department_1 ON e.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE company_2.name = :department_company_name",
            sql
        )
    }

    fun testFindByDepartment_NameOrderByDepartment_Company_NameAsc() {
        assertEquals(
            "SELECT e FROM users e LEFT JOIN departments department_1 ON e.department_id = department_1.id LEFT JOIN companies company_2 ON department_1.company_id = company_2.id WHERE department_1.name = :department_name ORDER BY company_2.name ASC",
            convertDerivedQuery("findByDepartment_NameOrderByDepartment_Company_NameAsc", "User")
        )
    }

    // ============ Match entity with deep nesting (FK optimization) ============

    fun testFindByParticipants_Bot_IdAndStatus() {
        // bot.id uses FK optimization - no JOIN needed for bot, uses FK column directly
        assertEquals(
            "SELECT e FROM matches e LEFT JOIN match_participants participants_1 ON participants_1.match_id = e.id WHERE participants_1.bot_id = :participants_bot_id AND e.status = :status",
            convertDerivedQuery("findByParticipants_Bot_IdAndStatus", "Match")
        )
    }

    fun testFindByParticipants_Bot_NameAndStatus() {
        // When accessing non-PK field (name), JOIN is required
        assertEquals(
            "SELECT e FROM matches e LEFT JOIN match_participants participants_1 ON participants_1.match_id = e.id LEFT JOIN bots bot_2 ON participants_1.bot_id = bot_2.id WHERE bot_2.name = :participants_bot_name AND e.status = :status",
            convertDerivedQuery("findByParticipants_Bot_NameAndStatus", "Match")
        )
    }

    // ============ Embedded properties (no JOIN needed) ============

    fun testFindByAddress_City() {
        assertEquals(
            "SELECT e FROM users e WHERE e.city = :address_city",
            convertDerivedQuery("findByAddress_City", "User")
        )
    }

    fun testFindByAddress_ZipCode() {
        assertEquals(
            "SELECT e FROM users e WHERE e.zip_code = :address_zipCode",
            convertDerivedQuery("findByAddress_ZipCode", "User")
        )
    }

    // ============ FindAll ============

    fun testFindAll() {
        val sql = convertDerivedQuery("findAll", "User")
        assertEquals("SELECT e FROM users e", sql)
    }

    // ============ Delete ============

    fun testDeleteByStatus() {
        val sql = convertDerivedQuery("deleteByStatus", "User")
        assertEquals("SELECT e FROM users e WHERE e.status = :status", sql)
    }
}
