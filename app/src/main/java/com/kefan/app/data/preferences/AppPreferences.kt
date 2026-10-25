package com.kefan.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.kefanDataStore: DataStore<Preferences> by preferencesDataStore(name = "kefan_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        // 沿用 primary_model_id，保证升级后已选模型不丢
        val ACTIVE_MODEL_ID = longPreferencesKey("primary_model_id")
        val KEEP_EMOJI = booleanPreferencesKey("copy_keep_emoji")
        val STRICT_FACT_CHECK = booleanPreferencesKey("strict_fact_check")
        val SENSITIVE_WORDS = stringPreferencesKey("sensitive_words_json")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
    }

    /** 使用中的模型；null = 还没选（回退到第一张卡片） */
    val activeModelId: Flow<Long?> = context.kefanDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_MODEL_ID]?.takeIf { it > 0 }
    }

    val copyKeepEmoji: Flow<Boolean> = context.kefanDataStore.data.map { it[Keys.KEEP_EMOJI] ?: false }
    val strictFactCheck: Flow<Boolean> = context.kefanDataStore.data.map { it[Keys.STRICT_FACT_CHECK] ?: false }

    /** 用户自定义敏感词库；null 表示使用内置词库 */
    val sensitiveWordsJson: Flow<String?> = context.kefanDataStore.data.map { it[Keys.SENSITIVE_WORDS] }

    /** 回收站保留天数，默认 7 天 */
    val trashRetentionDays: Flow<Int> = context.kefanDataStore.data.map {
        it[Keys.TRASH_RETENTION_DAYS] ?: 7
    }

    suspend fun setActiveModelId(id: Long?) {
        context.kefanDataStore.edit { prefs ->
            if (id == null || id <= 0) prefs.remove(Keys.ACTIVE_MODEL_ID)
            else prefs[Keys.ACTIVE_MODEL_ID] = id
        }
    }

    suspend fun setKeepEmoji(keep: Boolean) {
        context.kefanDataStore.edit { it[Keys.KEEP_EMOJI] = keep }
    }

    suspend fun setStrictFactCheck(enabled: Boolean) {
        context.kefanDataStore.edit { it[Keys.STRICT_FACT_CHECK] = enabled }
    }

    suspend fun setSensitiveWordsJson(json: String?) {
        context.kefanDataStore.edit { prefs ->
            if (json == null) prefs.remove(Keys.SENSITIVE_WORDS)
            else prefs[Keys.SENSITIVE_WORDS] = json
        }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.kefanDataStore.edit { it[Keys.TRASH_RETENTION_DAYS] = days }
    }
}
