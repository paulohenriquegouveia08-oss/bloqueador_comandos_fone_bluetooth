# Instalar quando o Play Protect bloqueia

O Play Protect bloqueia a instalação por dois motivos diferentes, e a
solução depende de qual é. **Leia a mensagem na tela do celular:**

| A mensagem diz | O motivo | O que resolve |
| --- | --- | --- |
| "app não seguro" / cita permissões | Proteção antifraude (Brasil): app de fora da loja pedindo SMS, notificações ou acessibilidade | Usar a versão **leve**, ou instalar por **ADB** |
| "app não verificado" / "desenvolvedor desconhecido" | O Play Protect não conhece este app nem quem o assinou | **ADB**, **teste interno do Play**, ou liberar nos ajustes |

Nenhuma das saídas abaixo engana o Play Protect. Elas usam caminhos de
instalação que a regra não cobre, ou estabelecem confiança de verdade.

## 1. ADB — funciona hoje, sem mexer em nada

A restrição vale para instalação por navegador, mensageiro e gerenciador
de arquivos. Pelo cabo é outro caminho.

```bash
# No celular: Ajustes → Sobre → tocar 7x em "Número da versão"
#             Opções do desenvolvedor → Depuração USB (ligar)
adb install -r BluetoothMediaGuard-completo.apk
```

Vantagem: instala a versão **completa**, com todas as funções.

## 2. Teste interno do Play Console — o caminho definitivo

O app é instalado *pela Play Store*, então não há bloqueio nenhum, e não
é preciso publicar para o público.

1. [play.google.com/console](https://play.google.com/console) → criar app
2. **Teste** → **Teste interno** → criar versão
3. Enviar `app-completo-release.aab`
4. Adicionar seu e-mail como testador
5. Abrir o link no celular e instalar

Custa a taxa única de cadastro de desenvolvedor. É o único caminho que
resolve para outras pessoas além de você — as duas opções acima servem
para testar, não para distribuir.

> **Atenção:** um app com acesso a notificações passa por revisão extra
> no Play, e é preciso declarar para que serve. O nosso tem motivo
> legítimo e verificável (controlar a sessão de mídia), mas a revisão
> existe.

## 3. Desligar a verificação no seu próprio aparelho

É o seu celular e o seu app. O Android permite:

**Play Store → foto do perfil → Play Protect → engrenagem → desligar
"Analisar apps com o Play Protect"**

Instale, e **ligue de volta depois**. Desligado, ele deixa de conferir
*todos* os apps do aparelho, não só este — a proteção some para o que
vier depois também.

## O que NÃO fazer

Disfarçar a permissão, ofuscar o serviço ou fazer o app parecer outra
coisa. É exatamente o comportamento que a proteção existe para pegar:
funcionaria até a próxima atualização do Play Protect, e deixaria o app
com cara de quem tem algo a esconder — justamente o oposto do que ele é.
