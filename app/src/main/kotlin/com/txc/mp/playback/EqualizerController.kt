package com.txc.mp.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh

/** Susunan band equalizer. Frekuensi dalam Hz, lebar band dalam oktaf. */
enum class EqLayout(val id: String, val label: String, val freqs: DoubleArray, val bandwidthOctaves: Double) {
    TEN(
        "10", "10 Band",
        doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0),
        1.0
    ),
    FIFTEEN(
        "15", "15 Band",
        doubleArrayOf(25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0),
        2.0 / 3.0
    );

    /** Q dari lebar band dalam oktaf. */
    val q: Double get() = 1.0 / (2.0 * sinh(ln(2.0) / 2.0 * bandwidthOctaves))

    companion object {
        fun byId(id: String?): EqLayout = values().firstOrNull { it.id == id } ?: TEN
    }
}

/** Preset didefinisikan pada 10 frekuensi acuan [EqLayout.TEN], lalu diinterpolasi ke layout lain. */
class EqPreset(val id: String, val label: String, private val gains10: FloatArray) {
    fun gainsFor(freqs: DoubleArray): FloatArray {
        val ref = EqLayout.TEN.freqs
        return FloatArray(freqs.size) { i ->
            val f = freqs[i]
            val raw: Float = when {
                f <= ref.first() -> gains10.first()
                f >= ref.last() -> gains10.last()
                else -> {
                    var hi = 1
                    while (hi < ref.size - 1 && ref[hi] < f) hi++
                    val lo = hi - 1
                    val t = ((ln(f) - ln(ref[lo])) / (ln(ref[hi]) - ln(ref[lo]))).toFloat()
                    gains10[lo] + (gains10[hi] - gains10[lo]) * t
                }
            }
            (raw * 2f).roundToInt() / 2f
        }
    }
}

val EqPresets: List<EqPreset> = listOf(
    EqPreset("flat", "Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("bass", "Bass Boost", floatArrayOf(6f, 5.5f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("bass_cut", "Bass Reducer", floatArrayOf(-6f, -5f, -4f, -2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("treble", "Treble Boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 6f)),
    EqPreset("treble_cut", "Treble Reducer", floatArrayOf(0f, 0f, 0f, 0f, 0f, -1f, -2.5f, -4f, -5f, -6f)),
    EqPreset("vocal", "Vocal", floatArrayOf(-2f, -3f, -3f, 1f, 4f, 4f, 3f, 2f, 0f, -2f)),
    EqPreset("rock", "Rock", floatArrayOf(4.5f, 3.5f, 2.5f, 1f, -0.5f, -1f, 0.5f, 2.5f, 3.5f, 4.5f)),
    EqPreset("pop", "Pop", floatArrayOf(-1.5f, -0.5f, 1f, 2.5f, 3f, 2.5f, 1f, -0.5f, -1f, -1.5f)),
    EqPreset("jazz", "Jazz", floatArrayOf(3f, 2f, 1f, 1.5f, -1.5f, -1.5f, 0f, 1f, 2f, 3f)),
    EqPreset("classical", "Klasik", floatArrayOf(3f, 2.5f, 2f, 1.5f, -1f, -1f, 0f, 1.5f, 2.5f, 3f)),
    EqPreset("dance", "Dance", floatArrayOf(6f, 4.5f, 1.5f, 0f, 0f, -3.5f, -4.5f, -4.5f, 0f, 0f)),
    EqPreset("hiphop", "Hip-Hop", floatArrayOf(5f, 4.5f, 1.5f, 3f, -1f, -1f, 1.5f, -0.5f, 2f, 3f)),
    EqPreset("electronic", "Electronic", floatArrayOf(4.5f, 3.5f, 1f, -0.5f, -2f, 1.5f, 0.5f, 1f, 3.5f, 4.5f)),
    EqPreset("acoustic", "Akustik", floatArrayOf(3.5f, 3f, 2.5f, 1f, 1.5f, 1.5f, 2.5f, 3f, 2.5f, 1.5f)),
    EqPreset("loudness", "Loudness", floatArrayOf(5f, 3.5f, 0f, -1f, -1f, -1f, 0f, -0.5f, 3f, 4f)),
    EqPreset("night", "Malam", floatArrayOf(-3f, -2f, 0f, 1f, 2f, 2f, 1f, 0f, -2f, -4f))
)

const val EQ_RESPONSE_POINTS = 96
const val EQ_MIN_GAIN_DB = -12f
const val EQ_MAX_GAIN_DB = 12f

data class EqualizerUiState(
    val enabled: Boolean = false,
    val layoutId: String = EqLayout.TEN.id,
    val bandFreqs: List<Int> = EqLayout.TEN.freqs.map { it.roundToInt() },
    val gainsDb: List<Float> = List(EqLayout.TEN.freqs.size) { 0f },
    val preampDb: Float = 0f,
    val bassDb: Float = 0f,
    val stereoWidth: Float = 1f,
    /** null = kustom (sudah diubah manual). */
    val selectedPresetId: String? = "flat",
    /** Respons frekuensi gabungan (dB) di [EQ_RESPONSE_POINTS] titik log 20 Hz..20 kHz, untuk digambar. */
    val response: List<Float> = List(EQ_RESPONSE_POINTS) { 0f }
)

/**
 * Sumber kebenaran tunggal untuk pengaturan equalizer. Pengaturan disimpan ke
 * SharedPreferences (selamat dari app di-close / proses dimatikan) dan didorong
 * ke [processor] yang dipakai pipeline audio ExoPlayer.
 */
class EqualizerController private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mp_equalizer_v2", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var persistJob: Job? = null

    val processor = EqualizerAudioProcessor()

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    // Gain tersimpan per layout supaya pindah 10 <-> 15 band tidak menghilangkan settingan.
    private val storedGains = HashMap<String, FloatArray>()

    init {
        loadSaved()
        publish(_state.value)
    }

    private val currentLayout: EqLayout get() = EqLayout.byId(_state.value.layoutId)

    fun setEnabled(enabled: Boolean) = mutate { it.copy(enabled = enabled) }

    fun setLayout(id: String) {
        val newLayout = EqLayout.byId(id)
        if (newLayout.id == _state.value.layoutId) return
        storedGains[currentLayout.id] = _state.value.gainsDb.toFloatArray()
        val presetId = _state.value.selectedPresetId
        val presetGains = presetId?.let { pid -> EqPresets.firstOrNull { it.id == pid }?.gainsFor(newLayout.freqs) }
        val gains = presetGains ?: storedGains[newLayout.id] ?: FloatArray(newLayout.freqs.size)
        mutate {
            it.copy(
                layoutId = newLayout.id,
                bandFreqs = newLayout.freqs.map { f -> f.roundToInt() },
                gainsDb = gains.toList()
            )
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        val g = ((gainDb * 2f).roundToInt() / 2f).coerceIn(EQ_MIN_GAIN_DB, EQ_MAX_GAIN_DB)
        val st = _state.value
        if (index !in st.gainsDb.indices) return
        if (st.gainsDb[index] == g && st.enabled) return
        mutate {
            it.copy(
                enabled = true,
                gainsDb = it.gainsDb.toMutableList().also { list -> list[index] = g },
                selectedPresetId = null
            )
        }
    }

    fun applyPreset(presetId: String) {
        val preset = EqPresets.firstOrNull { it.id == presetId } ?: return
        val gains = preset.gainsFor(currentLayout.freqs).toList()
        mutate { it.copy(enabled = presetId != "flat" || it.enabled, gainsDb = gains, selectedPresetId = presetId) }
    }

    fun setPreamp(db: Float) {
        val v = ((db * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
        mutate { it.copy(enabled = true, preampDb = v) }
    }

    fun setBassBoost(db: Float) {
        val v = ((db * 2f).roundToInt() / 2f).coerceIn(0f, 12f)
        mutate { it.copy(enabled = true, bassDb = v) }
    }

    fun setStereoWidth(width: Float) {
        val v = ((width * 20f).roundToInt() / 20f).coerceIn(0f, 2f)
        mutate { it.copy(enabled = true, stereoWidth = v) }
    }

    fun resetAll() {
        val size = currentLayout.freqs.size
        mutate {
            it.copy(
                gainsDb = List(size) { 0f },
                preampDb = 0f, bassDb = 0f, stereoWidth = 1f,
                selectedPresetId = "flat"
            )
        }
    }

    private fun mutate(transform: (EqualizerUiState) -> EqualizerUiState) {
        val updated = transform(_state.value)
        val withResponse = updated.copy(response = computeResponse(updated))
        _state.value = withResponse
        publish(withResponse)
        schedulePersist()
    }

    /** Dorong snapshot baru ke prosesor audio. */
    private fun publish(st: EqualizerUiState) {
        val l = EqLayout.byId(st.layoutId)
        val gains = DoubleArray(st.gainsDb.size) { st.gainsDb[it].toDouble() }
        // Headroom otomatis: kalau ada boost, turunkan level sedikit supaya tidak pecah.
        val maxGain = if (gains.isEmpty()) 0.0 else gains.max()
        val maxBoost = max(0.0, max(maxGain, st.bassDb.toDouble()))
        val effectivePreamp = st.preampDb.toDouble() - maxBoost * 0.5
        processor.params = EqParams(
            enabled = st.enabled,
            bandFreqs = l.freqs.copyOf(),
            bandGainsDb = gains,
            q = l.q,
            preampDb = effectivePreamp,
            bassDb = st.bassDb.toDouble(),
            stereoWidth = st.stereoWidth.toDouble()
        )
    }

    private fun computeResponse(st: EqualizerUiState): List<Float> {
        val l = EqLayout.byId(st.layoutId)
        val rate = 48000
        val coeffs = ArrayList<BiquadCoeffs>()
        for (i in l.freqs.indices) {
            val g = st.gainsDb.getOrNull(i)?.toDouble() ?: 0.0
            if (abs(g) >= 0.05) coeffs += Biquads.peaking(rate, l.freqs[i], g, l.q)
        }
        if (abs(st.bassDb) >= 0.05f) coeffs += Biquads.lowShelf(rate, EqParams.BASS_FREQ_HZ, st.bassDb.toDouble())
        val n = EQ_RESPONSE_POINTS
        return List(n) { i ->
            val f = 20.0 * 1000.0.pow(i.toDouble() / (n - 1)) // 20 Hz .. 20 kHz
            var db = 0.0
            for (c in coeffs) db += Biquads.magnitudeDb(c, rate, f)
            db.toFloat()
        }
    }

    // ---------- penyimpanan ----------

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(300) // digabung supaya drag slider tidak menulis disk tiap frame
            persistNow()
        }
    }

    private fun persistNow() {
        try {
            val s = _state.value
            storedGains[s.layoutId] = s.gainsDb.toFloatArray()
            val json = JSONObject()
            json.put("enabled", s.enabled)
            json.put("layout", s.layoutId)
            json.put("preamp", s.preampDb.toDouble())
            json.put("bass", s.bassDb.toDouble())
            json.put("width", s.stereoWidth.toDouble())
            json.put("preset", s.selectedPresetId ?: JSONObject.NULL)
            val gainsJson = JSONObject()
            for ((id, arr) in storedGains) {
                val a = JSONArray()
                arr.forEach { a.put(it.toDouble()) }
                gainsJson.put(id, a)
            }
            json.put("gains", gainsJson)
            prefs.edit().putString("state", json.toString()).apply()
        } catch (e: Exception) {
            // gagal simpan tidak boleh menjatuhkan app
        }
    }

    private fun loadSaved() {
        val raw = prefs.getString("state", null)
        if (raw == null) {
            _state.value = _state.value.copy(response = computeResponse(_state.value))
            return
        }
        try {
            val json = JSONObject(raw)
            val l = EqLayout.byId(json.optString("layout", EqLayout.TEN.id))
            val gainsJson = json.optJSONObject("gains")
            for (candidate in EqLayout.values()) {
                val arr = gainsJson?.optJSONArray(candidate.id) ?: continue
                if (arr.length() == candidate.freqs.size) {
                    storedGains[candidate.id] = FloatArray(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
                }
            }
            val presetId: String? = if (json.isNull("preset")) null else json.optString("preset", "flat")
            val gains = storedGains[l.id] ?: FloatArray(l.freqs.size)
            val restored = EqualizerUiState(
                enabled = json.optBoolean("enabled", false),
                layoutId = l.id,
                bandFreqs = l.freqs.map { it.roundToInt() },
                gainsDb = gains.toList(),
                preampDb = json.optDouble("preamp", 0.0).toFloat().coerceIn(-12f, 12f),
                bassDb = json.optDouble("bass", 0.0).toFloat().coerceIn(0f, 12f),
                stereoWidth = json.optDouble("width", 1.0).toFloat().coerceIn(0f, 2f),
                selectedPresetId = presetId
            )
            _state.value = restored.copy(response = computeResponse(restored))
        } catch (e: Exception) {
            val fresh = EqualizerUiState()
            _state.value = fresh.copy(response = computeResponse(fresh))
        }
    }

    companion object {
        @Volatile private var INSTANCE: EqualizerController? = null
        fun getInstance(context: Context): EqualizerController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: EqualizerController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
