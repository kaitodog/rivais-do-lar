-- Dados de exemplo para o Supabase local (supabase db reset). Não rode em produção.
-- Duas contas (senha "senha123") já na mesma casa, código de convite 123456, com as tarefas do protótipo.

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                        confirmation_token, email_change, email_change_token_new, recovery_token)
values
  ('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-4000-8000-000000000001', 'authenticated', 'authenticated',
   'vitor@rivais.local', extensions.crypt('senha123', extensions.gen_salt('bf')), now(),
   '{"provider":"email","providers":["email"]}', '{"nome":"Vitor"}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', 'aaaaaaaa-0000-4000-8000-000000000002', 'authenticated', 'authenticated',
   'bia@rivais.local', extensions.crypt('senha123', extensions.gen_salt('bf')), now(),
   '{"provider":"email","providers":["email"]}', '{"nome":"Bia"}', now(), now(), '', '', '', '');

insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
select gen_random_uuid(), u.id, u.id::text, jsonb_build_object('sub', u.id::text, 'email', u.email), 'email', now(), now(), now()
  from auth.users u
 where u.email in ('vitor@rivais.local', 'bia@rivais.local');

insert into public.casas (id, nome, codigo_convite)
values ('bbbbbbbb-0000-4000-8000-000000000001', 'Casa de exemplo', '123456');

insert into public.membros (casa_id, user_id, cor) values
  ('bbbbbbbb-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'azul'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000002', 'laranja');

select public._tarefas_iniciais('bbbbbbbb-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001');
select public._temporada_atual('bbbbbbbb-0000-4000-8000-000000000001');

-- Uma bomba para ver o vermelho no placar.
update public.tarefas
   set prazo = now() - interval '3 days', dias_parada = 3
 where casa_id = 'bbbbbbbb-0000-4000-8000-000000000001' and titulo = 'Limpar banheiro completo';
