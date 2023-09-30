package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.api.SchoolsService
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentWithSemesters
import io.github.wulkanowy.data.pojos.IntegrityRequest
import io.github.wulkanowy.data.pojos.LoginEvent
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.ui.modules.login.LoginData
import io.github.wulkanowy.utils.IntegrityHelper
import io.github.wulkanowy.utils.getCurrentOrLast
import io.github.wulkanowy.utils.init
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class SchoolsRepository @Inject constructor(
    private val integrityHelper: IntegrityHelper,
    private val schoolsService: SchoolsService,
    private val sdk: Sdk,
) {

    suspend fun logSchoolLogin(loginData: LoginData, students: List<StudentWithSemesters>) {
        students.forEach {
            runCatching {
                withTimeout(10.seconds) {
                    logLogin(loginData, it.student, it.semesters.getCurrentOrLast())
                }
            }
                .onFailure { Timber.e(it) }
        }
    }

    private suspend fun logLogin(loginData: LoginData, student: Student, semester: Semester) {
        val requestId = UUID.randomUUID().toString()
        val token = integrityHelper.getIntegrityToken(requestId) ?: return

        val schoolInfo = sdk
            .init(student.copy(password = loginData.password))
            .switchDiary(
                diaryId = semester.diaryId,
                kindergartenDiaryId = semester.kindergartenDiaryId,
                schoolYear = semester.schoolYear
            )
            .getSchool()

        schoolsService.logLoginEvent(
            IntegrityRequest(
                tokenString = token,
                data = LoginEvent(
                    uuid = requestId,
                    schoolAddress = schoolInfo.address,
                    schoolName = schoolInfo.name,
                    schoolShort = student.schoolShortName,
                    scraperBaseUrl = student.scrapperBaseUrl,
                    loginType = student.loginType,
                    symbol = student.symbol,
                    schoolId = student.schoolSymbol,
                )
            )
        )
    }
}
