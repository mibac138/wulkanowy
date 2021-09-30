package io.github.wulkanowy.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.time.Month

@Entity(tableName = "AttendanceSummary")
data class AttendanceSummary(

    @ColumnInfo(name = "student_id")
    val studentId: Int,

    @ColumnInfo(name = "diary_id")
    val diaryId: Int,

    @ColumnInfo(name = "subject_id")
    val subjectId: Int,

    val month: Month,

    val presence: Int,

    val absence: Int,

    @ColumnInfo(name = "absence_excused")
    val absenceExcused: Int,

    @ColumnInfo(name = "absence_for_school_reasons")
    val absenceForSchoolReasons: Int,

    val lateness: Int,

    @ColumnInfo(name = "lateness_excused")
    val latenessExcused: Int,

    val exemption: Int
) : Serializable {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

fun List<AttendanceSummary>.sum() = AttendanceSummary(
    month = Month.APRIL,
    presence = this.sumOf { it.presence },
    absence = this.sumOf { it.absence },
    absenceExcused = this.sumOf { it.absenceExcused },
    absenceForSchoolReasons = this.sumOf { it.absenceForSchoolReasons },
    exemption = this.sumOf { it.exemption },
    lateness = this.sumOf { it.lateness },
    latenessExcused = this.sumOf { it.latenessExcused },
    diaryId = this.getOrNull(0)?.diaryId ?: -1,
    studentId = this.getOrNull(0)?.studentId ?: -1,
    subjectId = this.getOrNull(0)?.subjectId ?: -1
)