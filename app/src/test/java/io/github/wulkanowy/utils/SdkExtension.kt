package io.github.wulkanowy.utils

import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.sdk.Sdk
import timber.log.Timber

fun Sdk.init(student: Student): Sdk {
    email = student.email
    password = student.password
    symbol = student.symbol
    schoolSymbol = student.schoolSymbol
    studentId = student.studentId
    classId = student.classId

    if (Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.API) {
        scrapperBaseUrl = student.scrapperBaseUrl
        loginType = Sdk.ScrapperLoginType.valueOf(student.loginType)
    }
    loginId = student.userLoginId

    mode = Sdk.Mode.valueOf(student.loginMode)
    mobileBaseUrl = student.mobileBaseUrl
    certKey = student.certificateKey
    privateKey = student.privateKey

    emptyCookieJarInterceptor = true

    Timber.d("Sdk in ${student.loginMode} mode reinitialized")

    return this
}
