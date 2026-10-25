package com.kefan.app.data.repository

import com.kefan.app.data.local.dao.ContextLogDao
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.entity.ContextLog
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.remote.ApiResult
import com.kefan.app.data.remote.prompts.MemoryPrompt
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class MemoryOutcome { DONE, SKIPPED, FAILED }

data class MemoryOpResult(
    val outcome: MemoryOutcome,
    val message: String
) {
    val isError: Boolean get() = outcome == MemoryOutcome.FAILED
}

/**
 * 四层记忆的维护：
 * L1 = Student 档案卡（人工，不在这里写）
 * L2/L3 = MemoryCard（AI 压缩，只读展示）
 * L4 = Feedback 原始记录（永不删除）
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryCardDao: MemoryCardDao,
    private val feedbackDao: FeedbackDao,
    private val contextLogDao: ContextLogDao,
    private val modelConfigRepository: ModelConfigRepository,
    private val studentRepository: StudentRepository
) {

    fun observe(studentId: Long): Flow<MemoryCard?> = memoryCardDao.observe(studentId)

    /**
     * 自动 / 手动整理记忆。
     * force=false：新记录满 [MIN_NEW_RECORDS] 条才整理；force=true：有多少整理多少。
     */
    suspend fun compact(studentId: Long, force: Boolean): MemoryOpResult {
        val memory = memoryCardDao.get(studentId) ?: MemoryCard(studentId)
        val pending = feedbackDao.listFinalAsc(studentId)
            .filter { it.id > memory.compactedUntilRecordId }
        if (pending.isEmpty()) {
            return MemoryOpResult(MemoryOutcome.SKIPPED, "没有新的课堂记录需要整理")
        }
        if (!force && pending.size < MIN_NEW_RECORDS) {
            return MemoryOpResult(
                MemoryOutcome.SKIPPED,
                "新记录 ${pending.size} 条，满 $MIN_NEW_RECORDS 条会自动整理"
            )
        }

        val config = modelConfigRepository.activeConfigOnce()
            ?: return MemoryOpResult(MemoryOutcome.FAILED, "还没有配置模型，先到设置里配一个")

        val batch = pending.take(MAX_BATCH)
        val (system, user) = MemoryPrompt.compactRecentSummary(memory.recentSummary, batch)
        val l3 = chat(config, system, user)
            ?: return MemoryOpResult(MemoryOutcome.FAILED, "摘要整理失败：模型没有返回内容")

        val newL3 = l3.trim()
        if (newL3.isBlank()) {
            return MemoryOpResult(MemoryOutcome.FAILED, "摘要整理失败：模型返回为空")
        }

        var mergedL2 = memory.longTermProfile
        var finalL3 = newL3
        var profileFailed = false

        if (newL3.length > L3_LIMIT) {
            val lines = newL3.lines()
            val split = (lines.size / 2).coerceAtLeast(1)
            val early = lines.take(split).joinToString("\n")
            val later = lines.drop(split).joinToString("\n")
            val (l2System, l2User) = MemoryPrompt.mergeLongTermProfile(mergedL2, early)
            val l2 = chat(config, l2System, l2User)
            if (!l2.isNullOrBlank()) {
                mergedL2 = l2.trim().take(L2_LIMIT)
                if (later.isNotBlank()) finalL3 = later
            } else {
                profileFailed = true
            }
        }

        val now = System.currentTimeMillis()
        val tokenEstimate = estimateTokens(mergedL2 + "\n" + finalL3)
        memoryCardDao.upsert(
            memory.copy(
                longTermProfile = mergedL2,
                recentSummary = finalL3.take(L3_LIMIT),
                compactedUntilRecordId = batch.last().id,
                tokenEstimate = tokenEstimate,
                version = memory.version + 1,
                updatedAt = now
            )
        )
        contextLogDao.insert(
            ContextLog(
                studentId = studentId,
                type = "COMPACT",
                refId = batch.last().id,
                tokenCount = tokenEstimate,
                compacted = true,
                createdAt = now
            )
        )

        val message = if (profileFailed) {
            "摘要已更新，长期画像整理失败，可稍后重试"
        } else {
            "已整理 ${batch.size} 次课堂记录"
        }
        return MemoryOpResult(
            if (profileFailed) MemoryOutcome.FAILED else MemoryOutcome.DONE,
            message
        )
    }

    /** 手动一键重算长期画像（L2），L3 与原始记录不动 */
    suspend fun rebuildProfile(studentId: Long): MemoryOpResult {
        val student = studentRepository.getById(studentId)
            ?: return MemoryOpResult(MemoryOutcome.FAILED, "学生档案不存在")
        val config = modelConfigRepository.activeConfigOnce()
            ?: return MemoryOpResult(MemoryOutcome.FAILED, "还没有配置模型，先到设置里配一个")
        val memory = memoryCardDao.get(studentId) ?: MemoryCard(studentId)
        val feedbacks = feedbackDao.listFinalAsc(studentId)

        if (feedbacks.isEmpty() && memory.recentSummary.isBlank()) {
            return MemoryOpResult(MemoryOutcome.SKIPPED, "还没有课堂记录，暂时无法生成画像")
        }

        val (system, user) = MemoryPrompt.rebuildLongTermProfile(
            student = student,
            currentProfile = memory.longTermProfile,
            recentSummary = memory.recentSummary,
            feedbacks = feedbacks
        )
        val content = chat(config, system, user)
            ?: return MemoryOpResult(MemoryOutcome.FAILED, "画像生成失败：模型没有返回内容")
        if (content.isBlank()) {
            return MemoryOpResult(MemoryOutcome.FAILED, "画像生成失败：模型返回为空")
        }

        memoryCardDao.upsert(
            memory.copy(
                longTermProfile = content.trim().take(L2_LIMIT),
                tokenEstimate = estimateTokens(content.trim() + "\n" + memory.recentSummary),
                version = memory.version + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        return MemoryOpResult(MemoryOutcome.DONE, "长期画像已重新生成")
    }

    private suspend fun chat(
        config: com.kefan.app.data.local.entity.ModelConfig,
        system: String,
        user: String
    ): String? {
        val result = modelConfigRepository.chatWith(
            config = config,
            systemPrompt = system,
            userPrompt = user,
            maxTokens = 1500,
            temperature = 0.2
        )
        return when (result) {
            is ApiResult.Success -> result.data.content.trim()
            is ApiResult.Failure -> null
        }
    }

    /** §4.4 估算：汉字 × 0.65 + 其他 × 0.3，再乘 1.15 */
    fun estimateTokens(text: String): Int {
        var chinese = 0
        var other = 0
        text.forEach { ch ->
            if (ch.code in 0x4E00..0x9FFF) chinese++ else other++
        }
        return ((chinese * 0.65 + other * 0.3) * 1.15).toInt()
    }

    companion object {
        const val MIN_NEW_RECORDS = 5
        const val MAX_BATCH = 5
        const val L3_LIMIT = 600
        const val L2_LIMIT = 800
    }
}
