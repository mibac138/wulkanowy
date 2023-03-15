package io.github.wulkanowy.data.db.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration56 : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `StudentAuthData`(email, version) SELECT DISTINCT email, 'V0_Legacy' FROM `Students`")
    }
}
