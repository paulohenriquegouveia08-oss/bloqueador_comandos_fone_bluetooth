package com.pk.bluetoothmediaguard.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Tenta fazer o Android entregar os botões do fone A NÓS, e não ao player.
 *
 * ## Por que isto existe
 *
 * Detectar o comando é fácil; barrar não, porque o sistema entrega o
 * botão direto ao app de música e nunca passa por nós. A única forma de
 * barrar de verdade é SER o destinatário.
 *
 * No Android 8+, o destinatário é "o último app com MediaSession que
 * tocou áudio localmente". Não basta ter sessão ativa: é preciso ter
 * tocado som. Este componente toca — silêncio absoluto, em repetição.
 *
 * ## O que ele NÃO faz, e é o mais importante
 *
 * **Não pede foco de áudio.** Pedir foco mandaria o Spotify pausar, que
 * é exatamente o problema que o app promete resolver. Foco é cooperativo:
 * quem não pede, não interrompe ninguém — os dois sons se misturam, e o
 * nosso é silêncio.
 *
 * ## Custo honesto
 *
 * Mantém o caminho de áudio do aparelho acordado, e isso gasta bateria.
 * Por isso é opcional e desligado por padrão: só faz sentido para quem
 * o fone atrapalha de verdade.
 *
 * ## E pode não funcionar
 *
 * A escolha do destinatário é heurística do sistema, varia por versão e
 * fabricante, e um player tocando no momento pode continuar ganhando.
 * O diagnóstico mostra se os botões passaram a chegar aqui — e se não
 * passaram, o app diz isso em vez de fingir.
 */
class CapturadorDeBotoes {

    private var trilha: AudioTrack? = null

    val ativo: Boolean get() = trilha != null

    fun iniciar(): Boolean {
        if (trilha != null) return true

        return try {
            // 8 kHz, mono, 16 bits: o mínimo que o Android aceita como
            // reprodução de mídia. Cada Hz e cada canal a mais seria
            // trabalho do DSP para produzir o mesmo silêncio.
            val taxa = 8_000
            val tamanho = AudioTrack.getMinBufferSize(
                taxa,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(BUFFER_MINIMO)

            val nova = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // MEDIA para o sistema contar como reprodução de
                        // mídia — é o que o alimenta a heurística de
                        // "quem tocou por último".
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(taxa)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(tamanho)
                // MODE_STATIC + laço no hardware: o buffer é escrito UMA
                // vez e o próprio caminho de áudio o repete. O modo
                // STREAM exigiria o app acordar para alimentar o buffer
                // várias vezes por segundo — que é o que faria o celular
                // esquentar.
                .setTransferMode(AudioTrack.MODE_STATIC)
                // Diz ao sistema que latência não importa aqui. Ele então
                // usa buffers maiores e acorda o DSP com menos
                // frequência, que é exatamente a troca que queremos: o
                // som é silêncio, ninguém percebe atraso.
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .build()

            // Zeros: silêncio digital. E o volume em 0 por cima, para o
            // caso de o buffer não ser exatamente silencioso em algum
            // aparelho — nada deste app pode produzir som audível.
            nova.write(ShortArray(tamanho / 2), 0, tamanho / 2)
            nova.setVolume(0f)
            nova.setLoopPoints(0, tamanho / 2, -1)
            nova.play()

            trilha = nova
            true
        } catch (e: Exception) {
            // Aparelho sem trilha disponível, política do fabricante,
            // memória. Falhar aqui NÃO derruba o app: ele volta a operar
            // só observando, e o diagnóstico mostra a diferença.
            Log.w(TAG, "Não consegui iniciar a captura", e)
            trilha = null
            false
        }
    }

    fun parar() {
        try {
            trilha?.stop()
            trilha?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Falha ao parar a captura", e)
        } finally {
            trilha = null
        }
    }

    private companion object {
        const val TAG = "CapturadorDeBotoes"
        // O buffer é escrito uma vez e repetido pelo hardware; grande
        // demais só ocuparia memória, pequeno demais faria o laço
        // reiniciar com mais frequência.

        /** Buffer pequeno: é silêncio em repetição, não conteúdo. */
        const val BUFFER_MINIMO = 4096
    }
}
