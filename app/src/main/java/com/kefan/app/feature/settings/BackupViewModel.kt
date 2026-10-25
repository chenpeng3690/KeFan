package com.kefan.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.repository.BackupMergeService
import com.kefan.app.data.repository.MergeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class BackupUi(
    val running: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val mergeService: BackupMergeService
) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUi())
    val ui = _ui.asStateFlow()

    fun exportBackup(targetUri: Uri) {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { buildBackupZip(targetUri) }
                _ui.update {
                    it.copy(running = false, message = "备份已导出，包含数据库和全部图片")
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(running = false, error = "备份失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    fun importBackup(sourceUri: Uri) {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { restoreBackupZip(sourceUri) }
                _ui.update {
                    it.copy(running = false, message = "恢复完成，App 即将自动重启…")
                }
                delay(600)
                restartApp()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(running = false, error = "恢复失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    /** 合并恢复：保留当前数据，只补充备份里没有的学生、反馈、成绩和错题。 */
    fun importBackupMerge(sourceUri: Uri) {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { mergeBackupZip(sourceUri) }
                val total = result.studentsAdded + result.feedbacksAdded +
                    result.scoresAdded + result.mistakesAdded
                _ui.update {
                    it.copy(
                        running = false,
                        message = if (total == 0) {
                            "合并完成：没有发现需要补充的新数据"
                        } else {
                            "合并完成：新增学生 ${result.studentsAdded}、反馈 ${result.feedbacksAdded}、" +
                                "成绩 ${result.scoresAdded}、错题 ${result.mistakesAdded}"
                        }
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(running = false, error = "合并恢复失败：${e.message.orEmpty()}")
                }
            }
        }
    }

    fun dismiss() {
        _ui.update { it.copy(message = null, error = null) }
    }

    private fun buildBackupZip(targetUri: Uri) {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
            .use { it.moveToFirst() }

        val zipFile = File(context.cacheDir, "kefan_backup_${System.currentTimeMillis()}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            addFileToZip(zip, dbFile, "kefan.db")
            addFileToZip(zip, File(dbFile.path + "-wal"), "kefan.db-wal")
            addFileToZip(zip, File(dbFile.path + "-shm"), "kefan.db-shm")

            listOf("feedback_images", "mistake_images").forEach { folder ->
                val dir = File(context.filesDir, folder)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zip, file, "$folder/${file.name}")
                        }
                    }
                }
            }
        }

        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(zipFile).use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法写入备份文件")
        zipFile.delete()
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun restoreBackupZip(sourceUri: Uri) {
        val tempDir = extractBackupZip(sourceUri)
        try {
            val restoredDb = File(tempDir, "kefan.db")
            if (!restoredDb.exists()) {
                throw IllegalStateException("备份里没有数据库文件")
            }

            database.close()

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            dbFile.parentFile?.mkdirs()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbFile.delete()
            restoredDb.copyTo(dbFile, overwrite = true)
            File(tempDir, "kefan.db-wal").takeIf { it.exists() }?.copyTo(
                File(dbFile.path + "-wal"),
                overwrite = true
            )
            File(tempDir, "kefan.db-shm").takeIf { it.exists() }?.copyTo(
                File(dbFile.path + "-shm"),
                overwrite = true
            )

            listOf("feedback_images", "mistake_images").forEach { folder ->
                val target = File(context.filesDir, folder)
                target.deleteRecursively()
                target.mkdirs()
                val source = File(tempDir, folder)
                if (source.exists()) {
                    source.listFiles()?.forEach { file ->
                        if (file.isFile) file.copyTo(File(target, file.name), overwrite = true)
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun mergeBackupZip(sourceUri: Uri): MergeResult {
        val tempDir = extractBackupZip(sourceUri)
        try {
            val restoredDb = File(tempDir, "kefan.db")
            if (!restoredDb.exists()) {
                throw IllegalStateException("备份里没有数据库文件")
            }
            return mergeService.merge(restoredDb, tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractBackupZip(sourceUri: Uri): File {
        val tempDir = File(context.cacheDir, "kefan_restore_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val canonicalRoot = tempDir.canonicalPath
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && !name.contains("..")) {
                            val target = File(tempDir, name)
                            if (target.canonicalPath.startsWith(canonicalRoot)) {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output -> zip.copyTo(output) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw IllegalStateException("无法读取备份文件")
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
        return tempDir
    }

    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) {
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }
}
