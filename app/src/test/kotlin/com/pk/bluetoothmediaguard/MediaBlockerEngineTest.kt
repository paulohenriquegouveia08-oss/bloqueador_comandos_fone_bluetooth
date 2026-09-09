package com.pk.bluetoothmediaguard

import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.MediaBlockerEngine
import com.pk.bluetoothmediaguard.domain.MediaCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBlockerEngineTest {

    private val engine = MediaBlockerEngine()
    private val ligado = GuardSettings(enabled = true)

    @Test
    fun `interruptor geral desligado nao bloqueia nada`() {
        // Regra 1 da especificacao. E o pior defeito possivel aqui: o app
        // continuar comendo comandos depois de a pessoa desligar.
        val tudoMarcadoMasDesligado = GuardSettings(
            enabled = false,
            blockPlay = true,
            blockPause = true,
            blockPlayPause = true,
            blockNext = true,
        )
        MediaCommand.values().forEach { comando ->
            assertFalse(
                "$comando nao pode ser bloqueado com o app desligado",
                engine.shouldBlock(comando, tudoMarcadoMasDesligado),
            )
        }
    }

    @Test
    fun `play e bloqueado apenas quando marcado`() {
        assertTrue(engine.shouldBlock(MediaCommand.PLAY, ligado.copy(blockPlay = true)))
        assertFalse(engine.shouldBlock(MediaCommand.PLAY, ligado.copy(blockPlay = false)))
    }

    @Test
    fun `pause e bloqueado apenas quando marcado`() {
        assertTrue(engine.shouldBlock(MediaCommand.PAUSE, ligado.copy(blockPause = true)))
        assertFalse(engine.shouldBlock(MediaCommand.PAUSE, ligado.copy(blockPause = false)))
    }

    @Test
    fun `play pause e categoria independente de play e de pause`() {
        // A maioria dos fones so manda PLAY_PAUSE. Se bloquear PLAY
        // tambem pegasse PLAY_PAUSE, marcar uma caixa afetaria a outra
        // sem o usuario entender por que.
        val soPlayPause = ligado.copy(blockPlayPause = true, blockPlay = false, blockPause = false)
        assertTrue(engine.shouldBlock(MediaCommand.PLAY_PAUSE, soPlayPause))
        assertFalse(engine.shouldBlock(MediaCommand.PLAY, soPlayPause))
        assertFalse(engine.shouldBlock(MediaCommand.PAUSE, soPlayPause))

        val soPlay = ligado.copy(blockPlay = true, blockPlayPause = false)
        assertFalse(engine.shouldBlock(MediaCommand.PLAY_PAUSE, soPlay))
    }

    @Test
    fun `comandos nao marcados continuam funcionando`() {
        // Regra 5: bloquear play nao pode quebrar o resto do fone.
        val perfilPadrao = GuardSettings.perfilInicial(GuardSettings())
        assertFalse(engine.shouldBlock(MediaCommand.NEXT, perfilPadrao))
        assertFalse(engine.shouldBlock(MediaCommand.PREVIOUS, perfilPadrao))
        assertFalse(engine.shouldBlock(MediaCommand.VOLUME_UP, perfilPadrao))
        assertFalse(engine.shouldBlock(MediaCommand.VOLUME_DOWN, perfilPadrao))
    }

    @Test
    fun `perfil inicial bloqueia so os tres comandos de reproducao`() {
        val p = GuardSettings.perfilInicial(GuardSettings())
        assertTrue(p.enabled)
        assertTrue(p.blockPlay && p.blockPause && p.blockPlayPause)
        assertFalse(p.blockNext || p.blockPrevious || p.blockStop || p.blockVolume)
    }

    @Test
    fun `volume nunca e bloqueado por padrao`() {
        // Quem instala por causa de pausa acidental nao espera perder o
        // controle de volume junto.
        val p = GuardSettings.perfilInicial(GuardSettings())
        assertFalse(engine.shouldBlock(MediaCommand.VOLUME_UP, p))
        assertFalse(engine.shouldBlock(MediaCommand.VOLUME_MUTE, p))
    }

    @Test
    fun `comando desconhecido nunca e bloqueado`() {
        // Bloquear o que nao soubemos identificar transformaria qualquer
        // botao novo de fone num defeito silencioso.
        val tudoLigado = GuardSettings(
            enabled = true, blockPlay = true, blockPause = true, blockPlayPause = true,
            blockNext = true, blockPrevious = true, blockStop = true, blockVolume = true,
        )
        assertFalse(engine.shouldBlock(MediaCommand.UNKNOWN, tudoLigado))
    }

    @Test
    fun `avancar e voltar acompanham proxima e anterior`() {
        assertTrue(engine.shouldBlock(MediaCommand.FAST_FORWARD, ligado.copy(blockNext = true)))
        assertTrue(engine.shouldBlock(MediaCommand.REWIND, ligado.copy(blockPrevious = true)))
        assertFalse(engine.shouldBlock(MediaCommand.FAST_FORWARD, ligado))
    }
}
