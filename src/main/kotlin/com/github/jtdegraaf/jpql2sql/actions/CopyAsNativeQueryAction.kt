package com.github.jtdegraaf.jpql2sql.actions

import com.github.jtdegraaf.jpql2sql.MyBundle
import com.github.jtdegraaf.jpql2sql.converter.EntityResolver
import com.github.jtdegraaf.jpql2sql.converter.SqlConverter
import com.github.jtdegraaf.jpql2sql.converter.dialect.getSqlDialect
import com.github.jtdegraaf.jpql2sql.parser.JpqlParseException
import com.github.jtdegraaf.jpql2sql.parser.JpqlParser
import com.github.jtdegraaf.jpql2sql.settings.JpqlToSqlSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiLiteralExpression
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

        e.presentation.isEnabledAndVisible = isQueryAnnotation
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)

        val annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation::class.java)
        if (annotation == null || !isQueryAnnotation(annotation)) {
            showNotification(
                project,
                MyBundle.message("action.copyAsNativeQuery.notOnQuery"),
                NotificationType.WARNING
            )
            return
        }

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

            // Copy to clipboard
            CopyPasteManager.getInstance().setContents(StringSelection(sql))

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

        val converter = SqlConverter(dialect, entityResolver)
        return converter.convert(ast)
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
    }
}
