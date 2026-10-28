# Rivais do Lar

A faxina virou campeonato. Jogo semanal de tarefas domésticas para duas pessoas: cada tarefa vale
pontos, a temporada fecha todo domingo às 22h, quem vencer leva a aposta da semana e existe uma meta
cooperativa que os dois batem juntos.

App Android nativo (Kotlin + Jetpack Compose) com backend no Supabase. O visual segue o protótipo
[`docs/conceito.html`](docs/conceito.html) (iOS 26 / Liquid Glass, tema preto) e as regras estão em
[`docs/PLANO.md`](docs/PLANO.md).

## O que tem

**Base (Fase 1)**
- Contas com e-mail e senha, confirmação e "esqueci minha senha" por código de 6 dígitos, sessão persistente.
- Criar casa (com ou sem tarefas de exemplo) e entrar por código de convite: 2 jogadores, cada um com sua cor.
- Tarefas próprias: nome, categoria (prontas ou inventadas por vocês), ícone, minutos, esforço, chatice,
  recorrência e prazo. Dá para editar e remover a qualquer hora (o histórico de pontos fica).
- Reservar → concluir → validar ou contestar (máximo 2 por semana); sem resposta em 12h vale sozinho.
- Placar ao vivo nos dois celulares (Realtime), cache offline (Room) e fila de ações feitas sem internet.
- Temporada semanal (fecha domingo 22h), aposta, resgate do prêmio e tela de fim de temporada.
- Atualização pelo GitHub Releases dentro do app.

**Fase 2**
- Tarefa bomba (+10% por dia de atraso, teto x2) e aviso quando vira bomba.
- Roubo: reserva parada 24h volta para a fila, quem largou perde a sequência e quem pegar ganha +25%;
  aviso 1h antes.
- Sequência (+5% por dia seguido, até x1,3), teto diário de 400 pontos, recuperação (mais de 20% atrás
  ganha moedas em dobro), meta da casa com aviso ao bater, modo trégua (placar pausa, conta só a meta).
- Notificações em 3 níveis (desligada, moderada, sem piedade) com tom de narrador implicante:
  virada, pedido de validação, bomba, roubável em 1h, fim de temporada, trocas e cartas. Sempre aparecem
  no sino do app; o push no celular vai pelo FCM (Edge Function `enviar-push`).
- Foto do depois (câmera ou galeria) no bucket privado `fotos`, mostrada na validação e apagada após
  30 dias (Edge Function `limpar-fotos`).
- Extrato do sheet de concluir calculado pelo servidor (`previa_conclusao`), igual ao que será gravado.
- Barra de abas que encolhe ao rolar.

**Fase 3**
- Loja e mão de cartas (até 3, uma por dia): Holofote, Escudo, Troca (com proposta, 1 recusa por
  semana, aceita sozinha em 12h), Dobradinha, Hora Feliz e Veto.
- Níveis (a cada 1.000 XP) e 13 conquistas, como Rei do Rejunte e Madrugador.
- Histórico com gráficos: pontos por temporada e a semana dia a dia, com toque para ler e visão em tabela.
- Widget do placar na tela inicial.

**Detalhes visuais**
- Vidro com refração na borda via shader AGSL (Android 13+), por cima do blur do Haze.
- Moedas voando até o contador quando os pontos entram; pontos sobem rolando; vibração ao concluir e validar.
- Tudo respeita "remover animações" e cai para vidro simples abaixo do Android 12 ou na economia de bateria.

## Estrutura

```
app/                      app Android (pacote com.rivaisdolar.app)
  src/main/java/.../
    domain/               Pontuacao.kt (espelho da fórmula do servidor, só para exibir) e formatos pt-BR
    data/model            modelos das tabelas
    data/remote           Supabase (auth, casa + Realtime + fila offline, atualização via GitHub)
    data/local            Room (cache e fila) e preferências
    ui/theme, components  tema preto, Inter, Material Symbols Rounded, GlassSurface (Haze) e controles
    ui/...                telas: placar, tarefas, sheets, fim de temporada, casa, editor, login
  src/test/               fórmula x grade do servidor, modelos, versões e capturas de tela (Robolectric)
supabase/
  migrations/             schema, regras (RPCs), RLS, crons e as Fases 2 e 3
  functions/              Edge Functions (Deno): enviar-push e limpar-fotos
  tests/database/         testes pgTAP (fórmula, fluxo, RLS, Fase 2, cartas)
  seed.sql                duas contas e uma casa de exemplo para o Supabase local
.github/workflows/        ci.yml (build, testes, db lint, pgTAP) e release.yml (APK assinado por tag)
```

## 1. Criar o projeto no Supabase

1. Crie um projeto em [supabase.com](https://supabase.com) (plano gratuito serve).
2. Instale a [Supabase CLI](https://supabase.com/docs/guides/cli) e aplique as migrações:
   ```bash
   supabase login
   supabase link --project-ref <ref-do-projeto>
   supabase db push
   ```
   Se o `db push` reclamar do `pg_cron`, ative a extensão em **Database → Extensions** e rode de novo.
3. Em **Authentication → Email Templates**, troque o link pelo código nos modelos **Confirm signup** e
   **Reset password**, usando `{{ .Token }}` (veja `supabase/templates/`). O app pede o código de 6
   dígitos em vez de abrir um link. Se preferir, desligue **Confirm email** em
   **Authentication → Providers → Email**.
4. Pegue a **Project URL** e a chave **anon / publishable** em **Project Settings → API**.
   A chave `service_role` nunca vai para o app.

### Push, fotos e rotinas (Fase 2)

O banco chama as Edge Functions pelo `pg_net`, com o endereço e um segredo guardados no Vault.

1. Ative a extensão **pg_net** em **Database → Extensions**.
2. Publique as funções:
   ```bash
   supabase functions deploy enviar-push --no-verify-jwt
   ```
   ```bash
   supabase functions deploy limpar-fotos --no-verify-jwt
   ```
3. Escolha um segredo longo qualquer e guarde nos dois lados:
   ```bash
   supabase secrets set CRON_SECRET=<segredo>
   ```
   No **SQL Editor**:
   ```sql
   select vault.create_secret('https://<ref>.supabase.co', 'rivais_url');
   select vault.create_secret('<segredo>', 'rivais_cron_secret');
   ```
4. Push no celular (opcional; sem isso as notificações ficam só no sino do app):
   - crie um projeto no [Firebase](https://console.firebase.google.com) e adicione um app Android com o
     pacote `com.rivaisdolar.app`;
   - em **Configurações do projeto → Contas de serviço**, gere uma chave privada (JSON) e rode
     `supabase secrets set FCM_SERVICE_ACCOUNT="$(cat conta-de-servico.json)"`;
   - guarde `FIREBASE_APP_ID` (mobilesdk_app_id), `FIREBASE_API_KEY` (current_key),
     `FIREBASE_PROJECT_ID` e `FIREBASE_SENDER_ID` (project_number) do `google-services.json` no
     `local.properties` e nos secrets do GitHub. O app não usa o `google-services.json` em si.

O bucket `fotos` e as políticas de acesso são criados pela migração.

## 2. Rodar no computador

Crie `local.properties` na raiz (fica fora do git):

```properties
sdk.dir=/caminho/do/Android/Sdk
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=<chave anon>
# opcionais, só para push:
FIREBASE_APP_ID=
FIREBASE_API_KEY=
FIREBASE_PROJECT_ID=
FIREBASE_SENDER_ID=
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug
```

Se o APK for gerado sem `SUPABASE_URL`/`SUPABASE_ANON_KEY`, o app pede o endereço e a chave na
primeira abertura.

Banco local, com dados de exemplo (`vitor@rivais.local` e `bia@rivais.local`, senha `senha123`):

```bash
supabase start
```

```bash
supabase db reset
```

```bash
supabase test db
```

## 3. Keystore (assinatura do APK)

Gere a chave uma única vez e guarde o arquivo e as senhas: um APK assinado com outra chave obriga a
desinstalar o app no celular.

```bash
keytool -genkeypair -v -keystore keystore/rivais-release.jks -alias rivais -keyalg RSA -keysize 4096 -validity 10000
```

Copie `keystore.properties.example` para `keystore.properties` e preencha. Com isso,
`./gradlew assembleRelease` gera `app/build/outputs/apk/release/app-release.apk` assinado. Sem
keystore, o release sai assinado com a chave de debug (instala, mas não serve para atualizar).

## 4. GitHub Actions como "bot" de distribuição

Em **Settings → Secrets and variables → Actions**, crie:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 keystore/rivais-release.jks` |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `rivais` |
| `KEY_PASSWORD` | senha da chave |
| `SUPABASE_URL` | URL do projeto |
| `SUPABASE_ANON_KEY` | chave anon |
| `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` | opcionais, para push |

Para publicar uma versão:

```bash
git tag v0.1.0
```

```bash
git push origin v0.1.0
```

O workflow `release.yml` compila o APK assinado e cria o release com o changelog. O app consulta
`https://api.github.com/repos/<rivais.updateRepo>/releases/latest` (definido em `gradle.properties`)
e oferece a versão nova. A API só enxerga releases de repositórios públicos; num repositório privado
a verificação simplesmente não encontra nada.

O `ci.yml` roda em todo push e PR: testes do app, APK de debug, checagem e testes das Edge Functions,
`supabase db lint` e os testes pgTAP.

## 5. Instalar nos dois celulares

1. Baixe o `.apk` do release (ou receba o arquivo) no celular.
2. Abra o arquivo e permita "instalar apps desconhecidos" para o navegador ou o gerenciador de arquivos.
3. No primeiro celular: crie a conta, crie a casa e toque em **Compartilhar código**.
4. No segundo: crie a conta e entre com o código de 6 dígitos.

## Testes

- `supabase/tests/database`: 137 asserções pgTAP. Fórmula (`20 min, médio, 1,3 = 40`;
  `45 min, pesado, 1,5 = 135`), fluxo completo com crons e fechamento, RLS (o cliente não altera moedas,
  XP, placar, meta, registros, reservas, cartas, efeitos nem trocas, e não enxerga outra casa), regras
  da Fase 2 (sequência, roubo, teto, recuperação, trégua, meta, níveis de notificação) e as seis cartas.
- `supabase/functions/_shared`: assinatura do JWT da conta de serviço do Google e formato das mensagens.
- `app/src/test`: a fórmula do app comparada com 5.544 combinações calculadas pelo Postgres
  (`resources/pontos_servidor.csv`, gerada por `supabase/tests/gerar_grade_pontos.sql`), leitura dos
  formatos do PostgREST, comparação de versões e capturas das telas em `app/build/outputs/roborazzi`.
