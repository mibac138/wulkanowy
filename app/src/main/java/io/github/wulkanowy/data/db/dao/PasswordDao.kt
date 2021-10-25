package io.github.wulkanowy.data.db.dao

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.utils.security.decrypt
import io.github.wulkanowy.utils.security.encrypt
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface PasswordDao {
    /**
     * Sets the `password` field of `student` to a value read from secure storage
     */
    fun loadPassword(student: Student)

    /**
     * Invalidates the `password` field of `student` and saves it to secure storage
     */
    fun savePassword(student: Student)
}

@Singleton
class PasswordDaoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PasswordDao {
    @RequiresApi(Build.VERSION_CODES.M)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "userdata",
        MasterKey.Builder(context, "wulkanowy_password2").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun loadPassword(student: Student) {
        student.password = if (student.newPasswordStorage) {
            if (!SUPPORTS_NEW_CRYPTO) {
                Timber.e("New password storage enabled for unsupported device: %s", Build.VERSION.SDK_INT)
                ""
            } else {
                encryptedPrefs.getPassword(student, null) ?: ""
            }
        } else decrypt(student.password)
    }

    override fun savePassword(student: Student) {
        if (student.newPasswordStorage) {
            if (!SUPPORTS_NEW_CRYPTO) {
                Timber.w("New password storage enabled for unsupported device: %s; saving in old format", Build.VERSION.SDK_INT)
                student.password = encrypt(student.password, context)
                student.newPasswordStorage = false
            } else {
                encryptedPrefs.edit(commit = true) {
                    putPassword(student)
                }
                student.password = ""
            }
        } else student.password = encrypt(student.password, context)
    }
}

private fun SharedPreferences.Editor.putPassword(student: Student, password: String = student.password) {
    putString("${student.email}.password", password)
}

private fun SharedPreferences.getPassword(student: Student, defValue: String?) =
    getString("${student.email}.password", defValue)


private val SUPPORTS_NEW_CRYPTO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
