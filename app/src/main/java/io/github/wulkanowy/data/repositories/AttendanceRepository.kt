package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.db.dao.AttendanceDao
import io.github.wulkanowy.data.db.dao.TimetableDao
import io.github.wulkanowy.data.db.entities.Attendance
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.data.networkBoundResource
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.sdk.pojo.Absent
import io.github.wulkanowy.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val attendanceDb: AttendanceDao,
    private val timetableDb: TimetableDao,
    private val sdk: Sdk,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "attendance"

    fun getAttendance(
        student: Student,
        semester: Semester,
        start: LocalDate,
        end: LocalDate,
        forceRefresh: Boolean,
        notify: Boolean = false,
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(
                key = getRefreshKey(cacheKey, semester, start, end)
            )
            it.isEmpty() || forceRefresh || isExpired
        },
        query = {
            attendanceDb.loadAll(semester.diaryId, semester.studentId, start.monday, end.sunday)
        },
        fetch = {
            val lessons = withContext(Dispatchers.IO) {
                timetableDb.load(
                    semester.diaryId, semester.studentId, start.monday, end.sunday
                )
            }
            sdk.init(student)
                .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
                .getAttendance(start.monday, end.sunday)
                .mapToEntities(semester, lessons)
        },
        saveFetchResult = { old, new ->
            attendanceDb.deleteAll(old uniqueSubtract new)
            val attendanceToAdd = (new uniqueSubtract old).map { newAttendance ->
                newAttendance.apply { if (notify) isNotified = false }
            }
            attendanceDb.insertAll(attendanceToAdd)

            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester, start, end))
        },
        filterResult = { it.filter { item -> item.date in start..end } }
    )

    fun getAttendanceFromDatabase(
        semester: Semester,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<Attendance>> {
        return attendanceDb.loadAll(semester.diaryId, semester.studentId, start, end)
    }

    suspend fun updateTimetable(timetable: List<Attendance>) {
        return attendanceDb.updateAll(timetable)
    }

    suspend fun excuseForAbsence(
        student: Student, semester: Semester,
        absenceList: List<Attendance>, reason: String? = null
    ) {
        val items = absenceList.map { attendance ->
            Absent(
                date = LocalDateTime.of(attendance.date, LocalTime.of(0, 0)),
                timeId = attendance.timeId
            )
        }
        sdk.init(student)
            .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
            .excuseForAbsence(items, reason)
    }
}
