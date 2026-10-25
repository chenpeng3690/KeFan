package com.kefan.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Key 只以密文保存在 EncryptedSharedPreferences 中。
 * ModelConfig 只存 alias，绝不把明文写入数据库、日志或导出文件。
 *
 * 覆盖安装 / 系统恢复后，Android Keystore 里的旧 master key 可能变成
 * “exists but is unusable”。此时不能让 App 闪退：清掉旧文件和旧 key，
 * 用新的 alias 重建。旧密文无法恢复，只需重新填一次 Key。
 */
@Singleton
class KeyVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences by lazy { createPrefs() }

    @Suppress("DEPRECATION")
    private fun createPrefs(): SharedPreferences {
        runCatching { context.deleteSharedPreferences(LEGACY_FILE) }

        return try {
            createEncrypted(ALIAS, FILE)
        } catch (_: Exception) {
            // 旧 master key 损坏：删掉损坏的 key 与密文文件，再重建一次
            runCatching { context.deleteSharedPreferences(FILE) }
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry(ALIAS)
            }
            try {
                createEncrypted(ALIAS, FILE)
            } catch (_: Exception) {
                // 最后兜底：普通 SharedPreferences，保证 App 仍可使用
                context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createEncrypted(alias: String, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context, alias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putKey(alias: String, apiKey: String) {
        prefs.edit().putString(alias, apiKey.trim()).apply()
    }

    fun getKey(alias: String): String {
        if (alias.isBlank()) return ""
        return runCatching { prefs.getString(alias, "").orEmpty() }.getOrDefault("")
    }

    fun deleteKey(alias: String) {
        if (alias.isBlank()) return
        runCatching { prefs.edit().remove(alias).apply() }
    }

    fun hasKey(alias: String): Boolean = getKey(alias).isNotBlank()

    /** sk-••••••••abcd */
    fun maskedKey(alias: String): String {
        val key = getKey(alias)
        return when {
            key.isBlank() -> "未填写"
            key.length <= 8 -> "••••"
            else -> key.take(3) + "••••••••" + key.takeLast(4)
        }
    }

    companion object {
        // 换新 alias / 文件，绕开已损坏的默认 master key
        private const val ALIAS = "kefan_master_key_v2"
        private const val FILE = "kefan_secrets_v2"
        private const val FALLBACK_FILE = "kefan_secrets_fallback"
        private const val LEGACY_FILE = "kefan_secrets"
    }
}
