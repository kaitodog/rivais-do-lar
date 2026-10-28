import { assinarJwt, dadosDaMensagem } from "./google.ts";

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg);
}

async function contaDeTeste() {
  const par = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", par.privateKey));
  const b64 = btoa(String.fromCharCode(...der)).match(/.{1,64}/g)!.join("\n");
  return {
    par,
    conta: {
      project_id: "rivais",
      client_email: "push@rivais.iam.gserviceaccount.com",
      private_key: `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n`,
    },
  };
}

function decodificar(parte: string): Uint8Array<ArrayBuffer> {
  const b64 = parte.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((parte.length + 3) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

Deno.test("JWT da conta de serviço é RS256 válido e com o escopo do FCM", async () => {
  const { par, conta } = await contaDeTeste();
  const jwt = await assinarJwt(conta, 1_700_000_000);
  const [cab, corpo, assinatura] = jwt.split(".");
  const dados = JSON.parse(new TextDecoder().decode(decodificar(corpo)));
  assert(JSON.parse(new TextDecoder().decode(decodificar(cab))).alg === "RS256", "alg");
  assert(dados.scope === "https://www.googleapis.com/auth/firebase.messaging", "escopo");
  assert(dados.exp - dados.iat === 3600, "validade de 1h");
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    par.publicKey,
    decodificar(assinatura),
    new TextEncoder().encode(`${cab}.${corpo}`),
  );
  assert(ok, "assinatura confere com a chave pública");
});

Deno.test("dados do FCM viram strings", () => {
  const d = dadosDaMensagem({ id: "n1", tipo: "virada", titulo: "T", corpo: "C", dados: { registro: "r1", n: 3, x: { a: 1 } } });
  assert(d.registro === "r1" && d.n === "3" && d.x === '{"a":1}' && d.tipo === "virada", JSON.stringify(d));
});
