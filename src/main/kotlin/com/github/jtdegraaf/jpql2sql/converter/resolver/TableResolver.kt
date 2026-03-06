package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiClass

/**
 * Resolves JPA entity names to SQL table names.
 *
 * Checks `@Table(name = "...")` first, then `@Entity(name = "...")`,
 * and falls back to snake_case of the class name.
 */
class TableResolver {

    fun resolve(psiClass: PsiClass): String {
        // Check @Subselect("SELECT ...") - Hibernate annotation for mapping to a subquery
        val subselectAnnotation = PsiUtils.findAnnotation(listOf(psiClass), JpaAnnotations.SUBSELECT)
        if (subselectAnnotation != null) {
            val subquery = PsiUtils.getAnnotationStringValue(subselectAnnotation, "value")
            if (!subquery.isNullOrBlank()) return "($subquery)"
        }

        // Check @Table(name = "...")
        val tableAnnotation = PsiUtils.findAnnotation(listOf(psiClass), JpaAnnotations.TABLE)
        if (tableAnnotation != null) {
            val name = PsiUtils.getAnnotationStringValue(tableAnnotation, "name")
            if (!name.isNullOrBlank()) return name
        }

        // Check @Entity(name = "...")
        val entityAnnotation = PsiUtils.findAnnotation(listOf(psiClass), JpaAnnotations.ENTITY)
        if (entityAnnotation != null) {
            val name = PsiUtils.getAnnotationStringValue(entityAnnotation, "name")
            if (!name.isNullOrBlank()) return name
        }

        // Default: snake_case of class name
        return NamingUtils.toSnakeCase(psiClass.name ?: "unknown")
    }
}

