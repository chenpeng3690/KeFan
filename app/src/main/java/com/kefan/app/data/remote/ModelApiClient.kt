package com.kefan.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionInfo(val latencyMs: Long, val reply: String)

data class BalanceInfo(
    val available: Boolean,
    val currency: String,
    val total: String,
    val granted: String,
    val toppedUp: String
)

data class ChatCompletion(
    val content: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Done(val promptTokens: Int = 0, val completionTokens: Int = 0) : StreamEvent
    data class Error(val message: String) : StreamEvent
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String, val code: Int? = null) : ApiResult<Nothing>
}

/**
 * OpenAI 兼容协议的最小客户端：连通性测试、模型列表、非流式调用。
 * 流式生成在第 3 步补充。
 */
@Singleton
class ModelApiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        customHeadersJson: String = "{}",
        timeoutSeconds: Int = 60
    ): ApiResult<ConnectionInfo> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext ApiResult.Failure("请先填写 Base URL")
        if (modelName.isBlank()) return@withContext ApiResult.Failure("请先选择或填写模型名")

        val requestBody = buildJsonObject {
            put("model", modelName)
            put("stream", false)
            put("max_tokens", 16)
            put("temperature", 0.0)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "ping")
                }
            }
        }.toString()

        val request = try {
            buildRequest(
                url = endpoint(baseUrl, "chat/completions"),
                apiKey = apiKey,
                customHeadersJson = customHeadersJson
            )
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            return@withContext ApiResult.Failure("Base URL 格式不正确：${e.message.orEmpty()}")
        }

        val started = System.nanoTime()
        try {
            clientWithTimeout(timeoutSeconds).newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val latency = (System.nanoTime() - started) / 1_000_000
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        humanError(response.code, extractErrorMessage(text)),
                        response.code
                    )
                }
                val reply = runCatching { parseReply(text) }.getOrDefault("")
                ApiResult.Success(ConnectionInfo(latency, reply.ifBlank { "连接成功" }))
            }
        } catch (e: IOException) {
            ApiResult.Failure("网络连接失败，请检查网络或 Base URL：${e.message.orEmpty()}")
        } catch (e: Exception) {
            ApiResult.Failure("请求失败：${e.message.orEmpty()}")
        }
    }

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        customHeadersJson: String = "{}",
        timeoutSeconds: Int = 60
    ): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext ApiResult.Failure("请先填写 Base URL")

        val request = try {
            buildRequest(
                url = endpoint(baseUrl, "models"),
                apiKey = apiKey,
                customHeadersJson = customHeadersJson
            ).get().build()
        } catch (e: Exception) {
            return@withContext ApiResult.Failure("Base URL 格式不正确：${e.message.orEmpty()}")
        }

        try {
            clientWithTimeout(timeoutSeconds).newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        humanError(response.code, extractErrorMessage(text)),
                        response.code
                    )
                }
                val models = runCatching {
                    json.parseToJsonElement(text).jsonObject["data"]
                        ?.jsonArray
                        ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.sorted()
                        ?: emptyList()
                }.getOrDefault(emptyList())

                if (models.isEmpty()) {
                    ApiResult.Failure("没有拉到可用模型，请确认接口是否为 OpenAI 兼容协议")
                } else {
                    ApiResult.Success(models)
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure("网络连接失败，请检查网络或 Base URL：${e.message.orEmpty()}")
        } catch (e: Exception) {
            ApiResult.Failure("请求失败：${e.message.orEmpty()}")
        }
    }

    /**
     * 查询账户余额。仅 DeepSeek 官方接口提供 /user/balance，其他服务商直接提示不支持。
     */
    suspend fun fetchBalance(
        baseUrl: String,
        apiKey: String,
        customHeadersJson: String = "{}",
        timeoutSeconds: Int = 30
    ): ApiResult<BalanceInfo> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ApiResult.Failure("请先配置 API Key")
        val url = balanceEndpoint(baseUrl)
            ?: return@withContext ApiResult.Failure("当前服务商不支持查询余额，仅 DeepSeek 官方接口可用")

        val request = try {
            buildRequest(url, apiKey, customHeadersJson).get().build()
        } catch (e: Exception) {
            return@withContext ApiResult.Failure("Base URL 格式不正确：${e.message.orEmpty()}")
        }

        try {
            clientWithTimeout(timeoutSeconds).newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = if (response.code == 404) {
                        "该服务商没有余额接口（404），目前仅 DeepSeek 官方支持查询"
                    } else {
                        humanError(response.code, extractErrorMessage(text))
                    }
                    return@withContext ApiResult.Failure(message, response.code)
                }
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: return@withContext ApiResult.Failure("余额返回格式无法解析")
                val info = root["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withContext ApiResult.Failure("没有返回余额信息")
                ApiResult.Success(
                    BalanceInfo(
                        available = root["is_available"]?.jsonPrimitive?.booleanOrNull ?: true,
                        currency = info["currency"]?.jsonPrimitive?.contentOrNull ?: "CNY",
                        total = info["total_balance"]?.jsonPrimitive?.contentOrNull ?: "0.00",
                        granted = info["granted_balance"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        toppedUp = info["topped_up_balance"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                )
            }
        } catch (e: IOException) {
            ApiResult.Failure("网络连接失败，请检查网络：${e.message.orEmpty()}")
        } catch (e: Exception) {
            ApiResult.Failure("请求失败：${e.message.orEmpty()}")
        }
    }

    /**
     * 非流式对话。模板提取、事实核查等一次性任务使用。
     */
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        customHeadersJson: String = "{}",
        timeoutSeconds: Int = 120,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 2048,
        temperature: Double = 0.3,
        disableThinking: Boolean = false
    ): ApiResult<ChatCompletion> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext ApiResult.Failure("请先配置 Base URL")
        if (modelName.isBlank()) return@withContext ApiResult.Failure("请先配置模型名")

        val requestBody = buildJsonObject {
            put("model", modelName)
            put("stream", false)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            if (disableThinking) {
                putJsonObject("thinking") { put("type", "disabled") }
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                }
            }
        }.toString()

        val request = try {
            buildRequest(
                url = endpoint(baseUrl, "chat/completions"),
                apiKey = apiKey,
                customHeadersJson = customHeadersJson
            )
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            return@withContext ApiResult.Failure("Base URL 格式不正确：${e.message.orEmpty()}")
        }

        try {
            clientWithTimeout(timeoutSeconds).newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        humanError(response.code, extractErrorMessage(text)),
                        response.code
                    )
                }

                val root = runCatching { json.parseToJsonElement(text).jsonObject }
                    .getOrNull()
                    ?: return@withContext ApiResult.Failure("模型返回格式无法解析")
                val message = root["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                val content = message?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                if (content.isNullOrBlank()) {
                    return@withContext ApiResult.Failure("模型返回了空内容")
                }

                val usage = root["usage"]?.jsonObject
                ApiResult.Success(
                    ChatCompletion(
                        content = content,
                        promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                        completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    )
                )
            }
        } catch (e: IOException) {
            ApiResult.Failure("网络连接失败，请检查网络或 Base URL：${e.message.orEmpty()}")
        } catch (e: Exception) {
            ApiResult.Failure("请求失败：${e.message.orEmpty()}")
        }
    }

    /**
     * SSE 流式生成。messagesJson 由上层按 OpenAI 兼容格式拼好（可含图片）。
     */
    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        customHeadersJson: String = "{}",
        timeoutSeconds: Int = 180,
        messagesJson: String,
        maxTokens: Int = 4096,
        temperature: Double = 0.7,
        disableThinking: Boolean = false
    ): Flow<StreamEvent> = callbackFlow {
        if (baseUrl.isBlank() || modelName.isBlank()) {
            trySend(StreamEvent.Error("请先配置 Base URL 和模型名"))
            close()
            return@callbackFlow
        }

        val requestBody = buildJsonObject {
            put("model", modelName)
            put("stream", true)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            if (disableThinking) {
                putJsonObject("thinking") { put("type", "disabled") }
            }
            put("messages", json.parseToJsonElement(messagesJson))
        }.toString()

        val request = try {
            buildRequest(
                url = endpoint(baseUrl, "chat/completions"),
                apiKey = apiKey,
                customHeadersJson = customHeadersJson
            )
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            trySend(StreamEvent.Error("Base URL 格式不正确：${e.message.orEmpty()}"))
            close()
            return@callbackFlow
        }

        val call = clientWithTimeout(timeoutSeconds).newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val text = response.body?.string().orEmpty()
                        trySend(
                            StreamEvent.Error(
                                humanError(response.code, extractErrorMessage(text))
                            )
                        )
                        close()
                        return@use
                    }

                    val source = response.body?.source()
                    if (source == null) {
                        trySend(StreamEvent.Error("模型没有返回内容"))
                        close()
                        return@use
                    }

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") {
                            if (isActive) trySend(StreamEvent.Done())
                            close()
                            return@use
                        }
                        val delta = runCatching { parseStreamDelta(data) }.getOrNull()
                        if (!delta.isNullOrEmpty()) {
                            trySend(StreamEvent.Delta(delta))
                        }
                    }
                    if (isActive) trySend(StreamEvent.Done())
                    close()
                }
            } catch (e: IOException) {
                if (isActive) {
                    trySend(StreamEvent.Error("网络连接中断：${e.message.orEmpty()}"))
                }
                close()
            } catch (e: Exception) {
                if (isActive) {
                    trySend(StreamEvent.Error("生成失败：${e.message.orEmpty()}"))
                }
                close()
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    private fun parseStreamDelta(data: String): String? {
        val root = json.parseToJsonElement(data).jsonObject
        val delta = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("delta")
            ?.jsonObject
        return delta?.get("content")?.jsonPrimitive?.contentOrNull
    }

    private fun buildRequest(
        url: String,
        apiKey: String,
        customHeadersJson: String
    ): Request.Builder {
        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        builder.header("Content-Type", "application/json")
        parseHeaders(customHeadersJson).forEach { (name, value) ->
            if (name.isNotBlank()) builder.header(name, value)
        }
        return builder
    }

    private fun clientWithTimeout(timeoutSeconds: Int): OkHttpClient {
        val seconds = timeoutSeconds.coerceIn(5, 300).toLong()
        return baseClient.newBuilder()
            .callTimeout(seconds, TimeUnit.SECONDS)
            .readTimeout(seconds, TimeUnit.SECONDS)
            .writeTimeout(seconds, TimeUnit.SECONDS)
            .build()
    }

    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val cleanPath = path.trimStart('/')
        return if (base.endsWith("/$cleanPath")) base else "$base/$cleanPath"
    }

    private fun balanceEndpoint(baseUrl: String): String? {
        val base = baseUrl.trim()
        if (base.isBlank()) return null
        return runCatching {
            val uri = URI(base.trimEnd('/'))
            val host = uri.host.orEmpty().lowercase()
            if (host != "api.deepseek.com" && !host.endsWith(".deepseek.com")) return null
            val scheme = uri.scheme ?: "https"
            "$scheme://${uri.authority}/user/balance"
        }.getOrNull()
    }

    private fun parseHeaders(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.entries.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseReply(text: String): String {
        val root = json.parseToJsonElement(text).jsonObject
        val message = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
        return message?.get("content")?.jsonPrimitive?.contentOrNull
            ?: message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
            ?: ""
    }

    private fun extractErrorMessage(body: String): String {
        val fromJson = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return fromJson ?: body.take(180)
    }

    private fun humanError(code: Int, detail: String): String = when (code) {
        401 -> "Key 无效（401）"
        402 -> "余额不足（402）"
        429 -> "限流（429），稍后再试"
        404 -> "接口或模型名不存在（404），可点「拉取模型」查看可用模型"
        in 500..599 -> "服务端错误（$code）"
        else -> "请求失败（$code）${if (detail.isNotBlank()) "：$detail" else ""}"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
