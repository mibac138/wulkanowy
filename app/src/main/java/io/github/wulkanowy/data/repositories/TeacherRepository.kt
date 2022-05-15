package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.SdkFactory
import io.github.wulkanowy.data.db.dao.TeacherDao
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
class TeacherRepository @Inject constructor(
    private val teacherDb: TeacherDao,
    private val sdk: SdkFactory,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "teachers"

    fun getTeachers(
        student: Student,
        semester: Semester,
        forceRefresh: Boolean,
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(getRefreshKey(cacheKey, semester))
            it.isEmpty() || forceRefresh || isExpired
        },
        query = { teacherDb.loadAll(semester.studentId, semester.classId) },
        fetch = {
            sdk.init(student)
                .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
                .getTeachers(semester.semesterId)
                .mapToEntities(semester)
        },
        saveFetchResult = { old, new ->
            teacherDb.deleteAll(old uniqueSubtract new)
            teacherDb.insertAll(new uniqueSubtract old)

            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester))
        }
    )
}
