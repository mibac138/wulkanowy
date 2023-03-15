package io.github.wulkanowy.data

import com.chuckerteam.chucker.api.ChuckerInterceptor
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.AuthDataRepository
import io.github.wulkanowy.sdk.Sdk
import io.github.wulkanowy.utils.RemoteConfigHelper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SdkFactory @Inject constructor(
    private val authRepo: AuthDataRepository,
    private val chuckerInterceptor: ChuckerInterceptor,
    private val remoteConfig: RemoteConfigHelper,
) {

    suspend fun init(student: Student): Sdk = initUnauthorized().apply {
        email = student.email
        password = authRepo.getPassword(student)
        symbol = student.symbol
        schoolSymbol = student.schoolSymbol
        studentId = student.studentId
        classId = student.classId
        userAgentTemplate = remoteConfig.userAgentTemplate

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
    }

    fun initUnauthorized(): Sdk = Sdk().apply {
        androidVersion = android.os.Build.VERSION.RELEASE
        buildTag = android.os.Build.MODEL
        setSimpleHttpLogger { Timber.d(it) }

        // for debug only
        addInterceptor(chuckerInterceptor, network = true)
    }
}
