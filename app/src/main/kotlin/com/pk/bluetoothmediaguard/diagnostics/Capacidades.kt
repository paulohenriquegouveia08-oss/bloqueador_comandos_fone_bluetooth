package com.pk.bluetoothmediaguard.diagnostics

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * O que este aparelho, de fato, permite (§28).
 *
 * Cada linha é uma pergunta respondida por consulta ao sistema, não por
 * suposição. A tela mostra isso cru — é o que separa "o app não
 * funciona" de "este aparelho não deixa".
 */
data class Capacidades(
    val acessoANotificacoes: Boolean,
    val acessibilidadeAtiva: Boolean,
    val servicoDeBloqueioAtivo: Boolean,
    val sessoesVisiveis: Int,
) {
    /**
     * O bloqueio de verdade depende de enxergar as sessões dos outros
     * apps. Sem acesso a notificações, o app fica em diagnóstico.
     */
    val podeAgir: Boolean get() = acessoANotificacoes

    val resumo: String get() = when {
        !acessoANotificacoes -> "Falta o acesso a notificações"
        sessoesVisiveis == 0 -> "Sem app de música ativo agora"
        else -> "Pronto"
    }
}

object DetectorDeCapacidades {

    /**
     * O usuário concedeu acesso a notificações?
     *
     * Lido de `enabled_notification_listeners`, que é a lista que o
     * próprio sistema mantém. Não há API melhor para um app comum
     * perguntar isso sobre si mesmo.
     */
    fun temAcessoANotificacoes(context: Context): Boolean {
        val nossos = context.packageName
        val habilitados = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return habilitados.split(":").any { entrada ->
            ComponentName.unflattenFromString(entrada)?.packageName == nossos
        }
    }

    /** A sonda de acessibilidade está ligada? */
    fun temAcessibilidade(context: Context, servico: Class<*>): Boolean {
        val esperado = ComponentName(context, servico).flattenToString()
        val ligados = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val separador = TextUtils.SimpleStringSplitter(':')
        separador.setString(ligados)
        while (separador.hasNext()) {
            if (separador.next().equals(esperado, ignoreCase = true)) return true
        }
        return false
    }
}
