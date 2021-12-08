package io.github.wulkanowy.data.enums

import com.fredporciuncula.flow.preferences.Serializer as PrefSerializer

enum class AttendanceInTimetable(val value: String) {
    No("no"),
    Line("line"),
    Dot("dot");

    companion object {
        fun getByValue(value: String) = values().find { it.value == value } ?: No
    }

    object Serializer : PrefSerializer<AttendanceInTimetable> {
        override fun deserialize(serialized: String): AttendanceInTimetable = getByValue(serialized)

        override fun serialize(value: AttendanceInTimetable): String = value.value
    }
}