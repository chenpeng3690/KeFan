package com.kefan.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 模型配置卡片：一套配置 = 地址 + 密钥 + 模型名。
 * 密钥本身不落库，ModelConfig 只保存 apiKeyAlias，密文在 KeyVault。
 */
@Entity(tableName = "model_config")
data class ModelConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** DeepSeek / 阿里通义 / 月之暗面 Kimi / 智谱 GLM / OpenAI / 硅基流动 / Ollama 本地 / 自定义 */
    val provider: String = "自定义",
    val baseUrl: String = "",
    /** KeyVault 中的密钥别名 */
    val apiKeyAlias: String = "",
    val modelName: String = "",
    val supportsVision: Boolean = false,
    val contextWindow: Int = 64_000,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Int = 120,
    /** 自定义 Header JSON，如 {"X-Api-Version":"..."} */
    val customHeadersJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
