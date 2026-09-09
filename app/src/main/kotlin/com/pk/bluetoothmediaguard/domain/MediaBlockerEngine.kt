package com.pk.bluetoothmediaguard.domain

/**
 * Decide se um comando deve ser barrado.
 *
 * Função pura, sem Android dentro, porque é a regra de negócio inteira do
 * produto — e é o que dá para testar sem aparelho, sem fone e sem
 * emulador. Tudo que sabe sobre `KeyEvent` fica no mapper; tudo que sabe
 * sobre sessão fica no serviço.
 */
class MediaBlockerEngine {

    /**
     * Regra 1 (§34): o interruptor geral vence.
     *
     * Fica antes de qualquer outra checagem de propósito: com `enabled`
     * falso, nenhuma configuração individual pode bloquear nada. É o que
     * a pessoa espera ao desligar o app — e o que evita o pior defeito
     * possível aqui, que é o app continuar comendo comandos depois de
     * "desligado".
     */
    fun shouldBlock(command: MediaCommand, settings: GuardSettings): Boolean {
        if (!settings.enabled) return false

        return when (command) {
            MediaCommand.PLAY -> settings.blockPlay
            MediaCommand.PAUSE -> settings.blockPause
            MediaCommand.PLAY_PAUSE -> settings.blockPlayPause
            MediaCommand.NEXT -> settings.blockNext
            MediaCommand.PREVIOUS -> settings.blockPrevious
            MediaCommand.STOP -> settings.blockStop

            MediaCommand.VOLUME_UP,
            MediaCommand.VOLUME_DOWN,
            MediaCommand.VOLUME_MUTE -> settings.blockVolume

            // Avanço e retrocesso rápidos acompanham NEXT/PREVIOUS: são o
            // mesmo gesto de "pular", e uma configuração separada para
            // cada seria uma tela cheia de caixas que ninguém marca.
            MediaCommand.FAST_FORWARD -> settings.blockNext
            MediaCommand.REWIND -> settings.blockPrevious

            // Comando que não soubemos identificar NUNCA é bloqueado.
            // Bloquear o desconhecido transformaria qualquer botão novo
            // de fone num defeito silencioso.
            MediaCommand.UNKNOWN -> false
        }
    }

    /**
     * O que o comando é, para quem lê o histórico.
     *
     * Serve à tela: dizer "PLAY_PAUSE — Bloqueado" só ajuda se o nome
     * for o que a pessoa reconhece do fone dela.
     */
    fun descrever(command: MediaCommand): String = when (command) {
        MediaCommand.PLAY -> "Play"
        MediaCommand.PAUSE -> "Pause"
        MediaCommand.PLAY_PAUSE -> "Play/Pause"
        MediaCommand.NEXT -> "Próxima"
        MediaCommand.PREVIOUS -> "Anterior"
        MediaCommand.STOP -> "Parar"
        MediaCommand.FAST_FORWARD -> "Avançar"
        MediaCommand.REWIND -> "Voltar"
        MediaCommand.VOLUME_UP -> "Volume +"
        MediaCommand.VOLUME_DOWN -> "Volume −"
        MediaCommand.VOLUME_MUTE -> "Mudo"
        MediaCommand.UNKNOWN -> "Desconhecido"
    }
}
