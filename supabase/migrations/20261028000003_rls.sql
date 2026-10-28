-- Permissões. O app (papel authenticated) só lê dados da própria casa e só escreve campos de
-- cadastro; tudo que é jogo passa pelas RPCs security definer.

-- O Supabase concede tudo por padrão em public; aqui começamos do zero.
revoke all on all tables in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;

alter table public.profiles   enable row level security;
alter table public.casas      enable row level security;
alter table public.membros    enable row level security;
alter table public.tarefas    enable row level security;
alter table public.temporadas enable row level security;
alter table public.registros  enable row level security;
alter table public.cartas     enable row level security;

-- Leitura: só a própria casa.
grant select on public.profiles, public.casas, public.membros, public.tarefas,
                public.temporadas, public.registros, public.cartas to authenticated;

create policy "profiles: eu e quem mora comigo" on public.profiles
  for select to authenticated
  using (
    id = auth.uid()
    or exists (select 1 from public.membros m where m.user_id = profiles.id and m.casa_id = public.minha_casa())
  );

create policy "casas: membros leem" on public.casas
  for select to authenticated using (public.is_membro(id));

create policy "membros: mesma casa" on public.membros
  for select to authenticated using (public.is_membro(casa_id));

create policy "tarefas: mesma casa" on public.tarefas
  for select to authenticated using (public.is_membro(casa_id));

create policy "temporadas: mesma casa" on public.temporadas
  for select to authenticated using (public.is_membro(casa_id));

create policy "registros: mesma casa" on public.registros
  for select to authenticated using (public.is_membro(casa_id));

create policy "cartas: mesma casa" on public.cartas
  for select to authenticated using (public.is_membro(casa_id));

-- Escrita direta: apenas colunas de cadastro. Pontos, moedas, XP, placar, status, reservas e
-- registros não têm grant de escrita, então qualquer tentativa falha com "permission denied".
grant update (nome) on public.profiles to authenticated;
create policy "profiles: edito o meu" on public.profiles
  for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

grant update (nome, modo_tregua) on public.casas to authenticated;
create policy "casas: membros editam" on public.casas
  for update to authenticated using (public.is_membro(id)) with check (public.is_membro(id));

grant update (nivel_provocacao, fcm_token) on public.membros to authenticated;
create policy "membros: edito o meu" on public.membros
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

grant insert (casa_id, titulo, categoria, icone, minutos, esforco, chatice, recorrencia, prazo)
  on public.tarefas to authenticated;
grant update (titulo, categoria, icone, minutos, esforco, chatice, recorrencia, prazo, ativa)
  on public.tarefas to authenticated;
create policy "tarefas: membros criam" on public.tarefas
  for insert to authenticated with check (public.is_membro(casa_id));
create policy "tarefas: membros editam" on public.tarefas
  for update to authenticated using (public.is_membro(casa_id)) with check (public.is_membro(casa_id));

-- RPCs liberadas para o app. Funções "_" e "cron_" ficam só para o servidor.
grant execute on function
  public.calcular_pontos(integer, smallint, numeric, integer, numeric),
  public.multiplicador_esforco(smallint),
  public.bonus_bomba(integer),
  public.minha_casa(),
  public.is_membro(uuid),
  public.criar_casa(text),
  public.entrar_casa(text),
  public.sair_da_casa(),
  public.definir_cor(text),
  public.reservar_tarefa(uuid),
  public.liberar_tarefa(uuid),
  public.concluir_tarefa(uuid, text),
  public.validar_registro(uuid),
  public.contestar_registro(uuid, text),
  public.definir_aposta(text),
  public.definir_meta(integer, text),
  public.resgatar_premio(uuid),
  public.comprar_carta(text),
  public.usar_carta(uuid, uuid)
to authenticated;

-- Realtime: os dois celulares recebem as mudanças (respeitando as políticas de leitura acima).
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime
      add table public.casas, public.membros, public.tarefas, public.temporadas, public.registros;
  end if;
end;
$$;
