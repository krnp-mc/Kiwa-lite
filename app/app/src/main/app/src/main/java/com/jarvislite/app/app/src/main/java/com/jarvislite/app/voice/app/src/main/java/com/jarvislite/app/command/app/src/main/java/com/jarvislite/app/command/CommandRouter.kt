package com.jarvislite.app.command

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import com.jarvislite.app.service.NotificationReaderService
import com.jarvislite.app.voice.VoiceAssistant

/**
 * Takes a raw command (typed or transcribed from voice) and figures out
 * what the user wants, then either does it or reports back that a
 * permission is missing — asking for it in the moment rather than
 * failing silently.
 *
 * This is intentionally simple keyword matching for now. The natural
 * upgrade later is to send the command text to an LLM and have it return
 * a structured {capability, action, args} result instead of matching
 * keywords by hand — same interface, smarter routing.
 */
object CommandRouter {

    fun handle(activity: Activity, command: String, notificationService: NotificationReaderService) {
        val lower = command.lowercase()

        when {
            lower.startsWith("search") || lower.contains("google") || lower.contains("look up") -> {
                runIfPermitted(activity, Capability.WEB_SEARCH) {
                    val query = command
                        .replace(Regex("(?i)search( for)?|google|look up"), "")
                        .trim()
                    openWebSearch(activity, query)
                }
            }

            lower.startsWith("reply") || lower.startsWith("tell") || lower.startsWith("send") -> {
                runIfPermitted(activity, Capability.REPLY_MESSAGES) {
                    val message = extractMessageToSend(command)
                    val sent = notificationService.sendReplyToLastMessage(message)
                    val feedback = if (sent) {
                        "Sent: $message"
                    } else {
                        "There's no recent message to reply to."
                    }
                    VoiceAssistant.speak(activity, feedback)
                }
            }

            lower.contains("read") && lower.contains("message") -> {
                runIfPermitted(activity, Capability.READ_MESSAGES) {
                    VoiceAssistant.speak(activity, "Notification access is on — I'll read new messages as they come in.")
                }
            }

            lower.contains("remind") -> {
                runIfPermitted(activity, Capability.SET_REMINDER) {
                    // Reminder scheduling itself (AlarmManager / WorkManager) isn't
                    // wired up yet — this just confirms the permission path works.
                    VoiceAssistant.speak(activity, "Got it, I'll remind you. Reminder scheduling is coming in the next update.")
                }
            }

            else -> {
                VoiceAssistant.speak(activity, "I heard: $command, but I don't know how to handle that yet.")
            }
        }
    }

    /**
     * The core pattern: check the permission, and either run [action]
     * or speak + surface the "I don't have permission for this" message
     * along with a one-tap way to grant it.
     */
    private fun runIfPermitted(activity: Activity, capability: Capability, action: () -> Unit) {
        when (val result = PermissionManager.check(activity, capability)) {
            is PermissionCheck.Granted -> action()
            is PermissionCheck.Missing -> {
                VoiceAssistant.speak(activity, result.explanation)
                // In a full UI you'd show a dialog with an "Grant access" button
                // that calls result.request() when tapped. Here we trigger it
                // directly so the flow is testable end-to-end immediately.
                result.request()
            }
        }
    }

    private fun openWebSearch(activity: Activity, query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            // Fallback: open the query directly in a browser
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=${Uri_encode(query)}")
            )
            activity.startActivity(browserIntent)
        }
    }

    private fun Uri_encode(text: String): String =
        java.net.URLEncoder.encode(text, "UTF-8")

    /**
     * Very simple extraction: "tell him I'll be there at 7" -> "I'll be there at 7"
     * "reply yes I'm coming" -> "yes I'm coming"
     * This is exactly the kind of step that gets much better once routed
     * through an LLM instead of regex.
     */
    private fun extractMessageToSend(command: String): String {
        return command
            .replace(Regex("(?i)^(reply|tell him|tell her|tell them|send)\\s*"), "")
            .trim()
            .ifEmpty { command }
    }
}
