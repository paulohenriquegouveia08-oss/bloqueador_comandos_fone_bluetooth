package com.pk.bluetoothmediaguard.domain

/**
 * O que o usuário escolheu.
 *
 * `enabled` desligado tem precedência sobre tudo (§34, Regra 1): é o
 * interruptor geral, e uma regra individual não pode ressuscitá-lo.
 *
 * O padrão de instalação é TUDO DESLIGADO (§12). Um app que começa
 * bloqueando comandos de mídia sem alguém pedir é indistinguível de um
 * defeito.
 */
data class GuardSettings(
    val enabled: Boolean = false,
    val blockPlay: Boolean = false,
    val blockPause: Boolean = false,
    val blockPlayPause: Boolean = false,
    val blockNext: Boolean = false,
    val blockPrevious: Boolean = false,
    val blockStop: Boolean = false,
    /**
     * Volume nunca entra no padrão (§6). Quem instala por causa de pausa
     * acidental não espera perder o controle de volume junto.
     */
    val blockVolume: Boolean = false,
    val autoStart: Boolean = false,
    val diagnosticMode: Boolean = false,
    /**
     * Desfazer a pausa quando não deu para bloquear.
     *
     * Separado do bloqueio porque é outra coisa: a música chega a parar.
     * Quem prefere um corte a nenhuma proteção liga; quem não tolera o
     * corte deixa desligado.
     */
    val reverterQuandoNaoBloquear: Boolean = true,
    /**
     * Tentar ser o destinatário dos botões, em vez de só observar.
     *
     * É a única forma de BLOQUEAR de verdade com outro app tocando: o
     * sistema entrega o botão a quem tocou áudio por último, então o app
     * toca silêncio para entrar nessa fila.
     *
     * Desligado por padrão porque tem custo real — mantém o caminho de
     * áudio acordado e gasta bateria — e porque pode não funcionar neste
     * aparelho. Quem liga precisa saber os dois.
     */
    val modoCaptura: Boolean = false,
    val debounceMs: Long = PADRAO_DEBOUNCE_MS,
) {
    companion object {
        /**
         * 250 ms.
         *
         * Escolhido dentro da faixa que a especificação manda testar
         * (100–300 ms) e não no meio por acaso: abaixo de ~200 ms um
         * duplo-toque lento de fone ainda entra como dois eventos, e
         * acima de ~300 ms dois toques deliberados viram um. Ajustável
         * na tela de diagnóstico, porque o número certo depende do fone
         * e não pode ser decidido sem o aparelho na mão.
         */
        const val PADRAO_DEBOUNCE_MS = 250L
        const val MIN_DEBOUNCE_MS = 50L
        const val MAX_DEBOUNCE_MS = 1000L

        /** Perfil aplicado quando o usuário liga o bloqueio pela primeira vez (§12). */
        fun perfilInicial(atual: GuardSettings) = atual.copy(
            enabled = true,
            blockPlay = true,
            blockPause = true,
            blockPlayPause = true,
        )
    }
}

/**
 * Quando vale a pena pagar o custo do modo captura.
 *
 * Separado do serviço, e puro, porque é a função que decide o consumo de
 * bateria do app inteiro — e é a única parte disso que dá para provar sem
 * um aparelho na mão.
 */
object DecisaoDeCaptura {

    /**
     * As três condições são obrigatórias juntas.
     *
     * Sem fone conectado não há botão para apertar: manter o silêncio
     * tocando seria gastar bateria o dia inteiro protegendo de nada.
     */
    fun deveCapturar(settings: GuardSettings, foneConectado: Boolean): Boolean =
        settings.enabled && settings.modoCaptura && foneConectado

    /** O que a notificação deve dizer, para não mentir sobre o estado. */
    fun descreverEstado(settings: GuardSettings, foneConectado: Boolean): String = when {
        !settings.enabled -> "Desativado"
        !settings.modoCaptura -> "Ativo — desfazendo a pausa"
        !foneConectado -> "Ativo — aguardando um fone"
        else -> "Ativo — bloqueando no fone"
    }
}
