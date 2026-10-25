package com.kefan.app

import android.app.Application
import com.kefan.app.data.preferences.AppPreferences
import com.kefan.app.data.repository.FeedbackRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KeFanApplication : Application() {

    @Inject
    lateinit var feedbackRepository: FeedbackRepository

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        // 启动时清理回收站里超过保留天数的反馈
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                feedbackRepository.purgeExpired(preferences.trashRetentionDays.first())
            }
        }
    }
}
