package com.kefan.app.domain.model

/**
 * 常见 OpenAI 兼容服务商的预设。选预设只是帮用户填默认值，所有字段都可手改。
 */
data class AiProviderPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val contextWindow: Int,
    val supportsVision: Boolean
)

object AiProviderPresets {
    const val CUSTOM = "自定义"

    val all: List<AiProviderPreset> = listOf(
        AiProviderPreset(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-flash",
            contextWindow = 1_000_000,
            supportsVision = true
        ),
        AiProviderPreset(
            name = "阿里通义",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            contextWindow = 131_072,
            supportsVision = false
        ),
        AiProviderPreset(
            name = "月之暗面 Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            contextWindow = 8_192,
            supportsVision = false
        ),
        AiProviderPreset(
            name = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            contextWindow = 131_072,
            supportsVision = false
        ),
        AiProviderPreset(
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            contextWindow = 128_000,
            supportsVision = true
        ),
        AiProviderPreset(
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            contextWindow = 32_768,
            supportsVision = false
        ),
        AiProviderPreset(
            name = "Ollama 本地",
            baseUrl = "http://192.168.1.100:11434/v1",
            defaultModel = "qwen2.5:7b",
            contextWindow = 32_768,
            supportsVision = false
        ),
        AiProviderPreset(
            name = CUSTOM,
            baseUrl = "",
            defaultModel = "",
            contextWindow = 64_000,
            supportsVision = false
        )
    )

    fun byName(name: String): AiProviderPreset =
        all.firstOrNull { it.name == name } ?: all.last()
}
