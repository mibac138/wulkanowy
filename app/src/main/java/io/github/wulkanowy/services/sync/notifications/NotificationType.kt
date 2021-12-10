package io.github.wulkanowy.services.sync.notifications

import io.github.wulkanowy.R
import io.github.wulkanowy.services.sync.channels.LuckyNumberChannel
import io.github.wulkanowy.services.sync.channels.NewAttendanceChannel
import io.github.wulkanowy.services.sync.channels.NewConferencesChannel
import io.github.wulkanowy.services.sync.channels.NewExamChannel
import io.github.wulkanowy.services.sync.channels.NewGradesChannel
import io.github.wulkanowy.services.sync.channels.NewHomeworkChannel
import io.github.wulkanowy.services.sync.channels.NewMessagesChannel
import io.github.wulkanowy.services.sync.channels.NewNotesChannel
import io.github.wulkanowy.services.sync.channels.NewSchoolAnnouncementsChannel
import io.github.wulkanowy.services.sync.channels.PushChannel
import io.github.wulkanowy.services.sync.channels.TimetableChangeChannel

enum class NotificationScope {
    /**
     * One notification for whole app
     */
    App,

    /**
     * One notification for every school
     */
    School,

    /**
     * One notification for every class
     */
    Class,

    /**
     * One notification for every group in a class
     */
    LessonGroup,

    /**
     * One notification for every person. When an account with the same name is registered in the
     * app more than once (e.g. both a student and a parent account) this is considered a single
     * person
     */
    Person,

    /**
     * One notification for every account.
     */
    Account,
}

//fun <T> Iterable<T>.groupBy(type: NotificationType, extractor: (T) -> Student): Collection<List<T>>
//    = groupBy(type.scope, extractor)
//
//fun <T> Iterable<T>.groupBy(scope: NotificationScope, extractor: (T) -> Student): Map<String, List<T>> {
//    val grouper = { data: T ->
//        val student = extractor(data)
//        when(scope) {
//            NotificationScope.App -> ""
//            NotificationScope.School -> student.schoolName
//            // TODO should also include level (e.g. `1a` instead of only `a`, which is what className means)
//            NotificationScope.Class -> student.className
//            NotificationScope.LessonGroup -> student.userName // TODO
//            NotificationScope.Person -> student.userName // *should* be unique
//            NotificationScope.Account -> student.id
//        }
//    }
//    return this.groupBy(grouper)
//}

enum class NotificationType(
    val channel: String,
    val icon: Int,
    val scope: NotificationScope,
) {
    NEW_CONFERENCE(
        channel = NewConferencesChannel.CHANNEL_ID,
        icon = R.drawable.ic_more_conferences,
        scope = NotificationScope.Class,
    ),
    NEW_EXAM(
        channel = NewExamChannel.CHANNEL_ID,
        icon = R.drawable.ic_main_exam,
        scope = NotificationScope.LessonGroup,
    ),
    NEW_GRADE_DETAILS(
        channel = NewGradesChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_grade,
        scope = NotificationScope.Person,
    ),
    NEW_GRADE_PREDICTED(
        channel = NewGradesChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_grade,
        scope = NotificationScope.Person,
    ),
    NEW_GRADE_FINAL(
        channel = NewGradesChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_grade,
        scope = NotificationScope.Person,
    ),
    NEW_HOMEWORK(
        channel = NewHomeworkChannel.CHANNEL_ID,
        icon = R.drawable.ic_more_homework,
        scope = NotificationScope.Class,
    ),
    NEW_LUCKY_NUMBER(
        channel = LuckyNumberChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_luckynumber,
        scope = NotificationScope.School,
    ),
    NEW_MESSAGE(
        channel = NewMessagesChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_message,
        scope = NotificationScope.Account,
    ),
    NEW_NOTE(
        channel = NewNotesChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_note,
        scope = NotificationScope.Person,
    ),
    NEW_ANNOUNCEMENT(
        channel = NewSchoolAnnouncementsChannel.CHANNEL_ID,
        icon = R.drawable.ic_all_about,
        scope = NotificationScope.School,
    ),
    CHANGE_TIMETABLE(
        channel = TimetableChangeChannel.CHANNEL_ID,
        icon = R.drawable.ic_main_timetable,
        scope = NotificationScope.LessonGroup,
    ),
    NEW_ATTENDANCE(
        channel = NewAttendanceChannel.CHANNEL_ID,
        icon = R.drawable.ic_main_attendance,
        scope = NotificationScope.Person,
    ),
    PUSH(
        channel = PushChannel.CHANNEL_ID,
        icon = R.drawable.ic_stat_all,
        scope = NotificationScope.App,
    )
}
