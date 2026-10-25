package com.kefan.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class MemoryTaskKind { COMPACT, PROFILE }

data class MemoryTaskState(
    val running: Boolean = false,
    val kind: MemoryTaskKind = MemoryTaskKind.COMPACT,
    val message: String? = null,
    val isError: Boolean = false,
    val lastSuccessAt: Long? = null
)

/**
 * 应用级记忆维护：保存反馈后异步整理，不随页面销毁而取消。
 */
@Singleton
class MemoryMaintenance @Inject constructor(
    private val repository: MemoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<Long, MemoryTaskState>>(emptyMap())
    val states: StateFlow<Map<Long, MemoryTaskState>> = _states.asStateFlow()

    fun compact(studentId: Long, force: Boolean) {
        run(studentId, MemoryTaskKind.COMPACT) {
            repository.compact(studentId, force = force)
        }
    }

    fun rebuildProfile(studentId: Long) {
        run(studentId, MemoryTaskKind.PROFILE) {
            repository.rebuildProfile(studentId)
        }
    }

    fun clearMessage(studentId: Long) {
        val current = _states.value[studentId] ?: return
        _states.value = _states.value + (studentId to current.copy(message = null))
    }

    private fun run(
        studentId: Long,
        kind: MemoryTaskKind,
        block: suspend () -> MemoryOpResult
    ) {
        if (_states.value[studentId]?.running == true) return
        val previous = _states.value[studentId] ?: MemoryTaskState()
        update(
            studentId,
            previous.copy(running = true, kind = kind, message = null, isError = false)
        )

        scope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                MemoryOpResult(MemoryOutcome.FAILED, "整理失败：${e.message.orEmpty()}")
            }
            val latest = _states.value[studentId] ?: previous
            update(
                studentId,
                latest.copy(
                    running = false,
                    message = result.message,
                    isError = result.isError,
                    lastSuccessAt = if (result.outcome == MemoryOutcome.DONE) {
                        System.currentTimeMillis()
                    } else {
                        latest.lastSuccessAt
                    }
                )
            )
        }
    }

    private fun update(studentId: Long, state: MemoryTaskState) {
        _states.value = _states.value + (studentId to state)
    }
}
