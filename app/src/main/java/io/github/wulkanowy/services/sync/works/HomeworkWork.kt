package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.HomeworkRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewHomeworkNotification
import io.github.wulkanowy.utils.nextOrSameSchoolDay
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class HomeworkWork @Inject constructor(
    private val homeworkRepository: HomeworkRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newHomeworkNotification: NewHomeworkNotification,
) : Work {

    override suspend fun doWork(student: Student, semester: Semester) {
        homeworkRepository.getHomework(
            student = student,
            semester = semester,
            start = now().nextOrSameSchoolDay,
            end = now().nextOrSameSchoolDay,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        homeworkRepository.getHomeworkFromDatabase(semester, now(), now().plusDays(7)).first()
            .filter { !it.isNotified }.let {
                if (it.isNotEmpty()) newHomeworkNotification.notify(it, student)

                homeworkRepository.updateHomework(it.onEach { homework ->
                    homework.isNotified = true
                })
            }
    }
}
