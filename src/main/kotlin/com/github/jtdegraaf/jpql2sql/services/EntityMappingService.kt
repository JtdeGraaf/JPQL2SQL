package com.github.jtdegraaf.jpql2sql.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class EntityMappingService(private val project: Project) {

    private val entityCache = ConcurrentHashMap<String, EntityMapping>()
    private var cacheInitialized = false

    fun getEntityMapping(entityName: String): EntityMapping? {
        ensureCacheInitialized()
        return entityCache[entityName] ?: entityCache.values.find {
            it.simpleName == entityName || it.qualifiedName == entityName
        }
    }

    fun getAllEntityMappings(): Collection<EntityMapping> {
        ensureCacheInitialized()
        return entityCache.values
    }

    fun invalidateCache() {
        entityCache.clear()
        cacheInitialized = false
    }

    private fun ensureCacheInitialized() {
        if (cacheInitialized) return

        synchronized(this) {
            if (cacheInitialized) return

            scanForEntities()
            cacheInitialized = true
        }
    }

    private fun scanForEntities() {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        // Search for @Entity annotated classes (both Jakarta and javax)
        val entityAnnotations = listOf(
            "jakarta.persistence.Entity",
            "javax.persistence.Entity"
        )

        for (annotationFqn in entityAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, scope) ?: continue

            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { psiClass ->
                val mapping = createEntityMapping(psiClass)
                entityCache[mapping.simpleName] = mapping
                entityCache[mapping.qualifiedName] = mapping
            }
        }
    }

    private fun createEntityMapping(psiClass: PsiClass): EntityMapping {
        val simpleName = psiClass.name ?: "Unknown"
        val qualifiedName = psiClass.qualifiedName ?: simpleName

        // Resolve table name
        val tableName = resolveTableName(psiClass)

        // Resolve column mappings
        val columns = mutableMapOf<String, String>()
        for (field in psiClass.allFields) {
            val columnName = resolveColumnName(field)
            columns[field.name] = columnName
        }

        return EntityMapping(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            tableName = tableName,
            columns = columns
        )
    }

    private fun resolveTableName(psiClass: PsiClass): String {
        val tableAnnotation = psiClass.getAnnotation("jakarta.persistence.Table")
            ?: psiClass.getAnnotation("javax.persistence.Table")

        if (tableAnnotation != null) {
            val nameAttr = tableAnnotation.findAttributeValue("name")
            if (nameAttr != null) {
                val text = nameAttr.text
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length - 1)
                }
            }
        }

        // Default: convert class name to snake_case
        return toSnakeCase(psiClass.name ?: "unknown")
    }

    private fun resolveColumnName(field: com.intellij.psi.PsiField): String {
        val columnAnnotation = field.getAnnotation("jakarta.persistence.Column")
            ?: field.getAnnotation("javax.persistence.Column")

        if (columnAnnotation != null) {
            val nameAttr = columnAnnotation.findAttributeValue("name")
            if (nameAttr != null) {
                val text = nameAttr.text
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length - 1)
                }
            }
        }

        // Default: convert field name to snake_case
        return toSnakeCase(field.name)
    }

    companion object {
        fun getInstance(project: Project): EntityMappingService =
            project.getService(EntityMappingService::class.java)

        fun toSnakeCase(input: String): String {
            return input.replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
                .lowercase()
        }
    }
}

data class EntityMapping(
    val simpleName: String,
    val qualifiedName: String,
    val tableName: String,
    val columns: Map<String, String>
)
