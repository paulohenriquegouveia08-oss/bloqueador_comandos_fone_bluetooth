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
import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.MediaCommand
import com.pk.bluetoothmediaguard.domain.MediaEvent
import com.pk.bluetoothmediaguard.domain.OrigemDoEvento
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
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

    override fun onCreate() {
        super.onCreate()
        criarCanal()

        sessao = MediaSessionCompat(this, "BluetoothMediaGuard").apply {
            setCallback(callbackDaSessao)
            isActive = true
        }

        GuardApplication.instancia.settingsRepository.settings
            .onEach { nova ->
                configuracao = nova
                // A sonda de acessibilidade lê daqui: ela não recebe
                // dependências do sistema e não pode fazer I/O por tecla.
                KeyProbeAccessibilityService.ultimaConfiguracao = nova
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

    private fun decidir(comando: MediaCommand) {
        val app = GuardApplication.instancia
        val bloquear = app.blockerEngine.shouldBlock(comando, configuracao)

        app.historyRepository.registrar(
            RegistroDeEvento(
                MediaEvent(
                    command = comando,
                    origem = OrigemDoEvento.NOSSA_SESSAO,
                    quandoMs = System.currentTimeMillis(),
                ),
                if (bloquear) ResultadoDoComando.BLOQUEADO else ResultadoDoComando.DETECTADO,
                if (bloquear) {
                    "Nossa sessão recebeu o botão e não repassou."
                } else {
                    "Nossa sessão recebeu o botão; não estava marcado para bloquear."
                },
            ),
        )
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
            .setContentText(getString(R.string.notificacao_ativo))
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
