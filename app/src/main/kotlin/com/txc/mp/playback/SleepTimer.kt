package com.txc.mp.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep timer yang dijalankan oleh [PlaybackService], jadi tetap berjalan walau app
 * ditutup ke home atau Activity dihancurkan. UI hanya memanggil fungsi di sini dan
 * membaca [remainingMs].
 *
 * Dua mode: hitung mundur menit, atau "akhir lagu ini". Volume memudar pelan di
 * beberapa detik terakhir sebelum musik berhenti.
 */
object SleepTimer {

    /** [endAtElapsed] null + [endOfTrack] true = berhenti di akhir lagu yang sedang diputar. */
    data class Request(val endAtElapsed: Long?, val endOfTrack: Boolean)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    /** Sisa waktu (ms); null = timer tidak aktif. */
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    val isEndOfTrack: Boolean get() = _request.value?.endOfTrack == true

    /** True saat volume sedang dipudarkan - crossfade tidak boleh menimpa volume. */
    @Volatile
    var fading: Boolean = false

    fun start(minutes: Int) {
        val ms = minutes.coerceAtLeast(1) * 60_000L
        _request.value = Request(SystemClock.elapsedRealtime() + ms, false)
        _remainingMs.value = ms
    }

    fun startEndOfTrack() {
        _request.value = Request(null, true)
        _remainingMs.value = 0L
    }

    /** Tambah waktu pada timer menit yang sedang berjalan. */
    fun extend(minutes: Int) {
        val r = _request.value ?: return
        val end = r.endAtElapsed ?: return
        _request.value = r.copy(endAtElapsed = end + minutes * 60_000L)
    }

    fun cancel() {
        _request.value = null
        _remainingMs.value = null
        fading = false
    }

    internal fun publishRemaining(ms: Long?) {
        _remainingMs.value = ms
    }
}
