package com.superxiao.budgettracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BudgetPeriodEntity::class,
        QuickButtonEntity::class,
        ExpenseRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: BudgetDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                // 1. 新建多周期表 budget_periods
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_periods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `totalBudgetCents` INTEGER NOT NULL,
                        `startDateEpochDay` INTEGER NOT NULL,
                        `endDateEpochDay` INTEGER NOT NULL,
                        `customDailyBudgetCents` INTEGER,
                        `isActive` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // 从旧 budget_config 迁移到 id=1 周期
                db.execSQL(
                    """
                    INSERT INTO `budget_periods` (
                        `id`, `totalBudgetCents`, `startDateEpochDay`, `endDateEpochDay`,
                        `customDailyBudgetCents`, `isActive`, `createdAtMillis`
                    )
                    SELECT
                        1,
                        CAST(ROUND(`totalBudget` * 100.0) AS INTEGER),
                        `startDateEpochDay`,
                        `endDateEpochDay`,
                        CASE WHEN `customDailyBudget` IS NULL THEN NULL ELSE CAST(ROUND(`customDailyBudget` * 100.0) AS INTEGER) END,
                        1,
                        $now
                    FROM `budget_config`
                    WHERE `id` = 1
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS `budget_config`")

                // 2. 迁移 quick_buttons 到整数分
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quick_buttons_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `label` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `quick_buttons_new` (`id`, `label`, `amountCents`, `sortOrder`)
                    SELECT `id`, `label`, CAST(ROUND(`amount` * 100.0) AS INTEGER), `sortOrder`
                    FROM `quick_buttons`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `quick_buttons`")
                db.execSQL("ALTER TABLE `quick_buttons_new` RENAME TO `quick_buttons`")

                // 3. 迁移 expense_records 到整数分 + periodId 外键
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `periodId` INTEGER NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestampMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`periodId`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `expense_records_new` (`id`, `periodId`, `amountCents`, `note`, `timestampMillis`)
                    SELECT `id`, 1, CAST(ROUND(`amount` * 100.0) AS INTEGER), `note`, `timestampMillis`
                    FROM `expense_records`
                    WHERE EXISTS (SELECT 1 FROM `budget_periods` WHERE `id` = 1)
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `expense_records`")
                db.execSQL("ALTER TABLE `expense_records_new` RENAME TO `expense_records`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_records_periodId_timestampMillis` ON `expense_records` (`periodId`, `timestampMillis`)"
                )
            }
        }

        fun getInstance(context: Context): BudgetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BudgetDatabase::class.java,
                    "budget_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
