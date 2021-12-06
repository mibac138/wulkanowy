package io.github.wulkanowy.ui.modules.grade.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wulkanowy.R
import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.db.entities.GradeSummary
import io.github.wulkanowy.data.enums.GradeColorTheme
import io.github.wulkanowy.data.repositories.GradeRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.ui.modules.grade.GradeAverageProvider
import io.github.wulkanowy.ui.modules.grade.GradeSubject
import io.github.wulkanowy.utils.FlowTrigger
import io.github.wulkanowy.utils.ResourceViewComposable
import io.github.wulkanowy.utils.collectAsState2
import io.github.wulkanowy.utils.flowWithTrigger
import io.github.wulkanowy.utils.quantityStringResource
import io.github.wulkanowy.utils.toFormattedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DetailsVM @Inject constructor(
    private val studentRepository: StudentRepository,
    private val semesterRepository: SemesterRepository,
    private val gradeRepository: GradeRepository,
    private val averageProvider: GradeAverageProvider,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    private val _colorTheme = preferencesRepository.gradeColorThemeFlow
    val colorTheme: StateFlow<GradeColorTheme>
        get() = _colorTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradeColorTheme.VULCAN)

    private val stateTrigger = FlowTrigger()
    private val _subjects = flowWithTrigger(stateTrigger) { manuallyTriggered ->
        val student = studentRepository.getCurrentStudent()
        val semester = semesterRepository.getCurrentSemester(student)
        emitAll(
            averageProvider.getGradesDetailsWithAverage(
                student,
                semester.semesterId,
                forceRefresh = manuallyTriggered
            )
        )
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000))
    val subjects: SharedFlow<Resource<List<GradeSubject>>>
        get() = _subjects

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            stateTrigger.triggerUntilCompletion()
            _isRefreshing.value = false
        }
    }

    fun retry() {
        viewModelScope.launch {
            stateTrigger.trigger()
        }
    }

    fun markAsRead(grade: Grade) {
        viewModelScope.launch {
            grade.isRead = true
            gradeRepository.updateGrade(grade)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GradeDetailsComposable() {
    val viewModel: DetailsVM = viewModel()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val subjectsRes by viewModel.subjects.collectAsState2(Resource.loading())
    val colorTheme by viewModel.colorTheme.collectAsState()
    ResourceViewComposable(resource = subjectsRes, onRetry = { viewModel.retry() }) { subjects ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
            onRefresh = { viewModel.refresh() },
            indicator = { s, trigger ->
                SwipeRefreshIndicator(s, trigger, contentColor = MaterialTheme.colors.primary)
            }
        ) {
            val state = rememberLazyListState()
            LazyColumn(state = state) {
                itemsIndexed(subjects, { _, item -> item.summary.id }) { _, item ->
                    GradeContainerComposable(
                        data = item,
                        gradeColorTheme = colorTheme,
                        onGradeClickListener = { grade -> viewModel.markAsRead(grade) },
                        modifier = Modifier.animateItemPlacement()
                    )
                    Divider(modifier = Modifier.animateItemPlacement())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun GradeContainerComposable(
    data: GradeSubject,
    gradeColorTheme: GradeColorTheme,
    onGradeClickListener: ((Grade) -> Unit),
    modifier: Modifier = Modifier,
    onExpand: (expanded: Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Box(
            Modifier
                .clickable { expanded = !expanded; onExpand(expanded) }
                .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
        ) {
            GradeHeaderComposable(data = data)
        }
        AnimatedContent(
            targetState = expanded,
            modifier = Modifier.padding(top = 4.dp)
        ) { expanded ->
            Column {
                for (grade in data.grades.takeIf { expanded } ?: emptyList()) {
                    Box(Modifier.clickable { onGradeClickListener(grade) }) {
                        GradeEntryComposable(
                            grade = grade,
                            gradeColorTheme = gradeColorTheme,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 20.dp,
                                top = 7.dp,
                                bottom = 7.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GradeHeaderComposable(
    data: GradeSubject,
    modifier: Modifier = Modifier
) {
    val unread by derivedStateOf { data.grades.count { !it.isRead } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 5.dp)
        ) {
            Text(
                text = data.subject,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Row {
                val avgText = if (data.average == .0) {
                    stringResource(id = R.string.grade_no_average)
                } else {
                    stringResource(id = R.string.grade_average, data.average)
                }
                Text(
                    text = avgText,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (data.points.isNotBlank()) {
                    Text(
                        text = stringResource(id = R.string.grade_points_sum, data.points),
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = quantityStringResource(
                        id = R.plurals.grade_number_item,
                        quantity = data.grades.size,
                        data.grades.size
                    ),
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        AnimatedContent(unread) {
            if (it > 0) {
                Box(
                    Modifier
                        .height(20.dp)
                        .wrapContentWidth()
                        .widthIn(min = 20.dp)
                        .padding(end = 2.dp)
                        .background(
                            color = MaterialTheme.colors.primary,
                            shape = RoundedCornerShape(12.dp)
                        ), Alignment.Center
                ) {
                    Text(
                        text = "$it",
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                        style = TextStyle(color = MaterialTheme.colors.onPrimary),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GradeEntryComposable(
    grade: Grade,
    gradeColorTheme: GradeColorTheme,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        GradeComposable(grade = grade, colorTheme = gradeColorTheme)
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f)
        ) {
            Text(
                text = when {
                    grade.description.isNotBlank() -> grade.description
                    grade.gradeSymbol.isNotBlank() -> grade.gradeSymbol
                    else -> stringResource(R.string.all_no_description)
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontSize = 14.sp,
            )
            Row {
                Text(text = grade.date.toFormattedString(), maxLines = 1, fontSize = 12.sp)
                Text(
                    text = "${stringResource(id = R.string.grade_weight)}: ${grade.weight}",
                    maxLines = 1,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        AnimatedContent(!grade.isRead) {
            if (it) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_all_round_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

//@Preview
//@Composable
//private fun Container() {
//    AppTheme {
//        Surface {
//            GradeDetailsComposable(listOf(
//                GradeSubject(
//                    "Biologia",
//                    5.0,
//                    "100",
//                    GradeSummary(0, 0, 0, "Biologia", "5", "5", "100", "100", "100", 5.0),
//                    listOf(
//                        Grade(
//                            0,
//                            0,
//                            "Biologia",
//                            "5",
//                            "5".toDoubleOrNull() ?: 0.0,
//                            0.0,
//                            "",
//                            "000000",
//                            "",
//                            "sprawdzian",
//                            "3,00",
//                            3.0,
//                            LocalDate.now(),
//                            ""
//                        )
//                    ),
//                    true
//                ),
//                GradeSubject(
//                    "Fizyka",
//                    4.0,
//                    "100",
//                    GradeSummary(0, 0, 0, "Fizyka", "4", "4", "50", "50", "50", 4.0),
//                    listOf(
//                        Grade(
//                            0,
//                            0,
//                            "Fizyka",
//                            "4",
//                            "4".toDoubleOrNull() ?: 0.0,
//                            0.0,
//                            "",
//                            "000000",
//                            "",
//                            "kartkówka",
//                            "1,00",
//                            1.0,
//                            LocalDate.now(),
//                            ""
//                        ).also {
//                            it.isRead = false
//                        }
//                    ),
//                    true
//                )),
//                GradeColorTheme.MATERIAL
//            )
//        }
//    }
//}

@Preview
@Composable
private fun Header() {
    AppTheme {
        Surface {
            GradeHeaderComposable(
                GradeSubject(
                    "Biologia",
                    5.0,
                    "100",
                    GradeSummary(0, 0, 0, "Biologia", "5", "5", "100", "100", "100", 5.0),
                    listOf(
                        Grade(
                            0,
                            0,
                            "Biologia",
                            "5",
                            "5".toDoubleOrNull() ?: 0.0,
                            0.0,
                            "",
                            "000000",
                            "",
                            "sprawdzian",
                            "3,00",
                            1.0,
                            LocalDate.now(),
                            ""
                        ).apply { isRead = false }
                    ),
                    true
                )
            )
        }
    }
}

@Preview
@Composable
private fun Read() {
    AppTheme {
        Surface {
            GradeEntryComposable(
                grade = Grade(
                    0,
                    0,
                    "Biologia",
                    "5",
                    "5".toDoubleOrNull() ?: 0.0,
                    0.0,
                    "",
                    "000000",
                    "",
                    "sprawdzian",
                    "3,00",
                    1.0,
                    LocalDate.now(),
                    ""
                ).apply { isRead = true }, gradeColorTheme = GradeColorTheme.MATERIAL
            )
        }
    }
}

@Preview
@Composable
private fun Unread() {
    AppTheme {
        Surface {
            GradeEntryComposable(
                grade = Grade(
                    0,
                    0,
                    "Biologia",
                    "5",
                    "5".toDoubleOrNull() ?: 0.0,
                    0.0,
                    "",
                    "000000",
                    "",
                    "sprawdzian",
                    "3,00",
                    1.0,
                    LocalDate.now(),
                    "",
                ).apply { isRead = false }, gradeColorTheme = GradeColorTheme.MATERIAL
            )
        }
    }
}

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MdcTheme(content = content)
}