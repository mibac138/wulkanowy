package io.github.wulkanowy.data.repositories

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wulkanowy.data.db.dao.StudentAuthDataDao
import io.github.wulkanowy.data.db.dao.StudentDao
import io.github.wulkanowy.data.db.entities.AuthVersion
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentAuthData
import io.github.wulkanowy.utils.DispatchersProvider
import io.github.wulkanowy.utils.security.ScramblerException
import io.github.wulkanowy.utils.security.decrypt
import io.github.wulkanowy.utils.security.encrypt
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthDataRepository @Inject constructor(
    @ApplicationContext context: Context,
    studentDao: StudentDao,
    private val authDao: StudentAuthDataDao,
    private val dispatchers: DispatchersProvider,
) {
    private val passwordServiceMap = mapOf(
        AuthVersion.V0_Legacy to LegacyPasswordService(context, studentDao),
        AuthVersion.V1_Custom to CustomEncryptedPrefsPasswordService(context),
        AuthVersion.V1_AndroidX to AndroidXPasswordService(context),
    )

    suspend fun getPassword(student: Student): String = withContext(dispatchers.io) {
        val authData = student.getAuthData()
        passwordServiceMap[authData.version]!!
            .getPassword(authData.id, student)
            .takeIf { it != PASSWORD_NOT_FOUND }
            ?: throw ScramblerException("Password not found")
    }

    suspend fun switchAuthVersion(student: Student, target: AuthVersion): Unit =
        withContext(dispatchers.io) {
            val authData = student.getAuthData()
            if (authData.version == target) return@withContext

            val pass = passwordServiceMap[authData.version]!!.getPassword(authData.id, student)
            passwordServiceMap[target]!!.savePassword(authData.id, student, pass)
            passwordServiceMap[authData.version]!!.removePassword(authData.id, student)

            val newAuthData = authData.copy(version = target)
            authDao.updateAll(listOf(newAuthData))
        }

    suspend fun savePassword(student: Student, password: String): Unit =
        withContext(dispatchers.io) {
            // Android versions older than marshmallow do not have the required APIs
            // for our usage of androidx.security
            // Also, only use new password storage starting with the 2022/2023 school year
            // to be on the safe side in case something goes wrong
            val newSchoolYear =
                LocalDate.now().isAfter(LocalDate.of(2022, 7, 1))
            val luckyTester = Math.random() <= 0.05
            val authVersion =
                if (SUPPORTS_NEW_CRYPTO && (newSchoolYear || luckyTester)) AuthVersion.V1_AndroidX
                else AuthVersion.V1_Custom

            val authData = authDao.loadStudentAuthData(student.email)?.copy(version = authVersion)
                ?: StudentAuthData(email = student.email, version = authVersion)
            val id = authDao.upsertStudentAuthData(authData)

            passwordServiceMap[authVersion]!!.savePassword(id, student, password)
        }

    suspend fun removePassword(student: Student): Unit = withContext(dispatchers.io) {
        authDao.loadStudentAuthData(student.email)?.let { authData ->
            authDao.deleteAll(listOf(authData))
            passwordServiceMap[authData.version]?.removePassword(authData.id, student)
        }
    }

    private fun Student.tryGetAuthData(): StudentAuthData? =
        authDao.loadStudentAuthData(email)

    private fun Student.getAuthData(): StudentAuthData =
        tryGetAuthData() ?: throw ScramblerException("Auth data not found")
}

private val SUPPORTS_NEW_CRYPTO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
private const val PASSWORD_NOT_FOUND = 0.toChar().toString()

// FIXME Remove student parameter; only used for legacy format
private interface PasswordService {
    suspend fun getPassword(id: Long, student: Student): String
    suspend fun savePassword(id: Long, student: Student, pass: String)
    suspend fun removePassword(id: Long, student: Student)
}

private class AndroidXPasswordService(context: Context) : PasswordService {
    @RequiresApi(Build.VERSION_CODES.M)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "userdata",
        MasterKey.Builder(context, "wulkanowy_password2")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getPassword(id: Long, student: Student): String {
        if (!SUPPORTS_NEW_CRYPTO) throw IllegalStateException()
        return encryptedPrefs.getString("password::$id", null) ?: PASSWORD_NOT_FOUND
    }

    override suspend fun savePassword(id: Long, student: Student, pass: String) {
        if (!SUPPORTS_NEW_CRYPTO) throw IllegalStateException()
        encryptedPrefs.edit(commit = true) {
            putString("password::$id", pass)
        }
    }

    override suspend fun removePassword(id: Long, student: Student) {
        if (!SUPPORTS_NEW_CRYPTO) throw IllegalStateException()
        encryptedPrefs.edit(commit = true) {
            remove("password::$id")
        }
    }
}

private class CustomEncryptedPrefsPasswordService(
    private val context: Context,
) : PasswordService {
    private val prefs = context.getSharedPreferences("userdata_custom", Context.MODE_PRIVATE)

    override suspend fun getPassword(id: Long, student: Student): String =
        prefs.getString("password::$id", null)?.let { decrypt(it) } ?: PASSWORD_NOT_FOUND

    override suspend fun savePassword(id: Long, student: Student, pass: String) {
        prefs.edit(commit = true) {
            putString("password::$id", encrypt(pass, context))
        }
    }

    override suspend fun removePassword(id: Long, student: Student) {
        prefs.edit(commit = true) {
            remove("password::$id")
        }
    }
}

private class LegacyPasswordService(
    private val context: Context,
    private val studentDao: StudentDao,
) : PasswordService {
    override suspend fun getPassword(id: Long, student: Student): String {
        @Suppress("DEPRECATION")
        return studentDao.loadById(student.id)?.let { decrypt(it.password) } ?: PASSWORD_NOT_FOUND
    }

    @Suppress("DEPRECATION")
    override suspend fun savePassword(id: Long, student: Student, pass: String) {
        if (studentDao.loadById(student.id) == null) {
            // StudentRepository is less complex with this restriction
            throw IllegalStateException("Legacy password service is unsupported for new accounts")
        }
        student.password = encrypt(pass, context)
        studentDao.update(student)
        student.password = ""
    }

    @Suppress("DEPRECATION")
    override suspend fun removePassword(id: Long, student: Student) {
        student.password = ""
        studentDao.update(student)
        studentDao.loadByEmail(student.email).forEach {
            it.password = ""
            studentDao.update(it)
        }
    }
}
