package io.github.wulkanowy.ui.modules.grade.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wulkanowy.data.db.entities.Grade
import io.github.wulkanowy.data.enums.GradeColorTheme
import io.github.wulkanowy.utils.getBackgroundColor
import java.time.LocalDate

@Composable
fun GradeComposable(grade: Grade, colorTheme: GradeColorTheme) {
    Row {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 45.dp, minHeight = 40.dp)
                .background(colorResource(grade.getBackgroundColor(colorTheme)))
        ) {
            Text(
                text = grade.entry,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private val THEME = GradeColorTheme.MATERIAL
@Preview
@Composable
private fun GradeComposablePreviewOther() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("50%"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview1() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("1"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview2() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("2"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview3() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("3"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview4() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("4"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview5() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("5"),
            THEME
        )
    }
}

@Preview
@Composable
private fun GradeComposablePreview6() {
    MaterialTheme {
        GradeComposable(
            grade = gradeFrom("6"),
            THEME
        )
    }
}

private fun gradeFrom(entry: String) = Grade(
    0,
    0,
    "Biologia",
    entry,
    entry.toDoubleOrNull() ?: 0.0,
    0.0,
    "",
    "000000",
    "",
    "",
    "",
    1.0,
    LocalDate.now(),
    ""
)