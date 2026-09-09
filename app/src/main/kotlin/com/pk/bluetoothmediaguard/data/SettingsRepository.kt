package com.pk.bluetoothmediaguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pk.bluetoothmediaguard.domain.GuardSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guard_settings")

/**
 * As configurações, em DataStore (§19).
 *
 * Sem banco: são dez interruptores e um número. Uma tabela para isso
 * traria migração, thread de escrita e um arquivo para corromper, em
 * troca de nada.
 */
class SettingsRepository(private val context: Context) {

    private object Chaves {
        val ENABLED = booleanPreferencesKey("enabled")
        val BLOCK_PLAY = booleanPreferencesKey("block_play")
        val BLOCK_PAUSE = booleanPreferencesKey("block_pause")
        val BLOCK_PLAY_PAUSE = booleanPreferencesKey("block_play_pause")
        val BLOCK_NEXT = booleanPreferencesKey("block_next")
        val BLOCK_PREVIOUS = booleanPreferencesKey("block_previous")
        val BLOCK_STOP = booleanPreferencesKey("block_stop")
        val BLOCK_VOLUME = booleanPreferencesKey("block_volume")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val DIAGNOSTIC = booleanPreferencesKey("diagnostic_mode")
        val REVERTER = booleanPreferencesKey("reverter")
        val DEBOUNCE = longPreferencesKey("debounce_ms")
    }

    val settings: Flow<GuardSettings> = context.dataStore.data.map { p ->
        val padrao = GuardSettings()
        GuardSettings(
            enabled = p[Chaves.ENABLED] ?: padrao.enabled,
            blockPlay = p[Chaves.BLOCK_PLAY] ?: padrao.blockPlay,
            blockPause = p[Chaves.BLOCK_PAUSE] ?: padrao.blockPause,
            blockPlayPause = p[Chaves.BLOCK_PLAY_PAUSE] ?: padrao.blockPlayPause,
            blockNext = p[Chaves.BLOCK_NEXT] ?: padrao.blockNext,
            blockPrevious = p[Chaves.BLOCK_PREVIOUS] ?: padrao.blockPrevious,
            blockStop = p[Chaves.BLOCK_STOP] ?: padrao.blockStop,
            blockVolume = p[Chaves.BLOCK_VOLUME] ?: padrao.blockVolume,
            autoStart = p[Chaves.AUTO_START] ?: padrao.autoStart,
            diagnosticMode = p[Chaves.DIAGNOSTIC] ?: padrao.diagnosticMode,
            reverterQuandoNaoBloquear = p[Chaves.REVERTER] ?: padrao.reverterQuandoNaoBloquear,
            // Preso à faixa mesmo vindo do disco: um valor absurdo
            // gravado por versão antiga não pode inutilizar o debounce.
            debounceMs = (p[Chaves.DEBOUNCE] ?: padrao.debounceMs)
                .coerceIn(GuardSettings.MIN_DEBOUNCE_MS, GuardSettings.MAX_DEBOUNCE_MS),
        )
    }

    suspend fun salvar(s: GuardSettings) {
        context.dataStore.edit { p ->
            p[Chaves.ENABLED] = s.enabled
            p[Chaves.BLOCK_PLAY] = s.blockPlay
            p[Chaves.BLOCK_PAUSE] = s.blockPause
            p[Chaves.BLOCK_PLAY_PAUSE] = s.blockPlayPause
            p[Chaves.BLOCK_NEXT] = s.blockNext
            p[Chaves.BLOCK_PREVIOUS] = s.blockPrevious
            p[Chaves.BLOCK_STOP] = s.blockStop
            p[Chaves.BLOCK_VOLUME] = s.blockVolume
            p[Chaves.AUTO_START] = s.autoStart
            p[Chaves.DIAGNOSTIC] = s.diagnosticMode
            p[Chaves.REVERTER] = s.reverterQuandoNaoBloquear
            p[Chaves.DEBOUNCE] =
                s.debounceMs.coerceIn(GuardSettings.MIN_DEBOUNCE_MS, GuardSettings.MAX_DEBOUNCE_MS)
        }
    }
}
