package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiAnnotation

/**
 * Helper for resolving `@AttributeOverride` column name mappings.
 *
 * Used by multiple resolvers (e.g. [EmbeddedResolver], [ColumnAnnotationResolver])
 * to check if a parent `@Embedded` field has overridden the column name.
 */
object AttributeOverrideHelper {

    /**
     * Search the given overrides for one matching `fieldName` and return its
     * `@Column(name = ...)` value.
     */
    fun findOverrideColumn(fieldName: String, overrides: List<PsiAnnotation>): String? {
        for (override in overrides) {
            val overrideName = PsiUtils.getAnnotationStringValue(override, "name")
            if (overrideName == fieldName) {
                val columnAnnotation = override.findAttributeValue("column")
                if (columnAnnotation is PsiAnnotation) {
                    val colName = PsiUtils.getAnnotationStringValue(columnAnnotation, "name")
                    if (!colName.isNullOrBlank()) return colName
                }
            }
        }
        return null
    }
}

