-- 0.4.1: novas cartas (Café, Seguro, Balde, Cobrança, Arrastão) e efeitos no servidor.

alter table public.membros
  add column cafe_passado boolean not null default false,
  add column balde_agua_fria boolean not null default false,
  add column seguro_ate timestamptz,
  add column nota_fiscal_ate timestamptz;

create table public.decisivas_usadas (
  temporada_id uuid not null references public.temporadas (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  tipo         text not null,
  usada_em     timestamptz not null default now(),
  primary key (temporada_id, user_id, tipo)
);
alter table public.decisivas_usadas enable row level security;

-- Preços novos (os já comprados continuam valendo).
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
  end
$$;

create or replace function public.carta_raridade(p_tipo text)
returns text
language sql
immutable
as $$
  select case
    when p_tipo in ('holofote', 'escudo', 'cafe_passado', 'seguro', 'balde') then 'comum'
    when p_tipo in ('troca', 'dobradinha', 'cobranca') then 'rara'
    when p_tipo in ('hora_feliz', 'veto') then 'epica'
    when p_tipo = 'arrastao' then 'decisiva'
  end
$$;

-- Cálculo da conclusão com Café Passado (+25%, máx. +25) e Balde (−25%, máx. −20).
create or replace function public._calcular_conclusao(p_tarefa public.tarefas, p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
  v_membro public.membros;
  v_hoje   date := public._hoje(p_tarefa.casa_id);
  v_seq    integer;
  v_mults  jsonb := '[]'::jsonb;
  v_fator  numeric := 1;
  v_base   integer;
  v_total  integer;
  v_sem    integer;
  v_item   jsonb;
begin
  select * into v_membro from public.membros where casa_id = p_tarefa.casa_id and user_id = p_user;
  v_base := public.calcular_pontos(p_tarefa.minutos, p_tarefa.esforco, p_tarefa.chatice);

  if p_tarefa.dias_parada > 0 then
    v_mults := v_mults || jsonb_build_object(
      'tipo', 'bomba',
      'rotulo', 'Bomba · parada há ' || p_tarefa.dias_parada || case when p_tarefa.dias_parada = 1 then ' dia' else ' dias' end,
      'valor', public.bonus_bomba(p_tarefa.dias_parada));
  end if;

  v_seq := case when v_membro.ultimo_dia_ativo >= v_hoje - 1 then v_membro.sequencia_dias else 0 end;
  if v_seq > 0 then
    v_mults := v_mults || jsonb_build_object(
      'tipo', 'sequencia',
      'rotulo', 'Sequência de ' || v_seq || case when v_seq = 1 then ' dia' else ' dias' end,
      'valor', least(1 + 0.05 * v_seq, 1.3));
  end if;

  if p_tarefa.roubada_de is not null and p_tarefa.roubada_de <> p_user then
    v_mults := v_mults || jsonb_build_object('tipo', 'roubo', 'rotulo', 'Roubada de ' || public._nome(p_tarefa.roubada_de), 'valor', 1.25);
  end if;

  if v_membro.dobradinha then
    v_mults := v_mults || jsonb_build_object('tipo', 'dobradinha', 'rotulo', 'Carta Dobradinha', 'valor', 2);
  end if;

  if v_membro.hora_feliz_ate > now() then
    v_mults := v_mults || jsonb_build_object('tipo', 'hora_feliz', 'rotulo', 'Hora Feliz', 'valor', 1.5);
  end if;

  for v_item in select * from jsonb_array_elements(v_mults) loop
    if v_item ->> 'tipo' <> 'bomba' then
      v_fator := v_fator * (v_item ->> 'valor')::numeric;
    end if;
  end loop;

  v_sem := public.calcular_pontos(p_tarefa.minutos, p_tarefa.esforco, p_tarefa.chatice, p_tarefa.dias_parada, v_fator);
  v_total := v_sem;

  if v_membro.cafe_passado and p_tarefa.minutos <= 30 then
    v_mults := v_mults || jsonb_build_object('tipo', 'cafe', 'rotulo', 'Café Passado', 'valor', 1.25);
    v_total := least(round(v_sem * 1.25)::integer, v_sem + 25);
  end if;

  if v_membro.balde_agua_fria then
    v_mults := v_mults || jsonb_build_object('tipo', 'balde', 'rotulo', 'Balde de Água Fria', 'valor', 0.75);
    v_total := greatest(round(v_total * 0.75)::integer, v_total - 20);
  end if;

  return jsonb_build_object('base', v_base, 'multiplicadores', v_mults, 'pontos', v_total, 'moedas', v_total / 10);
end;
$$;

-- Consome Café (só quando a tarefa é de até 30 min) e Balde (na próxima conclusão).
create or replace function public.concluir_tarefa(p_tarefa uuid, p_foto_path text default null)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_tarefa public.tarefas;
  v_conta  jsonb;
  v_reg    public.registros;
begin
  select * into v_tarefa from public.tarefas where id = p_tarefa for update;
  if not found or not public.is_membro(v_tarefa.casa_id) then
    perform public._erro('Tarefa não encontrada.');
  end if;
  if not v_tarefa.ativa then
    perform public._erro('Essa tarefa foi removida.');
  end if;
  if v_tarefa.vetada_por = v_uid and v_tarefa.vetada_ate > now() then
    perform public._erro('Você vetou essa tarefa nesta temporada.');
  end if;
  if v_tarefa.status = 'aguardando' then
    perform public._erro('Essa tarefa já está esperando validação.');
  end if;
  if v_tarefa.status = 'reservada' and v_tarefa.reservada_por <> v_uid then
    perform public._erro('Essa tarefa está reservada pela outra pessoa.');
  end if;
  if p_foto_path is not null and p_foto_path not like v_tarefa.casa_id || '/' || v_uid || '/%' then
    perform public._erro('Foto inválida.');
  end if;

  v_conta := public._calcular_conclusao(v_tarefa, v_uid);
  insert into public.registros (casa_id, tarefa_id, user_id, titulo, pontos_base, multiplicadores, pontos_finais, moedas, foto_path)
  values (v_tarefa.casa_id, v_tarefa.id, v_uid, v_tarefa.titulo, (v_conta ->> 'base')::integer, v_conta -> 'multiplicadores',
          (v_conta ->> 'pontos')::integer, (v_conta ->> 'moedas')::integer, p_foto_path)
  returning * into v_reg;

  update public.membros
     set dobradinha = false,
         cafe_passado = case when v_tarefa.minutos <= 30 then false else cafe_passado end,
         balde_agua_fria = false
   where user_id = v_uid
     and (dobradinha or (cafe_passado and v_tarefa.minutos <= 30) or balde_agua_fria);

  update public.tarefas
     set status = 'aguardando', reservada_por = v_uid, reservada_em = coalesce(reservada_em, now())
   where id = p_tarefa;

  perform public._notificar(public._outro_membro(v_tarefa.casa_id, v_uid), 'pedido_validacao',
    public._nome(v_uid) || ' concluiu ' || v_tarefa.titulo,
    'Vale ' || v_reg.pontos_finais || ' pontos. Confere com um toque?',
    public._nome(v_uid) || ' jura que fez ' || v_tarefa.titulo || '. Vai conferir ou vai deixar passar?',
    'moderada', jsonb_build_object('registro', v_reg.id));
  return v_reg;
end;
$$;

-- Usar cartas: novos efeitos + Seguro bloqueando ataques.
create or replace function public.usar_carta(p_carta uuid, p_alvo uuid default null, p_alvo2 uuid default null)
returns public.cartas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid          uuid := auth.uid();
  v_carta        public.cartas;
  v_casa         public.casas;
  v_tarefa       public.tarefas;
  v_outra        public.tarefas;
  v_temp         public.temporadas;
  v_outro        uuid;
  v_outro_membro public.membros;
  v_troca        public.trocas;
  v_pontos       integer;
  v_roubo        integer;
begin
  select * into v_carta from public.cartas where id = p_carta and dono = v_uid for update;
  if not found or v_carta.usada_em is not null then
    perform public._erro('Essa carta não está na sua mão.');
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
  v_temp := public._temporada_atual(v_casa.id);

  case v_carta.tipo
    when 'holofote' then
      select * into v_tarefa from public.tarefas where id = p_alvo and casa_id = v_casa.id and ativa for update;
      if not found or v_tarefa.status = 'aguardando' then
        perform public._erro('Escolha uma tarefa da casa que não esteja esperando validação.');
      end if;
      update public.tarefas
         set prazo = least(coalesce(prazo, now()), now() - interval '1 day'), dias_parada = greatest(dias_parada, 1)
       where id = v_tarefa.id;
      perform public._notificar(v_outro, 'carta', 'Holofote em ' || v_tarefa.titulo,
        public._nome(v_uid) || ' transformou ' || v_tarefa.titulo || ' em bomba.',
        public._nome(v_uid) || ' acendeu o holofote: ' || v_tarefa.titulo || ' virou bomba. Quem desarma?', 'sem_piedade');

    when 'escudo' then
      select * into v_tarefa from public.tarefas where id = p_alvo and casa_id = v_casa.id for update;
      if not found or v_tarefa.status <> 'reservada' or v_tarefa.reservada_por <> v_uid then
        perform public._erro('O Escudo protege uma tarefa reservada por você.');
      end if;
      update public.tarefas set escudo_ate = now() + interval '24 hours', aviso_roubo_em = null where id = v_tarefa.id;

    when 'troca' then
      select * into v_tarefa from public.tarefas where id = p_alvo and casa_id = v_casa.id for update;
      select * into v_outra from public.tarefas where id = p_alvo2 and casa_id = v_casa.id for update;
      if v_tarefa.id is null or v_tarefa.status <> 'reservada' or v_tarefa.reservada_por <> v_uid then
        perform public._erro('Escolha uma tarefa reservada por você para dar na troca.');
      end if;
      if v_outra.id is null or v_outra.status <> 'reservada' or v_outra.reservada_por <> v_outro then
        perform public._erro('Escolha uma tarefa reservada pela outra pessoa.');
      end if;
      insert into public.trocas (casa_id, proposta_por, para, tarefa_minha, tarefa_dele)
      values (v_casa.id, v_uid, v_outro, v_tarefa.id, v_outra.id)
      returning * into v_troca;
      if public._recusas_na_semana(v_outro, v_casa.id) >= 1 then
        perform public._executar_troca(v_troca.id, true);
      else
        perform public._notificar(v_outro, 'troca_proposta', public._nome(v_uid) || ' quer trocar',
          v_tarefa.titulo || ' pela sua ' || v_outra.titulo || '. Aceita?',
          public._nome(v_uid) || ' quer empurrar ' || v_tarefa.titulo || ' pra você em troca de ' || v_outra.titulo || '.',
          'moderada', jsonb_build_object('troca', v_troca.id));
      end if;

    when 'dobradinha' then
      if (select dobradinha from public.membros where user_id = v_uid) then
        perform public._erro('Sua Dobradinha já está armada para a próxima tarefa.');
      end if;
      update public.membros set dobradinha = true where user_id = v_uid;

    when 'hora_feliz' then
      update public.membros set hora_feliz_ate = now() + interval '1 hour' where user_id = v_uid;
      perform public._notificar(v_outro, 'carta', 'Hora Feliz',
        public._nome(v_uid) || ' ativou a Hora Feliz: por 1h, tudo vale x1,5 pra ' || public._nome(v_uid) || '.',
        public._nome(v_uid) || ' ativou a Hora Feliz. Por 1h, cada tarefa dela(e) vale x1,5. Corre.', 'sem_piedade');

    when 'veto' then
      select * into v_tarefa from public.tarefas
       where casa_id = v_casa.id and ativa and vetada_por is null and status <> 'aguardando'
       order by chatice desc, public.calcular_pontos(minutos, esforco, chatice) desc
       limit 1
       for update;
      if not found then
        perform public._erro('Não há tarefa para vetar agora.');
      end if;
      update public.tarefas
         set vetada_por = v_uid, vetada_ate = v_temp.fim,
             status = case when reservada_por = v_uid then 'livre' else status end,
             reservada_em = case when reservada_por = v_uid then null else reservada_em end,
             reservada_por = case when reservada_por = v_uid then null else reservada_por end
       where id = v_tarefa.id;
      p_alvo := v_tarefa.id;
      perform public._notificar(v_outro, 'carta', 'Veto',
        public._nome(v_uid) || ' vetou ' || v_tarefa.titulo || ' nesta temporada.',
        public._nome(v_uid) || ' vetou ' || v_tarefa.titulo || '. Adivinha pra quem sobrou.', 'sem_piedade');

    when 'cafe_passado' then
      if (select cafe_passado from public.membros where user_id = v_uid) then
        perform public._erro('Seu Café Passado já está armado.');
      end if;
      update public.membros set cafe_passado = true where user_id = v_uid;
      perform public._notificar(v_outro, 'carta', 'Café Passado',
        public._nome(v_uid) || ' armou o Café Passado: +25% na próxima tarefa de até 30 min.',
        public._nome(v_uid) || ' tomou um café. A próxima tarefa curta dele(a) vale +25%.', 'sem_piedade');

    when 'seguro' then
      if (select seguro_ate from public.membros where user_id = v_uid) > now() then
        perform public._erro('Seu Seguro já está ativo.');
      end if;
      update public.membros set seguro_ate = now() + interval '24 hours' where user_id = v_uid;
      perform public._notificar(v_outro, 'carta', 'Seguro Antissacanagem',
        public._nome(v_uid) || ' armou o Seguro: o próximo ataque contra ele(a) será bloqueado.',
        public._nome(v_uid) || ' se protegeu. O próximo ataque contra ele(a) vai bater no vidro.', 'sem_piedade');

    when 'balde' then
      select * into v_outro_membro from public.membros where user_id = v_outro for update;
      if v_outro_membro.seguro_ate > now() then
        update public.membros set seguro_ate = null where user_id = v_outro;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Seguro bloqueou o Balde',
          public._nome(v_uid) || ' tentou jogar o Balde de Água Fria, mas seu Seguro bloqueou.',
          public._nome(v_uid) || ' tentou te acertar com o Balde. Seu Seguro bloqueou. A casa agradece.', 'sem_piedade');
        return v_carta;
      end if;
      update public.membros set balde_agua_fria = true where user_id = v_outro;
      perform public._notificar(v_outro, 'carta', 'Balde de Água Fria',
        public._nome(v_uid) || ' jogou o Balde: sua próxima tarefa vale −25% (máx. −20).',
        public._nome(v_uid) || ' jogou um balde na sua próxima tarefa. −25%. Seca e corre.', 'sem_piedade');

    when 'cobranca' then
      select * into v_outro_membro from public.membros where user_id = v_outro for update;
      if v_outro_membro.seguro_ate > now() then
        update public.membros set seguro_ate = null where user_id = v_outro;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Seguro bloqueou a Cobrança',
          public._nome(v_uid) || ' tentou cobrar o aluguel, mas seu Seguro bloqueou.',
          public._nome(v_uid) || ' tentou cobrar aluguel. Seu Seguro bloqueou. Sem pagamento hoje.', 'sem_piedade');
        return v_carta;
      end if;
      v_pontos := coalesce((v_temp.placar ->> v_outro::text)::integer, 0);
      if v_pontos <= 0 then
        perform public._erro('O rival está sem pontos para cobrar.');
      end if;
      v_roubo := least(15, v_pontos);
      update public.temporadas
         set placar = jsonb_set(
               jsonb_set(placar, array[v_outro::text], to_jsonb(v_pontos - v_roubo)),
               array[v_uid::text],
               to_jsonb(coalesce((placar ->> v_uid::text)::integer, 0) + v_roubo)
             )
       where id = v_temp.id;
      update public.membros set nota_fiscal_ate = now() + interval '24 hours' where user_id = v_outro;
      perform public._notificar(v_outro, 'carta', 'Cobrança de Aluguel',
        public._nome(v_uid) || ' cobrou 15 pontos seus. Sua Nota Fiscal pode cobrar de volta em 24h.',
        public._nome(v_uid) || ' cobrou aluguel: −15 pra você. A Nota Fiscal está na sua mão. Cobra de volta.', 'sem_piedade');
  end case;

  update public.cartas set usada_em = now(), alvo = p_alvo where id = p_carta returning * into v_carta;
  return v_carta;
end;
$$;

-- Decisiva grátis: Arrastão do Sofá, uma vez por temporada.
create function public.usar_decisiva(p_tipo text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_casa   uuid := public.minha_casa();
  v_temp   public.temporadas;
  v_outro  uuid;
  v_membro public.membros;
  v_pontos integer;
  v_roubo  integer;
begin
  if p_tipo <> 'arrastao' then
    perform public._erro('Decisiva desconhecida.');
  end if;
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  v_temp := public._temporada_atual(v_casa);
  v_outro := public._outro_membro(v_casa, v_uid);
  if v_outro is null then
    perform public._erro('Você precisa de outra pessoa na casa.');
  end if;
  if exists (select 1 from public.decisivas_usadas where temporada_id = v_temp.id and user_id = v_uid and tipo = p_tipo) then
    perform public._erro('Você já usou o Arrastão nesta temporada.');
  end if;
  select * into v_membro from public.membros where user_id = v_outro for update;
  if v_membro.seguro_ate > now() then
    update public.membros set seguro_ate = null where user_id = v_outro;
    insert into public.decisivas_usadas (temporada_id, user_id, tipo) values (v_temp.id, v_uid, p_tipo);
    perform public._notificar(v_outro, 'carta', 'Seguro bloqueou o Arrastão',
      public._nome(v_uid) || ' tentou o Arrastão, mas seu Seguro bloqueou.',
      public._nome(v_uid) || ' tentou o Arrastão. Seu Seguro bloqueou. Ufa.', 'sem_piedade');
    perform public._notificar(v_uid, 'carta', 'Arrastão bloqueado',
      'O Seguro de ' || public._nome(v_outro) || ' bloqueou o Arrastão. A decisiva foi consumida.',
      'O Seguro do rival bloqueou seu Arrastão. Ele(a) estava preparado(a).', 'sem_piedade');
    return;
  end if;
  v_pontos := coalesce((v_temp.placar ->> v_outro::text)::integer, 0);
  if v_pontos <= 0 then
    perform public._erro('O rival está sem pontos para arrastar.');
  end if;
  v_roubo := least(30, v_pontos);
  update public.temporadas
     set placar = jsonb_set(
           jsonb_set(placar, array[v_outro::text], to_jsonb(v_pontos - v_roubo)),
           array[v_uid::text],
           to_jsonb(coalesce((placar ->> v_uid::text)::integer, 0) + v_roubo)
         )
   where id = v_temp.id;
  insert into public.decisivas_usadas (temporada_id, user_id, tipo) values (v_temp.id, v_uid, p_tipo);
  perform public._notificar(v_outro, 'carta', 'Arrastão do Sofá',
    public._nome(v_uid) || ' usou o Arrastão: você perdeu ' || v_roubo || ' pontos.',
    public._nome(v_uid) || ' puxou o sofá e levou ' || v_roubo || ' pontos seus. A decisiva foi usada.', 'sem_piedade');
end;
$$;

-- Nota Fiscal: o alvo da Cobrança cobra 15 de volta em 24h.
create function public.usar_nota_fiscal()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_casa   uuid := public.minha_casa();
  v_temp   public.temporadas;
  v_membro public.membros;
  v_outro  uuid;
  v_pontos integer;
  v_roubo  integer;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  select * into v_membro from public.membros where user_id = v_uid for update;
  if v_membro.nota_fiscal_ate is null or v_membro.nota_fiscal_ate <= now() then
    perform public._erro('Sua Nota Fiscal expirou.');
  end if;
  v_temp := public._temporada_atual(v_casa);
  v_outro := public._outro_membro(v_casa, v_uid);
  v_pontos := coalesce((v_temp.placar ->> v_outro::text)::integer, 0);
  if v_pontos <= 0 then
    perform public._erro('O rival está sem pontos para cobrar.');
  end if;
  v_roubo := least(15, v_pontos);
  update public.temporadas
     set placar = jsonb_set(
           jsonb_set(placar, array[v_outro::text], to_jsonb(v_pontos - v_roubo)),
           array[v_uid::text],
           to_jsonb(coalesce((placar ->> v_uid::text)::integer, 0) + v_roubo)
         )
   where id = v_temp.id;
  update public.membros set nota_fiscal_ate = null where user_id = v_uid;
  perform public._notificar(v_outro, 'carta', 'Nota Fiscal cobrada',
    public._nome(v_uid) || ' cobrou 15 pontos de volta.',
    public._nome(v_uid) || ' apresentou a Nota Fiscal e levou 15 de volta. A cobrança foi paga.', 'sem_piedade');
end;
$$;

revoke all on function public.usar_decisiva(text) from public;
revoke all on function public.usar_nota_fiscal() from public;
grant execute on function public.usar_decisiva(text) to authenticated;
grant execute on function public.usar_nota_fiscal() to authenticated;

-- O reset também limpa efeitos de cartas e a decisiva usada na temporada.
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

  delete from public.decisivas_usadas
   where temporada_id = v_temp.id;

  update public.membros
     set cafe_passado = false,
         balde_agua_fria = false,
         seguro_ate = null,
         nota_fiscal_ate = null
   where casa_id = v_casa;

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
