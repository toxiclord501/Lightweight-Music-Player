package com.txc.mp.playback

import android.content.Context
import com.txc.mp.MpApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status & saklar mode bit-perfect.
 *
 * Saat aktif, jalur audio dibuat "bersih": equalizer, karaoke, crossfade, dan
 * perubahan kecepatan dilewati, sampel PCM tidak diubah, dan (Android 14+) app
 * meminta mixer bit-perfect ke DAC USB yang terpasang. Bit-perfect sampai ke DAC
 * HANYA terjadi bila perangkat + DAC mendukungnya; [status] menjelaskan kondisi aktual.
 */
object BitPerfect {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _status = MutableStateFlow("Nonaktif")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    var active: Boolean = false
        private set

    fun load(context: Context) {
        val on = try { MpApplication.instance.settingsRepository.getBitPerfect() } catch (e: Exception) { false }
        set(on)
    }

    fun setEnabled(enabled: Boolean) {
        try { MpApplication.instance.settingsRepository.setBitPerfect(enabled) } catch (e: Exception) { }
        set(enabled)
    }

    private fun set(enabled: Boolean) {
        active = enabled
        _enabled.value = enabled
        if (!enabled) _status.value = "Nonaktif"
        else if (_status.value == "Nonaktif") _status.value = "Aktif - menunggu pemutaran"
    }

    fun updateStatus(text: String) {
        _status.value = text
    }
}
