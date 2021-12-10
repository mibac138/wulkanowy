package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.CompletedLesson
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.CompletedLessonsRepository
import io.github.wulkanowy.services.sync.notifications.NotificationScope
import io.github.wulkanowy.utils.monday
import io.github.wulkanowy.utils.sunday
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class CompletedLessonWork @Inject constructor(
    private val completedLessonsRepository: CompletedLessonsRepository
) : BaseScopedWork<List<CompletedLesson>>(NotificationScope.Person) {
    // Implements BaseScopedWork only for it's verification functionality

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<CompletedLesson> {
        return completedLessonsRepository.getCompletedLessons(student.student, student.currSemester, now().monday, now().sunday, true).first().data ?: emptyList()
    }

    override suspend fun notify(
        scope: String,
        newData: List<CompletedLesson>,
        recipients: List<Student>
    ) {}
}
