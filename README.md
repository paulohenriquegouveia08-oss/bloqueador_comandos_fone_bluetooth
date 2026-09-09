# Bluetooth Media Guard

Impedir que o botão do fone Bluetooth pause sua música sozinho.

> **Leia antes de instalar:** o Android **não permite** que um aplicativo
> comum bloqueie o botão de mídia do fone enquanto outro app está
> tocando. Isso não é limitação deste projeto — é o desenho do sistema, e
> está demonstrado em [`docs/pesquisa-tecnica.md`](docs/pesquisa-tecnica.md).
> O que este app faz, e o que não faz, está descrito abaixo sem rodeio.

## O que ele faz

| Situação | O que acontece | Como aparece no app |
| --- | --- | --- |
| Nada tocando, botão pressionado | O comando morre na nossa sessão | **Bloqueado** |
| Spotify tocando, fone manda pausar | O Spotify pausa e o app manda tocar de novo | **Revertido** |
| Comando que você não marcou | Segue para o player | **Permitido** |
| Nem bloqueamos nem revertemos | Registrado com o motivo | **Não bloqueável** |

**"Revertido" não é "bloqueado".** A música chega a parar por alguns
décimos de segundo, e você vai ouvir o corte. O app usa palavras
diferentes porque são coisas diferentes.

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
| **22 testes unitários** passando | Compatibilidade por fabricante |
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
