package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Exam
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.ExamRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewExamNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class ExamWork @Inject constructor(
    private val examRepository: ExamRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newExamNotification: NewExamNotification,
) : BaseScopedWork<List<Exam>>(NotificationType.NEW_EXAM) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Exam> {
        examRepository.getExams(
            student = student.student,
            semester = student.currSemester,
            start = now(),
            end = now(),
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return examRepository.getExamsFromDatabase(student.currSemester, now()).first()
            .filterNot { it.isNotified }
    }

    override suspend fun notify(
        scope: String,
        newData: List<Exam>,
        recipients: List<Student>
    ) {
        newExamNotification.notify(scope, newData, recipients)
        examRepository.updateExam(newData.onEach { it.isNotified = true })
    }
}
