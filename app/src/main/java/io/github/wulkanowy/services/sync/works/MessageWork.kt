package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Message
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.enums.MessageFolder.RECEIVED
import io.github.wulkanowy.data.repositories.MessageRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewMessageNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class MessageWork @Inject constructor(
    private val messageRepository: MessageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newMessageNotification: NewMessageNotification,
) : BaseScopedWork<List<Message>>(NotificationType.NEW_MESSAGE) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Message> {
        messageRepository.getMessages(
            student = student.student,
            semester = student.currSemester,
            folder = RECEIVED,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return messageRepository.getMessagesFromDatabase(student.student).first()
            .filter { !it.isNotified && it.unread }
    }

    override suspend fun notify(
        scope: String,
        newData: List<Message>,
        recipients: List<Student>,
    ) {
        newMessageNotification.notify(scope, newData, recipients)
        messageRepository.updateMessages(newData.onEach { it.isNotified = true })
    }
}
