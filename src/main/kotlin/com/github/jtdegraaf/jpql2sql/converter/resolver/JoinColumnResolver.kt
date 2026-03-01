package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiMember

/**
 * Resolves `@ManyToOne` / `@OneToOne` (single-valued association) fields.
 *
 * When the path ends at the association field (e.g. `u.department`), the resolver
 * returns the `@JoinColumn(name = ...)` value or defaults to `field_name_id`.
 *
 * When the path continues (e.g. `u.department.name`), it navigates into the
 * target entity class.
 */
class JoinColumnResolver : ColumnResolver {

    override fun resolve(context: ColumnResolverContext, chain: ColumnResolverChain): ColumnResolution {
        if (!isSingleValuedAssociation(context.members)) return ColumnResolution.Unhandled

        // Get the FK column name
        val joinColName = findJoinColumnName(context.members)
            ?: AttributeOverrideHelper.findOverrideColumn(context.fieldName, context.parentAttributeOverrides)
            ?: (NamingUtils.toSnakeCase(context.fieldName) + "_id")

        if (context.remainingPath.isEmpty()) {
            // Path ends at the association → resolve to the FK column
            return ColumnResolution.Resolved(joinColName)
        }

        // Special case: accessing .id on a FK relationship (e.g., p.bot.id)
        // This is equivalent to accessing the FK column itself (p.bot_id)
        if (context.remainingPath == listOf("id")) {
            return ColumnResolution.Resolved(joinColName)
        }

        // Path continues into the target entity → navigate
        val targetClass = PsiUtils.resolveMemberType(context.members, context.project)
        val columnName = chain.resolve(targetClass, context.remainingPath, emptyList())
        return ColumnResolution.Resolved(columnName)
    }

    private fun isSingleValuedAssociation(members: List<PsiMember>): Boolean {
        return PsiUtils.hasAnyAnnotation(members, JpaAnnotations.SINGLE_VALUED_ASSOCIATION)
    }

    companion object {
        /**
         * Extract the column name from `@JoinColumn(name = ...)` or `@JoinColumns`.
         * Also used by [JoinInfoResolver].
         */
        fun findJoinColumnName(members: List<PsiMember>): String? {
            for (member in members) {
                for (fqn in JpaAnnotations.JOIN_COLUMN) {
                    val annotation = PsiUtils.getAnnotation(member, fqn)
                    if (annotation != null) {
                        val name = PsiUtils.getAnnotationStringValue(annotation, "name")
                        if (!name.isNullOrBlank()) return name
                    }
                }
                for (fqn in JpaAnnotations.JOIN_COLUMNS) {
                    val annotation = PsiUtils.getAnnotation(member, fqn)
                    if (annotation != null) {
                        val inner = PsiUtils.getFirstNestedAnnotationValue(annotation, "value", "name")
                        if (!inner.isNullOrBlank()) return inner
                    }
                }
            }
            return null
        }

        /**
         * Extract `referencedColumnName` from `@JoinColumn`.
         */
        fun findReferencedColumnName(members: List<PsiMember>): String? {
            for (member in members) {
                for (fqn in JpaAnnotations.JOIN_COLUMN) {
                    val annotation = PsiUtils.getAnnotation(member, fqn)
                    if (annotation != null) {
                        val value = PsiUtils.getAnnotationStringValue(annotation, "referencedColumnName")
                        if (!value.isNullOrBlank()) return value
                    }
                }
            }
            return null
        }
    }
}

