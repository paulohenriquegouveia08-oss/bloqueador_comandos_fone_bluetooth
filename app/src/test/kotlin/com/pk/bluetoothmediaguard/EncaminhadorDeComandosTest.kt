package com.pk.bluetoothmediaguard

import android.media.session.MediaController
import android.media.session.PlaybackState
import com.pk.bluetoothmediaguard.media.EncaminhadorDeComandos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * A escolha do alvo e a parte que pode quebrar o fone da pessoa: mandar
 * "proxima" para a sessao errada faz um video pausado avancar sozinho.
 */
class EncaminhadorDeComandosTest {

    private val encaminhador = EncaminhadorDeComandos()

    private fun sessao(pacote: String, estado: Int): MediaController {
        val controller = mock(MediaController::class.java)
        val playback = mock(PlaybackState::class.java)
        `when`(playback.state).thenReturn(estado)
        `when`(controller.playbackState).thenReturn(playback)
        `when`(controller.packageName).thenReturn(pacote)
        return controller
    }

    @Test
    fun `prefere a sessao que esta tocando`() {
        // Com Spotify tocando e YouTube pausado, o botao e para o Spotify.
        val pausada = sessao("com.google.android.youtube", PlaybackState.STATE_PAUSED)
        val tocando = sessao("com.spotify.music", PlaybackState.STATE_PLAYING)

        val alvo = encaminhador.escolherAlvo(listOf(pausada, tocando))
        assertEquals("com.spotify.music", alvo?.packageName)
    }

    @Test
    fun `sem nenhuma tocando, usa a pausada`() {
        val pausada = sessao("com.spotify.music", PlaybackState.STATE_PAUSED)
        assertEquals("com.spotify.music", encaminhador.escolherAlvo(listOf(pausada))?.packageName)
    }

    @Test
    fun `nunca escolhe a nossa propria sessao`() {
        // Encaminhar para nos mesmos seria um laco infinito: o comando
        // voltaria ao callback que o encaminhou.
        val nossa = sessao("com.pk.bluetoothmediaguard", PlaybackState.STATE_PLAYING)
        assertNull(encaminhador.escolherAlvo(listOf(nossa)))
    }

    @Test
    fun `com a nossa e a do player, escolhe a do player`() {
        val nossa = sessao("com.pk.bluetoothmediaguard", PlaybackState.STATE_PLAYING)
        val player = sessao("com.spotify.music", PlaybackState.STATE_PLAYING)
        assertEquals("com.spotify.music", encaminhador.escolherAlvo(listOf(nossa, player))?.packageName)
    }

    @Test
    fun `sem sessao nenhuma nao ha alvo`() {
        assertNull(encaminhador.escolherAlvo(emptyList()))
    }
}
