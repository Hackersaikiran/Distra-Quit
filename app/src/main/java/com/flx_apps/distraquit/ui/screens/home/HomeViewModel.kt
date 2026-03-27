package com.flx_apps.distraquit.ui.screens.home;

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import com.flx_apps.distraquit.DistraQuitApplication
import com.flx_apps.distraquit.system_integration.DistraQuitAccessibilityService
import com.flx_apps.distraquit.system_integration.DistraQuitDeviceAdminReceiver
import com.flx_apps.distraquit.system_integration.DistraQuitState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * The component name of the accessibility service. This is used to enable and disable the service.
 * @see HomeViewModel.activateAccessibilityService
 * @see HomeViewModel.disableAccessibilityService
 */
val AccessibilityServiceComponent =
    DistraQuitApplication::class.java.`package`!!.name + "/" + DistraQuitAccessibilityService::class.java.name

/**
 * The state of the snackbar on the home screen.
 */
enum class HomeScreenSnackbarState {
    Hidden, ShowStartAcccessibilityServiceManually
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver

    /**
     * The current state of the accessibility service. This is a [StateFlow], so it can be observed
     * by other components.
     * @see DistraQuitAccessibilityService.state
     */
    val distraQuitState: StateFlow<DistraQuitState> = DistraQuitAccessibilityService.state

    private val _snackbarState = MutableStateFlow(HomeScreenSnackbarState.Hidden)
    val snackbarState: StateFlow<HomeScreenSnackbarState> = _snackbarState

    /**
     * Toggles the state of the accessibility service.
     * @return the new state of the accessibility service or null if the (de-)activation failed
     * @see DistraQuitAccessibilityService
     * @see DistraQuitState
     */
    fun toggleDistraQuitIsRunning(): DistraQuitState? {
        Timber.d("state = ${distraQuitState.value}")
        val shouldBeRunning = distraQuitState.value != DistraQuitState.Active
        kotlin.runCatching { // if we don't have the permission to write secure settings, an exception will be thrown
            if (shouldBeRunning && activateAccessibilityService()) {
                return DistraQuitState.Active
            } else if (!shouldBeRunning && disableAccessibilityService()) {
                return DistraQuitState.Inactive
            }
        }

        // (de-)activation of accessibility service failed, so we need to show a snackbar to ask the user to do it manually
        setSnackbarState(HomeScreenSnackbarState.ShowStartAcccessibilityServiceManually)
        return null
    }

    fun setSnackbarState(state: HomeScreenSnackbarState) {
        _snackbarState.value = state
    }

    /**
     * Activates the accessibility service. This is done by adding the service to the list of
     * enabled accessibility services and starting the service. The service is then triggered
     * manually once to make sure it is running.
     * @see DistraQuitAccessibilityService
     */
    private fun activateAccessibilityService(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "$accessibilityServices:$AccessibilityServiceComponent".trim(':')
        )
        Settings.Secure.putString(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1"
        )
        return application.startService(
            Intent(
                application, DistraQuitAccessibilityService::class.java
            )
        ) != null
    }

    /**
     * Disables the accessibility service.
     * @see DistraQuitAccessibilityService
     */
    private fun disableAccessibilityService(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            accessibilityServices.replace(AccessibilityServiceComponent, "").trim(':')
        )
        return application.stopService(
            Intent(
                application, DistraQuitAccessibilityService::class.java
            )
        )
    }

    /**
     * Stops DistraQuit and all running features, revokes the device admin permission and uninstalls.
     */
    fun uninstallDistraQuit() {
        // call onDestroy() manually to run all cleanup tasks (e.g. stop all features) in a blocking way
        // (as application.stopService() is asynchronous)
        DistraQuitAccessibilityService.instance?.onDestroy()
        kotlin.runCatching { disableAccessibilityService() } // run this anyway
        kotlin.runCatching { DistraQuitDeviceAdminReceiver.revokePermission(application) }
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = "package:${application.packageName}".toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        application.startActivity(intent)
    }
}