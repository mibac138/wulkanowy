package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.TestDispatchersProvider
import io.github.wulkanowy.data.SdkFactory
import io.github.wulkanowy.data.db.dao.SemesterDao
import io.github.wulkanowy.data.db.dao.StudentDao
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.sdk.pojo.Student
import io.github.wulkanowy.utils.AppInfo
import io.github.wulkanowy.utils.init
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StudentTest {

    @MockK
    private lateinit var sdkFactory: SdkFactory

    @MockK
    private lateinit var studentDb: StudentDao

    @MockK
    private lateinit var semesterDb: SemesterDao

    @MockK
    private lateinit var authDb: AuthDataRepository

    private lateinit var studentRepository: StudentRepository

    @Before
    fun initApi() {
        MockKAnnotations.init(this)
        studentRepository = StudentRepository(
            studentDb = studentDb,
            semesterDb = semesterDb,
            sdk = sdkFactory,
            appInfo = AppInfo(),
            appDatabase = mockk(),
            authDb = authDb,
        )
    }

    @Test
    fun testRemoteAll() {
        val sdk = mockk<Sdk>()
        coEvery { sdk.getStudentsFromScrapper(any(), any(), any(), any()) } returns listOf(
            getStudent("test")
        )
        val studentSlot = slot<io.github.wulkanowy.data.db.entities.Student>()
        coEvery { sdkFactory.init(capture(studentSlot)) } answers {
            sdk.init(studentSlot.captured)
        }
        coEvery { sdkFactory.initUnauthorized() } returns sdk

        val students = runBlocking { studentRepository.getStudentsScrapper("", "", "http://fakelog.cf", "") }
        assertEquals(1, students.size)
        assertEquals("test Kowalski", students.first().student.studentName)
    }

    private fun getStudent(name: String): Student {
        return Student(
            email = "",
            symbol = "",
            studentId = 0,
            userLoginId = 0,
            userLogin = "",
            userName = "",
            studentName = name,
            studentSurname = "Kowalski",
            schoolSymbol = "",
            schoolShortName = "",
            schoolName = "",
            className = "",
            classId = 0,
            certificateKey = "",
            privateKey = "",
            loginMode = Sdk.Mode.SCRAPPER,
            mobileBaseUrl = "",
            loginType = Sdk.ScrapperLoginType.STANDARD,
            scrapperBaseUrl = "",
            isParent = false,
            semesters = emptyList()
        )
    }
}
