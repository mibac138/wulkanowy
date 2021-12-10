package io.github.wulkanowy.services.sync.works

import io.github.wulkanowy.data.db.entities.Note
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.NoteRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.sync.notifications.NewNoteNotification
import io.github.wulkanowy.services.sync.notifications.NotificationType
import io.github.wulkanowy.utils.waitForResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class NoteWork @Inject constructor(
    private val noteRepository: NoteRepository,
    private val preferencesRepository: PreferencesRepository,
    private val newNoteNotification: NewNoteNotification,
) : BaseScopedWork<List<Note>>(NotificationType.NEW_NOTE) {

    override suspend fun fetchNewData(student: StudentWithCurrentSemester): List<Note> {
        noteRepository.getNotes(
            student = student.student,
            semester = student.currSemester,
            forceRefresh = true,
            notify = preferencesRepository.isNotificationsEnable
        ).waitForResult()

        return noteRepository.getNotesFromDatabase(student.student).first()
            .filterNot { it.isNotified }
    }

    override suspend fun notify(scope: String, newData: List<Note>, recipients: List<Student>) {
        // Messages can be both to a single recipient and to multiple recipients. Only notify
        // about the same message once (see #1496)
        newNoteNotification.notify(scope, newData.distinct(), recipients)
        noteRepository.updateNotes(newData.onEach { it.isNotified = true })
    }
}

