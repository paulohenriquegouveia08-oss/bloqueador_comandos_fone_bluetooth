package com.pk.bluetoothmediaguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.pk.bluetoothmediaguard.GuardApplication
import com.pk.bluetoothmediaguard.R
import com.pk.bluetoothmediaguard.domain.DecisaoDeCaptura
import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.MediaCommand
import com.pk.bluetoothmediaguard.domain.MediaEvent
import com.pk.bluetoothmediaguard.domain.OrigemDoEvento
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import com.pk.bluetoothmediaguard.bluetooth.MonitorDeFone
import com.pk.bluetoothmediaguard.media.CapturadorDeBotoes
import com.pk.bluetoothmediaguard.media.EncaminhadorDeComandos
import com.pk.bluetoothmediaguard.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Mantém a nossa MediaSession viva enquanto o bloqueio está ligado.
 *
 * Esta é a Camada A da especificação, e ela bloqueia DE VERDADE — quando
 * o sistema escolhe a nossa sessão como destino do botão. Isso acontece
 * quando nenhum outro app tem sessão mais relevante; com o Spotify
 * tocando, a sessão dele ganha (docs/pesquisa-tecnica.md).
 *
 * Por isso o serviço não é o produto inteiro: ele cobre o caso em que
 * ninguém está tocando, e o listener de notificações cobre o resto.
 *
 * Foreground porque uma MediaSession precisa de processo vivo, e o
 * Android exige a notificação — que também é honesta com o usuário sobre
 * o app estar rodando.
 */
class GuardService : Service() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessao: MediaSessionCompat
    private var configuracao = GuardSettings()
    private val capturador = CapturadorDeBotoes()
    private val encaminhador = EncaminhadorDeComandos()

    /**
     * A maior economia do app.
     *
     * Sem fone conectado não há botão para apertar, e manter a captura
     * ligada seria gastar bateria o dia inteiro protegendo de nada. O
     * custo passa a existir só enquanto o fone está no ouvido.
     */
    private var foneConectado = false
    private val monitorDeFone by lazy {
        MonitorDeFone(this, Handler(Looper.getMainLooper())) { conectado ->
            foneConectado = conectado
            ajustarCaptura()
        }
    }

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        monitorDeFone.iniciar()

        sessao = MediaSessionCompat(this, "BluetoothMediaGuard").apply {
            setCallback(callbackDaSessao)
            // O estado precisa anunciar as ações, senão o sistema não
            // considera esta sessão candidata a receber botão.
            setPlaybackState(
                android.support.v4.media.session.PlaybackStateCompat.Builder()
                    .setActions(
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP,
                    )
                    .setState(
                        android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                        0L,
                        1f,
                    )
                    .build(),
            )
            isActive = true
        }

        GuardApplication.instancia.settingsRepository.settings
            .onEach { nova ->
                configuracao = nova

                ajustarCaptura()
                if (!nova.enabled) pararSozinho()
            }
            .launchIn(escopo)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID_NOTIFICACAO, montarNotificacao())
        // START_STICKY: se o sistema matar por memória, queremos voltar —
        // é a diferença entre a proteção existir e ela sumir em silêncio.
        return START_STICKY
    }

    override fun onDestroy() {
        monitorDeFone.parar()
        capturador.parar()
        sessao.isActive = false
        sessao.release()
        escopo.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Quando a nossa sessão é a escolhida, o botão chega aqui.
     *
     * Não repassar é o bloqueio: o comando morre neste ponto e nenhum
     * player o recebe.
     */
    private val callbackDaSessao = object : MediaSessionCompat.Callback() {
        override fun onPlay() = decidir(MediaCommand.PLAY)
        override fun onPause() = decidir(MediaCommand.PAUSE)
        override fun onStop() = decidir(MediaCommand.STOP)
        override fun onSkipToNext() = decidir(MediaCommand.NEXT)
        override fun onSkipToPrevious() = decidir(MediaCommand.PREVIOUS)
        override fun onFastForward() = decidir(MediaCommand.FAST_FORWARD)
        override fun onRewind() = decidir(MediaCommand.REWIND)
    }

    /**
     * O botão chegou até nós. Aqui o bloqueio é real.
     *
     * Bloquear = não fazer nada: o comando morre neste ponto e nenhum
     * player o recebe. Permitir = encaminhar à mão, porque ao virarmos o
     * destinatário passamos a receber TODOS os botões, inclusive os que
     * a pessoa quer funcionando.
     */
    private fun decidir(comando: MediaCommand) {
        val app = GuardApplication.instancia
        val bloquear = app.blockerEngine.shouldBlock(comando, configuracao)
        val evento = MediaEvent(
            command = comando,
            origem = OrigemDoEvento.NOSSA_SESSAO,
            quandoMs = System.currentTimeMillis(),
        )

        if (bloquear) {
            app.historyRepository.registrar(
                RegistroDeEvento(
                    evento,
                    ResultadoDoComando.BLOQUEADO,
                    "Recebido por nós e descartado — não chegou ao player.",
                ),
            )
            return
        }

        val encaminhou = encaminhar(comando)
        app.historyRepository.registrar(
            RegistroDeEvento(
                evento,
                if (encaminhou) ResultadoDoComando.PERMITIDO else ResultadoDoComando.DETECTADO,
                if (encaminhou) {
                    "Repassado ao aplicativo de música."
                } else {
                    "Não estava marcado para bloquear, e não havia player para repassar."
                },
            ),
        )
    }

    /**
     * Manda o comando ao player de verdade.
     *
     * Precisa do acesso a notificações — é ele que dá os controllers das
     * sessões alheias. Sem a permissão, não há para onde repassar, e o
     * histórico registra isso em vez de o comando sumir sem explicação.
     */
    private fun encaminhar(comando: MediaCommand): Boolean = try {
        val componente = ComponentName(this, MediaGuardNotificationListener::class.java)
        val gerenciador = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        encaminhador.encaminhar(comando, gerenciador.getActiveSessions(componente))
    } catch (e: SecurityException) {
        false
    }

    /**
     * Liga ou desliga o silêncio conforme a situação REAL.
     *
     * Três condições, e todas precisam valer: a proteção ligada, o modo
     * captura escolhido, e um fone de fato conectado. Falhar qualquer uma
     * derruba a captura na hora — não faz sentido pagar o custo antes de
     * ele servir para algo.
     */
    private fun ajustarCaptura() {
        val deveCapturar = DecisaoDeCaptura.deveCapturar(configuracao, foneConectado)
        when {
            deveCapturar && !capturador.ativo -> capturador.iniciar()
            !deveCapturar && capturador.ativo -> capturador.parar()
        }

        // A notificação reflete o estado REAL. Dizer "ativo" enquanto
        // espera um fone faria a pessoa achar que está gastando bateria
        // quando não está.
        if (configuracao.enabled) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_NOTIFICACAO, montarNotificacao())
        }
    }

    private fun pararSozinho() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun montarNotificacao(): Notification {
        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(DecisaoDeCaptura.descreverEstado(configuracao, foneConectado))
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentIntent(abrir)
            .setOngoing(true)
            // Baixa de propósito: é um aviso de que o app está rodando,
            // não algo que mereça interromper quem está usando o celular.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            getString(R.string.canal_servico),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.canal_servico_descricao)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(canal)
    }

    companion object {
        private const val CANAL = "guard_service"
        private const val ID_NOTIFICACAO = 1

        fun iniciar(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
