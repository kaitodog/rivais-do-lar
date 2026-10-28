-- Fluxo completo: casa, convite, reservar, concluir, validar, contestar, crons e fechamento.
begin;
create extension if not exists pgtap with schema extensions;
select plan(39);

insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'vitor@exemplo.com', '{"nome": "Vitor"}'),
  ('22222222-2222-2222-2222-222222222222', 'bia@exemplo.com', '{"nome": "Bia"}'),
  ('33333333-3333-3333-3333-333333333333', 'intruso@exemplo.com', '{}');

select is((select nome from public.profiles where id = '11111111-1111-1111-1111-111111111111'), 'Vitor',
  'perfil criado com o nome do cadastro');
select is((select nome from public.profiles where id = '33333333-3333-3333-3333-333333333333'), 'intruso',
  'sem nome, usa o começo do e-mail');

-- Limites da temporada no fuso de São Paulo (UTC-3).
select is((select inicio from public._limites_temporada('America/Sao_Paulo', '2026-10-28 15:00-03')),
  '2026-10-26 00:00-03'::timestamptz, 'temporada começa segunda 00:00');
select is((select fim from public._limites_temporada('America/Sao_Paulo', '2026-10-28 15:00-03')),
  '2026-11-01 22:00-03'::timestamptz, 'temporada fecha domingo 22:00');
select is((select inicio from public._limites_temporada('America/Sao_Paulo', '2026-11-01 22:30-03')),
  '2026-11-02 00:00-03'::timestamptz, 'depois de domingo 22h já é a semana seguinte');

-- Vitor cria a casa.
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table ctx as select * from public.criar_casa('Casa do Vitor');
reset role;
grant select on ctx to authenticated;

select matches((select codigo_convite::text from ctx), '^[0-9]{6}$', 'código de convite com 6 dígitos');
select is((select count(*)::int from public.tarefas where casa_id = (select id from ctx)), 10, 'casa nasce com tarefas de exemplo');
select is((select count(*)::int from public.temporadas where casa_id = (select id from ctx) and not encerrada), 1,
  'casa nasce com uma temporada aberta');

-- Bia entra pelo código; o terceiro não cabe.
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select lives_ok(format('select public.entrar_casa(%L)', (select codigo_convite from ctx)), 'Bia entra pelo código');
set local request.jwt.claims = '{"sub": "33333333-3333-3333-3333-333333333333"}';
select throws_ok(format('select public.entrar_casa(%L)', (select codigo_convite from ctx)), 'P0001',
  'Essa casa já tem dois jogadores.', 'casa aceita no máximo 2 membros');
select throws_ok('select public.entrar_casa(''000000'')', 'P0001', 'Código de convite não encontrado.', 'código errado');
reset role;

select is((select cor from public.membros where user_id = '22222222-2222-2222-2222-222222222222'), 'laranja',
  'segunda pessoa fica com a outra cor');

create temp table louca as select id from public.tarefas where titulo = 'Lavar a louça do jantar';
create temp table lixo as select id from public.tarefas where titulo = 'Tirar o lixo';
grant select on louca, lixo to authenticated;

-- Reservar.
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select is((public.reservar_tarefa((select id from louca))).status, 'reservada', 'Vitor reserva a louça');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select throws_ok(format('select public.reservar_tarefa(%L)', (select id from louca)), 'P0001',
  'Alguém chegou antes: a tarefa já está reservada.', 'não dá pra reservar a reserva do outro');
select throws_ok(format('select public.concluir_tarefa(%L)', (select id from louca)), 'P0001',
  'Essa tarefa está reservada pela outra pessoa.', 'nem concluir a reserva do outro');

-- Concluir e validar.
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table r1 as select * from public.concluir_tarefa((select id from louca));
reset role;
grant select on r1 to authenticated;
select is((select pontos_finais from r1), 40, 'registro com os pontos calculados no servidor');
select is((select status from r1), 'pendente', 'registro nasce pendente');
select is((select status from public.tarefas where id = (select id from louca)), 'aguardando', 'tarefa aguarda validação');

set local role authenticated;
select throws_ok(format('select public.validar_registro(%L)', (select id from r1)), 'P0001',
  'Quem valida é a outra pessoa.', 'ninguém valida a própria tarefa');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((public.validar_registro((select id from r1))).status, 'validado', 'Bia valida');
reset role;

select is((select (placar ->> '11111111-1111-1111-1111-111111111111')::int from public.temporadas
            where casa_id = (select id from ctx) and not encerrada), 40, 'placar soma os pontos');
select is((select meta_atual from public.temporadas where casa_id = (select id from ctx) and not encerrada), 40,
  'meta da casa soma os pontos');
select results_eq(
  $$select moedas, xp, nivel from public.membros where user_id = '11111111-1111-1111-1111-111111111111'$$,
  $$values (4, 40, 1)$$, 'moedas = pontos/10 e XP = pontos');
select results_eq(
  format('select status, reservada_por is null, prazo is not null, ativa from public.tarefas where id = %L', (select id from louca)),
  $$values ('livre'::text, true, true, true)$$, 'tarefa diária volta livre com novo prazo');

-- Contestação: no máximo 2 por semana.
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table r2 as select * from public.concluir_tarefa((select id from lixo));
reset role;
grant select, insert on r2 to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select throws_ok(format('select public.contestar_registro(%L, %L)', (select id from r2), ''), 'P0001',
  'Conte o que ficou faltando.', 'contestar exige comentário');
select is((public.contestar_registro((select id from r2), 'Faltou o lixo do banheiro')).status, 'contestado',
  'primeira contestação');
reset role;
select is((select status from public.tarefas where id = (select id from lixo)), 'livre', 'contestada volta pra fila');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
insert into r2 select * from public.concluir_tarefa((select id from lixo));
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select lives_ok(format('select public.contestar_registro(%L, %L)',
  (select id from public.registros where status = 'pendente' and titulo = 'Tirar o lixo'), 'De novo não'), 'segunda contestação');
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table r3 as select * from public.concluir_tarefa((select id from lixo));
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select throws_ok(format('select public.contestar_registro(%L, %L)', (select id from r3), 'Terceira'), 'P0001',
  'Você já usou as 2 contestações desta semana.', 'terceira contestação na semana é barrada');
reset role;

-- Cron: sem resposta em 12h vale sozinho.
update public.registros set criado_em = now() - interval '13 hours' where id = (select id from r3);
select is(public.cron_validar_pendentes(), 1, 'cron valida pendentes há mais de 12h');
select is((select (placar ->> '11111111-1111-1111-1111-111111111111')::int from public.temporadas
            where casa_id = (select id from ctx) and not encerrada), 50, 'validação automática entra no placar');

-- Cron: reserva parada há 24h volta a ficar livre.
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.reservar_tarefa((select id from louca));
reset role;
update public.tarefas set reservada_em = now() - interval '25 hours' where id = (select id from louca);
select is(public.cron_liberar_reservas(), 1, 'cron libera reserva vencida');

-- Cron: tarefa bomba.
update public.tarefas set prazo = now() - interval '3 days' where id = (select id from louca);
select cmp_ok(public.cron_atualizar_bombas(), '>=', 1, 'cron conta os dias de atraso');
select is((select dias_parada from public.tarefas where id = (select id from louca)), 3, 'bomba parada há 3 dias');

-- Fechamento da temporada.
update public.temporadas set fim = now() - interval '1 minute' where casa_id = (select id from ctx) and not encerrada;
select is(public.cron_fechar_temporadas(), 1, 'cron fecha a temporada vencida');
select results_eq(
  format('select numero, encerrada, vencedor from public.temporadas where casa_id = %L order by numero', (select id from ctx)),
  $$values (1, true, '11111111-1111-1111-1111-111111111111'::uuid), (2, false, null::uuid)$$,
  'vencedor definido e próxima temporada criada');

set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select throws_ok(format('select public.resgatar_premio(%L)',
  (select id from public.temporadas where casa_id = (select id from ctx) and numero = 1)), 'P0001',
  'Só quem venceu a temporada pode resgatar o prêmio.', 'só o vencedor resgata');
select lives_ok($$select public.definir_cor('azul')$$, 'troca de cor entre os dois');
reset role;
select results_eq(
  format('select cor from public.membros where casa_id = %L order by user_id', (select id from ctx)),
  $$values ('laranja'::text), ('azul'::text)$$, 'as cores trocaram de dono');

select * from finish();
rollback;
