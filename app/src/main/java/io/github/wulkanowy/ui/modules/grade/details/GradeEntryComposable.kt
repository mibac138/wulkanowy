package io.github.wulkanowy.ui.modules.grade.details

import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.material.composethemeadapter.MdcTheme
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.db.entities.GradeSummary
import io.github.wulkanowy.ui.modules.grade.GradeColorTheme
import io.github.wulkanowy.ui.modules.grade.GradeSubject
import io.github.wulkanowy.utils.toFormattedString
import java.time.LocalDate

/**
 * Load a quantity string resource.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @return the string data associated with the resource
 */
@Composable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity)
}

/**
 * Load a quantity string resource with formatting.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @param formatArgs the format arguments
 * @return the string data associated with the resource
 */
@Composable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity, *formatArgs)
}

@Composable
fun GradeDetailsComposable(data: GradeSubject, gradeColorTheme: GradeColorTheme) {
    val isRefreshing = rememberSaveable {
        mutableStateOf(false)
    }
    SwipeRefresh(state = rememberSwipeRefreshState(isRefreshing = isRefreshing.value), onRefresh = {
        isRefreshing.value = true
    }) {
        GradeContainerComposable(data = data, gradeColorTheme = gradeColorTheme)
    }
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun GradeContainerComposable(
    data: GradeSubject,
    gradeColorTheme: GradeColorTheme,
    modifier: Modifier = Modifier,
    onGradeClickListener: ((Grade) -> Unit)? = null,
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    Column(modifier) {
        Divider()
        Box(Modifier.clickable { expanded = !expanded }) {
            GradeHeaderComposable(data = data)
        }
        AnimatedContent(targetState = expanded) { expanded ->
            if (expanded) {
                Column(
                    Modifier
                        .padding(top = 4.dp)
                ) {
                    for (grade in data.grades) {
                        if (onGradeClickListener != null) {
                            Box(Modifier.clickable { onGradeClickListener(grade) }) {
                                GradeEntryComposable(
                                    grade = grade,
                                    gradeColorTheme = gradeColorTheme
                                )
                            }
                        } else {
                            GradeEntryComposable(grade = grade, gradeColorTheme = gradeColorTheme)
                        }
                    }
                }
            }
        }
//        Column(
//            Modifier
//                .wrapContentHeight()
//                .padding(top = 4.dp)
//                .animateContentSize()
//        ) {
//            AnimatedVisibility(visible = expanded, enter = fadeIn().plus(), exit = fadeOut()) {
//                for (grade in data.grades) {
//                    GradeEntryComposable(grade, gradeColorTheme)
//                }
//            }
//        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GradeHeaderComposable(
    data: GradeSubject,
    modifier: Modifier = Modifier
) {
    val unread = remember { data.grades.count { !it.isRead } }
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
                fontSize = 15.sp
            )
            Row {
                val avgText = if (data.average == .0) {
                    stringResource(id = R.string.grade_no_average)
                } else {
                    stringResource(id = R.string.grade_average, data.average)
                }
                Text(text = avgText, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = stringResource(id = R.string.grade_points_sum, data.points),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = quantityStringResource(
                        id = R.plurals.grade_number_item,
                        quantity = data.grades.size,
                        data.grades.size
                    ),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        AnimatedVisibility(visible = unread > 0) {
            Box(
                Modifier
                    .height(20.dp)
                    .wrapContentWidth()
                    .widthIn(min = 20.dp)
                    .background(
                        color = MaterialTheme.colors.primary,
                        shape = RoundedCornerShape(12.dp)
                    ), Alignment.Center
            ) {
                Text(
                    text = "255",
                    modifier = Modifier.padding(horizontal = 5.dp),
                    style = TextStyle(color = MaterialTheme.colors.onPrimary),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

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
                text = grade.description,
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
        if (!grade.isRead) {
            Icon(
                painter = painterResource(id = R.drawable.ic_all_round_mark),
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Preview
@Composable
private fun Container() {
    AppTheme {
        Surface {
            GradeDetailsComposable(
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
                        )
                    ),
                    true
                ),
                GradeColorTheme.MATERIAL
            )
        }
    }
}

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