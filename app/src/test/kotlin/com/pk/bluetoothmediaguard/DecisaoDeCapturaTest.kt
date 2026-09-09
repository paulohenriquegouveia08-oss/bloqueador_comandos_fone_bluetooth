package com.pk.bluetoothmediaguard

import com.pk.bluetoothmediaguard.domain.DecisaoDeCaptura
import com.pk.bluetoothmediaguard.domain.GuardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Esta e a funcao que decide o consumo de bateria do app. Se ela errar
 * para o lado do "sim", o celular toca silencio o dia inteiro.
 */
class DecisaoDeCapturaTest {

    private val ligadoComCaptura = GuardSettings(enabled = true, modoCaptura = true)

    @Test
    fun `sem fone conectado nao gasta nada`() {
        // O caso mais importante: a maior parte do dia a pessoa nao esta
        // de fone, e nao ha botao nenhum para apertar.
        assertFalse(DecisaoDeCaptura.deveCapturar(ligadoComCaptura, foneConectado = false))
    }

    @Test
    fun `com fone e captura ligada, captura`() {
        assertTrue(DecisaoDeCaptura.deveCapturar(ligadoComCaptura, foneConectado = true))
    }

    @Test
    fun `protecao desligada nao captura nem com fone`() {
        val desligado = ligadoComCaptura.copy(enabled = false)
        assertFalse(DecisaoDeCaptura.deveCapturar(desligado, foneConectado = true))
    }

    @Test
    fun `modo captura desligado nao gasta`() {
        // O modo padrao (desfazer a pausa) nao toca audio nenhum: ele so
        // observa, e observar nao custa bateria.
        val semCaptura = ligadoComCaptura.copy(modoCaptura = false)
        assertFalse(DecisaoDeCaptura.deveCapturar(semCaptura, foneConectado = true))
    }

    @Test
    fun `a notificacao diz o estado real, e nao so ativo`() {
        // Dizer "ativo" enquanto espera um fone faria a pessoa achar que
        // esta gastando bateria quando nao esta — e desinstalar por isso.
        assertEquals("Desativado", DecisaoDeCaptura.descreverEstado(GuardSettings(), false))
        assertEquals(
            "Ativo — aguardando um fone",
            DecisaoDeCaptura.descreverEstado(ligadoComCaptura, false),
        )
        assertEquals(
            "Ativo — bloqueando no fone",
            DecisaoDeCaptura.descreverEstado(ligadoComCaptura, true),
        )
        assertEquals(
            "Ativo — desfazendo a pausa",
            DecisaoDeCaptura.descreverEstado(ligadoComCaptura.copy(modoCaptura = false), true),
        )
    }
}
