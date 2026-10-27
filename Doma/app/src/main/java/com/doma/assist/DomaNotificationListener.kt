package com.doma.assist

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Lê em voz alta as notificações que chegam ao relógio.
 *
 * Exige que o usuário ative o acesso a notificações do app em:
 * Configurações > Apps > Acesso especial > Acesso a notificações.
 */
class DomaNotificationListener : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("pt", "BR")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ignora notificações do próprio app (evita falar o alerta duas vezes)
        // e notificações "em andamento" (ex.: player de música).
        if (sbn.packageName == packageName || sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val message = listOfNotNull(title, text)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        if (message.isBlank()) return

        speak(getString(R.string.notification_speech, message))
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "notif_${System.nanoTime()}")
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
