package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiMember

/**
 * Resolves leaf fields using `@Column(name = ...)`.
 *
 * This is the default resolver for simple fields. It also checks for
 * `@AttributeOverride` from a parent `@Embedded` and bare `@JoinColumn`
 * (for rare cases where a FK field has no `@ManyToOne`).
 *
 * Only applies when there is no remaining path (i.e. the field is a leaf).
 */
class ColumnAnnotationResolver : ColumnResolver {

    override fun resolve(context: ColumnResolverContext, chain: ColumnResolverChain): ColumnResolution {
        if (context.remainingPath.isNotEmpty()) return ColumnResolution.Unhandled

        // 1) Check parent @AttributeOverride (from an enclosing @Embedded)
        val overrideName = AttributeOverrideHelper.findOverrideColumn(
            context.fieldName, context.parentAttributeOverrides
        )
        if (overrideName != null) return ColumnResolution.Resolved(overrideName)

        // 2) Check @Column(name = "...")
        val columnName = findColumnName(context.members)
        if (columnName != null) return ColumnResolution.Resolved(columnName)

        // 3) Check @JoinColumn (field might be a FK without @ManyToOne)
        val joinColName = JoinColumnResolver.findJoinColumnName(context.members)
        if (joinColName != null) return ColumnResolution.Resolved(joinColName)

        // 4) Default: snake_case
        return ColumnResolution.Resolved(NamingUtils.toSnakeCase(context.fieldName))
    }

    private fun findColumnName(members: List<PsiMember>): String? {
        for (member in members) {
            for (fqn in JpaAnnotations.COLUMN) {
                val annotation = PsiUtils.getAnnotation(member, fqn)
                if (annotation != null) {
                    val name = PsiUtils.getAnnotationStringValue(annotation, "name")
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return null
    }
}

