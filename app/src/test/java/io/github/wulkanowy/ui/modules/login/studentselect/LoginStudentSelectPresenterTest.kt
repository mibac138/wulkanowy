package io.github.wulkanowy.ui.modules.login.studentselect

import io.github.wulkanowy.MainCoroutineRule
import io.github.wulkanowy.data.pojos.RegisterStudent
import io.github.wulkanowy.data.pojos.RegisterSymbol
import io.github.wulkanowy.data.pojos.RegisterUnit
import io.github.wulkanowy.data.pojos.RegisterUser
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.github.wulkanowy.services.sync.SyncManager
import io.github.wulkanowy.ui.modules.login.LoginData
import io.github.wulkanowy.ui.modules.login.LoginErrorHandler
import io.github.wulkanowy.utils.AnalyticsHelper
import io.github.wulkanowy.utils.AppInfo
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginStudentSelectPresenterTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    @MockK(relaxed = true)
    lateinit var errorHandler: LoginErrorHandler

    @MockK
    lateinit var loginStudentSelectView: LoginStudentSelectView

    @MockK
    lateinit var studentRepository: StudentRepository

    @MockK(relaxed = true)
    lateinit var analytics: AnalyticsHelper

    @MockK(relaxed = true)
    lateinit var syncManager: SyncManager

    private val appInfo = AppInfo()

    private lateinit var presenter: LoginStudentSelectPresenter

    private val loginData = LoginData(
        login = "",
        password = "",
        baseUrl = "",
        symbol = null,
    )

    private val subject = RegisterStudent(
        studentId = 0,
        studentName = "",
        studentSecondName = "",
        studentSurname = "",
        className = "",
        classId = 0,
        isParent = false,
        semesters = listOf(),
    )

    private val school = RegisterUnit(
        userLoginId = 0,
        schoolId = "",
        schoolName = "",
        schoolShortName = "",
        parentIds = listOf(),
        studentIds = listOf(),
        employeeIds = listOf(),
        error = null,
        students = listOf(subject)
    )

    private val symbol = RegisterSymbol(
        symbol = "",
        error = null,
        userName = "",
        schools = listOf(school),
    )

    private val registerUser = RegisterUser(
        email = "",
        password = "",
        login = "",
        baseUrl = "",
        loginType = Scrapper.LoginType.AUTO,
        symbols = listOf(symbol),
    )

    private val testException by lazy { RuntimeException("Problem") }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        clearMocks(studentRepository, loginStudentSelectView)

        coEvery { studentRepository.getSavedStudents() } returns emptyList()

        every { loginStudentSelectView.initView() } just Runs
        every { loginStudentSelectView.symbols } returns emptyMap()

        every { loginStudentSelectView.enableSignIn(any()) } just Runs
        every { loginStudentSelectView.showProgress(any()) } just Runs
        every { loginStudentSelectView.showContent(any()) } just Runs

        presenter = LoginStudentSelectPresenter(
            studentRepository = studentRepository,
            loginErrorHandler = errorHandler,
            syncManager = syncManager,
            analytics = analytics,
            appInfo = appInfo,
        )
    }

    @Test
    fun initViewTest() {
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)
        verify { loginStudentSelectView.initView() }
    }

    @Test
    fun onSelectedStudentTest() {
        val itemsSlot = slot<List<LoginStudentSelectItem>>()
        every { loginStudentSelectView.updateData(capture(itemsSlot)) } just Runs
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)

        coEvery { studentRepository.saveStudents(any()) } just Runs

        every { loginStudentSelectView.navigateToNext() } just Runs

        itemsSlot.captured.filterIsInstance<LoginStudentSelectItem.Student>().first().let {
            it.onClick(it)
        }
        presenter.onSignIn()

        verify { loginStudentSelectView.showContent(false) }
        verify { loginStudentSelectView.showProgress(true) }
        verify { loginStudentSelectView.navigateToNext() }
    }

    @Test
    fun onSelectedStudentErrorTest() {
        val itemsSlot = slot<List<LoginStudentSelectItem>>()
        every { loginStudentSelectView.updateData(capture(itemsSlot)) } just Runs
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)

        coEvery { studentRepository.saveStudents(any()) } throws testException

        itemsSlot.captured.filterIsInstance<LoginStudentSelectItem.Student>().first().let {
            it.onClick(it)
        }
        presenter.onSignIn()

        verify { loginStudentSelectView.showContent(false) }
        verify { loginStudentSelectView.showProgress(true) }
        verify { errorHandler.dispatch(match { testException.message == it.message }) }
    }
}
