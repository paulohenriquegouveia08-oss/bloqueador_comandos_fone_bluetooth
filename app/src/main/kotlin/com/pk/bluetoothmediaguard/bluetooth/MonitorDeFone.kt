package com.pk.bluetoothmediaguard.bluetooth

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler

/**
 * Diz se existe um fone Bluetooth conectado agora.
 *
 * ## Por que isto é a maior economia do app
 *
 * O modo captura precisa tocar áudio para entrar na fila de "quem tocou
 * por último". Isso mantém o caminho de áudio do aparelho acordado, e é
 * o único gasto real do app.
 *
 * Sem fone conectado, **não existe problema para resolver** — nenhum
 * botão vai ser apertado. Manter a captura ligada nesse período seria
 * gastar bateria o dia inteiro para proteger de nada.
 *
 * Com este monitor, o custo passa a existir só enquanto o fone está no
 * ouvido da pessoa. No resto do dia, zero.
 *
 * ## Por que por evento, e não por consulta periódica
 *
 * `AudioDeviceCallback` avisa quando um dispositivo entra ou sai.
 * Perguntar de tempos em tempos acordaria o processador em intervalos
 * fixos — exatamente o padrão que faz um app aparecer na lista de
 * consumo de bateria do Android.
 */
class MonitorDeFone(
    private val context: Context,
    private val handler: Handler,
    private val aoMudar: (conectado: Boolean) -> Unit,
) {
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var registrado = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(dispositivos: Array<out AudioDeviceInfo>?) = avaliar()
        override fun onAudioDevicesRemoved(dispositivos: Array<out AudioDeviceInfo>?) = avaliar()
    }

    fun iniciar() {
        if (registrado) return
        audioManager.registerAudioDeviceCallback(callback, handler)
        registrado = true
        avaliar()
    }

    fun parar() {
        if (!registrado) return
        audioManager.unregisterAudioDeviceCallback(callback)
        registrado = false
    }

    fun temFoneConectado(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { ehFone(it.type) }

    private fun avaliar() = aoMudar(temFoneConectado())

    /**
     * Fone com fio entra na conta.
     *
     * O botão de um fone com fio produz o mesmo comando de mídia, e a
     * pessoa tem o mesmo problema. Excluir seria proteger metade.
     */
    private fun ehFone(tipo: Int): Boolean = when (tipo) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    /** O nome do fone, para a tela de diagnóstico. Vazio se não der para saber. */
    fun nomeDoFone(): String? = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { ehFone(it.type) }
        ?.productName
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}
