package com.github.jtdegraaf.jpql2sql.settings

import com.github.jtdegraaf.jpql2sql.converter.dialect.SqlDialectType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "JpqlToSqlSettings",
    storages = [Storage("JpqlToSqlSettings.xml")]
)
class JpqlToSqlSettings : PersistentStateComponent<JpqlToSqlSettings.State> {

    private var myState = State()

    var dialect: SqlDialectType
        get() = myState.dialect
        set(value) {
            myState.dialect = value
        }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    data class State(
        var dialect: SqlDialectType = SqlDialectType.POSTGRESQL
    )

    companion object {
        fun getInstance(): JpqlToSqlSettings =
            ApplicationManager.getApplication().getService(JpqlToSqlSettings::class.java)
    }
}
