-- 0.5.2: testes das cartas novas, vistorias, castigos e efeitos.
-- Roda em uma transação e desfaz tudo no fim.
begin;
create extension if not exists pgtap with schema extensions;
select no_plan();

insert into auth.users (id, email, raw_user_meta_data) values
  ('33333333-3333-3333-3333-333333333333', 'vitor2@exemplo.com', '{"nome": "Vitor"}'),
  ('44444444-4444-4444-4444-444444444444', 'isabelle@exemplo.com', '{"nome": "Isabelle"}');

set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
create temp table ctx as select * from public.criar_casa('Casa Teste');
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select public.entrar_casa((select codigo_convite from ctx));
reset role;
grant select on ctx to authenticated;

create temp table t as
  select (select id from public.tarefas where titulo = 'Lavar a louça do jantar') louca,
         (select id from public.tarefas where titulo = 'Tirar o lixo') lixo,
         (select id from public.tarefas where titulo = 'Mercado da semana') mercado,
         (select id from public.tarefas where titulo = 'Aspirar a sala') aspirar,
         (select id from public.tarefas where titulo = 'Limpar a gaiola dos porquinhos') gaiola,
         (select id from public.tarefas where titulo = 'Limpar o banheiro') banheiro;
grant select on t to authenticated;

create temp table m (tipo text, id uuid);
grant select, insert on m to authenticated;

create function pg_temp.limpar() returns void language plpgsql as $$
begin
  update public.membros set
    moedas = 1000, dobradinha = false, hora_feliz_ate = null,
    cafe_passado = false, balde_agua_fria = false, seguro_ate = null,
    nota_fiscal_ate = null, mao_atada_ate = null, combo_ate = null,
    reviravolta_ate = null, allin_ate = null, allin_count = 0,
    sequencia_dias = 0, ultimo_dia_ativo = null;
  update public.tarefas set
    status = 'livre', reservada_por = null, reservada_em = null,
    concluida_em = null, dias_parada = 0, prazo = null, ativa = true,
    escudo_ate = null, vetada_por = null, vetada_ate = null,
    cena_ate = null, cena_de = null;
  update public.temporadas set placar = '{}'::jsonb, meta_atual = 0
   where casa_id = (select id from ctx) and not encerrada;
  update public.casas set modo_tregua = false where id = (select id from ctx);
  delete from public.registros where casa_id = (select id from ctx);
  delete from public.cartas where casa_id = (select id from ctx);
  delete from public.castigos where casa_id = (select id from ctx);
  delete from public.vales where casa_id = (select id from ctx);
  delete from public.desafios where casa_id = (select id from ctx);
  delete from public.decisivas_usadas;
  delete from public.ocorrencias where casa_id = (select id from ctx);
end $$;

create function pg_temp.placar() returns jsonb language sql as $$
  select placar from public.temporadas
   where casa_id = (select id from ctx) and not encerrada
$$;

-- Catálogo -----------------------------------------------------------------------------------
select is(public.carta_custo('cafe_passado'), 15, 'Café custa 15');
select is(public.carta_custo('seguro'), 15, 'Seguro custa 15');
select is(public.carta_custo('balde'), 20, 'Balde custa 20');
select is(public.carta_custo('cobranca'), 30, 'Cobrança custa 30');
select is(public.carta_custo('castigo'), 20, 'Castigo custa 20');
select is(public.carta_custo('vale'), 10, 'Vale custa 10');
select is(public.carta_custo('pedagio'), 25, 'Pedágio custa 25');
select is(public.carta_custo('mao_atada'), 25, 'Mão Atada custa 25');
select is(public.carta_custo('roubo_cena'), 20, 'Roubo de Cena custa 20');
select is(public.carta_custo('combo'), 20, 'Combo custa 20');
select is(public.carta_custo('dobrou'), 25, 'Dobrou custa 25');
select is(public.carta_custo('reviravolta'), 20, 'Reviravolta custa 20');
select is(public.carta_custo('allin'), 30, 'All-in custa 30');

-- Café ---------------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'cafe1', id from public.comprar_carta('cafe_passado');
select public.usar_carta((select id from m where tipo = 'cafe1'));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select is(
  (public.previa_conclusao((select louca from t)) ->> 'pontos')::int,
  least(round(public.calcular_pontos(x.minutos, x.esforco, x.chatice) * 1.25)::int,
        public.calcular_pontos(x.minutos, x.esforco, x.chatice) + 25),
  'Café aplica +25% com teto de +25'
) from public.tarefas x where x.id = (select louca from t);
select public.concluir_tarefa((select louca from t));
reset role;
select is((select cafe_passado from public.membros where user_id = '33333333-3333-3333-3333-333333333333'), false,
  'Café é consumido na tarefa curta');

-- Seguro bloqueando Balde ---------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'seg1', id from public.comprar_carta('seguro');
select public.usar_carta((select id from m where tipo = 'seg1'));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
insert into m select 'balde1', id from public.comprar_carta('balde');
select public.usar_carta((select id from m where tipo = 'balde1'));
reset role;
select is((select balde_agua_fria from public.membros where user_id = '33333333-3333-3333-3333-333333333333'), false,
  'Seguro bloqueia o Balde');
select is((select seguro_ate from public.membros where user_id = '33333333-3333-3333-3333-333333333333'), null,
  'Seguro é consumido no bloqueio');

-- Balde --------------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'balde2', id from public.comprar_carta('balde');
select public.usar_carta((select id from m where tipo = 'balde2'));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select is(
  (public.previa_conclusao((select aspirar from t)) ->> 'pontos')::int,
  greatest(round(public.calcular_pontos(x.minutos, x.esforco, x.chatice) * 0.75)::int,
           public.calcular_pontos(x.minutos, x.esforco, x.chatice) - 20),
  'Balde aplica −25% com teto de −20'
) from public.tarefas x where x.id = (select aspirar from t);
reset role;

-- Cobrança + Nota Fiscal ---------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '33333333-3333-3333-3333-333333333333', 100,
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'cob1', id from public.comprar_carta('cobranca');
select public.usar_carta((select id from m where tipo = 'cob1'));
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 115, 'Cobrança credita 15');
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 85, 'Cobrança debita 15');
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select public.usar_nota_fiscal();
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 100, 'Nota Fiscal devolve 15');
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 100, 'Nota Fiscal volta ao placar');

-- Arrastão -----------------------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '33333333-3333-3333-3333-333333333333', 100,
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select public.usar_decisiva('arrastao');
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 130, 'Arrastão credita 30');
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 70, 'Arrastão debita 30');
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select throws_ok($$select public.usar_decisiva('arrastao')$$, 'P0001', 'Você já usou o Arrastão nesta temporada.',
  'Arrastão é uma vez por temporada');
reset role;

-- Pedágio ------------------------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '33333333-3333-3333-3333-333333333333', 100,
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'ped1', id from public.comprar_carta('pedagio');
select public.usar_carta((select id from m where tipo = 'ped1'));
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 110, 'Pedágio credita 10');
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 90, 'Pedágio debita 10');

-- Mão Atada ----------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
insert into m select 'holo1', id from public.comprar_carta('holofote');
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'mao1', id from public.comprar_carta('mao_atada');
select public.usar_carta((select id from m where tipo = 'mao1'));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select throws_ok(
  format('select public.usar_carta(%L, %L)', (select id from m where tipo = 'holo1'), (select louca from t)),
  'P0001', 'Sua Mão está atada: você não usa cartas ofensivas por enquanto.',
  'Mão Atada bloqueia carta ofensiva'
);
reset role;

-- Roubo de Cena ------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'cena1', id from public.comprar_carta('roubo_cena');
select public.usar_carta((select id from m where tipo = 'cena1'), (select gaiola from t));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select is(
  (public.previa_conclusao((select gaiola from t)) ->> 'pontos')::int,
  public.calcular_pontos(x.minutos, x.esforco, x.chatice) + 30,
  'Roubo de Cena adiciona +30'
) from public.tarefas x where x.id = (select gaiola from t);
reset role;

-- Combo --------------------------------------------------------------------------------------
select pg_temp.limpar();
insert into public.registros (casa_id, tarefa_id, user_id, titulo, pontos_base, multiplicadores, pontos_finais, moedas, status, criado_em, validado_em)
select (select id from ctx), x.id, '33333333-3333-3333-3333-333333333333', x.titulo, 10, '[]', 10, 1, 'validado', now(), now()
  from public.tarefas x where x.id in ((select louca from t), (select lixo from t));
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'combo1', id from public.comprar_carta('combo');
select public.usar_carta((select id from m where tipo = 'combo1'));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select is(
  (public.previa_conclusao((select aspirar from t)) ->> 'pontos')::int,
  public.calcular_pontos(x.minutos, x.esforco, x.chatice) + 30,
  'Combo paga +30 na terceira categoria'
) from public.tarefas x where x.id = (select aspirar from t);
reset role;

-- Dobrou ou Danou ----------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select public.reservar_tarefa((select mercado from t));
insert into m select 'dob1', id from public.comprar_carta('dobrou');
select public.usar_carta((select id from m where tipo = 'dob1'), (select mercado from t));
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select is(
  (public.previa_conclusao((select mercado from t)) ->> 'pontos')::int,
  public.calcular_pontos(x.minutos, x.esforco, x.chatice) + 40,
  'Dobrou ou Danou adiciona +40'
) from public.tarefas x where x.id = (select mercado from t);
select public.concluir_tarefa((select mercado from t));
reset role;
select is((select status from public.desafios where tarefa_id = (select mercado from t)),
  'cumprido', 'Desafio é marcado como cumprido');

-- Reviravolta --------------------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '33333333-3333-3333-3333-333333333333', 100,
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'rev1', id from public.comprar_carta('reviravolta');
select public.usar_reviravolta();
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
insert into m select 'cob2', id from public.comprar_carta('cobranca');
select public.usar_carta((select id from m where tipo = 'cob2'));
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 107,
  'Reviravolta devolve 7 (metade de 15)');
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 93,
  'Reviravolta tira 7 do atacante');
select is((select reviravolta_ate from public.membros where user_id = '33333333-3333-3333-3333-333333333333'), null,
  'Reviravolta é consumida');

-- All-in -------------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'allin1', id from public.comprar_carta('allin');
select public.usar_allin();
reset role;
update public.membros set allin_count = 2 where user_id = '33333333-3333-3333-3333-333333333333';
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select public.concluir_tarefa((select gaiola from t));
reset role;
select is((pg_temp.placar() ->> '33333333-3333-3333-3333-333333333333')::int, 60,
  'All-in paga +60 na terceira tarefa');
select is((select allin_ate from public.membros where user_id = '33333333-3333-3333-3333-333333333333'), null,
  'All-in é encerrado ao completar');

-- Castigo ------------------------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'cast1', id from public.comprar_carta('castigo');
select public.usar_castigo('lavar o banheiro');
reset role;
update public.castigos set prazo = now() - interval '1 hour' where casa_id = (select id from ctx);
select public.cron_castigos();
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 85,
  'Castigo vencido aplica −15');

-- Vale-favor ---------------------------------------------------------------------------------
select pg_temp.limpar();
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
insert into m select 'vale1', id from public.comprar_carta('vale');
select public.usar_vale('café da manhã');
reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select public.cumprir_vale((select id from public.vales where casa_id = (select id from ctx) and status = 'pendente'));
reset role;
select is((select status from public.vales where casa_id = (select id from ctx)), 'cumprido',
  'Vale é marcado como cumprido');

-- Vistoria -----------------------------------------------------------------------------------
select pg_temp.limpar();
update public.temporadas set placar = jsonb_build_object(
  '33333333-3333-3333-3333-333333333333', 100,
  '44444444-4444-4444-4444-444444444444', 100
) where casa_id = (select id from ctx) and not encerrada;
insert into public.ocorrencia_tipos (casa_id, nome, categoria, pontos, icone)
values ((select id from ctx), 'Roupa no chão', 'Quarto', 10, 'checkroom')
returning id;
create temp table tipo_vistoria as select id from public.ocorrencia_tipos where nome = 'Roupa no chão' and casa_id = (select id from ctx);
grant select on tipo_vistoria to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select public.registrar_ocorrencia((select id from tipo_vistoria), (select id from ctx)::text || '/33333333-3333-3333-3333-333333333333/foto.jpg');
reset role;
select is((pg_temp.placar() ->> '44444444-4444-4444-4444-444444444444')::int, 90,
  'Vistoria aplica −10 na hora');
select is((select count(*)::int from public.ocorrencias where casa_id = (select id from ctx)), 1,
  'Vistoria entra no B.O.');

select * from finish();
rollback;
