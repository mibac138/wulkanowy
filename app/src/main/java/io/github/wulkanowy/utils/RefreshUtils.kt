package io.github.wulkanowy.utils

import io.github.wulkanowy.data.db.SharedPrefProvider
import io.github.wulkanowy.data.db.entities.Mailbox
import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.enums.MessageFolder
import io.github.wulkanowy.data.repositories.PreferencesRepository
import timber.log.Timber
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

fun getRefreshKey(name: String, semester: Semester, start: LocalDate, end: LocalDate): String {
    return "${name}_${semester.studentId}_${semester.semesterId}_${start.monday}_${end.sunday}"
}

fun getRefreshKey(name: String, semester: Semester): String {
    return "${name}_${semester.studentId}_${semester.semesterId}"
}

fun getRefreshKey(name: String, student: Student): String {
    return "${name}_${student.userLoginId}"
}

fun getRefreshKey(name: String, mailbox: Mailbox?, folder: MessageFolder): String {
    return "${name}_${mailbox?.globalKey ?: "all"}_${folder.id}"
}

class AutoRefreshHelper @Inject constructor(
    private val sharedPref: SharedPrefProvider,
    private val preferencesRepository: PreferencesRepository,
) {

    fun shouldBeRefreshed(key: String): Boolean {
        val timestamp = sharedPref.getLong(key, 0).let(Instant::ofEpochMilli)
        val servicesInterval = preferencesRepository.servicesInterval

        val shouldBeRefreshed = timestamp < Instant.now().minus(ofMinutes(servicesInterval))

        Timber.d("Check if $key need to be refreshed: $shouldBeRefreshed (last refresh: $timestamp, interval: $servicesInterval min)")

        return shouldBeRefreshed
    }

    fun updateLastRefreshTimestamp(key: String) {
        sharedPref.putLong(key, Instant.now().toEpochMilli())
    }

    fun getLastRefreshTimestamp(key: String): Instant {
        val refreshTimestampMilli = sharedPref.getLong(key, 0)
        return Instant.ofEpochMilli(refreshTimestampMilli)
    }
}
