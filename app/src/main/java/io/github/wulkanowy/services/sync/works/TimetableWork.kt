package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.Timetable
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.TimetableRepository
import io.github.wulkanowy.services.sync.notifications.ChangeTimetableNotification
import io.github.wulkanowy.services.sync.notifications.NotificationScope
import io.github.wulkanowy.utils.nextOrSameSchoolDay
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class TimetableWork @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val changeTimetableNotification: ChangeTimetableNotification,
    private val preferencesRepository: PreferencesRepository
) : BaseScopedWork<Pair<String, List<Timetable>>>(NotificationScope.LessonGroup) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): Pair<String, List<Timetable>> {
        timetableRepository.getTimetable(
            student = student.student,
            semester = student.currSemester,
            start = now().nextOrSameSchoolDay,
            end = now().nextOrSameSchoolDay,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return student.currSemester.diaryName to timetableRepository.getTimetableFromDatabase(
            student.currSemester,
            now(),
            now().plusDays(7)
        ).first().filterNot { it.isNotified }
    }

    override suspend fun notify(
        _scope: String,
        newData: Pair<String, List<Timetable>>,
        recipients: List<Student>
    ) {
        val (diaryName, data) = newData
        for ((group, lessons) in data.groupBy { it.group }) {
            val scope = "$diaryName $group"
            changeTimetableNotification.notify(scope, lessons, recipients)
            timetableRepository.updateTimetable(data.onEach { it.isNotified = true })
        }
    }
}
