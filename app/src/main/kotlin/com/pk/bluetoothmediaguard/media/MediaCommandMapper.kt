package com.pk.bluetoothmediaguard.media

import android.view.KeyEvent
import com.pk.bluetoothmediaguard.domain.MediaCommand

/**
 * Converte o que o Android entrega no comando que o resto do app entende.
 *
 * É o único lugar do projeto que conhece `KEYCODE_*`. Espalhar essa
 * tradução faria cada camada ter a própria opinião sobre o que é um
 * "play", e elas divergiriam na primeira tecla nova.
 */
object MediaCommandMapper {

    fun deKeyCode(keyCode: Int): MediaCommand = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> MediaCommand.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaCommand.PAUSE

        // Categoria PRÓPRIA, e não "play ou pause" (§21).
        //
        // A maioria dos fones manda só este código: o botão é um só e o
        // Android não sabe se a intenção era tocar ou pausar. Traduzi-lo
        // para PLAY ou PAUSE faria a regra do usuário errar metade das
        // vezes.
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> MediaCommand.PLAY_PAUSE

        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> MediaCommand.NEXT

        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> MediaCommand.PREVIOUS

        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_CLOSE,
        KeyEvent.KEYCODE_MEDIA_EJECT -> MediaCommand.STOP

        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> MediaCommand.FAST_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> MediaCommand.REWIND

        KeyEvent.KEYCODE_VOLUME_UP -> MediaCommand.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> MediaCommand.VOLUME_DOWN
        KeyEvent.KEYCODE_VOLUME_MUTE -> MediaCommand.VOLUME_MUTE

        else -> MediaCommand.UNKNOWN
    }

    fun deKeyEvent(event: KeyEvent): MediaCommand = deKeyCode(event.keyCode)

    /** O nome da constante, para o diagnóstico mostrar o que o Android disse. */
    fun nomeDoKeyCode(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode)

    fun nomeDaAcao(action: Int): String = when (action) {
        KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
        KeyEvent.ACTION_UP -> "ACTION_UP"
        KeyEvent.ACTION_MULTIPLE -> "ACTION_MULTIPLE"
        else -> "AÇÃO_$action"
    }

    /** É um comando de mídia (e não volume ou outra tecla qualquer)? */
    fun ehDeMidia(command: MediaCommand): Boolean = when (command) {
        MediaCommand.VOLUME_UP,
        MediaCommand.VOLUME_DOWN,
        MediaCommand.VOLUME_MUTE,
        MediaCommand.UNKNOWN -> false
        else -> true
    }
}
