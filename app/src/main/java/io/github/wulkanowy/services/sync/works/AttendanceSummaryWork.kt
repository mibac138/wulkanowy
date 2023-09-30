package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.dataOrNull
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.errorOrNull
import io.github.wulkanowy.data.repositories.AttendanceSummaryRepository
import io.github.wulkanowy.data.repositories.SubjectRepository
import io.github.wulkanowy.data.toFirstResult
import io.github.wulkanowy.data.waitForResult
import javax.inject.Inject

class AttendanceSummaryWork @Inject constructor(
    private val attendanceSummaryRepository: AttendanceSummaryRepository,
    private val subjectRepository: SubjectRepository,
) : Work {

    override suspend fun doWork(student: Student, semester: Semester, notify: Boolean) {
        attendanceSummaryRepository.getAttendanceSummary(
            student = student,
            semester = semester,
            subjectId = -1,
            forceRefresh = true,
        ).waitForResult()
        val res = subjectRepository.getSubjects(student, semester).toFirstResult()
        res.errorOrNull?.let { throw it }

        res.dataOrNull?.forEach { subject ->
            attendanceSummaryRepository
                .getAttendanceSummary(student, semester, subject.realId, true)
                .toFirstResult().let { result ->
                    result.errorOrNull?.let { throw it }
                }
        }
    }
}
