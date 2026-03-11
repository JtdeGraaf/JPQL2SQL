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
class JoinInfoResolver @JvmOverloads constructor(
    private val project: Project,
    private val entityFinder: EntityFinder,
    private val tableResolver: TableResolver,
    private val pkColumnResolver: (String) -> String = { "id" }
) {

    fun resolve(entityName: String, fieldName: String): JoinInfo? {
        val psiClass = entityFinder.findEntityClass(entityName) ?: return null
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return null

        val field = members.filterIsInstance<PsiField>().firstOrNull()
        val getter = members.filterIsInstance<PsiMethod>().firstOrNull()

        // Check @JoinTable first (for @ManyToMany)
        resolveJoinTable(members, fieldName, field, getter, entityName)?.let { return it }

        // Check @OneToMany - resolve target table from collection generic type
        if (PsiUtils.hasAnyAnnotation(members, JpaAnnotations.ONE_TO_MANY)) {
            val targetTable = resolveTargetTable(members)
            if (targetTable != null) {
                return JoinInfo(
                    columnName = FkNamingUtils.entityFkColumnName(entityName),
                    referencedColumnName = pkColumnResolver(entityName),
                    targetTable = targetTable,
                    joinTable = null,
                    inverseColumnName = null,
                    isOneToMany = true
                )
            }
        }

        // Resolve target entity for PK column lookup
        val targetClass = PsiUtils.resolveMemberType(members, project)
        val targetEntityName = targetClass?.name
        val targetPkColumn = if (targetEntityName != null) pkColumnResolver(targetEntityName) else "id"

        // Check @JoinColumn
        val joinColName = JoinColumnResolver.findJoinColumnName(members)
        val referencedCol = JoinColumnResolver.findReferencedColumnName(members) ?: targetPkColumn
        val targetTable = if (targetClass != null) tableResolver.resolve(targetClass) else null

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
            referencedColumnName = targetPkColumn,
            targetTable = targetTable ?: NamingUtils.toSnakeCase(fieldName),
            joinTable = null,
            inverseColumnName = null
        )
    }

    private fun resolveJoinTable(
        members: List<PsiMember>,
        fieldName: String,
        field: PsiField?,
        getter: PsiMethod?,
        parentEntityName: String
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

                // Resolve the target entity's PK column
                val targetClass = PsiUtils.resolveMemberType(members, project)
                val targetEntityName = targetClass?.name
                val targetPkColumn = if (targetEntityName != null) pkColumnResolver(targetEntityName) else "id"

                return JoinInfo(
                    columnName = joinColumnName,
                    referencedColumnName = targetPkColumn,
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

