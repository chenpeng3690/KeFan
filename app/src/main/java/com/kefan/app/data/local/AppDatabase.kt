package com.kefan.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.kefan.app.data.local.entity.ContextLog
import com.kefan.app.data.local.entity.ExportJob
import com.kefan.app.data.local.entity.Feedback
import com.kefan.app.data.local.entity.FeedbackFts
import com.kefan.app.data.local.entity.FeedbackImage
import com.kefan.app.data.local.entity.FeedbackTemplate
import com.kefan.app.data.local.entity.ManualSample
import com.kefan.app.data.local.entity.MemoryCard
import com.kefan.app.data.local.entity.MistakeFts
import com.kefan.app.data.local.entity.MistakeImage
import com.kefan.app.data.local.entity.MistakeRecord
import com.kefan.app.data.local.entity.ModelConfig
import com.kefan.app.data.local.entity.ReviewLog
import com.kefan.app.data.local.entity.ScoreRecord
import com.kefan.app.data.local.entity.Student

@Database(
    entities = [
        Student::class,
        MemoryCard::class,
        ManualSample::class,
        FeedbackTemplate::class,
        Feedback::class,
        FeedbackImage::class,
        ContextLog::class,
        ScoreRecord::class,
        MistakeRecord::class,
        MistakeImage::class,
        ReviewLog::class,
        ExportJob::class,
        ModelConfig::class,
        FeedbackFts::class,
        MistakeFts::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun memoryCardDao(): MemoryCardDao
    abstract fun manualSampleDao(): ManualSampleDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun feedbackImageDao(): FeedbackImageDao
    abstract fun feedbackTemplateDao(): FeedbackTemplateDao
    abstract fun contextLogDao(): ContextLogDao
    abstract fun scoreRecordDao(): ScoreRecordDao
    abstract fun mistakeRecordDao(): MistakeRecordDao
    abstract fun mistakeImageDao(): MistakeImageDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun exportJobDao(): ExportJobDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun feedbackFtsDao(): FeedbackFtsDao
    abstract fun mistakeFtsDao(): MistakeFtsDao

    companion object {
        const val DB_NAME = "kefan.db"

        /** v2：学生增加每周上课日 / 上课时间（首页“今天要上课”）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE student ADD COLUMN classDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE student ADD COLUMN classTime TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3：反馈软删除，deletedAt 非空表示在回收站。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feedback ADD COLUMN deletedAt INTEGER")
            }
        }
    }
}
