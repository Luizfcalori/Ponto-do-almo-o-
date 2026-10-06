module.exports = async (req,res) => {
  res.setHeader('Cache-Control','no-store');
  if (req.method !== 'POST') {
    res.setHeader('Allow','POST');
    return res.status(405).json({ok:false,error:'Método não permitido.'});
  }
  res.setHeader('Set-Cookie','senior_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0');
  return res.status(200).json({ok:true});
};