package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.TimetableRepository
import io.github.wulkanowy.data.waitForResult
import io.github.wulkanowy.services.sync.notifications.ChangeTimetableNotification
import io.github.wulkanowy.utils.nextOrSameSchoolDay
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class TimetableWork @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val changeTimetableNotification: ChangeTimetableNotification,
) : Work {

    override suspend fun doWork(student: Student, semester: Semester, notify: Boolean) {
        timetableRepository.getTimetable(
            student = student,
            semester = semester,
            start = now().nextOrSameSchoolDay,
            end = now().nextOrSameSchoolDay,
            forceRefresh = true,
            notify = notify,
        )
            .waitForResult()

        timetableRepository.getTimetableFromDatabase(semester, now(), now().plusDays(7))
            .first()
            .filterNot { it.isNotified }
            .let {
                if (it.isNotEmpty()) changeTimetableNotification.notify(it, student)

                timetableRepository.updateTimetable(it.onEach { timetable ->
                    timetable.isNotified = true
                })
            }
    }
}
