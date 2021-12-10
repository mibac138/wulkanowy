package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Homework
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.HomeworkRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewHomeworkNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.nextOrSameSchoolDay
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class HomeworkWork @Inject constructor(
    private val homeworkRepository: HomeworkRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newHomeworkNotification: NewHomeworkNotification,
) : BaseScopedWork<List<Homework>>(NotificationType.NEW_HOMEWORK) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Homework> {
        homeworkRepository.getHomework(
            student = student.student,
            semester = student.currSemester,
            start = now().nextOrSameSchoolDay,
            end = now().nextOrSameSchoolDay,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return homeworkRepository.getHomeworkFromDatabase(
            student.currSemester,
            now(),
            now().plusDays(7)
        ).first().filterNot { it.isNotified }
    }

    override suspend fun notify(
        scope: String,
        newData: List<Homework>,
        recipients: List<Student>
    ) {
        newHomeworkNotification.notify(scope, newData, recipients)
        homeworkRepository.updateHomework(newData.onEach { it.isNotified = true })
    }
}
