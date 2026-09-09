package com.pk.bluetoothmediaguard.presentation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pk.bluetoothmediaguard.domain.GuardSettings
import com.pk.bluetoothmediaguard.domain.MediaBlockerEngine
import com.pk.bluetoothmediaguard.domain.RegistroDeEvento
import com.pk.bluetoothmediaguard.domain.ResultadoDoComando
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: GuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TelaPrincipal(
                        vm = vm,
                        abrirAcessoANotificacoes = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A pessoa sai para os Ajustes e volta. Sem reler aqui, a tela
        // continuaria dizendo que a permissão falta.
        vm.atualizarCapacidades()
    }
}

private val engine = MediaBlockerEngine()

@Composable
private fun TelaPrincipal(
    vm: GuardViewModel,
    abrirAcessoANotificacoes: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val capacidades by vm.capacidades.collectAsStateWithLifecycle()
    val historico by vm.historico.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Bluetooth Media Guard", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // A pergunta que traz a pessoa até aqui (§38).
        Text(
            "Seu fone pausa a música sozinho?",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Proteção", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.enabled) "Ativada" else "Desativada",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.enabled, onCheckedChange = { vm.alternarBloqueio(it) })
                }

                if (!capacidades.acessoANotificacoes) {
                    // Sem isto o app só observa. Dizer por que, e levar
                    // ao lugar certo — nunca ligar sozinho (§16).
                    Divider()
                    Text(
                        "Para agir quando o fone pausar, o Android exige acesso a notificações. " +
                            "É por ele que o app enxerga o aplicativo de música. Nada é lido nem enviado para fora.",
                        fontSize = 13.sp,
                    )
                    Button(onClick = abrirAcessoANotificacoes) { Text("Conceder acesso") }
                }
            }
        }

        Text("Bloquear comandos", fontWeight = FontWeight.SemiBold)
        Card {
            Column(Modifier.padding(vertical = 4.dp)) {
                LinhaDeOpcao("Play", s.blockPlay, s.enabled) { v -> vm.atualizar { it.copy(blockPlay = v) } }
                LinhaDeOpcao("Pause", s.blockPause, s.enabled) { v -> vm.atualizar { it.copy(blockPause = v) } }
                LinhaDeOpcao("Play/Pause", s.blockPlayPause, s.enabled) { v -> vm.atualizar { it.copy(blockPlayPause = v) } }
                LinhaDeOpcao("Próxima", s.blockNext, s.enabled) { v -> vm.atualizar { it.copy(blockNext = v) } }
                LinhaDeOpcao("Anterior", s.blockPrevious, s.enabled) { v -> vm.atualizar { it.copy(blockPrevious = v) } }
                LinhaDeOpcao("Parar", s.blockStop, s.enabled) { v -> vm.atualizar { it.copy(blockStop = v) } }
                LinhaDeOpcao("Volume", s.blockVolume, s.enabled) { v -> vm.atualizar { it.copy(blockVolume = v) } }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bloqueio de verdade", fontWeight = FontWeight.SemiBold)
                Text(
                    "Sem isto, o app só desfaz a pausa depois que ela acontece — e você ouve " +
                        "o corte. Ligado, ele tenta receber o botão antes do aplicativo de " +
                        "música, e aí o comando nem chega lá.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Modo captura", fontSize = 14.sp)
                    Switch(
                        checked = s.modoCaptura,
                        enabled = s.enabled,
                        onCheckedChange = { v -> vm.atualizar { it.copy(modoCaptura = v) } },
                    )
                }
                Text(
                    "Gasta mais bateria, porque mantém o áudio do aparelho acordado. E pode " +
                        "não funcionar aqui: depende de como o seu Android escolhe quem recebe " +
                        "o botão. O histórico abaixo mostra o que realmente aconteceu.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnóstico", fontWeight = FontWeight.SemiBold)
                LinhaDeEstado("Acesso a notificações", capacidades.acessoANotificacoes)
                LinhaDeEstado("Serviço de bloqueio", capacidades.servicoDeBloqueioAtivo)

                Text(
                    "O botão de mídia do fone vai direto para o aplicativo de música — o " +
                        "Android não o entrega a mais ninguém no caminho. É por isso que o app " +
                        "precisa ou receber o botão antes (modo captura), ou desfazer depois.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Últimos comandos", fontWeight = FontWeight.SemiBold)
            if (historico.isNotEmpty()) {
                TextButton(onClick = { vm.limparHistorico() }) { Text("Limpar") }
            }
        }

        if (historico.isEmpty()) {
            Text(
                "Nenhum comando ainda. Toque no botão do fone com a música tocando.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            historico.take(20).forEach { LinhaDeHistorico(it) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LinhaDeOpcao(rotulo: String, marcado: Boolean, habilitado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        Checkbox(checked = marcado, enabled = habilitado, onCheckedChange = aoMudar)
    }
}

@Composable
private fun LinhaDeEstado(rotulo: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, fontSize = 13.sp)
        Text(if (ok) "✓" else "—", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val formatoDaHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun LinhaDeHistorico(registro: RegistroDeEvento) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(engine.descrever(registro.evento.command), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(textoDoResultado(registro.resultado), fontSize = 13.sp)
        }
        Text(
            formatoDaHora.format(Date(registro.evento.quandoMs)) +
                (registro.evento.dispositivo?.let { " · $it" } ?: ""),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        registro.motivo?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Cada estado tem palavra própria.
 *
 * "Revertido" não vira "bloqueado" na tela: quem instalou o app precisa
 * saber que a música chegou a parar, senão vai achar que o app falhou
 * quando ouvir o corte.
 */
private fun textoDoResultado(r: ResultadoDoComando): String = when (r) {
    ResultadoDoComando.BLOQUEADO -> "Bloqueado"
    ResultadoDoComando.REVERTIDO -> "Revertido"
    ResultadoDoComando.DETECTADO -> "Detectado"
    ResultadoDoComando.PERMITIDO -> "Permitido"
    ResultadoDoComando.NAO_INTERCEPTAVEL -> "Não bloqueável"
}
