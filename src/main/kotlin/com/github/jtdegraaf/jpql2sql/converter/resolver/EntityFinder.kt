package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

/**
 * Finds JPA entity classes by name within the project.
 *
 * Supports lookup by:
 * - Fully qualified class name
 * - Simple class name (via short name cache)
 * - `@Entity(name = "...")` custom entity names
 */
class EntityFinder(private val project: Project) {

    fun findEntityClass(entityName: String): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        // Try fully qualified name first
        facade.findClass(entityName, scope)?.let {
            if (isEntity(it)) return it
        }

        // Try short name cache
        val shortNamesCache = PsiShortNamesCache.getInstance(project)
        for (psiClass in shortNamesCache.getClassesByName(entityName, scope)) {
            if (isEntity(psiClass)) return psiClass
        }

        // Try custom @Entity(name = "...") lookup
        return findByEntityNameAnnotation(entityName, facade, scope)
    }

    private fun findByEntityNameAnnotation(
        entityName: String,
        facade: JavaPsiFacade,
        scope: GlobalSearchScope
    ): PsiClass? {
        for (fqn in JpaAnnotations.ENTITY) {
            val annotationClass = facade.findClass(fqn, scope) ?: continue

            com.intellij.psi.search.searches.AnnotatedElementsSearch
                .searchPsiClasses(annotationClass, scope)
                .forEach { psiClass ->
                    val annotation = psiClass.getAnnotation(fqn)
                    if (annotation != null) {
                        val name = PsiUtils.getAnnotationStringValue(annotation, "name")
                        if (name == entityName) return psiClass
                    }
                }
        }
        return null
    }

    companion object {
        fun isEntity(psiClass: PsiClass): Boolean {
            return JpaAnnotations.ENTITY.any { psiClass.hasAnnotation(it) }
        }
    }
}

