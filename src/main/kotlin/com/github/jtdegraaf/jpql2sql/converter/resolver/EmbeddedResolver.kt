package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMember

/**
 * Resolves `@Embedded` / `@EmbeddedId` fields.
 *
 * When a field is annotated with `@Embedded` or `@EmbeddedId`, this resolver
 * collects any `@AttributeOverride` / `@AttributeOverrides` declared on it
 * and delegates resolution into the embedded type.
 */
class EmbeddedResolver : ColumnResolver {

    override fun resolve(context: ColumnResolverContext, chain: ColumnResolverChain): ColumnResolution {
        if (!isEmbedded(context.members)) return ColumnResolution.Unhandled

        val embeddedClass = PsiUtils.resolveMemberType(context.members, context.project)
        val overrides = collectAttributeOverrides(context.members)

        return if (context.remainingPath.isNotEmpty()) {
            val columnName = chain.resolve(
                embeddedClass,
                context.remainingPath,
                context.parentAttributeOverrides + overrides
            )
            ColumnResolution.Resolved(columnName)
        } else {
            ColumnResolution.Resolved(NamingUtils.toSnakeCase(context.fieldName))
        }
    }

    private fun isEmbedded(members: List<PsiMember>): Boolean {
        return PsiUtils.hasAnyAnnotation(members, JpaAnnotations.EMBEDDED)
    }

    private fun collectAttributeOverrides(members: List<PsiMember>): List<PsiAnnotation> {
        val result = mutableListOf<PsiAnnotation>()
        for (member in members) {
            for (fqn in JpaAnnotations.ATTRIBUTE_OVERRIDE) {
                PsiUtils.getAnnotation(member, fqn)?.let { result.add(it) }
            }
            for (fqn in JpaAnnotations.ATTRIBUTE_OVERRIDES) {
                val container = PsiUtils.getAnnotation(member, fqn) ?: continue
                val value = container.findAttributeValue("value")
                if (value is com.intellij.psi.PsiArrayInitializerMemberValue) {
                    for (initializer in value.initializers) {
                        if (initializer is PsiAnnotation) result.add(initializer)
                    }
                } else if (value is PsiAnnotation) {
                    result.add(value)
                }
            }
        }
        return result
    }
}

