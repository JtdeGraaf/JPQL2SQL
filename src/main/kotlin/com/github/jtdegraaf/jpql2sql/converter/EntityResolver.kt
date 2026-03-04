package com.github.jtdegraaf.jpql2sql.converter

import com.github.jtdegraaf.jpql2sql.converter.resolver.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass

/**
 * Facade that resolves JPA entity/field names to SQL table/column names.
 *
 * Delegates to specialised resolver classes:
 * - [EntityFinder]           — locates `@Entity` classes by name
 * - [TableResolver]          — `@Table` / `@Entity` → table name
 * - [EmbeddedResolver]       — `@Embedded` / `@EmbeddedId` + `@AttributeOverride`
 * - [JoinColumnResolver]     — `@ManyToOne` / `@OneToOne` + `@JoinColumn`
 * - [ColumnAnnotationResolver] — `@Column`
 * - [JoinInfoResolver]       — `@JoinTable` / `@JoinColumn` for JOIN clauses
 */
open class EntityResolver(private val project: Project?) {

    private val entityFinder: EntityFinder? = project?.let { EntityFinder(it) }
    private val tableResolver = TableResolver()
    private val joinInfoResolver: JoinInfoResolver? = project?.let {
        JoinInfoResolver(it, entityFinder!!, tableResolver)
    }

    /** Ordered chain of column resolvers — first match wins. */
    private val columnResolvers: List<ColumnResolver> = listOf(
        EmbeddedResolver(),
        JoinColumnResolver(),
        ColumnAnnotationResolver()
    )

    // ---- Public API used by SqlConverter ----

    open fun resolveTableName(entityName: String): String {
        val psiClass = findEntity(entityName) ?: return NamingUtils.toSnakeCase(entityName)
        return tableResolver.resolve(psiClass)
    }

    open fun resolveColumnName(entityName: String, fieldPath: List<String>): String {
        if (fieldPath.isEmpty()) return ""
        if (project == null) return NamingUtils.toSnakeCase(fieldPath.last())

        val psiClass = findEntity(entityName) ?: return NamingUtils.toSnakeCase(fieldPath.last())
        return resolveColumnFromClass(psiClass, fieldPath, emptyList())
    }

    open fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo? {
        return joinInfoResolver?.resolve(entityName, fieldName)
    }

    /**
     * Resolves the target entity name for a relationship field.
     * For example, for `Match.participants` (which is `List<MatchParticipant>`),
     * this returns `"MatchParticipant"`.
     *
     * @return the target entity's simple class name, or `null` if not resolvable
     */
    open fun resolveTargetEntityName(parentEntityName: String, fieldName: String): String? {
        if (project == null) return null
        val psiClass = findEntity(parentEntityName) ?: return null
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return null
        val targetClass = PsiUtils.resolveMemberType(members, project!!) ?: return null
        return targetClass.name
    }

    /**
     * Checks if a field is a relationship (association) that requires a JOIN to access its properties.
     *
     * This includes:
     * - `@ManyToOne` / `@OneToOne` (single-valued associations)
     * - `@OneToMany` / `@ManyToMany` (collection-valued associations)
     *
     * Does NOT include `@Embedded` fields (which don't require JOINs).
     *
     * @return true if the field is a relationship, false otherwise
     */
    open fun isRelationshipField(entityName: String, fieldName: String): Boolean {
        if (project == null) return false
        val psiClass = findEntity(entityName) ?: return false
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return false

        return PsiUtils.hasAnyAnnotation(members, JpaAnnotations.SINGLE_VALUED_ASSOCIATION) ||
                PsiUtils.hasAnyAnnotation(members, JpaAnnotations.ONE_TO_MANY) ||
                PsiUtils.hasAnyAnnotation(members, JpaAnnotations.MANY_TO_MANY)
    }

    /**
     * Checks if a field is an embedded field (`@Embedded` or `@EmbeddedId`).
     * Embedded fields don't require JOINs - their properties map to columns in the same table.
     */
    open fun isEmbeddedField(entityName: String, fieldName: String): Boolean {
        if (project == null) return false
        val psiClass = findEntity(entityName) ?: return false
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return false

        return PsiUtils.hasAnyAnnotation(members, JpaAnnotations.EMBEDDED)
    }

    /**
     * Checks if a field is the primary key of an entity (`@Id` or `@EmbeddedId`).
     *
     * @return true if the field has `@Id` or `@EmbeddedId` annotation
     */
    open fun isPrimaryKeyField(entityName: String, fieldName: String): Boolean {
        if (project == null) return false
        val psiClass = findEntity(entityName) ?: return false
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)
        if (members.isEmpty()) return false

        return PsiUtils.hasAnyAnnotation(members, JpaAnnotations.ID)
    }

    // ---- Internal resolution logic ----

    private fun findEntity(entityName: String): PsiClass? = entityFinder?.findEntityClass(entityName)

    /**
     * Walk the field path through the resolver chain.
     * This method is passed as the [ColumnResolverChain] callback so individual
     * resolvers can recurse (e.g. navigate into an embedded type).
     */
    private fun resolveColumnFromClass(
        psiClass: PsiClass?,
        fieldPath: List<String>,
        parentOverrides: List<PsiAnnotation>
    ): String {
        if (psiClass == null || fieldPath.isEmpty()) {
            return fieldPath.lastOrNull()?.let { NamingUtils.toSnakeCase(it) } ?: ""
        }

        val proj = project ?: return NamingUtils.toSnakeCase(fieldPath.last())
        val fieldName = fieldPath[0]
        val remaining = fieldPath.drop(1)
        val members = PsiUtils.findAnnotatedMembers(psiClass, fieldName)

        val context = ColumnResolverContext(
            psiClass = psiClass,
            fieldName = fieldName,
            remainingPath = remaining,
            members = members,
            parentAttributeOverrides = parentOverrides,
            project = proj
        )

        val chain = ColumnResolverChain { nextClass, nextPath, nextOverrides ->
            resolveColumnFromClass(nextClass, nextPath, nextOverrides)
        }

        for (resolver in columnResolvers) {
            when (val result = resolver.resolve(context, chain)) {
                is ColumnResolution.Resolved -> return result.columnName
                is ColumnResolution.Unhandled -> continue
            }
        }

        // No resolver handled it — navigate into the type if there's remaining path
        if (remaining.isNotEmpty()) {
            val nextClass = PsiUtils.resolveMemberType(members, proj)
            return resolveColumnFromClass(nextClass, remaining, parentOverrides)
        }

        return NamingUtils.toSnakeCase(fieldName)
    }

    companion object {
        /** Kept for backward compatibility with [SqlConverter]. */
        fun toSnakeCase(input: String): String = NamingUtils.toSnakeCase(input)
    }
}
