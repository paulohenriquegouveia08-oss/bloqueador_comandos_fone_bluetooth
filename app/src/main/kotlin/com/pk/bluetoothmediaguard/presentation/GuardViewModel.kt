package com.pk.bluetoothmediaguard.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pk.bluetoothmediaguard.GuardApplication
import com.pk.bluetoothmediaguard.diagnostics.Capacidades
import com.pk.bluetoothmediaguard.diagnostics.DetectorDeCapacidades
import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.service.GuardService
import com.pk.bluetoothmediaguard.service.KeyProbeAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GuardViewModel(app: Application) : AndroidViewModel(app) {

    private val guardApp = app as GuardApplication

    val settings: StateFlow<GuardSettings> = guardApp.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardSettings())

    val historico: StateFlow<List<RegistroDeEvento>> = guardApp.historyRepository.historico

    private val _capacidades = MutableStateFlow(
        Capacidades(
            acessoANotificacoes = false,
            acessibilidadeAtiva = false,
            servicoDeBloqueioAtivo = false,
            sessoesVisiveis = 0,
        ),
    )
    val capacidades: StateFlow<Capacidades> = _capacidades.asStateFlow()

    /**
     * Relê o estado real do sistema.
     *
     * Chamado toda vez que a tela volta ao primeiro plano: a pessoa sai
     * para os Ajustes conceder a permissão e volta — sem reler, a tela
     * continuaria dizendo que falta.
     */
    fun atualizarCapacidades() {
        val ctx = getApplication<Application>()
        _capacidades.value = Capacidades(
            acessoANotificacoes = DetectorDeCapacidades.temAcessoANotificacoes(ctx),
            acessibilidadeAtiva = DetectorDeCapacidades.temAcessibilidade(
                ctx,
                KeyProbeAccessibilityService::class.java,
            ),
            servicoDeBloqueioAtivo = settings.value.enabled,
            sessoesVisiveis = 0,
        )
    }

    fun alternarBloqueio(ligar: Boolean) = viewModelScope.launch {
        val atual = settings.value
        val nova = if (ligar) {
            // Primeira ativação aplica o perfil da especificação (§12):
            // só os três comandos de reprodução. Se a pessoa já tinha
            // configurado algo, respeitamos a escolha dela.
            val jaConfigurou = atual.blockPlay || atual.blockPause || atual.blockPlayPause ||
                atual.blockNext || atual.blockPrevious || atual.blockStop || atual.blockVolume
            if (jaConfigurou) atual.copy(enabled = true) else GuardSettings.perfilInicial(atual)
        } else {
            atual.copy(enabled = false)
        }

        guardApp.settingsRepository.salvar(nova)

        val ctx = getApplication<Application>()
        if (nova.enabled) GuardService.iniciar(ctx) else GuardService.parar(ctx)
        atualizarCapacidades()
    }

    fun atualizar(transformar: (GuardSettings) -> GuardSettings) = viewModelScope.launch {
        guardApp.settingsRepository.salvar(transformar(settings.value))
    }

    fun limparHistorico() = guardApp.historyRepository.limpar()
}
