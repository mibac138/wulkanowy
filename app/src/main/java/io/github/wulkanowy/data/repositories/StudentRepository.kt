package io.github.wulkanowy.data.repositories

import androidx.room.withTransaction
import io.github.wulkanowy.data.SdkFactory
import io.github.wulkanowy.data.db.AppDatabase
import io.github.wulkanowy.data.db.dao.SemesterDao
import io.github.wulkanowy.data.db.dao.StudentDao
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentName
import io.github.wulkanowy.data.db.entities.StudentNickAndAvatar
import io.github.wulkanowy.data.db.entities.StudentWithSemesters
import io.github.wulkanowy.data.exceptions.NoCurrentStudentException
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.data.mappers.mapToPojo
import io.github.wulkanowy.data.pojos.RegisterUser
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.utils.AppInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val studentDb: StudentDao,
    private val semesterDb: SemesterDao,
    private val authDb: AuthDataRepository,
    private val sdk: SdkFactory,
    private val appDatabase: AppDatabase,
) {

    suspend fun isStudentSaved() = getSavedStudents().isNotEmpty()

    suspend fun isCurrentStudentSet() = studentDb.loadCurrent()?.isCurrent ?: false

    suspend fun getStudentsApi(
        pin: String,
        symbol: String,
        token: String
    ): RegisterUser = sdk.initUnauthorized()
        .getStudentsFromHebe(token, pin, symbol, "")
        .mapToPojo(null)

    suspend fun getUserSubjectsFromScrapper(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        domainSuffix: String,
        symbol: String
    ): RegisterUser = sdk.initUnauthorized()
        .getUserSubjectsFromScrapper(email, password, scrapperBaseUrl, domainSuffix, symbol)
        .mapToPojo(password)

    suspend fun getStudentsHybrid(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        symbol: String
    ): RegisterUser = sdk.initUnauthorized()
        .getStudentsHybrid(email, password, scrapperBaseUrl, "", symbol)
        .mapToPojo(password)

    suspend fun getSavedStudents(): List<StudentWithSemesters> =
        studentDb.loadStudentsWithSemesters()

    suspend fun getSavedStudentById(id: Long): StudentWithSemesters? =
        studentDb.loadStudentWithSemestersById(id)

    suspend fun getStudentById(id: Long): Student =
        studentDb.loadById(id) ?: throw NoCurrentStudentException()

    suspend fun getCurrentStudent(): Student =
        studentDb.loadCurrent() ?: throw NoCurrentStudentException()

    suspend fun saveStudents(studentsWithSemesters: List<StudentWithSemesters>) {
        val semesters = studentsWithSemesters.flatMap { it.semesters }
        val students = studentsWithSemesters.map { it.student }
            .map { student ->
                if (Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE) {
                    student.copy().also {
                        it.nick = student.nick
                        it.avatarColor = student.avatarColor

                        @Suppress("DEPRECATION")
                        authDb.savePassword(student, student.password)
                    }
                } else student
            }
            .mapIndexed { index, student ->
                if (index == 0) {
                    student.copy(isCurrent = true).apply {
                        nick = student.nick
                        avatarColor = student.avatarColor
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

    suspend fun logoutStudent(student: Student) {
        studentDb.delete(student)
        if (!studentDb.isEmailUsed(student.email)) {
            authDb.removePassword(student)
        }
    }

    suspend fun updateStudentNickAndAvatar(studentNickAndAvatar: StudentNickAndAvatar) =
        studentDb.update(studentNickAndAvatar)

    suspend fun isOneUniqueStudent() = getSavedStudents()
        .distinctBy { it.student.studentName }.size == 1

    suspend fun authorizePermission(student: Student, semester: Semester, pesel: String) =
        sdk.init(student)
            .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
            .authorizePermission(pesel)

    suspend fun refreshStudentName(student: Student, semester: Semester) {
        val newCurrentApiStudent = sdk.init(student)
            .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
            .getCurrentStudent() ?: return

        val studentName = StudentName(
            studentName = "${newCurrentApiStudent.studentName} ${newCurrentApiStudent.studentSurname}"
        ).apply { id = student.id }

        studentDb.update(studentName)
    }
}
