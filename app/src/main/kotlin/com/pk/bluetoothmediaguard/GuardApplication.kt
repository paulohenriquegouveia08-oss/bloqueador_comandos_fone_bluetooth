package com.pk.bluetoothmediaguard

import android.app.Application
import com.pk.bluetoothmediaguard.data.EventHistoryRepository
import com.pk.bluetoothmediaguard.data.SettingsRepository
import com.pk.bluetoothmediaguard.domain.MediaBlockerEngine

/**
 * As dependências, montadas à mão.
 *
 * Sem injetor: são três objetos e um deles é um `object`. Uma biblioteca
 * de injeção aqui adicionaria geração de código e uma curva de leitura em
 * troca de nada.
 *
 * O histórico vive aqui porque precisa sobreviver à tela e ser o MESMO
 * que o serviço alimenta — dois históricos seriam a tela mostrando uma
 * coisa e o serviço outra.
 */
class GuardApplication : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val historyRepository: EventHistoryRepository by lazy { EventHistoryRepository() }
    val blockerEngine: MediaBlockerEngine by lazy { MediaBlockerEngine() }

    companion object {
        lateinit var instancia: GuardApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instancia = this
    }
}
