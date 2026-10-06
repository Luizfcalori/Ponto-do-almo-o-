const crypto = require('crypto');

const LOGIN_URL = 'https://platform.senior.com.br/t/senior.com.br/bridge/1.0/rest/platform/authentication/actions/login';

function keyFromSecret() {
  const secret = process.env.SESSION_SECRET;
  if (!secret) throw new Error('SESSION_SECRET não configurado');
  return crypto.createHash('sha256').update(secret).digest();
}

function encryptSession(payload) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', keyFromSecret(), iv);
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(payload), 'utf8'),
    cipher.final()
  ]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]).toString('base64url');
}

function parseSeniorToken(data) {
  let token = data && data.jsonToken ? data.jsonToken : data;
  if (typeof token === 'string') {
    try { token = JSON.parse(token); } catch (_) {}
  }
  if (token && token.jsonToken) token = token.jsonToken;
  return token || {};
}

module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ ok:false, error:'Método não permitido.' });
  }

  try {
    const username = String(req.body?.username || '').trim();
    const password = String(req.body?.password || '');

    if (!username || !password || username.length > 180 || password.length > 300) {
      return res.status(400).json({ ok:false, error:'Informe seu usuário e senha da Senior.' });
    }

    const response = await fetch(LOGIN_URL, {
      method:'POST',
      headers:{ 'Content-Type':'application/json', 'Accept':'application/json' },
      body:JSON.stringify({ username, password })
    });

    let data = {};
    const text = await response.text();
    try { data = text ? JSON.parse(text) : {}; } catch (_) { data = { raw:text }; }

    if (!response.ok) {
      return res.status(response.status === 401 ? 401 : 502).json({
        ok:false,
        error: response.status === 401
          ? 'A Senior recusou o login. Confira o mesmo usuário e senha que você usa na plataforma.'
          : 'A Senior não concluiu o login agora.',
        seniorStatus: response.status
      });
    }

    const token = parseSeniorToken(data);
    const accessToken = token.access_token || token.accessToken;
    const refreshToken = token.refresh_token || token.refreshToken || null;
    const expiresIn = Number(token.expires_in || token.expiresIn || 604800);

    if (!accessToken) {
      return res.status(502).json({
        ok:false,
        error:'A Senior autenticou, mas não retornou um token de acesso compatível.'
      });
    }

    const session = encryptSession({
      accessToken,
      refreshToken,
      tokenType: token.token_type || token.tokenType || 'Bearer',
      username: token.username || username,
      expiresAt: Date.now() + Math.max(300, expiresIn) * 1000
    });

    const maxAge = Math.min(Math.max(300, expiresIn), 604800);
    res.setHeader(
      'Set-Cookie',
      'senior_session=' + session +
      '; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=' + maxAge
    );

    return res.status(200).json({
      ok:true,
      username: token.username || username,
      expiresIn:maxAge
    });
  } catch (error) {
    return res.status(500).json({ ok:false, error:'Não foi possível iniciar a conexão segura com a Senior.' });
  }
};