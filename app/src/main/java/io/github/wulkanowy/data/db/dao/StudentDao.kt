package io.github.wulkanowy.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentName
import io.github.wulkanowy.data.db.entities.StudentNickAndAvatar
import javax.inject.Singleton

@Singleton
@Dao
abstract class StudentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAll(student: List<Student>): List<Long>

    @Delete
    abstract suspend fun delete(student: Student)

    @Update(entity = Student::class)
    abstract suspend fun update(studentNickAndAvatar: StudentNickAndAvatar)

    @Update(entity = Student::class)
    abstract suspend fun update(studentName: StudentName)

    @Query("SELECT * FROM Students WHERE is_current = 1")
    abstract suspend fun loadCurrent(): Student?

    @Query("SELECT * FROM Students WHERE id = :id")
    abstract suspend fun loadById(id: Long): Student?

    @Query("SELECT * FROM Students")
    abstract suspend fun loadAll(): List<Student>

    @Transaction
    @Query("SELECT * FROM Students JOIN Semesters ON Students.student_id = Semesters.student_id AND Students.class_id = Semesters.class_id")
    abstract suspend fun loadStudentsWithSemesters(): Map<Student, List<Semester>>

    @Transaction
    @Query("SELECT * FROM Students JOIN Semesters ON Students.student_id = Semesters.student_id AND Students.class_id = Semesters.class_id WHERE Students.id = :id")
    abstract suspend fun loadStudentWithSemestersById(id: Long): Map<Student, List<Semester>>

    @Query("UPDATE Students SET is_current = 1 WHERE id = :id")
    abstract suspend fun updateCurrent(id: Long)

    @Query("UPDATE Students SET is_current = 0")
    abstract suspend fun resetCurrent()

    @Query("DELETE FROM Students WHERE email = :email AND user_name = :userName")
    abstract suspend fun deleteByEmailAndUserName(email: String, userName: String)

    @Transaction
    open suspend fun switchCurrent(id: Long) {
        resetCurrent()
        updateCurrent(id)
    }
}
