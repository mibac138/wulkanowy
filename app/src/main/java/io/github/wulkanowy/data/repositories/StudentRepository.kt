package io.github.wulkanowy.data.repositories

import androidx.room.withTransaction
import io.github.wulkanowy.data.db.AppDatabase
import io.github.wulkanowy.data.db.dao.PasswordDao
import io.github.wulkanowy.data.db.dao.SemesterDao
import io.github.wulkanowy.data.db.dao.StudentDao
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentNickAndAvatar
import io.github.wulkanowy.data.db.entities.StudentWithSemesters
import io.github.wulkanowy.data.exceptions.NoCurrentStudentException
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.utils.AppInfo
import io.github.wulkanowy.utils.DispatchersProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dispatchers: DispatchersProvider,
    private val studentDb: StudentDao,
    private val semesterDb: SemesterDao,
    private val passwordDao: PasswordDao,
    private val sdk: Sdk,
    private val appInfo: AppInfo,
    private val appDatabase: AppDatabase,
) {

    suspend fun isStudentSaved() = getSavedStudents(false).isNotEmpty()

    suspend fun isCurrentStudentSet() = studentDb.loadCurrent()?.isCurrent ?: false

    suspend fun getStudentsApi(
        pin: String,
        symbol: String,
        token: String
    ): List<StudentWithSemesters> =
        sdk.getStudentsFromMobileApi(token, pin, symbol, "")
            .mapToEntities(colors = appInfo.defaultColorsForAvatar)

    suspend fun getStudentsScrapper(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        symbol: String
    ): List<StudentWithSemesters> =
        sdk.getStudentsFromScrapper(email, password, scrapperBaseUrl, symbol)
            .mapToEntities(password, appInfo.defaultColorsForAvatar)

    suspend fun getStudentsHybrid(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        symbol: String
    ): List<StudentWithSemesters> =
        sdk.getStudentsHybrid(email, password, scrapperBaseUrl, "", symbol)
            .mapToEntities(password, appInfo.defaultColorsForAvatar)

    suspend fun getSavedStudents(decryptPass: Boolean = true) =
        studentDb.loadStudentsWithSemesters()
            .onEach { (student, _semesters) ->
                loadPassword(student, decryptPass)
            }

    suspend fun getSavedStudentById(id: Long, decryptPass: Boolean = true) =
        studentDb.loadStudentWithSemestersById(id)?.apply {
            loadPassword(student, decryptPass)
        }

    suspend fun getStudentById(id: Long, decryptPass: Boolean = true): Student {
        val student = studentDb.loadById(id) ?: throw NoCurrentStudentException()
        loadPassword(student, decryptPass)

        return student
    }

    suspend fun getCurrentStudent(decryptPass: Boolean = true): Student {
        val student = studentDb.loadCurrent() ?: throw NoCurrentStudentException()
        loadPassword(student, decryptPass)

        return student
    }

    suspend fun saveStudents(studentsWithSemesters: List<StudentWithSemesters>) {
        val semesters = studentsWithSemesters.flatMap { it.semesters }
        val students = studentsWithSemesters.map { it.student }
            .map { student ->
                if (Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.API) {
                    student.copy().also {
                        it.nick = student.nick
                        it.avatarColor = student.avatarColor
                        it.newPasswordStorage = student.newPasswordStorage
                        passwordDao.savePassword(it)
                    }
                } else student
            }
            .mapIndexed { index, student ->
                if (index == 0) {
                    student.copy(isCurrent = true).apply {
                        nick = student.nick
                        avatarColor = student.avatarColor
                        newPasswordStorage = student.newPasswordStorage
                    }
                } else student
            }

        appDatabase.withTransaction {
            studentDb.resetCurrent()
            semesterDb.insertSemesters(semesters)
            studentDb.insertAll(students)
        }
    }

    suspend fun switchStudent(studentWithSemesters: StudentWithSemesters) {
        studentDb.switchCurrent(studentWithSemesters.student.id)
    }

    suspend fun logoutStudent(student: Student) = studentDb.delete(student)

    suspend fun updateStudentNickAndAvatar(studentNickAndAvatar: StudentNickAndAvatar) =
        studentDb.update(studentNickAndAvatar)

    suspend fun isOneUniqueStudent() = getSavedStudents(false)
        .distinctBy { it.student.studentName }.size == 1

    private suspend fun loadPassword(student: Student, decryptPass: Boolean = false) {
        if (decryptPass && Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.API) {
            withContext(dispatchers.io) {
                passwordDao.loadPassword(student)
            }
        }
    }
}
