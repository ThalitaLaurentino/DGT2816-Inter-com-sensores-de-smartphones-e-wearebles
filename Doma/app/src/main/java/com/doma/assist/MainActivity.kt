package com.doma.assist

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : Activity() {

    private lateinit var audioHelper: AudioHelper
    private lateinit var voiceHelper: VoiceCommandHelper
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech = 0

    // Fluxo "deseja ouvir?": mensagem aguardando resposta do usuário.
    // Mensagens que chegam no meio de uma interação ficam na fila e só
    // são anunciadas quando a conversa atual termina.
    private var pendingMessage: String? = null
    private var awaitingReply = false
    private var listenAfterSpeech = false
    private var replyAttempts = 0
    private var isForeground = false
    private val pendingInbox = ArrayDeque<String>()

    private lateinit var statusText: TextView
    private lateinit var messages: MutableList<String>
    private lateinit var messagesAdapter: ArrayAdapter<String>
    private var lastReadIndex = 0

    // Mensagens/alertas chegam sozinhos e são lidos em voz alta,
    // sem necessidade de toque na tela (acessibilidade).
    private val handler = Handler(Looper.getMainLooper())
    private var messageIndex = 0
    private val incomingMessages by lazy {
        resources.getStringArray(R.array.incoming_messages).toList()
    }

    private val incomingMessageTask = object : Runnable {
        override fun run() {
            receiveMessage(incomingMessages[messageIndex++ % incomingMessages.size])
            handler.postDelayed(this, MESSAGE_INTERVAL_MS)
        }
    }

    // --- Gatilhos de voz acessíveis (sem necessidade de ver/tocar a tela) ---

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private lateinit var gestureDetector: GestureDetector

    /** Agitar o pulso inicia a escuta de comando de voz. */
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val accel = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            val now = System.currentTimeMillis()
            if (accel > SHAKE_THRESHOLD && now - lastShakeTime > SHAKE_DEBOUNCE_MS) {
                lastShakeTime = now
                startVoiceCommand()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            updateAudioStatus()
            if (audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)) {
                addMessage(getString(R.string.msg_bluetooth_connected))
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            updateAudioStatus()
            if (!audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)) {
                addMessage(getString(R.string.msg_bluetooth_disconnected))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioHelper = AudioHelper(this)
        voiceHelper = VoiceCommandHelper(this,
            onCommand = ::handleVoiceCommand,
            onError = { toast(it) })

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                RC_POST_NOTIFICATIONS
            )
        }

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("pt", "BR")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {}
                    // Callbacks do TTS rodam em outra thread — volta pra main
                    override fun onDone(utteranceId: String) {
                        handler.post { onSpeechFinished() }
                    }
                    @Deprecated("deprecated")
                    override fun onError(utteranceId: String) {
                        handler.post { onSpeechFinished() }
                    }
                })
                speak(getString(R.string.startup_speech))
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                // Toque duplo em qualquer ponto da tela inicia a escuta
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    startVoiceCommand()
                    return true
                }
            })

        statusText = findViewById(R.id.audioStatus)

        messages = mutableListOf(
            getString(R.string.msg_reminder_1),
            getString(R.string.msg_reminder_2)
        )
        // "Ler mensagens" só lê as novas — as iniciais já foram exibidas
        lastReadIndex = messages.size
        val listView = findViewById<ListView>(R.id.messageList)
        messagesAdapter =
            ArrayAdapter(this, R.layout.list_item_message, messages)
        listView.adapter = messagesAdapter

        updateAudioStatus()
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        audioHelper.registerDeviceCallback(deviceCallback)
        updateAudioStatus()
        handler.postDelayed(incomingMessageTask, FIRST_MESSAGE_DELAY_MS)
        accelerometer?.let {
            sensorManager.registerListener(
                shakeListener, it, SensorManager.SENSOR_DELAY_UI
            )
        }
        // Anuncia mensagens que chegaram com o app fora da tela
        drainInbox()
    }

    override fun onPause() {
        isForeground = false
        audioHelper.unregisterDeviceCallback(deviceCallback)
        sensorManager.unregisterListener(shakeListener)
        voiceHelper.stop()
        // Mensagem aguardando resposta volta pra fila — reanuncia ao reabrir
        pendingMessage?.let { pendingInbox.addFirst(it) }
        pendingMessage = null
        awaitingReply = false
        super.onPause()
    }

    /** Toque duplo em qualquer lugar da tela (inclusive sobre a lista). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Botões físicos laterais do relógio (STEM 1–3) iniciam a escuta. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_STEM_2 ||
            keyCode == KeyEvent.KEYCODE_STEM_3
        ) {
            startVoiceCommand()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacks(incomingMessageTask)
        tts?.shutdown()
        voiceHelper.destroy()
        super.onDestroy()
    }

    private fun updateAudioStatus() {
        val outputs = audioHelper.currentOutputNames()
        statusText.text = if (outputs.isEmpty()) {
            getString(R.string.audio_status_none)
        } else {
            getString(R.string.audio_status, outputs.joinToString())
        }
    }

    private fun addMessage(text: String) {
        messages.add(text)
        messagesAdapter.notifyDataSetChanged()
        // Mantém a mensagem mais recente visível na lista
        findViewById<ListView>(R.id.messageList)
            .setSelection(messages.size - 1)
    }

    /**
     * Chegada de uma nova mensagem/alerta: aparece na lista, vira uma
     * notificação real no relógio e o app pergunta em voz alta se a
     * pessoa quer ouvir. Se outra interação estiver em andamento, a
     * mensagem espera na fila para não sobrepor avisos.
     */
    private fun receiveMessage(text: String) {
        addMessage(text)
        postNotification(getString(R.string.new_message_title), text)
        if (!isForeground || interactionBusy()) {
            pendingInbox.addLast(text)
            return
        }
        announceMessage(text)
    }

    private fun interactionBusy(): Boolean =
        awaitingReply || listenAfterSpeech ||
            pendingSpeech > 0 || voiceHelper.isActive()

    private fun announceMessage(text: String) {
        pendingMessage = text
        awaitingReply = true
        replyAttempts = 0
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            listenAfterSpeech = true
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_RECORD_AUDIO)
        }
        speak(getString(R.string.new_message_prompt))
    }

    /** Anuncia a próxima mensagem da fila, se houver e estiver livre. */
    private fun drainInbox() {
        if (isForeground && ttsReady && !interactionBusy() &&
            pendingInbox.isNotEmpty()
        ) {
            announceMessage(pendingInbox.removeFirst())
        }
    }

    private fun readMessagesAloud() {
        if (!ttsReady) {
            toast(getString(R.string.tts_unavailable))
            return
        }
        val unread = messages.drop(lastReadIndex)
        if (unread.isEmpty()) {
            speak(getString(R.string.no_new_messages))
            return
        }
        unread.forEach { speak(it) }
        lastReadIndex = messages.size
    }

    private fun startVoiceCommand() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_RECORD_AUDIO)
            return
        }
        listenAfterSpeech = true
        speak(getString(R.string.listening))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_RECORD_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (awaitingReply) {
                    voiceHelper.start()
                } else {
                    startVoiceCommand()
                }
            } else {
                toast(getString(R.string.mic_permission_denied))
            }
        }
    }

    private fun handleVoiceCommand(command: String?) {
        // Resposta a "Nova mensagem. Deseja ouvir?" — uma segunda chance
        // se a resposta não for entendida na primeira vez
        if (awaitingReply && !listenAfterSpeech) {
            val answer = command?.lowercase(Locale.getDefault()).orEmpty()
            when {
                answer.contains("sim") -> {
                    awaitingReply = false
                    val message = pendingMessage
                    pendingMessage = null
                    message?.let { speak(it) }
                    lastReadIndex = messages.size
                }
                answer.contains("não") || answer.contains("nao") -> {
                    awaitingReply = false
                    pendingMessage = null
                    speak(getString(R.string.message_ignored))
                }
                replyAttempts == 0 -> {
                    replyAttempts = 1
                    listenAfterSpeech = true
                    speak(getString(R.string.new_message_retry))
                }
                else -> {
                    awaitingReply = false
                    pendingMessage = null
                    drainInbox()
                }
            }
            return
        }
        if (command == null) return

        val normalized = command.lowercase(Locale.getDefault())
        when {
            normalized.contains("hora") -> speakTime()
            normalized.contains("alerta") -> playSafetyAlert()
            normalized.contains("ler") || normalized.contains("mensagem") ->
                readMessagesAloud()
            normalized.contains("bluetooth") || normalized.contains("fone") ->
                openBluetoothSettings()
            else -> speak(getString(R.string.command_not_recognized, command))
        }
    }

    private fun speakTime() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        val text = if (minute == 0) {
            getString(R.string.time_speech_exact, hour)
        } else {
            getString(R.string.time_speech, hour, minute)
        }
        speak(text)
    }

    private fun playSafetyAlert() {
        postNotification(
            getString(R.string.alert_notification_title),
            getString(R.string.alert_message)
        )
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
        tone.release()
        speak(getString(R.string.alert_message))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun postNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Tocar na notificação (ou toque duplo com TalkBack) abre o app
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("EXTRA_CONNECTION_ONLY", true)
            putExtra("EXTRA_CLOSE_ON_CONNECT", true)
            putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 1)
        }
        startActivity(intent)
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        pendingSpeech++
        voiceHelper.pauseForSpeech()
        val result = tts?.speak(
            text, TextToSpeech.QUEUE_ADD, null, "tts_${System.nanoTime()}"
        )
        if (result == TextToSpeech.ERROR) onSpeechFinished()
    }

    private fun onSpeechFinished() {
        pendingSpeech = (pendingSpeech - 1).coerceAtLeast(0)
        if (pendingSpeech != 0) return
        voiceHelper.resumeAfterSpeech()
        if (listenAfterSpeech) {
            listenAfterSpeech = false
            voiceHelper.start()
        } else {
            drainInbox()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val RC_RECORD_AUDIO = 1
        private const val RC_POST_NOTIFICATIONS = 2
        private const val ALERT_CHANNEL_ID = "alertas"
        private const val ALERT_NOTIFICATION_ID = 100
        private const val FIRST_MESSAGE_DELAY_MS = 8_000L
        private const val MESSAGE_INTERVAL_MS = 30_000L
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_DEBOUNCE_MS = 2_000L
    }
}
