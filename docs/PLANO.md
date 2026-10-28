# Projeto: Rivais do Lar (app Android, distribuído como APK)

Você vai construir do zero um app Android nativo chamado **Rivais do Lar**: um jogo semanal de tarefas domésticas entre duas pessoas (um casal). Cada tarefa vale pontos, a temporada fecha todo domingo às 22h, quem vencer escolhe o prêmio da semana, e existe uma meta cooperativa que os dois batem juntos.

O arquivo `docs/conceito.html` (anexo) é o protótipo visual de referência. Abra no navegador antes de começar e siga o visual das 4 telas de celular dele.

Trabalhe em fases. **Comece só pela Fase 1 (MVP)**, deixe o projeto compilando e gerando APK, e me mostre antes de seguir. Ao fim de cada fase, rode o build, corrija erros e me diga o que testar.

---

## 1. Stack e decisões técnicas

- **Linguagem/UI:** Kotlin + Jetpack Compose, arquitetura MVVM com `ViewModel` + `StateFlow`, injeção com Hilt, navegação com Navigation Compose.
- **SDK:** `minSdk 26`, `targetSdk` e `compileSdk` na versão estável mais recente.
- **Backend hospedado: Supabase (plano gratuito)**, acessado pelo SDK `supabase-kt` (Auth, Postgrest, Realtime, Storage, Functions).
  - **Contas próprias:** cadastro com nome, e-mail e senha, login, "esqueci minha senha" e sessão persistente. Sem login social no MVP.
  - **Postgres:** todas as tabelas com **Row Level Security** ativada. Cada usuário só lê e escreve dados da casa da qual é membro.
  - **Realtime:** placar, tarefas e registros atualizam ao vivo nos dois celulares.
  - **Lógica no servidor:** todo cálculo de pontos, moedas, XP, placar e meta da casa acontece em **funções Postgres (RPC, `security definer`)** ou **Edge Functions**. O app **nunca** grava esses campos diretamente; as políticas RLS bloqueiam isso.
  - **Agendamentos:** `pg_cron` no Supabase para: fechar a temporada domingo 22h (America/Sao_Paulo), incrementar dias de tarefa bomba, liberar reservas vencidas (roubo) e validar automaticamente registros pendentes há mais de 12h.
  - **Storage:** bucket privado para fotos do depois (Fase 2), apagadas após 30 dias.
  - **Migrações:** todo o schema, RLS, funções e crons versionados em `supabase/migrations/*.sql` e aplicados com a Supabase CLI. Inclua um `seed.sql` com tarefas de exemplo.
  - Credenciais (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) lidas de `local.properties` via `BuildConfig`, nunca commitadas. A `service_role` key nunca vai para o app.
- **Notificações push (Fase 2):** Firebase Cloud Messaging usado **só** para entrega de push. Uma Edge Function do Supabase envia as mensagens (FCM HTTP v1) disparada por triggers/cron.
- **Offline:** cache local com Room; ações feitas offline entram numa fila e sincronizam quando voltar a conexão.
- **GitHub como "bot" de distribuição:**
  - Workflow do GitHub Actions que, a cada tag `v*`, compila o **APK release assinado** (keystore e senhas em GitHub Secrets, keystore em base64) e publica no **GitHub Releases** com changelog.
  - Workflow de CI em cada push/PR: build + testes unitários + `supabase db lint`.
  - O app verifica, ao abrir, o último release via API do GitHub e oferece baixar e instalar o APK novo (permissão `REQUEST_INSTALL_PACKAGES`).
  - Localmente, `signingConfigs` lê `keystore.properties` (fora do git, com `keystore.properties.example` no repo).
- **README:** como criar o projeto no Supabase, aplicar as migrações, configurar os secrets do GitHub, gerar o keystore e instalar o APK nos dois celulares.

## 2. Modelo de dados (Postgres / Supabase)

```
profiles      id (= auth.users.id), nome, criado_em
casas         id, nome, codigo_convite (6 dígitos, único), fuso ('America/Sao_Paulo'),
              modo_tregua (bool), criada_em
membros       casa_id, user_id, cor ('azul' | 'laranja'), nivel, xp, moedas,
              sequencia_dias, ultimo_dia_ativo, nivel_provocacao, fcm_token
              (máximo 2 membros por casa)
tarefas       id, casa_id, titulo, categoria, icone, minutos, esforco (0|1|2),
              chatice (1.0 a 1.6), recorrencia, prazo, reservada_por, reservada_em,
              dias_parada, ativa
registros     id, casa_id, tarefa_id, user_id, pontos_base, multiplicadores (jsonb),
              pontos_finais, moedas, foto_path, status ('pendente'|'validado'|'contestado'),
              comentario, criado_em, validado_em
temporadas    id, casa_id, inicio, fim, placar (jsonb {user_id: pontos}), aposta,
              meta_alvo, meta_atual, meta_premio, vencedor, premio_resgatado, encerrada
cartas        id, casa_id, dono, tipo, raridade, comprada_em, usada_em
```

Funções RPC mínimas: `criar_casa`, `entrar_casa(codigo)`, `reservar_tarefa`, `concluir_tarefa`, `validar_registro`, `contestar_registro`, `definir_aposta`, `resgatar_premio`, `comprar_carta`, `usar_carta`. Cada uma valida as regras do jogo no servidor (teto diário, limite de contestações, reserva do outro, etc.).

## 3. Regras do jogo

### Pontuação
```
pontos = minutos × esforço × chatice × bônusBomba × multiplicadoresDoJogador
```
- esforço: Leve ×1,0 · Médio ×1,5 · Pesado ×2,0
- chatice: ×1,0 a ×1,6 (passo 0,1)
- bônusBomba: `min(1 + 0,1 × diasParada, 2,0)`
- arredondar para o múltiplo de 5 mais próximo, mínimo 5
- moedas = `floor(pontos / 10)`
- XP = pontos (XP nunca zera; nível sobe a cada 1000 XP)

Exemplos para teste unitário: louça 20 min, médio, chatice 1,3 → 40 pts. Banheiro 45 min, pesado, chatice 1,5 → 135 pts.

### Fluxo de uma tarefa
1. **Livre** → alguém toca em "Reservar" → fica **reservada** por aquela pessoa.
2. Pessoa marca como concluída (foto do depois opcional) → cria `registro` **pendente**.
3. A outra pessoa **valida** com um toque, ou **contesta** com comentário (volta para a fila). Sem resposta em 12h, valida automaticamente.
4. Na validação, a função do servidor calcula os pontos finais e atualiza placar, moedas, XP e meta da casa.

### Mecânicas
- **Temporada semanal:** começa segunda 00:00 e fecha domingo 22:00 (fuso da casa). No fechamento: define vencedor, registra histórico, zera placar e cria a próxima temporada.
- **Aposta:** texto livre definido na segunda (ex.: "vencedor escolhe o restaurante"). Vencedor marca como resgatado.
- **Tarefa bomba:** tarefa com prazo vencido incrementa `diasParada` diariamente (+10% por dia, teto ×2).
- **Roubo:** reserva sem conclusão em 24h volta a ficar livre; quem concluir ganha +25%, e quem largou perde a sequência.
- **Sequência:** pelo menos 1 tarefa validada por dia soma +5% por dia, até ×1,3. Dia sem tarefa zera.
- **Meta da casa:** soma dos pontos dos dois na semana. Bateu o alvo, os dois ganham o prêmio coletivo.
- **Recuperação:** quem estiver mais de 20% atrás no placar ganha moedas em dobro (não pontos).
- **Teto diário:** 400 pontos por pessoa por dia.
- **Contestação:** máximo 2 por pessoa por semana.
- **Modo trégua:** pausa a competição; tarefas continuam contando só para a meta da casa.

### Cartas de poder (Fase 3)
Mão máxima de 3 cartas, 1 uso por dia.

| Carta | Raridade | Custo | Efeito |
|---|---|---|---|
| Holofote | Comum | 40 | Transforma uma tarefa em bomba agora |
| Escudo | Comum | 50 | Reserva não pode ser roubada por 24h |
| Troca | Rara | 60 | Troca uma tarefa sua por uma do outro (ele pode recusar 1× por semana) |
| Dobradinha | Rara | 80 | Próxima tarefa vale ×2 |
| Hora Feliz | Épica | 90 | Por 1h tudo vale ×1,5 |
| Veto | Épica | 120 | Você não pega a tarefa mais odiada da semana |

### Notificações (Fase 2)
Três níveis configuráveis: desligada, moderada, sem piedade. Eventos: virada no placar, pedido de validação, tarefa virou bomba, tarefa roubável em 1h, fim de temporada. Tom de narrador esportivo implicante, por exemplo: "Virada! Ela passou na frente lavando o banheiro."

## 4. Design (siga à risca)

Visual inspirado no **iOS 26 / Liquid Glass da Apple**, em **tema preto único**. Nada de Material You padrão, nada de emoji em lugar nenhum.

### Regra principal do Liquid Glass
- **Vidro só na camada de navegação e controles:** tab bar flutuante, botão "+" flutuante, botões circulares do topo, bottom sheets e segmented control.
- **Conteúdo é sólido:** placar, listas e cartões usam superfícies opacas.
- **Nunca vidro sobre vidro.** Dentro de um sheet de vidro, o conteúdo é só texto e divisórias.
- **Cor com moderação:** ícones e textos sobre o vidro são monocromáticos. Só a ação principal recebe vidro tingido.

### Cores
| Token | Valor | Uso |
|---|---|---|
| background | `#000000` | fundo de todas as telas |
| surface | `#1C1C1E` | cartões e listas |
| surfaceFill | `#2C2C2E` / `rgba(120,120,128,.24)` | fundo de ícones, trilhas de barra |
| label | `#FFFFFF` | texto principal |
| secondaryLabel | `rgba(235,235,245,.6)` | subtítulos |
| separator | `rgba(84,84,88,.6)` | divisórias (0,5dp) |
| player1 | `#0A84FF` | jogador 1 |
| player2 | `#FF9F0A` | jogador 2 |
| alert | `#FF453A` | **somente** tarefa bomba / atraso |
| reward | `#E6B422` | **somente** recompensa, prêmio, cartas épicas, troféu |

A cor de destaque do app (botão "+", aba ativa, ação principal) é a **cor do jogador logado**.

### Tipografia
Use a fonte **Inter** (empacotada em `res/font`), que é a mais próxima do SF Pro:
- Título grande: 31sp, Bold, letterSpacing -0,035em, alinhado à esquerda.
- Números do placar: 36sp, Bold, letterSpacing -0,05em, algarismos tabulares (`fontFeatureSettings = "tnum"`).
- Linha de lista: 15sp Medium; subtítulo 12sp Regular secondaryLabel.
- Cabeçalho de seção: 12sp Medium, maiúsculas, secondaryLabel.

### Ícones
Material Symbols **Rounded**, estilo outline, peso 400, monocromáticos. Ícone de tarefa dentro de um quadrado 32dp com cantos 9dp e fundo surfaceFill.

### Componente de vidro (`GlassSurface`)
Crie um composable reutilizável:
- Blur real do conteúdo por trás com a biblioteca **Haze** (`dev.chrisbanes.haze`), raio ~20dp, saturação aumentada.
- Fundo `rgba(44,44,48,.42)` por cima do blur.
- Borda de 1dp com gradiente diagonal (branco 50% no canto superior esquerdo, transparente no meio, branco 25% no inferior direito), simulando reflexo especular.
- Brilho interno suave no topo (radial branco 14%).
- Sombra externa difusa.
- Variante `tinted` (cor do jogador a ~82% de opacidade, texto branco) para ação principal.
- No Android 13+, opcionalmente um `RuntimeShader` (AGSL) para leve distorção na borda.
- **Fallback:** abaixo do Android 12, ou com "reduzir transparência"/economia de bateria, usar superfície `#2C2C2E` com 92% de opacidade e a mesma borda.

### Formas
- Controles em **cápsula** (raio 50%). Botões do topo circulares de 38dp.
- Cartões e listas com raio 22dp.
- Bottom sheet com margem de 7dp das bordas e raio grande (~38dp), concêntrico com a tela.

### Telas (MVP)

**Placar (aba 1)**
- Topo: dois botões circulares de vidro à direita (notificações e perfil/Casa).
- Título grande "Placar" + subtítulo "Temporada 7 · fecha domingo às 22h".
- Cartão de placar (sólido): avatar circular colorido com inicial + nome de cada jogador, pontos grandes, selo "Na frente" no líder, "vs" no centro, barra dividida nas duas cores proporcional ao placar, rodapé com sequência e diferença.
- Cartão "Meta da casa": título com o prêmio coletivo, contagem `atual / alvo`, barra branca.
- Seção "HOJE": lista agrupada com as tarefas do dia. Tarefa bomba com ícone e pontos em vermelho e subtítulo "Bomba · parada há N dias". Tarefa reservada mostra um ponto na cor do dono.

**Tarefas (aba 2)**
- Título grande, botões de busca e filtro no topo.
- Segmented control: Livres / Minhas / Dela.
- Seções "Reservadas" e "Livres". Reservada pelo outro mostra cronômetro em vermelho: "Roubável em 5h 42min".
- Toque na linha abre o detalhe/ações (reservar, concluir, editar).

**Concluir tarefa (bottom sheet de vidro)**
- Fundo da tela escurecido.
- Puxador, botão fechar circular, título da tarefa.
- Área para foto do depois (opcional, câmera ou galeria; envio para o Supabase Storage na Fase 2).
- Extrato da conta: base com a fórmula, cada multiplicador em uma linha.
- Total grande ("+95") e moedas.
- Botão cápsula de vidro tingido "Enviar pra validar" + texto "Ela confirma com um toque. Sem resposta em 12h, vale sozinho."

**Fim de temporada (tela cheia)**
- Raios dourados girando lentamente atrás de um troféu dourado (gradiente), confete branco e dourado.
- "Vitória!" grande + "Temporada 7 · 1.410 a 1.335". Para quem perdeu, versão equivalente sem dourado ("Quase! Semana que vem é sua").
- Cartão do prêmio a resgatar, cartão da meta da casa, mão de cartas, botão cápsula dourado "Definir aposta da próxima".

**Cartas (aba 3):** Fase 3. No MVP, tela placeholder com "Em breve".

**Casa (via botão de perfil):** perfil e sair da conta, criar casa, entrar por código de 6 dígitos, nome e cor de cada jogador, cadastro/edição de tarefas (título, categoria, ícone, minutos, esforço, chatice, recorrência), preferências de notificação, modo trégua.

### Navegação
- Tab bar flutuante de vidro em cápsula com 3 abas (Placar, Tarefas, Cartas), ícone + rótulo; a aba ativa tem uma cápsula interna levemente mais clara e ícone na cor do jogador.
- Ao lado, botão circular "+" de vidro tingido (nova tarefa).
- A lista rola **por baixo** da tab bar, com um degradê preto na borda inferior.
- A tab bar encolhe (só ícones, mais estreita) ao rolar para baixo e volta ao rolar para cima.

### Movimento
- Pontos sobem com contador rolando quando um registro é validado.
- Moedas "voam" até o contador.
- Vibração curta (haptic) em conclusão e validação.
- Respeitar a configuração de reduzir animações do sistema.

## 5. Fases

**Fase 1 (MVP):** setup do projeto e do Supabase (migrações, RLS, RPCs, crons), tema e `GlassSurface`, telas de cadastro/login/recuperar senha, criar/entrar em casa por código, CRUD de tarefas, reservar → concluir → validar/contestar, cálculo de pontos no servidor com testes, placar semanal em tempo real, fechamento de temporada, aposta, telas Placar, Tarefas, Concluir e Fim de temporada, workflows do GitHub Actions (CI e release do APK), README.

**Fase 2:** tarefa bomba, roubo, sequência, meta da casa, recuperação, teto diário, modo trégua, notificações FCM com os 3 níveis, foto do depois, tab bar que encolhe.

**Fase 3:** loja e uso de cartas, níveis e conquistas (ex.: "Rei do Rejunte" = 10 banheiros, "Madrugador" = tarefa antes das 7h), histórico com gráficos, widget do placar na tela inicial.

## 6. Critérios de pronto (Fase 1)
- `./gradlew assembleRelease` gera APK assinado, e uma tag `v0.1.0` faz o GitHub Actions publicar o APK no Releases.
- Dá pra criar conta com e-mail e senha, sair e entrar de novo.
- Dois celulares logados na mesma casa veem o placar atualizar em tempo real.
- Testes unitários da fórmula de pontos passando (no app e nas funções SQL do servidor).
- As políticas RLS impedem o cliente de alterar pontos, moedas, XP e placar (teste isso explicitamente).
- Nenhum emoji na interface; todas as telas em tema preto conforme a seção 4.
