// Token OAuth do Google a partir da conta de serviço do Firebase (JWT RS256 assinado com WebCrypto).

export interface ContaDeServico {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
}

const ESCOPO_FCM = "https://www.googleapis.com/auth/firebase.messaging";

function base64url(dados: Uint8Array | string): string {
  const bytes = typeof dados === "string" ? new TextEncoder().encode(dados) : dados;
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemParaDer(pem: string): ArrayBuffer {
  const corpo = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const binario = atob(corpo);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return bytes.buffer;
}

export async function assinarJwt(conta: ContaDeServico, agoraSegundos: number): Promise<string> {
  const cabecalho = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const corpo = base64url(JSON.stringify({
    iss: conta.client_email,
    scope: ESCOPO_FCM,
    aud: conta.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: agoraSegundos,
    exp: agoraSegundos + 3600,
  }));
  const chave = await crypto.subtle.importKey(
    "pkcs8",
    pemParaDer(conta.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const assinatura = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", chave, new TextEncoder().encode(`${cabecalho}.${corpo}`));
  return `${cabecalho}.${corpo}.${base64url(new Uint8Array(assinatura))}`;
}

export async function tokenDeAcesso(conta: ContaDeServico): Promise<string> {
  const jwt = await assinarJwt(conta, Math.floor(Date.now() / 1000));
  const resposta = await fetch(conta.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!resposta.ok) throw new Error(`Google OAuth ${resposta.status}: ${await resposta.text()}`);
  const { access_token } = await resposta.json();
  return access_token as string;
}

/** O FCM só aceita strings em `data`. */
export function dadosDaMensagem(
  n: { id: string; tipo: string; titulo: string; corpo: string; dados: Record<string, unknown> },
): Record<string, string> {
  const extra = Object.fromEntries(
    Object.entries(n.dados ?? {}).map(([k, v]) => [k, typeof v === "string" ? v : JSON.stringify(v)]),
  );
  return { ...extra, id: n.id, tipo: n.tipo, titulo: n.titulo, corpo: n.corpo };
}
