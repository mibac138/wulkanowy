package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.db.dao.ExamDao
import io.github.wulkanowy.data.db.entities.Exam
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.data.networkBoundResource
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.utils.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val examDb: ExamDao,
    private val sdk: Sdk,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "exam"

    fun getExams(
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
            examDb.loadAll(
                diaryId = semester.diaryId,
                studentId = semester.studentId,
                from = start.startExamsDay,
                end = start.endExamsDay
            )
        },
        fetch = {
            sdk.init(student)
                .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
                .getExams(start.startExamsDay, start.endExamsDay)
                .mapToEntities(semester)
        },
        saveFetchResult = { old, new ->
            val examsToSave = (new uniqueSubtract old).onEach {
                if (notify) it.isNotified = false
            }

            examDb.deleteAll(old uniqueSubtract new)
            examDb.insertAll(examsToSave)
            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester, start, end))
        },
        filterResult = { it.filter { item -> item.date in start..end } }
    )

    fun getExamsFromDatabase(semester: Semester, start: LocalDate): Flow<List<Exam>> {
        return examDb.loadAll(
            diaryId = semester.diaryId,
            studentId = semester.studentId,
            from = start.startExamsDay,
            end = start.endExamsDay
        )
    }

    suspend fun updateExam(exam: List<Exam>) = examDb.updateAll(exam)
}
