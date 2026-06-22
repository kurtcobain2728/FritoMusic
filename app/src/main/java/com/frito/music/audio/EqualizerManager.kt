package com.frito.music.audio

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(
    val index: Short,
    val centerFreqHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    var currentLevel: Short
)

data class EqPreset(
    val index: Short,
    val name: String
)

class EqualizerManager(private val context: Context) {
    private var equalizer: Equalizer? = null
    private val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("is_enabled", false))
    val isEnabled = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands = _bands.asStateFlow()

    private val _presets = MutableStateFlow<List<EqPreset>>(emptyList())
    val presets = _presets.asStateFlow()

    private val _selectedPreset = MutableStateFlow(prefs.getInt("selected_preset", -1).toShort())
    val selectedPreset = _selectedPreset.asStateFlow()

    fun attachAudioSession(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value

                // Load bands
                val numBands = numberOfBands
                val bandLevelRange = bandLevelRange
                val minLvl = bandLevelRange[0]
                val maxLvl = bandLevelRange[1]

                val newBands = (0 until numBands).map { i ->
                    val idx = i.toShort()
                    val centerFreq = getCenterFreq(idx) / 1000 // Convert mHz to Hz
                    // Try to load saved level or use current
                    val savedLevel = prefs.getInt("band_$idx", getBandLevel(idx).toInt()).toShort()
                    setBandLevel(idx, savedLevel)
                    
                    EqBand(
                        index = idx,
                        centerFreqHz = centerFreq,
                        minLevel = minLvl,
                        maxLevel = maxLvl,
                        currentLevel = savedLevel
                    )
                }
                _bands.value = newBands

                // Load presets
                val numPresets = numberOfPresets
                val newPresets = (0 until numPresets).map { i ->
                    EqPreset(i.toShort(), getPresetName(i.toShort()))
                }
                
                // Add a "Custom" preset logic
                val allPresets = mutableListOf<EqPreset>()
                allPresets.add(EqPreset(-1, "Personalizado"))
                allPresets.addAll(newPresets)
                _presets.value = allPresets

                val currentPreset = _selectedPreset.value
                if (currentPreset.toInt() != -1 && currentPreset < numPresets) {
                    usePreset(currentPreset)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        prefs.edit().putBoolean("is_enabled", enabled).apply()
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        equalizer?.setBandLevel(bandIndex, level)
        val updatedBands = _bands.value.map {
            if (it.index == bandIndex) it.copy(currentLevel = level) else it
        }
        _bands.value = updatedBands
        prefs.edit().putInt("band_$bandIndex", level.toInt()).apply()
        
        // Switch to custom preset if user modifies band
        _selectedPreset.value = (-1).toShort()
        prefs.edit().putInt("selected_preset", -1).apply()
    }

    fun usePreset(presetIndex: Short) {
        if (presetIndex.toInt() == -1) return
        equalizer?.usePreset(presetIndex)
        _selectedPreset.value = presetIndex
        prefs.edit().putInt("selected_preset", presetIndex.toInt()).apply()

        // Update bands state to reflect preset levels
        equalizer?.let { eq ->
            val updatedBands = _bands.value.map { band ->
                val level = eq.getBandLevel(band.index)
                prefs.edit().putInt("band_${band.index}", level.toInt()).apply()
                band.copy(currentLevel = level)
            }
            _bands.value = updatedBands
        }
    }
}
