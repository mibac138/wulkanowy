package io.github.wulkanowy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.wulkanowy.data.db.entities.StudentAuthData

@Dao
interface StudentAuthDataDao : BaseDao<StudentAuthData> {

    @Query("SELECT * FROM StudentAuthData WHERE email = :email")
    fun loadStudentAuthData(email: String): StudentAuthData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStudentAuthData(authData: StudentAuthData): Long
}
