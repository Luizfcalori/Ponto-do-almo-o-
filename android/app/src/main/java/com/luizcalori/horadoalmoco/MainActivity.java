package com.luizcalori.horadoalmoco;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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

public class MainActivity extends Activity {

    private static final String DASHBOARD_URL =
            "https://ponto-do-almoco-luiz.vercel.app/?native=1";

    private static final String RECEIPTS_URL =
            "https://platform.senior.com.br/hcm-pontomobile/hcm/pontomobile/#/clocking-event-receipts";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView dashboardWebView;
    private LinearLayout seniorContainer;
    private WebView seniorWebView;
    private boolean seniorVisible = false;

    private final String EXPAND_RECEIPTS_JS =
            "(function(){" +
            "function n(s){return (s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').trim().toUpperCase();}" +
            "var all=[].slice.call(document.querySelectorAll('button,a,[role=button],h1,h2,h3,h4,div,span'));" +
            "var el=all.find(function(x){var t=n(x.innerText);return t.indexOf('COMPROVANTES DE MARCACOES')>=0 && t.length<120;});" +
            "if(!el)return 'not-found';" +
            "try{el.scrollIntoView({block:'center',behavior:'instant'});}catch(e){}" +
            "var p=el;" +
            "for(var i=0;i<5 && p;i++,p=p.parentElement){" +
            " var tag=(p.tagName||'').toLowerCase();" +
            " var role=p.getAttribute&&p.getAttribute('role');" +
            " if(tag==='button'||tag==='a'||role==='button'||typeof p.onclick==='function'){try{p.click();return 'clicked';}catch(e){}}" +
            "}" +
            "try{el.click();return 'clicked-heading';}catch(e){return 'found';}" +
            "})()";

    private final String EXTRACT_RECEIPTS_JS =
            "(function(){" +
            "function norm(s){return (s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}" +
            "function timeMatches(s){" +
            " var out=[],re=/(^|\\D)([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?=\\D|$)/g,m;" +
            " while((m=re.exec(s||''))!==null){var v=String(m[2]).padStart(2,'0')+':'+m[3];if(out.indexOf(v)<0)out.push(v);}" +
            " return out;" +
            "}" +
            "var body=document.body;" +
            "if(!body)return JSON.stringify({ok:false,loggedIn:true,message:'Página da Senior ainda carregando.'});" +
            "var full=body.innerText||'';" +
            "var normalized=norm(full).toUpperCase();" +
            "var marker='COMPROVANTES DE MARCACOES';" +
            "var idx=normalized.indexOf(marker);" +
            "if(idx<0){" +
            " var u=location.href||'';" +
            " var first=normalized.slice(0,2200);" +
            " var login=/LOGIN|ENTRAR|USUARIO|SENHA|PASSWORD|AUTENTICACAO/.test(first)||/login|authentication|signin|sso/i.test(u);" +
            " return JSON.stringify({ok:false,loggedIn:!login,message:login?'Faça login na Senior para continuar.':'Aguardando a área de comprovantes de marcação.'});" +
            "}" +
            "var section=full.slice(idx);" +
            "var today=new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Sao_Paulo',day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date());" +
            "var todayShort=today.slice(0,6)+today.slice(-2);" +
            "var dated=[];" +
            "var candidates=[].slice.call(document.querySelectorAll('body *'));" +
            "for(var i=0;i<candidates.length;i++){" +
            " var e=candidates[i],t=(e.innerText||'').trim();" +
            " if(!t||t.length>700)continue;" +
            " var nt=norm(t);" +
            " if(nt.indexOf(today)>=0||nt.indexOf(todayShort)>=0){" +
            "   var tm=timeMatches(t);for(var j=0;j<tm.length;j++)if(dated.indexOf(tm[j])<0)dated.push(tm[j]);" +
            " }" +
            "}" +
            "var times=dated.length?dated:timeMatches(section);" +
            "times=times.filter(function(v){return v!=='00:00'&&v!=='23:59';});" +
            "if(times.length>8)times=times.slice(0,8);" +
            "return JSON.stringify({ok:times.length>0,loggedIn:true,day:today,times:times,message:times.length?'Marcações lidas dos comprovantes.':'Comprovantes encontrados, mas os horários ainda não apareceram.'});" +
            "})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(7, 24, 39));
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 32));

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

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
                    handler.postDelayed(() -> expandAndRead(seniorVisible), 1200);
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
                 u.contains("clocking_event_receipts"));
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

                if (attempt < 3 && isReceiptsUrl(seniorWebView.getUrl())) {
                    handler.postDelayed(
                            () -> readReceipts(returnToDashboardOnSuccess, attempt + 1),
                            1400L + (attempt * 500L)
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
