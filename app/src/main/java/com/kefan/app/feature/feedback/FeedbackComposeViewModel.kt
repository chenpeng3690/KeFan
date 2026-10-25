package com.kefan.app.feature.feedback

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.core.util.ImageProcessor
import com.kefan.app.core.util.formatDate
import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.dao.ScoreRecordDao
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student
import com.kefan.app.data.remote.StreamEvent
import com.kefan.app.data.remote.prompts.FeedbackPrompt
import com.kefan.app.data.remote.prompts.FeedbackPromptInputs
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.quality.FeedbackReview
import com.kefan.app.data.quality.ReviewIssue
import com.kefan.app.data.quality.SensitiveDictionary
import com.kefan.app.data.repository.FeedbackRepository
import com.kefan.app.data.repository.MemoryMaintenance
import com.kefan.app.data.repository.ModelConfigRepository
import com.kefan.app.data.repository.SensitiveWordsRepository
import com.kefan.app.data.repository.StudentRepository
import com.kefan.app.data.repository.TemplateRepository
import com.kefan.app.domain.model.FeedbackAssembly
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.domain.model.TemplateSection
import com.kefan.app.domain.model.TemplateSpec
import com.kefan.app.navigation.KfRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class ComposeImageItem(val file: File) {
    val uri: Uri get() = Uri.fromFile(file)
}

data class ComposeUi(
    val loading: Boolean = true,
    val student: Student? = null,
    val templateLabel: String = "临时模板",
    val templateSpec: TemplateSpec = TemplateDefaults.fallback(),
    val classDate: Long = System.currentTimeMillis(),
    val lessonCount: Int = 2,
    val keyPoints: String = "",
    val images: List<ComposeImageItem> = emptyList(),
    val processingTotal: Int = 0,
    val processingDone: Int = 0,
    val hasModel: Boolean = false,
    val visionSupported: Boolean = true,
    val generating: Boolean = false,
    val generated: Boolean = false,
    val rawAi: String = "",
    val finalContent: String = "",
    val aiDraft: String = "",
    val summary: String = "",
    val generatedTitle: String = "",
    val taskText: String = "复习错题",
    val missingSections: List<String> = emptyList(),
    val error: String? = null,
    val needsModel: Boolean = false,
    val saving: Boolean = false,
    val latestScoreText: String? = null,
    val reviewIssues: List<ReviewIssue> = emptyList(),
    val dismissedIssueKeys: Set<String> = emptySet(),
    val keepEmoji: Boolean = false,
    val strictFactCheck: Boolean = false
) {
    val processing: Boolean get() = processingTotal > 0 && processingDone < processingTotal
    val resultMode: Boolean get() = generated
    val unresolvedIssues: List<ReviewIssue>
        get() = FeedbackReview.unresolved(reviewIssues, dismissedIssueKeys)
    val strictBlocked: Boolean
        get() = strictFactCheck && unresolvedIssues.isNotEmpty()
}

sealed interface ComposeEvent {
    data class Message(val text: String) : ComposeEvent
    data class Saved(val feedbackId: Long) : ComposeEvent
}

@HiltViewModel
class FeedbackComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val studentRepository: StudentRepository,
    private val templateRepository: TemplateRepository,
    private val feedbackRepository: FeedbackRepository,
    private val modelConfigRepository: ModelConfigRepository,
    private val scoreRecordDao: ScoreRecordDao,
    private val memoryCardDao: MemoryCardDao,
    private val feedbackDao: FeedbackDao,
    private val mistakeRecordDao: MistakeRecordDao,
    private val preferences: AppPreferences,
    private val memoryMaintenance: MemoryMaintenance,
    private val sensitiveWordsRepository: SensitiveWordsRepository
) : ViewModel() {

    private val studentId: Long = savedStateHandle[KfRoutes.ARG_STUDENT_ID] ?: -1L

    private val _ui = MutableStateFlow(ComposeUi())
    val ui: StateFlow<ComposeUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<ComposeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var templateId: Long? = null
    private var templateVersion: Int = 1
    private var generationJob: Job? = null
    private var saved = false
    private var reviewSources: List<String> = emptyList()
    private var sensitiveWords: List<String> = SensitiveDictionary.defaultWords
    private val draftFiles = mutableSetOf<File>()
    private val imageDir: File by lazy { File(context.filesDir, "feedback_images") }

    init {
        viewModelScope.launch {
            preferences.copyKeepEmoji.collect { keep ->
                _ui.update { it.copy(keepEmoji = keep) }
            }
        }
        viewModelScope.launch {
            preferences.strictFactCheck.collect { strict ->
                _ui.update { it.copy(strictFactCheck = strict) }
            }
        }
        viewModelScope.launch {
            sensitiveWordsRepository.words.collect { words ->
                sensitiveWords = words
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val student = studentRepository.getById(studentId)
            if (student == null) {
                _ui.update { it.copy(loading = false, error = "学生档案不存在") }
                return@launch
            }

            val template = templateRepository.loadEffectiveTemplate(studentId)
            val spec = template?.let { TemplateJson.toSpec(it) } ?: TemplateDefaults.fallback()
            templateId = template?.id
            templateVersion = template?.version ?: 1

            val config = modelConfigRepository.activeConfigOnce()
            val scores = recentScores()
            val taskDefault = spec.sections.firstOrNull {
                it.source == TemplateSection.SOURCE_APP && it.title.contains("课后任务")
            }?.defaultValue?.ifBlank { "复习错题" } ?: "复习错题"

            _ui.value = ComposeUi(
                loading = false,
                student = student,
                templateLabel = templateLabel(student, template, spec),
                templateSpec = spec,
                classDate = System.currentTimeMillis(),
                lessonCount = student.defaultLessons.coerceIn(1, 20),
                hasModel = config != null,
                visionSupported = config?.supportsVision ?: false,
                taskText = taskDefault,
                latestScoreText = scoreText(scores)
            )
        }
    }

    fun updateClassDate(millis: Long) {
        _ui.update { state ->
            val content = if (state.finalContent.isNotBlank()) {
                FeedbackAssembly.replaceTimeInContent(
                    state.finalContent,
                    state.templateSpec,
                    millis,
                    state.lessonCount
                )
            } else {
                state.finalContent
            }
            state.copy(
                classDate = millis,
                finalContent = content,
                generatedTitle = "",
                reviewIssues = reviewOf(content)
            )
        }
    }

    fun updateLessonCount(count: Int) {
        val safe = count.coerceIn(1, 20)
        _ui.update { state ->
            val content = if (state.finalContent.isNotBlank()) {
                FeedbackAssembly.replaceTimeInContent(
                    state.finalContent,
                    state.templateSpec,
                    state.classDate,
                    safe
                )
            } else {
                state.finalContent
            }
            state.copy(
                lessonCount = safe,
                finalContent = content,
                reviewIssues = reviewOf(content)
            )
        }
    }

    fun updateKeyPoints(value: String) {
        _ui.update { it.copy(keyPoints = value) }
    }

    fun updateTask(value: String) {
        _ui.update { state ->
            val content = if (state.finalContent.isNotBlank()) {
                FeedbackAssembly.replaceTaskInContent(
                    state.finalContent,
                    state.templateSpec,
                    value
                )
            } else {
                state.finalContent
            }
            state.copy(
                taskText = value,
                finalContent = content,
                reviewIssues = reviewOf(content)
            )
        }
    }

    fun updateFinalContent(value: String) {
        _ui.update { it.copy(finalContent = value, reviewIssues = reviewOf(value)) }
    }

    fun keepIssue(issue: ReviewIssue) {
        _ui.update {
            it.copy(dismissedIssueKeys = it.dismissedIssueKeys + issue.key)
        }
    }

    fun deleteIssueSentence(issue: ReviewIssue) {
        val updated = FeedbackReview.deleteSentence(_ui.value.finalContent, issue)
        updateFinalContent(updated)
    }

    fun applySuggestion(issue: ReviewIssue, suggestion: String) {
        val updated = FeedbackReview.replaceIssue(_ui.value.finalContent, issue, suggestion)
        updateFinalContent(updated)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val state = _ui.value
        if (state.processing) {
            _events.tryEmit(ComposeEvent.Message("正在处理图片，稍等一下"))
            return
        }
        val remaining = 12 - state.images.size
        if (remaining <= 0) {
            _events.tryEmit(ComposeEvent.Message("最多 12 张教案照片"))
            return
        }
        val selected = uris.take(remaining)
        if (uris.size > remaining) {
            _events.tryEmit(ComposeEvent.Message("最多 12 张，已忽略多余照片"))
        }

        _ui.update { it.copy(processingTotal = selected.size, processingDone = 0, error = null) }
        viewModelScope.launch {
            var failed = 0
            selected.forEachIndexed { index, uri ->
                try {
                    val file = ImageProcessor.compressToFile(context, uri, imageDir)
                    draftFiles.add(file)
                    _ui.update {
                        it.copy(images = it.images + ComposeImageItem(file))
                    }
                } catch (e: Exception) {
                    failed += 1
                }
                _ui.update { it.copy(processingDone = index + 1) }
            }
            _ui.update { it.copy(processingTotal = 0, processingDone = 0) }
            if (failed > 0) {
                _events.tryEmit(ComposeEvent.Message("有 $failed 张图片处理失败"))
            }
        }
    }

    fun removeImage(item: ComposeImageItem) {
        item.file.delete()
        draftFiles.remove(item.file)
        _ui.update { state ->
            state.copy(images = state.images.filterNot { it.file == item.file })
        }
    }

    fun generate() {
        if (_ui.value.generating) return
        val state = _ui.value
        val student = state.student ?: return
        if (!state.hasModel) {
            _ui.update {
                it.copy(
                    error = "还没有配置模型。先到设置里填 Base URL、Key 和模型名。",
                    needsModel = true
                )
            }
            return
        }
        if (state.processing) {
            _ui.update { it.copy(error = "图片还在处理中，稍等一下") }
            return
        }
        if (state.images.isEmpty() && state.keyPoints.isBlank()) {
            _ui.update { it.copy(error = "先上传教案照片，或写一句本次要点") }
            return
        }

        _ui.update {
            it.copy(
                generating = true,
                generated = false,
                rawAi = "",
                finalContent = "",
                aiDraft = "",
                summary = "",
                generatedTitle = "",
                missingSections = emptyList(),
                error = null,
                needsModel = false
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val config = modelConfigRepository.activeConfigOnce()
                if (config == null) {
                    _ui.update {
                        it.copy(
                            generating = false,
                            error = "还没有配置模型，先到设置里配置。",
                            needsModel = true
                        )
                    }
                    return@launch
                }
                if (!config.supportsVision && state.keyPoints.isBlank()) {
                    _ui.update {
                        it.copy(
                            generating = false,
                            error = "当前模型不支持识图，请先在「本次要点」里写清楚这节课的内容"
                        )
                    }
                    return@launch
                }

                val memory = memoryCardDao.get(studentId)
                val lastFeedback = feedbackDao.latestForStudent(studentId)
                val samples = templateRepository.loadSamples(studentId)
                val scores = recentScores()
                val mistakes = mistakeRecordDao.listRecent(
                    studentId,
                    System.currentTimeMillis() - 120L * 24 * 60 * 60 * 1000
                )
                val inputs = FeedbackPromptInputs(
                    student = student,
                    spec = state.templateSpec,
                    memory = memory,
                    lastFeedback = lastFeedback,
                    samples = samples,
                    scores = scores,
                    mistakes = mistakes,
                    classDate = state.classDate,
                    lessonCount = state.lessonCount,
                    keyPoints = state.keyPoints.trim(),
                    imageFiles = if (config.supportsVision) {
                        state.images.map { it.file }
                    } else {
                        emptyList()
                    }
                )

                val messagesJson = FeedbackPrompt.buildMessagesJson(inputs)
                modelConfigRepository.streamWith(
                    config = config,
                    messagesJson = messagesJson,
                    maxTokens = 4096,
                    temperature = config.temperature.coerceIn(0.0, 1.5)
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> _ui.update {
                            it.copy(rawAi = it.rawAi + event.text)
                        }
                        is StreamEvent.Done -> finishGeneration()
                        is StreamEvent.Error -> _ui.update {
                            it.copy(generating = false, error = event.message)
                        }
                    }
                }
                // 兜底：部分服务商直接断流、不发 [DONE]，流结束后仍要进入结果态
                if (_ui.value.generating) finishGeneration()
            } catch (e: CancellationException) {
                _ui.update { it.copy(generating = false) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        generating = false,
                        error = "生成失败：${e.message.orEmpty()}"
                    )
                }
            } finally {
                generationJob = null
            }
        }
    }

    private suspend fun finishGeneration() {
        val state = _ui.value
        val student = state.student ?: return
        val raw = state.rawAi
        if (raw.isBlank()) {
            _ui.update { it.copy(generating = false, error = "模型没有返回内容，请重试") }
            return
        }

        val result = FeedbackAssembly.assemble(
            spec = state.templateSpec,
            student = student,
            classDate = state.classDate,
            lessonCount = state.lessonCount,
            taskText = state.taskText,
            aiText = raw
        )
        if (result.content.isBlank()) {
            _ui.update { it.copy(generating = false, error = "AI 未按模板输出内容，请重新生成") }
            return
        }

        val title = feedbackRepository.nextTitle(studentId, state.classDate)
        reviewSources = buildReviewSources(state, student)
        _ui.update {
            it.copy(
                generating = false,
                generated = true,
                finalContent = result.content,
                aiDraft = result.content,
                summary = result.summary.ifBlank { state.keyPoints.take(20) },
                generatedTitle = title,
                missingSections = result.missingSections,
                reviewIssues = reviewOf(result.content),
                dismissedIssueKeys = emptySet(),
                error = null
            )
        }
    }

    private fun reviewOf(content: String): List<ReviewIssue> =
        if (content.isBlank()) emptyList()
        else FeedbackReview.analyze(content, reviewSources, sensitiveWords)

    private suspend fun buildReviewSources(state: ComposeUi, student: Student): List<String> {
        val sources = mutableListOf<String>()
        sources += state.keyPoints
        sources += state.latestScoreText.orEmpty()
        sources += formatDate(state.classDate)
        sources += "${state.lessonCount} 课时"
        sources += student.name
        sources += student.grade
        sources += student.subject
        sources += student.goal
        sources += student.personality
        sources += student.parentNote
        recentScores(3).forEach { score ->
            sources += "${score.examName} ${scoreTextValue(score.score)} 分 / ${scoreTextValue(score.fullScore)} 分"
            sources += formatDate(score.date)
            score.rank?.let { sources += "第 $it 名" }
            sources += score.knowledgeTagsJson
        }
        mistakeRecordDao.observeByStudent(studentId).first().forEach { mistake ->
            sources += mistake.stemText
            sources += mistake.knowledgeTagsJson
            sources += mistake.errorType
            sources += formatDate(mistake.createdAt)
        }
        memoryCardDao.get(studentId)?.let { memory ->
            sources += memory.longTermProfile
            sources += memory.recentSummary
        }
        return sources.filter { it.isNotBlank() }
    }

    private fun scoreTextValue(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.CHINA, "%.1f", value)

    /** 只取近 7 天成绩；没有就填"无"，绝不编造成绩 */
    private suspend fun recentScores(limit: Int = 2): List<ScoreRecord> {
        val now = System.currentTimeMillis()
        val start = now - 7L * 24 * 60 * 60 * 1000
        return scoreRecordDao.listInRange(studentId, start, now + 1)
            .sortedByDescending { it.date }
            .take(limit)
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _ui.update {
            it.copy(generating = false, generated = false, rawAi = "", error = "已停止生成")
        }
    }

    fun regenerate() {
        if (_ui.value.generating) return
        _ui.update {
            it.copy(
                generated = false,
                finalContent = "",
                aiDraft = "",
                generatedTitle = "",
                missingSections = emptyList(),
                reviewIssues = emptyList(),
                dismissedIssueKeys = emptySet(),
                error = null
            )
        }
        generate()
    }

    fun save() {
        val state = _ui.value
        if (state.saving || state.finalContent.isBlank()) return
        val student = state.student ?: return

        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val title = state.generatedTitle.ifBlank {
                    feedbackRepository.nextTitle(studentId, state.classDate)
                }
                val feedback = Feedback(
                    studentId = studentId,
                    title = title,
                    summary = state.summary.ifBlank { state.keyPoints.take(20) },
                    classDate = state.classDate,
                    lessonCount = state.lessonCount,
                    taskText = state.taskText,
                    aiDraft = state.aiDraft,
                    finalContent = state.finalContent,
                    status = Feedback.STATUS_FINAL,
                    modelConfigId = modelConfigRepository.activeConfigOnce()?.id,
                    templateId = templateId,
                    templateVersion = templateVersion
                )
                val id = feedbackRepository.save(
                    feedback = feedback,
                    imageFiles = state.images.map { it.file }
                )
                saved = true
                draftFiles.clear()
                // 保存后异步整理记忆，不阻塞跳转
                memoryMaintenance.compact(studentId, force = false)
                _ui.update { it.copy(saving = false) }
                _events.emit(ComposeEvent.Saved(id))
            } catch (e: Exception) {
                _ui.update {
                    it.copy(saving = false, error = "保存失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null, needsModel = false) }
    }

    override fun onCleared() {
        super.onCleared()
        if (!saved) {
            draftFiles.forEach { file ->
                runCatching { file.delete() }
            }
        }
    }

    private fun templateLabel(
        student: Student,
        template: FeedbackTemplate?,
        spec: TemplateSpec
    ): String {
        val count = spec.sections.size
        return when {
            template == null -> "临时模板 · $count 段"
            template.scope == FeedbackTemplate.SCOPE_GLOBAL -> "继承全局模板 · $count 段"
            else -> "${student.name}的模板 · $count 段"
        }
    }

    private fun scoreText(scores: List<ScoreRecord>): String? {
        val latest = scores.firstOrNull() ?: return null
        val days = (System.currentTimeMillis() - latest.date) / (24L * 60 * 60 * 1000)
        if (days > 7) return null
        val previous = scores.getOrNull(1)
        val latestScore = formatScore(latest.score)
        return if (previous == null) {
            "本次 $latestScore 分"
        } else {
            val delta = latest.score - previous.score
            val deltaText = when {
                delta > 0 -> "↑${formatScore(delta)}"
                delta < 0 -> "↓${formatScore(-delta)}"
                else -> "持平"
            }
            "本次 $latestScore 分（上次 ${formatScore(previous.score)}，$deltaText）"
        }
    }

    private fun formatScore(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.CHINA, "%.1f", value)
}
