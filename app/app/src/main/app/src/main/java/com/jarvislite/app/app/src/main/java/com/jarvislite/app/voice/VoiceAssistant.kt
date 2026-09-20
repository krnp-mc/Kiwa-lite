package com.jarvislite.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper around Android's built-in TextToSpeech (speak notifications
 * aloud) and SpeechRecognizer (listen for the user's spoken reply).
 *
 * Both are free, on-device (or Google-powered) Android APIs — no external
 * service needed to get this working. Later you can swap speak()/listen()
 * to call a nicer cloud voice (e.g. ElevenLabs) or Whisper if you want
 * higher quality, without changing anything that calls this class.
 */
object VoiceAssistant {

    private var tts: TextToSpeech? = null

    /** Must be called once, e.g. from MainActivity.onCreate(), before speak() is used. */
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    /**
     * Starts listening for a single spoken phrase and returns the
     * recognized text via [onResult]. Requires RECORD_AUDIO permission
     * to already be granted, and must be called from an Activity (it
     * needs a UI context for the recognizer to attach to).
     */
    fun listenOnce(
        context: Context,
        onResult: (String) -> Unit,
        onError: (() -> Unit)? = null
    ) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text != null) onResult(text) else onError?.invoke()
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                onError?.invoke()
                recognizer.destroy()
            }

            // Unused callbacks — required by the interface.
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }
}
