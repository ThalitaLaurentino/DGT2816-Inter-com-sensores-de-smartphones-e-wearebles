package com.doma.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Escuta de voz por sessão: começa quando o app pede (pergunta ou
 * toque duplo) e termina ao reconhecer algo ou dar erro/timeout.
 *
 * onCommand recebe o texto reconhecido, ou null se a sessão terminou
 * sem reconhecer nada (timeout, erro, cancelamento).
 */
class VoiceCommandHelper(
    context: Context,
    private val onCommand: (String?) -> Unit,
    private val onError: (String) -> Unit
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private var running = false       // sessão de escuta autorizada
    private var sessionActive = false // sessão em andamento
    private var speaking = false      // TTS falando (mic em pausa)

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    /** true enquanto uma sessão de escuta está ativa ou prestes a iniciar. */
    fun isActive(): Boolean = running || sessionActive

    fun start() {
        if (!isRecognitionAvailable()) {
            onError("Reconhecimento de voz indisponível neste dispositivo")
            return
        }
        running = true
        listen()
    }

    fun stop() {
        running = false
        stopSession()
    }

    private fun stopSession() {
        if (!sessionActive) return
        sessionActive = false
        // SpeechRecognizer só pode ser usado na main thread
        handler.post { recognizer?.stopListening() }
    }

    /** Chamado quando o TTS começa a falar — pausa o microfone. */
    fun pauseForSpeech() {
        speaking = true
        stopSession()
    }

    /** Chamado quando o TTS termina de falar. */
    fun resumeAfterSpeech() {
        speaking = false
    }

    private fun listen() {
        // SpeechRecognizer só pode ser criado/usado na main thread —
        // start() pode vir do callback do TTS, que roda em outra thread
        handler.post {
            if (!running || speaking || sessionActive) return@post
            if (recognizer == null) createRecognizer()
            sessionActive = true
            recognizer?.startListening(recognizerIntent())
        }
    }

    private fun finish(command: String?) {
        sessionActive = false
        running = false
        onCommand(command)
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    private fun createRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    finish(matches?.firstOrNull())
                }

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        onError("Sem permissão de microfone")
                    }
                    finish(null)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun destroy() {
        stop()
        handler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }
}
