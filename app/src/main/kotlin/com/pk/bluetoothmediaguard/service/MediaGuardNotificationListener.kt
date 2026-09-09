package com.pk.bluetoothmediaguard.service

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.pk.bluetoothmediaguard.GuardApplication
import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.MediaCommand
import com.pk.bluetoothmediaguard.domain.MediaEvent
import com.pk.bluetoothmediaguard.domain.OrigemDoEvento
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
import com.pk.bluetoothmediaguard.domain.DebounceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers

/**
 * Onde o app realmente age.
 *
 * A pesquisa (docs/pesquisa-tecnica.md) mostrou que o botão do fone é
 * entregue pelo sistema DIRETO à sessão do app de música — não passa
 * pelo despacho de teclas, e não há como interceptá-lo antes. O que
 * sobra, com API oficial, é observar aquela sessão e desfazer.
 *
 * Este serviço não lê notificação nenhuma. Ele existe porque
 * `MediaSessionManager.getActiveSessions` exige o componente de um
 * NotificationListenerService habilitado — é o caminho documentado para
 * um app comum obter os controllers das sessões alheias.
 *
 * O que ele faz NÃO é bloqueio, e o app nunca chama de bloqueio: a
 * música chega a pausar por alguns décimos de segundo. Registrado como
 * REVERTIDO.
 */
class MediaGuardNotificationListener : NotificationListenerService() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val principal = Handler(Looper.getMainLooper())
    private val debounce = DebounceManager()

    private var gerenciador: MediaSessionManager? = null
    private var configuracao = GuardSettings()
    private var jobConfig: Job? = null

    /** Um callback por sessão observada, para conseguir soltar depois. */
    private val observados = HashMap<MediaController, MediaController.Callback>()

    private val aoMudarSessoes =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            reobservar(controllers ?: emptyList())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()

        jobConfig = GuardApplication.instancia.settingsRepository.settings
            .onEach { configuracao = it }
            .launchIn(escopo)

        val componente = ComponentName(this, MediaGuardNotificationListener::class.java)
        gerenciador = (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager).also { m ->
            try {
                m.addOnActiveSessionsChangedListener(aoMudarSessoes, componente)
                reobservar(m.getActiveSessions(componente))
            } catch (e: SecurityException) {
                // Acontece quando a permissão foi revogada entre a
                // conexão e esta chamada. Não é motivo para derrubar o
                // serviço: a tela de diagnóstico já mostra a permissão
                // faltando.
                Log.w(TAG, "Sem permissão para listar sessões", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        soltarTudo()
        gerenciador?.removeOnActiveSessionsChangedListener(aoMudarSessoes)
        jobConfig?.cancel()
        escopo.cancel()
        super.onListenerDisconnected()
    }

    private fun reobservar(controllers: List<MediaController>) {
        // Solta os que sumiram. Sem isso, cada troca de música deixaria
        // um callback pendurado num controller morto — vazamento lento e
        // difícil de achar.
        val atuais = controllers.toSet()
        observados.keys.filterNot { it in atuais }.forEach { antigo ->
            observados.remove(antigo)?.let { antigo.unregisterCallback(it) }
        }

        controllers.forEach { controller ->
            if (observados.containsKey(controller)) return@forEach
            val callback = criarCallback(controller)
            controller.registerCallback(callback, principal)
            observados[controller] = callback
        }
    }

    private fun soltarTudo() {
        observados.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        observados.clear()
    }

    private fun criarCallback(controller: MediaController) = object : MediaController.Callback() {

        /** O último estado visto, para saber o que MUDOU. */
        private var estadoAnterior: Int = PlaybackState.STATE_NONE

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val novo = state?.state ?: return
            val anterior = estadoAnterior
            estadoAnterior = novo

            // Só interessa a transição TOCANDO → PAUSADO. É a que a
            // pessoa reclama. Qualquer outra é uso normal.
            val virouPausa = anterior == PlaybackState.STATE_PLAYING &&
                (novo == PlaybackState.STATE_PAUSED || novo == PlaybackState.STATE_STOPPED)
            if (!virouPausa) return

            val comando = if (novo == PlaybackState.STATE_STOPPED) {
                MediaCommand.STOP
            } else {
                // Não temos o KeyEvent: o sistema o entregou ao player,
                // não a nós. Só vemos o EFEITO. PLAY_PAUSE é o que
                // corresponde ao botão único da maioria dos fones, e é
                // sob essa categoria que o usuário configura.
                MediaCommand.PLAY_PAUSE
            }

            tratar(comando, controller)
        }
    }

    private fun tratar(comando: MediaCommand, controller: MediaController) {
        val app = GuardApplication.instancia
        val agora = System.currentTimeMillis()

        val evento = MediaEvent(
            command = comando,
            origem = OrigemDoEvento.SESSAO_DE_TERCEIRO,
            quandoMs = agora,
            dispositivo = controller.packageName,
        )

        if (!app.blockerEngine.shouldBlock(comando, configuracao)) {
            app.historyRepository.registrar(RegistroDeEvento(evento, ResultadoDoComando.PERMITIDO))
            return
        }

        if (!debounce.aceitar(comando, configuracao.debounceMs)) return

        if (!configuracao.reverterQuandoNaoBloquear) {
            // O usuário desligou a reversão. Vimos e não agimos — e é
            // isso que fica registrado. Chamar de bloqueado seria mentira.
            app.historyRepository.registrar(
                RegistroDeEvento(
                    evento,
                    ResultadoDoComando.NAO_INTERCEPTAVEL,
                    "A reversão está desligada nas configurações.",
                ),
            )
            return
        }

        try {
            controller.transportControls.play()
            app.historyRepository.registrar(
                RegistroDeEvento(
                    evento,
                    ResultadoDoComando.REVERTIDO,
                    "O player pausou e mandamos tocar de novo.",
                ),
            )
        } catch (e: SecurityException) {
            app.historyRepository.registrar(
                RegistroDeEvento(
                    evento,
                    ResultadoDoComando.NAO_INTERCEPTAVEL,
                    "Este aplicativo de música não aceitou o comando de retomar.",
                ),
            )
            Log.w(TAG, "Player recusou play()", e)
        }
    }

    companion object {
        private const val TAG = "MediaGuardListener"
    }
}
