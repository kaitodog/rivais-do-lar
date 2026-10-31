-- 0.4.0: vistorias (flagras) com multa imediata e catálogo editável por casa.
-- Sem prazo de conserto e sem contestação automática: o flagra vale na hora.

alter table public.casas add column vistorias_ativas boolean not null default true;
grant update (vistorias_ativas) on public.casas to authenticated;

create table public.ocorrencia_tipos (
  id         uuid primary key default gen_random_uuid(),
  casa_id    uuid not null references public.casas (id) on delete cascade,
  nome       text not null check (char_length(nome) between 2 and 40),
  categoria  text not null default 'Geral' check (char_length(categoria) between 2 and 30),
  pontos     integer not null check (pontos in (5, 10, 15)),
  icone      text not null default 'report' check (icone ~ '^[a-z0-9_]{1,40}$'),
  ativo      boolean not null default true,
  criado_em  timestamptz not null default now(),
  unique (casa_id, nome)
);
create index ocorrencia_tipos_casa_idx on public.ocorrencia_tipos (casa_id, categoria, nome);
alter table public.ocorrencia_tipos enable row level security;

create policy "ocorrencia_tipos: membros leem" on public.ocorrencia_tipos
  for select to authenticated using (public.is_membro(casa_id));
create policy "ocorrencia_tipos: membros criam" on public.ocorrencia_tipos
  for insert to authenticated with check (public.is_membro(casa_id));
create policy "ocorrencia_tipos: membros editam" on public.ocorrencia_tipos
  for update to authenticated using (public.is_membro(casa_id)) with check (public.is_membro(casa_id));
create policy "ocorrencia_tipos: membros removem" on public.ocorrencia_tipos
  for delete to authenticated using (public.is_membro(casa_id));

grant select, insert, update, delete on public.ocorrencia_tipos to authenticated;

create table public.ocorrencias (
  id              uuid primary key default gen_random_uuid(),
  casa_id         uuid not null references public.casas (id) on delete cascade,
  autor           uuid not null references public.profiles (id) on delete cascade,
  alvo            uuid not null references public.profiles (id) on delete cascade,
  tipo_id         uuid references public.ocorrencia_tipos (id) on delete set null,
  tipo_nome       text not null,
  tipo_categoria  text not null,
  pontos          integer not null check (pontos between 5 and 15),
  foto_path       text not null,
  temporada_id    uuid references public.temporadas (id) on delete set null,
  criada_em       timestamptz not null default now(),
  check (autor <> alvo)
);
create index ocorrencias_casa_idx on public.ocorrencias (casa_id, criada_em desc);
create index ocorrencias_alvo_idx on public.ocorrencias (casa_id, alvo, criada_em desc);
alter table public.ocorrencias enable row level security;

create policy "ocorrencias: membros leem" on public.ocorrencias
  for select to authenticated using (public.is_membro(casa_id));

grant select on public.ocorrencias to authenticated;

-- Catálogo padrão (o app permite editar/remover/adicionar depois).
create function public._semear_vistorias(p_casa uuid)
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
  insert into public.ocorrencia_tipos (casa_id, nome, categoria, pontos, icone) values
    (p_casa, 'Roupa fora do lugar', 'Quarto',    10, 'checkroom'),
    (p_casa, 'Cama desfeita',       'Quarto',     5, 'bed'),
    (p_casa, 'Louça na pia',        'Cozinha',   10, 'restaurant'),
    (p_casa, 'Lixo cheio',          'Cozinha',    5, 'delete'),
    (p_casa, 'Comida aberta',       'Cozinha',   10, 'kitchen'),
    (p_casa, 'Banheiro molhado',    'Banheiro',  10, 'water_drop'),
    (p_casa, 'Cabelo no ralo',      'Banheiro',  10, 'shower'),
    (p_casa, 'Papel acabou',        'Banheiro',   5, 'wc'),
    (p_casa, 'Sapato no meio',      'Sala',       5, 'directions_walk'),
    (p_casa, 'Luz acesa',           'Sala',       5, 'lightbulb'),
    (p_casa, 'Bagunça acumulada',   'Geral',     15, 'cleaning_services'),
    (p_casa, 'Infração grave',      'Geral',     15, 'warning')
  on conflict (casa_id, nome) do nothing;
$$;

create function public._trigger_semear_vistorias()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public._semear_vistorias(new.id);
  return new;
end;
$$;

create trigger casas_semear_vistorias
  after insert on public.casas
  for each row execute function public._trigger_semear_vistorias();

-- Casas que já existiam recebem o catálogo padrão.
select public._semear_vistorias(id) from public.casas;

-- Flagra: valida casa/tipo/alvo, aplica a multa na hora e registra no B.O.
create function public.registrar_ocorrencia(p_tipo uuid, p_foto_path text)
returns public.ocorrencias
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid          uuid := auth.uid();
  v_casa         uuid := public.minha_casa();
  v_casa_row     public.casas;
  v_tipo         public.ocorrencia_tipos;
  v_alvo         uuid;
  v_temp         public.temporadas;
  v_inicio_dia   timestamptz;
  v_perdidos_dia integer;
  v_perdidos_temp integer;
  v_qtd_autor    integer;
  v_ocorrencia   public.ocorrencias;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;

  select * into v_casa_row from public.casas where id = v_casa;
  if not v_casa_row.vistorias_ativas then
    perform public._erro('As vistorias estão desligadas nesta casa.');
  end if;

  select * into v_tipo
    from public.ocorrencia_tipos
   where id = p_tipo and casa_id = v_casa and ativo;
  if not found then
    perform public._erro('Essa vistoria não existe mais.');
  end if;

  if p_foto_path is null or split_part(p_foto_path, '/', 1) <> v_casa::text then
    perform public._erro('A foto do flagra é obrigatória.');
  end if;

  v_alvo := public._outro_membro(v_casa, v_uid);
  if v_alvo is null then
    perform public._erro('Você precisa de outra pessoa na casa para flagrar.');
  end if;

  v_temp := public._temporada_atual(v_casa);
  v_inicio_dia := date_trunc('day', now() at time zone v_casa_row.fuso) at time zone v_casa_row.fuso;

  select count(*) into v_qtd_autor
    from public.ocorrencias
   where casa_id = v_casa and autor = v_uid and criada_em >= v_inicio_dia;
  if v_qtd_autor >= 2 then
    perform public._erro('Duas vistorias por dia. Amanhã tem mais.');
  end if;

  if exists (
    select 1 from public.ocorrencias
     where casa_id = v_casa and alvo = v_alvo and tipo_id = p_tipo and criada_em >= v_inicio_dia
  ) then
    perform public._erro('Essa vistoria já foi registrada hoje.');
  end if;

  select coalesce(sum(pontos), 0) into v_perdidos_dia
    from public.ocorrencias
   where casa_id = v_casa and alvo = v_alvo and criada_em >= v_inicio_dia;
  select coalesce(sum(pontos), 0) into v_perdidos_temp
    from public.ocorrencias
   where casa_id = v_casa and alvo = v_alvo and temporada_id = v_temp.id;

  if v_perdidos_dia + v_tipo.pontos > 50 then
    perform public._erro('O limite de 50 pontos de perda por dia já foi atingido.');
  end if;
  if v_perdidos_temp + v_tipo.pontos > 120 then
    perform public._erro('O limite de 120 pontos de perda na temporada já foi atingido.');
  end if;

  insert into public.ocorrencias
    (casa_id, autor, alvo, tipo_id, tipo_nome, tipo_categoria, pontos, foto_path, temporada_id)
  values
    (v_casa, v_uid, v_alvo, v_tipo.id, v_tipo.nome, v_tipo.categoria, v_tipo.pontos, p_foto_path, v_temp.id)
  returning * into v_ocorrencia;

  update public.temporadas
     set placar = jsonb_set(
       placar,
       array[v_alvo::text],
       to_jsonb(greatest(0, coalesce((placar ->> v_alvo::text)::integer, 0) - v_tipo.pontos))
     )
   where id = v_temp.id;

  perform public._notificar(
    v_alvo,
    'vistoria',
    'Flagra: ' || v_tipo.nome,
    '−' || v_tipo.pontos || ' pontos. B.O. Doméstico.',
    'Flagra: ' || v_tipo.nome || '. −' || v_tipo.pontos || '.',
    'moderada',
    jsonb_build_object('ocorrencia', v_ocorrencia.id)
  );

  return v_ocorrencia;
end;
$$;

revoke all on function public.registrar_ocorrencia(uuid, text) from public;
grant execute on function public.registrar_ocorrencia(uuid, text) to authenticated;

-- O reset da temporada também limpa o B.O. e as multas daquela temporada.
create or replace function public.reiniciar_temporada()
returns public.temporadas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid  uuid := auth.uid();
  v_casa uuid := public.minha_casa();
  v_temp public.temporadas;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;

  v_temp := public._temporada_atual(v_casa);

  with pendentes as (
    select id, tarefa_id
      from public.registros
     where casa_id = v_casa
       and status = 'pendente'
       and criado_em >= v_temp.inicio
       and criado_em <= v_temp.fim
  ), libertadas as (
    update public.tarefas t
       set status = 'livre',
           reservada_por = null,
           reservada_em = null,
           concluida_em = null
      from pendentes p
     where t.id = p.tarefa_id
     returning t.id
  )
  delete from public.registros r
   using pendentes p
   where r.id = p.id;

  delete from public.ocorrencias
   where casa_id = v_casa and temporada_id = v_temp.id;

  update public.temporadas
     set placar = '{}'::jsonb,
         meta_atual = 0,
         vencedor = null,
         premio_resgatado = false
   where id = v_temp.id
   returning * into v_temp;

  perform public._notificar(
    v_uid, 'reset', 'Temporada zerada',
    'O placar, a meta e o B.O. voltaram a zero. Tarefas, moedas, XP e cartas continuam.',
    'Resetou a temporada. Agora é faxina limpa.',
    'moderada'
  );
  perform public._notificar(
    public._outro_membro(v_casa, v_uid), 'reset', 'Temporada zerada',
    'O placar, a meta e o B.O. voltaram a zero. Tarefas, moedas, XP e cartas continuam.',
    'Resetou a temporada. Agora é faxina limpa.',
    'moderada'
  );

  return v_temp;
end;
$$;

revoke all on function public.reiniciar_temporada() from public;
grant execute on function public.reiniciar_temporada() to authenticated;
