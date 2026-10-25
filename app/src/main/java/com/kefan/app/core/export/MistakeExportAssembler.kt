package com.kefan.app.core.export

import android.content.Context
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.entity.MistakeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把错题记录映射成 Word 题目，并统一处理插图。
 */
@Singleton
class MistakeExportAssembler @Inject constructor(
    private val mistakeImageDao: MistakeImageDao,
    private val docxBuilder: MistakeDocxBuilder
) {

    suspend fun buildDocx(
        context: Context,
        studentName: String,
        mistakes: List<MistakeRecord>,
        includeFigures: Boolean,
        includeAnswers: Boolean,
        onePerPage: Boolean,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val questions = mistakes.map { mistake ->
            val imageUri = if (includeFigures && mistake.hasFigure) {
                val images = mistakeImageDao.listByMistake(mistake.id)
                images.firstOrNull { !it.figureUri.isNullOrBlank() }?.figureUri
                    ?: images.firstOrNull { !it.croppedUri.isNullOrBlank() }?.croppedUri
            } else {
                null
            }
            DocxQuestion(
                stemText = mistake.stemText,
                correctAnswer = if (includeAnswers) mistake.correctAnswer else "",
                analysis = if (includeAnswers) mistake.analysis else "",
                imageUri = imageUri
            )
        }
        docxBuilder.build(
            context = context,
            studentName = studentName,
            questions = questions,
            includeAnswers = includeAnswers,
            oneQuestionPerPage = onePerPage,
            outputFile = outputFile
        )
    }
}
