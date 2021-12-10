package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.repositories.AttendanceSummaryRepository
import io.github.wulkanowy.utils.waitForResult
import javax.inject.Inject

class AttendanceSummaryWork @Inject constructor(
    private val attendanceSummaryRepository: AttendanceSummaryRepository,
) : Work {

    override suspend fun doWork(students: List<StudentWithCurrentSemester>) {
        for ((student, semester) in students) {
            attendanceSummaryRepository.getOverallAttendanceSummary(student, semester, true)
                .waitForResult()
        }
    }
}
