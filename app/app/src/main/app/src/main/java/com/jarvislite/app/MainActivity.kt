package com.jarvislite.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvislite.app.command.CommandRouter
import com.jarvislite.app.databinding.ActivityMainBinding
import com.jarvislite.app.service.NotificationReaderService
import com.jarvislite.app.voice.VoiceAssistant

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationService = NotificationReaderService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        VoiceAssistant.init(this)
        ensureMicPermission()

        // Step 1: send the user to the system screen where they grant
        // notification access. Android requires this to be done manually
        // in Settings — apps cannot request it via a normal popup, since
        // it's a sensitive, high-trust permission.
        binding.btnGrantNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Step 2: manually test the "listen and reply" flow, without
        // waiting for a real WhatsApp message. Useful while developing.
        binding.btnTestVoiceReply.setOnClickListener {
            VoiceAssistant.speak(this, "What would you like to say?")
            VoiceAssistant.listenOnce(
                context = this,
                onResult = { spokenText ->
                    binding.tvLastHeard.text = "Heard: $spokenText"
                    val sent = notificationService.sendReplyToLastMessage(spokenText)
                    val message = if (sent) {
                        "Reply sent: $spokenText"
                    } else {
                        "Nothing to reply to yet — trigger a WhatsApp message first."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                },
                onError = {
                    Toast.makeText(this, "Didn't catch that, try again.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Step 3: general-purpose command entry point. Say (or later,
        // wake-word trigger) anything — "search best pizza near me",
        // "reply I'll be there at 7", "remind me to call mom" — and
        // CommandRouter figures out what kind of task it is, checks
        // whether it has the permission it needs, and either does it
        // or asks the user to grant that permission on the spot.
        binding.btnTalkToJarvis.setOnClickListener {
            VoiceAssistant.speak(this, "What can I do?")
            VoiceAssistant.listenOnce(
                context = this,
                onResult = { spokenCommand ->
                    binding.tvLastHeard.text = "Heard: $spokenCommand"
                    CommandRouter.handle(this, spokenCommand, notificationService)
                },
                onError = {
                    Toast.makeText(this, "Didn't catch that, try again.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }
}
