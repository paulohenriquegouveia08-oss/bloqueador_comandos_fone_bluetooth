package com.pk.bluetoothmediaguard.data

import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Histórico dos últimos comandos (§20).
 *
 * Em memória, e limitado a 100, de propósito: é material de diagnóstico
 * de agora, não registro permanente. Guardar em disco criaria um arquivo
 * com o padrão de uso da pessoa — dado que o app não precisa e não quer
 * ter (§32).
 *
 * O anel descarta o mais antigo sozinho; sem isso, uma sessão longa com
 * um fone defeituoso encheria a memória com o mesmo evento repetido.
 */
class EventHistoryRepository(private val limite: Int = LIMITE_PADRAO) {

    private val _historico = MutableStateFlow<List<RegistroDeEvento>>(emptyList())
    val historico: StateFlow<List<RegistroDeEvento>> = _historico.asStateFlow()

    fun registrar(registro: RegistroDeEvento) {
        _historico.value = (listOf(registro) + _historico.value).take(limite)
    }

    fun limpar() {
        _historico.value = emptyList()
    }

    companion object {
        const val LIMITE_PADRAO = 100
    }
}
