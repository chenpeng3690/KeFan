package com.kefan.app.domain.model

/**
 * AI 从教案照片里提取出来的一道错题，确认后才会入库。
 */
data class ExtractedMistake(
    val stemText: String,
    val studentAnswer: String = "",
    val correctAnswer: String = "",
    val knowledgeTags: List<String> = emptyList(),
    val errorType: String = "",
    val difficulty: Int = 3,
    val hasFigure: Boolean = false,
    val figureNote: String = "",
    val figureBox: List<Float> = emptyList(),
    val pageIndex: Int = 1,
    val confidence: Double = 0.5,
    val cropHint: String = ""
)
