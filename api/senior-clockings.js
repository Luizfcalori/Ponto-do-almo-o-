const crypto = require('crypto');

const BASE_URL = 'https://platform.senior.com.br/t/senior.com.br/bridge/1.0/rest/hcm/pontomobile';

function keyFromSecret() {
  const secret = process.env.SESSION_SECRET;
  if (!secret) throw new Error('SESSION_SECRET não configurado');
  return crypto.createHash('sha256').update(secret).digest();
}

function decryptSession(value) {
  const raw = Buffer.from(value, 'base64url');
  if (raw.length < 29) throw new Error('Sessão inválida');
  const iv = raw.subarray(0, 12);
  const tag = raw.subarray(12, 28);
  const encrypted = raw.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', keyFromSecret(), iv);
  decipher.setAuthTag(tag);
  return JSON.parse(
    Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8')
  );
}

function readCookie(req, name) {
  const header = req.headers.cookie || '';
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    if (part.slice(0, i).trim() === name) return part.slice(i + 1).trim();
  }
  return null;
}

function todaySaoPaulo() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
}

function normalizeTime(value) {
  if (typeof value !== 'string') return null;
  const m = value.match(/([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?/);
  return m ? String(m[1]).padStart(2, '0') + ':' + m[2] : null;
}

function normalizeDate(value) {
  if (typeof value !== 'string') return null;
  const m = value.match(/(\d{4}-\d{2}-\d{2})/);
  return m ? m[1] : null;
}

function normalizeEvents(payload) {
  let rows = payload;
  if (rows && Array.isArray(rows.result)) rows = rows.result;
  else if (rows && Array.isArray(rows.contents)) rows = rows.contents;
  else if (rows && Array.isArray(rows.content)) rows = rows.content;
  else if (rows && Array.isArray(rows.items)) rows = rows.items;
  if (!Array.isArray(rows)) rows = [];

  return rows.map((event, index) => {
    const day = normalizeDate(event.dateEvent);
    const time = normalizeTime(event.timeEvent);

    if (day && time) {
      return {
        id: String(event.id || event.uuid || event.appointmentId || index),
        day,
        time,
        sortKey: day + 'T' + time,
        origin: event.origin || null,
        nsrNumber: event.nsrNumber || null
      };
    }

    const rawDate =
      event.clockingDateTime ||
      event.eventDateTime ||
      event.dateTime ||
      event.createdAt ||
      null;

    if (typeof rawDate === 'string') {
      const d = new Date(rawDate);
      if (Number.isFinite(d.getTime())) {
        const parts = new Intl.DateTimeFormat('en-CA', {
          timeZone: 'America/Sao_Paulo',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hourCycle: 'h23'
        }).formatToParts(d);
        const get = type => parts.find(x => x.type === type)?.value || '';
        const dday = get('year') + '-' + get('month') + '-' + get('day');
        const ttime = get('hour') + ':' + get('minute');
        return {
          id: String(event.id || event.uuid || index),
          day: dday,
          time: ttime,
          sortKey: dday + 'T' + ttime,
          origin: event.origin || null,
          nsrNumber: event.nsrNumber || null
        };
      }
    }

    return null;
  }).filter(Boolean).sort((a, b) => a.sortKey.localeCompare(b.sortKey));
}

async function seniorFetch(path, session, options = {}) {
  const response = await fetch(BASE_URL + path, {
    ...options,
    headers: {
      'Authorization': (session.tokenType || 'Bearer') + ' ' + session.accessToken,
      'Accept': 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  });

  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = null;
  }

  return {
    ok: response.ok,
    status: response.status,
    data,
    bodyPreview: text ? text.slice(0, 280) : ''
  };
}

module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');

  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ ok: false, error: 'Método não permitido.' });
  }

  try {
    const cookie = readCookie(req, 'senior_session');
    if (!cookie) {
      return res.status(401).json({
        ok: false,
        connected: false,
        error: 'Senior não conectada.'
      });
    }

    const session = decryptSession(cookie);

    if (!session.accessToken || session.expiresAt <= Date.now()) {
      res.setHeader(
        'Set-Cookie',
        'senior_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0'
      );
      return res.status(401).json({
        ok: false,
        connected: false,
        error: 'Sua sessão da Senior expirou.'
      });
    }

    const today = todaySaoPaulo();

    // Consulta feita para o próprio usuário autenticado.
    // A Senior descreve clockingEventByActiveUserQuery como a rota que
    // recupera as marcações do colaborador do usuário que fez a requisição.
    const selfBody = {
      filter: {
        pageInfo: {
          pageSize: 250,
          page: 0
        },
        period: {
          initialDate: today,
          finalDate: today,
          initialTime: '00:00:00',
          finalTime: '23:59:59'
        }
      }
    };

    let clockResp = await seniorFetch(
      '/queries/clockingEventByActiveUserQuery',
      session,
      {
        method: 'POST',
        body: JSON.stringify(selfBody)
      }
    );

    // Fallback para instalações antigas da Senior que não tenham a consulta
    // self-service habilitada, mas liberem a identificação do colaborador.
    if (!clockResp.ok && clockResp.status !== 401) {
      const employeeResp = await seniorFetch(
        '/queries/employeeByUserQuery',
        session,
        { method: 'GET' }
      );

      const employee =
        employeeResp.data?.employee ||
        employeeResp.data?.result?.employee ||
        null;

      const employeeId =
        employee?.id ||
        employee?.uuid ||
        employee?.employeeId ||
        null;

      if (employeeResp.ok && employeeId) {
        const employeeBody = {
          employeeId,
          filter: selfBody.filter
        };

        const fallbackResp = await seniorFetch(
          '/queries/clockingEventBetweenPeriodByEmployeeQuery',
          session,
          {
            method: 'POST',
            body: JSON.stringify(employeeBody)
          }
        );

        if (fallbackResp.ok || fallbackResp.status === 401) {
          clockResp = fallbackResp;
        }
      }
    }

    if (clockResp.status === 401) {
      return res.status(401).json({
        ok: false,
        connected: false,
        error: 'A Senior pediu uma nova autenticação.',
        step: 'clockings'
      });
    }

    if (clockResp.status === 403) {
      return res.status(403).json({
        ok: false,
        connected: true,
        sessionHeld: true,
        permissionDenied: true,
        error: 'Seu login continua ativo, mas a Senior bloqueou também a consulta de marcações do próprio usuário.',
        step: 'active-user-clockings'
      });
    }

    if (!clockResp.ok) {
      return res.status(502).json({
        ok: false,
        connected: true,
        sessionHeld: true,
        error: 'Seu login continua ativo, mas a Senior não retornou suas marcações nesta tentativa.',
        step: 'active-user-clockings',
        seniorStatus: clockResp.status
      });
    }

    const events = normalizeEvents(clockResp.data);
    const todayEvents = events.filter(event => event.day === today);

    return res.status(200).json({
      ok: true,
      connected: true,
      sessionHeld: true,
      username: session.username || null,
      today,
      events: todayEvents,
      eventCount: todayEvents.length,
      scannedCount: events.length,
      source: 'active-user-clockings'
    });
  } catch (error) {
    return res.status(401).json({
      ok: false,
      connected: false,
      error: 'A sessão segura da Senior precisa ser refeita.'
    });
  }
};