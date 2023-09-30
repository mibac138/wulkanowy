package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.db.dao.GradeDao
import io.github.wulkanowy.data.db.dao.GradeSummaryDao
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.db.entities.GradeSummary
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.mappers.mapToEntities
import io.github.wulkanowy.data.networkBoundResource
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.utils.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradeRepository @Inject constructor(
    private val gradeDb: GradeDao,
    private val gradeSummaryDb: GradeSummaryDao,
    private val sdk: Sdk,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "grade"

    fun getGrades(
        student: Student,
        semester: Semester,
        forceRefresh: Boolean,
        notify: Boolean = false,
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = {
            //When details is empty and summary is not, app will not use summary cache - edge case
            it.first.isEmpty()
        },
        shouldFetch = { (details, summaries) ->
            val isExpired = refreshHelper.shouldBeRefreshed(getRefreshKey(cacheKey, semester))
            details.isEmpty() || summaries.isEmpty() || forceRefresh || isExpired
        },
        query = {
            val detailsFlow = gradeDb.loadAll(semester.semesterId, semester.studentId)
            val summaryFlow = gradeSummaryDb.loadAll(semester.semesterId, semester.studentId)
            detailsFlow.combine(summaryFlow) { details, summaries -> details to summaries }
        },
        fetch = {
            val (details, summary) = sdk.init(student)
                .switchDiary(semester.diaryId, semester.kindergartenDiaryId, semester.schoolYear)
                .getGrades(semester.semesterId)

            details.mapToEntities(semester) to summary.mapToEntities(semester)
        },
        saveFetchResult = { (oldDetails, oldSummary), (newDetails, newSummary) ->
            refreshGradeDetails(student, oldDetails, newDetails, notify)
            refreshGradeSummaries(oldSummary, newSummary, notify)

            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester))
        }
    )

    private suspend fun refreshGradeDetails(
        student: Student,
        oldGrades: List<Grade>,
        newDetails: List<Grade>,
        notify: Boolean
    ) {
        val notifyBreakDate = oldGrades.maxByOrNull { it.date }?.date
            ?: student.registrationDate.toLocalDate()
        gradeDb.deleteAll(oldGrades uniqueSubtract newDetails)
        gradeDb.insertAll((newDetails uniqueSubtract oldGrades).onEach {
            if (it.date >= notifyBreakDate) it.apply {
                isRead = false
                if (notify) isNotified = false
            }
        })
    }

    private suspend fun refreshGradeSummaries(
        oldSummaries: List<GradeSummary>,
        newSummary: List<GradeSummary>,
        notify: Boolean
    ) {
        gradeSummaryDb.deleteAll(oldSummaries uniqueSubtract newSummary)
        gradeSummaryDb.insertAll((newSummary uniqueSubtract oldSummaries).onEach { summary ->
            val oldSummary = oldSummaries.find { old -> old.subject == summary.subject }
            summary.isPredictedGradeNotified = when {
                summary.predictedGrade.isEmpty() -> true
                notify && oldSummary?.predictedGrade != summary.predictedGrade -> false
                else -> true
            }
            summary.isFinalGradeNotified = when {
                summary.finalGrade.isEmpty() -> true
                notify && oldSummary?.finalGrade != summary.finalGrade -> false
                else -> true
            }

            summary.predictedGradeLastChange = when {
                oldSummary == null -> Instant.now()
                summary.predictedGrade != oldSummary.predictedGrade -> Instant.now()
                else -> oldSummary.predictedGradeLastChange
            }
            summary.finalGradeLastChange = when {
                oldSummary == null -> Instant.now()
                summary.finalGrade != oldSummary.finalGrade -> Instant.now()
                else -> oldSummary.finalGradeLastChange
            }
        })
    }

    fun getUnreadGrades(semester: Semester): Flow<List<Grade>> {
        return gradeDb.loadAll(semester.semesterId, semester.studentId).map {
            it.filter { grade -> !grade.isRead }
        }
    }

    fun getGradesFromDatabase(semester: Semester): Flow<List<Grade>> {
        return gradeDb.loadAll(semester.semesterId, semester.studentId)
    }

    fun getGradesPredictedFromDatabase(semester: Semester): Flow<List<GradeSummary>> {
        return gradeSummaryDb.loadAll(semester.semesterId, semester.studentId)
    }

    fun getGradesFinalFromDatabase(semester: Semester): Flow<List<GradeSummary>> {
        return gradeSummaryDb.loadAll(semester.semesterId, semester.studentId)
    }

    suspend fun updateGrade(grade: Grade) {
        return gradeDb.updateAll(listOf(grade))
    }

    suspend fun updateGrades(grades: List<Grade>) {
        return gradeDb.updateAll(grades)
    }

    suspend fun updateGradesSummary(gradesSummary: List<GradeSummary>) {
        return gradeSummaryDb.updateAll(gradesSummary)
    }
}
