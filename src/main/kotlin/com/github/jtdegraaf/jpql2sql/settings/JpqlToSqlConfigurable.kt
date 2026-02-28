package com.github.jtdegraaf.jpql2sql.settings

import com.github.jtdegraaf.jpql2sql.MyBundle
import com.github.jtdegraaf.jpql2sql.converter.dialect.SqlDialectType
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class JpqlToSqlConfigurable : Configurable {

    private var selectedDialect: SqlDialectType = SqlDialectType.POSTGRESQL

    override fun getDisplayName(): String = MyBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val settings = JpqlToSqlSettings.getInstance()
        selectedDialect = settings.dialect

        return panel {
            row(MyBundle.message("settings.dialect.label")) {
                comboBox(SqlDialectType.entries)
                    .applyToComponent {
                        selectedItem = selectedDialect
                        addActionListener {
                            selectedDialect = selectedItem as SqlDialectType
                        }
                    }
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = JpqlToSqlSettings.getInstance()
        return selectedDialect != settings.dialect
    }

    override fun apply() {
        val settings = JpqlToSqlSettings.getInstance()
        settings.dialect = selectedDialect
    }

    override fun reset() {
        val settings = JpqlToSqlSettings.getInstance()
        selectedDialect = settings.dialect
    }
}
