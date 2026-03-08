package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod

/**
 * Resolves `@JoinTable` for `@ManyToMany` relationships, and `@JoinColumn` for
 * `@ManyToOne` / `@OneToOne` relationships, producing [JoinInfo] used when
 * generating SQL JOIN clauses.
 */
class JoinInfoResolver(
    private val project: Project,
    private val entityFinder: EntityFinder,
    private val tableResolver: TableResolver
) {

    fun resolve(entityName: String, fieldName: String): JoinInfo? {
        val psiClass = entityFinder.findEntityClass(entityName) ?: return null
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return null

        val field = members.filterIsInstance<PsiField>().firstOrNull()
        val getter = members.filterIsInstance<PsiMethod>().firstOrNull()

        // Check @JoinTable first (for @ManyToMany)
        resolveJoinTable(members, fieldName, field, getter)?.let { return it }

        // Check @OneToMany - resolve target table from collection generic type
        if (PsiUtils.hasAnyAnnotation(members, JpaAnnotations.ONE_TO_MANY)) {
            val targetTable = resolveTargetTable(members)
            if (targetTable != null) {
                return JoinInfo(
                    columnName = FkNamingUtils.entityFkColumnName(entityName),
                    referencedColumnName = FkNamingUtils.DEFAULT_REFERENCED_COLUMN,
                    targetTable = targetTable,
                    joinTable = null,
                    inverseColumnName = null,
                    isOneToMany = true
                )
            }
        }

        // Check @JoinColumn
        val joinColName = JoinColumnResolver.findJoinColumnName(members)
        val referencedCol = JoinColumnResolver.findReferencedColumnName(members) ?: FkNamingUtils.DEFAULT_REFERENCED_COLUMN
        val targetTable = resolveTargetTable(members)

        if (joinColName != null) {
            return JoinInfo(
                columnName = joinColName,
                referencedColumnName = referencedCol,
                targetTable = targetTable ?: NamingUtils.toSnakeCase(fieldName),
                joinTable = null,
                inverseColumnName = null
            )
        }

        // Default
        return JoinInfo(
            columnName = FkNamingUtils.defaultFkColumnName(fieldName),
            referencedColumnName = FkNamingUtils.DEFAULT_REFERENCED_COLUMN,
            targetTable = targetTable ?: NamingUtils.toSnakeCase(fieldName),
            joinTable = null,
            inverseColumnName = null
        )
    }

    private fun resolveJoinTable(
        members: List<PsiMember>,
        fieldName: String,
        field: PsiField?,
        getter: PsiMethod?
    ): JoinInfo? {
        for (member in members) {
            for (fqn in JpaAnnotations.JOIN_TABLE) {
                val annotation = PsiUtils.getAnnotation(member, fqn) ?: continue

                val tableName = PsiUtils.getAnnotationStringValue(annotation, "name")
                    ?: (NamingUtils.toSnakeCase(fieldName) + "_mapping")

                val joinColumnName = PsiUtils.getFirstNestedAnnotationValue(
                    annotation, "joinColumns", "name"
                ) ?: FkNamingUtils.DEFAULT_REFERENCED_COLUMN

                val inverseJoinColumnName = PsiUtils.getFirstNestedAnnotationValue(
                    annotation, "inverseJoinColumns", "name"
                ) ?: FkNamingUtils.defaultFkColumnName(fieldName)

                val targetTable = resolveTargetTableFromFieldOrGetter(field, getter)
                    ?: NamingUtils.toSnakeCase(fieldName)

                return JoinInfo(
                    columnName = joinColumnName,
                    referencedColumnName = "id",
                    targetTable = targetTable,
                    joinTable = tableName,
                    inverseColumnName = inverseJoinColumnName
                )
            }
        }
        return null
    }

    private fun resolveTargetTable(members: List<PsiMember>): String? {
        val targetClass = PsiUtils.resolveMemberType(members, project) ?: return null
        return tableResolver.resolve(targetClass)
    }

    private fun resolveTargetTableFromFieldOrGetter(field: PsiField?, getter: PsiMethod?): String? {
        val members = listOfNotNull<PsiMember>(field, getter)
        return resolveTargetTable(members)
    }
}

data class JoinInfo(
    val columnName: String,
    val referencedColumnName: String,
    val targetTable: String,
    val joinTable: String? = null,
    val inverseColumnName: String? = null,
    val isOneToMany: Boolean = false
)

