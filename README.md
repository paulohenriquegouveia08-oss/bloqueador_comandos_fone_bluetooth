# Bluetooth Media Guard

Impedir que o botão do fone Bluetooth pause sua música sozinho.

> **Leia antes de instalar:** o Android entrega o botão do fone
> diretamente ao aplicativo de música — não há como um app comum
> "interceptar no caminho". Ver [`docs/pesquisa-tecnica.md`](docs/pesquisa-tecnica.md).
>
> O app trabalha de duas formas: **desfazendo** a pausa (funciona sempre,
> mas você ouve o corte) ou **recebendo o botão antes** do player (bloqueio
> de verdade, e pode não funcionar no seu aparelho). Detalhes abaixo, sem
> rodeio.

## Os dois modos

### Padrão — desfazer

O app observa o aplicativo de música. Quando o fone o pausa, manda tocar
de novo. **Funciona em qualquer aparelho**, e a música chega a parar por
alguns décimos de segundo — você ouve o corte. Aparece como
**Revertido**, nunca como "bloqueado", porque são coisas diferentes.

### Modo captura — bloquear de verdade

Ligue *Bloqueio de verdade → Modo captura*. O app passa a tocar silêncio
para entrar na fila de "quem tocou áudio por último", que é como o
Android escolhe quem recebe o botão. Se conseguir, o comando **chega até
nós e morre ali** — o player nem fica sabendo. Sem corte nenhum.

O que ele **não** faz: pedir foco de áudio. Pedir mandaria o Spotify
pausar, que é exatamente o problema. Foco é cooperativo — quem não pede,
não interrompe.

Os comandos que você **não** marcou são repassados ao player, senão
proteger contra a pausa quebraria o "próxima" do fone.

**Custos honestos:** gasta mais bateria (mantém o áudio acordado) e
**pode não funcionar** — a escolha do destinatário é heurística do
sistema e varia por versão e fabricante. O histórico mostra o que de
fato aconteceu.

| Situação | Como aparece |
| --- | --- |
| Recebemos o botão e descartamos | **Bloqueado** |
| Player pausou e mandamos tocar | **Revertido** |
| Comando não marcado, repassado | **Permitido** |
| Vimos e não conseguimos agir | **Não bloqueável** |

## Por que não dá para bloquear de verdade

```
Fone → AVRCP → Pilha Bluetooth → MediaSessionService → sessão do app de música
```

O `MediaSessionService` escolhe **quem recebe** o botão: no Android 8+, o
app que tocou áudio por último. Aplicativos não competem por esse evento
e não existe API para "pegar antes". Com o Spotify tocando, o botão vai
para o Spotify.

O evento também **não passa** pelo despacho de teclas do Android — então
um serviço de acessibilidade, que é onde se interceptariam teclas, não o
vê. O serviço de acessibilidade deste app existe como **sonda de
diagnóstico**: se o seu aparelho for exceção, ele mostra isso.

## Permissões, e por que cada uma

| Permissão | Para quê | Sem ela |
| --- | --- | --- |
| Acesso a notificações | Enxergar o app de música e mandar tocar de novo | O app só observa |
| Notificações | O aviso permanente que o Android exige do serviço | O serviço não roda |
| Bluetooth (conectar) | Mostrar o nome do fone no diagnóstico | Funciona, sem o nome |
| Acessibilidade *(opcional)* | Sonda de teclas físicas | Diagnóstico menos completo |

Nenhuma é ativada por código. O app leva você aos Ajustes e explica.

## Instalar

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Ativar

1. Abra o app.
2. Ligue **Proteção** — isso já marca Play, Pause e Play/Pause.
3. Toque em **Conceder acesso** e habilite em *Acesso a notificações*.
4. Volte ao app. O diagnóstico deve mostrar ✓.

## Testar (roteiro manual)

| # | Passo | Esperado |
| --- | --- | --- |
| 1 | Fone conectado, música tocando, apertar o botão | Aparece no histórico |
| 2 | Proteção ligada, apertar Play/Pause | Música volta sozinha (**Revertido**) |
| 3 | Proteção desligada, apertar Play/Pause | Música pausa e fica pausada |
| 4 | Só Play/Pause marcado, apertar Próxima | Troca de música normalmente |

O teste 2 é o que importa. Se o histórico disser **Não bloqueável**, o
motivo aparece na linha.

## Por que alguns fones podem não funcionar

- **O fone manda um código que o Android não expõe.** Alguns modelos usam
  comandos proprietários que não viram evento de mídia.
- **O app de música recusa o comando de retomar.** Alguns players só
  aceitam controle externo com a tela aberta.
- **O fabricante do celular mata o serviço.** Xiaomi, Oppo e Samsung têm
  gerenciadores de bateria agressivos; é preciso liberar o app à mão.

Em todos esses casos o app **diz** que não conseguiu. Ele nunca escreve
"bloqueado" quando só detectou.

## Privacidade

Tudo local. Sem servidor, sem conta, sem analytics. O histórico fica em
memória, limitado aos últimos 100 eventos, e some ao fechar o app — não é
gravado em disco de propósito: seria um arquivo com seu padrão de uso.

## Estado do projeto

| Entregue | Falta |
| --- | --- |
| Projeto Kotlin + Compose compilando | Validação em aparelho real com fone |
| MediaSession, listener de sessões, sonda de acessibilidade | Testes instrumentados executados |
| Mapper, engine, debounce, DataStore, histórico | Ajuste do debounce com fone real |
| **27 testes unitários** passando | Se o modo captura funciona neste aparelho |
| Modo captura (bloqueio real) | Compatibilidade por fabricante |
| APK debug | |

**Nada aqui foi validado com um fone Bluetooth de verdade.** A lógica tem
teste; o comportamento no aparelho, não. O critério de sucesso da
especificação (§42) só se cumpre com o aparelho na mão.

## Estrutura

```
app/src/main/kotlin/com/pk/bluetoothmediaguard/
├── domain/       MediaCommand, GuardSettings, MediaBlockerEngine, DebounceManager
├── data/         SettingsRepository (DataStore), EventHistoryRepository
├── media/        MediaCommandMapper
├── service/      GuardService, MediaGuardNotificationListener,
│                 KeyProbeAccessibilityService, BootReceiver
├── diagnostics/  Capacidades
└── presentation/ MainActivity, GuardViewModel
```
