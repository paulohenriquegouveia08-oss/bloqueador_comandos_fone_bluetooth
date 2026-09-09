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
roteamento. Um `AccessibilityService` com `canRequestFilterKeyEvents`
continua útil para teclas físicas do aparelho (volume, por exemplo), e
por isso foi mantido no projeto — mas como **sonda de diagnóstico**, não
como mecanismo de bloqueio. A tela de diagnóstico diz isso ao usuário.

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
