package io.github.wulkanowy.services.sync.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.db.entities.GradeSummary
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.pojos.GroupNotificationData
import io.github.wulkanowy.data.pojos.NotificationData
import io.github.wulkanowy.ui.modules.Destination
import io.github.wulkanowy.ui.modules.splash.SplashActivity
import io.github.wulkanowy.utils.getPlural
import javax.inject.Inject

class NewGradeNotification @Inject constructor(
    private val appNotificationManager: AppNotificationManager,
    @ApplicationContext private val context: Context
) {

    suspend fun notifyDetails(items: List<Grade>, student: Student) {
        val notificationDataList = items.map {
            NotificationData(
                title = context.getPlural(R.plurals.grade_new_items, 1),
                content = "${it.subject}: ${it.entry}",
                intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            )
        }

        val groupNotificationData = GroupNotificationData(
            notificationDataList = notificationDataList,
            title = context.getPlural(R.plurals.grade_new_items, items.size),
            content = context.getPlural(R.plurals.grade_notify_new_items, items.size, items.size),
            intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            type = NotificationType.NEW_GRADE_DETAILS
        )

        appNotificationManager.sendMultipleNotifications(groupNotificationData, student)
    }

    suspend fun notifyPredicted(items: List<GradeSummary>, student: Student) {
        val notificationDataList = items.map {
            NotificationData(
                title = context.getPlural(R.plurals.grade_new_items_predicted, 1),
                content = "${it.subject}: ${it.predictedGrade}",
                intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            )
        }

        val groupNotificationData = GroupNotificationData(
            notificationDataList = notificationDataList,
            title = context.getPlural(R.plurals.grade_new_items_predicted, items.size),
            content = context.getPlural(
                R.plurals.grade_notify_new_items_predicted,
                items.size,
                items.size
            ),
            intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            type = NotificationType.NEW_GRADE_PREDICTED
        )

        appNotificationManager.sendMultipleNotifications(groupNotificationData, student)
    }

    suspend fun notifyFinal(items: List<GradeSummary>, student: Student) {
        val notificationDataList = items.map {
            NotificationData(
                title = context.getPlural(R.plurals.grade_new_items_final, 1),
                content = "${it.subject}: ${it.finalGrade}",
                intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            )
        }

        val groupNotificationData = GroupNotificationData(
            notificationDataList = notificationDataList,
            title = context.getPlural(R.plurals.grade_new_items_final, items.size),
            content = context.getPlural(
                R.plurals.grade_notify_new_items_final,
                items.size,
                items.size
            ),
            intentToStart = SplashActivity.getStartIntent(context, Destination.Grade),
            type = NotificationType.NEW_GRADE_FINAL
        )

        appNotificationManager.sendMultipleNotifications(groupNotificationData, student)
    }
}
