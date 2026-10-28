-- RLS e grants: o cliente não consegue mexer em pontos, moedas, XP, placar nem ver outra casa.
begin;
create extension if not exists pgtap with schema extensions;
select plan(20);

insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'vitor@exemplo.com', '{"nome": "Vitor"}'),
  ('22222222-2222-2222-2222-222222222222', 'bia@exemplo.com', '{"nome": "Bia"}'),
  ('44444444-4444-4444-4444-444444444444', 'vizinho@exemplo.com', '{"nome": "Vizinho"}');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table ctx as select * from public.criar_casa('Casa do Vitor');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.entrar_casa((select codigo_convite from ctx));
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select public.criar_casa('Casa do Vizinho');
reset role;
grant select on ctx to authenticated;

create temp table t as select id from public.tarefas where casa_id = (select id from ctx) limit 1;
create temp table temp_atual as select id from public.temporadas where casa_id = (select id from ctx);
grant select on t, temp_atual to authenticated;

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';

-- Campos de jogo: escrita direta proibida.
select throws_ok($$update public.membros set moedas = 9999 where user_id = auth.uid()$$, '42501', null,
  'cliente não altera as próprias moedas');
select throws_ok($$update public.membros set xp = 9999, nivel = 10 where user_id = auth.uid()$$, '42501', null,
  'cliente não altera XP nem nível');
select throws_ok($$update public.temporadas set placar = '{"11111111-1111-1111-1111-111111111111": 9999}'$$, '42501', null,
  'cliente não altera o placar');
select throws_ok($$update public.temporadas set meta_atual = 9999$$, '42501', null, 'cliente não altera a meta da casa');
select throws_ok($$update public.temporadas set vencedor = auth.uid()$$, '42501', null, 'cliente não se declara vencedor');
select throws_ok(
  format($$insert into public.registros (casa_id, tarefa_id, user_id, titulo, pontos_base, pontos_finais, moedas, status)
           values (%L, %L, auth.uid(), 'falso', 999, 999, 99, 'validado')$$, (select id from ctx), (select id from t)),
  '42501', null, 'cliente não cria registros direto');
select throws_ok($$update public.registros set pontos_finais = 9999$$, '42501', null, 'cliente não altera pontos de registro');
select throws_ok(format($$update public.tarefas set status = 'reservada', reservada_por = auth.uid() where id = %L$$, (select id from t)),
  '42501', null, 'reserva só pela RPC');
select throws_ok(format($$update public.tarefas set dias_parada = 10 where id = %L$$, (select id from t)),
  '42501', null, 'cliente não infla o bônus bomba');
select throws_ok($$insert into public.cartas (casa_id, dono, tipo, raridade) select casa_id, auth.uid(), 'veto', 'epica' from public.membros where user_id = auth.uid()$$,
  '42501', null, 'cliente não cria cartas');
select throws_ok(format('select public._aplicar_validacao(%L, null)', gen_random_uuid()), '42501', null,
  'funções internas não ficam expostas');
select throws_ok('select public.cron_fechar_temporadas()', '42501', null, 'rotinas de cron não ficam expostas');

-- Campos de cadastro: permitidos.
select lives_ok(format($$update public.tarefas set titulo = 'Louça caprichada', minutos = 25 where id = %L$$, (select id from t)),
  'membro edita o cadastro da tarefa');
select lives_ok($$update public.profiles set nome = 'Vitão' where id = auth.uid()$$, 'cada um edita o próprio nome');
select lives_ok($$insert into public.tarefas (casa_id, titulo, minutos) select casa_id, 'Varrer a varanda', 15 from public.membros where user_id = auth.uid()$$,
  'membro cria tarefa na própria casa');

-- Isolamento entre casas.
set local request.jwt.claims = '{"sub": "44444444-4444-4444-4444-444444444444"}';
select is((select count(*)::int from public.tarefas where casa_id = (select id from ctx)), 0, 'vizinho não vê tarefas de outra casa');
select is((select count(*)::int from public.temporadas where casa_id = (select id from ctx)), 0, 'vizinho não vê o placar de outra casa');
select is((select count(*)::int from public.profiles where id = '11111111-1111-1111-1111-111111111111'), 0, 'vizinho não vê perfis de outra casa');
select throws_ok(format($$insert into public.tarefas (casa_id, titulo, minutos) values (%L, 'Invasão', 10)$$, (select id from ctx)),
  '42501', null, 'vizinho não cria tarefa em outra casa');
select throws_ok(format('select public.reservar_tarefa(%L)', (select id from t)), 'P0001', 'Tarefa não encontrada.',
  'vizinho não reserva tarefa de outra casa');

reset role;
select * from finish();
rollback;
