package com.pk.bluetoothmediaguard

import android.view.KeyEvent
import com.pk.bluetoothmediaguard.domain.MediaCommand
import com.pk.bluetoothmediaguard.media.MediaCommandMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `KeyEvent.KEYCODE_*` sao constantes inteiras, disponiveis na JVM sem
 * aparelho — por isso este teste roda como teste unitario comum.
 */
class MediaCommandMapperTest {

    @Test
    fun `play pause e headsethook caem na mesma categoria`() {
        // HEADSETHOOK e o que fones antigos e alguns adaptadores mandam
        // para o mesmo gesto. Tratar como categoria diferente faria a
        // configuracao do usuario falhar so nesses aparelhos.
        assertEquals(MediaCommand.PLAY_PAUSE, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(MediaCommand.PLAY_PAUSE, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_HEADSETHOOK))
    }

    @Test
    fun `play e pause continuam separados de play pause`() {
        assertEquals(MediaCommand.PLAY, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(MediaCommand.PAUSE, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    @Test
    fun `variacoes de pular caem em proxima e anterior`() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        ).forEach { assertEquals(MediaCommand.NEXT, MediaCommandMapper.deKeyCode(it)) }

        listOf(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        ).forEach { assertEquals(MediaCommand.PREVIOUS, MediaCommandMapper.deKeyCode(it)) }
    }

    @Test
    fun `volume e reconhecido mas nao e comando de midia`() {
        assertEquals(MediaCommand.VOLUME_UP, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(MediaCommandMapper.ehDeMidia(MediaCommand.VOLUME_UP))
        assertTrue(MediaCommandMapper.ehDeMidia(MediaCommand.PLAY_PAUSE))
    }

    @Test
    fun `tecla sem relacao com midia vira desconhecido`() {
        assertEquals(MediaCommand.UNKNOWN, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_A))
        assertEquals(MediaCommand.UNKNOWN, MediaCommandMapper.deKeyCode(KeyEvent.KEYCODE_HOME))
    }

    @Test
    fun `nomeia a acao para o diagnostico`() {
        assertEquals("ACTION_DOWN", MediaCommandMapper.nomeDaAcao(KeyEvent.ACTION_DOWN))
        assertEquals("ACTION_UP", MediaCommandMapper.nomeDaAcao(KeyEvent.ACTION_UP))
    }
}
