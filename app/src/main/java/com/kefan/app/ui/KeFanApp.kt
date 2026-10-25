package com.kefan.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kefan.app.core.design.KfBg
import com.kefan.app.core.design.KfBottomBar
import com.kefan.app.core.design.KfBottomTab
import com.kefan.app.feature.home.HomeScreen
import com.kefan.app.feature.feedback.FeedbackComposeScreen
import com.kefan.app.feature.feedback.FeedbackDetailScreen
import com.kefan.app.feature.mistake.MistakeConfirmScreen
import com.kefan.app.feature.mistake.MistakeCropScreen
import com.kefan.app.feature.mistake.MistakeEditScreen
import com.kefan.app.feature.mistake.MistakeExportScreen
import com.kefan.app.feature.model.ModelEditScreen
import com.kefan.app.feature.model.ModelListScreen
import com.kefan.app.feature.score.ScoreEditScreen
import com.kefan.app.feature.settings.BackupScreen
import com.kefan.app.feature.settings.SensitiveWordsScreen
import com.kefan.app.feature.settings.SettingsScreen
import com.kefan.app.feature.settings.TrashScreen
import com.kefan.app.feature.student.StudentAlbumScreen
import com.kefan.app.feature.student.StudentEditScreen
import com.kefan.app.feature.student.StudentsScreen
import com.kefan.app.feature.template.ImportHistoryScreen
import com.kefan.app.feature.template.TemplateEditScreen
import com.kefan.app.feature.template.WritingStyleScreen
import com.kefan.app.navigation.KfRoutes

private val topLevelRoutes = setOf(
    KfRoutes.HOME,
    KfRoutes.STUDENTS,
    KfRoutes.SETTINGS
)

private val bottomTabs = listOf(
    KfBottomTab(KfRoutes.HOME, "首页", Icons.Outlined.Home),
    KfBottomTab(KfRoutes.STUDENTS, "学生", Icons.Outlined.People),
    KfBottomTab(KfRoutes.SETTINGS, "设置", Icons.Outlined.Settings)
)

@Composable
fun KeFanApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute?.let { it in topLevelRoutes } == true

    fun openStudents(sort: String) {
        if (currentRoute == KfRoutes.STUDENTS) return
        navController.navigate(KfRoutes.students(sort)) {
            popUpTo(KfRoutes.HOME) { saveState = true }
            launchSingleTop = true
        }
    }

    fun selectTab(route: String) {
        if (route == currentRoute) return
        when (route) {
            KfRoutes.STUDENTS -> openStudents(KfRoutes.SORT_CLASS_TIME)
            else -> navController.navigate(route) {
                popUpTo(KfRoutes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = KfBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showBottomBar) {
                KfBottomBar(
                    tabs = bottomTabs,
                    currentRoute = currentRoute,
                    onSelect = { selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KfRoutes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(KfRoutes.HOME) {
                HomeScreen(
                    onOpenSettings = { selectTab(KfRoutes.SETTINGS) },
                    onOpenStudents = { openStudents(KfRoutes.SORT_RECENT) },
                    onOpenPendingMistakes = { openStudents(KfRoutes.SORT_PENDING) },
                    onOpenStudent = { id ->
                        navController.navigate(KfRoutes.studentAlbum(id))
                    },
                    onNewFeedback = { id ->
                        navController.navigate(KfRoutes.feedbackCompose(id))
                    },
                    onOpenFeedback = { id ->
                        navController.navigate(KfRoutes.feedbackDetail(id))
                    }
                )
            }
            composable(
                route = KfRoutes.STUDENTS,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_SORT) {
                        type = NavType.StringType
                        defaultValue = KfRoutes.SORT_RECENT
                    }
                )
            ) {
                StudentsScreen(
                    onAddStudent = { navController.navigate(KfRoutes.studentEdit()) },
                    onOpenStudent = { studentId ->
                        navController.navigate(KfRoutes.studentAlbum(studentId))
                    },
                    onOpenStudentMistakes = { studentId ->
                        navController.navigate(
                            KfRoutes.studentAlbum(studentId, KfRoutes.TAB_MISTAKES)
                        )
                    }
                )
            }
            composable(
                route = KfRoutes.STUDENT_EDIT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                StudentEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = KfRoutes.STUDENT_ALBUM,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType },
                    navArgument(KfRoutes.ARG_TAB) {
                        type = NavType.IntType
                        defaultValue = KfRoutes.TAB_OVERVIEW
                    }
                )
            ) { entry ->
                val studentId = entry.arguments?.getLong(KfRoutes.ARG_STUDENT_ID) ?: -1L
                val initialTab = entry.arguments?.getInt(KfRoutes.ARG_TAB) ?: KfRoutes.TAB_OVERVIEW
                StudentAlbumScreen(
                    initialTab = initialTab,
                    onBack = { navController.popBackStack() },
                    onEditStudent = { id ->
                        navController.navigate(KfRoutes.studentEdit(id))
                    },
                    onOpenWritingStyle = { id ->
                        navController.navigate(KfRoutes.writingStyle(id))
                    },
                    onOpenImport = { id ->
                        navController.navigate(KfRoutes.importHistory(id))
                    },
                    onNewFeedback = { id ->
                        navController.navigate(KfRoutes.feedbackCompose(id))
                    },
                    onOpenFeedback = { id ->
                        navController.navigate(KfRoutes.feedbackDetail(id))
                    },
                    onOpenScoreEdit = { studentId, scoreId ->
                        navController.navigate(KfRoutes.scoreEdit(studentId, scoreId))
                    },
                    onOpenMistake = { studentId, mistakeId ->
                        navController.navigate(KfRoutes.mistakeEdit(studentId, mistakeId))
                    },
                    onExportMistakes = { studentId ->
                        navController.navigate(KfRoutes.mistakeExport(studentId))
                    }
                )
            }
            composable(
                route = KfRoutes.SCORE_EDIT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType },
                    navArgument(KfRoutes.ARG_SCORE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                ScoreEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = KfRoutes.MISTAKE_EDIT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType },
                    navArgument(KfRoutes.ARG_MISTAKE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                MistakeEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = KfRoutes.MISTAKE_CROP,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_FEEDBACK_ID) { type = NavType.LongType }
                )
            ) {
                MistakeCropScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConfirm = {
                        navController.navigate(KfRoutes.MISTAKE_CONFIRM)
                    }
                )
            }
            composable(KfRoutes.MISTAKE_CONFIRM) {
                MistakeConfirmScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        if (!navController.popBackStack(KfRoutes.MISTAKE_CROP, inclusive = true)) {
                            navController.popBackStack()
                        }
                    }
                )
            }
            composable(
                route = KfRoutes.MISTAKE_EXPORT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType }
                )
            ) {
                MistakeExportScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = KfRoutes.IMPORT_HISTORY,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType }
                )
            ) {
                ImportHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                    onOpenModels = { navController.navigate(KfRoutes.MODEL_LIST) }
                )
            }
            composable(
                route = KfRoutes.WRITING_STYLE,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val studentId = entry.arguments?.getLong(KfRoutes.ARG_STUDENT_ID) ?: -1L
                WritingStyleScreen(
                    onBack = { navController.popBackStack() },
                    onEditTemplate = {
                        navController.navigate(KfRoutes.templateEdit(studentId))
                    },
                    onImportHistory = {
                        navController.navigate(KfRoutes.importHistory(studentId))
                    },
                    onOpenModels = { navController.navigate(KfRoutes.MODEL_LIST) }
                )
            }
            composable(
                route = KfRoutes.TEMPLATE_EDIT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(KfRoutes.ARG_GLOBAL) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val isGlobal = entry.arguments?.getBoolean(KfRoutes.ARG_GLOBAL) ?: false
                TemplateEditScreen(
                    onBack = { navController.popBackStack() },
                    isGlobal = isGlobal
                )
            }
            composable(
                route = KfRoutes.FEEDBACK_COMPOSE,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_STUDENT_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val studentId = entry.arguments?.getLong(KfRoutes.ARG_STUDENT_ID) ?: -1L
                FeedbackComposeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navController.navigate(KfRoutes.MODEL_LIST) },
                    onOpenWritingStyle = {
                        navController.navigate(KfRoutes.writingStyle(studentId))
                    },
                    onSaved = { feedbackId ->
                        navController.navigate(KfRoutes.feedbackDetail(feedbackId)) {
                            popUpTo(KfRoutes.FEEDBACK_COMPOSE) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = KfRoutes.FEEDBACK_DETAIL,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_FEEDBACK_ID) { type = NavType.LongType }
                )
            ) {
                FeedbackDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMistakeCrop = { feedbackId ->
                        navController.navigate(KfRoutes.mistakeCrop(feedbackId))
                    }
                )
            }
            composable(KfRoutes.SETTINGS) {
                SettingsScreen(
                    onOpenModels = { navController.navigate(KfRoutes.MODEL_LIST) },
                    onOpenBackup = { navController.navigate(KfRoutes.BACKUP) },
                    onOpenGlobalTemplate = {
                        navController.navigate(KfRoutes.templateEdit(0L, global = true))
                    },
                    onOpenSensitiveWords = {
                        navController.navigate(KfRoutes.SENSITIVE_WORDS)
                    },
                    onOpenTrash = {
                        navController.navigate(KfRoutes.TRASH)
                    }
                )
            }
            composable(KfRoutes.SENSITIVE_WORDS) {
                SensitiveWordsScreen(onBack = { navController.popBackStack() })
            }
            composable(KfRoutes.TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(KfRoutes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(KfRoutes.MODEL_LIST) {
                ModelListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(KfRoutes.modelEdit()) },
                    onEdit = { id -> navController.navigate(KfRoutes.modelEdit(id)) }
                )
            }
            composable(
                route = KfRoutes.MODEL_EDIT,
                arguments = listOf(
                    navArgument(KfRoutes.ARG_CONFIG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                ModelEditScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
