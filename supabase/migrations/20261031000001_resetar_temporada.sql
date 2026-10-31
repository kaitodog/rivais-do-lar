-- Reset manual da temporada atual (0.3.0).
-- Zera placar/meta e cancela conclusões pendentes; não mexe em XP, moedas, cartas ou tarefas
-- já validadas. A temporada mantém número e datas; o fechamento normal continua valendo.

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

  -- Trava a temporada aberta (e cria a primeira se por algum motivo não existir).
  v_temp := public._temporada_atual(v_casa);

  -- Conclusões que ainda esperavam validação não podem repor pontos depois do reset.
  -- As tarefas voltam para a fila; os registros pendentes são apagados.
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

  update public.temporadas
     set placar = '{}'::jsonb,
         meta_atual = 0,
         vencedor = null,
         premio_resgatado = false
   where id = v_temp.id
   returning * into v_temp;

  perform public._notificar(
    v_uid,
    'reset',
    'Temporada zerada',
    'O placar e a meta voltaram a zero. Tarefas, moedas, XP e cartas continuam.',
    'Resetou a temporada. Agora é faxina limpa.',
    'moderada'
  );
  perform public._notificar(
    public._outro_membro(v_casa, v_uid),
    'reset',
    'Temporada zerada',
    'O placar e a meta voltaram a zero. Tarefas, moedas, XP e cartas continuam.',
    'Resetou a temporada. Agora é faxina limpa.',
    'moderada'
  );

  return v_temp;
end;
$$;

revoke all on function public.reiniciar_temporada() from public;
grant execute on function public.reiniciar_temporada() to authenticated;
