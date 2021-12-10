package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.SchoolAnnouncement
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SchoolAnnouncementRepository
import io.github.wulkanowy.services.sync.notifications.NewSchoolAnnouncementNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SchoolAnnouncementWork @Inject constructor(
    private val schoolAnnouncementRepository: SchoolAnnouncementRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newSchoolAnnouncementNotification: NewSchoolAnnouncementNotification,
) : BaseScopedWork<List<SchoolAnnouncement>>(NotificationType.NEW_ANNOUNCEMENT) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<SchoolAnnouncement> {
        schoolAnnouncementRepository.getSchoolAnnouncements(
            student = student.student,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return schoolAnnouncementRepository.getSchoolAnnouncementFromDatabase(student.student)
            .first().filterNot { it.isNotified }
    }

    override suspend fun notify(
        scope: String,
        newData: List<SchoolAnnouncement>,
        recipients: List<Student>,
    ) {
        newSchoolAnnouncementNotification.notify(scope, newData, recipients)
        schoolAnnouncementRepository.updateSchoolAnnouncement(newData.onEach { it.isNotified = true })
    }
}
