package io.github.wulkanowy.ui.modules.timetable

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
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.google.android.material.composethemeadapter.MdcTheme
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Timetable
import io.github.wulkanowy.ui.modules.debug.notification.mock.debugTimetableItems
import io.github.wulkanowy.utils.getThemeAttrColor
import io.github.wulkanowy.utils.toFormattedString

fun Modifier.fillLineHeight(
    lineHeight: TextUnit = TextUnit.Unspecified
): Modifier = composed(debugInspectorInfo {
    name = "fillLineHeight"
    value = lineHeight
}) {
    val resolvedLineHeight = lineHeight.takeOrElse { LocalTextStyle.current.lineHeight }
    if (resolvedLineHeight.isSpecified) {
        val lineHeightPx = with(LocalDensity.current) { resolvedLineHeight.roundToPx() }
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val space = (lineHeightPx - placeable.height % lineHeightPx) % lineHeightPx
            layout(placeable.width, placeable.height + space) {
                placeable.place(0, space / 2)
            }
        }
    } else {
        Modifier
    }
}

@Composable
fun TimetableEntry(timetable: Timetable, present: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Box(modifier = Modifier.wrapContentHeight()) {
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 2.5.dp,
                color = if (present) colorResource(id = R.color.note_positive) else colorResource(id = R.color.note_negative)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = timetable.number.toString(),
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle()
                    .yellowIfTrue(timetable.changes || timetable.info.isNotBlank())
                    .redIfCanceled(timetable),
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 40.dp)
                    .fillLineHeight(40.sp),
            )
        }
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.padding(start = 8.dp, end = 10.dp)
        ) {
            Text(
                text = timetable.start.toFormattedString("HH:mm"),
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = timetable.end.toFormattedString("HH:mm"),
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Light
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp)
        ) {
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
            Row {
                TimetableEntryDetails(timetable)
            }
        }
    }
}

@Composable
private fun TimetableEntryDetails(timetable: Timetable) {
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
        if (timetable.group.isNotBlank()) { // only if enabled in preferences
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
fun TimetableEntryPreview() {
    MdcTheme {
        Surface {
            TimetableEntry(timetable = debugTimetableItems[0])
        }
    }
}

@Preview
@Composable
fun TimetableEntryPreview2() {
    MdcTheme {
        Surface {
            TimetableEntry(timetable = debugTimetableItems[1], true)
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