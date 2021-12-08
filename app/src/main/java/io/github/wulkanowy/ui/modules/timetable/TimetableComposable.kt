package io.github.wulkanowy.ui.modules.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wulkanowy.R
import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.db.entities.Attendance
import io.github.wulkanowy.data.db.entities.Timetable
import io.github.wulkanowy.data.enums.AttendanceInTimetable
import io.github.wulkanowy.data.repositories.AttendanceRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.data.repositories.TimetableRepository
import io.github.wulkanowy.ui.modules.debug.notification.mock.debugTimetableItems
import io.github.wulkanowy.utils.FlowTrigger
import io.github.wulkanowy.utils.SwipeRefreshResourceViewComposable
import io.github.wulkanowy.utils.flatFlowWithTrigger
import io.github.wulkanowy.utils.getThemeAttrColor
import io.github.wulkanowy.utils.toFormattedString
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TimetableVM @Inject constructor(
    private val studentRepository: StudentRepository,
    private val semesterRepository: SemesterRepository,
    private val timetableRepository: TimetableRepository,
    private val attendanceRepository: AttendanceRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _showGroups = preferencesRepository.showGroupsInPlanFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    )
    val showGroups: StateFlow<Boolean>
        get() = _showGroups

    private val _attendanceMode = preferencesRepository.showAttendanceInTimetableFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, AttendanceInTimetable.No
    )
    val attendanceMode: StateFlow<AttendanceInTimetable>
        get() = _attendanceMode

    private val _showCurrentLessonTimer = preferencesRepository.showTimetableTimersFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showCurrentLessonTimer: StateFlow<Boolean>
        get() = _showCurrentLessonTimer

    private val stateTrigger = FlowTrigger()
    private val _timetableWithAttendance = flatFlowWithTrigger(stateTrigger) { manuallyTriggered ->
        val student = studentRepository.getCurrentStudent()
        val semester = semesterRepository.getCurrentSemester(student)
        val timetableFlow = timetableRepository.getTimetable(
            student,
            semester,
            LocalDate.now(),
            LocalDate.now(),
            manuallyTriggered
        ).map { Resource(it.status, it.data?.lessons, it.error) }
        val attendanceFlow = attendanceRepository.getAttendance(
            student,
            semester,
            LocalDate.now(),
            LocalDate.now(),
            manuallyTriggered
        )
        combine(timetableFlow, attendanceFlow) { a, b ->
            val timetable = a.data?.sortedBy { it.number }
            val attendance = b.data?.sortedBy { it.number }
            Resource(
                a.status,
                timetable?.map { it to attendance?.singleOrNull { att -> att.number == it.number } },
                a.error ?: b.error
            )
        }
    }.catch { emit(Resource.error(it)) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    val timetableWithAttendance: SharedFlow<Resource<List<Pair<Timetable, Attendance?>>>>
        get() = _timetableWithAttendance

    fun refresh() {
        viewModelScope.launch {
            stateTrigger.trigger(emitLoading = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableEntryList(viewModel: TimetableVM = viewModel()) {
    val showGroups by viewModel.showGroups.collectAsState()
    val showTimer by viewModel.showCurrentLessonTimer.collectAsState()
    val attendanceMode by viewModel.attendanceMode.collectAsState()
    val res by viewModel.timetableWithAttendance.collectAsState(initial = Resource.loading())
    SwipeRefreshResourceViewComposable(resource = res, onRefresh = { viewModel.refresh() }) {
        val state = rememberLazyListState()
        LazyColumn(state = state) {
            itemsIndexed(it, { _, item -> item.first.id }) { _, item ->
                TimetableEntry(
                    timetable = item.first,
                    attendance = item.second,
                    showGroups = showGroups,
                    showTimer = showTimer,
                    attendanceMode = attendanceMode,
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(top = 6.dp, bottom = 6.dp)
                )
                Divider(modifier = Modifier.animateItemPlacement())
            }
        }
    }
}

@Composable
fun TimetableEntry(
    timetable: Timetable,
    attendance: Attendance?,
    showGroups: Boolean,
    showTimer: Boolean,
    attendanceMode: AttendanceInTimetable,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(IntrinsicSize.Min)
            .padding(start = 6.dp)
    ) {
        if (attendanceMode == AttendanceInTimetable.Line) {
            Box(modifier = Modifier.wrapContentHeight()) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 3.dp,
                    color = attendance.indicatorColor()
                )
            }
        }
        Text(
            text = timetable.number.toString(),
            fontSize = 32.sp,
            maxLines = 1,
            style = TextStyle(textAlign = TextAlign.Center)
                .yellowIfTrue(timetable.changes || timetable.info.isNotBlank())
                .redIfCanceled(timetable),
            modifier = Modifier
                .padding(
                    end = 6.dp,
                    bottom = 4.dp
                ) // this padding is here only to center the text vertically
                // because Compose doesn't yet have `Modifier.ignoreFontPadding()`, this hack is
                // required to keep it relatively centered
                .height(40.dp)
                .widthIn(min = 38.dp + if (attendanceMode == AttendanceInTimetable.Line) 2.dp else 0.dp)
        )
        Column(
            modifier = Modifier
                .padding(end = 10.dp)
                .height(40.dp),
        ) {
            Text(
                text = timetable.start.toFormattedString("HH:mm"),
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(
                text = timetable.end.toFormattedString("HH:mm"),
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.End)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = timetable.subject,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    style = TextStyle()
                        .lineThroughIfCanceled(timetable)
                        .redIfCanceled(timetable)
                        .yellowIfTrue(timetable.subjectOld.isNotBlank() && timetable.subject != timetable.subjectOld)
                )
                if (showTimer) {
                    TimeLeftIndicator(text = "20 min left")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TimetableEntryDetails(timetable, showGroups)
                if (attendanceMode == AttendanceInTimetable.Dot) {
                    AttendanceIndicatorDot(attendance)
                }
            }
        }
    }
}

private fun Attendance?.indicatorColor() = this?.let { when {
    it.presence -> Color.Green
    it.absence -> Color.Red
    it.excused -> Color.Magenta
    it.lateness -> Color.Yellow
    else -> null
} } ?: Color.Transparent

@Composable
private fun AttendanceIndicatorDot(attendance: Attendance?) {
    Box(modifier = Modifier
        .padding(horizontal = 4.dp)
        .padding(top = 4.dp)
        .background(
            color = attendance.indicatorColor(),
            shape = RoundedCornerShape(6.dp),
        )
        .padding(6.dp), contentAlignment = Alignment.Center) {}
}

@Composable
private fun TimetableEntryDetails(timetable: Timetable, showGroups: Boolean) {
    Row {
    if (timetable.info.isNotBlank() && !timetable.changes) {
        Text(
            text = timetable.info,
            fontSize = 13.sp,
            style = TextStyle(
                color = Color(LocalContext.current.getThemeAttrColor(R.attr.colorTimetableChange))
            )
                .redIfCanceled(timetable)
        )
    } else {
        Text(
            text = timetable.room,
            fontSize = 13.sp,
            maxLines = 1,
            fontWeight = FontWeight.Light,
            style = TextStyle().yellowIfTrue(timetable.roomOld.isNotBlank() && timetable.room != timetable.roomOld)
        )
        if (timetable.group.isNotBlank() && showGroups) {
            Text(
                text = timetable.group,
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 10.dp, end = 5.dp),
            )
        }
        Text(
            text = timetable.teacher,
            fontSize = 13.sp,
            maxLines = 1,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 10.dp),
            style = TextStyle().yellowIfTrue(timetable.teacherOld.isNotBlank())
        )
    }
    }
}

@Composable
private fun TimeLeftIndicator(text: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .background(
                color = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp), Alignment.Center
    ) {
        Text(text = text, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colors.onPrimary)
    }
}

@Composable
private fun TextStyle.redIfCanceled(lesson: Timetable) =
    this.copy(color = if (lesson.canceled) MaterialTheme.colors.primary else this.color)

@Composable
private fun TextStyle.yellowIfTrue(bool: Boolean) =
    this.copy(color = if (bool) Color(LocalContext.current.getThemeAttrColor(R.attr.colorTimetableChange)) else this.color)

@Composable
private fun TextStyle.lineThroughIfCanceled(lesson: Timetable) =
    this.copy(textDecoration = if (lesson.canceled) TextDecoration.LineThrough else this.textDecoration)

@Preview
@Composable
fun TimeLeftIndicatorPreview() {
    MdcTheme {
        Surface {
            TimeLeftIndicator("jeszcze 10min")
        }
    }
}

@Preview
@Composable
fun TimetableEntryPreview() {
    MdcTheme {
        Surface {
            TimetableEntry(
                timetable = debugTimetableItems[0],
                null,
                true,
                false,
                attendanceMode = AttendanceInTimetable.Line
            )
        }
    }
}

@Preview
@Composable
fun TimetableEntryPreview2() {
    MdcTheme {
        Surface {
            TimetableEntry(
                timetable = debugTimetableItems[1],
                null,
                true,
                true,
                attendanceMode = AttendanceInTimetable.Dot
            )
        }
    }
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onSurface.copy(alpha = DividerAlpha),
    thickness: Dp = 1.dp
) {
    Box(
        modifier
//            .fillMaxHeight()
            .width(thickness)
            .background(color = color)
    )
}

private const val DividerAlpha = 0.12f