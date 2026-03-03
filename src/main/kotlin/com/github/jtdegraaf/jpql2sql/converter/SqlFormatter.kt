package com.github.jtdegraaf.jpql2sql.converter

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * Formats SQL using IntelliJ's built-in SQL formatter.
 * Falls back to returning unformatted SQL if the SQL plugin is not available.
 */
object SqlFormatter {

    fun format(project: Project?, sql: String): String {
        if (project == null) {
            // No project context, return unformatted SQL
            return sql
        }
        return try {
            val sqlLanguage = Language.findLanguageByID("SQL") ?: return sql
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("temp.sql", sqlLanguage, sql)

            var formattedText = sql
            WriteCommandAction.runWriteCommandAction(project) {
                CodeStyleManager.getInstance(project).reformat(psiFile)
                formattedText = psiFile.text
            }
            formattedText
        } catch (e: Exception) {
            // If formatting fails (e.g., SQL plugin not installed), return unformatted SQL
            sql
        }
    }
}
