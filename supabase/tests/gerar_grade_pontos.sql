-- Regera app/src/test/resources/pontos_servidor.csv (a grade que o PontuacaoTest compara com o app):
--   psql "$DB_URL" -qAt -F, -f supabase/tests/gerar_grade_pontos.sql > grade.csv  (e acrescente o cabeçalho)
select m, e, c::numeric(2, 1), d, public.calcular_pontos(m, e::smallint, c, d)
  from generate_series(5, 120, 5) m,
       generate_series(0, 2) e,
       (select (10 + s)::numeric / 10 c from generate_series(0, 6) s) cs,
       generate_series(0, 10) d
 order by 1, 2, 3, 4;
