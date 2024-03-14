package io.github.wulkanowy.ui.modules.dashboard

import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.dataOrNull
import io.github.wulkanowy.data.db.entities.AdminMessage
import io.github.wulkanowy.data.db.entities.LuckyNumber
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.enums.MessageFolder
import io.github.wulkanowy.data.enums.MessageType
import io.github.wulkanowy.data.errorOrNull
import io.github.wulkanowy.data.flatResourceFlow
import io.github.wulkanowy.data.logResourceStatus
import io.github.wulkanowy.data.mapResourceData
import io.github.wulkanowy.data.onResourceError
import io.github.wulkanowy.data.repositories.AttendanceSummaryRepository
import io.github.wulkanowy.data.repositories.ConferenceRepository
import io.github.wulkanowy.data.repositories.ExamRepository
import io.github.wulkanowy.data.repositories.GradeRepository
import io.github.wulkanowy.data.repositories.HomeworkRepository
import io.github.wulkanowy.data.repositories.LuckyNumberRepository
import io.github.wulkanowy.data.repositories.MessageRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SchoolAnnouncementRepository
import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.data.repositories.TimetableRepository
import io.github.wulkanowy.domain.adminmessage.GetAppropriateAdminMessageUseCase
import io.github.wulkanowy.domain.timetable.IsStudentHasLessonsOnWeekendUseCase
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.ui.base.ErrorHandler
import io.github.wulkanowy.ui.modules.dashboard.DashboardItem.Importance.NonBlocking
import io.github.wulkanowy.utils.AdsHelper
import io.github.wulkanowy.utils.calculatePercentage
import io.github.wulkanowy.utils.nextOrSameSchoolDay
import io.github.wulkanowy.utils.sunday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class DashboardPresenter @Inject constructor(
    errorHandler: ErrorHandler,
    studentRepository: StudentRepository,
    private val luckyNumberRepository: LuckyNumberRepository,
    private val gradeRepository: GradeRepository,
    private val semesterRepository: SemesterRepository,
    private val messageRepository: MessageRepository,
    private val attendanceSummaryRepository: AttendanceSummaryRepository,
    private val timetableRepository: TimetableRepository,
    private val isStudentHasLessonsOnWeekendUseCase: IsStudentHasLessonsOnWeekendUseCase,
    private val homeworkRepository: HomeworkRepository,
    private val examRepository: ExamRepository,
    private val conferenceRepository: ConferenceRepository,
    private val preferencesRepository: PreferencesRepository,
    private val schoolAnnouncementRepository: SchoolAnnouncementRepository,
    private val getAppropriateAdminMessageUseCase: GetAppropriateAdminMessageUseCase,
    private val adsHelper: AdsHelper
) : BasePresenter<DashboardView>(errorHandler, studentRepository) {

    private val dashboardItemLoadedList = mutableListOf<DashboardItem>()

    private val dashboardItemRefreshLoadedList = mutableListOf<DashboardItem>()

    private var dashboardItemsToLoad = emptySet<DashboardItem.Type>()

    private var dashboardTileLoadedList = emptySet<DashboardItem.Tile>()

    // List of types that have loaded actual data at least once
    private val firstLoadedItemList = mutableListOf<DashboardItem.Type>()

    private val selectedDashboardTiles
        get() = preferencesRepository.selectedDashboardTiles
            .filterNot { it == DashboardItem.Tile.ADS && !adsHelper.canShowAd }
            .toSet()

    private lateinit var lastError: Throwable

    override fun onAttachView(view: DashboardView) {
        super.onAttachView(view)

        with(view) {
            initView()
            showProgress(true)
            showContent(false)
        }

        val selectedDashboardTilesFlow = preferencesRepository.selectedDashboardTilesFlow
        val isAdsEnabledFlow =
            preferencesRepository.isAdsEnabledFlow.filter { (adsHelper.canShowAd && it) || !it }
        val isMobileAdsSdkInitializedFlow = adsHelper.isMobileAdsSdkInitialized.filter { it }

        merge(
            selectedDashboardTilesFlow, isAdsEnabledFlow, isMobileAdsSdkInitializedFlow
        ).onEach { loadData(tilesToLoad = selectedDashboardTiles) }.launch("dashboard_pref")
    }

    fun onAdminMessageDismissed(adminMessage: AdminMessage) {
        preferencesRepository.dismissedAdminMessageIds += adminMessage.id

        loadData(selectedDashboardTiles)
    }

    fun onDragAndDropEnd(list: List<DashboardItem>) {
        with(dashboardItemLoadedList) {
            clear()
            addAll(list)
        }

        val positionList =
            list.mapIndexed { index, dashboardItem -> Pair(dashboardItem.type, index) }.toMap()

        preferencesRepository.dashboardItemsPosition = positionList
    }

    fun loadData(
        tilesToLoad: Set<DashboardItem.Tile>,
        forceRefresh: Boolean = false,
    ) {
        val oldDashboardTileLoadedList = dashboardTileLoadedList
        dashboardItemsToLoad = tilesToLoad.map(DashboardItem.Tile::type).toSet()
        dashboardTileLoadedList = tilesToLoad

        val itemsToLoad = generateDashboardTileListToLoad(
            dashboardTilesToLoad = tilesToLoad,
            dashboardLoadedTiles = oldDashboardTileLoadedList,
            forceRefresh = forceRefresh
        ).map(DashboardItem.Tile::type)

        removeUnselectedTiles(tilesToLoad)
        loadTiles(tileList = itemsToLoad, forceRefresh = forceRefresh)
    }

    private fun generateDashboardTileListToLoad(
        dashboardTilesToLoad: Set<DashboardItem.Tile>,
        dashboardLoadedTiles: Set<DashboardItem.Tile>,
        forceRefresh: Boolean
    ) = dashboardTilesToLoad.filter { newItemToLoad ->
        forceRefresh || newItemToLoad.type.refreshBehavior == DashboardItem.RefreshBehavior.Always || dashboardLoadedTiles.none { it == newItemToLoad }
    }

    private fun removeUnselectedTiles(tilesToLoad: Collection<DashboardItem.Tile>) {
        dashboardItemLoadedList.removeAll { loadedTile -> dashboardItemsToLoad.none { it == loadedTile.type } }

        val horizontalGroup =
            dashboardItemLoadedList.firstNotNullOfOrNull { it as? DashboardItem.HorizontalGroup }

        if (horizontalGroup != null) {
            val isAttendanceToLoad = DashboardItem.Tile.ATTENDANCE in tilesToLoad
            val isMessagesToLoad = DashboardItem.Tile.MESSAGES in tilesToLoad
            val isLuckyNumberToLoad = DashboardItem.Tile.LUCKY_NUMBER in tilesToLoad

            val newHorizontalGroup = horizontalGroup.copy(
                attendancePercentage = horizontalGroup.attendancePercentage.takeIf { isAttendanceToLoad },
                unreadMessagesCount = horizontalGroup.unreadMessagesCount.takeIf { isMessagesToLoad },
                luckyNumber = horizontalGroup.luckyNumber.takeIf { isLuckyNumberToLoad }
            )

            val horizontalGroupIndex = dashboardItemLoadedList.indexOf(horizontalGroup)
            dashboardItemLoadedList[horizontalGroupIndex] = newHorizontalGroup
        }

        view?.updateData(dashboardItemLoadedList.filter(DashboardItem::canBeDisplayed))
    }

    private fun loadTiles(
        tileList: List<DashboardItem.Type>,
        forceRefresh: Boolean
    ) {
        presenterScope.launch {
            Timber.i("Loading dashboard account data started")
            val student = runCatching { studentRepository.getCurrentStudent(true) }
                .onFailure {
                    Timber.i("Loading dashboard account result: An exception occurred")
                    errorHandler.dispatch(it)
                    updateData(DashboardItem.Account(error = it), forceRefresh)
                }
                .onSuccess { Timber.i("Loading dashboard account result: Success") }
                .getOrNull() ?: return@launch

            tileList.forEach {
                when (it) {
                    DashboardItem.Type.ACCOUNT -> {
                        updateData(DashboardItem.Account(student), forceRefresh)
                    }

                    DashboardItem.Type.HORIZONTAL_GROUP -> {
                        loadHorizontalGroup(student, forceRefresh)
                    }

                    DashboardItem.Type.LESSONS -> loadLessons(student, forceRefresh)
                    DashboardItem.Type.GRADES -> loadGrades(student, forceRefresh)
                    DashboardItem.Type.HOMEWORK -> loadHomework(student, forceRefresh)
                    DashboardItem.Type.ANNOUNCEMENTS -> {
                        loadSchoolAnnouncements(student, forceRefresh)
                    }

                    DashboardItem.Type.EXAMS -> loadExams(student, forceRefresh)
                    DashboardItem.Type.CONFERENCES -> {
                        loadConferences(student, forceRefresh)
                    }

                    DashboardItem.Type.ADS -> loadAds(forceRefresh)
                    DashboardItem.Type.ADMIN_MESSAGE -> loadAdminMessage(student, forceRefresh)
                }
            }
        }
    }

    fun onSwipeRefresh() {
        Timber.i("Force refreshing the dashboard")
        loadData(selectedDashboardTiles, forceRefresh = true)
    }

    fun onRetry() {
        view?.run {
            showErrorView(false)
            showProgress(true)
        }
        loadData(selectedDashboardTiles, forceRefresh = true)
    }

    fun onRetryAfterCaptcha() {
        view?.run {
            showErrorView(false)
            showProgress(true)
        }
        loadData(selectedDashboardTiles, forceRefresh = true)
    }

    fun onViewReselected() {
        Timber.i("Dashboard view is reselected")
        view?.run {
            resetView()
            popViewToRoot()
        }
    }

    fun onDetailsClick() {
        view?.showErrorDetailsDialog(lastError)
    }

    fun onNotificationsCenterSelected(): Boolean {
        view?.openNotificationsCenterView()
        return true
    }

    fun onDashboardTileSettingsSelected(): Boolean {
        view?.showDashboardTileSettings(selectedDashboardTiles.toList())
        return true
    }

    fun onDashboardTileSettingSelected(selectedItems: List<String>) {
        preferencesRepository.selectedDashboardTiles = selectedItems.map {
            DashboardItem.Tile.valueOf(it)
        }.toSet()
    }

    fun onAdminMessageSelected(url: String?) {
        url?.let { view?.openInternetBrowser(it) }
    }

    private fun loadHorizontalGroup(student: Student, forceRefresh: Boolean) {
        flow {
            val selectedTiles = selectedDashboardTiles
            val flowSuccess = flowOf(Resource.Success(null))

            val luckyNumberFlow = luckyNumberRepository.getLuckyNumber(student, forceRefresh)
                .mapResourceData {
                    it ?: LuckyNumber(0, LocalDate.now(), 0)
                }
                .onResourceError { errorHandler.dispatch(it) }
                .takeIf { DashboardItem.Tile.LUCKY_NUMBER in selectedTiles } ?: flowSuccess

            val messageFlow = flatResourceFlow {
                val mailbox = messageRepository.getMailboxByStudent(student)

                messageRepository.getMessages(
                    student = student,
                    mailbox = mailbox,
                    folder = MessageFolder.RECEIVED,
                    forceRefresh = forceRefresh
                )
            }
                .mapResourceData { it.map { messageWithAuthor -> messageWithAuthor.message } }
                .onResourceError { errorHandler.dispatch(it) }
                .takeIf { DashboardItem.Tile.MESSAGES in selectedTiles } ?: flowSuccess

            val attendanceFlow = flatResourceFlow {
                val semester = semesterRepository.getCurrentSemester(student)
                attendanceSummaryRepository.getAttendanceSummary(
                    student = student,
                    semester = semester,
                    subjectId = -1,
                    forceRefresh = forceRefresh
                )
            }
                .onResourceError { errorHandler.dispatch(it) }
                .takeIf { DashboardItem.Tile.ATTENDANCE in selectedTiles } ?: flowSuccess

            emitAll(
                combine(
                    flow = luckyNumberFlow,
                    flow2 = messageFlow,
                    flow3 = attendanceFlow,
                ) { luckyNumberResource, messageResource, attendanceResource ->
                    val resList = listOf(luckyNumberResource, messageResource, attendanceResource)

                    resList to DashboardItem.HorizontalGroup(
                        isLoading = resList.any { it is Resource.Loading },
                        error = resList.map { it.errorOrNull }.let { errors ->
                            if (errors.all { it != null }) {
                                errors.firstOrNull()
                            } else null
                        },
                        attendancePercentage = DashboardItem.HorizontalGroup.Cell(
                            data = attendanceResource.dataOrNull?.calculatePercentage(),
                            error = attendanceResource.errorOrNull != null,
                            isLoading = attendanceResource is Resource.Loading,
                        ),
                        unreadMessagesCount = DashboardItem.HorizontalGroup.Cell(
                            data = messageResource.dataOrNull?.count { it.unread },
                            error = messageResource.errorOrNull != null,
                            isLoading = messageResource is Resource.Loading,
                        ),
                        luckyNumber = DashboardItem.HorizontalGroup.Cell(
                            data = luckyNumberResource.dataOrNull?.luckyNumber,
                            error = luckyNumberResource.errorOrNull != null,
                            isLoading = luckyNumberResource is Resource.Loading,
                        )
                    )
                })
        }
            .filterNot { (_, it) -> it.isLoading && forceRefresh }
            .distinctUntilChanged()
            .onEach { (_, it) ->
                updateData(it, forceRefresh)

                if (it.isLoading) {
                    Timber.i("Loading horizontal group data started")
                } else {
                    Timber.i("Loading horizontal group result: Success")
                }
            }
            .catch {
                Timber.i("Loading horizontal group result: An exception occurred")
                updateData(
                    DashboardItem.HorizontalGroup(error = it),
                    forceRefresh,
                )
                errorHandler.dispatch(it)
            }
            .launchWithUniqueRefreshJob("horizontal_group", forceRefresh)
    }

    private fun loadGrades(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            val semester = semesterRepository.getCurrentSemester(student)

            gradeRepository.getGrades(student, semester, forceRefresh)
        }
            .mapResourceData { (details, _) ->
                val filteredSubjectWithGrades = details
                    .filter { it.date >= LocalDate.now().minusDays(7) }
                    .groupBy { it.subject }
                    .mapValues { entry ->
                        entry.value
                            .take(5)
                            .sortedByDescending { it.date }
                    }
                    .toList()
                    .sortedByDescending { (_, grades) -> grades[0].date }
                    .toMap()

                filteredSubjectWithGrades
            }
            .logResourceStatus("Loading dashboard grades")
            .onEach {
                updateData(
                    DashboardItem.Grades(
                        subjectWithGrades = it.dataOrNull,
                        gradeTheme = preferencesRepository.gradeColorTheme,
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_grades", forceRefresh)
    }

    private fun loadLessons(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            val semester = semesterRepository.getCurrentSemester(student)
            val date = when (isStudentHasLessonsOnWeekendUseCase(semester)) {
                true -> LocalDate.now()
                else -> LocalDate.now().nextOrSameSchoolDay
            }

            timetableRepository.getTimetable(
                student = student,
                semester = semester,
                start = date,
                end = date.sunday,
                forceRefresh = forceRefresh,
            )
        }
            .logResourceStatus("Loading dashboard lessons")
            .onEach {
                updateData(
                    DashboardItem.Lessons(
                        lessons = it.dataOrNull,
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_lessons", forceRefresh)
    }

    private fun loadHomework(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            val semester = semesterRepository.getCurrentSemester(student)
            val date = LocalDate.now().nextOrSameSchoolDay

            homeworkRepository.getHomework(
                student = student,
                semester = semester,
                start = date,
                end = date,
                forceRefresh = forceRefresh
            )
        }
            .mapResourceData { homework ->
                val currentDate = LocalDate.now()

                val filteredHomework = homework.filter {
                    (it.date.isAfter(currentDate) || it.date == currentDate) && !it.isDone
                }.sortedBy { it.date }

                filteredHomework
            }
            .logResourceStatus("Loading dashboard homework")
            .onEach {
                updateData(
                    DashboardItem.Homework(
                        homework = it.dataOrNull.orEmpty(),
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_homework", forceRefresh)
    }

    private fun loadSchoolAnnouncements(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            schoolAnnouncementRepository.getSchoolAnnouncements(student, forceRefresh)
        }
            .logResourceStatus("Loading dashboard announcements")
            .onEach {
                updateData(
                    DashboardItem.Announcements(
                        announcement = it.dataOrNull.orEmpty(),
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_announcements", forceRefresh)
    }

    private fun loadExams(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            val semester = semesterRepository.getCurrentSemester(student)

            examRepository.getExams(
                student = student,
                semester = semester,
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(7),
                forceRefresh = forceRefresh
            )
        }
            .mapResourceData { exams -> exams.sortedBy { exam -> exam.date } }
            .logResourceStatus("Loading dashboard exams")
            .onEach {
                updateData(
                    DashboardItem.Exams(
                        exams = it.dataOrNull.orEmpty(),
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_exams", forceRefresh)
    }

    private fun loadConferences(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            val semester = semesterRepository.getCurrentSemester(student)

            conferenceRepository.getConferences(
                student = student,
                semester = semester,
                forceRefresh = forceRefresh,
                startDate = Instant.now(),
            )
        }
            .logResourceStatus("Loading dashboard conferences")
            .onEach {
                updateData(
                    DashboardItem.Conferences(
                        conferences = it.dataOrNull.orEmpty(),
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { errorHandler.dispatch(it) }
            .launchWithUniqueRefreshJob("dashboard_conferences", forceRefresh)
    }

    private fun loadAdminMessage(student: Student, forceRefresh: Boolean) {
        flatResourceFlow {
            getAppropriateAdminMessageUseCase(
                student = student,
                type = MessageType.DASHBOARD_MESSAGE,
            )
        }
            .logResourceStatus("Loading dashboard admin message")
            .onEach {
                updateData(
                    DashboardItem.AdminMessages(
                        adminMessage = it.dataOrNull,
                        isLoading = it is Resource.Loading,
                        error = it.errorOrNull
                    ), forceRefresh
                )
            }
            .onResourceError { Timber.e(it) }
            .launchWithUniqueRefreshJob("dashboard_admin_messages", forceRefresh)
    }

    private fun loadAds(forceRefresh: Boolean) {
        presenterScope.launch {
            updateData(DashboardItem.Ads(isLoading = true), false)

            val dashboardAdItem =
                runCatching {
                    DashboardItem.Ads(adsHelper.getDashboardTileAdBanner(view!!.tileWidth))
                }
                    .onFailure { Timber.e(it) }
                    .getOrElse { DashboardItem.Ads(error = it) }

            updateData(dashboardAdItem, forceRefresh)
        }
    }

    private fun updateData(dashboardItem: DashboardItem, forceRefresh: Boolean) {
        if (forceRefresh && dashboardItem.isLoading) return
        if (!forceRefresh && dashboardItem.isDataLoaded && dashboardItem.isLoading) {
            firstLoadedItemList += dashboardItem.type
        }

        // Replace the existing item only if the new item isn't an error (because a snack bar will
        // be shown instead), or if this item doesn't yet exist on the dashboard yet.
        // It's better to show an error tile than nothing, and it's better to keep showing a tile
        // with old data than replace it with an error tile.
        if (dashboardItem.error == null || (!forceRefresh && dashboardItem.type !in firstLoadedItemList)) {
            with(dashboardItemLoadedList) {
                removeAll { it.type == dashboardItem.type }
                add(dashboardItem)
            }

            sortDashboardItems()
        }

        if (forceRefresh) {
            updateForceRefreshData(dashboardItem)
        } else {
            updateNormalData()
        }
    }

    private fun updateNormalData() {
        // Loading as in at least started to load
        val isLoading =
            dashboardItemsToLoad.all { type -> type.importance == NonBlocking || dashboardItemLoadedList.any { it.type == type } }
        // Finished loading or at least has some meaningful intermediate data
        val isLoaded = isLoading && dashboardItemLoadedList.all(DashboardItem::isConsideredLoaded)

        if (isLoaded) {
            view?.run {
                showProgress(false)
                showErrorView(false)
                showContent(true)
                updateData(dashboardItemLoadedList.filter(DashboardItem::canBeDisplayed))
            }
        }

        showErrorIfExists(
            isItemsLoaded = isLoading,
            itemsLoadedList = dashboardItemLoadedList,
            forceRefresh = false
        )
    }

    private fun updateForceRefreshData(dashboardItem: DashboardItem) {
        with(dashboardItemRefreshLoadedList) {
            removeAll { it.type == dashboardItem.type }
            add(dashboardItem)
        }

        // Loading as in at least started to load
        val isLoading =
            dashboardItemsToLoad.all { type -> type.importance == NonBlocking || dashboardItemRefreshLoadedList.any { it.type == type } }
        // Finished loading or at least has some meaningful intermediate data
        val isLoaded = isLoading && dashboardItemRefreshLoadedList.all(DashboardItem::isConsideredLoaded)

        if (isLoaded) {
            view?.run {
                showRefresh(false)
                showErrorView(false)
                showContent(true)
                updateData(dashboardItemLoadedList.filter(DashboardItem::canBeDisplayed))
            }
        }

        showErrorIfExists(
            isItemsLoaded = isLoading,
            itemsLoadedList = dashboardItemRefreshLoadedList,
            forceRefresh = true
        )

        if (isLoaded) dashboardItemRefreshLoadedList.clear()
    }

    private fun showErrorIfExists(
        isItemsLoaded: Boolean,
        itemsLoadedList: List<DashboardItem>,
        forceRefresh: Boolean
    ) {
        val filteredItems = itemsLoadedList.filterNot {
            it.type == DashboardItem.Type.ACCOUNT || it.type == DashboardItem.Type.ADMIN_MESSAGE
        }
        val dataLoadedAdminMessageItem =
            itemsLoadedList.find { it.type == DashboardItem.Type.ADMIN_MESSAGE && it.isDataLoaded } as DashboardItem.AdminMessages?
        val isAccountItemError =
            itemsLoadedList.find { it.type == DashboardItem.Type.ACCOUNT }?.error != null
        val isGeneralError =
            filteredItems.none { it.error == null } && filteredItems.isNotEmpty() || isAccountItemError

        val filteredOriginalLoadedList =
            dashboardItemLoadedList.filterNot { it.type == DashboardItem.Type.ACCOUNT }
        val wasAccountItemError =
            dashboardItemLoadedList.find { it.type == DashboardItem.Type.ACCOUNT }?.error != null
        val wasGeneralError =
            filteredOriginalLoadedList.none { it.error == null } && filteredOriginalLoadedList.isNotEmpty() || wasAccountItemError

        if (isGeneralError && isItemsLoaded) {
            lastError = itemsLoadedList.firstNotNullOf { it.error }

            view?.run {
                showProgress(false)
                showRefresh(false)
                if ((forceRefresh && wasGeneralError) || !forceRefresh) {
                    showContent(false)
                    showErrorView(true, dataLoadedAdminMessageItem)
                    setErrorDetails(lastError)
                }
            }
        }
    }

    private fun sortDashboardItems() {
        val dashboardItemsPosition = preferencesRepository.dashboardItemsPosition

        dashboardItemLoadedList.sortBy { tile ->
            dashboardItemsPosition?.get(tile.type) ?: tile.order
        }
    }

    private fun Flow<Resource<*>>.launchWithUniqueRefreshJob(name: String, forceRefresh: Boolean) {
        val jobName = if (forceRefresh) "$name-forceRefresh" else name

        if (forceRefresh) {
            onEach {
                if (it is Resource.Success || it is Resource.Error) {
                    cancelJobs(jobName)
                }
            }.launch(jobName)
        } else {
            launch(jobName)
        }
    }

    @JvmName("launchWithUniqueRefreshJobHorizontalGroup")
    private fun Flow<Pair<List<Resource<*>>, *>>.launchWithUniqueRefreshJob(
        name: String,
        forceRefresh: Boolean
    ) {
        val jobName = if (forceRefresh) "$name-forceRefresh" else name

        if (forceRefresh) {
            onEach { (resources, _) ->
                if (resources.all { it is Resource.Success<*> }) {
                    cancelJobs(jobName)
                } else if (resources.any { it is Resource.Error<*> }) {
                    cancelJobs(jobName)
                }
            }.launch(jobName)
        } else {
            launch(jobName)
        }
    }
}
