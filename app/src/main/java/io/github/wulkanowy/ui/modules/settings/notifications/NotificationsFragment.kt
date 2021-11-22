package io.github.wulkanowy.ui.modules.settings.notifications

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.thelittlefireman.appkillermanager.AppKillerManager
import com.thelittlefireman.appkillermanager.exceptions.NoActionFoundException
import dagger.hilt.android.AndroidEntryPoint
import io.github.wulkanowy.R
import io.github.wulkanowy.ui.base.BaseActivity
import io.github.wulkanowy.ui.base.ErrorDialog
import io.github.wulkanowy.ui.modules.main.MainView
import io.github.wulkanowy.utils.AppInfo
import io.github.wulkanowy.utils.openInternetBrowser
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MainView.TitledView, NotificationsView {

    @Inject
    lateinit var presenter: NotificationsPresenter

    @Inject
    lateinit var appInfo: AppInfo

    override val titleStringId get() = R.string.pref_settings_notifications_title

    override val isNotificationPermissionGranted: Boolean
        get() {
            val packageNameList =
                NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            val appPackageName = requireContext().packageName

            return appPackageName in packageNameList
        }

    private val notificationSettingsPiggybackContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            presenter.onNotificationPiggybackPermissionResult()
        }

    private val notificationSettingsExactAlarmsContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            presenter.onNotificationExactAlarmPermissionResult()
        }

    override fun initView(showDebugNotificationSwitch: Boolean) {
        findPreference<Preference>(getString(R.string.pref_key_notification_debug))?.isVisible =
            showDebugNotificationSwitch

        findPreference<Preference>(getString(R.string.pref_key_notifications_fix_issues))?.run {
            isVisible = AppKillerManager.isDeviceSupported()
                && AppKillerManager.isAnyActionAvailable(requireContext())

            setOnPreferenceClickListener {
                presenter.onFixSyncIssuesClicked()
                true
            }
        }

        findPreference<Preference>(getString(R.string.pref_key_notifications_system_settings))
            ?.setOnPreferenceClickListener {
                presenter.onOpenSystemSettingsClicked()
                true
            }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater?,
        parent: ViewGroup?,
        state: Bundle?
    ): RecyclerView? = super.onCreateRecyclerView(inflater, parent, state)
        .also {
            it.itemAnimator = null
            it.layoutAnimation = null
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.onAttachView(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.scheme_preferences_notifications, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        presenter.onSharedPreferenceChanged(key)
    }

    override fun enableNotification(notificationKey: String, enable: Boolean) {
        findPreference<Preference>(notificationKey)?.run {
            isEnabled = enable
            summary = if (enable) null else getString(R.string.pref_notify_disabled_summary)
        }
    }

    override fun showError(text: String, error: Throwable) {
        (activity as? BaseActivity<*, *>)?.showError(text, error)
    }

    override fun showMessage(text: String) {
        (activity as? BaseActivity<*, *>)?.showMessage(text)
    }

    override fun showExpiredDialog() {
        (activity as? BaseActivity<*, *>)?.showExpiredDialog()
    }

    override fun showChangePasswordSnackbar(redirectUrl: String) {
        (activity as? BaseActivity<*, *>)?.showChangePasswordSnackbar(redirectUrl)
    }

    override fun openClearLoginView() {
        (activity as? BaseActivity<*, *>)?.openClearLoginView()
    }

    override fun showErrorDetailsDialog(error: Throwable) {
        ErrorDialog.newInstance(error).show(childFragmentManager, error.toString())
    }

    override fun showFixSyncDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_notify_fix_sync_issues)
            .setMessage(R.string.pref_notify_fix_sync_issues_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.pref_notify_open_system_settings) { _, _ ->
                try {
                    AppKillerManager.doActionPowerSaving(requireContext())
                    AppKillerManager.doActionAutoStart(requireContext())
                    AppKillerManager.doActionNotification(requireContext())
                } catch (e: NoActionFoundException) {
                    requireContext().openInternetBrowser(
                        "https://dontkillmyapp.com/${AppKillerManager.getDevice()?.manufacturer}",
                        ::showMessage
                    )
                }
            }
            .show()
    }

    @SuppressLint("InlinedApi")
    override fun openSystemSettings() {
        val intent = if (appInfo.systemVersion >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra("android.provider.extra.APP_PACKAGE", requireActivity().packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireActivity().packageName, null)
            }
        }
        try {
            requireActivity().startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    override fun openNotificationPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref_notification_piggyback_popup_title))
            .setMessage(getString(R.string.pref_notification_piggyback_popup_description))
            .setPositiveButton(getString(R.string.pref_notification_go_to_settings)) { _, _ ->
                notificationSettingsPiggybackContract.launch(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setNotificationPiggybackPreferenceChecked(false)
            }
            .setOnDismissListener { setNotificationPiggybackPreferenceChecked(false) }
            .show()
    }

    override fun openNotificationExactAlarmSettings() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref_notification_exact_alarm_popup_title))
            .setMessage(getString(R.string.pref_notification_exact_alarm_popup_descriptions))
            .setPositiveButton(getString(R.string.pref_notification_go_to_settings)) { _, _ ->
                notificationSettingsExactAlarmsContract.launch(Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM"))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setUpcomingLessonsNotificationPreferenceChecked(false)
            }
            .setOnDismissListener { setUpcomingLessonsNotificationPreferenceChecked(false) }
            .show()
    }

    override fun setNotificationPiggybackPreferenceChecked(isChecked: Boolean) {
        findPreference<SwitchPreferenceCompat>(getString(R.string.pref_key_notifications_piggyback))?.isChecked =
            isChecked
    }

    override fun setUpcomingLessonsNotificationPreferenceChecked(isChecked: Boolean) {
        findPreference<SwitchPreferenceCompat>(getString(R.string.pref_key_notifications_upcoming_lessons_enable))?.isChecked =
            isChecked
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}
