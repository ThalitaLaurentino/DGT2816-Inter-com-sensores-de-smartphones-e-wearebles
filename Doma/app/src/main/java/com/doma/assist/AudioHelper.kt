package com.doma.assist

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager

class AudioHelper(context: Context) {

    private val packageManager = context.packageManager
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun audioOutputAvailable(type: Int): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            return false
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == type }
    }

    fun currentOutputNames(): List<String> {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            return emptyList()
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { describe(it.type) }
    }

    fun registerDeviceCallback(callback: AudioDeviceCallback) {
        audioManager.registerAudioDeviceCallback(callback, null)
    }

    fun unregisterDeviceCallback(callback: AudioDeviceCallback) {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    companion object {
        fun describe(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Fone Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Fone Bluetooth (SCO)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Fone com fio"
            else -> "Saída de áudio ($type)"
        }
    }
}
