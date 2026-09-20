package com.jarvislite.app.command

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvislite.app.service.NotificationReaderService

/**
 * Every capability Jarvis Lite has needs some permission to work.
 * This maps each [Capability] to what it requires and how to grant it,
 * so a command handler can just say "I need X" and get either
 * "already granted" or "here's how the user grants it" back —
 * instead of scattering permission checks across the codebase.
 */
enum class Capability {
    WEB_SEARCH,       // no special permission needed
    VOICE_INPUT,      // RECORD_AUDIO runtime permission
    READ_MESSAGES,    // Notification listener access (special settings screen)
    REPLY_MESSAGES,   // same as READ_MESSAGES — needs the listener active
    SET_REMINDER       // POST_NOTIFICATIONS (Android 13+) to show the reminder itself
}

sealed class PermissionCheck {
    object Granted : PermissionCheck()
    data class Missing(val explanation: String, val request: () -> Unit) : PermissionCheck()
}

object PermissionManager {

    /**
     * Checks whether [capability] is currently usable. If not, returns a
     * Missing result carrying both a spoken/displayed explanation
     * ("I don't have permission for this") and a ready-to-call [request]
     * action that sends the user to grant it.
     */
    fun check(activity: Activity, capability: Capability): PermissionCheck {
        return when (capability) {
            Capability.WEB_SEARCH -> PermissionCheck.Granted // opening a browser needs nothing special

            Capability.VOICE_INPUT -> {
                val granted = ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    PermissionCheck.Granted
                } else {
                    PermissionCheck.Missing(
                        explanation = "I don't have permission to use the microphone yet.",
                        request = {
                            ActivityCompat.requestPermissions(
                                activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_MIC
                            )
                        }
                    )
                }
            }

            Capability.READ_MESSAGES, Capability.REPLY_MESSAGES -> {
                val granted = isNotificationListenerEnabled(activity)
                if (granted) {
                    PermissionCheck.Granted
                } else {
                    PermissionCheck.Missing(
                        explanation = "I don't have permission to read your notifications yet. " +
                            "I'll open Settings so you can turn it on for Jarvis Lite.",
                        request = {
                            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }

            Capability.SET_REMINDER -> {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    PermissionCheck.Granted // not needed before Android 13
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        activity, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        PermissionCheck.Granted
                    } else {
                        PermissionCheck.Missing(
                            explanation = "I don't have permission to show notifications yet.",
                            request = {
                                ActivityCompat.requestPermissions(
                                    activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIF
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(activity: Activity): Boolean {
        val enabledListeners = Settings.Secure.getString(
            activity.contentResolver, "enabled_notification_listeners"
        ) ?: ""
        return enabledListeners.contains(activity.packageName)
    }

    const val REQUEST_CODE_MIC = 100
    const val REQUEST_CODE_NOTIF = 101
}
