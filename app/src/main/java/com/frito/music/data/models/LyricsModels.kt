package com.frito.music.data.models

/**
 * Representa una línea individual de letra con su marca de tiempo en milisegundos.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Representa la letra completa de una canción, ya sea sincronizada (LRC) o texto plano.
 */
data class LyricsData(
    val title: String,
    val artist: String,
    val lines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val isSynced: Boolean = lines.isNotEmpty(),
    val source: String = "LRCLIB"
)

/**
 * Estados de la interfaz de usuario para la pantalla de letras.
 */
sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Success(val lyrics: LyricsData) : LyricsUiState
    data object Empty : LyricsUiState
}
