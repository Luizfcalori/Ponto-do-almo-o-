package com.luizcalori.horadoalmoco;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Calendar;

public class MainActivity extends Activity {

    private static final String DASHBOARD_URL =
            "https://ponto-do-almoco-luiz.vercel.app/?native=1";

    private static final String RECEIPTS_URL =
            "https://platform.senior.com.br/senior-x/#/Gest%C3%A3o%20de%20Pessoas%20%7C%20HCM/1/res:%2F%2Fsenior.com.br%2Fhcm%2Fpontomobile%2FclockingEventReceipts?category=frame&link=https:%2F%2Fplatform.senior.com.br%2Fhcm-pontomobile%2Fhcm%2Fpontomobile%2F%23%2Fclocking-event-receipts&withCredentials=true&r=2";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView dashboardWebView;
    private LinearLayout seniorContainer;
    private WebView seniorWebView;
    private boolean seniorVisible = false;

    private final String EXPAND_RECEIPTS_JS =
            "(function(){" +
            "function n(s){return (s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').trim().toUpperCase();}" +
            "function docs(){" +
            " var out=[document],seen=[];" +
            " for(var q=0;q<out.length;q++){" +
            "  var d=out[q];if(!d||seen.indexOf(d)>=0)continue;seen.push(d);" +
            "  try{var fs=d.querySelectorAll('iframe');for(var i=0;i<fs.length;i++){try{var fd=fs[i].contentDocument;if(fd&&out.indexOf(fd)<0)out.push(fd);}catch(e){}}}catch(e){}" +
            " }" +
            " return out;" +
            "}" +
            "var ds=docs();" +
            "for(var di=0;di<ds.length;di++){" +
            " var d=ds[di];" +
            " try{" +
            "  var all=[].slice.call(d.querySelectorAll('button,a,[role=button],h1,h2,h3,h4,div,span'));" +
            "  var el=all.find(function(x){var t=n(x.innerText);return (t.indexOf('COMPROVANTES DE MARCACOES')>=0||t.indexOf('COMPROVANTE DE MARCACOES')>=0)&&t.length<160;});" +
            "  if(!el)continue;" +
            "  try{el.scrollIntoView({block:'center',behavior:'instant'});}catch(e){}" +
            "  var p=el;" +
            "  for(var j=0;j<6&&p;j++,p=p.parentElement){" +
            "   var tag=(p.tagName||'').toLowerCase(),role=p.getAttribute&&p.getAttribute('role');" +
            "   if(tag==='button'||tag==='a'||role==='button'||typeof p.onclick==='function'){try{p.click();return 'clicked-frame-'+di;}catch(e){}}" +
            "  }" +
            "  try{el.click();return 'clicked-heading-'+di;}catch(e){return 'found-'+di;}" +
            " }catch(e){}" +
            "}" +
            "return 'not-found';" +
            "})()";

    private final String EXTRACT_RECEIPTS_JS =
            "(function(){" +
            "function norm(s){return (s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}" +
            "function timeMatches(s){" +
            " var out=[],re=/(^|\\D)([01]?\\d|2[0-3]):([0-5]\\d):([0-5]\\d)(?=\\D|$)/g,m;" +
            " while((m=re.exec(s||''))!==null){var v=String(m[2]).padStart(2,'0')+':'+m[3];if(out.indexOf(v)<0)out.push(v);}" +
            " return out;" +
            "}" +
            "function docs(){" +
            " var out=[document],seen=[];" +
            " for(var q=0;q<out.length;q++){" +
            "  var d=out[q];if(!d||seen.indexOf(d)>=0)continue;seen.push(d);" +
            "  try{var fs=d.querySelectorAll('iframe');for(var i=0;i<fs.length;i++){try{var fd=fs[i].contentDocument;if(fd&&out.indexOf(fd)<0)out.push(fd);}catch(e){}}}catch(e){}" +
            " }" +
            " return out;" +
            "}" +
            "function findHeading(d){" +
            " try{" +
            "  var all=[].slice.call(d.querySelectorAll('h1,h2,h3,h4,h5,button,a,[role=button],div,span'));" +
            "  for(var i=0;i<all.length;i++){" +
            "   var t=norm((all[i].innerText||'').trim()).toUpperCase();" +
            "   if((t==='COMPROVANTES DE MARCACOES'||t==='COMPROVANTE DE MARCACOES'||t.indexOf('COMPROVANTES DE MARCACOES')===0)&&t.length<120)return all[i];" +
            "  }" +
            " }catch(e){}" +
            " return null;" +
            "}" +
            "function collectAfterHeading(d,h){" +
            " var texts=[];" +
            " try{" +
            "  var root=h;" +
            "  for(var up=0;up<4&&root&&root.parentElement;up++){" +
            "   var p=root.parentElement,txt=norm(p.innerText||'').toUpperCase();" +
            "   if(txt.indexOf('COMPROVANTES DE MARCACOES')>=0 && txt.length<12000){root=p;}" +
            "   else break;" +
            "  }" +
            "  var all=[].slice.call(root.querySelectorAll('*'));" +
            "  var passed=false;" +
            "  for(var i=0;i<all.length;i++){" +
            "   var el=all[i];" +
            "   if(el===h){passed=true;continue;}" +
            "   if(!passed)continue;" +
            "   var t=(el.innerText||'').trim();" +
            "   if(!t||t.length>500)continue;" +
            "   var nt=norm(t).toUpperCase();" +
            "   if(/ESCALA DE TRABALHO|ULTIMA ATUALIZACAO|EMPRESA|FILIAL|COLABORADOR|CENTRO DE CUSTO/.test(nt))continue;" +
            "   if(/(^|\\s)1:12(\\s|$)/.test(t))continue;" +
            "   texts.push(t);" +
            "  }" +
            "  if(!texts.length){" +
            "   var raw=(root.innerText||'');" +
            "   var marker=norm(raw).toUpperCase().indexOf('COMPROVANTES DE MARCACOES');" +
            "   if(marker>=0)texts.push(raw.slice(marker+'COMPROVANTES DE MARCACOES'.length));" +
            "  }" +
            " }catch(e){}" +
            " return texts;" +
            "}" +
            "function findTodayBlock(d,today,todayShort){" +
            " var best=null,bestLen=999999;" +
            " try{" +
            "  var els=[].slice.call(d.querySelectorAll('tr,[role=row],tbody,li,section,article,div'));" +
            "  for(var i=0;i<els.length;i++){" +
            "   var txt=(els[i].innerText||'').trim();" +
            "   if(!txt||txt.length>6000)continue;" +
            "   if(txt.indexOf(today)<0&&txt.indexOf(todayShort)<0)continue;" +
            "   var tm=timeMatches(txt);" +
            "   if(!tm.length)continue;" +
            "   if(txt.length<bestLen){best=txt;bestLen=txt.length;}" +
            "  }" +
            " }catch(e){}" +
            " return best;" +
            "}" +
            "var ds=docs(),today=new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Sao_Paulo',day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date()),todayShort=today.slice(0,5);" +
            "for(var di=0;di<ds.length;di++){" +
            " var d=ds[di],h=findHeading(d);if(!h)continue;" +
            " var bodyText=(d.body&&d.body.innerText)||'';" +
            " var scheduleMatch=bodyText.match(/([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\s*\\((\\d{1,2}:[0-5]\\d)\\)/);" +
            " var scheduleStart=scheduleMatch?(String(scheduleMatch[1]).padStart(2,'0')+':'+scheduleMatch[2]):null;" +
            " var scheduleEnd=scheduleMatch?(String(scheduleMatch[3]).padStart(2,'0')+':'+scheduleMatch[4]):null;" +
            " var scheduleBreak=scheduleMatch?scheduleMatch[5]:null;" +
            " var lines=bodyText.split(/\\n+/).map(function(x){return (x||'').trim();}).filter(Boolean),firstName=null;" +
            " var esc=-1;for(var li=0;li<lines.length;li++){if(norm(lines[li]).toUpperCase().indexOf('ESCALA DE TRABALHO')>=0){esc=li;break;}}" +
            " if(esc>=0){" +
            "  var parts=[];" +
            "  for(var z=esc-1;z>=Math.max(0,esc-8);z--){" +
            "   var cand=lines[z],nc=norm(cand).toUpperCase();" +
            "   if(/^[A-ZÀ-Ú][A-ZÀ-Ú ]{1,60}$/.test(cand)&&!/(MARCA|PONTO|COMPROVANTE|PERIODO|ENTITIES|DEPENDENCIES|ESCALA|ATUALIZACAO)/.test(nc)){parts.unshift(cand);}" +
            "   else if(parts.length)break;" +
            "  }" +
            "  if(parts.length)firstName=parts[0].split(/\\s+/)[0];" +
            " }" +
            " var todayBlock=findTodayBlock(d,today,todayShort),times=[],evidence=[];" +
            " if(todayBlock){" +
            "  var todays=timeMatches(todayBlock);" +
            "  for(var tb=0;tb<todays.length;tb++){if(times.indexOf(todays[tb])<0){times.push(todays[tb]);evidence.push(todayBlock.slice(0,260));}}" +
            " }" +
            " if(times.length>0){" +
            "  if(times.length>4)times=[times[0],times[1],times[2],times[times.length-1]];" +
            "  return JSON.stringify({ok:true,loggedIn:true,day:today,times:times,message:'Marcações de hoje lidas nos comprovantes.',frame:di,evidence:evidence.slice(0,times.length),firstName:firstName,scheduleStart:scheduleStart,scheduleEnd:scheduleEnd,scheduleBreak:scheduleBreak});" +
            " }" +
            " var texts=collectAfterHeading(d,h);times=[];evidence=[];" +
            " for(var k=0;k<texts.length;k++){" +
            "  var t=texts[k],nt=norm(t).toUpperCase();" +
            "  var hasDate=t.indexOf(today)>=0 || t.indexOf(todayShort)>=0;" +
            "  var looksReceipt=/COMPROVANTE|MARCACAO|DATA|HORA|NSR|LOCAL|ORIGEM|REGISTRO/.test(nt);" +
            "  if(!hasDate && !looksReceipt && t.length>120)continue;" +
            "  var tm=timeMatches(t);" +
            "  for(var j=0;j<tm.length;j++){" +
            "   var v=tm[j];" +
            "   if(v==='00:00'||v==='23:59'||v==='01:12'||v==='08:00'||v==='18:00'){" +
            "    if(!hasDate || /ESCALA|JORNADA|INTERVALO/.test(nt))continue;" +
            "   }" +
            "   if(times.indexOf(v)<0){times.push(v);evidence.push(t.slice(0,180));}" +
            "  }" +
            " }" +
            " if(times.length>0){" +
            "  if(times.length>4)times=[times[0],times[1],times[2],times[times.length-1]];" +
            "  return JSON.stringify({ok:true,loggedIn:true,day:today,times:times,message:'Marcações de hoje lidas nos comprovantes.',frame:di,evidence:evidence.slice(0,times.length),firstName:firstName,scheduleStart:scheduleStart,scheduleEnd:scheduleEnd,scheduleBreak:scheduleBreak});" +
            " }" +
            " return JSON.stringify({ok:false,loggedIn:true,day:today,times:[],message:'Comprovantes encontrados, mas ainda não identifiquei as batidas de hoje.',frame:di,firstName:firstName,scheduleStart:scheduleStart,scheduleEnd:scheduleEnd,scheduleBreak:scheduleBreak});" +
            "}" +
            "var topText=(document.body&&document.body.innerText)||'',first=norm(topText).toUpperCase().slice(0,3000),u=location.href||'';" +
            "var login=/LOGIN|ENTRAR|USUARIO|SENHA|PASSWORD|AUTENTICACAO/.test(first)||/login|authentication|signin|sso/i.test(u);" +
            "return JSON.stringify({ok:false,loggedIn:!login,message:login?'Faça login na Senior para continuar.':'Aguardando a tela de Comprovantes de Marcação carregar.'});" +
            "})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(7, 24, 39));
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 32));

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1042);
        }

        root = new FrameLayout(this);
        dashboardWebView = new WebView(this);
        configureDashboardWebView();

        root.addView(
                dashboardWebView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        createSeniorContainer();
        root.addView(
                seniorContainer,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        seniorContainer.setVisibility(View.GONE);

        setContentView(root);
        dashboardWebView.loadUrl(DASHBOARD_URL);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureDashboardWebView() {
        WebSettings settings = dashboardWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        dashboardWebView.setWebChromeClient(new WebChromeClient());
        dashboardWebView.addJavascriptInterface(new SeniorBridge(), "AndroidSenior");

        dashboardWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("intent:")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }

                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("ponto-do-almoco-luiz.vercel.app".equalsIgnoreCase(host)) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createSeniorContainer() {
        seniorContainer = new LinearLayout(this);
        seniorContainer.setOrientation(LinearLayout.VERTICAL);
        seniorContainer.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        toolbar.setBackgroundColor(Color.rgb(7, 24, 39));

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(22);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> showDashboard());

        TextView title = new TextView(this);
        title.setText("Hora do Almoço + Senior");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(8), 0);

        Button read = new Button(this);
        read.setText("LER");
        read.setTextColor(Color.WHITE);
        read.setBackgroundColor(Color.TRANSPARENT);
        read.setOnClickListener(v -> expandAndRead(true));

        toolbar.addView(back, new LinearLayout.LayoutParams(dp(54), dp(56)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        toolbar.addView(read, new LinearLayout.LayoutParams(dp(72), dp(56)));

        seniorContainer.addView(
                toolbar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        seniorWebView = new WebView(this);
        WebSettings settings = seniorWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptThirdPartyCookies(seniorWebView, true);

        seniorWebView.setWebChromeClient(new WebChromeClient());
        seniorWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("intent:")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();

                if (isReceiptsUrl(url)) {
                    sendStatus("ready", "Comprovantes abertos. Lendo suas marcações...");
                    handler.postDelayed(() -> expandAndRead(seniorVisible), 2400);
                } else if (isSeniorUrl(url)) {
                    handler.postDelayed(() -> {
                        String current = seniorWebView.getUrl();
                        if (current != null && !isReceiptsUrl(current)) {
                            sendStatus(
                                    "login_required",
                                    "Toque no card Senior para entrar e abrir seus comprovantes."
                            );
                        }
                    }, 1600);
                }
            }
        });

        seniorContainer.addView(
                seniorWebView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );
    }

    private boolean isSeniorUrl(String url) {
        if (url == null) return false;
        try {
            String host = Uri.parse(url).getHost();
            return host != null && host.toLowerCase().endsWith("senior.com.br");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isReceiptsUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return isSeniorUrl(url) &&
                (u.contains("clocking-event-receipts") ||
                 u.contains("clockingeventreceipts") ||
                 u.contains("clocking_event_receipts") ||
                 u.contains("pontomobile%2fclockingeventreceipts"));
    }

    private void showSenior() {
        seniorVisible = true;
        dashboardWebView.setVisibility(View.GONE);
        seniorContainer.setVisibility(View.VISIBLE);

        String current = seniorWebView.getUrl();
        if (current == null || current.isBlank()) {
            seniorWebView.loadUrl(RECEIPTS_URL);
        } else if (!isSeniorUrl(current)) {
            seniorWebView.loadUrl(RECEIPTS_URL);
        }
    }

    private void showDashboard() {
        seniorVisible = false;
        seniorContainer.setVisibility(View.GONE);
        dashboardWebView.setVisibility(View.VISIBLE);
    }

    private void syncSeniorNow() {
        if (seniorVisible) return;

        String current = seniorWebView.getUrl();
        if (isReceiptsUrl(current)) {
            expandAndRead(false);
            return;
        }

        sendStatus("loading", "Abrindo seus comprovantes na Senior...");
        seniorWebView.loadUrl(RECEIPTS_URL);
    }

    private void expandAndRead(boolean returnToDashboardOnSuccess) {
        if (!isReceiptsUrl(seniorWebView.getUrl())) {
            if (seniorVisible) {
                seniorWebView.loadUrl(RECEIPTS_URL);
            } else {
                sendStatus("login_required", "Toque no card Senior para entrar na sua conta.");
            }
            return;
        }

        seniorWebView.evaluateJavascript(EXPAND_RECEIPTS_JS, ignored ->
                handler.postDelayed(
                        () -> readReceipts(returnToDashboardOnSuccess, 0),
                        900
                )
        );
    }

    private void readReceipts(boolean returnToDashboardOnSuccess, int attempt) {
        seniorWebView.evaluateJavascript(EXTRACT_RECEIPTS_JS, value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                String raw = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
                JSONObject result = new JSONObject(raw);

                boolean ok = result.optBoolean("ok", false);
                JSONArray times = result.optJSONArray("times");

                if (ok && times != null && times.length() > 0) {
                    deliverClockings(result);

                    if (returnToDashboardOnSuccess) {
                        handler.postDelayed(this::showDashboard, 250);
                    }
                    return;
                }

                if (attempt < 6 && isReceiptsUrl(seniorWebView.getUrl())) {
                    handler.postDelayed(
                            () -> readReceipts(returnToDashboardOnSuccess, attempt + 1),
                            1800L + (attempt * 650L)
                    );
                    return;
                }

                deliverClockings(result);
            } catch (Exception e) {
                JSONObject status = new JSONObject();
                try {
                    status.put("ok", false);
                    status.put("loggedIn", true);
                    status.put("message", "A Senior abriu, mas a leitura dos comprovantes ainda não terminou.");
                } catch (Exception ignored) {}
                deliverClockings(status);
            }
        });
    }

    private void deliverClockings(JSONObject result) {
        String quoted = JSONObject.quote(result.toString());
        dashboardWebView.evaluateJavascript(
                "window.receiveSeniorClockings && window.receiveSeniorClockings(" + quoted + ");",
                null
        );
    }

    private void sendStatus(String state, String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("state", state);
            obj.put("message", message);

            String quoted = JSONObject.quote(obj.toString());
            dashboardWebView.evaluateJavascript(
                    "window.receiveSeniorStatus && window.receiveSeniorStatus(" + quoted + ");",
                    null
            );
        } catch (Exception ignored) {}
    }

    private void clearSeniorSession() {
        seniorWebView.evaluateJavascript(
                "(function(){try{localStorage.clear();sessionStorage.clear();}catch(e){}})()",
                null
        );

        CookieManager.getInstance().removeAllCookies(value -> {
            CookieManager.getInstance().flush();
            seniorWebView.clearCache(true);
            seniorWebView.clearHistory();
            seniorWebView.loadUrl("about:blank");
            sendStatus("login_required", "Sessão removida. Toque no card Senior para entrar novamente.");
        });
    }

    private void scheduleNativeReturnAlarms(String returnTime) {
        try {
            String[] parts = returnTime.split(":");
            if (parts.length < 2) return;

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            Calendar now = Calendar.getInstance();
            Calendar exact = Calendar.getInstance();
            exact.set(Calendar.HOUR_OF_DAY, hour);
            exact.set(Calendar.MINUTE, minute);
            exact.set(Calendar.SECOND, 0);
            exact.set(Calendar.MILLISECOND, 0);
            if (!exact.after(now)) {
                exact.add(Calendar.DAY_OF_YEAR, 1);
            }

            cancelNativeReturnAlarms();

            long exactAt = exact.getTimeInMillis();
            long beforeAt = exactAt - (3L * 60L * 1000L);
            if (beforeAt > System.currentTimeMillis()) {
                scheduleOneReturnAlarm(
                        beforeAt,
                        7301,
                        "Faltam 3 minutos",
                        "Faltam 3 minutos para bater o ponto de retorno do almoço."
                );
            }

            scheduleOneReturnAlarm(
                    exactAt,
                    7302,
                    "Hora de retornar",
                    "Hora de bater o ponto de retorno do almoço."
            );
        } catch (Exception ignored) {}
    }

    private void scheduleOneReturnAlarm(long triggerAtMillis, int requestCode, String title, String message) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, ReturnAlarmReceiver.class);
        intent.putExtra("notification_id", requestCode);
        intent.putExtra("title", title);
        intent.putExtra("message", message);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            }
        } catch (SecurityException exactDenied) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            }
        }
    }

    private void cancelNativeReturnAlarms() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) return;

        for (int requestCode : new int[]{7301, 7302}) {
            Intent intent = new Intent(this, ReturnAlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public class SeniorBridge {
        @JavascriptInterface
        public void openReceipts() {
            runOnUiThread(() -> {
                showSenior();
                String current = seniorWebView.getUrl();
                if (current == null || current.equals("about:blank")) {
                    seniorWebView.loadUrl(RECEIPTS_URL);
                }
            });
        }

        @JavascriptInterface
        public void syncNow() {
            runOnUiThread(MainActivity.this::syncSeniorNow);
        }

        @JavascriptInterface
        public void scheduleReturnAlarms(String returnTime) {
            runOnUiThread(() -> scheduleNativeReturnAlarms(returnTime));
        }

        @JavascriptInterface
        public void cancelReturnAlarms() {
            runOnUiThread(MainActivity.this::cancelNativeReturnAlarms);
        }

        @JavascriptInterface
        public void clearSession() {
            runOnUiThread(MainActivity.this::clearSeniorSession);
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (seniorVisible) {
            if (seniorWebView.canGoBack()) {
                seniorWebView.goBack();
            } else {
                showDashboard();
            }
            return;
        }

        if (dashboardWebView.canGoBack()) {
            dashboardWebView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (dashboardWebView != null) dashboardWebView.destroy();
        if (seniorWebView != null) seniorWebView.destroy();
        super.onDestroy();
    }
}
