package com.kefan.app.navigation

object KfRoutes {
    const val HOME = "home"
    const val STUDENTS = "students?sort={sort}"
    const val SETTINGS = "settings"

    const val MODEL_LIST = "models"
    const val MODEL_EDIT = "model_edit?configId={configId}"
    const val BACKUP = "backup"
    const val SENSITIVE_WORDS = "sensitive_words"
    const val TRASH = "trash"

    const val STUDENT_ALBUM = "student/{studentId}?tab={tab}"
    const val STUDENT_EDIT = "student_edit?studentId={studentId}"
    const val SCORE_EDIT = "score_edit?studentId={studentId}&scoreId={scoreId}"
    const val MISTAKE_EDIT = "mistake_edit?studentId={studentId}&mistakeId={mistakeId}"
    const val MISTAKE_CROP = "mistake_crop/{feedbackId}"
    const val MISTAKE_CONFIRM = "mistake_confirm"
    const val MISTAKE_EXPORT = "mistake_export/{studentId}"
    const val IMPORT_HISTORY = "import_history/{studentId}"
    const val WRITING_STYLE = "writing_style/{studentId}"
    const val TEMPLATE_EDIT = "template_edit?studentId={studentId}&global={global}"
    const val FEEDBACK_COMPOSE = "feedback/new?studentId={studentId}"
    const val FEEDBACK_DETAIL = "feedback/{feedbackId}"

    const val ARG_CONFIG_ID = "configId"
    const val ARG_STUDENT_ID = "studentId"
    const val ARG_FEEDBACK_ID = "feedbackId"
    const val ARG_SCORE_ID = "scoreId"
    const val ARG_MISTAKE_ID = "mistakeId"
    const val ARG_SORT = "sort"
    const val ARG_TAB = "tab"
    const val ARG_GLOBAL = "global"

    const val SORT_RECENT = "recent"
    const val SORT_PENDING = "pending"
    const val SORT_NAME = "name"
    const val SORT_CLASS_TIME = "class_time"

    const val TAB_OVERVIEW = 0
    const val TAB_FEEDBACK = 1
    const val TAB_PHOTOS = 2
    const val TAB_SCORES = 3
    const val TAB_MISTAKES = 4
    const val TAB_MEMORY = 5

    fun students(sort: String = SORT_RECENT): String = "students?sort=$sort"

    fun modelEdit(configId: Long = -1L): String = "model_edit?configId=$configId"

    fun studentAlbum(studentId: Long, tab: Int = TAB_OVERVIEW): String = "student/$studentId?tab=$tab"

    fun studentEdit(studentId: Long = -1L): String = "student_edit?studentId=$studentId"

    fun scoreEdit(studentId: Long, scoreId: Long = -1L): String =
        "score_edit?studentId=$studentId&scoreId=$scoreId"

    fun mistakeEdit(studentId: Long, mistakeId: Long = -1L): String =
        "mistake_edit?studentId=$studentId&mistakeId=$mistakeId"

    fun mistakeCrop(feedbackId: Long): String = "mistake_crop/$feedbackId"

    fun mistakeExport(studentId: Long): String = "mistake_export/$studentId"

    fun importHistory(studentId: Long): String = "import_history/$studentId"

    fun writingStyle(studentId: Long): String = "writing_style/$studentId"

    fun templateEdit(studentId: Long, global: Boolean = false): String =
        "template_edit?studentId=$studentId&global=$global"

    fun feedbackCompose(studentId: Long): String = "feedback/new?studentId=$studentId"

    fun feedbackDetail(feedbackId: Long): String = "feedback/$feedbackId"
}
