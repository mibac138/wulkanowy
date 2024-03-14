package io.github.wulkanowy.services.sync.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.pojos.NotificationData
import io.github.wulkanowy.ui.modules.Destination
import javax.inject.Inject

class CaptchaRequiredNotification @Inject constructor(
    private val appNotificationManager: AppNotificationManager,
    @ApplicationContext private val context: Context,
) {
    suspend fun notify(student: Student) {
        val notificationData = NotificationData(
            title = context.getString(R.string.captcha_required_notify_title),
            content = context.getString(R.string.captcha_required_notify_content),
            destination = Destination.Dashboard,
        )
        appNotificationManager.trySendSingletonNotification(
            notificationData, NotificationType.CAPTCHA_REQUIRED, student
        )
    }
}
