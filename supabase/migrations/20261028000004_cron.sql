-- Agendamentos. As rotinas são idempotentes e independem de fuso (cada casa usa o seu),
-- então rodam em intervalos curtos em vez de horários fixos.
create extension if not exists pg_cron;

select cron.schedule('rivais-fechar-temporadas', '*/5 * * * *', 'select public.cron_fechar_temporadas()');
select cron.schedule('rivais-atualizar-bombas', '7 * * * *', 'select public.cron_atualizar_bombas()');
select cron.schedule('rivais-liberar-reservas', '*/10 * * * *', 'select public.cron_liberar_reservas()');
select cron.schedule('rivais-validar-pendentes', '*/10 * * * *', 'select public.cron_validar_pendentes()');
