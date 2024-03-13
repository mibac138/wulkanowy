package io.github.wulkanowy.ui.modules.dashboard

import io.github.wulkanowy.data.db.entities.AdminMessage
import io.github.wulkanowy.data.db.entities.Conference
import io.github.wulkanowy.data.db.entities.Exam
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.db.entities.SchoolAnnouncement
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.enums.GradeColorTheme
import io.github.wulkanowy.data.pojos.TimetableFull
import io.github.wulkanowy.utils.AdBanner
import io.github.wulkanowy.data.db.entities.Homework as EntitiesHomework

sealed class DashboardItem(val type: Type, val order: Int = type.ordinal + 100) {

    abstract val error: Throwable?

    abstract val isLoading: Boolean

    abstract val isDataLoaded: Boolean

    data class AdminMessages(
        val adminMessage: AdminMessage? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.ADMIN_MESSAGE, order = -1) {

        override val isDataLoaded get() = adminMessage != null
    }

    data class Account(
        val student: Student? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.ACCOUNT) {

        override val isDataLoaded get() = student != null
    }

    data class HorizontalGroup(
        val unreadMessagesCount: Cell<Int?>? = null,
        val attendancePercentage: Cell<Double>? = null,
        val luckyNumber: Cell<Int>? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.HORIZONTAL_GROUP) {

        data class Cell<T>(
            val data: T?,
            val error: Boolean,
            val isLoading: Boolean,
        ) {
            val isHidden: Boolean
                get() = data == null && !error && !isLoading
        }

        override val isDataLoaded
            get() = unreadMessagesCount?.isLoading == false || attendancePercentage?.isLoading == false || luckyNumber?.isLoading == false

        val isFullDataLoaded
            get() = luckyNumber?.isLoading != true && attendancePercentage?.isLoading != true && unreadMessagesCount?.isLoading != true
    }

    data class Grades(
        val subjectWithGrades: Map<String, List<Grade>>? = null,
        val gradeTheme: GradeColorTheme? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.GRADES) {

        override val isDataLoaded get() = subjectWithGrades != null
    }

    data class Lessons(
        val lessons: TimetableFull? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.LESSONS) {

        override val isDataLoaded get() = lessons != null
    }

    data class Homework(
        val homework: List<EntitiesHomework>? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.HOMEWORK) {

        override val isDataLoaded get() = homework != null
    }

    data class Announcements(
        val announcement: List<SchoolAnnouncement>? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.ANNOUNCEMENTS) {

        override val isDataLoaded get() = announcement != null
    }

    data class Exams(
        val exams: List<Exam>? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.EXAMS) {

        override val isDataLoaded get() = exams != null
    }

    data class Conferences(
        val conferences: List<Conference>? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.CONFERENCES) {

        override val isDataLoaded get() = conferences != null
    }

    data class Ads(
        val adBanner: AdBanner? = null,
        override val error: Throwable? = null,
        override val isLoading: Boolean = false
    ) : DashboardItem(Type.ADS) {

        override val isDataLoaded get() = adBanner != null
    }

    enum class Type(val refreshBehavior: RefreshBehavior = RefreshBehavior.OnScreen) {
        ADMIN_MESSAGE(refreshBehavior = RefreshBehavior.Always),
        ACCOUNT,
        HORIZONTAL_GROUP,
        LESSONS,
        ADS,
        GRADES,
        HOMEWORK,
        ANNOUNCEMENTS,
        EXAMS,
        CONFERENCES,
    }

    enum class Tile(val type: Type) {
        ADMIN_MESSAGE(Type.ADMIN_MESSAGE),
        ACCOUNT(Type.ACCOUNT),
        LUCKY_NUMBER(Type.HORIZONTAL_GROUP),
        MESSAGES(Type.HORIZONTAL_GROUP),
        ATTENDANCE(Type.HORIZONTAL_GROUP),
        LESSONS(Type.LESSONS),
        ADS(Type.ADS),
        GRADES(Type.GRADES),
        HOMEWORK(Type.HOMEWORK),
        ANNOUNCEMENTS(Type.ANNOUNCEMENTS),
        EXAMS(Type.EXAMS),
        CONFERENCES(Type.CONFERENCES),
    }

    enum class RefreshBehavior {
        /**
         * Types with this refresh behavior should always be refreshed, no matter whether they are
         * selected (in the dashboard tile list menu) or not.
         */
        Always,

        /**
         * Types with this refresh behavior are only refreshed when they are actually selected (in
         * the dashboard tile list menu).
         */
        OnScreen,
    }

}
