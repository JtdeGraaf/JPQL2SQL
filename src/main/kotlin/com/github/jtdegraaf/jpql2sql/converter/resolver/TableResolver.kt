package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiClass

/**
 * Resolves JPA entity names to SQL table names.
 *
 * Checks `@Table(name = "...")` first, then `@Entity(name = "...")`,
 * and falls back to snake_case of the class name.
 *
 * For SINGLE_TABLE inheritance, subclasses use the root entity's table name.
 */
class TableResolver {

    fun resolve(psiClass: PsiClass): String {
        // Check @Subselect("SELECT ...") - Hibernate annotation for mapping to a subquery
        val subselectAnnotation = PsiUtils.findAnnotation(listOf(psiClass), JpaAnnotations.SUBSELECT)
        if (subselectAnnotation != null) {
            val subquery = PsiUtils.getAnnotationStringValue(subselectAnnotation, "value")
            if (!subquery.isNullOrBlank()) return "($subquery)"
        }

        // For SINGLE_TABLE inheritance, find the root entity that defines the table
        val rootClass = findInheritanceRoot(psiClass)
        val targetClass = rootClass ?: psiClass

        // Check @Table(name = "...")
        val tableAnnotation = PsiUtils.findAnnotation(listOf(targetClass), JpaAnnotations.TABLE)
        if (tableAnnotation != null) {
            val name = PsiUtils.getAnnotationStringValue(tableAnnotation, "name")
            if (!name.isNullOrBlank()) return name
        }

        // Check @Entity(name = "...")
        val entityAnnotation = PsiUtils.findAnnotation(listOf(targetClass), JpaAnnotations.ENTITY)
        if (entityAnnotation != null) {
            val name = PsiUtils.getAnnotationStringValue(entityAnnotation, "name")
            if (!name.isNullOrBlank()) return name
        }

        // Default: snake_case of class name
        return NamingUtils.toSnakeCase(targetClass.name ?: "unknown")
    }

    /**
     * Finds the root entity in a SINGLE_TABLE inheritance hierarchy.
     * Returns null if the entity is not part of such a hierarchy.
     */
    private fun findInheritanceRoot(psiClass: PsiClass): PsiClass? {
        var current: PsiClass? = psiClass
        var root: PsiClass? = null

        while (current != null) {
            // Check if this class has @Inheritance(strategy = SINGLE_TABLE)
            val inheritanceAnnotation = PsiUtils.findAnnotation(listOf(current), JpaAnnotations.INHERITANCE)
            if (inheritanceAnnotation != null) {
                val strategy = PsiUtils.getAnnotationEnumValue(inheritanceAnnotation, "strategy")
                // SINGLE_TABLE is the default if @Inheritance is present without explicit strategy
                if (strategy == null || strategy == "SINGLE_TABLE") {
                    root = current
                }
            }
            current = current.superClass
        }

        return root
    }
}

