# Rivais do Lar 0.3.0 — "Suco"

> Plano revisado a partir da auditoria do Codex. A auditoria está correta no diagnóstico de
> backend, mas mistura três releases num só e trata o visual como detalhe. Este documento
> **separa** as duas coisas: primeiro o que faz o jogo gostoso de abrir, depois as regras novas.

## 0. Decisão

| | |
|---|---|
| **0.3.0 (este plano)** | Vidro v2 + partículas + haptics + celebrações + cartas com flip/foil. **Zero migração de banco, zero mudança de regra.** |
| **0.4.0** | O plano do Codex, com recorte: ocorrências, penalidades, 3 cartas novas, 1 decisiva, extrato. |
| **0.5.0** | Resto das cartas e o modo combinado completo. |

**O tema Liquid Glass não é removido em nenhum momento.** As regras da seção 4 do `PLANO.md`
continuam valendo: vidro só na camada de navegação e controles, conteúdo sólido, nunca vidro
sobre vidro, preto único, sem Material, sem emoji.

O que muda é a **intensidade**: o vidro ganha vida (reflexo que reage, pulso de refração,
"squish" no toque) e o jogo ganha partículas, som e haptics nos momentos que importam.

---

## 1. Por que não fazer tudo de uma vez

O 0.3.0 do Codex tem 4–6 semanas de trabalho:

- ocorrências de tarefa (mudança estrutural de dados);
- 9 cartas normais + 3 decisivas;
- rebalanceamento de economia e penalidades;
- versionamento de regras por temporada;
- fila offline com idempotência;
- redesign de 3 telas.

Nada disso é divertido de testar com uma pessoa só. E o risco de parar no meio é alto.

O "suco" é o contrário: **3–5 dias, baixo risco, impacto percebido imediato**, e independente
do banco. Dá para instalar em cima do 0.2.0 e sentir a diferença no mesmo dia. É o que
transforma "app de tarefas" em "jogo do casal".

---

## 2. Escopo do 0.3.0

### Entra

1. `GlassSurface` v2: reflexo vivo, varredura de luz, squish, pulso de refração, grão, caustica.
2. Sistema único de partículas (`ui/juice/Particulas.kt`).
3. Catálogo de eventos → partículas + haptic.
4. Cartas com flip 3D, foil e brilho de raridade.
5. Placar com barra elástica, número com "pop" e rastro nas moedas.
6. Fim de temporada com confete físico (substitui os 9 pedaços fixos).
7. Haptics de verdade (`VibrationEffect` predefinido).
8. Micro-rituais de retenção (resumo do dia, marco de sequência, recap semanal).
9. **Resetar temporada** em Casa: zera placar e meta da temporada atual, cancela conclusões
   pendentes e devolve essas tarefas para a fila. XP, moedas, sequência, cartas e tarefas já
   validadas continuam.
10. Testes de acessibilidade, performance e captura de tela.

### Não entra

- Nenhuma migração, tabela, RPC ou cron novo — **exceto** `20261031000001_resetar_temporada.sql`,
  que cria a RPC do reset. Ela precisa ser aplicada no Supabase antes de publicar o APK.
- Nenhuma carta, preço, teto ou penalidade nova.
- Nenhuma tela nova de navegação.
- Nada de Material, nada de emoji, nada de vidro sobre vidro.
- Nada de animação infinita em background (bateria).

### Versionamento

- `versionName = "0.3.0"`, `versionCode = 300`.
- Mesmo `applicationId` e **mesma keystore** (`rivais-release.jks`), senão o Android recusa a
  atualização por cima da 0.2.0.
- Tag `v0.3.0` → workflow publica no GitHub Releases.
- O atualizador já existe (`UPDATE_REPO`, `AtualizacaoTest`). Só validar descoberta e download.

---

## 3. Vidro v2

### 3.1 O que fica igual

- `GlassSurface` como único componente de vidro.
- Blur Haze ~20dp + véu `rgba(44,44,48,.42)`.
- Borda especular 1dp (branco 50% → transparente → 25%).
- Brilho radial no topo, sombra difusa, fallback opaco em economia de bateria.
- AGSL `lenteDeVidro` no Android 13+.

### 3.2 O que evolui

**a) Reflexo vivo (`LocalInclinacao`)**

Novo composable em `ui/juice/Inclinacao.kt`:

```kotlin
val LocalInclinacao = compositionLocalOf { Offset.Zero } // -1..1 nos dois eixos

@Composable
fun InclinacaoProvider(conteudo: @Composable () -> Unit)
```

- Lê `TYPE_ACCELEROMETER` com `SENSOR_DELAY_GAME`.
- Filtro passa-baixa: `atual = atual * 0.92f + novo * 0.08f` (não treme).
- Limita a ±1 e zera quando a tela não está `RESUMED`.
- Desligado com "reduzir movimento" ou economia de bateria.
- O gradiente especular desloca `start`/`end` em até 12% da largura/altura.

Custo: um listener de sensor, sem impacto perceptível.

**b) Varredura de luz na entrada**

Uma faixa branca a 8% atravessa o vidro em 700ms quando ele aparece (uma vez, não em loop).
Efeito de "vidro recém-colocado". Desligado com reduzir movimento.

**c) Squish líquido**

`Modifier.pressao()` com `collectIsPressedAsState()`:

- escala `0.975f`;
- raio do canto `+4dp` (22 → 26, 50% → 50% nas cápsulas);
- mola: `spring(dampingRatio = 0.55f, stiffness = 700f)`.

É o detalhe que mais vende a sensação de vidro.

**d) Pulso de refração**

O shader `LENTE` já expõe o uniform `borda`. Animar `8dp → 12dp → 8dp` em 450ms no toque.
Isso faz a beirada "engrossar" como lente real.

> **Correção de performance necessária:** hoje `lenteDeVidro` cria um `RuntimeShader` novo
> dentro do bloco `graphicsLayer`, que roda a cada frame. Passar a cachear o shader por
> `(largura, altura, raio, borda)` e só recriar quando mudar. Sem isso, animar a borda fica caro.

**e) Grão e caustica**

- `HazeBlurStyle.noiseFactor(0.015f)` (hoje está `0f`): grão fino dá materialidade.
- Atrás de `tinta != null`, um radial `tinta.copy(alpha = 0.20f)` deslocado para baixo/direita,
  raio 1.2× — a "luz atravessando o vidro".
- Sombra interna inferior (preto 8%) para dar espessura.

### 3.3 Onde aplicar

| Componente | Reflexo vivo | Squish | Pulso |
|---|---|---|---|
| `BotaoCirculo` do topo | sim | sim | sim |
| Botão "+" | sim | sim | sim |
| `BarraAbas` | sim | — | — |
| `BotaoCapsula` tingido | sim | sim | sim |
| `Segmentado` | sim | — | — |
| `FolhaVidro` (sheet) | leve | — | — |

Nunca no conteúdo sólido.

---

## 4. Sistema de partículas

### 4.1 Arquivos

```
app/src/main/java/com/rivaisdolar/app/ui/juice/
  Particulas.kt   // modelo, pool, loop de frame, Canvas
  Emissores.kt    // moeda, confete, fagulha, brasa, cinza, brilho
  Juice.kt        // LocalJuice, EventoJuice, JuiceState
  Haptico.kt      // VibrationEffect com fallback
  Inclinacao.kt   // acelerômetro filtrado
```

Um único `Canvas` por tela, sobreposto ao conteúdo. Não é um composable por partícula.

### 4.2 Modelo

```kotlin
class Particula {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var vida = 0f; var vidaMax = 1f
    var tamanho = 3f
    var cor = Color.White
    var giro = 0f; var giroVel = 0f
    var forma = Forma.Circulo   // Circulo, Retangulo, Anel
    var gravidade = 900f
    var arrasto = 1f
    var brilho = false          // additive
}

class SistemaParticulas {
    private val pool = Array(160) { Particula() }  // ring buffer, sem alocação por frame
    fun emitir(explosao: Explosao)
    fun atualizar(dt: Float)
    fun desenhar(escopo: DrawScope)
    val ativo: Boolean
}
```

Loop: `withFrameNanos` só enquanto `ativo`. Sem `LaunchedEffect` por partícula.

### 4.3 Catálogo de eventos

| Evento | Emissor | Onde | Haptic |
|---|---|---|---|
| Tarefa validada (meus pontos) | fagulha + moeda | placar e contador | Pop |
| Tarefa validada (do rival) | fagulha discreta | linha do rival | Tick |
| Subiu de nível | confete curto | avatar | Pop duplo |
| Sequência 7/30 dias | brasa | chama da sequência | Sucesso |
| Carta comprada | brilho + moedas saindo | saldo | Clique |
| Carta usada | burst na cor da raridade | centro da tela | Sucesso |
| Carta recebida (ataque) | cinza | topo | Erro |
| Meta da casa batida | confete dourado | tela toda | Vitoria |
| Virada no placar | fagulha na barra | barra dividida | Pop |
| Fim de temporada (vitória) | confete físico | tela toda | Vitoria |
| Fim de temporada (derrota) | cinza leve | tela toda | — |
| Erro de ação | nada | — | Erro |

Regra: **partícula nunca é o único sinal**. Todo evento tem texto e/ou haptic.

### 4.4 Especificação dos emissores

**Moeda** (contador)
- 8–12 partículas, 300–600 px/s, gravidade 900, vida 0.5–0.8s, 3–5dp.
- Cores: `Cores.Ouro`, `#F6DE8D`, branco.
- Integra com `VooDeMoedas`: cada moeda que chega solta 3 fagulhas.

**Confete** (vitória, meta)
- 60–90 partículas, 200–500 px/s, gravidade 400, arrasto 0.98.
- Vida 2.5–4s, retângulo 5×9dp, rotação ±90–360°/s.
- Cores: branco, `Cores.Ouro`, `#F6DE8D`.
- Duas camadas de profundidade (tamanho e alpha) para não parecer papel picado plano.

**Fagulha** (pontos)
- 10–16 radiais, 120–300 px/s, sem gravidade, vida 0.4–0.7s, 2dp.
- Cor do jogador. `brilho = true`.

**Brasa** (sequência)
- 6–10 subindo, `vy = -60`, vida 1.2s, laranja, cintilância senoidal.

**Cinza** (ataque/derrota)
- 12 caindo devagar, cinza, vida 1.5s, alpha baixo.

**Brilho** (carta)
- 1 anel expandindo, 300ms, stroke 1.5dp, cor da raridade.

### 4.5 Limites de performance

- Teto de **160 partículas**; acima disso, descarta as mais antigas.
- Canvas único, sem alocação por frame.
- Pausa quando o ciclo de vida não está `RESUMED`.
- Nada de partícula em loop de fundo.
- Desligado com reduzir movimento / economia de bateria (haptic continua).
- Meta: < 1% de frames perdidos em aparelho intermediário, APK +< 50 KB.

---

## 5. Haptics

`ui/juice/Haptico.kt`:

```kotlin
enum class Toque { Clique, Tick, Pop, Sucesso, Erro, Vitoria }
```

- **API 30+:** `VibrationEffect.createPredefined(EFFECT_CLICK / EFFECT_TICK / EFFECT_POP)` via
  `VibratorManager`.
- **API 26–29:** `oneShot` com envelope de amplitude (20–40ms).
- **Vitória:** waveform `[0, 30, 40, 20, 60, 30, 120]` com amplitudes `[0, 180, 0, 255, 0, 120, 0]`.
- Respeita `Settings.System.HAPTIC_FEEDBACK_ENABLED` e o modo silencioso.
- Substitui os `HapticFeedbackType.Confirm/Reject` atuais, mantendo o fallback da API do Compose.

---

## 6. Som (opcional — 0.3.1)

Fora do escopo inicial para não atrasar. Se entrar:

- 3 sons curtos: moeda, whoosh de carta, fanfarra de vitória.
- `SoundPool`, volume 0.4, sem loop.
- Interruptor em **Casa → Preferências** (SharedPreferences local, sem banco).
- Nunca som como único feedback.

---

## 7. Cartas

- **Flip 3D** na revelação/compra: `rotationY 0 → 180`, `cameraDistance = 12 * density`,
  mola. Frente = carta; verso = brasão do app.
- **Foil:** gradiente linear a 12% de alpha que desliza com `LocalInclinacao`; blend `Screen`.
- **Raridade:** comum branca 1dp; rara azul; épica dourada com brilho pulsante **só quando visível**.
- **Uso:** a carta sobe, escala 1.05, voa para o centro e solta o burst da raridade.
- A mão continua com 3 espaços (regra atual). O 0.4.0 muda para 4 + decisiva.

---

## 8. Placar

- `BarraDividida` v2: mola + ponto de luz na ponta do líder + leve overshoot na virada.
- Número do placar: `pop` de escala 1 → 1.08 → 1 e flash dourado quando sobe.
- `+95` flutuante subindo e sumindo a partir do placar.
- Diferença no rodapé ganha copy dinâmica: "Empatados", "+12 pts", "Faltam 12 pra virar".
- Contador de moedas: anel de pulso + 3 fagulhas na chegada.

---

## 9. Fim de temporada

- Trocar os 9 pedaços fixos de `FimTemporadaTela.kt` pelo emissor de confete físico.
- Manter os raios dourados girando (é a identidade da tela).
- Troféu ganha varredura de brilho dourado (700ms, uma vez).
- Derrota: sem confete; cinza leve + troféu cinza (já existe).

---

## 10. Retenção — ritual, não cassino

O que faz um app de casal ser aberto todo dia:

- **Resumo de hoje** no topo do placar: uma linha, "2 tarefas suas, 1 pra validar".
- **Sequência** com chama e marco visual em 7/30 dias.
- **"Faltam X pra virar"** no rodapé do placar.
- **Recap de segunda:** melhor tarefa, pontos, sequência, quem venceu — cartão no topo.
- **Cutucada no rival:** 1 por dia, mensagens prontas com deboche (sem texto livre).
- **Notificação com atitude:** já previsto no `PLANO.md`, agora com o copy certo.

O que **não** entra:

- Recompensa por login diário.
- Medo de perder / punição-surpresa.
- Escassez artificial.
- Qualquer mecânica que penalize quem tem uma semana ruim de verdade.

---

## 11. Acessibilidade e guardrails

- `LocalReduzirMovimento` desliga: inclinação, varredura, confete, brasa, pulso, squish.
- `LocalVidroReal` (economia de bateria) desliga: blur, sensor, partículas.
- Partículas decorativas com `contentDescription = null`.
- Haptic nunca é o único sinal.
- Áreas de toque ≥ 44dp.
- Contraste e tamanhos de fonte atuais preservados.
- Fonte ampliada: nada de altura fixa que corte texto.

---

## 12. Testes e critérios de pronto

**Unitários**
- Matemática das partículas com semente fixa (posição, vida, cap de 160).
- Mapeamento evento → emissor/haptic.
- `InclinacaoProvider` filtra e limita.

**Captura (Robolectric/Roborazzi)**
- Placar, Cartas, Fim de temporada, sheet de concluir.
- Comparar com as capturas atuais para garantir que o tema não regrediu.

**Manual**
- Android 8, 12, 13 e 14.
- Economia de bateria ligada/desligada.
- Reduzir animações ligado/desligado.
- Aparelho intermediário: rolar o placar sem travar.
- Instalar 0.3.0 por cima da 0.2.0 e conferir sessão, cache e histórico.

**Pronto quando**
- `./gradlew assembleRelease` gera APK assinado.
- Só a migração do reset foi adicionada; nenhuma regra de pontuação mudou.
- Tema preto + vidro intacto e mais bonito.
- Partículas só em evento, sem jank perceptível.
- APK cresce < 50 KB.
- Atualização pelo app funciona.

---

## 13. Roadmap 0.4.0 — regras do Codex, corrigidas

O plano do Codex continua válido no essencial. Ajustes:

1. **Configuração por temporada, não por jogador.** Tabela
   `regras_temporada (temporada_id, versao, parametros jsonb)`. O servidor lê a versão da
   temporada; o app só exibe. Elimina "registrar a versão compatível de cada jogador".
2. **Recorte de cartas.** Entram só: Café Passado, Seguro Antissacanagem, Balde de Água Fria
   e a decisiva Arrastão do Sofá. As outras 9 vão para 0.5.0 depois do playtest. 15 tipos com
   4 espaços de mão é catálogo demais para dois jogadores.
3. **Ataque com teto estourado falha antes de consumir a carta.** O plano não diz o que
   acontece quando o alvo já bateu 50/dia ou 120/temporada. Resposta: erro claro, carta
   preservada.
4. **Ordem de defesa explícita.** Seguro é checado antes de Reviravolta. Se o Seguro bloquear,
   a Reviravolta **não** dispara e **não** é consumida. Só uma defesa é consumida por ataque.
5. **Ocorrências idempotentes.** `unique (tarefa_id, periodo_inicio)` e índice de idempotência
   nas mutações. Conclusão duplicada devolve o mesmo resultado.
6. **Extrato único.** Toda perda/ganho com origem, valor e horário, incluindo abandono,
   desafios e reflexões. É a fonte da verdade para os tetos.
7. **Compatibilidade durante a transição.** Colunas novas nullable, RPCs novas aditivas, RPCs
   antigas funcionando até os dois aparelhos atualizarem. Migração primeiro, APK depois.
8. **Sem penalidade retroativa**, confirmado. Temporada em andamento termina nas regras atuais.

Ordem de execução do 0.4.0:

```
schema + RPC + testes pgTAP  →  app (UI e chamadas)  →  migração no Supabase  →  APK
```

---

## 14. Riscos

| Risco | Mitigação |
|---|---|
| Partículas travarem em aparelho fraco | teto de 160, Canvas único, teste em intermediário, desligar com bateria baixa |
| Sensor gastar bateria | só com tela visível, filtro passa-baixa, desligar em economia |
| Vidro "exagerado" virar enfeite | manter as regras: vidro só em controle, conteúdo sólido |
| APK não atualizar por assinatura | mesma keystore, testar install por cima da 0.2.0 antes de publicar |
| Escopo voltar a crescer | 0.3.0 não toca em banco; regra nova só em 0.4.0 |
| Regressão visual | capturas Roborazzi antes/depois |

---

## 15. Checklist de implementação (0.3.0)

- [ ] `ui/juice/Particulas.kt` (pool, loop, Canvas)
- [ ] `ui/juice/Emissores.kt` (moeda, confete, fagulha, brasa, cinza, brilho)
- [ ] `ui/juice/Juice.kt` + `LocalJuice` no `PrincipalTela` e `FimTemporadaTela`
- [ ] `ui/juice/Haptico.kt` e troca dos `HapticFeedbackType`
- [ ] `ui/juice/Inclinacao.kt` + `InclinacaoProvider` no `RivaisTema`
- [ ] `GlassSurface` v2 (reflexo, squish, pulso, grão, caustica)
- [ ] Cache do `RuntimeShader` em `Lente.kt`
- [ ] `BarraDividida` elástica e `pop` do placar
- [ ] Cartas: flip, foil, raridade
- [ ] Confete físico no fim de temporada
- [ ] Resumo do dia + marco de sequência
- [ ] Resetar temporada (RPC + Casa + confirmação)
- [ ] Testes unitários e capturas
- [ ] `versionCode 300` / `versionName 0.3.0`
- [ ] Build release assinado + teste de atualização sobre 0.2.0
- [ ] Tag `v0.3.0`
