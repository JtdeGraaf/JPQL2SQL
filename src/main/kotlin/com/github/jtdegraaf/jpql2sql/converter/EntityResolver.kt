package com.github.jtdegraaf.jpql2sql.converter

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.search.GlobalSearchScope

open class EntityResolver(private val project: Project?) {

    open fun resolveTableName(entityName: String): String {
        if (project == null) return toSnakeCase(entityName)
        val psiClass = findEntityClass(entityName) ?: return toSnakeCase(entityName)

        // Check for @Table annotation
        val tableAnnotation = psiClass.getAnnotation("jakarta.persistence.Table")
            ?: psiClass.getAnnotation("javax.persistence.Table")

        if (tableAnnotation != null) {
            val nameValue = getAnnotationStringValue(tableAnnotation, "name")
            if (!nameValue.isNullOrBlank()) {
                return nameValue
            }
        }

        // Check for @Entity(name = "...")
        val entityAnnotation = psiClass.getAnnotation("jakarta.persistence.Entity")
            ?: psiClass.getAnnotation("javax.persistence.Entity")

        if (entityAnnotation != null) {
            val nameValue = getAnnotationStringValue(entityAnnotation, "name")
            if (!nameValue.isNullOrBlank()) {
                return nameValue
            }
        }

        // Default: convert class name to snake_case
        return toSnakeCase(psiClass.name ?: entityName)
    }

    open fun resolveColumnName(entityName: String, fieldPath: List<String>): String {
        if (fieldPath.isEmpty()) return ""
        if (project == null) return toSnakeCase(fieldPath.last())

        val psiClass = findEntityClass(entityName) ?: return toSnakeCase(fieldPath.last())

        // Navigate through the field path for nested properties
        var currentClass: PsiClass? = psiClass
        var currentFieldName = fieldPath.last()

        for (i in 0 until fieldPath.size - 1) {
            val field = currentClass?.findFieldByName(fieldPath[i], true)
            if (field != null) {
                currentClass = getFieldType(field)
            } else {
                break
            }
        }

        // Find the final field
        val field = currentClass?.findFieldByName(currentFieldName, true)
        if (field != null) {
            // Check for @Column annotation
            val columnAnnotation = field.getAnnotation("jakarta.persistence.Column")
                ?: field.getAnnotation("javax.persistence.Column")

            if (columnAnnotation != null) {
                val nameValue = getAnnotationStringValue(columnAnnotation, "name")
                if (!nameValue.isNullOrBlank()) {
                    return nameValue
                }
            }
        }

        return toSnakeCase(currentFieldName)
    }

    open fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo? {
        if (project == null) return null
        val psiClass = findEntityClass(entityName) ?: return null
        val field = psiClass.findFieldByName(fieldName, true) ?: return null

        // Check for @JoinColumn
        val joinColumnAnnotation = field.getAnnotation("jakarta.persistence.JoinColumn")
            ?: field.getAnnotation("javax.persistence.JoinColumn")

        if (joinColumnAnnotation != null) {
            val columnName = getAnnotationStringValue(joinColumnAnnotation, "name")
            val referencedColumn = getAnnotationStringValue(joinColumnAnnotation, "referencedColumnName") ?: "id"
            val targetClass = getFieldType(field)
            val targetTable = targetClass?.let { resolveTableNameFromClass(it) }

            return JoinInfo(
                columnName = columnName ?: toSnakeCase(fieldName) + "_id",
                referencedColumnName = referencedColumn,
                targetTable = targetTable ?: toSnakeCase(fieldName)
            )
        }

        // Default join info based on field name
        val targetClass = getFieldType(field)
        return JoinInfo(
            columnName = toSnakeCase(fieldName) + "_id",
            referencedColumnName = "id",
            targetTable = targetClass?.let { resolveTableNameFromClass(it) } ?: toSnakeCase(fieldName)
        )
    }

    private fun findEntityClass(entityName: String): PsiClass? {
        val proj = project ?: return null
        val facade = JavaPsiFacade.getInstance(proj)
        val scope = GlobalSearchScope.allScope(proj)

        // Try to find by fully qualified name first
        facade.findClass(entityName, scope)?.let { return it }

        // Search for class by simple name with @Entity annotation
        val shortNameClasses = facade.findClasses(entityName, scope)
        for (psiClass in shortNameClasses) {
            if (hasEntityAnnotation(psiClass)) {
                return psiClass
            }
        }

        // Search in common packages
        val commonPackages = listOf(
            "entity", "entities", "model", "models", "domain"
        )

        for (pkg in commonPackages) {
            val candidates = facade.findClasses("$pkg.$entityName", scope)
            for (psiClass in candidates) {
                if (hasEntityAnnotation(psiClass)) {
                    return psiClass
                }
            }
        }

        return null
    }

    private fun hasEntityAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("jakarta.persistence.Entity")
                || psiClass.hasAnnotation("javax.persistence.Entity")
    }

    private fun resolveTableNameFromClass(psiClass: PsiClass): String {
        val tableAnnotation = psiClass.getAnnotation("jakarta.persistence.Table")
            ?: psiClass.getAnnotation("javax.persistence.Table")

        if (tableAnnotation != null) {
            val nameValue = getAnnotationStringValue(tableAnnotation, "name")
            if (!nameValue.isNullOrBlank()) {
                return nameValue
            }
        }

        return toSnakeCase(psiClass.name ?: "unknown")
    }

    private fun getFieldType(field: PsiField): PsiClass? {
        val proj = project ?: return null
        val type = field.type
        val canonicalText = type.canonicalText

        // Handle generic types like List<Entity>
        val genericMatch = Regex("<(.+)>").find(canonicalText)
        val typeName = genericMatch?.groupValues?.get(1) ?: canonicalText

        val facade = JavaPsiFacade.getInstance(proj)
        val scope = GlobalSearchScope.allScope(proj)
        return facade.findClass(typeName, scope)
    }

    private fun getAnnotationStringValue(annotation: PsiAnnotation, attributeName: String): String? {
        val value = annotation.findAttributeValue(attributeName) ?: return null
        val text = value.text
        // Remove quotes from string literal
        return if (text.startsWith("\"") && text.endsWith("\"")) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }

    companion object {
        fun toSnakeCase(input: String): String {
            return input.replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
                .lowercase()
        }
    }
}

data class JoinInfo(
    val columnName: String,
    val referencedColumnName: String,
    val targetTable: String
)
