package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Semester
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.services.sync.notifications.NotificationScope
import io.github.wulkanowy.services.sync.notifications.NotificationType
import timber.log.Timber
import kotlin.random.Random

interface Work {

    suspend fun doWork(students: List<StudentWithCurrentSemester>)
}

abstract class BaseScopedWork<T>(private val scope: NotificationScope) : Work {

    constructor(notificationType: NotificationType) : this(notificationType.scope)

    private val verificationFrequency = 0.01

    final override suspend fun doWork(students: List<StudentWithCurrentSemester>) {
        val verifyResults = Random.Default.nextFloat() <= 1
        val groupedStudents = students.groupBy(scope) { it }

        for (group in groupedStudents) {
            val newData = if (verifyResults) {
                val data = group.value.map { fetchNewData(it) }.toSet()

                if (data.size > 1) {
                    Timber.wtf("Data differs inside scope `$scope` (${javaClass.name}): `$data`")
                }

                data.first()
            } else {
                // TODO fetch only from a single account and copy results with the ids of other students
                group.value.map { fetchNewData(it) }.first()
            }

            notify(group.key, newData, group.value.map { it.student })
        }
    }

    abstract suspend fun fetchNewData(student: StudentWithCurrentSemester): T

    abstract suspend fun notify(scope: String, newData: T, recipients: List<Student>)

    private fun <T> Iterable<T>.groupBy(
        scope: NotificationScope,
        extractor: (T) -> StudentWithCurrentSemester,
    ): Map<String, List<T>> {
        val grouper = { data: T ->
            val (student, semester) = extractor(data)
            when (scope) {
                NotificationScope.App -> ""
                NotificationScope.School -> student.schoolName
                NotificationScope.Class -> semester.diaryName
                NotificationScope.LessonGroup -> semester.diaryName
                NotificationScope.Person -> student.studentName
                NotificationScope.Account -> student.userName
            }
        }
        return this.groupBy(grouper)
    }
}

data class StudentWithCurrentSemester(val student: Student, val currSemester: Semester)