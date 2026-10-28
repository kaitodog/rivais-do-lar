-- Fase 3: loja, mão de 3 cartas, 1 uso por dia, efeitos das 6 cartas e trocas.
begin;
create extension if not exists pgtap with schema extensions;
select plan(29);

insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'vitor@exemplo.com', '{"nome": "Vitor"}'),
  ('22222222-2222-2222-2222-222222222222', 'bia@exemplo.com', '{"nome": "Bia"}');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table ctx as select * from public.criar_casa('Casa');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.entrar_casa((select codigo_convite from ctx));
reset role;
grant select on ctx to authenticated;
create temp table t as
  select (select id from public.tarefas where titulo = 'Lavar a louça do jantar') louca,
         (select id from public.tarefas where titulo = 'Tirar o lixo') lixo,
         (select id from public.tarefas where titulo = 'Mercado da semana') mercado,
         (select id from public.tarefas where titulo = 'Aspirar a sala') aspirar,
         (select id from public.tarefas where titulo = 'Limpar a gaiola dos porquinhos') gaiola;
grant select on t to authenticated;
create temp table m (tipo text, id uuid);
grant select, insert on m to authenticated;

-- Permite usar várias cartas no mesmo teste (a regra é uma por dia).
create function pg_temp.virar_o_dia() returns void language sql as $$
  update public.cartas set usada_em = usada_em - interval '1 day' where usada_em is not null
$$;

-- Loja --------------------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select throws_ok($$select public.comprar_carta('veto')$$, 'P0001', 'Faltam 120 moedas para essa carta.', 'sem moedas não compra');
reset role;
update public.membros set moedas = 1000 where user_id = '11111111-1111-1111-1111-111111111111';
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select tipo, id from public.comprar_carta('dobradinha');
insert into m select tipo, id from public.comprar_carta('hora_feliz');
insert into m select tipo, id from public.comprar_carta('holofote');
select throws_ok($$select public.comprar_carta('escudo')$$, 'P0001', 'Sua mão já tem 3 cartas. Use uma antes de comprar outra.', 'mão máxima de 3');
reset role;
select is((select moedas from public.membros where user_id = '11111111-1111-1111-1111-111111111111'), 1000 - 80 - 90 - 40, 'compras descontam as moedas');
select is((select raridade from public.cartas where tipo = 'hora_feliz'), 'epica', 'Hora Feliz é épica');

-- Dobradinha --------------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select lives_ok(format('select public.usar_carta(%L)', (select id from m where tipo = 'dobradinha')), 'usa a Dobradinha');
select throws_ok(format('select public.usar_carta(%L)', (select id from m where tipo = 'hora_feliz')), 'P0001',
  'Uma carta por dia. Amanhã tem mais.', 'uma carta por dia');
select is((public.previa_conclusao((select louca from t)) ->> 'pontos')::int, 80, 'Dobradinha: próxima tarefa vale x2');
create temp table r1 as select * from public.concluir_tarefa((select louca from t));
reset role;
select is((select dobradinha from public.membros where user_id = '11111111-1111-1111-1111-111111111111'), false, 'Dobradinha some depois de usada');
select is((select pontos_finais from r1), 80, 'registro com x2');

-- Hora Feliz --------------------------------------------------------------------------------
select pg_temp.virar_o_dia();
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select public.usar_carta((select id from m where tipo = 'hora_feliz'));
select is((public.previa_conclusao((select aspirar from t)) ->> 'pontos')::int, 70, 'Hora Feliz: x1,5 (45 x 1,5 = 67,5 -> 70)');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((public.previa_conclusao((select aspirar from t)) ->> 'pontos')::int, 45, 'Hora Feliz vale só para quem usou');
reset role;
select is((select count(*)::int from public.notificacoes where tipo = 'carta' and user_id = '22222222-2222-2222-2222-222222222222'), 1,
  'a outra pessoa fica sabendo da Hora Feliz');

-- Holofote ----------------------------------------------------------------------------------
select pg_temp.virar_o_dia();
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select throws_ok(format('select public.usar_carta(%L, %L)', (select id from m where tipo = 'holofote'), (select louca from t)), 'P0001',
  'Escolha uma tarefa da casa que não esteja esperando validação.', 'Holofote não mira tarefa esperando validação');
select public.usar_carta((select id from m where tipo = 'holofote'), (select lixo from t));
reset role;
select ok((select dias_parada >= 1 and prazo < now() from public.tarefas where id = (select lixo from t)), 'Holofote transforma em bomba na hora');

-- Escudo ------------------------------------------------------------------------------------
select pg_temp.virar_o_dia();
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select tipo, id from public.comprar_carta('escudo');
select public.reservar_tarefa((select mercado from t));
select public.usar_carta((select id from m where tipo = 'escudo'), (select mercado from t));
reset role;
update public.tarefas set reservada_em = now() - interval '25 hours' where id = (select mercado from t);
select public.cron_liberar_reservas();
select is((select status from public.tarefas where id = (select mercado from t)), 'reservada', 'Escudo segura a reserva por 24h');

-- Troca -------------------------------------------------------------------------------------
select pg_temp.virar_o_dia();
update public.tarefas set reservada_em = now() where id = (select mercado from t);
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.reservar_tarefa((select aspirar from t));
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select tipo, id from public.comprar_carta('troca');
select throws_ok(format('select public.usar_carta(%L, %L, %L)', (select id from m where tipo = 'troca'), (select aspirar from t), (select mercado from t)),
  'P0001', 'Escolha uma tarefa reservada por você para dar na troca.', 'troca confere os dois lados');
select public.usar_carta((select id from m where tipo = 'troca'), (select mercado from t), (select aspirar from t));
reset role;
create temp table tr as select * from public.trocas;
grant select on tr to authenticated;
select is((select status from tr), 'pendente', 'troca vira proposta');
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((public.responder_troca((select id from tr), false)).status, 'recusada', 'a outra pessoa pode recusar');
reset role;
select pg_temp.virar_o_dia();
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select 'troca2', id from public.comprar_carta('troca');
select public.usar_carta((select id from m where tipo = 'troca2'), (select mercado from t), (select aspirar from t));
reset role;
select results_eq(format('select reservada_por from public.tarefas where id in (%L, %L) order by titulo', (select aspirar from t), (select mercado from t)),
  $$values ('11111111-1111-1111-1111-111111111111'::uuid), ('22222222-2222-2222-2222-222222222222'::uuid)$$,
  'segunda troca na semana acontece direto: recusa só 1x');

-- Troca sem resposta em 12h acontece sozinha.
select pg_temp.virar_o_dia();
update public.membros set moedas = 1000 where user_id = '22222222-2222-2222-2222-222222222222';
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
insert into m select 'troca3', id from public.comprar_carta('troca');
select public.usar_carta((select id from m where tipo = 'troca3'), (select mercado from t), (select aspirar from t));
reset role;
update public.trocas set criada_em = now() - interval '13 hours' where status = 'pendente';
select is(public.cron_trocas(), 1, 'troca parada há 12h é aceita pelo cron');
select is((select reservada_por from public.tarefas where id = (select mercado from t)), '11111111-1111-1111-1111-111111111111'::uuid,
  'tarefas trocaram de dono');

-- Veto --------------------------------------------------------------------------------------
select pg_temp.virar_o_dia();
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select tipo, id from public.comprar_carta('veto');
select public.usar_carta((select id from m where tipo = 'veto'));
reset role;
select is((select vetada_por from public.tarefas where id = (select gaiola from t)), '11111111-1111-1111-1111-111111111111'::uuid,
  'Veto cai na tarefa mais chata (gaiola, chatice 1,6)');
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select throws_ok(format('select public.reservar_tarefa(%L)', (select gaiola from t)), 'P0001',
  'Você vetou essa tarefa nesta temporada.', 'quem vetou não pega a tarefa');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select lives_ok(format('select public.reservar_tarefa(%L)', (select gaiola from t)), 'a outra pessoa pega');
reset role;

-- Trégua guarda as cartas; o app não mexe em cartas nem efeitos direto.
select pg_temp.virar_o_dia();
update public.casas set modo_tregua = true where id = (select id from ctx);
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into m select 'dob2', id from public.comprar_carta('dobradinha');
select throws_ok(format('select public.usar_carta(%L)', (select id from m where tipo = 'dob2')), 'P0001',
  'Modo trégua: as cartas estão guardadas até a competição voltar.', 'trégua pausa as cartas');
select throws_ok($$update public.membros set dobradinha = true where user_id = auth.uid()$$, '42501', null, 'app não arma Dobradinha direto');
select throws_ok($$update public.cartas set usada_em = null$$, '42501', null, 'app não devolve carta usada');
select throws_ok($$update public.tarefas set escudo_ate = now() + interval '9 days'$$, '42501', null, 'app não cria escudo');
select throws_ok($$update public.trocas set status = 'aceita'$$, '42501', null, 'app não aceita troca direto');
reset role;

select * from finish();
rollback;
