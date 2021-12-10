package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Conference
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.ConferenceRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewConferenceNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ConferenceWork @Inject constructor(
    private val conferenceRepository: ConferenceRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newConferenceNotification: NewConferenceNotification,
) : BaseScopedWork<List<Conference>>(NotificationType.NEW_CONFERENCE) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Conference> {
        conferenceRepository.getConferences(
            student = student.student,
            semester = student.currSemester,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return conferenceRepository.getConferencesFromDatabase(student.currSemester).first()
            .filterNot { it.isNotified }
    }

    override suspend fun notify(
        scope: String,
        newData: List<Conference>,
        recipients: List<Student>
    ) {
        newConferenceNotification.notify(scope, newData, recipients)
        conferenceRepository.updateConference(newData.onEach { it.isNotified = true })
    }
}
