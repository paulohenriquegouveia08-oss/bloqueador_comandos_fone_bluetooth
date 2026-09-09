# Fase 1 — Pesquisa técnica

Feita **antes de escrever código**, como manda a especificação (§44 e §48).
A conclusão principal contradiz um requisito do documento original, e está
registrada aqui em vez de contornada em silêncio.

## A pergunta que decide a arquitetura

> Em que ponto o comando do fone se torna observável — e interceptável —
> por um aplicativo de terceiros?

## Como o Android entrega o comando

```
Fone Bluetooth
   ↓  AVRCP (PASS THROUGH: PLAY / PAUSE / PLAY_PAUSE)
Pilha Bluetooth do Android
   ↓
MediaSessionService  ← decide QUEM recebe
   ↓  KeyEvent entregue À SESSÃO escolhida
MediaSession.Callback.onMediaButtonEvent()
   ↓
onPlay() / onPause() / …
```

Fontes: [Responding to media buttons](https://developer.android.com/guide/topics/media/legacy/media-buttons)
e [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager).

### Achado 1 — botão de mídia NÃO passa pelo despacho normal de teclas

A documentação oficial é explícita: eventos de botão de mídia **não
percorrem** `View.dispatchKeyEvent()` nem `Window.onKeyDown()`. O sistema
os intercepta antes e os encaminha direto ao framework de media session.

**Consequência:** `AccessibilityService.onKeyEvent()` — a "Camada de
interceptação global" da §9 — **não recebe comandos de mídia vindos de
fone Bluetooth**. Ela vive no pipeline de input (InputDispatcher), que é
justamente o pipeline que esses eventos contornam.

Isso não é limitação de fabricante nem de versão: é o desenho do
roteamento.

**O serviço de acessibilidade foi REMOVIDO do projeto.** Ele chegou a
existir como sonda de diagnóstico, mas é a permissão mais sinalizada do
Android — dispara aviso do Play Protect na instalação e cai em
"configuração restrita" no Android 13+. Manter uma permissão alarmante,
que a própria pesquisa mostrou não receber o evento que interessa, custa
a confiança de quem instala e não entrega nada em troca.

### Achado 2 — quem recebe o comando é decidido pelo sistema

O `MediaSessionService` escolhe a sessão "mais relevante":

| Versão | Critério |
| --- | --- |
| Android 8+ | app que tocou áudio por último (mais recente) |
| Android 5–7 | estado: preparando > tocando > pausado > parado |

**Aplicativos não competem por esse evento.** Não há API para "pegar
primeiro". Com o Spotify tocando, a sessão do Spotify é a mais relevante
e recebe o botão — a nossa não recebe nada para descartar.

**Consequência:** bloqueio por MediaSession própria (§8, Camada A)
funciona **apenas quando a nossa sessão é a escolhida** — ou seja,
quando nada mais está tocando. É real, mas é o caso menos útil.

### Achado 3 — o caminho que funciona com o Spotify tocando

`MediaSessionManager.getActiveSessions()` devolve `MediaController` das
sessões de OUTROS aplicativos. Para um app comum, o acesso vem de um
`NotificationListenerService` habilitado pelo usuário (Ajustes → Acesso a
notificações) — API oficial, sem root, sem API privada.

Com o controller é possível:

- **observar** `onPlaybackStateChanged` — ver a pausa acontecer;
- **agir** via `transportControls.play()` — desfazer a pausa.

Não é bloqueio: é **reversão**. O comando chega ao Spotify, o Spotify
pausa, e nós mandamos tocar de novo em seguida. Para quem usa, o efeito é
"o fone parou de pausar minha música"; tecnicamente é outra coisa, e o
aplicativo precisa dizer isso — a §29 e a §30 exigem exatamente essa
honestidade.

## O que isto muda na especificação

| Requisito | Situação |
| --- | --- |
| §8 Camada A — MediaSession | Mantida. Bloqueia de verdade quando nossa sessão é a escolhida. |
| §9 Camada B — AccessibilityService | **Não funciona para o alvo.** Mantida só como sonda de diagnóstico, rotulada como tal. |
| §30 Fallback | Passa a ser o caminho PRINCIPAL: observar e reverter via MediaController. |
| §47 Evidência de bloqueio real | O app distingue **detectado / bloqueado / revertido / não interceptável** e nunca chama reversão de bloqueio. |

## Três estados, nunca confundidos

```
DETECTADO       — vimos o comando acontecer
BLOQUEADO       — o comando não chegou ao player (nossa sessão o consumiu)
REVERTIDO       — chegou, o player obedeceu, e desfizemos em seguida
NAO_INTERCEPTAVEL — vimos o efeito, e não conseguimos nem bloquear nem reverter
```

## Depois da pesquisa: existe um caminho para bloqueio real

O Achado 2 diz que o sistema escolhe o destinatário — e diz QUAL é o
critério: no Android 8+, "o último app com MediaSession que **tocou áudio
localmente**".

Isso é uma porta. Não dá para pedir prioridade, mas dá para **satisfazer o
critério**: manter uma sessão ativa e tocar áudio. O app toca silêncio, em
volume zero, **sem pedir foco de áudio** — pedir foco mandaria o player
pausar, que é o problema que ele existe para resolver. Foco é cooperativo:
quem não pede, não interrompe ninguém.

Se o sistema passar a entregar o botão a nós, o bloqueio é REAL: o comando
morre na nossa sessão e o player nem fica sabendo. E os comandos não
bloqueados são repassados à sessão do player, senão proteger contra a
pausa quebraria o resto do fone.

Isto é o `modoCaptura`, desligado por padrão. Duas razões, e as duas
precisam ser ditas a quem liga:

1. **Custa bateria** — mantém o caminho de áudio do aparelho acordado.
2. **Pode não funcionar** — a heurística varia por versão e fabricante, e
   um player tocando no momento pode continuar ganhando.

O diagnóstico mostra se os botões passaram a chegar. Se não passarem, o
app diz isso e continua desfazendo a pausa, que é o que sempre funciona.

## Limites que permanecem

- Reverter uma pausa produz um corte audível de alguns décimos de segundo.
  Não há como evitar: o player já parou quando percebemos.
- Se o usuário pausar de propósito pelo aplicativo de música, o efeito
  observável é o mesmo de uma pausa acidental. O app não consegue
  distinguir intenção — por isso a reversão só age quando o comando veio
  logo após atividade do fone, e há um interruptor para desligar.
- Nada disso funciona sem o usuário conceder acesso a notificações. Sem
  a permissão, o app fica em modo diagnóstico.

## O que NÃO foi usado

Root, Xposed, Magisk, API privada, injeção de evento, sniffing de
Bluetooth alheio — nada disso, conforme §10 e §31.
