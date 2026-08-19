package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ScriptRepository: Handles persistent storage of Auto Click scripts
 * with reactive StateFlow updates across the whole application.
 */
class ScriptRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("autoclicker_scripts_prefs", Context.MODE_PRIVATE)

    private val _scriptsFlow = MutableStateFlow<List<AutoClickScript>>(emptyList())
    val scriptsFlow: StateFlow<List<AutoClickScript>> = _scriptsFlow.asStateFlow()

    private val _activeScriptFlow = MutableStateFlow<AutoClickScript>(
        AutoClickScript.getDefaultPresets().first()
    )
    val activeScriptFlow: StateFlow<AutoClickScript> = _activeScriptFlow.asStateFlow()

    init {
        loadScriptsFromStorage()
    }

    private fun loadScriptsFromStorage() {
        val jsonString = prefs.getString(KEY_SCRIPTS_LIST, null)
        val activeId = prefs.getString(KEY_ACTIVE_SCRIPT_ID, null)

        val list = mutableListOf<AutoClickScript>()
        if (!jsonString.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    list.add(AutoClickScript.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            list.addAll(AutoClickScript.getDefaultPresets())
            saveScriptsToStorage(list)
        }

        _scriptsFlow.value = list

        val active = list.find { it.id == activeId } ?: list.first()
        _activeScriptFlow.value = active
    }

    private fun saveScriptsToStorage(scripts: List<AutoClickScript>) {
        val array = JSONArray()
        scripts.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SCRIPTS_LIST, array.toString()).apply()
    }

    suspend fun saveOrUpdateScript(script: AutoClickScript) = withContext(Dispatchers.IO) {
        val currentList = _scriptsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == script.id }
        script.updatedAt = System.currentTimeMillis()

        if (index >= 0) {
            currentList[index] = script
        } else {
            currentList.add(0, script)
        }

        saveScriptsToStorage(currentList)
        _scriptsFlow.value = currentList

        if (_activeScriptFlow.value.id == script.id) {
            _activeScriptFlow.value = script
        }
    }

    suspend fun deleteScript(scriptId: String) = withContext(Dispatchers.IO) {
        val currentList = _scriptsFlow.value.toMutableList()
        currentList.removeAll { it.id == scriptId }

        if (currentList.isEmpty()) {
            currentList.addAll(AutoClickScript.getDefaultPresets())
        }

        saveScriptsToStorage(currentList)
        _scriptsFlow.value = currentList

        if (_activeScriptFlow.value.id == scriptId) {
            val newActive = currentList.first()
            setActiveScript(newActive)
        }
    }

    fun setActiveScript(script: AutoClickScript) {
        _activeScriptFlow.value = script
        prefs.edit().putString(KEY_ACTIVE_SCRIPT_ID, script.id).apply()
    }

    fun getActiveScript(): AutoClickScript {
        return _activeScriptFlow.value
    }

    companion object {
        private const val KEY_SCRIPTS_LIST = "key_scripts_list"
        private const val KEY_ACTIVE_SCRIPT_ID = "key_active_script_id"

        @Volatile
        private var INSTANCE: ScriptRepository? = null

        fun getInstance(context: Context): ScriptRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScriptRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
