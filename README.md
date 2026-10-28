# Rivais do Lar

A faxina virou campeonato. Jogo semanal de tarefas domésticas para duas pessoas: cada tarefa vale
pontos, a temporada fecha todo domingo às 22h, quem vencer leva a aposta da semana e existe uma meta
cooperativa que os dois batem juntos.

App Android nativo (Kotlin + Jetpack Compose) com backend no Supabase. O visual segue o protótipo
[`docs/conceito.html`](docs/conceito.html) (iOS 26 / Liquid Glass, tema preto) e as regras estão em
[`docs/PLANO.md`](docs/PLANO.md).

## Estado: Fase 1 (MVP)

- Contas com e-mail e senha, confirmação e "esqueci minha senha" por código de 6 dígitos, sessão persistente.
- Criar casa e entrar por código de convite (máximo 2 jogadores, cada um com sua cor).
- Cadastro e edição de tarefas (minutos, esforço, chatice, categoria, ícone, recorrência, prazo).
- Reservar → concluir → validar ou contestar (máximo 2 por semana); sem resposta em 12h vale sozinho.
- Pontos, moedas, XP, placar e meta da casa calculados só no servidor (funções `security definer`);
  as políticas RLS e os grants impedem o app de gravar esses campos.
- Placar ao vivo nos dois celulares (Supabase Realtime), cache offline com Room e fila de ações
  feitas sem internet.
- Temporada semanal: fecha domingo 22h (fuso da casa), define vencedor, abre a próxima. Aposta e
  resgate do prêmio. Tela de fim de temporada.
- Crons (`pg_cron`): fechar temporadas, contar dias de tarefa bomba, liberar reservas paradas há 24h,
  validar pendentes há 12h.
- Atualização pelo GitHub Releases: o app confere o último release ao abrir e oferece baixar e instalar.

Ficam para a Fase 2: notificações FCM, foto do depois, roubo com bônus de 25%, sequência, recuperação,
teto diário e efeito do modo trégua (o interruptor já grava a escolha). Cartas, conquistas, histórico
e widget são da Fase 3.

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
  migrations/             schema, regras (RPCs), RLS e crons
  tests/database/         testes pgTAP (fórmula, fluxo completo, RLS)
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

## 2. Rodar no computador

Crie `local.properties` na raiz (fica fora do git):

```properties
sdk.dir=/caminho/do/Android/Sdk
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_ANON_KEY=<chave anon>
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

O `ci.yml` roda em todo push e PR: testes do app, APK de debug, `supabase db lint` e os testes pgTAP.

## 5. Instalar nos dois celulares

1. Baixe o `.apk` do release (ou receba o arquivo) no celular.
2. Abra o arquivo e permita "instalar apps desconhecidos" para o navegador ou o gerenciador de arquivos.
3. No primeiro celular: crie a conta, crie a casa e toque em **Compartilhar código**.
4. No segundo: crie a conta e entre com o código de 6 dígitos.

## Testes

- `supabase/tests/database`: 73 asserções pgTAP. Fórmula (`20 min, médio, 1,3 = 40`;
  `45 min, pesado, 1,5 = 135`), fluxo completo com crons e fechamento, e RLS: o cliente não altera
  moedas, XP, nível, placar, meta, vencedor, registros, reservas nem bônus bomba, e não enxerga outra casa.
- `app/src/test`: a fórmula do app comparada com 5.544 combinações calculadas pelo Postgres
  (`resources/pontos_servidor.csv`, gerada por `supabase/tests/gerar_grade_pontos.sql`), leitura dos
  formatos do PostgREST, comparação de versões e capturas das telas em `app/build/outputs/roborazzi`.
