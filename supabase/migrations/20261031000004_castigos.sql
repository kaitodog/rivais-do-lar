-- 0.4.2: Castigo da Casa e Vale-favor.
-- Castigo é uma carta separada: o alvo tem até 24h para cumprir ou perde 15 pontos.
-- Vale-favor é social: não tira ponto, só registra a dívida de um favor.

create table public.castigos (
  id           uuid primary key default gen_random_uuid(),
  casa_id      uuid not null references public.casas (id) on delete cascade,
  autor        uuid not null references public.profiles (id) on delete cascade,
  alvo         uuid not null references public.profiles (id) on delete cascade,
  descricao    text not null check (char_length(descricao) between 3 and 80),
  pontos       integer not null default 15 check (pontos between 5 and 15),
  prazo        timestamptz not null,
  status       text not null default 'pendente' check (status in ('pendente', 'cumprido', 'falhou')),
  criado_em    timestamptz not null default now(),
  resolvido_em timestamptz,
  check (autor <> alvo)
);
create index castigos_casa_idx on public.castigos (casa_id, status, prazo);
alter table public.castigos enable row level security;
create policy "castigos: membros leem" on public.castigos
  for select to authenticated using (public.is_membro(casa_id));
grant select on public.castigos to authenticated;

create table public.vales (
  id           uuid primary key default gen_random_uuid(),
  casa_id      uuid not null references public.casas (id) on delete cascade,
  autor        uuid not null references public.profiles (id) on delete cascade,
  alvo         uuid not null references public.profiles (id) on delete cascade,
  descricao    text not null check (char_length(descricao) between 3 and 80),
  status       text not null default 'pendente' check (status in ('pendente', 'cumprido')),
  criado_em    timestamptz not null default now(),
  resolvido_em timestamptz,
  check (autor <> alvo)
);
create index vales_casa_idx on public.vales (casa_id, status);
alter table public.vales enable row level security;
create policy "vales: membros leem" on public.vales
  for select to authenticated using (public.is_membro(casa_id));
grant select on public.vales to authenticated;

create or replace function public.carta_custo(p_tipo text)
returns integer
language sql
immutable
as $$
  select case p_tipo
    when 'holofote' then 15 when 'escudo' then 20 when 'troca' then 25
    when 'dobradinha' then 40 when 'hora_feliz' then 45 when 'veto' then 30
    when 'cafe_passado' then 15 when 'seguro' then 15
    when 'balde' then 20 when 'cobranca' then 30
    when 'castigo' then 20 when 'vale' then 10
  end
$$;

create or replace function public.carta_raridade(p_tipo text)
returns text
language sql
immutable
as $$
  select case
    when p_tipo in ('holofote', 'escudo', 'cafe_passado', 'seguro', 'balde', 'castigo', 'vale') then 'comum'
    when p_tipo in ('troca', 'dobradinha', 'cobranca') then 'rara'
    when p_tipo in ('hora_feliz', 'veto') then 'epica'
    when p_tipo = 'arrastao' then 'decisiva'
  end
$$;

-- Castigo: exige uma carta na mão e consome a jogada do dia.
create function public.usar_castigo(p_descricao text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_carta  public.cartas;
  v_casa   public.casas;
  v_outro  uuid;
  v_membro public.membros;
begin
  select * into v_carta
    from public.cartas
   where dono = v_uid and tipo = 'castigo' and usada_em is null
   order by comprada_em
   limit 1
   for update;
  if not found then
    perform public._erro('Você não tem um Castigo na mão.');
  end if;
  select * into v_casa from public.casas where id = v_carta.casa_id;
  if v_casa.modo_tregua then
    perform public._erro('Modo trégua: as cartas estão guardadas até a competição voltar.');
  end if;
  if exists (select 1 from public.cartas where dono = v_uid and usada_em is not null
               and (usada_em at time zone v_casa.fuso)::date = public._hoje(v_casa.id)) then
    perform public._erro('Uma carta por dia. Amanhã tem mais.');
  end if;
  v_outro := public._outro_membro(v_casa.id, v_uid);
  if v_outro is null then
    perform public._erro('Você precisa de outra pessoa na casa.');
  end if;
  if char_length(trim(p_descricao)) < 3 then
    perform public._erro('Descreva o castigo em pelo menos 3 letras.');
  end if;

  select * into v_membro from public.membros where user_id = v_outro for update;
  if v_membro.seguro_ate > now() then
    update public.membros set seguro_ate = null where user_id = v_outro;
    update public.cartas set usada_em = now(), alvo = v_outro where id = v_carta.id;
    perform public._notificar(v_outro, 'carta', 'Seguro bloqueou o Castigo',
      public._nome(v_uid) || ' tentou mandar um castigo, mas seu Seguro bloqueou.',
      public._nome(v_uid) || ' tentou te dar um castigo. Seu Seguro bloqueou. Ufa.', 'sem_piedade');
    return;
  end if;

  insert into public.castigos (casa_id, autor, alvo, descricao, prazo)
  values (v_casa.id, v_uid, v_outro, trim(p_descricao), now() + interval '24 hours');
  update public.cartas set usada_em = now(), alvo = v_outro where id = v_carta.id;
  perform public._notificar(v_outro, 'castigo', 'Castigo da Casa',
    public._nome(v_uid) || ' te deu um castigo: ' || trim(p_descricao) || '. Cumpra em 24h ou perca 15 pontos.',
    public._nome(v_uid) || ' te deu um castigo: ' || trim(p_descricao) || '. 24h pra resolver, ou −15.', 'sem_piedade');
end;
$$;

create function public.cumprir_castigo(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_castigo public.castigos;
begin
  select * into v_castigo from public.castigos where id = p_id and alvo = auth.uid() for update;
  if not found or v_castigo.status <> 'pendente' then
    perform public._erro('Esse castigo não está mais pendente.');
  end if;
  update public.castigos set status = 'cumprido', resolvido_em = now() where id = p_id;
  perform public._notificar(v_castigo.autor, 'castigo', 'Castigo cumprido',
    public._nome(auth.uid()) || ' cumpriu o castigo: ' || v_castigo.descricao || '.',
    public._nome(auth.uid()) || ' cumpriu o castigo. Sem multa.', 'moderada');
end;
$$;

-- Cron: castigo vencido vira −15 para o alvo.
create function public.cron_castigos()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_castigo public.castigos;
  v_temp    public.temporadas;
  v_pontos  integer;
  v_perda   integer;
  v_total   integer := 0;
begin
  for v_castigo in
    select * from public.castigos
     where status = 'pendente' and prazo < now()
     for update
  loop
    v_temp := public._temporada_atual(v_castigo.casa_id);
    v_pontos := coalesce((v_temp.placar ->> v_castigo.alvo::text)::integer, 0);
    v_perda := least(v_castigo.pontos, v_pontos);
    update public.temporadas
       set placar = jsonb_set(
             placar,
             array[v_castigo.alvo::text],
             to_jsonb(greatest(0, v_pontos - v_perda))
           )
     where id = v_temp.id;
    update public.castigos
       set status = 'falhou', resolvido_em = now()
     where id = v_castigo.id;
    perform public._notificar(v_castigo.alvo, 'castigo', 'Castigo não cumprido',
      'O prazo venceu: −' || v_perda || ' pontos. Da próxima vez, corre.',
      'O castigo venceu. −' || v_perda || ' pontos. A casa não perdoa.', 'sem_piedade');
    perform public._notificar(v_castigo.autor, 'castigo', 'Castigo venceu',
      public._nome(v_castigo.alvo) || ' não cumpriu o castigo. −' || v_perda || ' pontos.',
      public._nome(v_castigo.alvo) || ' deixou o castigo vencer. −' || v_perda || ' pontos.', 'sem_piedade');
    v_total := v_total + 1;
  end loop;
  return v_total;
end;
$$;

select cron.schedule('rivais-castigos', '*/5 * * * *', 'select public.cron_castigos()');

-- Vale-favor: social, não tira ponto.
create function public.usar_vale(p_descricao text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid   uuid := auth.uid();
  v_carta public.cartas;
  v_casa  public.casas;
  v_outro uuid;
begin
  select * into v_carta
    from public.cartas
   where dono = v_uid and tipo = 'vale' and usada_em is null
   order by comprada_em
   limit 1
   for update;
  if not found then
    perform public._erro('Você não tem um Vale-favor na mão.');
  end if;
  select * into v_casa from public.casas where id = v_carta.casa_id;
  if v_casa.modo_tregua then
    perform public._erro('Modo trégua: as cartas estão guardadas até a competição voltar.');
  end if;
  if exists (select 1 from public.cartas where dono = v_uid and usada_em is not null
               and (usada_em at time zone v_casa.fuso)::date = public._hoje(v_casa.id)) then
    perform public._erro('Uma carta por dia. Amanhã tem mais.');
  end if;
  v_outro := public._outro_membro(v_casa.id, v_uid);
  if v_outro is null then
    perform public._erro('Você precisa de outra pessoa na casa.');
  end if;
  if char_length(trim(p_descricao)) < 3 then
    perform public._erro('Descreva o favor em pelo menos 3 letras.');
  end if;

  insert into public.vales (casa_id, autor, alvo, descricao)
  values (v_casa.id, v_uid, v_outro, trim(p_descricao));
  update public.cartas set usada_em = now(), alvo = v_outro where id = v_carta.id;
  perform public._notificar(v_outro, 'vale', 'Vale-favor',
    public._nome(v_uid) || ' deixou um vale-favor: ' || trim(p_descricao) || '.',
    public._nome(v_uid) || ' deixou um vale-favor: ' || trim(p_descricao) || '. Sem pontos, só a honra.', 'moderada');
end;
$$;

create function public.cumprir_vale(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_vale public.vales;
begin
  select * into v_vale from public.vales where id = p_id and alvo = auth.uid() for update;
  if not found or v_vale.status <> 'pendente' then
    perform public._erro('Esse vale já foi resolvido.');
  end if;
  update public.vales set status = 'cumprido', resolvido_em = now() where id = p_id;
  perform public._notificar(v_vale.autor, 'vale', 'Vale cumprido',
    public._nome(auth.uid()) || ' cumpriu o vale: ' || v_vale.descricao || '.',
    public._nome(auth.uid()) || ' pagou o vale. A honra agradece.', 'moderada');
end;
$$;

revoke all on function public.usar_castigo(text) from public;
revoke all on function public.cumprir_castigo(uuid) from public;
revoke all on function public.usar_vale(text) from public;
revoke all on function public.cumprir_vale(uuid) from public;
revoke all on function public.cron_castigos() from public;
grant execute on function public.usar_castigo(text) to authenticated;
grant execute on function public.cumprir_castigo(uuid) to authenticated;
grant execute on function public.usar_vale(text) to authenticated;
grant execute on function public.cumprir_vale(uuid) to authenticated;
