-- 0.5.1: Reviravolta (defesa), All-in da Faxina e Código da Casa.

alter table public.membros
  add column reviravolta_ate timestamptz,
  add column allin_ate timestamptz,
  add column allin_count integer not null default 0;

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
    when 'pedagio' then 25 when 'mao_atada' then 25 when 'roubo_cena' then 20
    when 'combo' then 20 when 'dobrou' then 25
    when 'reviravolta' then 20 when 'allin' then 30
  end
$$;

create or replace function public.carta_raridade(p_tipo text)
returns text
language sql
immutable
as $$
  select case
    when p_tipo in ('holofote', 'escudo', 'cafe_passado', 'seguro', 'balde', 'castigo', 'vale', 'combo') then 'comum'
    when p_tipo in ('troca', 'dobradinha', 'cobranca', 'pedagio', 'mao_atada', 'roubo_cena', 'dobrou', 'reviravolta') then 'rara'
    when p_tipo in ('hora_feliz', 'veto', 'allin') then 'epica'
    when p_tipo = 'arrastao' then 'decisiva'
  end
$$;

-- Reviravolta: arma a defesa; não usa a jogada de ataque.
create function public.usar_reviravolta()
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
   where dono = v_uid and tipo = 'reviravolta' and usada_em is null
   order by comprada_em
   limit 1
   for update;
  if not found then
    perform public._erro('Você não tem uma Reviravolta na mão.');
  end if;
  select * into v_casa from public.casas where id = v_carta.casa_id;
  if v_casa.modo_tregua then
    perform public._erro('Modo trégua: as cartas estão guardadas até a competição voltar.');
  end if;
  if (select reviravolta_ate from public.membros where user_id = v_uid) > now() then
    perform public._erro('Sua Reviravolta já está armada.');
  end if;
  v_outro := public._outro_membro(v_casa.id, v_uid);
  update public.membros set reviravolta_ate = now() + interval '24 hours' where user_id = v_uid;
  update public.cartas set usada_em = now(), alvo = v_outro where id = v_carta.id;
  perform public._notificar(v_outro, 'carta', 'Reviravolta',
    public._nome(v_uid) || ' armou uma Reviravolta: o próximo ataque de ponto volta pela metade.',
    public._nome(v_uid) || ' armou a Reviravolta. Atacar agora é dar pontos de volta.', 'sem_piedade');
end;
$$;

-- All-in: 3 tarefas de 20+ em 24h. +60 se completar, -30 se falhar.
create function public.usar_allin()
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
  v_membro public.membros;
begin
  select * into v_carta
    from public.cartas
   where dono = v_uid and tipo = 'allin' and usada_em is null
   order by comprada_em
   limit 1
   for update;
  if not found then
    perform public._erro('Você não tem um All-in na mão.');
  end if;
  select * into v_casa from public.casas where id = v_carta.casa_id;
  if v_casa.modo_tregua then
    perform public._erro('Modo trégua: as cartas estão guardadas até a competição voltar.');
  end if;
  select * into v_membro from public.membros where user_id = v_uid for update;
  if v_membro.allin_ate > now() then
    perform public._erro('Seu All-in já está valendo.');
  end if;
  v_outro := public._outro_membro(v_casa.id, v_uid);
  update public.membros set allin_ate = now() + interval '24 hours', allin_count = 0 where user_id = v_uid;
  update public.cartas set usada_em = now(), alvo = v_outro where id = v_carta.id;
  perform public._notificar(v_outro, 'carta', 'All-in da Faxina',
    public._nome(v_uid) || ' apostou o All-in: 3 tarefas de 20+ em 24h. +60 ou −30.',
    public._nome(v_uid) || ' apostou tudo: 3 tarefas de 20+ em 24h. Se falhar, −30. Vai dar ruim.', 'sem_piedade');
end;
$$;

-- Conta as tarefas do All-in e paga +60 na terceira.
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
  v_membro public.membros;
  v_temp   public.temporadas;
  v_base   integer;
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

  if (v_conta -> 'multiplicadores') @> '[{"tipo":"combo"}]'::jsonb then
    update public.membros set combo_ate = null where user_id = v_uid;
  end if;

  update public.desafios
     set status = 'cumprido', resolvido_em = now()
   where tarefa_id = p_tarefa and user_id = v_uid and status = 'pendente';

  update public.tarefas
     set status = 'aguardando', reservada_por = v_uid, reservada_em = coalesce(reservada_em, now()),
         cena_ate = null, cena_de = null
   where id = p_tarefa;

  -- All-in: conta tarefas de 20+ e paga +60 na terceira.
  select * into v_membro from public.membros where user_id = v_uid for update;
  v_base := (v_conta ->> 'base')::integer;
  if v_membro.allin_ate > now() and v_base >= 20 then
    update public.membros set allin_count = allin_count + 1 where user_id = v_uid;
    if v_membro.allin_count + 1 >= 3 then
      v_temp := public._temporada_atual(v_tarefa.casa_id);
      update public.temporadas
         set placar = jsonb_set(
               placar, array[v_uid::text],
               to_jsonb(coalesce((placar ->> v_uid::text)::integer, 0) + 60)
             )
       where id = v_temp.id;
      update public.membros set allin_ate = null, allin_count = 0 where user_id = v_uid;
      perform public._notificar(v_uid, 'carta', 'All-in completo',
        'Três tarefas de 20+ em 24h: +60 pontos.',
        'Você fechou o All-in. +60 pontos. A casa viu.', 'sem_piedade');
    end if;
  end if;

  perform public._notificar(public._outro_membro(v_tarefa.casa_id, v_uid), 'pedido_validacao',
    public._nome(v_uid) || ' concluiu ' || v_tarefa.titulo,
    'Vale ' || v_reg.pontos_finais || ' pontos. Confere com um toque?',
    public._nome(v_uid) || ' jura que fez ' || v_tarefa.titulo || '. Vai conferir ou vai deixar passar?',
    'moderada', jsonb_build_object('registro', v_reg.id));
  return v_reg;
end;
$$;

-- Cron: All-in vencido sem 3 tarefas = -30.
create function public.cron_allins()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_membro public.membros;
  v_temp   public.temporadas;
  v_pontos integer;
  v_total  integer := 0;
begin
  for v_membro in
    select * from public.membros
     where allin_ate is not null and allin_ate < now() and allin_count < 3
     for update
  loop
    v_temp := public._temporada_atual(v_membro.casa_id);
    v_pontos := coalesce((v_temp.placar ->> v_membro.user_id::text)::integer, 0);
    update public.temporadas
       set placar = jsonb_set(
             placar,
             array[v_membro.user_id::text],
             to_jsonb(greatest(0, v_pontos - 30))
           )
     where id = v_temp.id;
    update public.membros set allin_ate = null, allin_count = 0 where user_id = v_membro.user_id;
    perform public._notificar(v_membro.user_id, 'carta', 'All-in falhou',
      'O prazo venceu sem 3 tarefas de 20+: −30 pontos.',
      'O All-in venceu. −30 pontos. Na próxima você fecha.', 'sem_piedade');
    v_total := v_total + 1;
  end loop;
  return v_total;
end;
$$;

select cron.schedule('rivais-allins', '*/5 * * * *', 'select public.cron_allins()');

-- Bloqueio da Reviravolta nas cartas de roubo de ponto.
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
  v_membro_self  public.membros;
  v_alvo_membro  public.membros;
  v_troca        public.trocas;
  v_pontos       integer;
  v_roubo        integer;
  v_reflet       integer;
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
  select * into v_membro_self from public.membros where user_id = v_uid for update;

  if v_membro_self.mao_atada_ate > now()
     and v_carta.tipo in ('holofote', 'troca', 'veto', 'balde', 'cobranca', 'pedagio', 'mao_atada', 'roubo_cena') then
    perform public._erro('Sua Mão está atada: você não usa cartas ofensivas por enquanto.');
  end if;

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
      select * into v_alvo_membro from public.membros where user_id = v_outro for update;
      if v_alvo_membro.seguro_ate > now() then
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
      select * into v_alvo_membro from public.membros where user_id = v_outro for update;
      if v_alvo_membro.seguro_ate > now() then
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
      if v_alvo_membro.reviravolta_ate > now() then
        v_reflet := least(15, v_roubo / 2);
        update public.membros set reviravolta_ate = null where user_id = v_outro;
        update public.temporadas
           set placar = jsonb_set(
                 jsonb_set(placar, array[v_uid::text], to_jsonb(greatest(0, coalesce((placar ->> v_uid::text)::integer, 0) - v_reflet))),
                 array[v_outro::text], to_jsonb(v_pontos + v_reflet)
               )
         where id = v_temp.id;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Reviravolta!',
          public._nome(v_uid) || ' tentou cobrar, mas sua Reviravolta devolveu ' || v_reflet || ' pontos.',
          public._nome(v_uid) || ' tentou cobrar. Sua Reviravolta devolveu ' || v_reflet || '. Que virada.', 'sem_piedade');
        return v_carta;
      end if;
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

    when 'castigo' then
      perform public._erro('Use o Castigo pela tela de cartas.');

    when 'vale' then
      perform public._erro('Use o Vale-favor pela tela de cartas.');

    when 'reviravolta' then
      perform public._erro('Use a Reviravolta pela tela de cartas.');

    when 'allin' then
      perform public._erro('Use o All-in pela tela de cartas.');

    when 'pedagio' then
      select * into v_alvo_membro from public.membros where user_id = v_outro for update;
      if v_alvo_membro.seguro_ate > now() then
        update public.membros set seguro_ate = null where user_id = v_outro;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Seguro bloqueou o Pedágio',
          public._nome(v_uid) || ' tentou cobrar o pedágio, mas seu Seguro bloqueou.',
          public._nome(v_uid) || ' tentou cobrar pedágio. Seu Seguro bloqueou. Passou de graça.', 'sem_piedade');
        return v_carta;
      end if;
      v_pontos := coalesce((v_temp.placar ->> v_outro::text)::integer, 0);
      if v_pontos <= 0 then
        perform public._erro('O rival está sem pontos para cobrar.');
      end if;
      v_roubo := least(10, v_pontos);
      if v_alvo_membro.reviravolta_ate > now() then
        v_reflet := least(15, v_roubo / 2);
        update public.membros set reviravolta_ate = null where user_id = v_outro;
        update public.temporadas
           set placar = jsonb_set(
                 jsonb_set(placar, array[v_uid::text], to_jsonb(greatest(0, coalesce((placar ->> v_uid::text)::integer, 0) - v_reflet))),
                 array[v_outro::text], to_jsonb(v_pontos + v_reflet)
               )
         where id = v_temp.id;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Reviravolta!',
          public._nome(v_uid) || ' tentou cobrar o pedágio, mas sua Reviravolta devolveu ' || v_reflet || ' pontos.',
          public._nome(v_uid) || ' tentou cobrar pedágio. Sua Reviravolta devolveu ' || v_reflet || '.', 'sem_piedade');
        return v_carta;
      end if;
      update public.temporadas
         set placar = jsonb_set(
               jsonb_set(placar, array[v_outro::text], to_jsonb(v_pontos - v_roubo)),
               array[v_uid::text],
               to_jsonb(coalesce((placar ->> v_uid::text)::integer, 0) + v_roubo)
             )
       where id = v_temp.id;
      perform public._notificar(v_outro, 'carta', 'Pedágio Doméstico',
        public._nome(v_uid) || ' cobrou 10 pontos de pedágio.',
        public._nome(v_uid) || ' cobrou o pedágio: −10 pra você. A casa tem pedágio agora.', 'sem_piedade');

    when 'mao_atada' then
      select * into v_alvo_membro from public.membros where user_id = v_outro for update;
      if v_alvo_membro.seguro_ate > now() then
        update public.membros set seguro_ate = null where user_id = v_outro;
        update public.cartas set usada_em = now(), alvo = v_outro where id = p_carta returning * into v_carta;
        perform public._notificar(v_outro, 'carta', 'Seguro bloqueou a Mão Atada',
          public._nome(v_uid) || ' tentou amarrar suas mãos, mas seu Seguro bloqueou.',
          public._nome(v_uid) || ' tentou te deixar sem cartas. Seu Seguro bloqueou.', 'sem_piedade');
        return v_carta;
      end if;
      update public.membros set mao_atada_ate = now() + interval '12 hours' where user_id = v_outro;
      perform public._notificar(v_outro, 'carta', 'Mão Atada',
        public._nome(v_uid) || ' amarrou suas mãos: 12h sem cartas ofensivas.',
        public._nome(v_uid) || ' te deixou de mãos atadas. 12h sem atacar. Aguenta.', 'sem_piedade');

    when 'roubo_cena' then
      select * into v_tarefa from public.tarefas
       where id = p_alvo and casa_id = v_casa.id and status = 'livre' and ativa
       for update;
      if not found then
        perform public._erro('Escolha uma tarefa livre para roubar a cena.');
      end if;
      update public.tarefas set cena_ate = now() + interval '24 hours', cena_de = v_uid where id = v_tarefa.id;
      perform public._notificar(v_outro, 'carta', 'Roubo de Cena em ' || v_tarefa.titulo,
        public._nome(v_uid) || ' marcou ' || v_tarefa.titulo || ': quem concluir em 24h leva +30.',
        public._nome(v_uid) || ' armou o Roubo de Cena em ' || v_tarefa.titulo || '. Corre que o +30 é seu.', 'sem_piedade');

    when 'combo' then
      if (select combo_ate from public.membros where user_id = v_uid) > now() then
        perform public._erro('Seu Combo da Casa já está ativo.');
      end if;
      update public.membros set combo_ate = now() + interval '24 hours' where user_id = v_uid;
      perform public._notificar(v_outro, 'carta', 'Combo da Casa',
        public._nome(v_uid) || ' ativou o Combo: +30 ao completar 3 categorias distintas em 24h.',
        public._nome(v_uid) || ' ativou o Combo. Se ela(e) variar as categorias, o +30 vem.', 'sem_piedade');

    when 'dobrou' then
      select * into v_tarefa from public.tarefas
       where id = p_alvo and casa_id = v_casa.id and status = 'reservada' and reservada_por = v_uid
       for update;
      if not found then
        perform public._erro('Escolha uma tarefa reservada por você.');
      end if;
      if public.calcular_pontos(v_tarefa.minutos, v_tarefa.esforco, v_tarefa.chatice) < 40 then
        perform public._erro('Essa tarefa vale menos de 40 pontos. Escolha uma maior.');
      end if;
      insert into public.desafios (casa_id, user_id, tarefa_id, prazo)
      values (v_casa.id, v_uid, v_tarefa.id, now() + interval '12 hours');
      perform public._notificar(v_outro, 'carta', 'Dobrou ou Danou',
        public._nome(v_uid) || ' desafiou ' || v_tarefa.titulo || ': +40 se concluir em 12h, −20 se falhar.',
        public._nome(v_uid) || ' apostou ' || v_tarefa.titulo || '. Se não fizer em 12h, −20. Vai encarar?', 'sem_piedade');
  end case;

  update public.cartas set usada_em = now(), alvo = p_alvo where id = p_carta returning * into v_carta;
  return v_carta;
end;
$$;

-- Reviravolta também segura o Arrastão.
create or replace function public.usar_decisiva(p_tipo text)
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
  v_self   public.membros;
  v_pontos integer;
  v_roubo  integer;
  v_reflet integer;
begin
  if p_tipo <> 'arrastao' then
    perform public._erro('Decisiva desconhecida.');
  end if;
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  select * into v_self from public.membros where user_id = v_uid for update;
  if v_self.mao_atada_ate > now() then
    perform public._erro('Sua Mão está atada: você não usa cartas ofensivas por enquanto.');
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
  if v_membro.reviravolta_ate > now() then
    v_reflet := least(15, v_roubo / 2);
    update public.membros set reviravolta_ate = null where user_id = v_outro;
    update public.temporadas
       set placar = jsonb_set(
             jsonb_set(placar, array[v_uid::text], to_jsonb(greatest(0, coalesce((placar ->> v_uid::text)::integer, 0) - v_reflet))),
             array[v_outro::text], to_jsonb(v_pontos + v_reflet)
           )
     where id = v_temp.id;
    insert into public.decisivas_usadas (temporada_id, user_id, tipo) values (v_temp.id, v_uid, p_tipo);
    perform public._notificar(v_outro, 'carta', 'Reviravolta no Arrastão',
      public._nome(v_uid) || ' tentou o Arrastão, mas sua Reviravolta devolveu ' || v_reflet || ' pontos.',
      public._nome(v_uid) || ' tentou o Arrastão. Sua Reviravolta devolveu ' || v_reflet || '.', 'sem_piedade');
    return;
  end if;
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

revoke all on function public.usar_reviravolta() from public;
revoke all on function public.usar_allin() from public;
revoke all on function public.cron_allins() from public;
grant execute on function public.usar_reviravolta() to authenticated;
grant execute on function public.usar_allin() to authenticated;
