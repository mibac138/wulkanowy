package io.github.wulkanowy.ui.modules.main

import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentWithSemesters
import io.github.wulkanowy.ui.base.BaseView
import io.github.wulkanowy.ui.modules.Destination

interface MainView : BaseView {

    val isRootView: Boolean

    val currentViewTitle: String?

    val currentViewSubtitle: String?

    val currentStackSize: Int?

    fun initView(startMenuIndex: Int, rootDestinations: List<Destination>)

    fun switchMenuView(position: Int)

    fun showHomeArrow(show: Boolean)

    fun showAccountPicker(studentWithSemesters: List<StudentWithSemesters>)

    fun showActionBarElevation(show: Boolean)

    fun showBottomNavigation(show: Boolean)

    fun notifyMenuViewReselected()

    fun notifyMenuViewChanged()

    fun setViewTitle(title: String)

    fun setViewSubTitle(subtitle: String?)

    fun popView(depth: Int = 1)

    fun showStudentAvatar(student: Student)

    fun showInAppReview()

    fun openMoreDestination(destination: Destination)

    interface MainChildView {

        fun onFragmentReselected()

        fun onFragmentChanged() {}
    }

    interface TitledView {

        val titleStringId: Int

        var subtitleString: String
            get() = ""
            set(_) {}
    }
}
