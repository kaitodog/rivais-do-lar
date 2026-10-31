-- 0.5.2: libera os tipos novos no CHECK da tabela cartas.
-- Sem isso, comprar Café/Seguro/Balde/Cobrança/Castigo/Vale/etc. falha no banco,
-- mesmo com a função carta_custo conhecendo o tipo.

alter table public.cartas drop constraint if exists cartas_tipo_check;
alter table public.cartas add constraint cartas_tipo_check check (
  tipo in (
    'holofote', 'escudo', 'troca', 'dobradinha', 'hora_feliz', 'veto',
    'cafe_passado', 'seguro', 'balde', 'cobranca', 'castigo', 'vale',
    'pedagio', 'mao_atada', 'roubo_cena', 'combo', 'dobrou',
    'reviravolta', 'allin', 'arrastao'
  )
);

alter table public.cartas drop constraint if exists cartas_raridade_check;
alter table public.cartas add constraint cartas_raridade_check check (
  raridade in ('comum', 'rara', 'epica', 'decisiva')
);
