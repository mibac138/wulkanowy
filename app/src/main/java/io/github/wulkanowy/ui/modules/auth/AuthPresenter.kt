package io.github.wulkanowy.ui.modules.auth

import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.ui.base.ErrorHandler
import kotlinx.coroutines.launch
import javax.inject.Inject

class AuthPresenter @Inject constructor(
    private val semesterRepository: SemesterRepository,
    errorHandler: ErrorHandler,
    studentRepository: StudentRepository
) : BasePresenter<AuthView>(errorHandler, studentRepository) {

    private var pesel: String = ""

    override fun onAttachView(view: AuthView) {
        super.onAttachView(view)
        view.enableAuthButton(pesel.length == 11)
        view.showSuccess(false)
        view.showProgress(false)

        loadName()
    }

    private fun loadName() {
        presenterScope.launch {
            runCatching { studentRepository.getCurrentStudent(false) }
                .onSuccess { view?.showDescriptionWithName(it.studentName) }
                .onFailure { errorHandler.dispatch(it) }
        }
    }

    fun onPeselChange(newPesel: String?) {
        pesel = newPesel.orEmpty()

        view?.enableAuthButton(pesel.length == 11)
        view?.showPeselError(false)
        view?.showInvalidPeselError(false)
    }

    fun authorize() {
        presenterScope.launch {
            view?.showProgress(true)
            view?.showContent(false)

            if (!isValidPESEL(pesel)) {
                view?.showInvalidPeselError(true)
                view?.showProgress(false)
                view?.showContent(true)
                return@launch
            }

            runCatching {
                val student = studentRepository.getCurrentStudent()
                val semester = semesterRepository.getCurrentSemester(student)

                val isSuccess = studentRepository.authorizePermission(student, semester, pesel)
                if (isSuccess) {
                    studentRepository.refreshStudentName(student, semester)
                }
                isSuccess
            }
                .onFailure { errorHandler.dispatch(it) }
                .onSuccess {
                    if (it) {
                        view?.showSuccess(true)
                        view?.showContent(false)
                        view?.showPeselError(false)
                    } else {
                        view?.showSuccess(false)
                        view?.showContent(true)
                        view?.showPeselError(true)
                    }
                }

            view?.showProgress(false)
        }
    }

    private fun isValidPESEL(peselString: String): Boolean {
        if (peselString.length != 11) {
            return false
        }

        val weights = intArrayOf(1, 3, 7, 9, 1, 3, 7, 9, 1, 3)
        var sum = 0

        for (i in 0 until 10) {
            sum += weights[i] * Character.getNumericValue(peselString[i])
        }

        sum %= 10
        sum = 10 - sum
        sum %= 10

        return sum == Character.getNumericValue(peselString[10])
    }
}
