package com.pk.bluetoothmediaguard.domain

/**
 * O comando, já normalizado.
 *
 * Existe para o resto do sistema não raciocinar em `KeyEvent.KEYCODE_*`:
 * o mesmo botão do fone chega como PLAY, como PAUSE ou como PLAY_PAUSE
 * dependendo do aparelho, e a regra do usuário é sobre a INTENÇÃO, não
 * sobre o código numérico.
 */
enum class MediaCommand {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    STOP,
    FAST_FORWARD,
    REWIND,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    UNKNOWN,
}

/**
 * O que aconteceu com o comando. Quatro estados, nunca confundidos.
 *
 * A distinção é o ponto do produto. Um app que diz "bloqueado" quando
 * apenas viu o comando passar está mentindo para quem instalou
 * justamente porque a música pausa sozinha (§29, §47).
 */
enum class ResultadoDoComando {
    /** Vimos o comando acontecer. Nada mais que isso. */
    DETECTADO,

    /** Não chegou ao player: nossa sessão o consumiu. Bloqueio de verdade. */
    BLOQUEADO,

    /**
     * Chegou, o player obedeceu, e desfizemos em seguida.
     *
     * NÃO é bloqueio. A música chega a parar por alguns décimos de
     * segundo. É o melhor resultado possível quando outro app é o dono
     * da sessão ativa — ver docs/pesquisa-tecnica.md.
     */
    REVERTIDO,

    /** Vimos o efeito e não conseguimos nem bloquear nem reverter. */
    NAO_INTERCEPTAVEL,

    /** O usuário não pediu bloqueio deste comando; seguiu o caminho normal. */
    PERMITIDO,
}

/** De onde a observação veio. Importa no diagnóstico. */
enum class OrigemDoEvento {
    /** Nossa MediaSession recebeu o botão — só acontece quando ela é a sessão escolhida. */
    NOSSA_SESSAO,

    /** Observamos a sessão de outro app mudar de estado (MediaController). */
    SESSAO_DE_TERCEIRO,

    /** Serviço de acessibilidade. Não recebe mídia de Bluetooth; serve a teclas físicas. */
    ACESSIBILIDADE,
}

/**
 * Um evento observado, já normalizado e datado.
 *
 * `keyCode` e `action` ficam anuláveis de propósito: quando a observação
 * vem de mudança de estado de outra sessão, não existe KeyEvent nenhum —
 * inventar um número ali seria fabricar diagnóstico.
 */
data class MediaEvent(
    val command: MediaCommand,
    val origem: OrigemDoEvento,
    val quandoMs: Long,
    val keyCode: Int? = null,
    val action: Int? = null,
    val repeatCount: Int = 0,
    val deviceId: Int? = null,
    val source: Int? = null,
    val flags: Int? = null,
    val dispositivo: String? = null,
)

/** O evento com o que o sistema decidiu sobre ele. */
data class RegistroDeEvento(
    val evento: MediaEvent,
    val resultado: ResultadoDoComando,
    val motivo: String? = null,
)
