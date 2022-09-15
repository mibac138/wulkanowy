package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.SdkFactory
import io.github.wulkanowy.data.db.dao.AttendanceSummaryDao
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.data.networkBoundResource
import io.github.wulkanowy.utils.AutoRefreshHelper
import io.github.wulkanowy.utils.getRefreshKey
import io.github.wulkanowy.utils.uniqueSubtract
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceSummaryRepository @Inject constructor(
    private val attendanceDb: AttendanceSummaryDao,
    private val sdk: SdkFactory,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "attendance_summary"

    fun getAttendanceSummary(
        student: Student,
        semester: Semester,
        subjectId: Int,
        forceRefresh: Boolean,
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(getRefreshKey(cacheKey, semester))
            it.isEmpty() || forceRefresh || isExpired
        },
        query = { attendanceDb.loadAll(semester.diaryId, semester.studentId, subjectId) },
        fetch = {
            sdk.init(student)
                .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
                .getAttendanceSummary(subjectId)
                .mapToEntities(semester, subjectId)
        },
        saveFetchResult = { old, new ->
            attendanceDb.deleteAll(old uniqueSubtract new)
            attendanceDb.insertAll(new uniqueSubtract old)
            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester))
        }
    )
}
