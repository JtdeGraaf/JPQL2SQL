package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.dialect.MySqlDialect
import com.github.jtdegraaf.jpql2sql.converter.dialect.OracleDialect
import com.github.jtdegraaf.jpql2sql.converter.dialect.PostgreSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Base test class that provides JPA annotation stubs and conversion helpers.
 * Extend this class for all JPQL conversion tests.
 */
abstract class BaseJpaTestCase : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        addJpaStubs()
        setUpEntities()
    }

    /**
     * Override this method to add entity classes for your test.
     */
    protected open fun setUpEntities() {}

    private fun addJpaStubs() {
        // Core annotations
        myFixture.addClass("package jakarta.persistence; public @interface Entity { String name() default \"\"; }")
        myFixture.addClass("package jakarta.persistence; public @interface Table { String name() default \"\"; String schema() default \"\"; }")
        myFixture.addClass("package jakarta.persistence; public @interface Column { String name() default \"\"; boolean nullable() default true; int length() default 255; }")
        myFixture.addClass("package jakarta.persistence; public @interface Id {}")
        myFixture.addClass("package jakarta.persistence; public @interface GeneratedValue {}")

        // Relationships
        myFixture.addClass("package jakarta.persistence; public @interface ManyToOne {}")
        myFixture.addClass("package jakarta.persistence; public @interface OneToOne {}")
        myFixture.addClass("package jakarta.persistence; public @interface OneToMany { String mappedBy() default \"\"; }")
        myFixture.addClass("package jakarta.persistence; public @interface ManyToMany { String mappedBy() default \"\"; }")
        myFixture.addClass("package jakarta.persistence; public @interface JoinColumn { String name() default \"\"; String referencedColumnName() default \"\"; boolean nullable() default true; }")
        myFixture.addClass("package jakarta.persistence; public @interface JoinTable { String name() default \"\"; JoinColumn[] joinColumns() default {}; JoinColumn[] inverseJoinColumns() default {}; }")

        // Embedded
        myFixture.addClass("package jakarta.persistence; public @interface Embedded {}")
        myFixture.addClass("package jakarta.persistence; public @interface EmbeddedId {}")
        myFixture.addClass("package jakarta.persistence; public @interface Embeddable {}")
        myFixture.addClass("package jakarta.persistence; public @interface AttributeOverride { String name(); Column column(); }")
        myFixture.addClass("package jakarta.persistence; public @interface AttributeOverrides { AttributeOverride[] value(); }")

        // Other
        myFixture.addClass("package jakarta.persistence; public @interface Enumerated {}")
        myFixture.addClass("package jakarta.persistence; public @interface IdClass { Class value(); }")

        // Hibernate-specific annotations
        myFixture.addClass("package org.hibernate.annotations; public @interface Subselect { String value(); }")

        // Inheritance annotations
        myFixture.addClass("package jakarta.persistence; public enum InheritanceType { SINGLE_TABLE, JOINED, TABLE_PER_CLASS }")
        myFixture.addClass("package jakarta.persistence; public @interface Inheritance { InheritanceType strategy() default InheritanceType.SINGLE_TABLE; }")
        myFixture.addClass("package jakarta.persistence; public @interface DiscriminatorColumn { String name() default \"DTYPE\"; }")
        myFixture.addClass("package jakarta.persistence; public @interface DiscriminatorValue { String value(); }")
    }

    protected fun convertWithPostgres(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        return SqlConverter(PostgreSqlDialect, resolver).convert(ast)
    }

    protected fun convertWithMySql(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        return SqlConverter(MySqlDialect, resolver).convert(ast)
    }

    protected fun convertWithOracle(jpql: String): String {
        val ast = JpqlParser(jpql).parse()
        val resolver = EntityResolver(project)
        return SqlConverter(OracleDialect, resolver).convert(ast)
    }
}
