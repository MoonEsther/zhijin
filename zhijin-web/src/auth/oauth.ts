// OAuth2 PKCE 工具：zhijin-console 公共客户端（无 client_secret，D1）
const CLIENT_ID = 'zhijin-console';
const REDIRECT_URI = `${window.location.origin}/callback`;
const AUTH_BASE = '/oauth2';

/** 生成 64 字符 code_verifier（RFC 7636：43~128 位 ASCII 字母数字，去除非 base64url 字符）。 */
export function generateVerifier(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/[^a-zA-Z0-9]/g, '').slice(0, 64);
}

/** 由 verifier 计算 S256 code_challenge（base64url 无填充编码的 SHA-256 摘要）。 */
export async function generateChallenge(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** 生成防 CSRF 的 state（时间戳 + 随机串）。 */
export function generateState(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

/** 跳转到授权服务器登录（PKCE，跳转 URL 必须带 code_challenge，D7：缺省会被 requireProofKey 拒绝）。 */
export async function redirectToAuthorize() {
  const verifier = generateVerifier();
  sessionStorage.setItem('code_verifier', verifier);
  const challenge = await generateChallenge(verifier);
  const state = generateState();
  sessionStorage.setItem('oauth_state', state);
  window.location.href =
    `${AUTH_BASE}/authorize?` +
    new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: 'code',
      scope: 'openid profile',
      redirect_uri: REDIRECT_URI,
      state,
      code_challenge: challenge,
      code_challenge_method: 'S256',
    }).toString();
}

/** 换 token（公共客户端无 secret，D1：Authorization 头不带 client_secret，仅表单提交 client_id）。 */
export async function exchangeCode(code: string, verifier: string) {
  const resp = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      code_verifier: verifier,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
    }),
  });
  if (!resp.ok) throw new Error(`token exchange failed: ${resp.status}`);
  return resp.json() as Promise<{ access_token: string; expires_in?: number }>;
}
