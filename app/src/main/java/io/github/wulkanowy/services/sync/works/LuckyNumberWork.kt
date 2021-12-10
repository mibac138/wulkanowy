package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.LuckyNumber
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.LuckyNumberRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewLuckyNumberNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import javax.inject.Inject

class LuckyNumberWork @Inject constructor(
    private val luckyNumberRepository: LuckyNumberRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newLuckyNumberNotification: NewLuckyNumberNotification,
) : BaseScopedWork<LuckyNumber?>(NotificationType.NEW_LUCKY_NUMBER) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): LuckyNumber? {
        luckyNumberRepository.getLuckyNumber(
            student = student.student,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return luckyNumberRepository.getNotNotifiedLuckyNumber(student.student)
    }

    override suspend fun notify(
        scope: String,
        newData: LuckyNumber?,
        recipients: List<Student>
    ) {
        if (newData != null) {
            newLuckyNumberNotification.notify(scope, newData, recipients)
            luckyNumberRepository.updateLuckyNumber(newData.apply { isNotified = true })
        }
    }
}
