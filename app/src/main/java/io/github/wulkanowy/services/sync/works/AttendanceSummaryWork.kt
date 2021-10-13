package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.AttendanceSummaryRepository
import io.github.wulkanowy.data.repositories.SubjectRepository
import io.github.wulkanowy.utils.toFirstResult
import io.github.wulkanowy.utils.waitForResult
import javax.inject.Inject

class AttendanceSummaryWork @Inject constructor(
    private val attendanceSummaryRepository: AttendanceSummaryRepository,
    private val subjectRepository: SubjectRepository,
) : Work {

    override suspend fun doWork(student: Student, semester: Semester) {
        attendanceSummaryRepository.getAttendanceSummary(student, semester, -1, true).waitForResult()
        val (_status, data, error) = subjectRepository.getSubjects(student, semester).toFirstResult()
        if (error != null) {
            throw error
        }
        data!!.forEach {
            attendanceSummaryRepository.getAttendanceSummary(student, semester, it.realId, true).toFirstResult().let {
                if (it.error != null) throw it.error
            }
        }
    }
}
