package com.pk.bluetoothmediaguard

import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
import com.pk.bluetoothmediaguard.domain.VerificadorDeBloqueio
import com.pk.bluetoothmediaguard.domain.VerificadorDeBloqueio.EstadoDoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O app dizia "Bloqueado" sem evidencia nenhuma. Estes testes existem
 * para essa mentira nao voltar.
 */
class VerificadorDeBloqueioTest {

    @Test
    fun `tocando antes e depois e bloqueio de verdade`() {
        assertEquals(
            ResultadoDoComando.BLOQUEADO,
            VerificadorDeBloqueio.avaliar(EstadoDoPlayer.TOCANDO, EstadoDoPlayer.TOCANDO),
        )
    }

    @Test
    fun `a musica parou, entao NAO bloqueamos`() {
        // O caso que o app relatava errado: a musica pausava e a tela
        // dizia "Bloqueado".
        assertEquals(
            ResultadoDoComando.NAO_INTERCEPTAVEL,
            VerificadorDeBloqueio.avaliar(EstadoDoPlayer.TOCANDO, EstadoDoPlayer.PAUSADO),
        )
    }

    @Test
    fun `sem saber o estado, nao afirma bloqueio`() {
        // Assumir sucesso quando nao se sabe e como o app comecou a
        // mentir. Na duvida, "detectado".
        assertEquals(
            ResultadoDoComando.DETECTADO,
            VerificadorDeBloqueio.avaliar(EstadoDoPlayer.DESCONHECIDO, EstadoDoPlayer.TOCANDO),
        )
        assertEquals(
            ResultadoDoComando.DETECTADO,
            VerificadorDeBloqueio.avaliar(EstadoDoPlayer.TOCANDO, EstadoDoPlayer.DESCONHECIDO),
        )
    }

    @Test
    fun `ja estava pausado, nao havia o que interromper`() {
        assertEquals(
            ResultadoDoComando.DETECTADO,
            VerificadorDeBloqueio.avaliar(EstadoDoPlayer.PAUSADO, EstadoDoPlayer.PAUSADO),
        )
    }

    @Test
    fun `a explicacao do fracasso diz o que houve, e nao um codigo`() {
        val texto = VerificadorDeBloqueio.explicar(ResultadoDoComando.NAO_INTERCEPTAVEL)
        assertEquals(true, texto.contains("chegou ao player"))
        assertEquals(true, texto.contains("música parou"))
    }
}
