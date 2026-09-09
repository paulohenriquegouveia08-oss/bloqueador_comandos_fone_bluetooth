package com.pk.bluetoothmediaguard.domain

/**
 * Junta repetições do MESMO pressionamento num evento só.
 *
 * Dois motivos, e são diferentes (§22 e §23):
 *
 * 1. Um toque gera `ACTION_DOWN` e `ACTION_UP`. São duas entregas do
 *    Android para um único gesto — contar as duas mostraria "PLAY_PAUSE,
 *    PLAY_PAUSE" no histórico para quem tocou uma vez, e faria a pessoa
 *    achar que o fone está pior do que está.
 *
 * 2. Botão mantido pressionado repete o evento (`repeatCount` sobe).
 *
 * O filtro é por COMANDO, não global: bloquear um PLAY_PAUSE não pode
 * engolir o NEXT que veio logo depois — são gestos diferentes da pessoa,
 * e ela esperaria que o segundo funcionasse.
 */
class DebounceManager(
    private val relogio: () -> Long = System::currentTimeMillis,
) {
    private val ultimoPorComando = HashMap<MediaCommand, Long>()

    /**
     * `true` = é um evento novo, trate. `false` = é eco do anterior.
     */
    fun aceitar(command: MediaCommand, janelaMs: Long): Boolean {
        val agora = relogio()
        val anterior = ultimoPorComando[command]

        if (anterior != null && agora - anterior < janelaMs) {
            // NÃO atualiza o carimbo: senão um botão preso, repetindo
            // mais rápido que a janela, empurraria o limite para sempre e
            // o comando nunca mais passaria.
            return false
        }

        ultimoPorComando[command] = agora
        return true
    }

    /** Esquece o que viu. Usado ao ligar/desligar o bloqueio. */
    fun limpar() = ultimoPorComando.clear()
}
