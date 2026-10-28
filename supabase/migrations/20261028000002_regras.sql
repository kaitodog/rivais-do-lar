-- Regras do jogo. Toda escrita de pontos, moedas, XP, placar e status de tarefa passa por aqui.
-- Funções com prefixo "_" são internas (sem grant para o app). As demais são RPCs do app.

------------------------------------------------------------------------------------------------
-- Fórmula
------------------------------------------------------------------------------------------------

create function public.multiplicador_esforco(p_esforco smallint)
returns numeric
language sql
immutable
as $$
  select case p_esforco when 0 then 1.0 when 1 then 1.5 when 2 then 2.0 end
$$;

create function public.bonus_bomba(p_dias_parada integer)
returns numeric
language sql
immutable
as $$
  select least(1 + 0.1 * greatest(p_dias_parada, 0), 2.0)
$$;

-- pontos = minutos × esforço × chatice × bônusBomba × multiplicadores, múltiplo de 5 mais próximo, mínimo 5.
-- O app (Pontuacao.kt) replica esta conta só para exibir; o valor que vale é o daqui.
create function public.calcular_pontos(
  p_minutos integer,
  p_esforco smallint,
  p_chatice numeric,
  p_dias_parada integer default 0,
  p_multiplicador numeric default 1
)
returns integer
language sql
immutable
as $$
  select greatest(
    5,
    (round(
      p_minutos * public.multiplicador_esforco(p_esforco) * p_chatice
        * public.bonus_bomba(p_dias_parada) * p_multiplicador / 5.0
    ) * 5)::integer
  )
$$;

------------------------------------------------------------------------------------------------
-- Auxiliares
------------------------------------------------------------------------------------------------

create function public._erro(p_mensagem text)
returns void
language plpgsql
as $$
begin
  raise exception using message = p_mensagem, errcode = 'P0001';
end;
$$;

create function public.minha_casa()
returns uuid
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select casa_id from public.membros where user_id = auth.uid()
$$;

create function public.is_membro(p_casa uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (select 1 from public.membros where casa_id = p_casa and user_id = auth.uid())
$$;

-- Semana da temporada no fuso da casa: segunda 00:00 até domingo 22:00.
-- Entre domingo 22:00 e segunda 00:00 o horário já pertence à semana seguinte.
create function public._limites_temporada(p_fuso text, p_ref timestamptz, out inicio timestamptz, out fim timestamptz)
language plpgsql
stable
as $$
declare
  v_local   timestamp := p_ref at time zone p_fuso;
  v_segunda timestamp := date_trunc('week', v_local);
begin
  if v_local >= v_segunda + interval '6 days 22 hours' then
    v_segunda := v_segunda + interval '7 days';
  end if;
  inicio := v_segunda at time zone p_fuso;
  fim := (v_segunda + interval '6 days 22 hours') at time zone p_fuso;
end;
$$;

-- Temporada aberta da casa, criando a primeira se ainda não houver. Trava a linha.
create function public._temporada_atual(p_casa uuid)
returns public.temporadas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_temp public.temporadas;
  v_fuso text;
  v_lim  record;
begin
  select * into v_temp from public.temporadas
   where casa_id = p_casa and not encerrada
   for update;
  if found then
    return v_temp;
  end if;

  select fuso into v_fuso from public.casas where id = p_casa;
  select * into v_lim from public._limites_temporada(v_fuso, now());
  insert into public.temporadas (casa_id, numero, inicio, fim)
  values (
    p_casa,
    coalesce((select max(numero) from public.temporadas where casa_id = p_casa), 0) + 1,
    v_lim.inicio,
    v_lim.fim
  )
  returning * into v_temp;
  return v_temp;
end;
$$;

create function public._proximo_prazo(p_recorrencia text, p_fuso text, p_ref timestamptz)
returns timestamptz
language sql
stable
as $$
  select case p_recorrencia
    when 'avulsa' then null
    else ((
      (p_ref at time zone p_fuso)::date
      + case p_recorrencia
          when 'diaria' then interval '1 day'
          when '3x_semana' then interval '2 days'
          when '2x_semana' then interval '3 days'
          when 'semanal' then interval '7 days'
          when 'quinzenal' then interval '14 days'
          when 'mensal' then interval '1 month'
        end
      + interval '23 hours 59 minutes'
    ) at time zone p_fuso)
  end
$$;

create function public._gerar_codigo()
returns char(6)
language plpgsql
as $$
declare
  v_codigo char(6);
begin
  loop
    v_codigo := lpad((floor(random() * 1000000))::integer::text, 6, '0');
    exit when not exists (select 1 from public.casas where codigo_convite = v_codigo);
  end loop;
  return v_codigo;
end;
$$;

-- Lista inicial de uma casa nova (pode ser editada depois).
create function public._tarefas_iniciais(p_casa uuid, p_autor uuid)
returns void
language sql
as $$
  insert into public.tarefas (casa_id, titulo, categoria, icone, minutos, esforco, chatice, recorrencia, criada_por)
  values
    (p_casa, 'Lavar a louça do jantar',        'Cozinha',      'restaurant',            20, 1, 1.3, 'diaria',    p_autor),
    (p_casa, 'Limpar banheiro completo',       'Pesada',       'bathtub',               45, 2, 1.5, 'semanal',   p_autor),
    (p_casa, 'Limpar a gaiola dos porquinhos', 'Pets',         'pets',                  25, 1, 1.6, '2x_semana', p_autor),
    (p_casa, 'Estender e recolher roupa',      'Lavanderia',   'checkroom',             15, 1, 1.1, '3x_semana', p_autor),
    (p_casa, 'Tirar o lixo',                   'Rápida',       'delete',                 5, 1, 1.3, 'diaria',    p_autor),
    (p_casa, 'Mercado da semana',              'Rua',          'shopping_cart',         60, 1, 1.0, 'semanal',   p_autor),
    (p_casa, 'Planejar o cardápio',            'Carga mental', 'edit_note',             20, 0, 1.5, 'semanal',   p_autor),
    (p_casa, 'Aspirar a sala',                 'Limpeza',      'mop',                   25, 1, 1.2, 'semanal',   p_autor),
    (p_casa, 'Trocar toalhas',                 'Rápida',       'dry_cleaning',           5, 1, 1.0, 'semanal',   p_autor),
    (p_casa, 'Regar plantas',                  'Rápida',       'potted_plant',          10, 1, 1.0, '3x_semana', p_autor)
$$;

-- Validação efetiva de um registro (pelo outro jogador, pelo cron de 12h ou no fechamento).
create function public._aplicar_validacao(p_registro uuid, p_validador uuid)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_reg  public.registros;
  v_temp public.temporadas;
  v_fuso text;
  v_hoje date;
  v_uid  text;
begin
  select * into v_reg from public.registros where id = p_registro for update;
  if not found or v_reg.status <> 'pendente' then
    return v_reg;
  end if;

  select fuso into v_fuso from public.casas where id = v_reg.casa_id;
  v_hoje := (now() at time zone v_fuso)::date;
  v_temp := public._temporada_atual(v_reg.casa_id);
  v_uid := v_reg.user_id::text;

  update public.registros
     set status = 'validado', validado_em = now(), validado_por = p_validador, temporada_id = v_temp.id
   where id = p_registro
   returning * into v_reg;

  update public.temporadas
     set placar = jsonb_set(placar, array[v_uid], to_jsonb(coalesce((placar ->> v_uid)::integer, 0) + v_reg.pontos_finais)),
         meta_atual = meta_atual + v_reg.pontos_finais
   where id = v_temp.id;

  update public.membros
     set xp = xp + v_reg.pontos_finais,
         nivel = 1 + (xp + v_reg.pontos_finais) / 1000,
         moedas = moedas + v_reg.moedas,
         ultimo_dia_ativo = v_hoje
   where casa_id = v_reg.casa_id and user_id = v_reg.user_id;

  update public.tarefas t
     set status = 'livre',
         reservada_por = null,
         reservada_em = null,
         dias_parada = 0,
         concluida_em = now(),
         ativa = t.recorrencia <> 'avulsa',
         prazo = public._proximo_prazo(t.recorrencia, v_fuso, now())
   where id = v_reg.tarefa_id and status = 'aguardando';

  return v_reg;
end;
$$;

create function public._fechar_temporada(p_temporada uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_temp    public.temporadas;
  v_reg     uuid;
  v_lider   uuid;
  v_maximo  integer;
  v_empate  boolean;
  v_fuso    text;
  v_lim     record;
begin
  select * into v_temp from public.temporadas where id = p_temporada for update;
  if not found or v_temp.encerrada then
    return;
  end if;

  -- O que foi concluído antes do apito final entra nesta temporada.
  for v_reg in
    select id from public.registros
     where casa_id = v_temp.casa_id and status = 'pendente' and criado_em < v_temp.fim
     order by criado_em
  loop
    perform public._aplicar_validacao(v_reg, null);
  end loop;

  select * into v_temp from public.temporadas where id = p_temporada;

  select m.user_id, coalesce((v_temp.placar ->> m.user_id::text)::integer, 0)
    into v_lider, v_maximo
    from public.membros m
   where m.casa_id = v_temp.casa_id
   order by 2 desc
   limit 1;

  select count(*) > 1 into v_empate
    from public.membros m
   where m.casa_id = v_temp.casa_id
     and coalesce((v_temp.placar ->> m.user_id::text)::integer, 0) = v_maximo;

  update public.temporadas
     set encerrada = true,
         encerrada_em = now(),
         vencedor = case
           when v_empate or coalesce(v_maximo, 0) = 0
             or (select count(*) from public.membros where casa_id = v_temp.casa_id) < 2
           then null
           else v_lider
         end
   where id = p_temporada;

  select fuso into v_fuso from public.casas where id = v_temp.casa_id;
  select * into v_lim from public._limites_temporada(v_fuso, greatest(v_temp.fim, now()));
  insert into public.temporadas (casa_id, numero, inicio, fim, meta_alvo, meta_premio)
  values (v_temp.casa_id, v_temp.numero + 1, v_lim.inicio, v_lim.fim, v_temp.meta_alvo, v_temp.meta_premio);
end;
$$;

------------------------------------------------------------------------------------------------
-- RPCs do app
------------------------------------------------------------------------------------------------

create function public.criar_casa(p_nome text)
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
  perform public._tarefas_iniciais(v_casa.id, v_uid);
  perform public._temporada_atual(v_casa.id);
  return v_casa;
end;
$$;

create function public.entrar_casa(p_codigo text)
returns public.casas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid  uuid := auth.uid();
  v_casa public.casas;
  v_cor  text;
begin
  if v_uid is null then
    perform public._erro('Faça login para continuar.');
  end if;
  if exists (select 1 from public.membros where user_id = v_uid) then
    perform public._erro('Você já participa de uma casa.');
  end if;

  select * into v_casa from public.casas where codigo_convite = trim(p_codigo) for update;
  if not found then
    perform public._erro('Código de convite não encontrado.');
  end if;
  if (select count(*) from public.membros where casa_id = v_casa.id) >= 2 then
    perform public._erro('Essa casa já tem dois jogadores.');
  end if;

  select c into v_cor from unnest(array['azul', 'laranja']) c
   where c not in (select cor from public.membros where casa_id = v_casa.id)
   limit 1;
  insert into public.membros (casa_id, user_id, cor) values (v_casa.id, v_uid, v_cor);
  return v_casa;
end;
$$;

create function public.sair_da_casa()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid  uuid := auth.uid();
  v_casa uuid := public.minha_casa();
begin
  if v_casa is null then
    return;
  end if;
  update public.tarefas set status = 'livre', reservada_por = null, reservada_em = null
   where casa_id = v_casa and reservada_por = v_uid and status = 'reservada';
  delete from public.membros where casa_id = v_casa and user_id = v_uid;
  -- Casa vazia não tem por que existir.
  delete from public.casas c where c.id = v_casa and not exists (select 1 from public.membros where casa_id = v_casa);
end;
$$;

create function public.definir_cor(p_cor text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid   uuid := auth.uid();
  v_casa  uuid := public.minha_casa();
  v_atual text;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  if p_cor not in ('azul', 'laranja') then
    perform public._erro('Cor inválida.');
  end if;
  select cor into v_atual from public.membros where casa_id = v_casa and user_id = v_uid for update;
  if v_atual = p_cor then
    return;
  end if;
  -- As duas cores trocam de dono no mesmo update; a unicidade só é conferida no fim da transação.
  set constraints public.membros_cor_unica deferred;
  update public.membros m
     set cor = case when m.user_id = v_uid then p_cor else v_atual end
   where m.casa_id = v_casa;
end;
$$;

create function public.reservar_tarefa(p_tarefa uuid)
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
    perform public._erro('Essa tarefa foi arquivada.');
  end if;
  if v_tarefa.status <> 'livre' then
    perform public._erro(case when v_tarefa.reservada_por = v_uid
      then 'Essa tarefa já é sua.' else 'Alguém chegou antes: a tarefa já está reservada.' end);
  end if;

  update public.tarefas
     set status = 'reservada', reservada_por = v_uid, reservada_em = now()
   where id = p_tarefa
   returning * into v_tarefa;
  return v_tarefa;
end;
$$;

create function public.liberar_tarefa(p_tarefa uuid)
returns public.tarefas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_tarefa public.tarefas;
begin
  update public.tarefas
     set status = 'livre', reservada_por = null, reservada_em = null
   where id = p_tarefa and status = 'reservada' and reservada_por = auth.uid()
   returning * into v_tarefa;
  if not found then
    perform public._erro('Só dá pra largar uma tarefa reservada por você.');
  end if;
  return v_tarefa;
end;
$$;

create function public.concluir_tarefa(p_tarefa uuid, p_foto_path text default null)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid    uuid := auth.uid();
  v_tarefa public.tarefas;
  v_base   integer;
  v_final  integer;
  v_mult   jsonb := '[]'::jsonb;
  v_reg    public.registros;
begin
  select * into v_tarefa from public.tarefas where id = p_tarefa for update;
  if not found or not public.is_membro(v_tarefa.casa_id) then
    perform public._erro('Tarefa não encontrada.');
  end if;
  if not v_tarefa.ativa then
    perform public._erro('Essa tarefa foi arquivada.');
  end if;
  if v_tarefa.status = 'aguardando' then
    perform public._erro('Essa tarefa já está esperando validação.');
  end if;
  if v_tarefa.status = 'reservada' and v_tarefa.reservada_por <> v_uid then
    perform public._erro('Essa tarefa está reservada pela outra pessoa.');
  end if;

  v_base := public.calcular_pontos(v_tarefa.minutos, v_tarefa.esforco, v_tarefa.chatice);
  v_final := public.calcular_pontos(v_tarefa.minutos, v_tarefa.esforco, v_tarefa.chatice, v_tarefa.dias_parada);
  if v_tarefa.dias_parada > 0 then
    v_mult := v_mult || jsonb_build_object(
      'tipo', 'bomba',
      'rotulo', 'Bomba · parada há ' || v_tarefa.dias_parada || case when v_tarefa.dias_parada = 1 then ' dia' else ' dias' end,
      'valor', public.bonus_bomba(v_tarefa.dias_parada)
    );
  end if;

  insert into public.registros (casa_id, tarefa_id, user_id, titulo, pontos_base, multiplicadores, pontos_finais, moedas, foto_path)
  values (v_tarefa.casa_id, v_tarefa.id, v_uid, v_tarefa.titulo, v_base, v_mult, v_final, v_final / 10, p_foto_path)
  returning * into v_reg;

  update public.tarefas
     set status = 'aguardando', reservada_por = v_uid, reservada_em = coalesce(reservada_em, now())
   where id = p_tarefa;
  return v_reg;
end;
$$;

create function public.validar_registro(p_registro uuid)
returns public.registros
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_reg public.registros;
begin
  select * into v_reg from public.registros where id = p_registro;
  if not found or not public.is_membro(v_reg.casa_id) then
    perform public._erro('Registro não encontrado.');
  end if;
  if v_reg.user_id = auth.uid() then
    perform public._erro('Quem valida é a outra pessoa.');
  end if;
  if v_reg.status <> 'pendente' then
    perform public._erro('Esse registro já foi resolvido.');
  end if;
  return public._aplicar_validacao(p_registro, auth.uid());
end;
$$;

create function public.contestar_registro(p_registro uuid, p_comentario text)
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

  -- "Volta pra fila": a tarefa fica livre de novo.
  update public.tarefas
     set status = 'livre', reservada_por = null, reservada_em = null
   where id = v_reg.tarefa_id and status = 'aguardando';
  return v_reg;
end;
$$;

create function public.definir_aposta(p_aposta text)
returns public.temporadas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_casa uuid := public.minha_casa();
  v_temp public.temporadas;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  v_temp := public._temporada_atual(v_casa);
  update public.temporadas set aposta = nullif(left(trim(p_aposta), 120), '')
   where id = v_temp.id
   returning * into v_temp;
  return v_temp;
end;
$$;

create function public.definir_meta(p_alvo integer, p_premio text)
returns public.temporadas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_casa uuid := public.minha_casa();
  v_temp public.temporadas;
begin
  if v_casa is null then
    perform public._erro('Você ainda não está em uma casa.');
  end if;
  if p_alvo is null or p_alvo < 100 or p_alvo > 100000 then
    perform public._erro('A meta precisa ficar entre 100 e 100.000 pontos.');
  end if;
  v_temp := public._temporada_atual(v_casa);
  update public.temporadas set meta_alvo = p_alvo, meta_premio = nullif(left(trim(p_premio), 60), '')
   where id = v_temp.id
   returning * into v_temp;
  return v_temp;
end;
$$;

create function public.resgatar_premio(p_temporada uuid)
returns public.temporadas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_temp public.temporadas;
begin
  update public.temporadas
     set premio_resgatado = true
   where id = p_temporada and encerrada and vencedor = auth.uid()
   returning * into v_temp;
  if not found then
    perform public._erro('Só quem venceu a temporada pode resgatar o prêmio.');
  end if;
  return v_temp;
end;
$$;

-- Cartas de poder chegam na Fase 3; as RPCs já existem para o contrato do app ficar estável.
create function public.comprar_carta(p_tipo text)
returns public.cartas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public._erro('As cartas de poder chegam numa próxima versão.');
  return null;
end;
$$;

create function public.usar_carta(p_carta uuid, p_alvo uuid default null)
returns public.cartas
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public._erro('As cartas de poder chegam numa próxima versão.');
  return null;
end;
$$;

------------------------------------------------------------------------------------------------
-- Rotinas agendadas (pg_cron, ver 20261028000004_cron.sql)
------------------------------------------------------------------------------------------------

create function public.cron_fechar_temporadas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid;
  v_n  integer := 0;
begin
  for v_id in select id from public.temporadas where not encerrada and fim <= now() loop
    perform public._fechar_temporada(v_id);
    v_n := v_n + 1;
  end loop;
  return v_n;
end;
$$;

-- Dias inteiros de atraso no fuso da casa (+10% por dia, teto x2 no cálculo).
create function public.cron_atualizar_bombas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_n integer;
begin
  update public.tarefas t
     set dias_parada = greatest(0, (now() at time zone c.fuso)::date - (t.prazo at time zone c.fuso)::date)
    from public.casas c
   where c.id = t.casa_id
     and t.ativa
     and t.prazo is not null
     and t.prazo < now()
     and t.dias_parada <> greatest(0, (now() at time zone c.fuso)::date - (t.prazo at time zone c.fuso)::date);
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

-- Reserva sem conclusão em 24h volta a ficar livre.
create function public.cron_liberar_reservas()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_n integer;
begin
  update public.tarefas
     set status = 'livre', reservada_por = null, reservada_em = null
   where status = 'reservada' and reservada_em < now() - interval '24 hours';
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

-- Sem resposta em 12h, o registro vale sozinho.
create function public.cron_validar_pendentes()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid;
  v_n  integer := 0;
begin
  for v_id in
    select id from public.registros
     where status = 'pendente' and criado_em < now() - interval '12 hours'
     order by criado_em
  loop
    perform public._aplicar_validacao(v_id, null);
    v_n := v_n + 1;
  end loop;
  return v_n;
end;
$$;
