package com.kefan.app.di

import android.content.Context
import androidx.room.Room
import com.kefan.app.data.local.AppDatabase
import com.kefan.app.data.local.dao.ContextLogDao
import com.kefan.app.data.local.dao.ExportJobDao
import com.kefan.app.data.local.dao.FeedbackDao
import com.kefan.app.data.local.dao.FeedbackFtsDao
import com.kefan.app.data.local.dao.FeedbackImageDao
import com.kefan.app.data.local.dao.FeedbackTemplateDao
import com.kefan.app.data.local.dao.ManualSampleDao
import com.kefan.app.data.local.dao.MemoryCardDao
import com.kefan.app.data.local.dao.MistakeFtsDao
import com.kefan.app.data.local.dao.MistakeImageDao
import com.kefan.app.data.local.dao.MistakeRecordDao
import com.kefan.app.data.local.dao.ModelConfigDao
import com.kefan.app.data.local.dao.ReviewLogDao
import com.kefan.app.data.local.dao.ScoreRecordDao
import com.kefan.app.data.local.dao.StudentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()

    @Provides
    fun provideMemoryCardDao(db: AppDatabase): MemoryCardDao = db.memoryCardDao()

    @Provides
    fun provideManualSampleDao(db: AppDatabase): ManualSampleDao = db.manualSampleDao()

    @Provides
    fun provideFeedbackDao(db: AppDatabase): FeedbackDao = db.feedbackDao()

    @Provides
    fun provideFeedbackImageDao(db: AppDatabase): FeedbackImageDao = db.feedbackImageDao()

    @Provides
    fun provideFeedbackTemplateDao(db: AppDatabase): FeedbackTemplateDao = db.feedbackTemplateDao()

    @Provides
    fun provideContextLogDao(db: AppDatabase): ContextLogDao = db.contextLogDao()

    @Provides
    fun provideScoreRecordDao(db: AppDatabase): ScoreRecordDao = db.scoreRecordDao()

    @Provides
    fun provideMistakeRecordDao(db: AppDatabase): MistakeRecordDao = db.mistakeRecordDao()

    @Provides
    fun provideMistakeImageDao(db: AppDatabase): MistakeImageDao = db.mistakeImageDao()

    @Provides
    fun provideReviewLogDao(db: AppDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideExportJobDao(db: AppDatabase): ExportJobDao = db.exportJobDao()

    @Provides
    fun provideModelConfigDao(db: AppDatabase): ModelConfigDao = db.modelConfigDao()

    @Provides
    fun provideFeedbackFtsDao(db: AppDatabase): FeedbackFtsDao = db.feedbackFtsDao()

    @Provides
    fun provideMistakeFtsDao(db: AppDatabase): MistakeFtsDao = db.mistakeFtsDao()
}
