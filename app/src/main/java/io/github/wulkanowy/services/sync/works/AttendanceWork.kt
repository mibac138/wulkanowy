package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Attendance
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.AttendanceRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewAttendanceNotification
import io.github.wulkanowy.services.sync.notifications.NotificationScope
import io.github.wulkanowy.utils.previousOrSameSchoolDay
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate.now
import javax.inject.Inject

class AttendanceWork @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val newAttendanceNotification: NewAttendanceNotification,
    private val preferencesRepository: PreferencesRepository
) : BaseScopedWork<List<Attendance>>(NotificationScope.Person) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Attendance> {
        attendanceRepository.getAttendance(
            student = student.student,
            semester = student.currSemester,
            start = now().previousOrSameSchoolDay,
            end = now().previousOrSameSchoolDay,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        )
            .waitForResult()

        return attendanceRepository.getAttendanceFromDatabase(
            student.currSemester,
            now().minusDays(7),
            now()
        ).first().filterNot { it.isNotified }
    }

    override suspend fun notify(
        scope: String,
        newData: List<Attendance>,
        recipients: List<Student>
    ) {
        newAttendanceNotification.notify(scope, newData, recipients)
        attendanceRepository.updateTimetable(newData.onEach { it.isNotified = true })
    }
}
