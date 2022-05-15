package io.github.wulkanowy.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "StudentAuthData", indices = [Index(
    value = ["email"],
    unique = true,
)])
data class StudentAuthData(
    val email: String,
    val version: AuthVersion,
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

enum class AuthVersion {
    /**
     * Custom encryption in database
     */
    V0_Legacy,

    /**
     * Custom encryption in shared prefs
     */
    V1_Custom,

    /**
     * Preferred, uses androidx.security.EncryptedSharedPrefs
     */
    V1_AndroidX,
}
