package com.github.jtdegraaf.jpql2sql.actions

import com.github.jtdegraaf.jpql2sql.MyBundle
import com.github.jtdegraaf.jpql2sql.converter.EntityResolver
import com.github.jtdegraaf.jpql2sql.converter.SqlConverter
import com.github.jtdegraaf.jpql2sql.converter.dialect.getSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParseException
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import com.github.jtdegraaf.jpql2sql.parser.JpqlQuery
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryAstBuilder
import com.github.jtdegraaf.jpql2sql.repository.DerivedQueryParser
import com.github.jtdegraaf.jpql2sql.settings.JpqlToSqlSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection

class CopyAsNativeQueryAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)

        // Check if we're on a @Query annotation
        val annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation::class.java)
        val isQueryAnnotation = annotation != null && isQueryAnnotation(annotation)

        // Check if we're on a derived query method
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        val isDerivedQueryMethod = method != null && isDerivedQueryMethod(method)

        e.presentation.isEnabledAndVisible = isQueryAnnotation || isDerivedQueryMethod
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)

        // First check for @Query annotation
        val annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation::class.java)
        if (annotation != null && isQueryAnnotation(annotation)) {
            handleQueryAnnotation(project, annotation)
            return
        }

        // Check for derived query method
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (method != null && isDerivedQueryMethod(method)) {
            handleDerivedQueryMethod(project, method)
            return
        }

        showNotification(
            project,
            MyBundle.message("action.copyAsNativeQuery.notOnQuery"),
            NotificationType.WARNING
        )
    }

    private fun handleQueryAnnotation(project: com.intellij.openapi.project.Project, annotation: PsiAnnotation) {
        try {
            val jpql = extractJpqlFromAnnotation(annotation)
            if (jpql.isNullOrBlank()) {
                showNotification(
                    project,
                    MyBundle.message("action.copyAsNativeQuery.error", "Empty query"),
                    NotificationType.ERROR
                )
                return
            }

            val sql = convertJpqlToSql(project, jpql)
            copyToClipboard(sql)

            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.success"),
                NotificationType.INFORMATION
            )

        } catch (ex: JpqlParseException) {
            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.error", ex.message ?: "Parse error"),
                NotificationType.ERROR
            )
        } catch (ex: Exception) {
            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.error", ex.message ?: "Unknown error"),
                NotificationType.ERROR
            )
        }
    }

    private fun handleDerivedQueryMethod(project: com.intellij.openapi.project.Project, method: PsiMethod) {
        try {
            val entityName = extractEntityFromRepository(method.containingClass)
            if (entityName == null) {
                showNotification(
                    project,
                    MyBundle.message("action.copyAsNativeQuery.error", "Could not determine entity type"),
                    NotificationType.ERROR
                )
                return
            }

            val parser = DerivedQueryParser()
            val components = parser.parse(method.name, entityName)
            if (components == null) {
                showNotification(
                    project,
                    MyBundle.message("action.copyAsNativeQuery.error", "Could not parse method name"),
                    NotificationType.ERROR
                )
                return
            }

            val ast = DerivedQueryAstBuilder().build(components)
            val sql = convertAstToSql(project, ast)
            copyToClipboard(sql)

            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.success"),
                NotificationType.INFORMATION
            )

        } catch (ex: Exception) {
            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.error", ex.message ?: "Unknown error"),
                NotificationType.ERROR
            )
        }
    }

    private fun isDerivedQueryMethod(method: PsiMethod): Boolean {
        // Must be in an interface
        val containingClass = method.containingClass ?: return false
        if (!containingClass.isInterface) return false

        // Must not have @Query annotation
        if (method.hasAnnotation("org.springframework.data.jpa.repository.Query")) return false

        // Must be in a JPA repository
        if (!isJpaRepository(containingClass)) return false

        // Method name must match derived query pattern
        val methodName = method.name
        return DERIVED_QUERY_PREFIXES.any { methodName.startsWith(it) }
    }

    private fun isJpaRepository(psiClass: PsiClass): Boolean {
        val visited = mutableSetOf<String>()
        return checkExtendsJpaRepository(psiClass, visited)
    }

    private fun checkExtendsJpaRepository(psiClass: PsiClass, visited: MutableSet<String>): Boolean {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null) {
            if (qualifiedName in JPA_REPOSITORY_INTERFACES) return true
            if (qualifiedName in visited) return false
            visited.add(qualifiedName)
        }

        // Check superinterfaces
        for (superInterface in psiClass.interfaces) {
            if (checkExtendsJpaRepository(superInterface, visited)) return true
        }

        // Check superclass
        psiClass.superClass?.let {
            if (checkExtendsJpaRepository(it, visited)) return true
        }

        return false
    }

    private fun extractEntityFromRepository(psiClass: PsiClass?): String? {
        if (psiClass == null) return null

        // Look for generic type parameter in extends clause
        for (superType in psiClass.extendsListTypes) {
            val entityType = extractEntityFromSuperType(superType)
            if (entityType != null) return entityType
        }

        // Check implemented interfaces
        for (implementsType in psiClass.implementsListTypes) {
            val entityType = extractEntityFromSuperType(implementsType)
            if (entityType != null) return entityType
        }

        return null
    }

    private fun extractEntityFromSuperType(type: PsiClassType): String? {
        val resolvedClass = type.resolve() ?: return null
        val qualifiedName = resolvedClass.qualifiedName

        // Check if this is a JPA repository type
        if (qualifiedName in JPA_REPOSITORY_INTERFACES) {
            // Get the first type parameter (entity type)
            val typeParameters = type.parameters
            if (typeParameters.isNotEmpty()) {
                val entityType = typeParameters[0]
                if (entityType is PsiClassType) {
                    return entityType.resolve()?.name
                }
            }
        }

        // Recursively check parent interfaces
        for (superType in resolvedClass.extendsListTypes) {
            val entityType = extractEntityFromSuperType(superType)
            if (entityType != null) return entityType
        }

        return null
    }

    private fun isQueryAnnotation(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        return qualifiedName in QUERY_ANNOTATION_NAMES
    }

    private fun extractJpqlFromAnnotation(annotation: PsiAnnotation): String? {
        // Try "value" attribute first
        var value = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue(null) // unnamed attribute

        if (value == null) {
            return null
        }

        // Handle string literal
        if (value is PsiLiteralExpression) {
            return value.value as? String
        }

        // Handle string concatenation or reference
        val text = value.text
        return if (text.startsWith("\"") && text.endsWith("\"")) {
            text.substring(1, text.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        } else {
            text
        }
    }

    private fun convertJpqlToSql(project: com.intellij.openapi.project.Project, jpql: String): String {
        val settings = JpqlToSqlSettings.getInstance()
        val dialect = getSqlDialect(settings.dialect)
        val entityResolver = EntityResolver(project)

        val parser = JpqlParser(jpql)
        val ast = parser.parse()

        val converter = SqlConverter(dialect, entityResolver, project)
        return converter.convert(ast)
    }

    private fun convertAstToSql(project: com.intellij.openapi.project.Project, ast: JpqlQuery): String {
        val settings = JpqlToSqlSettings.getInstance()
        val dialect = getSqlDialect(settings.dialect)
        val entityResolver = EntityResolver(project)

        val converter = SqlConverter(dialect, entityResolver, project)
        return converter.convert(ast)
    }

    private fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun showNotification(
        project: com.intellij.openapi.project.Project,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JPQL to SQL Notifications")
            .createNotification(content, type)
            .notify(project)
    }

    companion object {
        private val QUERY_ANNOTATION_NAMES = setOf(
            "org.springframework.data.jpa.repository.Query",
            "jakarta.persistence.NamedQuery",
            "javax.persistence.NamedQuery",
            "jakarta.persistence.Query",
            "javax.persistence.Query"
        )

        private val JPA_REPOSITORY_INTERFACES = setOf(
            "org.springframework.data.jpa.repository.JpaRepository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.reactive.ReactiveCrudRepository",
            "org.springframework.data.repository.reactive.ReactiveSortingRepository"
        )

        private val DERIVED_QUERY_PREFIXES = listOf(
            "find", "read", "get", "query", "search", "stream",
            "count", "exists", "delete", "remove"
        )
    }
}
