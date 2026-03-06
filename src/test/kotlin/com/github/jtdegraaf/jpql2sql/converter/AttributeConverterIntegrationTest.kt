package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser

/**
 * Integration tests for JPA AttributeConverter support.
 *
 * Tests verify that JPQL literal values are converted using the actual
 * converter logic when comparing against fields with @Convert annotation.
 */
class AttributeConverterIntegrationTest : BaseJpaTestCase() {

    override fun setUpEntities() {
        addConverterEntities()
    }

    private fun addConverterEntities() {
        // Boolean to String converter (Y/N pattern)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Converter
            public class BooleanToStringConverter implements AttributeConverter<Boolean, String> {
                public String convertToDatabaseColumn(Boolean attribute) {
                    return attribute != null && attribute ? "Y" : "N";
                }
                public Boolean convertToEntityAttribute(String dbData) {
                    return "Y".equals(dbData);
                }
            }
        """.trimIndent())

        // Boolean to Integer converter (1/0 pattern)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Converter
            public class BooleanToIntegerConverter implements AttributeConverter<Boolean, Integer> {
                public Integer convertToDatabaseColumn(Boolean attribute) {
                    return attribute != null && attribute ? 1 : 0;
                }
                public Boolean convertToEntityAttribute(Integer dbData) {
                    return dbData != null && dbData == 1;
                }
            }
        """.trimIndent())

        // Boolean to custom codes converter (T/F pattern)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Converter
            public class BooleanToTrueFalseConverter implements AttributeConverter<Boolean, String> {
                public String convertToDatabaseColumn(Boolean attribute) {
                    if (attribute != null && attribute) {
                        return "T";
                    }
                    return "F";
                }
                public Boolean convertToEntityAttribute(String dbData) {
                    return "T".equals(dbData);
                }
            }
        """.trimIndent())

        // Status enum
        myFixture.addClass("""
            package com.example;

            public enum Status {
                ACTIVE, INACTIVE, PENDING, DELETED
            }
        """.trimIndent())

        // Enum to String converter (stores enum as code)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Converter
            public class StatusToCodeConverter implements AttributeConverter<Status, String> {
                public String convertToDatabaseColumn(Status attribute) {
                    if (attribute == null) return null;
                    switch (attribute) {
                        case ACTIVE: return "ACT";
                        case INACTIVE: return "INA";
                        case PENDING: return "PND";
                        case DELETED: return "DEL";
                        default: return attribute.name();
                    }
                }
                public Status convertToEntityAttribute(String dbData) {
                    return null;
                }
            }
        """.trimIndent())

        // JSON Map converter (for complex objects)
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.util.Map;

            @Converter
            public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
                public String convertToDatabaseColumn(Map<String, Object> attribute) {
                    return "{}";
                }
                public Map<String, Object> convertToEntityAttribute(String dbData) {
                    return null;
                }
            }
        """.trimIndent())

        // User settings entity with Y/N converter
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;
            import java.util.Map;

            @Entity
            @Table(name = "user_settings")
            public class UserSettings {
                @Id
                private Long id;

                @Column(name = "user_id")
                private Long userId;

                @Column(name = "preferences")
                @Convert(converter = JsonMapConverter.class)
                private Map<String, Object> preferences;

                @Column(name = "notifications_enabled")
                @Convert(converter = BooleanToStringConverter.class)
                private Boolean notificationsEnabled;

                @Column(name = "dark_mode")
                @Convert(converter = BooleanToTrueFalseConverter.class)
                private Boolean darkMode;
            }
        """.trimIndent())

        // Product entity with 1/0 converter
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "products")
            public class Product {
                @Id
                private Long id;

                @Column(name = "name")
                private String name;

                @Column(name = "is_available")
                @Convert(converter = BooleanToIntegerConverter.class)
                private Boolean available;

                @Column(name = "is_featured")
                @Convert(converter = BooleanToIntegerConverter.class)
                private Boolean featured;

                @Column(name = "status_code")
                @Convert(converter = StatusToCodeConverter.class)
                private Status status;

                @Column(name = "price")
                private java.math.BigDecimal price;
            }
        """.trimIndent())
    }

    private fun convert(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        val transformer = ImplicitJoinTransformer(resolver)
        val transformedAst = transformer.transform(ast)
        return SqlConverter(PostgreSqlDialect, resolver).convert(transformedAst)
    }

    // ============ BooleanToString (Y/N) Converter Tests ============

    fun testBooleanToStringConverterTrue() {
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.notifications_enabled = 'Y'",
            convert("SELECT s FROM UserSettings s WHERE s.notificationsEnabled = true")
        )
    }

    fun testBooleanToStringConverterFalse() {
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.notifications_enabled = 'N'",
            convert("SELECT s FROM UserSettings s WHERE s.notificationsEnabled = false")
        )
    }

    fun testBooleanToStringConverterNotEquals() {
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.notifications_enabled != 'N'",
            convert("SELECT s FROM UserSettings s WHERE s.notificationsEnabled != false")
        )
    }

    // ============ BooleanToTrueFalse (T/F) Converter Tests ============

    fun testBooleanToTrueFalseConverterTrue() {
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.dark_mode = 'T'",
            convert("SELECT s FROM UserSettings s WHERE s.darkMode = true")
        )
    }

    fun testBooleanToTrueFalseConverterFalse() {
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.dark_mode = 'F'",
            convert("SELECT s FROM UserSettings s WHERE s.darkMode = false")
        )
    }

    // ============ BooleanToInteger (1/0) Converter Tests ============

    fun testBooleanToIntegerConverterTrue() {
        assertEquals(
            "SELECT p FROM products p WHERE p.is_available = 1",
            convert("SELECT p FROM Product p WHERE p.available = true")
        )
    }

    fun testBooleanToIntegerConverterFalse() {
        assertEquals(
            "SELECT p FROM products p WHERE p.is_available = 0",
            convert("SELECT p FROM Product p WHERE p.available = false")
        )
    }

    fun testBooleanToIntegerConverterNotEquals() {
        assertEquals(
            "SELECT p FROM products p WHERE p.is_available != 0",
            convert("SELECT p FROM Product p WHERE p.available != false")
        )
    }

    // ============ Multiple Converters in Same Query ============

    fun testMultipleConvertersInWhereClause() {
        assertEquals(
            "SELECT p FROM products p WHERE p.is_available = 1 AND p.is_featured = 0",
            convert("SELECT p FROM Product p WHERE p.available = true AND p.featured = false")
        )
    }

    fun testConverterWithOtherConditions() {
        assertEquals(
            "SELECT p FROM products p WHERE p.is_available = 1 AND p.price > 100",
            convert("SELECT p FROM Product p WHERE p.available = true AND p.price > 100")
        )
    }

    // ============ Non-Converted Fields (No Change Expected) ============

    fun testNonConvertedBooleanField() {
        // Fields without @Convert should use standard boolean representation
        myFixture.addClass("""
            package com.example;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "flags")
            public class Flag {
                @Id
                private Long id;

                @Column(name = "is_active")
                private Boolean active;
            }
        """.trimIndent())

        assertEquals(
            "SELECT f FROM flags f WHERE f.is_active = TRUE",
            convert("SELECT f FROM Flag f WHERE f.active = true")
        )
    }

    // ============ Select Clause with Converted Fields ============

    fun testConverterFieldInSelect() {
        assertEquals(
            "SELECT p.is_available, p.name FROM products p",
            convert("SELECT p.available, p.name FROM Product p")
        )
    }

    fun testConverterFieldInSelectWithWhere() {
        assertEquals(
            "SELECT p.name FROM products p WHERE p.is_available = 1",
            convert("SELECT p.name FROM Product p WHERE p.available = true")
        )
    }

    // ============ Parameter Values (No Conversion) ============

    fun testConverterFieldWithParameter() {
        // Parameters should not be converted - they're passed at runtime
        assertEquals(
            "SELECT s FROM user_settings s WHERE s.notifications_enabled = :enabled",
            convert("SELECT s FROM UserSettings s WHERE s.notificationsEnabled = :enabled")
        )
    }
}
