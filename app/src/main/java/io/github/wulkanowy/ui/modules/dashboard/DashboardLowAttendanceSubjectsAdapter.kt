package io.github.wulkanowy.ui.modules.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.AttendanceSummary
import io.github.wulkanowy.databinding.SubitemDashboardLowAttendanceSubjectsBinding
import io.github.wulkanowy.utils.calculatePercentage
import io.github.wulkanowy.utils.getThemeAttrColor
import kotlin.math.roundToInt

class DashboardLowAttendanceSubjectsAdapter :
    RecyclerView.Adapter<DashboardLowAttendanceSubjectsAdapter.ViewHolder>() {

    var items = listOf<Pair<String, AttendanceSummary>>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ) = ViewHolder(
        SubitemDashboardLowAttendanceSubjectsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (subjectName, attendanceSummary) = items[position]
        val context = holder.binding.root.context

        with(holder.binding) {
            dashboardLowAttendanceSubjectSubitemTitle.text = subjectName
            val attendancePercentage = attendanceSummary.calculatePercentage()
            val attendanceColor = when {
                attendancePercentage == .0 -> {
                    context.getThemeAttrColor(R.attr.colorOnSurface)
                }
                attendancePercentage <= DashboardAdapter.ATTENDANCE_SECOND_WARNING_THRESHOLD -> {
                    context.getThemeAttrColor(R.attr.colorPrimary)
                }
                attendancePercentage <= DashboardAdapter.ATTENDANCE_FIRST_WARNING_THRESHOLD -> {
                    context.getThemeAttrColor(R.attr.colorTimetableChange)
                }
                else -> context.getThemeAttrColor(R.attr.colorOnSurface)
            }
            dashboardLowAttendanceSubjectSubitemTitle.setTextColor(attendanceColor)
            dashboardLowAttendanceSubjectSubitemText.text = attendancePercentage.roundToInt().toString() + "%"
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val binding: SubitemDashboardLowAttendanceSubjectsBinding) :
        RecyclerView.ViewHolder(binding.root)
}