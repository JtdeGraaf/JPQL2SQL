package com.github.jtdegraaf.jpql2sql.converter.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Shared PSI utility functions for reading annotations, resolving types, and finding members.
 */
object PsiUtils {

    fun findField(psiClass: PsiClass, fieldName: String): PsiField? {
        return psiClass.findFieldByName(fieldName, true)
            ?: psiClass.allFields.firstOrNull { it.name == fieldName }
    }

    fun findGetter(psiClass: PsiClass, fieldName: String): PsiMethod? {
        val getterName = "get" + fieldName.replaceFirstChar { it.uppercase() }
        return psiClass.findMethodsByName(getterName, true).firstOrNull()
            ?: run {
                val isGetterName = "is" + fieldName.replaceFirstChar { it.uppercase() }
                psiClass.findMethodsByName(isGetterName, true).firstOrNull()
            }
    }

    fun findAnnotatedMembers(psiClass: PsiClass, fieldName: String): List<PsiMember> {
        val field = findField(psiClass, fieldName)
        val getter = findGetter(psiClass, fieldName)
        return listOfNotNull(field, getter)
    }

    fun hasAnyAnnotation(member: PsiMember, fqns: List<String>): Boolean {
        return fqns.any { fqn ->
            when (member) {
                is PsiField -> member.hasAnnotation(fqn)
                is PsiMethod -> member.hasAnnotation(fqn)
                else -> false
            }
        }
    }

    fun hasAnyAnnotation(members: List<PsiMember>, fqns: List<String>): Boolean {
        return members.any { hasAnyAnnotation(it, fqns) }
    }

    fun getAnnotation(member: PsiMember, fqn: String): PsiAnnotation? {
        return when (member) {
            is PsiField -> member.getAnnotation(fqn)
            is PsiMethod -> member.getAnnotation(fqn)
            is PsiClass -> member.getAnnotation(fqn)
            else -> null
        }
    }

    fun findAnnotation(members: List<PsiMember>, fqns: List<String>): PsiAnnotation? {
        for (member in members) {
            for (fqn in fqns) {
                getAnnotation(member, fqn)?.let { return it }
            }
        }
        return null
    }

    fun getAnnotationStringValue(annotation: PsiAnnotation, attributeName: String): String? {
        val value = annotation.findAttributeValue(attributeName) ?: return null
        val text = value.text
        return if (text.startsWith("\"") && text.endsWith("\"")) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }

    /**
     * Gets the simple name of an enum value from an annotation attribute.
     * For example, for `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`,
     * calling this with "strategy" returns "SINGLE_TABLE".
     */
    fun getAnnotationEnumValue(annotation: PsiAnnotation, attributeName: String): String? {
        val value = annotation.findAttributeValue(attributeName) ?: return null
        val text = value.text
        // Handle qualified names like "InheritanceType.SINGLE_TABLE" -> "SINGLE_TABLE"
        return text.substringAfterLast(".")
    }

    /**
     * Get the value of a named attribute from the first nested annotation in an array attribute.
     * e.g. for `@JoinTable(joinColumns = @JoinColumn(name = "x"))`, this extracts `"x"`.
     */
    fun getFirstNestedAnnotationValue(
        annotation: PsiAnnotation,
        arrayAttribute: String,
        nestedAttribute: String
    ): String? {
        val value = annotation.findAttributeValue(arrayAttribute) ?: return null
        val firstAnnotation: PsiAnnotation? = when (value) {
            is PsiArrayInitializerMemberValue -> value.initializers.firstOrNull() as? PsiAnnotation
            is PsiAnnotation -> value
            else -> null
        }
        return firstAnnotation?.let { getAnnotationStringValue(it, nestedAttribute) }
    }

    fun resolveFieldType(field: PsiField, project: Project): PsiClass? {
        return resolveType(field.type, project)
    }

    fun resolveMethodReturnType(method: PsiMethod, project: Project): PsiClass? {
        return resolveType(method.returnType ?: return null, project)
    }

    fun resolveMemberType(members: List<PsiMember>, project: Project): PsiClass? {
        for (member in members) {
            when (member) {
                is PsiField -> resolveFieldType(member, project)?.let { return it }
                is PsiMethod -> resolveMethodReturnType(member, project)?.let { return it }
            }
        }
        return null
    }

    /**
     * Resolves a [PsiType] to its [PsiClass].
     * For generic collection types like `List<Entity>`, resolves the type argument instead.
     */
    private fun resolveType(type: PsiType, project: Project): PsiClass? {
        if (type is PsiClassType) {
            // If the type has generic parameters (e.g. List<Entity>), resolve the first type argument
            val parameters = type.parameters
            if (parameters.isNotEmpty()) {
                val innerType = parameters[0]
                if (innerType is PsiClassType) {
                    innerType.resolve()?.let { return it }
                }
            }
            // Otherwise resolve the type itself
            return type.resolve()
        }
        // Fallback: try string-based resolution
        return resolveClassName(type.canonicalText, project)
    }

    private fun resolveClassName(canonicalText: String, project: Project): PsiClass? {
        // Handle generic types like List<Entity>, Set<Entity>, Optional<Entity>
        val genericMatch = Regex("<(.+?)>").find(canonicalText)
        val typeName = genericMatch?.groupValues?.get(1) ?: canonicalText

        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        return facade.findClass(typeName, scope)
    }
}


