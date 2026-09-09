package com.pk.bluetoothmediaguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pk.bluetoothmediaguard.GuardApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Restaura o bloqueio depois de reiniciar o aparelho (§18).
 *
 * Só age se a pessoa tiver marcado "iniciar automaticamente" E o
 * bloqueio estiver ligado. Subir um serviço em primeiro plano depois do
 * boot sem alguém ter pedido é o tipo de coisa que faz o usuário
 * desinstalar o app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val resultado = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = GuardApplication.instancia.settingsRepository.settings.first()
                if (s.autoStart && s.enabled) GuardService.iniciar(context)
            } finally {
                resultado.finish()
            }
        }
    }
}
