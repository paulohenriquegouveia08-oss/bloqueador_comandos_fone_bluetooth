package com.pk.bluetoothmediaguard

import com.pk.bluetoothmediaguard.domain.DebounceManager
import com.pk.bluetoothmediaguard.domain.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebounceManagerTest {

    private var agora = 1_000L
    private val debounce = DebounceManager { agora }
    private val janela = 250L

    @Test
    fun `primeiro evento sempre passa`() {
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
    }

    @Test
    fun `down e up do mesmo toque contam uma vez so`() {
        // Um toque gera ACTION_DOWN e ACTION_UP. Contar os dois mostraria
        // "PLAY_PAUSE, PLAY_PAUSE" para quem tocou uma vez.
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
        agora += 30
        assertFalse(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
    }

    @Test
    fun `dois toques deliberados passam os dois`() {
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
        agora += janela + 1
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
    }

    @Test
    fun `botao preso vira no maximo um evento por janela`() {
        // Um botao travado repete a cada ~50 ms. Sem filtro seriam 20
        // eventos por segundo no historico; o que importa e que sobre
        // aproximadamente um por janela, e nao que pare de vez — parar
        // de vez esconderia que o botao esta preso.
        var aceitos = 0
        repeat(21) {
            if (debounce.aceitar(MediaCommand.PLAY_PAUSE, janela)) aceitos++
            agora += 50
        }

        // 21 pressoes cobrindo 1000 ms, janela de 250 ms: 5 aceitos
        // (t=0, 250, 500, 750, 1000). O numero exato importa menos que a
        // ordem de grandeza — nao pode ser 21 nem 1.
        assertEquals(5, aceitos)
    }

    @Test
    fun `evento recusado nao adia o carimbo`() {
        // Se o carimbo fosse atualizado a cada recusa, um botao travado
        // empurraria a janela indefinidamente e o comando NUNCA mais
        // passaria — a protecao viraria um travamento.
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
        agora += 100
        assertFalse(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
        agora += 160 // 260 ms desde o ACEITO, 160 desde a recusa
        assertTrue(
            "a janela conta a partir do ultimo evento aceito, nao do ultimo recusado",
            debounce.aceitar(MediaCommand.PLAY_PAUSE, janela),
        )
    }

    @Test
    fun `comandos diferentes nao se atrapalham`() {
        // Bloquear um PLAY_PAUSE nao pode engolir o NEXT logo depois:
        // sao gestos diferentes da pessoa.
        assertTrue(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
        agora += 10
        assertTrue(debounce.aceitar(MediaCommand.NEXT, janela))
        agora += 10
        assertFalse(debounce.aceitar(MediaCommand.PLAY_PAUSE, janela))
    }

    @Test
    fun `limpar faz o proximo evento passar`() {
        assertTrue(debounce.aceitar(MediaCommand.PAUSE, janela))
        debounce.limpar()
        agora += 10
        assertTrue(debounce.aceitar(MediaCommand.PAUSE, janela))
    }
}
