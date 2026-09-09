package com.pk.bluetoothmediaguard.domain

/**
 * Confere se o bloqueio realmente aconteceu.
 *
 * ## Por que precisou existir
 *
 * O app dizia "Bloqueado" só porque a regra mandava bloquear — sem
 * nenhuma evidência de que o player não tinha recebido o comando. Quando
 * a captura não pegava, ele mentia: a música pausava e a tela dizia
 * "bloqueado". É exatamente o defeito que a especificação proíbe (§47), e
 * o pior possível num app cujo valor inteiro é dizer a verdade sobre o
 * que conseguiu fazer.
 *
 * A evidência é simples e observável: **o player pausou depois?** Se
 * pausou, não bloqueamos nada — o comando chegou lá.
 */
object VerificadorDeBloqueio {

    /**
     * Quanto esperar antes de conferir.
     *
     * Tempo suficiente para o player reagir ao comando e publicar o novo
     * estado, e curto o bastante para a reversão ainda soar como um
     * tropeço, e não como uma segunda pausa.
     */
    const val ESPERA_MS = 400L

    /** O estado do player, do ponto de vista do verificador. */
    enum class EstadoDoPlayer { TOCANDO, PAUSADO, DESCONHECIDO }

    /**
     * O veredito, comparando antes e depois.
     *
     * `DESCONHECIDO` em qualquer um dos lados vira "não deu para
     * conferir" — e isso é dito, em vez de assumido como sucesso.
     * Assumir sucesso quando não se sabe é como o app começou a mentir.
     */
    fun avaliar(antes: EstadoDoPlayer, depois: EstadoDoPlayer): ResultadoDoComando = when {
        antes == EstadoDoPlayer.DESCONHECIDO || depois == EstadoDoPlayer.DESCONHECIDO ->
            ResultadoDoComando.DETECTADO

        // Estava tocando e continuou: o comando morreu conosco.
        antes == EstadoDoPlayer.TOCANDO && depois == EstadoDoPlayer.TOCANDO ->
            ResultadoDoComando.BLOQUEADO

        // Estava tocando e parou: chegou ao player. Não bloqueamos.
        antes == EstadoDoPlayer.TOCANDO && depois == EstadoDoPlayer.PAUSADO ->
            ResultadoDoComando.NAO_INTERCEPTAVEL

        // Já estava pausado: não havia o que interromper.
        else -> ResultadoDoComando.DETECTADO
    }

    fun explicar(resultado: ResultadoDoComando): String = when (resultado) {
        ResultadoDoComando.BLOQUEADO ->
            "Conferido: a música continuou tocando depois do comando."
        ResultadoDoComando.NAO_INTERCEPTAVEL ->
            "O comando chegou ao player mesmo assim — a música parou. Este aparelho não " +
                "entregou o botão para nós."
        else ->
            "Não deu para conferir o que aconteceu com o player."
    }
}
