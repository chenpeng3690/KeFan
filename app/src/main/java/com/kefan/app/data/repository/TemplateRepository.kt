package com.kefan.app.data.repository

import com.kefan.app.data.local.TemplateJson
import com.kefan.app.data.local.dao.FeedbackTemplateDao
import com.kefan.app.data.local.dao.ManualSampleDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.remote.prompts.TemplateExtractionPrompt
import com.kefan.app.domain.model.TemplateDefaults
import com.kefan.app.domain.model.TemplateRules
import com.kefan.app.domain.model.TemplateSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TemplateExtractResult {
    data class Success(
        val templateId: Long,
        val version: Int,
        val sectionCount: Int,
        val styleSummary: String
    ) : TemplateExtractResult

    data class NeedsModel(val message: String) : TemplateExtractResult
    data class Failure(val message: String) : TemplateExtractResult
}

@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: FeedbackTemplateDao,
    private val sampleDao: ManualSampleDao,
    private val memoryCardDao: MemoryCardDao,
    private val modelConfigRepository: ModelConfigRepository
) {
    fun observeActiveTemplate(studentId: Long): Flow<FeedbackTemplate?> =
        templateDao.observeActiveForStudent(studentId)

    fun observeGlobalTemplate(): Flow<FeedbackTemplate?> = templateDao.observeActiveGlobal()

    /** 该生的模板优先，其次全局模板；都没有时返回 null，由界面使用极简兜底。 */
    fun observeEffectiveTemplate(studentId: Long): Flow<FeedbackTemplate?> = combine(
        templateDao.observeActiveForStudent(studentId),
        templateDao.observeActiveGlobal()
    ) { studentTemplate, globalTemplate ->
        studentTemplate ?: globalTemplate
    }

    fun observeSamples(studentId: Long): Flow<List<ManualSample>> =
        sampleDao.observeByStudent(studentId)

    fun observeVersions(studentId: Long): Flow<List<FeedbackTemplate>> =
        templateDao.observeVersionsForStudent(studentId)

    suspend fun loadEditingSpec(studentId: Long): TemplateSpec {
        val active = templateDao.getActiveForStudent(studentId)
            ?: templateDao.getActiveGlobal()
        return if (active == null) {
            TemplateDefaults.fallback()
        } else {
            TemplateJson.toSpec(active)
        }
    }

    suspend fun loadGlobalEditingSpec(): TemplateSpec {
        val active = templateDao.getActiveGlobal()
        return if (active == null) TemplateDefaults.fallback() else TemplateJson.toSpec(active)
    }

    /**
     * 保存全局模板：新生没导入历史反馈时继承它。
     */
    suspend fun saveGlobalTemplate(spec: TemplateSpec): TemplateExtractResult {
        if (spec.sections.isEmpty()) {
            return TemplateExtractResult.Failure("模板至少要有一段")
        }
        if (spec.sections.any { it.title.isBlank() }) {
            return TemplateExtractResult.Failure("段落标题不能为空")
        }

        val version = templateDao.maxGlobalVersion() + 1
        templateDao.deactivateGlobal()

        val templateId = templateDao.insert(
            FeedbackTemplate(
                studentId = null,
                scope = FeedbackTemplate.SCOPE_GLOBAL,
                opening = spec.opening,
                blankLineAfterOpening = spec.blankLineAfterOpening,
                sectionsJson = TemplateJson.encodeSections(
                    TemplateRules.normalizeSections(spec.sections)
                ),
                tone = spec.tone,
                address = spec.address,
                lengthMin = spec.lengthMin,
                lengthMax = spec.lengthMax,
                bannedWordsJson = TemplateJson.encodeStrings(spec.bannedWords),
                useEmoji = spec.useEmoji,
                sourceSampleIdsJson = TemplateJson.encodeLongs(spec.sourceSampleIds),
                styleSummary = spec.styleSummary,
                version = version,
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
        )

        return TemplateExtractResult.Success(
            templateId = templateId,
            version = version,
            sectionCount = spec.sections.size,
            styleSummary = spec.styleSummary
        )
    }

    suspend fun loadEffectiveTemplate(studentId: Long): FeedbackTemplate? =
        templateDao.getActiveForStudent(studentId) ?: templateDao.getActiveGlobal()

    suspend fun loadSamples(studentId: Long): List<ManualSample> =
        sampleDao.listByStudent(studentId)

    /**
     * 导入页：先保存样例，再调用 AI 提取。失败时样例仍在，可直接重试。
     */
    suspend fun extractFromTexts(studentId: Long, texts: List<String>): TemplateExtractResult {
        val cleaned = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return TemplateExtractResult.Failure("先粘贴至少一条历史反馈")

        sampleDao.insertAll(
            cleaned.map {
                ManualSample(
                    studentId = studentId,
                    content = it,
                    sourceType = ManualSample.SOURCE_PASTE
                )
            }
        )
        return analyze(studentId)
    }

    /** 写作风格页：用已保存的样例重新分析 */
    suspend fun reanalyze(studentId: Long): TemplateExtractResult {
        val samples = sampleDao.listByStudent(studentId)
        if (samples.isEmpty()) {
            return TemplateExtractResult.Failure("还没有历史反馈，先导入几条")
        }
        return analyze(studentId)
    }

    private suspend fun analyze(studentId: Long): TemplateExtractResult {
        val samples = sampleDao.listByStudent(studentId).takeLast(6)
        if (samples.isEmpty()) {
            return TemplateExtractResult.Failure("还没有历史反馈，先导入几条")
        }

        val config = modelConfigRepository.activeConfigOnce()
            ?: return TemplateExtractResult.NeedsModel(
                "还没有配置模型。先到设置里填 Base URL、Key 和模型名。"
            )

        return when (
            val result = modelConfigRepository.chatWith(
                config = config,
                systemPrompt = TemplateExtractionPrompt.SYSTEM_PROMPT,
                userPrompt = TemplateExtractionPrompt.userPrompt(samples.map { it.content }),
                maxTokens = 2048,
                temperature = 0.2
            )
        ) {
            is ApiResult.Failure -> TemplateExtractResult.Failure(result.message)
            is ApiResult.Success -> {
                val spec = try {
                    TemplateJson.parseExtraction(result.data.content)
                } catch (e: Exception) {
                    return TemplateExtractResult.Failure(
                        "AI 返回的模板无法解析：${e.message.orEmpty()}"
                    )
                }

                val version = templateDao.maxVersionForStudent(studentId) + 1
                templateDao.deactivateForStudent(studentId)

                val now = System.currentTimeMillis()
                val templateId = templateDao.insert(
                    FeedbackTemplate(
                        studentId = studentId,
                        scope = FeedbackTemplate.SCOPE_STUDENT,
                        opening = spec.opening,
                        blankLineAfterOpening = spec.blankLineAfterOpening,
                        sectionsJson = TemplateJson.encodeSections(spec.sections),
                        tone = spec.tone,
                        address = spec.address,
                        lengthMin = spec.lengthMin,
                        lengthMax = spec.lengthMax,
                        bannedWordsJson = TemplateJson.encodeStrings(spec.bannedWords),
                        useEmoji = spec.useEmoji,
                        sourceSampleIdsJson = TemplateJson.encodeLongs(samples.map { it.id }),
                        styleSummary = spec.styleSummary,
                        version = version,
                        isActive = true,
                        updatedAt = now
                    )
                )

                sampleDao.markAllAnalyzed(studentId)

                if (spec.profileDraft.isNotBlank()) {
                    val existing = memoryCardDao.get(studentId)
                    memoryCardDao.upsert(
                        MemoryCard(
                            studentId = studentId,
                            longTermProfile = spec.profileDraft,
                            recentSummary = existing?.recentSummary.orEmpty(),
                            compactedUntilRecordId = existing?.compactedUntilRecordId ?: 0,
                            tokenEstimate = existing?.tokenEstimate ?: 0,
                            version = (existing?.version ?: 0) + 1,
                            updatedAt = now
                        )
                    )
                }

                TemplateExtractResult.Success(
                    templateId = templateId,
                    version = version,
                    sectionCount = spec.sections.size,
                    styleSummary = spec.styleSummary
                )
            }
        }
    }

    /**
     * 手动编辑模板：保存为新版本，旧版本保留可回滚。
     */
    suspend fun saveEditedTemplate(studentId: Long, spec: TemplateSpec): TemplateExtractResult {
        if (spec.sections.isEmpty()) {
            return TemplateExtractResult.Failure("模板至少要有一段")
        }
        if (spec.sections.any { it.title.isBlank() }) {
            return TemplateExtractResult.Failure("段落标题不能为空")
        }

        val previous = templateDao.getActiveForStudent(studentId)
        val sampleIds = spec.sourceSampleIds.ifEmpty {
            previous?.let { TemplateJson.decodeLongs(it.sourceSampleIdsJson) }.orEmpty()
        }
        val version = templateDao.maxVersionForStudent(studentId) + 1
        templateDao.deactivateForStudent(studentId)

        val templateId = templateDao.insert(
            FeedbackTemplate(
                studentId = studentId,
                scope = FeedbackTemplate.SCOPE_STUDENT,
                opening = spec.opening,
                blankLineAfterOpening = spec.blankLineAfterOpening,
                sectionsJson = TemplateJson.encodeSections(
                    TemplateRules.normalizeSections(spec.sections)
                ),
                tone = spec.tone,
                address = spec.address,
                lengthMin = spec.lengthMin,
                lengthMax = spec.lengthMax,
                bannedWordsJson = TemplateJson.encodeStrings(spec.bannedWords),
                useEmoji = spec.useEmoji,
                sourceSampleIdsJson = TemplateJson.encodeLongs(sampleIds),
                styleSummary = spec.styleSummary,
                version = version,
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
        )

        return TemplateExtractResult.Success(
            templateId = templateId,
            version = version,
            sectionCount = spec.sections.size,
            styleSummary = spec.styleSummary
        )
    }

    suspend fun activateVersion(studentId: Long, templateId: Long): Boolean {
        val template = templateDao.getById(templateId) ?: return false
        if (template.studentId != studentId) return false

        templateDao.deactivateForStudent(studentId)
        templateDao.update(
            template.copy(isActive = true, updatedAt = System.currentTimeMillis())
        )
        return true
    }
}
