package io.github.wulkanowy.data.repositories

import io.github.wulkanowy.data.SdkFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoverRepository @Inject constructor(private val sdk: SdkFactory) {

    suspend fun getReCaptchaSiteKey(host: String, symbol: String): Pair<String, String> {
        return sdk.initUnauthorized().getPasswordResetCaptchaCode(host, symbol)
    }

    suspend fun sendRecoverRequest(
        url: String, symbol: String, email: String, reCaptchaResponse: String
    ): String = sdk.initUnauthorized().sendPasswordResetRequest(url, symbol, email, reCaptchaResponse)
}
