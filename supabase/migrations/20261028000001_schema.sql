-- Rivais do Lar: tabelas base.
-- Campos de jogo (pontos, moedas, XP, placar, status de tarefa) só são escritos pelas funções
-- security definer de 20261028000002_regras.sql; os grants em 20261028000003_rls.sql garantem isso.

-- gen_random_uuid() é nativo do Postgres; pgcrypto (crypt) já vem no schema extensions do Supabase.
create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
  id        uuid primary key references auth.users (id) on delete cascade,
  nome      text not null check (char_length(nome) between 1 and 40),
  criado_em timestamptz not null default now()
);

create table public.casas (
  id             uuid primary key default gen_random_uuid(),
  nome           text not null check (char_length(nome) between 1 and 40),
  codigo_convite char(6) not null unique check (codigo_convite ~ '^[0-9]{6}$'),
  fuso           text not null default 'America/Sao_Paulo',
  modo_tregua    boolean not null default false,
  criada_em      timestamptz not null default now()
);

create table public.membros (
  casa_id           uuid not null references public.casas (id) on delete cascade,
  user_id           uuid not null references public.profiles (id) on delete cascade,
  cor               text not null check (cor in ('azul', 'laranja')),
  nivel             integer not null default 1,
  xp                integer not null default 0,
  moedas            integer not null default 0,
  sequencia_dias    integer not null default 0,
  ultimo_dia_ativo  date,
  nivel_provocacao  text not null default 'moderada'
                    check (nivel_provocacao in ('desligada', 'moderada', 'sem_piedade')),
  fcm_token         text,
  entrou_em         timestamptz not null default now(),
  primary key (casa_id, user_id),
  -- Cada pessoa joga em uma casa só; a cor identifica o jogador dentro da casa.
  unique (user_id),
  constraint membros_cor_unica unique (casa_id, cor) deferrable initially immediate
);

create table public.tarefas (
  id            uuid primary key default gen_random_uuid(),
  casa_id       uuid not null references public.casas (id) on delete cascade,
  titulo        text not null check (char_length(titulo) between 1 and 60),
  categoria     text not null default 'Limpeza' check (char_length(categoria) between 1 and 30),
  icone         text not null default 'cleaning_services' check (icone ~ '^[a-z0-9_]{1,40}$'),
  minutos       integer not null check (minutos between 1 and 480),
  esforco       smallint not null default 0 check (esforco in (0, 1, 2)),
  chatice       numeric(2, 1) not null default 1.0 check (chatice between 1.0 and 1.6),
  recorrencia   text not null default 'avulsa'
                check (recorrencia in ('avulsa', 'diaria', '3x_semana', '2x_semana', 'semanal', 'quinzenal', 'mensal')),
  prazo         timestamptz,
  status        text not null default 'livre' check (status in ('livre', 'reservada', 'aguardando')),
  reservada_por uuid references public.profiles (id) on delete set null,
  reservada_em  timestamptz,
  dias_parada   integer not null default 0 check (dias_parada >= 0),
  ativa         boolean not null default true,
  concluida_em  timestamptz,
  criada_por    uuid references public.profiles (id) on delete set null default auth.uid(),
  criada_em     timestamptz not null default now(),
  check ((status = 'livre') = (reservada_por is null))
);
create index tarefas_casa_idx on public.tarefas (casa_id) where ativa;

create table public.temporadas (
  id               uuid primary key default gen_random_uuid(),
  casa_id          uuid not null references public.casas (id) on delete cascade,
  numero           integer not null,
  inicio           timestamptz not null,
  fim              timestamptz not null,
  placar           jsonb not null default '{}'::jsonb,
  aposta           text check (char_length(aposta) <= 120),
  meta_alvo        integer not null default 2000 check (meta_alvo > 0),
  meta_atual       integer not null default 0,
  meta_premio      text check (char_length(meta_premio) <= 60),
  vencedor         uuid references public.profiles (id) on delete set null,
  premio_resgatado boolean not null default false,
  encerrada        boolean not null default false,
  encerrada_em     timestamptz,
  unique (casa_id, numero)
);
-- No máximo uma temporada aberta por casa.
create unique index temporadas_aberta_idx on public.temporadas (casa_id) where not encerrada;

create table public.registros (
  id              uuid primary key default gen_random_uuid(),
  casa_id         uuid not null references public.casas (id) on delete cascade,
  tarefa_id       uuid not null references public.tarefas (id) on delete cascade,
  user_id         uuid not null references public.profiles (id) on delete cascade,
  temporada_id    uuid references public.temporadas (id) on delete set null,
  titulo          text not null,
  pontos_base     integer not null,
  multiplicadores jsonb not null default '[]'::jsonb,
  pontos_finais   integer not null,
  moedas          integer not null,
  foto_path       text,
  status          text not null default 'pendente' check (status in ('pendente', 'validado', 'contestado')),
  comentario      text check (char_length(comentario) <= 200),
  contestado_por  uuid references public.profiles (id) on delete set null,
  contestado_em   timestamptz,
  validado_por    uuid references public.profiles (id) on delete set null,
  validado_em     timestamptz,
  criado_em       timestamptz not null default now()
);
create index registros_casa_idx on public.registros (casa_id, criado_em desc);
create index registros_pendentes_idx on public.registros (criado_em) where status = 'pendente';

create table public.cartas (
  id         uuid primary key default gen_random_uuid(),
  casa_id    uuid not null references public.casas (id) on delete cascade,
  dono       uuid not null references public.profiles (id) on delete cascade,
  tipo       text not null check (tipo in ('holofote', 'escudo', 'troca', 'dobradinha', 'hora_feliz', 'veto')),
  raridade   text not null check (raridade in ('comum', 'rara', 'epica')),
  comprada_em timestamptz not null default now(),
  usada_em   timestamptz
);

-- Perfil criado junto com a conta, com o nome enviado no cadastro (user_metadata.nome).
create function public._criar_profile()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.profiles (id, nome)
  values (
    new.id,
    left(coalesce(nullif(trim(new.raw_user_meta_data ->> 'nome'), ''), split_part(new.email, '@', 1), 'Jogador'), 40)
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public._criar_profile();
