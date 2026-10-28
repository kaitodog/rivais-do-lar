// Apaga as fotos do depois com mais de 30 dias (Storage só aceita remoção pela API) e limpa o caminho.
import { createClient } from "npm:@supabase/supabase-js@2.118.0";

Deno.serve(async (req) => {
  const segredo = Deno.env.get("CRON_SECRET");
  if (!segredo || req.headers.get("Authorization") !== `Bearer ${segredo}`) {
    return new Response("Não autorizado", { status: 401 });
  }
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const limite = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString();

  let apagadas = 0;
  for (;;) {
    const { data: antigos, error } = await supabase
      .from("registros")
      .select("id, foto_path")
      .not("foto_path", "is", null)
      .lt("criado_em", limite)
      .limit(500);
    if (error) return Response.json({ erro: error.message }, { status: 500 });
    if (!antigos?.length) break;

    const { error: erroStorage } = await supabase.storage.from("fotos").remove(antigos.map((r) => r.foto_path as string));
    if (erroStorage) return Response.json({ erro: erroStorage.message, apagadas }, { status: 500 });
    await supabase.from("registros").update({ foto_path: null }).in("id", antigos.map((r) => r.id));
    apagadas += antigos.length;
  }
  return Response.json({ apagadas });
});
