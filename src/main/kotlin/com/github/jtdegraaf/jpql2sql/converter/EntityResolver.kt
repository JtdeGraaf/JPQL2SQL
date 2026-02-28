package com.github.jtdegraaf.jpql2sql.converter

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

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
        return resolveColumnNameFromClass(psiClass, fieldPath, emptyList())
    }

    /**
     * Recursively resolve a field path to its SQL column name, handling:
     * - @Column on fields and getters
     * - @JoinColumn on @ManyToOne / @OneToOne associations (single-valued)
     * - @Embedded / @EmbeddedId with @AttributeOverride
     * - Nested embedded paths
     */
    private fun resolveColumnNameFromClass(
        psiClass: PsiClass?,
        fieldPath: List<String>,
        parentAttributeOverrides: List<PsiAnnotation>
    ): String {
        if (psiClass == null || fieldPath.isEmpty()) return fieldPath.lastOrNull()?.let { toSnakeCase(it) } ?: ""

        val currentFieldName = fieldPath[0]
        val remainingPath = fieldPath.drop(1)

        // Find the field — try direct lookup first, then scan all fields (including supertypes)
        val field = psiClass.findFieldByName(currentFieldName, true)
            ?: psiClass.allFields.firstOrNull { it.name == currentFieldName }
        val getter = findGetter(psiClass, currentFieldName)

        // The "annotated member" is either the field or getter – we check both for annotations
        val annotatedMembers: List<PsiMember> = listOfNotNull(field, getter)

        // --- Handle @Embedded / @EmbeddedId ---
        if (isEmbedded(annotatedMembers)) {
            val embeddedClass = field?.let { getFieldType(it) } ?: getter?.let { getMethodReturnType(it) }

            // Collect @AttributeOverride(s) declared on this embedded field/getter
            val overrides = collectAttributeOverrides(annotatedMembers)

            return if (remainingPath.isNotEmpty()) {
                // Navigate into the embedded type
                resolveColumnNameFromClass(embeddedClass, remainingPath, parentAttributeOverrides + overrides)
            } else {
                // Path ends at the embedded field itself – not typical but return snake_case
                toSnakeCase(currentFieldName)
            }
        }

        // --- Handle @ManyToOne / @OneToOne (single-valued association) ---
        if (isSingleValuedAssociation(annotatedMembers)) {
            if (remainingPath.isEmpty()) {
                // e.g. "u.department" with no further path → resolve to the FK column name
                val joinColName = findJoinColumnName(annotatedMembers)
                if (joinColName != null) return joinColName

                // Check parent @AttributeOverride (unlikely for associations but be safe)
                val overrideName = findAttributeOverrideColumn(currentFieldName, parentAttributeOverrides)
                if (overrideName != null) return overrideName

                return toSnakeCase(currentFieldName) + "_id"
            } else {
                // e.g. "u.department.name" → navigate into the target entity
                val targetClass = field?.let { getFieldType(it) } ?: getter?.let { getMethodReturnType(it) }
                return resolveColumnNameFromClass(targetClass, remainingPath, emptyList())
            }
        }

        // --- Leaf field (or last segment) ---
        if (remainingPath.isEmpty()) {
            // 1) Check parent @AttributeOverride (from an enclosing @Embedded)
            val overrideName = findAttributeOverrideColumn(currentFieldName, parentAttributeOverrides)
            if (overrideName != null) return overrideName

            // 2) Check @Column on field or getter
            val columnName = findColumnAnnotationName(annotatedMembers)
            if (columnName != null) return columnName

            // 3) Check @JoinColumn (field might be a FK without @ManyToOne – rare but possible)
            val joinColName = findJoinColumnName(annotatedMembers)
            if (joinColName != null) return joinColName

            // 4) Default: snake_case
            return toSnakeCase(currentFieldName)
        }

        // --- Navigate into a nested type (shouldn't normally happen unless it's an embedded without annotation) ---
        val nextClass = field?.let { getFieldType(it) } ?: getter?.let { getMethodReturnType(it) }
        return resolveColumnNameFromClass(nextClass, remainingPath, parentAttributeOverrides)
    }

    // ---- Annotation detection helpers ----

    private fun isEmbedded(members: List<PsiMember>): Boolean {
        return members.any { member ->
            hasAnyAnnotation(member, EMBEDDED_ANNOTATIONS)
        }
    }

    private fun isSingleValuedAssociation(members: List<PsiMember>): Boolean {
        return members.any { member ->
            hasAnyAnnotation(member, SINGLE_VALUED_ASSOCIATION_ANNOTATIONS)
        }
    }

    private fun hasAnyAnnotation(member: PsiMember, annotations: List<String>): Boolean {
        return annotations.any { fqn ->
            when (member) {
                is PsiField -> member.hasAnnotation(fqn)
                is PsiMethod -> member.hasAnnotation(fqn)
                else -> false
            }
        }
    }

    private fun getAnnotation(member: PsiMember, fqn: String): PsiAnnotation? {
        return when (member) {
            is PsiField -> member.getAnnotation(fqn)
            is PsiMethod -> member.getAnnotation(fqn)
            else -> null
        }
    }

    /**
     * Find the column name from @Column annotation on any of the given members.
     */
    private fun findColumnAnnotationName(members: List<PsiMember>): String? {
        for (member in members) {
            for (fqn in COLUMN_ANNOTATIONS) {
                val annotation = getAnnotation(member, fqn)
                if (annotation != null) {
                    val nameValue = getAnnotationStringValue(annotation, "name")
                    if (!nameValue.isNullOrBlank()) return nameValue
                }
            }
        }
        return null
    }

    /**
     * Find the column name from @JoinColumn annotation on any of the given members.
     */
    private fun findJoinColumnName(members: List<PsiMember>): String? {
        for (member in members) {
            for (fqn in JOIN_COLUMN_ANNOTATIONS) {
                val annotation = getAnnotation(member, fqn)
                if (annotation != null) {
                    val nameValue = getAnnotationStringValue(annotation, "name")
                    if (!nameValue.isNullOrBlank()) return nameValue
                }
            }
            // Also check @JoinColumns (plural) containing multiple @JoinColumn — return the first
            for (fqn in JOIN_COLUMNS_ANNOTATIONS) {
                val annotation = getAnnotation(member, fqn)
                if (annotation != null) {
                    val inner = getFirstNestedAnnotationValue(annotation, "value", "name")
                    if (!inner.isNullOrBlank()) return inner
                }
            }
        }
        return null
    }

    /**
     * Collect all @AttributeOverride annotations from the given members.
     * Handles both singular @AttributeOverride and plural @AttributeOverrides.
     */
    private fun collectAttributeOverrides(members: List<PsiMember>): List<PsiAnnotation> {
        val result = mutableListOf<PsiAnnotation>()
        for (member in members) {
            for (fqn in ATTRIBUTE_OVERRIDE_ANNOTATIONS) {
                getAnnotation(member, fqn)?.let { result.add(it) }
            }
            for (fqn in ATTRIBUTE_OVERRIDES_ANNOTATIONS) {
                val container = getAnnotation(member, fqn)
                if (container != null) {
                    val value = container.findAttributeValue("value")
                    if (value is com.intellij.psi.PsiArrayInitializerMemberValue) {
                        for (initializer in value.initializers) {
                            if (initializer is PsiAnnotation) {
                                result.add(initializer)
                            }
                        }
                    } else if (value is PsiAnnotation) {
                        result.add(value)
                    }
                }
            }
        }
        return result
    }

    /**
     * Check if any @AttributeOverride in the list overrides the given field name,
     * and return the column name from its nested @Column.
     */
    private fun findAttributeOverrideColumn(fieldName: String, overrides: List<PsiAnnotation>): String? {
        for (override in overrides) {
            val overrideName = getAnnotationStringValue(override, "name")
            if (overrideName == fieldName) {
                val columnAnnotation = override.findAttributeValue("column")
                if (columnAnnotation is PsiAnnotation) {
                    val colName = getAnnotationStringValue(columnAnnotation, "name")
                    if (!colName.isNullOrBlank()) return colName
                }
            }
            // Also handle dotted paths in @AttributeOverride(name = "address.street", ...)
            if (overrideName != null && overrideName.startsWith("$fieldName.")) {
                // This override is for a nested path inside fieldName — skip here,
                // it will be matched when we recurse deeper
                continue
            }
        }
        return null
    }

    private fun findGetter(psiClass: PsiClass, fieldName: String): PsiMethod? {
        val getterName = "get" + fieldName.replaceFirstChar { it.uppercase() }
        return psiClass.findMethodsByName(getterName, true).firstOrNull()
            ?: run {
                // Also try "is" prefix for booleans
                val isGetterName = "is" + fieldName.replaceFirstChar { it.uppercase() }
                psiClass.findMethodsByName(isGetterName, true).firstOrNull()
            }
    }

    private fun getMethodReturnType(method: PsiMethod): PsiClass? {
        val proj = project ?: return null
        val returnType = method.returnType ?: return null
        val canonicalText = returnType.canonicalText

        // Handle generic types like List<Entity>, Set<Entity>, Optional<Entity>
        val genericMatch = Regex("<(.+?)>").find(canonicalText)
        val typeName = genericMatch?.groupValues?.get(1) ?: canonicalText

        val facade = JavaPsiFacade.getInstance(proj)
        val scope = GlobalSearchScope.allScope(proj)
        return facade.findClass(typeName, scope)
    }

    open fun resolveJoinTable(entityName: String, fieldName: String): JoinInfo? {
        if (project == null) return null
        val psiClass = findEntityClass(entityName) ?: return null
        val field = psiClass.findFieldByName(fieldName, true)
        val getter = findGetter(psiClass, fieldName)
        val annotatedMembers: List<PsiMember> = listOfNotNull(field, getter)

        if (annotatedMembers.isEmpty()) return null

        // --- @JoinTable (for @ManyToMany) ---
        val joinTableInfo = resolveJoinTableAnnotation(annotatedMembers, fieldName, field, getter)
        if (joinTableInfo != null) return joinTableInfo

        // --- @JoinColumn ---
        val joinColName = findJoinColumnName(annotatedMembers)
        val referencedCol = findJoinColumnReferencedName(annotatedMembers) ?: "id"
        val targetClass = field?.let { getFieldType(it) } ?: getter?.let { getMethodReturnType(it) }
        val targetTable = targetClass?.let { resolveTableNameFromClass(it) }

        if (joinColName != null) {
            return JoinInfo(
                columnName = joinColName,
                referencedColumnName = referencedCol,
                targetTable = targetTable ?: toSnakeCase(fieldName),
                joinTable = null,
                inverseColumnName = null
            )
        }

        // Default join info based on field name
        return JoinInfo(
            columnName = toSnakeCase(fieldName) + "_id",
            referencedColumnName = "id",
            targetTable = targetClass?.let { resolveTableNameFromClass(it) } ?: toSnakeCase(fieldName),
            joinTable = null,
            inverseColumnName = null
        )
    }

    /**
     * Resolve @JoinTable annotation for @ManyToMany relationships.
     */
    private fun resolveJoinTableAnnotation(
        members: List<PsiMember>,
        fieldName: String,
        field: PsiField?,
        getter: PsiMethod?
    ): JoinInfo? {
        for (member in members) {
            for (fqn in JOIN_TABLE_ANNOTATIONS) {
                val annotation = getAnnotation(member, fqn) ?: continue

                val tableName = getAnnotationStringValue(annotation, "name")
                    ?: (toSnakeCase(fieldName) + "_mapping")

                val joinColumnName = getFirstNestedAnnotationValue(
                    annotation, "joinColumns", "name"
                ) ?: "id"

                val inverseJoinColumnName = getFirstNestedAnnotationValue(
                    annotation, "inverseJoinColumns", "name"
                ) ?: (toSnakeCase(fieldName) + "_id")

                val targetClass = field?.let { getFieldType(it) } ?: getter?.let { getMethodReturnType(it) }
                val targetTable = targetClass?.let { resolveTableNameFromClass(it) } ?: toSnakeCase(fieldName)

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

    /**
     * Find referencedColumnName from @JoinColumn.
     */
    private fun findJoinColumnReferencedName(members: List<PsiMember>): String? {
        for (member in members) {
            for (fqn in JOIN_COLUMN_ANNOTATIONS) {
                val annotation = getAnnotation(member, fqn)
                if (annotation != null) {
                    val value = getAnnotationStringValue(annotation, "referencedColumnName")
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return null
    }

    /**
     * Get the value of a named attribute from the first nested annotation in an array attribute.
     * e.g. for @JoinTable(joinColumns = @JoinColumn(name = "x")), this extracts "x".
     */
    private fun getFirstNestedAnnotationValue(
        annotation: PsiAnnotation,
        arrayAttribute: String,
        nestedAttribute: String
    ): String? {
        val value = annotation.findAttributeValue(arrayAttribute) ?: return null
        val firstAnnotation: PsiAnnotation? = when (value) {
            is com.intellij.psi.PsiArrayInitializerMemberValue -> {
                value.initializers.firstOrNull() as? PsiAnnotation
            }
            is PsiAnnotation -> value
            else -> null
        }
        if (firstAnnotation != null) {
            return getAnnotationStringValue(firstAnnotation, nestedAttribute)
        }
        return null
    }

    private fun findEntityClass(entityName: String): PsiClass? {
        val proj = project ?: return null
        val facade = JavaPsiFacade.getInstance(proj)
        val scope = GlobalSearchScope.allScope(proj)

        // Try to find by fully qualified name first
        facade.findClass(entityName, scope)?.let {
            if (hasEntityAnnotation(it)) return it
        }

        // Use PsiShortNamesCache to find classes by short name
        val shortNamesCache = PsiShortNamesCache.getInstance(proj)
        val classesByShortName = shortNamesCache.getClassesByName(entityName, scope)

        for (psiClass in classesByShortName) {
            if (hasEntityAnnotation(psiClass)) {
                return psiClass
            }
        }

        // Also check for @Entity(name = "entityName") where the class name differs
        // This requires scanning all entity classes
        val entityAnnotations = listOf(
            "jakarta.persistence.Entity",
            "javax.persistence.Entity"
        )

        for (annotationFqn in entityAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, scope) ?: continue

            com.intellij.psi.search.searches.AnnotatedElementsSearch
                .searchPsiClasses(annotationClass, scope)
                .forEach { psiClass ->
                    // Check if @Entity(name = "entityName")
                    val entityAnnotation = psiClass.getAnnotation(annotationFqn)
                    if (entityAnnotation != null) {
                        val nameValue = getAnnotationStringValue(entityAnnotation, "name")
                        if (nameValue == entityName) {
                            return psiClass
                        }
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

        // Handle generic types like List<Entity>, Set<Entity>, Optional<Entity>
        val genericMatch = Regex("<(.+?)>").find(canonicalText)
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

        // Annotation FQN constants for both jakarta and javax namespaces

        private val COLUMN_ANNOTATIONS = listOf(
            "jakarta.persistence.Column",
            "javax.persistence.Column"
        )

        private val JOIN_COLUMN_ANNOTATIONS = listOf(
            "jakarta.persistence.JoinColumn",
            "javax.persistence.JoinColumn"
        )

        private val JOIN_COLUMNS_ANNOTATIONS = listOf(
            "jakarta.persistence.JoinColumns",
            "javax.persistence.JoinColumns"
        )

        private val JOIN_TABLE_ANNOTATIONS = listOf(
            "jakarta.persistence.JoinTable",
            "javax.persistence.JoinTable"
        )

        private val EMBEDDED_ANNOTATIONS = listOf(
            "jakarta.persistence.Embedded",
            "javax.persistence.Embedded",
            "jakarta.persistence.EmbeddedId",
            "javax.persistence.EmbeddedId"
        )

        private val ATTRIBUTE_OVERRIDE_ANNOTATIONS = listOf(
            "jakarta.persistence.AttributeOverride",
            "javax.persistence.AttributeOverride"
        )

        private val ATTRIBUTE_OVERRIDES_ANNOTATIONS = listOf(
            "jakarta.persistence.AttributeOverrides",
            "javax.persistence.AttributeOverrides"
        )

        private val SINGLE_VALUED_ASSOCIATION_ANNOTATIONS = listOf(
            "jakarta.persistence.ManyToOne",
            "javax.persistence.ManyToOne",
            "jakarta.persistence.OneToOne",
            "javax.persistence.OneToOne"
        )
    }
}

data class JoinInfo(
    val columnName: String,
    val referencedColumnName: String,
    val targetTable: String,
    val joinTable: String? = null,
    val inverseColumnName: String? = null
)
