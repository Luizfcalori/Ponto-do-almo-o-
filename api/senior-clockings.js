const crypto = require('crypto');

const CLOCKINGS_URL = 'https://platform.senior.com.br/t/senior.com.br/bridge/1.0/rest/hcm/pontomobile/entities/clockingEvent';

function keyFromSecret() {
  const secret = process.env.SESSION_SECRET;
  if (!secret) throw new Error('SESSION_SECRET não configurado');
  return crypto.createHash('sha256').update(secret).digest();
}

function decryptSession(value) {
  const raw = Buffer.from(value, 'base64url');
  if (raw.length < 29) throw new Error('Sessão inválida');
  const iv = raw.subarray(0,12);
  const tag = raw.subarray(12,28);
  const encrypted = raw.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', keyFromSecret(), iv);
  decipher.setAuthTag(tag);
  return JSON.parse(Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8'));
}

function readCookie(req, name) {
  const header = req.headers.cookie || '';
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    const k = part.slice(0,i).trim();
    if (k === name) return part.slice(i+1).trim();
  }
  return null;
}

function flatten(obj, prefix='', out=[], depth=0) {
  if (!obj || typeof obj !== 'object' || depth > 4) return out;
  for (const [key,value] of Object.entries(obj)) {
    const path = prefix ? prefix + '.' + key : key;
    if (value && typeof value === 'object') {
      if (!Array.isArray(value)) flatten(value,path,out,depth+1);
    } else {
      out.push({ key:path, leaf:key, value });
    }
  }
  return out;
}

function scoreKey(key) {
  const k = key.toLowerCase();
  let score = 0;
  if (k.includes('clocking')) score += 16;
  if (k.includes('event')) score += 9;
  if (k.includes('timestamp')) score += 12;
  if (k.includes('datetime')) score += 10;
  if (k.includes('date')) score += 5;
  if (k.includes('time')) score += 5;
  if (k.includes('moment')) score += 4;
  if (k.includes('created')) score -= 8;
  if (k.includes('updated')) score -= 10;
  if (k.includes('export')) score -= 8;
  return score;
}

function parseFullDate(value) {
  if (typeof value === 'number' && value > 100000000000) {
    const d = new Date(value);
    return Number.isFinite(d.getTime()) ? d : null;
  }
  if (typeof value !== 'string') return null;
  const s = value.trim();
  if (!/[T\s]\d{1,2}:\d{2}/.test(s) && !/^\d{10,13}$/.test(s)) return null;
  const d = /^\d{10,13}$/.test(s)
    ? new Date(Number(s.length === 10 ? s + '000' : s))
    : new Date(s);
  return Number.isFinite(d.getTime()) && d.getFullYear() >= 2020 ? d : null;
}

function extractTimestamp(event) {
  const flat = flatten(event);
  const candidates = [];

  for (const item of flat) {
    const d = parseFullDate(item.value);
    if (d) candidates.push({ d, score:scoreKey(item.key), key:item.key });
  }

  if (candidates.length) {
    candidates.sort((a,b)=>b.score-a.score);
    return { date:candidates[0].d, source:candidates[0].key };
  }

  const dateParts = flat.filter(x => typeof x.value === 'string' && /\d{4}-\d{2}-\d{2}/.test(x.value))
    .sort((a,b)=>scoreKey(b.key)-scoreKey(a.key));
  const timeParts = flat.filter(x => typeof x.value === 'string' && /(?:^|\D)([01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?:\D|$)/.test(x.value))
    .sort((a,b)=>scoreKey(b.key)-scoreKey(a.key));

  if (dateParts.length && timeParts.length) {
    const dm = dateParts[0].value.match(/(\d{4}-\d{2}-\d{2})/);
    const tm = timeParts[0].value.match(/([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?/);
    if (dm && tm) {
      const iso = dm[1] + 'T' + String(tm[1]).padStart(2,'0') + ':' + tm[2] + ':' + (tm[3] || '00') + '-03:00';
      const d = new Date(iso);
      if (Number.isFinite(d.getTime())) return { date:d, source:dateParts[0].key + '+' + timeParts[0].key };
    }
  }

  return null;
}

function saoPauloParts(date) {
  const parts = new Intl.DateTimeFormat('en-CA',{
    timeZone:'America/Sao_Paulo',
    year:'numeric',month:'2-digit',day:'2-digit',
    hour:'2-digit',minute:'2-digit',second:'2-digit',
    hourCycle:'h23'
  }).formatToParts(date);
  const get = type => parts.find(x=>x.type===type)?.value || '';
  return {
    day:get('year') + '-' + get('month') + '-' + get('day'),
    time:get('hour') + ':' + get('minute'),
    iso:date.toISOString()
  };
}

function normalizeEvents(payload) {
  let rows = payload;
  if (rows && Array.isArray(rows.contents)) rows = rows.contents;
  if (rows && Array.isArray(rows.content)) rows = rows.content;
  if (rows && Array.isArray(rows.items)) rows = rows.items;
  if (!Array.isArray(rows)) rows = [];

  return rows.map((event,index) => {
    const ts = extractTimestamp(event);
    if (!ts) return null;
    const local = saoPauloParts(ts.date);
    return {
      id: String(event.id || event.uuid || event.clockId || index),
      day:local.day,
      time:local.time,
      iso:local.iso,
      origin:event.origin || event.clockEventOrigin || event.clockingEventOrigin || null
    };
  }).filter(Boolean).sort((a,b)=>a.iso.localeCompare(b.iso));
}

module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');

  if (req.method !== 'GET') {
    res.setHeader('Allow','GET');
    return res.status(405).json({ ok:false, error:'Método não permitido.' });
  }

  try {
    const cookie = readCookie(req,'senior_session');
    if (!cookie) return res.status(401).json({ ok:false, connected:false, error:'Senior não conectada.' });

    const session = decryptSession(cookie);
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      res.setHeader('Set-Cookie','senior_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0');
      return res.status(401).json({ ok:false, connected:false, error:'Sua sessão da Senior expirou.' });
    }

    const url = CLOCKINGS_URL + '?offset=0&size=250&translation=false';
    const response = await fetch(url,{
      headers:{
        'Authorization': (session.tokenType || 'Bearer') + ' ' + session.accessToken,
        'Accept':'application/json'
      }
    });

    const text = await response.text();
    let data = {};
    try { data = text ? JSON.parse(text) : []; } catch (_) { data = []; }

    if (response.status === 401 || response.status === 403) {
      return res.status(response.status).json({
        ok:false,
        connected:true,
        permissionDenied:response.status === 403,
        error: response.status === 403
          ? 'Seu usuário entrou na Senior, mas essa conta não liberou a leitura das marcações por API.'
          : 'A Senior pediu uma nova autenticação.'
      });
    }

    if (!response.ok) {
      return res.status(502).json({ ok:false, connected:true, error:'A Senior não retornou as marcações agora.', seniorStatus:response.status });
    }

    const events = normalizeEvents(data);
    const today = new Intl.DateTimeFormat('en-CA',{
      timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'
    }).format(new Date());

    const todayEvents = events.filter(e=>e.day===today);

    return res.status(200).json({
      ok:true,
      connected:true,
      username:session.username || null,
      today,
      events:todayEvents,
      eventCount:todayEvents.length,
      scannedCount:events.length
    });
  } catch (error) {
    return res.status(401).json({ ok:false, connected:false, error:'A sessão segura da Senior precisa ser refeita.' });
  }
};