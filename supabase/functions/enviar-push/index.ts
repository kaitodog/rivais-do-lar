// Entrega as notificações pendentes (tabela notificacoes) via FCM HTTP v1.
// Chamada pelo pg_net (trigger + cron) com o segredo CRON_SECRET; usa a service role do Supabase.
import { createClient } from "npm:@supabase/supabase-js@2.118.0";
import { type ContaDeServico, dadosDaMensagem, tokenDeAcesso } from "../_shared/google.ts";

const LOTE = 100;
const MAX_TENTATIVAS = 5;

Deno.serve(async (req) => {
  const segredo = Deno.env.get("CRON_SECRET");
  if (!segredo || req.headers.get("Authorization") !== `Bearer ${segredo}`) {
    return new Response("Não autorizado", { status: 401 });
  }
  const bruta = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!bruta) {
    return Response.json({ enviadas: 0, motivo: "FCM_SERVICE_ACCOUNT não configurado" });
  }
  const conta = JSON.parse(bruta) as ContaDeServico;
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const { data: pendentes, error } = await supabase
    .from("notificacoes")
    .select("id, user_id, tipo, titulo, corpo, dados, tentativas")
    .eq("push", true)
    .is("enviada_em", null)
    .lt("tentativas", MAX_TENTATIVAS)
    .order("criada_em")
    .limit(LOTE);
  if (error) return Response.json({ erro: error.message }, { status: 500 });
  if (!pendentes?.length) return Response.json({ enviadas: 0 });

  const usuarios = [...new Set(pendentes.map((n) => n.user_id))];
  const { data: membros } = await supabase.from("membros").select("user_id, fcm_token").in("user_id", usuarios);
  const tokens = new Map((membros ?? []).map((m) => [m.user_id, m.fcm_token as string | null]));
  const acesso = await tokenDeAcesso(conta);

  let enviadas = 0;
  for (const n of pendentes) {
    const token = tokens.get(n.user_id);
    if (!token) {
      // Sem aparelho registrado: fica só no histórico do app.
      await supabase.from("notificacoes").update({ enviada_em: new Date().toISOString(), erro: "sem token" }).eq("id", n.id);
      continue;
    }
    const resposta = await fetch(`https://fcm.googleapis.com/v1/projects/${conta.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${acesso}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: dadosDaMensagem(n),
          android: { priority: "high", ttl: "86400s" },
        },
      }),
    });
    if (resposta.ok) {
      enviadas++;
      await supabase.from("notificacoes").update({ enviada_em: new Date().toISOString(), erro: null }).eq("id", n.id);
    } else {
      const texto = await resposta.text();
      const tokenMorto = resposta.status === 404 || texto.includes("UNREGISTERED");
      if (tokenMorto) await supabase.from("membros").update({ fcm_token: null }).eq("user_id", n.user_id);
      await supabase.from("notificacoes").update({
        tentativas: n.tentativas + 1,
        erro: `${resposta.status}: ${texto.slice(0, 300)}`,
        enviada_em: tokenMorto ? new Date().toISOString() : null,
      }).eq("id", n.id);
    }
  }
  return Response.json({ enviadas, total: pendentes.length });
});
