package com.kefan.app.data.repository

import android.net.Uri
import com.kefan.app.data.remote.MistakeExtractionParser
import com.kefan.app.data.remote.StreamEvent
import com.kefan.app.data.remote.prompts.MistakeQuestionPrompt
import com.kefan.app.domain.model.ExtractedMistake
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MistakeExtractionSession(
    val studentId: Long,
    val feedbackId: Long,
    val imageUris: List<String>,
    val items: List<ExtractedMistake>
)

data class QuestionRecognitionOutcome(
    val items: List<ExtractedMistake> = emptyList(),
    val error: String? = null,
    val needsModel: Boolean = false
)

/**
 * 框选错题识别：调一次视觉模型，把待确认结果暂存在内存里。
 */
@Singleton
class MistakeExtractionRepository @Inject constructor(
    private val modelConfigRepository: ModelConfigRepository
) {

    private var session: MistakeExtractionSession? = null

    fun currentSession(): MistakeExtractionSession? = session

    /** 框选页识别完成后，把合并结果交给确认清单。 */
    fun setSession(session: MistakeExtractionSession) {
        this.session = session
    }

    fun clear() {
        session = null
    }

    /**
     * 手动框选题目后识别单题；不写共享 state，也不影响待确认会话。
     */
    suspend fun recognizeQuestion(imageUri: String): QuestionRecognitionOutcome {
        val file = File(Uri.parse(imageUri).path.orEmpty())
        if (!file.exists() || file.length() <= 0) {
            return QuestionRecognitionOutcome(error = "图片不存在，请重新框选")
        }
        val config = modelConfigRepository.activeConfigOnce()
            ?: return QuestionRecognitionOutcome(
                error = "还没有配置模型，先到设置里配置",
                needsModel = true
            )
        if (!config.supportsVision) {
            return QuestionRecognitionOutcome(
                error = "当前模型不支持识图",
                needsModel = true
            )
        }

        val messagesJson = MistakeQuestionPrompt.buildMessagesJson(file)
        val raw = StringBuilder()
        var error: String? = null
        modelConfigRepository.streamWith(
            config = config,
            messagesJson = messagesJson,
            maxTokens = 4096,
            temperature = 0.2
        ).collect { event ->
            when (event) {
                is StreamEvent.Delta -> raw.append(event.text)
                is StreamEvent.Done -> Unit
                is StreamEvent.Error -> if (error == null) error = event.message
            }
        }

        if (error != null) {
            return QuestionRecognitionOutcome(error = error)
        }
        val items = runCatching {
            MistakeExtractionParser.parse(raw.toString())
        }.getOrNull().orEmpty()
        if (items.isEmpty()) {
            return QuestionRecognitionOutcome(error = "没有识别出题目，请调整框选范围后重试")
        }
        return QuestionRecognitionOutcome(items = items.take(5))
    }
}
