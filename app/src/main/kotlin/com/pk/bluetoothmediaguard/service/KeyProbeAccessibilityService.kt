package com.pk.bluetoothmediaguard.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.pk.bluetoothmediaguard.GuardApplication
import com.pk.bluetoothmediaguard.domain.MediaEvent
import com.pk.bluetoothmediaguard.domain.OrigemDoEvento
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
import com.pk.bluetoothmediaguard.media.MediaCommandMapper

/**
 * SONDA DE TECLAS — não é o mecanismo de bloqueio.
 *
 * A especificação previa este serviço como a "camada de interceptação
 * global". A pesquisa mostrou que não pode ser: botão de mídia vindo de
 * fone Bluetooth não percorre o despacho de teclas do Android — o
 * sistema o encaminha direto ao framework de media session
 * (docs/pesquisa-tecnica.md).
 *
 * O serviço ficou porque é ele que PROVA isso no aparelho de quem usa:
 * se algum fabricante realmente entregar o evento por aqui, aparece no
 * diagnóstico e passa a ser bloqueado de verdade. Se não aparecer — o
 * esperado — o diagnóstico diz exatamente isso, em vez de o app fingir
 * que tentou.
 *
 * Teclas físicas do aparelho (volume, botão de fone com fio) chegam aqui
 * normalmente.
 */
class KeyProbeAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nada. Não lemos conteúdo de tela — só teclas. É o que permite
        // explicar a permissão com honestidade.
    }

    override fun onInterrupt() = Unit

    /**
     * Retorna `true` para consumir o evento.
     *
     * Consumimos SOMENTE o que o usuário mandou bloquear e que de fato
     * chegou aqui. Consumir mais que isso quebraria teclas do aparelho
     * que não têm nada a ver com o problema.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val app = GuardApplication.instancia
        val comando = MediaCommandMapper.deKeyEvent(event)
        if (comando == com.pk.bluetoothmediaguard.domain.MediaCommand.UNKNOWN) return false

        // Uma tecla gera DOWN e UP. Registrar os dois mostraria o dobro
        // de eventos para um único toque; a decisão sai no DOWN, e o UP
        // é consumido junto para o player não receber metade do gesto.
        val ehDown = event.action == KeyEvent.ACTION_DOWN

        val configuracao = ultimaConfiguracao ?: return false
        val bloquear = app.blockerEngine.shouldBlock(comando, configuracao)

        if (ehDown) {
            app.historyRepository.registrar(
                RegistroDeEvento(
                    MediaEvent(
                        command = comando,
                        origem = OrigemDoEvento.ACESSIBILIDADE,
                        quandoMs = System.currentTimeMillis(),
                        keyCode = event.keyCode,
                        action = event.action,
                        repeatCount = event.repeatCount,
                        deviceId = event.deviceId,
                        source = event.source,
                        flags = event.flags,
                    ),
                    if (bloquear) ResultadoDoComando.BLOQUEADO else ResultadoDoComando.PERMITIDO,
                    if (bloquear) "Consumido antes de chegar ao player." else null,
                ),
            )
        }

        return bloquear
    }

    companion object {
        /**
         * A configuração vigente, publicada pelo serviço principal.
         *
         * Um `AccessibilityService` é criado pelo sistema e não recebe
         * dependências; ler o DataStore a cada tecla seria I/O no caminho
         * de um evento que precisa de resposta imediata.
         */
        @Volatile
        var ultimaConfiguracao: com.pk.bluetoothmediaguard.domain.GuardSettings? = null
    }
}
