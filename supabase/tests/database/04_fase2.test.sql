-- Fase 2: sequência, roubo, teto diário, recuperação, trégua, meta batida, notificações e fotos.
begin;
create extension if not exists pgtap with schema extensions;
select plan(35);

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
         (select id from public.tarefas where titulo = 'Aspirar a sala') aspirar;
grant select on t to authenticated;

-- Sequência ---------------------------------------------------------------------------------
update public.membros set sequencia_dias = 4, ultimo_dia_ativo = public._hoje((select id from ctx)) - 1
 where user_id = '11111111-1111-1111-1111-111111111111';
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select is((public.previa_conclusao((select louca from t)) ->> 'pontos')::int, 45, 'sequência de 4 dias vale x1,2 (39 x 1,2 = 45)');
select ok(public.previa_conclusao((select louca from t)) -> 'multiplicadores' @> '[{"tipo": "sequencia", "valor": 1.2}]',
  'extrato mostra a sequência');
create temp table r1 as select * from public.concluir_tarefa((select louca from t));
select is((select pontos_finais from r1), 45, 'concluir usa a mesma conta da prévia');
select throws_ok(format('select public.concluir_tarefa(%L, %L)', (select lixo from t), 'outra-casa/x.jpg'), 'P0001',
  'Foto inválida.', 'foto precisa estar na pasta da casa e do autor');
reset role;
grant select on r1 to authenticated;

select results_eq(
  $$select tipo, push from public.notificacoes where user_id = '22222222-2222-2222-2222-222222222222'$$,
  $$values ('pedido_validacao'::text, true)$$, 'concluir avisa a outra pessoa');

set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.validar_registro((select id from r1));
reset role;
select is((select sequencia_dias from public.membros where user_id = '11111111-1111-1111-1111-111111111111'), 5,
  'validar no dia seguinte soma um dia de sequência');
select is((select count(*)::int from public.notificacoes where user_id = '11111111-1111-1111-1111-111111111111' and tipo = 'virada'), 1,
  'quem passa na frente recebe a virada');
select is((select count(*)::int from public.conquistas where user_id = '11111111-1111-1111-1111-111111111111' and codigo = 'primeira_faxina'), 1,
  'primeira tarefa validada dá conquista');

update public.membros set ultimo_dia_ativo = public._hoje((select id from ctx)) - 3 where user_id = '11111111-1111-1111-1111-111111111111';
select cmp_ok(public.cron_sequencias(), '>=', 1, 'cron de sequência roda');
select is((select sequencia_dias from public.membros where user_id = '11111111-1111-1111-1111-111111111111'), 0, 'dia sem tarefa zera a sequência');

-- Roubo -------------------------------------------------------------------------------------
update public.membros set sequencia_dias = 3, ultimo_dia_ativo = public._hoje((select id from ctx))
 where user_id = '11111111-1111-1111-1111-111111111111';
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select public.reservar_tarefa((select mercado from t));
reset role;
update public.tarefas set reservada_em = now() - interval '23 hours 30 minutes' where id = (select mercado from t);
select is(public.cron_liberar_reservas(), 0, 'com 23h30 ainda não libera');
select ok((select aviso_roubo_em is not null from public.tarefas where id = (select mercado from t)), 'mas avisa que fica roubável em 1h');
select is((select count(*)::int from public.notificacoes where tipo = 'roubavel'), 2, 'aviso vai para os dois');
update public.tarefas set reservada_em = now() - interval '25 hours' where id = (select mercado from t);
select is(public.cron_liberar_reservas(), 1, 'depois de 24h a reserva cai');
select results_eq(format('select status, roubada_de from public.tarefas where id = %L', (select mercado from t)),
  $$values ('livre'::text, '11111111-1111-1111-1111-111111111111'::uuid)$$, 'tarefa volta livre marcada como roubável');
select is((select sequencia_dias from public.membros where user_id = '11111111-1111-1111-1111-111111111111'), 0, 'quem largou perde a sequência');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select ok(not (public.previa_conclusao((select mercado from t)) -> 'multiplicadores' @> '[{"tipo": "roubo"}]'),
  'quem largou não ganha bônus de roubo');
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((public.previa_conclusao((select mercado from t)) ->> 'pontos')::int, 115, 'quem rouba ganha +25% (90 x 1,25 = 112,5 -> 115)');
create temp table r2 as select * from public.concluir_tarefa((select mercado from t));
reset role;
grant select on r2 to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select public.validar_registro((select id from r2));
reset role;
select is((select roubada_de from public.tarefas where id = (select mercado from t)), null::uuid, 'roubo some depois de validado');

-- Teto diário -------------------------------------------------------------------------------
insert into public.registros (casa_id, tarefa_id, user_id, titulo, pontos_base, pontos_finais, moedas, status)
values ((select id from ctx), (select aspirar from t), '22222222-2222-2222-2222-222222222222', 'Maratona', 300, 300, 30, 'validado');
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
create temp table r3 as select * from public.concluir_tarefa((select louca from t));
reset role;
grant select on r3 to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select public.validar_registro((select id from r3));
reset role;
select results_eq(format('select pontos_previstos, pontos_finais from public.registros where id = %L', (select id from r3)),
  $$values (40, 0)$$, 'teto diário de 400 corta o excedente (300 + 115 já passou)');
select ok((select jsonb_array_length(observacoes) > 0 from public.registros where id = (select id from r3)), 'extrato explica o teto');
select is((select count(*)::int from public.conquistas where user_id = '22222222-2222-2222-2222-222222222222' and codigo = 'maratonista'), 1,
  'bater o teto dá a conquista Maratonista');

-- Recuperação -------------------------------------------------------------------------------
update public.temporadas set placar = '{"11111111-1111-1111-1111-111111111111": 1000, "22222222-2222-2222-2222-222222222222": 100}'
 where casa_id = (select id from ctx) and not encerrada;
update public.registros set criado_em = now() - interval '2 days' where user_id = '22222222-2222-2222-2222-222222222222';
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
create temp table r4 as select * from public.concluir_tarefa((select aspirar from t));
reset role;
grant select on r4 to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
select public.validar_registro((select id from r4));
reset role;
select results_eq(format('select pontos_finais, moedas from public.registros where id = %L', (select id from r4)),
  $$values (45, 8)$$, 'mais de 20% atrás: moedas em dobro, pontos iguais');

-- Trégua e meta -----------------------------------------------------------------------------
update public.casas set modo_tregua = true where id = (select id from ctx);
update public.temporadas set meta_alvo = 1300, meta_atual = 1290 where casa_id = (select id from ctx) and not encerrada;
create temp table antes as select placar from public.temporadas where casa_id = (select id from ctx) and not encerrada;
set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-1111-1111-111111111111"}';
create temp table r5 as select * from public.concluir_tarefa((select lixo from t));
reset role;
grant select on r5 to authenticated;
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select public.validar_registro((select id from r5));
select throws_ok(
  $$select public.comprar_carta('escudo')$$, 'P0001', 'Faltam 20 moedas para essa carta.', 'loja confere o saldo (moedas do roubo vieram em dobro)');
reset role;
select is((select placar from public.temporadas where casa_id = (select id from ctx) and not encerrada), (select placar from antes),
  'trégua: placar não mexe');
select is((select moedas from public.registros where id = (select id from r5)), 0, 'trégua: sem moedas');
select ok((select meta_batida_em is not null from public.temporadas where casa_id = (select id from ctx) and not encerrada),
  'trégua ainda conta para a meta, e a meta foi batida');
select is((select count(*)::int from public.notificacoes where tipo = 'meta'), 2, 'meta batida avisa os dois');
update public.temporadas set fim = now() - interval '1 minute' where casa_id = (select id from ctx) and not encerrada;
select public.cron_fechar_temporadas();
select is((select vencedor from public.temporadas where casa_id = (select id from ctx) and numero = 1), null::uuid,
  'semana de trégua não tem vencedor');
select is((select count(*)::int from public.notificacoes where tipo = 'fim_temporada'), 2, 'fim de temporada avisa os dois');

-- Níveis de provocação ----------------------------------------------------------------------
update public.casas set modo_tregua = false where id = (select id from ctx);
update public.membros set nivel_provocacao = 'desligada' where user_id = '22222222-2222-2222-2222-222222222222';
update public.membros set nivel_provocacao = 'sem_piedade' where user_id = '11111111-1111-1111-1111-111111111111';
select public._notificar('22222222-2222-2222-2222-222222222222', 'teste', 'T', 'moderado', 'sem piedade');
select public._notificar('11111111-1111-1111-1111-111111111111', 'teste', 'T', 'moderado', 'sem piedade');
select results_eq($$select user_id::text, corpo, push from public.notificacoes where tipo = 'teste' order by user_id$$,
  $$values ('11111111-1111-1111-1111-111111111111', 'sem piedade'::text, true), ('22222222-2222-2222-2222-222222222222', 'moderado'::text, false)$$,
  'desligada não recebe push; sem piedade recebe o tom implicante');
update public.membros set nivel_provocacao = 'moderada' where user_id = '22222222-2222-2222-2222-222222222222';
select public._notificar('22222222-2222-2222-2222-222222222222', 'teste2', 'T', 'moderado', null, 'sem_piedade');
select is((select push from public.notificacoes where tipo = 'teste2'), false, 'moderada não recebe push dos eventos sem piedade');

-- RLS das notificações ----------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims = '{"sub": "22222222-2222-2222-2222-222222222222"}';
select is((select count(*)::int from public.notificacoes where user_id <> auth.uid()), 0, 'cada um só lê as próprias notificações');
select throws_ok($$insert into public.notificacoes (casa_id, user_id, tipo, titulo, corpo) select casa_id, user_id, 'x', 'x', 'x' from public.membros limit 1$$,
  '42501', null, 'app não cria notificações');
select lives_ok('select public.marcar_notificacoes_lidas()', 'marcar como lidas');
reset role;

select * from finish();
rollback;
