module.exports = async function handler(req, res) {
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ ok: false, error: 'Método não permitido.' });
  }

  const latitude = Number(req.headers['x-vercel-ip-latitude']);
  const longitude = Number(req.headers['x-vercel-ip-longitude']);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) ||
      latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return res.status(503).json({ ok: false, error: 'Localização aproximada indisponível.' });
  }

  try {
    const url =
      'https://api.open-meteo.com/v1/forecast?latitude=' + encodeURIComponent(latitude) +
      '&longitude=' + encodeURIComponent(longitude) +
      '&current=temperature_2m,weather_code,is_day&timezone=auto';

    const response = await fetch(url, {
      headers: { 'Accept': 'application/json' }
    });

    if (!response.ok) {
      throw new Error('Falha no provedor de clima.');
    }

    const data = await response.json();
    const current = data && data.current;

    if (!current) {
      throw new Error('Clima atual indisponível.');
    }

    res.setHeader('Cache-Control', 's-maxage=600, stale-while-revalidate=900');
    return res.status(200).json({
      ok: true,
      temp: Number(current.temperature_2m),
      code: Number(current.weather_code),
      isDay: Number(current.is_day)
    });
  } catch (error) {
    return res.status(502).json({ ok: false, error: 'Não foi possível consultar o clima agora.' });
  }
};
