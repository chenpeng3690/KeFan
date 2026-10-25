package com.kefan.app.data.repository

import com.kefan.app.data.local.dao.ModelConfigDao
import com.kefan.app.data.local.entity.ModelConfig
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.remote.BalanceInfo
import com.kefan.app.data.remote.ChatCompletion
import com.kefan.app.data.remote.ConnectionInfo
import com.kefan.app.data.remote.ModelApiClient
import com.kefan.app.data.remote.StreamEvent
import com.kefan.app.data.security.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelConfigRepository @Inject constructor(
    private val dao: ModelConfigDao,
    private val keyVault: KeyVault,
    private val preferences: AppPreferences,
    private val apiClient: ModelApiClient
) {
    fun observeConfigs(): Flow<List<ModelConfig>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    val activeModelId: Flow<Long?> = preferences.activeModelId

    /** 使用中的模型：已选卡片，若未选则回退到第一张。所有任务共用。 */
    fun observeActiveConfig(): Flow<ModelConfig?> = combine(
        dao.observeAll(),
        preferences.activeModelId
    ) { configs, activeId ->
        configs.firstOrNull { it.id == activeId } ?: configs.firstOrNull()
    }

    suspend fun getById(id: Long): ModelConfig? = dao.getById(id)

    suspend fun activeConfigOnce(): ModelConfig? = observeActiveConfig().first()

    /**
     * 保存卡片。plainApiKey 为空时保留原密钥（编辑态“留空不修改”）。
     */
    suspend fun save(config: ModelConfig, plainApiKey: String?): Long {
        val alias = config.apiKeyAlias.ifBlank { UUID.randomUUID().toString() }
        val newKey = plainApiKey?.trim().orEmpty()
        if (newKey.isNotEmpty()) {
            keyVault.putKey(alias, newKey)
        }

        return if (config.id == 0L) {
            val id = dao.insert(config.copy(apiKeyAlias = alias))
            // 新卡片直接设为使用中；编辑已有卡片不改变当前使用中状态
            preferences.setActiveModelId(id)
            id
        } else {
            dao.update(config.copy(apiKeyAlias = alias, updatedAt = System.currentTimeMillis()))
            config.id
        }
    }

    suspend fun delete(config: ModelConfig) {
        dao.delete(config)
        keyVault.deleteKey(config.apiKeyAlias)

        if (preferences.activeModelId.first() == config.id) {
            val next = dao.observeAll().first().firstOrNull()
            preferences.setActiveModelId(next?.id)
        }
    }

    suspend fun setActive(id: Long) {
        preferences.setActiveModelId(id)
    }

    fun maskedKey(config: ModelConfig): String = keyVault.maskedKey(config.apiKeyAlias)

    fun apiKeyOf(config: ModelConfig): String = keyVault.getKey(config.apiKeyAlias)

    suspend fun testConnection(config: ModelConfig): ApiResult<ConnectionInfo> =
        apiClient.testConnection(
            baseUrl = config.baseUrl,
            apiKey = apiKeyOf(config),
            modelName = config.modelName,
            customHeadersJson = config.customHeadersJson,
            timeoutSeconds = config.timeoutSeconds
        )

    /** 编辑页用：还没保存的表单也能测 */
    suspend fun testDraft(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        customHeadersJson: String,
        timeoutSeconds: Int
    ): ApiResult<ConnectionInfo> = apiClient.testConnection(
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        customHeadersJson = customHeadersJson,
        timeoutSeconds = timeoutSeconds
    )

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        customHeadersJson: String,
        timeoutSeconds: Int
    ): ApiResult<List<String>> = apiClient.fetchModels(
        baseUrl = baseUrl,
        apiKey = apiKey,
        customHeadersJson = customHeadersJson,
        timeoutSeconds = timeoutSeconds
    )

    /** 查询余额：只支持 DeepSeek 官方接口，其余服务商会返回不支持提示。 */
    suspend fun fetchBalance(config: ModelConfig): ApiResult<BalanceInfo> =
        apiClient.fetchBalance(
            baseUrl = config.baseUrl,
            apiKey = apiKeyOf(config),
            customHeadersJson = config.customHeadersJson
        )

    suspend fun chatWith(
        config: ModelConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 2048,
        temperature: Double = 0.3
    ): ApiResult<ChatCompletion> = apiClient.chatCompletion(
        baseUrl = config.baseUrl,
        apiKey = apiKeyOf(config),
        modelName = config.modelName,
        customHeadersJson = config.customHeadersJson,
        timeoutSeconds = config.timeoutSeconds,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        disableThinking = config.provider == "DeepSeek"
    )

    fun streamWith(
        config: ModelConfig,
        messagesJson: String,
        maxTokens: Int = 4096,
        temperature: Double = 0.7
    ): Flow<StreamEvent> = apiClient.streamChatCompletion(
        baseUrl = config.baseUrl,
        apiKey = apiKeyOf(config),
        modelName = config.modelName,
        customHeadersJson = config.customHeadersJson,
        timeoutSeconds = maxOf(config.timeoutSeconds, 180),
        messagesJson = messagesJson,
        maxTokens = maxTokens,
        temperature = temperature,
        disableThinking = config.provider == "DeepSeek"
    )
}
