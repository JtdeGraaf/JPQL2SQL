package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for resolver tests that need Java PSI.
 *
 * Provides helper methods to add Java source files to the test fixture
 * and look up [PsiClass] instances by name.
 */
abstract class BaseResolverTestCase : BasePlatformTestCase() {

    private var classCounter = 0

    /**
     * Add a Java class to the test fixture from source code.
     * Automatically determines the file path from the class/package declaration.
     */
    protected fun addClass(source: String) {
        val trimmed = source.trimIndent().trim()

        // Extract package name if present
        val packageMatch = Regex("""^package\s+([\w.]+)\s*;""").find(trimmed)
        val packagePath = packageMatch?.groupValues?.get(1)?.replace('.', '/') ?: ""

        // Extract class/interface/enum name
        val classMatch = Regex("""(?:public\s+)?(?:abstract\s+)?(?:class|interface|enum|@interface)\s+(\w+)""").find(trimmed)
        val className = classMatch?.groupValues?.get(1) ?: "AnonymousClass${classCounter++}"

        val dir = if (packagePath.isNotEmpty()) "$packagePath/" else ""
        myFixture.addFileToProject("${dir}${className}.java", trimmed)
    }

    /**
     * Look up a [PsiClass] by simple or fully qualified name.
     * Fails the test if not found.
     */
    protected fun findClass(name: String): PsiClass {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        val psiClass = facade.findClass(name, scope)
        assertNotNull("Fixture should contain class '$name'", psiClass)
        return psiClass!!
    }

    /** Build a [ColumnResolverContext] for the given entity field. */
    protected fun buildContext(
        entityName: String,
        fieldName: String,
        remainingPath: List<String> = emptyList(),
        parentOverrides: List<com.intellij.psi.PsiAnnotation> = emptyList()
    ): ColumnResolverContext {
        val psiClass = findClass(entityName)
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        return ColumnResolverContext(psiClass, fieldName, remainingPath, members, parentOverrides, project)
    }

    /** A no-op chain that just snake-cases the last path element. */
    protected fun noopChain() = ColumnResolverChain { _, fieldPath, _ ->
        NamingUtils.toSnakeCase(fieldPath.lastOrNull() ?: "")
    }

    /** Minimal JPA annotation stubs so PSI recognises them. */
    protected fun addJpaStubs() {
        addClass("package jakarta.persistence; public @interface Entity { String name() default \"\"; }")
        addClass("package jakarta.persistence; public @interface Table { String name() default \"\"; }")
        addClass("package jakarta.persistence; public @interface Column { String name() default \"\"; boolean nullable() default true; }")
        addClass("package jakarta.persistence; public @interface Id {}")
        addClass("package jakarta.persistence; public @interface GeneratedValue {}")
        addClass("package jakarta.persistence; public @interface ManyToOne {}")
        addClass("package jakarta.persistence; public @interface OneToOne {}")
        addClass("package jakarta.persistence; public @interface ManyToMany {}")
        addClass("package jakarta.persistence; public @interface JoinColumn { String name() default \"\"; String referencedColumnName() default \"\"; boolean nullable() default true; }")
        addClass("package jakarta.persistence; public @interface JoinTable { String name() default \"\"; JoinColumn[] joinColumns() default {}; JoinColumn[] inverseJoinColumns() default {}; }")
        addClass("package jakarta.persistence; public @interface Embedded {}")
        addClass("package jakarta.persistence; public @interface EmbeddedId {}")
        addClass("package jakarta.persistence; public @interface Embeddable {}")
        addClass("package jakarta.persistence; public @interface Enumerated {}")
        addClass("package jakarta.persistence; public @interface AttributeOverride { String name(); Column column(); }")
        addClass("package jakarta.persistence; public @interface AttributeOverrides { AttributeOverride[] value(); }")
    }
}

