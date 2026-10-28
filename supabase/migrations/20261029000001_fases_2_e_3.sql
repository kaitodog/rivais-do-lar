-- Fases 2 e 3: sequência, roubo, teto diário, recuperação, trégua, meta batida, notificações,
-- fotos do depois, cartas de poder, trocas e conquistas. Tudo continua só no servidor.

------------------------------------------------------------------------------------------------
-- Colunas e tabelas novas
------------------------------------------------------------------------------------------------

alter table public.tarefas
  add column roubada_de     uuid references public.profiles (id) on delete set null,
  add column aviso_roubo_em timestamptz,
  add column escudo_ate     timestamptz,
  add column vetada_por     uuid references public.profiles (id) on delete set null,
  add column vetada_ate     timestamptz;

alter table public.membros
  add column dobradinha     boolean not null default false,
  add column hora_feliz_ate timestamptz;

alter table public.temporadas
  add column meta_batida_em timestamptz;

alter table public.registros
  add column pontos_previstos integer,
  add column observacoes      jsonb not null default '[]'::jsonb;

alter table public.cartas
  add column alvo uuid;

create table public.trocas (
  id            uuid primary key default gen_random_uuid(),
  casa_id       uuid not null references public.casas (id) on delete cascade,
  proposta_por  uuid not null references public.profiles (id) on delete cascade,
  para          uuid not null references public.profiles (id) on delete cascade,
  tarefa_minha  uuid not null references public.tarefas (id) on delete cascade,
  tarefa_dele   uuid not null references public.tarefas (id) on delete cascade,
  status        text not null default 'pendente' check (status in ('pendente', 'aceita', 'recusada', 'cancelada')),
  automatica    boolean not null default false,
  criada_em     timestamptz not null default now(),
  respondida_em timestamptz
);
create index trocas_casa_idx on public.trocas (casa_id, criada_em desc);

create table public.conquistas (
  user_id   uuid not null references public.profiles (id) on delete cascade,
  codigo    text not null,
  casa_id   uuid not null references public.casas (id) on delete cascade,
  obtida_em timestamptz not null default now(),
  primary key (user_id, codigo)
);

-- Caixa de saída: o app lista as do próprio usuário; a Edge Function enviar-push entrega as com push.
create table public.notificacoes (
  id         uuid primary key default gen_random_uuid(),
  casa_id    uuid not null references public.casas (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  tipo       text not null,
  titulo     text not null,
  corpo      text not null,
  dados      jsonb not null default '{}'::jsonb,
  push       boolean not null default true,
  criada_em  timestamptz not null default now(),
  lida_em    timestamptz,
  enviada_em timestamptz,
  tentativas integer not null default 0,
  erro       text
);
create index notificacoes_usuario_idx on public.notificacoes (user_id, criada_em desc);
create index notificacoes_fila_idx on public.notificacoes (criada_em) where push and enviada_em is null;

------------------------------------------------------------------------------------------------
-- Auxiliares
------------------------------------------------------------------------------------------------

create function public._hoje(p_casa uuid)
returns date
language sql
stable
as $$
  select (now() at time zone fuso)::date from public.casas where id = p_casa
$$;

create function public._outro_membro(p_casa uuid, p_user uuid)
returns uuid
language sql
stable
as $$
  select user_id from public.membros where casa_id = p_casa and user_id <> p_user limit 1
$$;

create function public._nome(p_user uuid)
returns text
language sql
stable
as $$
  select coalesce((select nome from public.profiles where id = p_user), 'Alguém')
$$;

-- Reserva vale 24h; um Escudo empurra o prazo.
create function public._expira_reserva(p_reservada_em timestamptz, p_escudo_ate timestamptz)
returns timestamptz
language sql
immutable
as $$
  select greatest(p_reservada_em + interval '24 hours', coalesce(p_escudo_ate, p_reservada_em))
$$;

-- Chama uma Edge Function pelo pg_net, com URL e segredo guardados no Vault. Sem pg_net/Vault
-- (ambiente local de testes) ou sem os segredos, não faz nada.
create function public._chamar_funcao(p_nome text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_url     text;
  v_segredo text;
begin
  if to_regnamespace('net') is null or to_regclass('vault.decrypted_secrets') is null then
    return;
  end if;
  execute $q$select decrypted_secret from vault.decrypted_secrets where name = 'rivais_url'$q$ into v_url;
  execute $q$select decrypted_secret from vault.decrypted_secrets where name = 'rivais_cron_secret'$q$ into v_segredo;
  if v_url is null or v_segredo is null then
    return;
  end if;
  execute 'select net.http_post(url := $1, body := $2, headers := $3)'
    using rtrim(v_url, '/') || '/functions/v1/' || p_nome,
          '{}'::jsonb,
          jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_segredo);
exception when others then
  raise warning 'Falha ao chamar a função %: %', p_nome, sqlerrm;
end;
$$;

/**
 * Notifica um jogador. Sempre fica no histórico do app; o push depende do nível de provocação:
 * desligada não recebe push, moderada recebe os eventos 'moderada', sem piedade recebe tudo
 * (e no tom mais implicante quando houver um texto para isso).
 */
create function public._notificar(
  p_user        uuid,
  p_tipo        text,
  p_titulo      text,
  p_moderado    text,
  p_sem_piedade text default null,
  p_minimo      text default 'moderada',
  p_dados       jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_casa  uuid;
  v_nivel text;
begin
  if p_user is null then
    return;
  end if;
  select casa_id, nivel_provocacao into v_casa, v_nivel from public.membros where user_id = p_user;
  if not found then
    return;
  end if;
  insert into public.notificacoes (casa_id, user_id, tipo, titulo, corpo, dados, push)
  values (
    v_casa, p_user, p_tipo, p_titulo,
    case when v_nivel = 'sem_piedade' and p_sem_piedade is not null then p_sem_piedade else p_moderado end,
    p_dados,
    v_nivel <> 'desligada' and (p_minimo = 'moderada' or v_nivel = 'sem_piedade')
  );
end;
$$;

create function public._disparar_push()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if exists (select 1 from novas where push) then
    perform public._chamar_funcao('enviar-push');
  end if;
  return null;
end;
$$;

create trigger notificacoes_push
  after insert on public.notificacoes
  referencing new table as novas
  for each statement execute function public._disparar_push();

-- Líder estrito do placar (null se empate ou se só há um jogador).
create function public._lider(p_placar jsonb, p_casa uuid)
returns uuid
language sql
stable
as $$
  with pts as (
    select m.user_id, coalesce((p_placar ->> m.user_id::text)::integer, 0) as p
      from public.membros m where m.casa_id = p_casa
  )
  select case
    when (select count(*) from pts) < 2 then null
    when (select count(*) from pts where p = (select max(p) from pts)) > 1 then null
    else (select user_id from pts order by p desc limit 1)
  end
$$;

------------------------------------------------------------------------------------------------
-- Cartas: catálogo
------------------------------------------------------------------------------------------------

create function public.carta_custo(p_tipo text)
returns integer
language sql
immutable
as $$
  select case p_tipo
    when 'holofote' then 40 when 'escudo' then 50 when 'troca' then 60
    when 'dobradinha' then 80 when 'hora_feliz' then 90 when 'veto' then 120
  end
$$;

create function public.carta_raridade(p_tipo text)
returns text
language sql
immutable
as $$
  select case
    when p_tipo in ('holofote', 'escudo') then 'comum'
    when p_tipo in ('troca', 'dobradinha') then 'rara'
    when p_tipo in ('hora_feliz', 'veto') then 'epica'
  end
$$;

------------------------------------------------------------------------------------------------
-- Cálculo da conclusão (base x bomba x multiplicadores do jogador)
------------------------------------------------------------------------------------------------

/**
 * Extrato de quanto a tarefa vale para p_user agora: base, multiplicadores e total. Usado pela
 * prévia do sheet de concluir e pelo próprio concluir_tarefa (mesma conta nos dois).
 */
create function public._calcular_conclusao(p_tarefa public.tarefas, p_user uuid)
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

  -- Sequência: +5% por dia seguido com tarefa validada, até x1,3. Dia sem tarefa zera.
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

  v_total := public.calcular_pontos(p_tarefa.minutos, p_tarefa.esforco, p_tarefa.chatice, p_tarefa.dias_parada, v_fator);
  return jsonb_build_object('base', v_base, 'multiplicadores', v_mults, 'pontos', v_total, 'moedas', v_total / 10);
end;
$$;

create function public.previa_conclusao(p_tarefa uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
  v_tarefa public.tarefas;
begin
  select * into v_tarefa from public.tarefas where id = p_tarefa;
  if not found or not public.is_membro(v_tarefa.casa_id) then
    perform public._erro('Tarefa não encontrada.');
  end if;
  return public._calcular_conclusao(v_tarefa, auth.uid());
end;
$$;

------------------------------------------------------------------------------------------------
-- Conquistas
------------------------------------------------------------------------------------------------

create function public._avaliar_conquistas(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_casa    uuid;
  v_fuso    text;
  v_membro  public.membros;
  v_total   integer;
  v_banho   integer;
  v_cozinha integer;
  v_bombas  integer;
  v_roubos  integer;
  v_cedo    boolean;
  v_tarde   boolean;
  v_teto    boolean;
  v_vitorias integer;
  v_codigo  text;
  v_ok      boolean;
  v_nomes   jsonb := '{
    "primeira_faxina": "Primeira faxina", "centuriao": "Centurião", "rei_do_rejunte": "Rei do Rejunte",
    "madrugador": "Madrugador", "coruja": "Coruja", "desarma_bombas": "Desarma-bombas",
    "ladrao": "Ladrão de casaca", "em_chamas": "Em chamas", "mestre_cuca": "Mestre-cuca",
    "maratonista": "Maratonista", "campeao": "Campeão da semana", "tricampeao": "Tricampeão",
    "veterano": "Veterano"}';
begin
  select * into v_membro from public.membros where user_id = p_user;
  if not found then
    return;
  end if;
  v_casa := v_membro.casa_id;
  select fuso into v_fuso from public.casas where id = v_casa;

  select count(*),
         count(*) filter (where r.titulo ilike '%banheiro%' or t.icone = 'bathtub'),
         count(*) filter (where t.categoria = 'Cozinha'),
         count(*) filter (where r.multiplicadores @> '[{"tipo": "bomba"}]'),
         count(*) filter (where r.multiplicadores @> '[{"tipo": "roubo"}]'),
         bool_or(extract(hour from r.criado_em at time zone v_fuso) < 7),
         bool_or(extract(hour from r.criado_em at time zone v_fuso) >= 23),
         bool_or(r.pontos_previstos > r.pontos_finais)
    into v_total, v_banho, v_cozinha, v_bombas, v_roubos, v_cedo, v_tarde, v_teto
    from public.registros r
    left join public.tarefas t on t.id = r.tarefa_id
   where r.user_id = p_user and r.status = 'validado';

  select count(*) into v_vitorias from public.temporadas where vencedor = p_user;

  for v_codigo, v_ok in
    select * from (values
      ('primeira_faxina', v_total >= 1),
      ('centuriao', v_total >= 100),
      ('rei_do_rejunte', v_banho >= 10),
      ('madrugador', coalesce(v_cedo, false)),
      ('coruja', coalesce(v_tarde, false)),
      ('desarma_bombas', v_bombas >= 5),
      ('ladrao', v_roubos >= 3),
      ('em_chamas', v_membro.sequencia_dias >= 7),
      ('mestre_cuca', v_cozinha >= 20),
      ('maratonista', coalesce(v_teto, false)),
      ('campeao', v_vitorias >= 1),
      ('tricampeao', v_vitorias >= 3),
      ('veterano', v_membro.nivel >= 5)
    ) c(codigo, ok)
  loop
    if v_ok then
      insert into public.conquistas (user_id, codigo, casa_id) values (p_user, v_codigo, v_casa)
      on conflict do nothing;
      if found then
        perform public._notificar(p_user, 'conquista', 'Conquista desbloqueada',
          'Você ganhou "' || (v_nomes ->> v_codigo) || '".', null, 'moderada', jsonb_build_object('codigo', v_codigo));
      end if;
    end if;
  end loop;
end;
$$;

------------------------------------------------------------------------------------------------
-- Validação (substitui a da Fase 1)
------------------------------------------------------------------------------------------------

create or replace function public._aplicar_validacao(p_registro uuid, p_validador uuid)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_reg     public.registros;
  v_temp    public.temporadas;
  v_casa    public.casas;
  v_membro  public.membros;
  v_dia     date;
  v_uid     text;
  v_ja_hoje integer;
  v_final   integer;
  v_moedas  integer;
  v_obs     jsonb := '[]'::jsonb;
  v_meu     integer;
  v_outro   integer;
  v_lider_antes uuid;
  v_lider_depois uuid;
  v_meta_antes integer;
  v_seq     integer;
  v_parceiro uuid;
begin
  select * into v_reg from public.registros where id = p_registro for update;
  if not found or v_reg.status <> 'pendente' then
    return v_reg;
  end if;

  select * into v_casa from public.casas where id = v_reg.casa_id;
  v_dia := (v_reg.criado_em at time zone v_casa.fuso)::date;
  v_temp := public._temporada_atual(v_reg.casa_id);
  v_uid := v_reg.user_id::text;
  v_parceiro := public._outro_membro(v_reg.casa_id, v_reg.user_id);

  -- Teto diário: 400 pontos por pessoa por dia (dia em que a tarefa foi feita).
  select coalesce(sum(pontos_finais), 0) into v_ja_hoje
    from public.registros
   where user_id = v_reg.user_id and status = 'validado' and id <> v_reg.id
     and (criado_em at time zone v_casa.fuso)::date = v_dia;
  v_final := least(v_reg.pontos_finais, greatest(0, 400 - v_ja_hoje));
  if v_final < v_reg.pontos_finais then
    v_obs := v_obs || to_jsonb('Teto diário de 400 pontos: entraram ' || v_final || '.'::text);
  end if;
  v_moedas := v_final / 10;

  -- Recuperação: mais de 20% atrás no placar ganha moedas em dobro (não pontos).
  v_meu := coalesce((v_temp.placar ->> v_uid)::integer, 0);
  v_outro := coalesce((v_temp.placar ->> v_parceiro::text)::integer, 0);
  if not v_casa.modo_tregua and v_outro > 0 and v_meu < v_outro * 0.8 then
    v_moedas := v_moedas * 2;
    v_obs := v_obs || to_jsonb('Recuperação: moedas em dobro.'::text);
  end if;

  -- Trégua: a competição pausa; a tarefa conta só para a meta da casa (e para o XP pessoal).
  if v_casa.modo_tregua then
    v_moedas := 0;
    v_obs := v_obs || to_jsonb('Modo trégua: contou só para a meta da casa.'::text);
  end if;

  update public.registros
     set status = 'validado', validado_em = now(), validado_por = p_validador, temporada_id = v_temp.id,
         pontos_previstos = pontos_finais, pontos_finais = v_final, moedas = v_moedas, observacoes = v_obs
   where id = p_registro
   returning * into v_reg;

  v_lider_antes := public._lider(v_temp.placar, v_reg.casa_id);
  v_meta_antes := v_temp.meta_atual;
  update public.temporadas
     set placar = case when v_casa.modo_tregua then placar
                       else jsonb_set(placar, array[v_uid], to_jsonb(v_meu + v_final)) end,
         meta_atual = meta_atual + v_final,
         meta_batida_em = case when meta_batida_em is null and meta_atual + v_final >= meta_alvo then now() else meta_batida_em end
   where id = v_temp.id
   returning * into v_temp;
  v_lider_depois := public._lider(v_temp.placar, v_reg.casa_id);

  -- Sequência conta dias seguidos com pelo menos uma tarefa validada.
  select * into v_membro from public.membros where casa_id = v_reg.casa_id and user_id = v_reg.user_id for update;
  v_seq := case
    when v_membro.ultimo_dia_ativo is null or v_membro.ultimo_dia_ativo < v_dia - 1 then 1
    when v_membro.ultimo_dia_ativo = v_dia - 1 then v_membro.sequencia_dias + 1
    else greatest(v_membro.sequencia_dias, 1)
  end;
  update public.membros
     set xp = xp + v_final,
         nivel = 1 + (xp + v_final) / 1000,
         moedas = moedas + v_moedas,
         sequencia_dias = v_seq,
         ultimo_dia_ativo = greatest(coalesce(ultimo_dia_ativo, v_dia), v_dia)
   where casa_id = v_reg.casa_id and user_id = v_reg.user_id;

  update public.tarefas t
     set status = 'livre', reservada_por = null, reservada_em = null, dias_parada = 0,
         concluida_em = now(), ativa = t.recorrencia <> 'avulsa',
         prazo = public._proximo_prazo(t.recorrencia, v_casa.fuso, now()),
         roubada_de = null, escudo_ate = null, aviso_roubo_em = null
   where id = v_reg.tarefa_id and status = 'aguardando';

  -- Notificações.
  perform public._notificar(v_reg.user_id, 'validado',
    case when p_validador is null then 'Validado automaticamente' else public._nome(p_validador) || ' validou' end,
    v_reg.titulo || ': +' || v_final || ' pontos.', null, 'moderada', jsonb_build_object('registro', v_reg.id));

  if v_lider_depois is not null and v_lider_depois is distinct from v_lider_antes then
    perform public._notificar(v_lider_depois, 'virada', 'Você passou na frente',
      'Virada com ' || v_reg.titulo || '. Agora é segurar.',
      'Virada! Você passou na frente com ' || v_reg.titulo || '. Que comece a provocação.');
    perform public._notificar(public._outro_membro(v_reg.casa_id, v_lider_depois), 'virada', 'Virada no placar',
      public._nome(v_lider_depois) || ' passou na frente com ' || v_reg.titulo || '.',
      'Virada! ' || public._nome(v_lider_depois) || ' passou na frente fazendo ' || v_reg.titulo || '. Vai deixar barato?');
  end if;

  if v_meta_antes < v_temp.meta_alvo and v_temp.meta_atual >= v_temp.meta_alvo then
    perform public._notificar(v_reg.user_id, 'meta', 'Meta da casa batida',
      coalesce(v_temp.meta_premio, 'O prêmio coletivo') || ' garantido. Os dois ganham.');
    perform public._notificar(v_parceiro, 'meta', 'Meta da casa batida',
      coalesce(v_temp.meta_premio, 'O prêmio coletivo') || ' garantido. Os dois ganham.');
  end if;

  perform public._avaliar_conquistas(v_reg.user_id);
  return v_reg;
end;
$$;

------------------------------------------------------------------------------------------------
-- Fechamento da temporada (substitui o da Fase 1: trégua, notificações, conquistas)
------------------------------------------------------------------------------------------------

create or replace function public._fechar_temporada(p_temporada uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_temp   public.temporadas;
  v_reg    uuid;
  v_casa   public.casas;
  v_lim    record;
  v_venc   uuid;
  v_m      record;
  v_meu    integer;
  v_dele   integer;
begin
  select * into v_temp from public.temporadas where id = p_temporada for update;
  if not found or v_temp.encerrada then
    return;
  end if;
  select * into v_casa from public.casas where id = v_temp.casa_id;

  for v_reg in
    select id from public.registros
     where casa_id = v_temp.casa_id and status = 'pendente' and criado_em < v_temp.fim
     order by criado_em
  loop
    perform public._aplicar_validacao(v_reg, null);
  end loop;

  select * into v_temp from public.temporadas where id = p_temporada;
  v_venc := case when v_casa.modo_tregua then null else public._lider(v_temp.placar, v_temp.casa_id) end;

  update public.temporadas set encerrada = true, encerrada_em = now(), vencedor = v_venc where id = p_temporada;

  select * into v_lim from public._limites_temporada(v_casa.fuso, greatest(v_temp.fim, now()));
  insert into public.temporadas (casa_id, numero, inicio, fim, meta_alvo, meta_premio)
  values (v_temp.casa_id, v_temp.numero + 1, v_lim.inicio, v_lim.fim, v_temp.meta_alvo, v_temp.meta_premio);

  -- Vetos valem só até o fim da temporada.
  update public.tarefas set vetada_por = null, vetada_ate = null where casa_id = v_temp.casa_id and vetada_por is not null;

  for v_m in select user_id from public.membros where casa_id = v_temp.casa_id loop
    v_meu := coalesce((v_temp.placar ->> v_m.user_id::text)::integer, 0);
    v_dele := coalesce((v_temp.placar ->> public._outro_membro(v_temp.casa_id, v_m.user_id)::text)::integer, 0);
    if v_venc = v_m.user_id then
      perform public._notificar(v_m.user_id, 'fim_temporada', 'Vitória!',
        'Temporada ' || v_temp.numero || ': ' || v_meu || ' a ' || v_dele || '.' ||
        coalesce(' Prêmio: ' || v_temp.aposta || '.', ''),
        'Temporada ' || v_temp.numero || ' é sua: ' || v_meu || ' a ' || v_dele || '. Pode cobrar o prêmio.');
    elsif v_venc is not null then
      perform public._notificar(v_m.user_id, 'fim_temporada', 'Quase!',
        public._nome(v_venc) || ' levou a temporada ' || v_temp.numero || ' por ' || v_dele || ' a ' || v_meu || '. Semana que vem é sua.',
        public._nome(v_venc) || ' levou a temporada ' || v_temp.numero || ' por ' || v_dele || ' a ' || v_meu || '. A revanche começa agora.');
    else
      perform public._notificar(v_m.user_id, 'fim_temporada', 'Fim da temporada ' || v_temp.numero,
        case when v_casa.modo_tregua then 'Semana de trégua: ninguém leva a aposta.' else 'Empate! Ninguém leva a aposta.' end);
    end if;
    perform public._avaliar_conquistas(v_m.user_id);
  end loop;
end;
$$;

------------------------------------------------------------------------------------------------
-- RPCs do app (substituições e novas)
------------------------------------------------------------------------------------------------

drop function public.criar_casa(text);

create function public.criar_casa(p_nome text, p_exemplos boolean default true)
returns public.casas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid  uuid := auth.uid();
  v_casa public.casas;
begin
  if v_uid is null then
    perform public._erro('Faça login para continuar.');
  end if;
  if exists (select 1 from public.membros where user_id = v_uid) then
    perform public._erro('Você já participa de uma casa.');
  end if;
  if coalesce(trim(p_nome), '') = '' then
    perform public._erro('Dê um nome para a casa.');
  end if;

  insert into public.casas (nome, codigo_convite)
  values (left(trim(p_nome), 40), public._gerar_codigo())
  returning * into v_casa;

  insert into public.membros (casa_id, user_id, cor) values (v_casa.id, v_uid, 'azul');
  if p_exemplos then
    perform public._tarefas_iniciais(v_casa.id, v_uid);
  end if;
  perform public._temporada_atual(v_casa.id);
  return v_casa;
end;
$$;

create or replace function public.reservar_tarefa(p_tarefa uuid)
returns public.tarefas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_tarefa public.tarefas;
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
  if v_tarefa.status <> 'livre' then
    perform public._erro(case when v_tarefa.reservada_por = v_uid
      then 'Essa tarefa já é sua.' else 'Alguém chegou antes: a tarefa já está reservada.' end);
  end if;

  update public.tarefas
     set status = 'reservada', reservada_por = v_uid, reservada_em = now(), aviso_roubo_em = null, escudo_ate = null
   where id = p_tarefa
   returning * into v_tarefa;
  return v_tarefa;
end;
$$;

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

  -- A Dobradinha vale para uma tarefa só.
  update public.membros set dobradinha = false where user_id = v_uid and dobradinha;

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

create or replace function public.contestar_registro(p_registro uuid, p_comentario text)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid  uuid := auth.uid();
  v_reg  public.registros;
  v_temp public.temporadas;
  v_usadas integer;
begin
  select * into v_reg from public.registros where id = p_registro for update;
  if not found or not public.is_membro(v_reg.casa_id) then
    perform public._erro('Registro não encontrado.');
  end if;
  if v_reg.user_id = v_uid then
    perform public._erro('Você não pode contestar a própria tarefa.');
  end if;
  if v_reg.status <> 'pendente' then
    perform public._erro('Esse registro já foi resolvido.');
  end if;
  if char_length(coalesce(trim(p_comentario), '')) < 3 then
    perform public._erro('Conte o que ficou faltando.');
  end if;

  v_temp := public._temporada_atual(v_reg.casa_id);
  select count(*) into v_usadas from public.registros
   where casa_id = v_reg.casa_id and contestado_por = v_uid and contestado_em >= v_temp.inicio;
  if v_usadas >= 2 then
    perform public._erro('Você já usou as 2 contestações desta semana.');
  end if;

  update public.registros
     set status = 'contestado', comentario = left(trim(p_comentario), 200), contestado_por = v_uid, contestado_em = now()
   where id = p_registro
   returning * into v_reg;

  update public.tarefas
     set status = 'livre', reservada_por = null, reservada_em = null
   where id = v_reg.tarefa_id and status = 'aguardando';

  perform public._notificar(v_reg.user_id, 'contestado', public._nome(v_uid) || ' contestou ' || v_reg.titulo,
    '"' || v_reg.comentario || '" A tarefa voltou pra fila.', null, 'moderada', jsonb_build_object('registro', v_reg.id));
  return v_reg;
end;
$$;

create function public.marcar_notificacoes_lidas()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
  update public.notificacoes set lida_em = now() where user_id = auth.uid() and lida_em is null
$$;

-- Cartas -------------------------------------------------------------------------------------

drop function public.comprar_carta(text);
drop function public.usar_carta(uuid, uuid);

create function public.comprar_carta(p_tipo text)
returns public.cartas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_membro public.membros;
  v_custo  integer := public.carta_custo(p_tipo);
  v_carta  public.cartas;
begin
  select * into v_membro from public.membros where user_id = v_uid for update;
  if not found then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  if v_custo is null then
    perform public._erro('Carta desconhecida.');
  end if;
  if (select count(*) from public.cartas where dono = v_uid and usada_em is null) >= 3 then
    perform public._erro('Sua mão já tem 3 cartas. Use uma antes de comprar outra.');
  end if;
  if v_membro.moedas < v_custo then
    perform public._erro('Faltam ' || (v_custo - v_membro.moedas) || ' moedas para essa carta.');
  end if;

  update public.membros set moedas = moedas - v_custo where user_id = v_uid;
  insert into public.cartas (casa_id, dono, tipo, raridade)
  values (v_membro.casa_id, v_uid, p_tipo, public.carta_raridade(p_tipo))
  returning * into v_carta;
  return v_carta;
end;
$$;

create function public._executar_troca(p_troca uuid, p_automatica boolean)
returns public.trocas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_troca public.trocas;
  v_a     public.tarefas;
  v_b     public.tarefas;
begin
  select * into v_troca from public.trocas where id = p_troca for update;
  select * into v_a from public.tarefas where id = v_troca.tarefa_minha for update;
  select * into v_b from public.tarefas where id = v_troca.tarefa_dele for update;
  -- Só troca se as duas reservas ainda estão de pé.
  if v_a.status <> 'reservada' or v_a.reservada_por <> v_troca.proposta_por
     or v_b.status <> 'reservada' or v_b.reservada_por <> v_troca.para then
    update public.trocas set status = 'cancelada', respondida_em = now() where id = p_troca returning * into v_troca;
    return v_troca;
  end if;
  update public.tarefas set reservada_por = v_troca.para, reservada_em = now(), escudo_ate = null, aviso_roubo_em = null where id = v_a.id;
  update public.tarefas set reservada_por = v_troca.proposta_por, reservada_em = now(), escudo_ate = null, aviso_roubo_em = null where id = v_b.id;
  update public.trocas set status = 'aceita', automatica = p_automatica, respondida_em = now() where id = p_troca returning * into v_troca;
  perform public._notificar(v_troca.proposta_por, 'troca', 'Troca feita',
    'Agora ' || v_b.titulo || ' é sua, e ' || v_a.titulo || ' ficou com ' || public._nome(v_troca.para) || '.');
  if p_automatica then
    perform public._notificar(v_troca.para, 'troca', 'Troca feita',
      'Você ficou com ' || v_a.titulo || ' no lugar de ' || v_b.titulo || '.',
      'Troca na marra: você ficou com ' || v_a.titulo || ' no lugar de ' || v_b.titulo || '.');
  end if;
  return v_troca;
end;
$$;

create function public._recusas_na_semana(p_user uuid, p_casa uuid)
returns integer
language sql
stable
as $$
  select count(*)::integer from public.trocas
   where para = p_user and status = 'recusada'
     and respondida_em >= (select inicio from public.temporadas where casa_id = p_casa and not encerrada)
$$;

/**
 * Usa uma carta da mão (uma por dia). Alvos: holofote (qualquer tarefa), escudo (reserva sua),
 * troca (p_alvo = reserva sua, p_alvo2 = reserva da outra pessoa). Dobradinha, Hora Feliz e Veto
 * não pedem alvo (o Veto escolhe a tarefa mais chata).
 */
create function public.usar_carta(p_carta uuid, p_alvo uuid default null, p_alvo2 uuid default null)
returns public.cartas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_carta  public.cartas;
  v_casa   public.casas;
  v_tarefa public.tarefas;
  v_outra  public.tarefas;
  v_temp   public.temporadas;
  v_outro  uuid;
  v_troca  public.trocas;
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
      -- Quem recebe pode recusar uma troca por semana; depois disso ela acontece direto.
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
      v_temp := public._temporada_atual(v_casa.id);
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
  end case;

  update public.cartas set usada_em = now(), alvo = p_alvo where id = p_carta returning * into v_carta;
  return v_carta;
end;
$$;

create function public.responder_troca(p_troca uuid, p_aceitar boolean)
returns public.trocas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_troca public.trocas;
begin
  select * into v_troca from public.trocas where id = p_troca and para = auth.uid() for update;
  if not found or v_troca.status <> 'pendente' then
    perform public._erro('Essa troca não está mais esperando resposta.');
  end if;
  if p_aceitar then
    return public._executar_troca(p_troca, false);
  end if;
  if public._recusas_na_semana(auth.uid(), v_troca.casa_id) >= 1 then
    perform public._erro('Você já recusou uma troca nesta semana. Esta precisa ser aceita.');
  end if;
  update public.trocas set status = 'recusada', respondida_em = now() where id = p_troca returning * into v_troca;
  perform public._notificar(v_troca.proposta_por, 'troca', 'Troca recusada',
    public._nome(v_troca.para) || ' recusou a troca.', public._nome(v_troca.para) || ' recusou a troca. Esta semana não rola mais.');
  return v_troca;
end;
$$;

------------------------------------------------------------------------------------------------
-- Rotinas agendadas (substituições e novas)
------------------------------------------------------------------------------------------------

-- Conta os dias de atraso e avisa quando a tarefa vira bomba.
create or replace function public.cron_atualizar_bombas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_t record;
  v_m uuid;
  v_n integer := 0;
begin
  for v_t in
    with alvo as (
      select t.id, t.dias_parada as antes,
             greatest(0, (now() at time zone c.fuso)::date - (t.prazo at time zone c.fuso)::date) as depois
        from public.tarefas t
        join public.casas c on c.id = t.casa_id
       where t.ativa and t.prazo is not null and t.prazo < now()
    ), mudou as (
      update public.tarefas t
         set dias_parada = a.depois
        from alvo a
       where t.id = a.id and a.depois <> a.antes
      returning t.id, t.casa_id, t.titulo, a.antes, a.depois
    )
    select * from mudou
  loop
    v_n := v_n + 1;
    if v_t.antes = 0 and v_t.depois >= 1 then
      for v_m in select user_id from public.membros where casa_id = v_t.casa_id loop
        perform public._notificar(v_m, 'bomba', v_t.titulo || ' virou bomba',
          'Parada há ' || v_t.depois || case when v_t.depois = 1 then ' dia' else ' dias' end || ': agora vale mais, e sobe a cada dia.',
          v_t.titulo || ' virou bomba. Quem desarma leva o bônus; quem enrola, leva a fama.',
          'sem_piedade', jsonb_build_object('tarefa', v_t.id));
      end loop;
    end if;
  end loop;
  return v_n;
end;
$$;

-- Reserva vencida volta para a fila; quem largou perde a sequência e quem pegar ganha +25%.
create or replace function public.cron_liberar_reservas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_t record;
  v_n integer := 0;
begin
  -- Aviso de 1h antes.
  for v_t in
    update public.tarefas
       set aviso_roubo_em = now()
     where status = 'reservada' and aviso_roubo_em is null
       and public._expira_reserva(reservada_em, escudo_ate) between now() and now() + interval '1 hour'
    returning id, casa_id, titulo, reservada_por
  loop
    perform public._notificar(v_t.reservada_por, 'roubavel', 'Corre: ' || v_t.titulo,
      'Fica livre em 1h se você não concluir.', 'Em 1h ' || v_t.titulo || ' vira presa fácil. Vai largar?');
    perform public._notificar(public._outro_membro(v_t.casa_id, v_t.reservada_por), 'roubavel', 'Dá pra roubar em 1h',
      v_t.titulo || ' de ' || public._nome(v_t.reservada_por) || ' fica livre em 1h.',
      'Em 1h você pode roubar ' || v_t.titulo || ' de ' || public._nome(v_t.reservada_por) || ' e levar +25%.', 'sem_piedade');
  end loop;

  for v_t in
    update public.tarefas
       set status = 'livre', roubada_de = reservada_por, reservada_por = null, reservada_em = null,
           escudo_ate = null, aviso_roubo_em = null
     where status = 'reservada' and public._expira_reserva(reservada_em, escudo_ate) < now()
    returning id, casa_id, titulo, roubada_de
  loop
    v_n := v_n + 1;
    update public.membros set sequencia_dias = 0 where user_id = v_t.roubada_de;
    perform public._notificar(public._outro_membro(v_t.casa_id, v_t.roubada_de), 'roubo_liberado', v_t.titulo || ' está livre',
      public._nome(v_t.roubada_de) || ' largou. Quem fizer agora ganha +25%.',
      public._nome(v_t.roubada_de) || ' largou ' || v_t.titulo || '. Rouba e leva +25%.');
    perform public._notificar(v_t.roubada_de, 'roubo_liberado', 'Você largou ' || v_t.titulo,
      'A reserva venceu e sua sequência zerou.', 'Reserva vencida, sequência zerada. Agora ' || v_t.titulo || ' vale +25% pra outra pessoa.',
      'moderada');
  end loop;
  return v_n;
end;
$$;

-- Dia sem tarefa validada zera a sequência.
create function public.cron_sequencias()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_n integer;
begin
  update public.membros m
     set sequencia_dias = 0
    from public.casas c
   where c.id = m.casa_id and m.sequencia_dias > 0
     and (m.ultimo_dia_ativo is null or m.ultimo_dia_ativo < (now() at time zone c.fuso)::date - 1);
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

-- Troca sem resposta em 12h acontece sozinha.
create function public.cron_trocas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid;
  v_n  integer := 0;
begin
  for v_id in select id from public.trocas where status = 'pendente' and criada_em < now() - interval '12 hours' loop
    perform public._executar_troca(v_id, true);
    v_n := v_n + 1;
  end loop;
  return v_n;
end;
$$;

------------------------------------------------------------------------------------------------
-- Permissões das tabelas e funções novas
------------------------------------------------------------------------------------------------

revoke all on public.trocas, public.conquistas, public.notificacoes from anon, authenticated;
revoke all on all functions in schema public from public, anon;
revoke execute on all functions in schema public from authenticated;

alter table public.trocas       enable row level security;
alter table public.conquistas   enable row level security;
alter table public.notificacoes enable row level security;

grant select on public.trocas, public.conquistas, public.notificacoes to authenticated;
create policy "trocas: mesma casa" on public.trocas for select to authenticated using (public.is_membro(casa_id));
create policy "conquistas: mesma casa" on public.conquistas for select to authenticated using (public.is_membro(casa_id));
create policy "notificacoes: só as minhas" on public.notificacoes for select to authenticated using (user_id = auth.uid());

grant execute on function
  public.calcular_pontos(integer, smallint, numeric, integer, numeric),
  public.multiplicador_esforco(smallint),
  public.bonus_bomba(integer),
  public.carta_custo(text),
  public.carta_raridade(text),
  public.minha_casa(),
  public.is_membro(uuid),
  public.criar_casa(text, boolean),
  public.entrar_casa(text),
  public.sair_da_casa(),
  public.definir_cor(text),
  public.reservar_tarefa(uuid),
  public.liberar_tarefa(uuid),
  public.previa_conclusao(uuid),
  public.concluir_tarefa(uuid, text),
  public.validar_registro(uuid),
  public.contestar_registro(uuid, text),
  public.definir_aposta(text),
  public.definir_meta(integer, text),
  public.resgatar_premio(uuid),
  public.comprar_carta(text),
  public.usar_carta(uuid, uuid, uuid),
  public.responder_troca(uuid, boolean),
  public.marcar_notificacoes_lidas()
to authenticated;

do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table public.cartas, public.trocas, public.conquistas, public.notificacoes;
  end if;
end;
$$;

------------------------------------------------------------------------------------------------
-- Fotos do depois (Supabase Storage). Pasta: <casa>/<usuário>/<arquivo>.jpg
------------------------------------------------------------------------------------------------

do $$
begin
  if to_regclass('storage.buckets') is not null then
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('fotos', 'fotos', false, 5242880, array['image/jpeg'])
    on conflict (id) do nothing;

    execute $p$
      create policy "fotos: a casa vê" on storage.objects for select to authenticated
      using (bucket_id = 'fotos' and (storage.foldername(name))[1] = public.minha_casa()::text)
    $p$;
    execute $p$
      create policy "fotos: cada um envia as suas" on storage.objects for insert to authenticated
      with check (bucket_id = 'fotos'
                  and (storage.foldername(name))[1] = public.minha_casa()::text
                  and (storage.foldername(name))[2] = auth.uid()::text)
    $p$;
  end if;
end;
$$;

------------------------------------------------------------------------------------------------
-- Agendamentos novos
------------------------------------------------------------------------------------------------

select cron.schedule('rivais-sequencias', '17 * * * *', 'select public.cron_sequencias()');
select cron.schedule('rivais-trocas', '*/10 * * * *', 'select public.cron_trocas()');
-- Reenvio de push que falhou e limpeza das fotos com mais de 30 dias (Edge Functions).
select cron.schedule('rivais-push', '*/5 * * * *', 'select public._chamar_funcao(''enviar-push'')');
select cron.schedule('rivais-limpar-fotos', '23 4 * * *', 'select public._chamar_funcao(''limpar-fotos'')');
