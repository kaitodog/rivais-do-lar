-- Fórmula de pontos no servidor. Os mesmos casos estão em app/src/test (PontuacaoTest.kt).
begin;
create extension if not exists pgtap with schema extensions;
select plan(14);

select is(public.calcular_pontos(20, 1::smallint, 1.3), 40, 'louça: 20 min, médio, chatice 1,3 = 40');
select is(public.calcular_pontos(45, 2::smallint, 1.5), 135, 'banheiro: 45 min, pesado, chatice 1,5 = 135');
select is(public.calcular_pontos(45, 2::smallint, 1.5, 3), 175, 'banheiro bomba há 3 dias = 175');
select is(public.calcular_pontos(25, 1::smallint, 1.6), 60, 'gaiola = 60');
select is(public.calcular_pontos(15, 1::smallint, 1.1), 25, 'roupa = 25');
select is(public.calcular_pontos(5, 1::smallint, 1.3), 10, 'lixo = 10');
select is(public.calcular_pontos(60, 1::smallint, 1.0), 90, 'mercado = 90');
select is(public.calcular_pontos(20, 0::smallint, 1.5), 30, 'cardápio = 30');
select is(public.calcular_pontos(1, 0::smallint, 1.0), 5, 'mínimo de 5 pontos');
select is(public.calcular_pontos(25, 0::smallint, 1.1), 30, 'meio exato (27,5) arredonda para cima');
select is(public.calcular_pontos(20, 1::smallint, 1.3, 15), 80, 'bônus bomba tem teto x2');
select is(public.calcular_pontos(20, 1::smallint, 1.3, 0, 2), 80, 'multiplicador do jogador entra na conta');
select is(public.bonus_bomba(4), 1.4, 'bomba +10% por dia');
select is(public.calcular_pontos(120, 2::smallint, 1.6, 10), 770, 'pior caso: 120 min, pesado, 1,6, bomba x2');

select * from finish();
rollback;
