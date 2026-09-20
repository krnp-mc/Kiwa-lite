package com.jarvislite.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvislite.app.voice.VoiceAssistant

/**
 * Listens for incoming notifications on the device.
 *
 * This is an official Android API (NotificationListenerService) — the user
 * must explicitly grant "Notification access" in system Settings, since
 * Android treats this as a sensitive permission. Once granted, this
 * service receives a callback for every notification the phone shows.
 *
 * We filter to specific apps (e.g. WhatsApp) and, when a message comes in,
 * read it aloud via VoiceAssistant. If the notification supports an inline
 * "quick reply" action (RemoteInput — the same thing that lets you type a
 * reply without opening the app), we can send a reply through that action
 * once the user tells us what to say.
 */
class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotifListener"

        // Package names of apps we care about. WhatsApp's package is
        // "com.whatsapp" (or "com.whatsapp.w4b" for WhatsApp Business).
        // Add/remove packages here to control what Jarvis Lite reacts to.
        val WATCHED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )

        // Keep a reference to the last notification with a reply action,
        // so the UI/voice flow can send a reply to "the last message" once
        // the user says what to send. In a real app, keep a small history
        // (per sender) instead of just the single last one.
        var lastReplyableNotification: StatusBarNotification? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName !in WATCHED_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Someone"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (text.isBlank()) return

        Log.d(TAG, "New message from $title: $text")

        // Remember this notification if it has a reply action attached,
        // so we can use it later to actually send a response.
        if (findReplyAction(sbn.notification) != null) {
            lastReplyableNotification = sbn
        }

        // Read it aloud. VoiceAssistant is a small wrapper around
        // Android's TextToSpeech engine (see VoiceAssistant.kt).
        VoiceAssistant.speak(
            applicationContext,
            "Message from $title: $text"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn == lastReplyableNotification) {
            lastReplyableNotification = null
        }
    }

    /**
     * Finds the "quick reply" action on a notification, if one exists.
     * This is what lets us reply without opening WhatsApp — it's the
     * same mechanism the notification shade itself uses.
     */
    private fun findReplyAction(notification: Notification): Notification.Action? {
        return notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }

    /**
     * Sends [message] as a reply to the most recent replyable notification.
     * Call this after the user has said (or typed) what they want to send —
     * e.g. from MainActivity after a voice command like
     * "tell him I'll be there at 7".
     *
     * Returns true if a reply was sent, false if there was nothing to
     * reply to (e.g. no recent message with a quick-reply action).
     */
    fun sendReplyToLastMessage(message: String): Boolean {
        val sbn = lastReplyableNotification ?: return false
        val action = findReplyAction(sbn.notification) ?: return false
        val remoteInputs = action.remoteInputs ?: return false

        val intent = Intent()
        val bundle = Bundle()
        for (remoteInput in remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, message)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "Reply sent: $message")
            lastReplyableNotification = null
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Failed to send reply", e)
            false
        }
    }
}
