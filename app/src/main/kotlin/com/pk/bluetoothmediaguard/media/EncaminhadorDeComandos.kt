package com.pk.bluetoothmediaguard.media

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import com.pk.bluetoothmediaguard.domain.MediaCommand

/**
 * Repassa ao player de verdade o que o usuário NÃO mandou bloquear.
 *
 * Só existe por causa da captura. Quando o nosso app vira o destinatário
 * dos botões, ele passa a receber TODOS eles — inclusive "próxima", que
 * a pessoa quer funcionando. Sem repassar, proteger contra a pausa
 * acidental quebraria o resto do fone, e o remédio seria pior.
 *
 * Repassa para a sessão que está tocando, e não para "a primeira da
 * lista": com Spotify e YouTube abertos ao mesmo tempo, mandar para a
 * errada faria o vídeo pausado avançar de faixa sozinho.
 */
class EncaminhadorDeComandos {

    fun encaminhar(comando: MediaCommand, sessoes: List<MediaController>): Boolean {
        val alvo = escolherAlvo(sessoes) ?: return false

        return try {
            val controles = alvo.transportControls
            when (comando) {
                MediaCommand.PLAY -> controles.play()
                MediaCommand.PAUSE -> controles.pause()
                MediaCommand.PLAY_PAUSE -> alternar(alvo)
                MediaCommand.NEXT -> controles.skipToNext()
                MediaCommand.PREVIOUS -> controles.skipToPrevious()
                MediaCommand.STOP -> controles.stop()
                MediaCommand.FAST_FORWARD -> controles.fastForward()
                MediaCommand.REWIND -> controles.rewind()

                // Volume não passa por aqui: o Android já o trata em
                // outro caminho, e mexer nele daqui seria mudar o volume
                // do sistema por conta própria.
                MediaCommand.VOLUME_UP,
                MediaCommand.VOLUME_DOWN,
                MediaCommand.VOLUME_MUTE,
                MediaCommand.UNKNOWN -> return false
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "O player recusou o comando encaminhado", e)
            false
        }
    }

    /**
     * PLAY_PAUSE é um botão só: quem sabe o que ele significa agora é o
     * estado do player, não nós.
     */
    private fun alternar(alvo: MediaController) {
        val tocando = alvo.playbackState?.state == PlaybackState.STATE_PLAYING
        if (tocando) alvo.transportControls.pause() else alvo.transportControls.play()
    }

    /**
     * A sessão que está tocando; sem nenhuma tocando, a primeira pausada.
     *
     * Nossa própria sessão fica de fora — encaminhar para nós mesmos
     * seria um laço infinito.
     */
    fun escolherAlvo(sessoes: List<MediaController>): MediaController? {
        val candidatas = sessoes.filterNot { it.packageName == NOSSO_PACOTE }
        return candidatas.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidatas.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: candidatas.firstOrNull()
    }

    private companion object {
        const val TAG = "Encaminhador"
        const val NOSSO_PACOTE = "com.pk.bluetoothmediaguard"
    }
}
