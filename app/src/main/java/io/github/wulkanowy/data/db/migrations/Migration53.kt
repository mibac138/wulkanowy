package io.github.wulkanowy.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration53 : Migration(52, 53) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Students` ADD COLUMN `new_password_storage` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("INSERT INTO `StudentAuthData`(email, version) SELECT DISTINCT email, 'V0_Legacy' FROM `Students`")
    }
}
