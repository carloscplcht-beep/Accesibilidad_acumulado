package com.carlospl.accesibilidadmediaap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONTokener;
import org.json.JSONException;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1024;
    private static final String DASHBOARD_HOST = "carloscplcht-beep.github.io";
    private static final String DASHBOARD_PATH_PREFIX = "/Accesibilidad_acumulado";
    private static final String PRINT_REQUEST = "accesibilidad-ap://print/report";
    private static final String PRINT_JOB_NAME = "Informe evolutivo de accesibilidad";

    private WebView webView;
    private WebView reportView;
    private boolean preparingReport;
    private ProgressBar progressBar;
    private LinearLayout errorView;
    private ValueCallback<Uri[]> filePathCallback;
    private String dashboardUrl;

    private final String[] spreadsheetMimeTypes = new String[] {
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroEnabled.12",
            "text/csv",
            "application/csv",
            "application/octet-stream"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dashboardUrl = getString(R.string.dashboard_url);
        buildLayout();
        configureWebView();
        webView.loadUrl(dashboardUrl);
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        errorView = buildErrorView();

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(progressBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
                Gravity.TOP
        ));
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar.setMax(100);
        progressBar.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        setContentView(root);
    }

    private LinearLayout buildErrorView() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(28), dp(28), dp(28), dp(28));
        container.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText(R.string.load_error);
        title.setTextColor(getColor(R.color.ink));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);

        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setTextColor(Color.WHITE);
        retry.setBackgroundColor(getColor(R.color.sescam_green));
        retry.setOnClickListener(view -> {
            showError(false);
            webView.loadUrl(dashboardUrl);
        });

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(18);

        container.addView(title, textParams);
        container.addView(retry, buttonParams);
        return container;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " AccesibilidadMediaAP/AndroidPrint");
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        WebView.setWebContentsDebuggingEnabled(false);

        CookieManager.getInstance().setAcceptCookie(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new DashboardWebViewClient());
        webView.setWebChromeClient(new DashboardChromeClient());
    }

    private boolean isDashboardUrl(Uri uri) {
        if (uri == null) return false;
        String path = uri.getPath();
        return "https".equalsIgnoreCase(uri.getScheme())
                && DASHBOARD_HOST.equalsIgnoreCase(uri.getHost())
                && (DASHBOARD_PATH_PREFIX.equals(path) || (path != null && path.startsWith(DASHBOARD_PATH_PREFIX + "/")));
    }

    private void requestEvolutionReport(WebView source) {
        if (preparingReport || reportView != null) return;
        preparingReport = true;
        source.evaluateJavascript(
                "typeof buildAndroidPrintDocument === 'function' ? buildAndroidPrintDocument() : null",
                result -> {
                    preparingReport = false;
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        Object document = new JSONTokener(result == null ? "null" : result).nextValue();
                        if (document instanceof String && !((String) document).isEmpty()) {
                            printEvolutionReport((String) document);
                        } else {
                            showPrintError();
                        }
                    } catch (JSONException error) {
                        showPrintError();
                    }
                });
    }

    private void printEvolutionReport(String document) {
        PrintManager printManager = getSystemService(PrintManager.class);
        if (printManager == null) {
            showPrintError();
            return;
        }

        WebView printingView = new WebView(this);
        reportView = printingView;
        WebSettings settings = printingView.getSettings();
        settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        printingView.setWebViewClient(new WebViewClient() {
            private boolean submitted;

            @Override
            public void onPageFinished(WebView view, String url) {
                if (submitted || reportView != view || isFinishing() || isDestroyed()) return;
                submitted = true;
                try {
                    PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(PRINT_JOB_NAME);
                    printManager.print(PRINT_JOB_NAME, new PrintDocumentAdapter() {
                        @Override
                        public void onStart() {
                            adapter.onStart();
                        }

                        @Override
                        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                             CancellationSignal signal, LayoutResultCallback callback, Bundle extras) {
                            adapter.onLayout(oldAttributes, newAttributes, signal, callback, extras);
                        }

                        @Override
                        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                            CancellationSignal signal, WriteResultCallback callback) {
                            adapter.onWrite(pages, destination, signal, callback);
                        }

                        @Override
                        public void onFinish() {
                            try {
                                adapter.onFinish();
                            } finally {
                                releaseReportView(view);
                            }
                        }
                    }, new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                } catch (RuntimeException error) {
                    releaseReportView(view);
                    showPrintError();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                releaseReportView(view);
                showPrintError();
            }
        });
        printingView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null);
    }

    private void releaseReportView(WebView view) {
        if (reportView != view) return;
        reportView = null;
        view.destroy();
    }

    private void showPrintError() {
        Toast.makeText(this, R.string.print_error, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (reportView != null) releaseReportView(reportView);
        super.onDestroy();
    }

    private void showError(boolean show) {
        errorView.setVisibility(show ? View.VISIBLE : View.GONE);
        webView.setVisibility(show ? View.GONE : View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int index = 0; index < count; index++) {
                    Uri uri = data.getClipData().getItemAt(index).getUri();
                    results[index] = uri;
                    persistReadPermission(uri, flags);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                results = new Uri[] { uri };
                persistReadPermission(uri, flags);
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private void persistReadPermission(Uri uri, int flags) {
        if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant only temporary access; that is enough for WebView file upload.
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class DashboardWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (PRINT_REQUEST.equals(uri.toString())) {
                if (request.isForMainFrame() && isDashboardUrl(Uri.parse(view.getUrl() == null ? "" : view.getUrl()))) {
                    requestEvolutionReport(view);
                }
                return true;
            }
            if (isDashboardUrl(uri)) return false;
            Toast.makeText(MainActivity.this, "Solo se permite cargar el dashboard oficial.", Toast.LENGTH_LONG).show();
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            showError(false);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(8);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showError(true);
        }
    }

    private class DashboardChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, spreadsheetMimeTypes);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            try {
                startActivityForResult(Intent.createChooser(intent, "Seleccionar archivo Excel"), FILE_CHOOSER_REQUEST);
            } catch (ActivityNotFoundException exception) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "No hay selector de documentos disponible.", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
    }
}
