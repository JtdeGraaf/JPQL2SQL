package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember

/**
 * Context passed through the column resolution chain.
 */
data class ColumnResolverContext(
    val psiClass: PsiClass,
    val fieldName: String,
    val remainingPath: List<String>,
    val members: List<PsiMember>,
    val parentAttributeOverrides: List<com.intellij.psi.PsiAnnotation>,
    val project: Project
)

/**
 * Result of a column resolution attempt.
 */
sealed class ColumnResolution {
    /** The resolver handled the field and produced a final column name. */
    data class Resolved(val columnName: String) : ColumnResolution()

    /** The resolver did not handle this field; pass to the next resolver. */
    data object Unhandled : ColumnResolution()
}

/**
 * Strategy interface for resolving a JPA field to its SQL column name.
 *
 * Each implementation handles a specific annotation pattern (e.g. `@Column`, `@JoinColumn`,
 * `@Embedded`). Implementations are tried in order; the first one that returns
 * [ColumnResolution.Resolved] wins.
 */
interface ColumnResolver {

    /**
     * Attempt to resolve the column name for the given context.
     *
     * @param context  the current field context
     * @param chain    callback to continue resolution (e.g. navigate into an embedded type)
     */
    fun resolve(context: ColumnResolverContext, chain: ColumnResolverChain): ColumnResolution
}

/**
 * Callback that lets a [ColumnResolver] delegate further resolution
 * (e.g. navigating into an embedded class or association target).
 */
fun interface ColumnResolverChain {
    fun resolve(
        psiClass: PsiClass?,
        fieldPath: List<String>,
        parentAttributeOverrides: List<com.intellij.psi.PsiAnnotation>
    ): String
}

