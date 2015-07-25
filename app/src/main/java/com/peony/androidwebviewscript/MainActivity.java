package com.peony.androidwebviewscript;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "Java";

    private final static String TEST_URL = "http://apis.baidu.com/apistore/weatherservice/weather";

    private WebView mWebView;
    private WebViewJavascriptBridge bridge;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mWebView = (WebView) this.findViewById(R.id.webView);
        bridge = new WebViewJavascriptBridge(this, mWebView, new PeonyWVJBHandler());
        loadUserClient();
        registerHandler();
    }

    private void loadUserClient() {
        InputStream is = getResources().openRawResource(R.raw.user_client);
        String userClientHtml = WebViewJavascriptBridge.convertStreamToString(is);
        mWebView.loadData(userClientHtml, "text/html", "UTF-8");
    }

    class PeonyWVJBHandler implements WebViewJavascriptBridge.WVJBHandler {
        @Override
        public void handle(String data, WebViewJavascriptBridge.WVJBResponseCallback jsCallback) {
            Log.d(TAG, "Received message from javascript: " + data);
            if (null != jsCallback) {
                jsCallback.callback("Java said:Right back atcha");
            }
            /*
            bridge.send("I expect a response!", new WebViewJavascriptBridge.WVJBResponseCallback() {
                @Override
                public void callback(String responseData) {
                    Log.d(TAG, "Got response! " + responseData);
                }
            });
            bridge.send("Hi");
            */
        }
    }

    private void registerHandler() {
        bridge.registerHandler(WebViewJavascriptBridge.WVJB_LOAD_DATA, new WebViewJavascriptBridge.WVJBHandler() {
            @Override
            public void handle(String data, WebViewJavascriptBridge.WVJBResponseCallback jsCallback) {
                Log.d(TAG, WebViewJavascriptBridge.WVJB_LOAD_DATA + " got: " + data);
                /*
                if (null != jsCallback) {
                    jsCallback.callback(WebViewJavascriptBridge.WVJB_LOAD_DATA + ": " + data);
                }
                */
                doGetRequest(TEST_URL, jsCallback);
                //bridge.callHandler("showAlert", "42");
            }
        });
    }

    private void doGetRequest(String url, final WebViewJavascriptBridge.WVJBResponseCallback jsCallback) {
        if (jsCallback == null) return;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<String> callFuture = executorService.submit(new HttpRequestThread(url));
        try {
            final String response = callFuture.get();
            System.out.println("Callable:" + response);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    jsCallback.callback(WebViewJavascriptBridge.WVJB_LOAD_DATA + ": " + response);
                }
            });

        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    class HttpRequestThread implements Callable<String> {
        private String mUrl;

        public HttpRequestThread(String url) {
            mUrl = url;
        }

        @Override
        public String call() {
            return sendGet(mUrl);
        }
    }

    // HTTP GET request
    private String sendGet(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            connection.setRequestMethod("GET");

            //add request header
            connection.setRequestProperty("User-Agent", "WebViewJavaScript");

            connection.setRequestProperty("apikey",  "fbf3e7de8cd8d0a63a807949301f9f10");
            connection.connect();
            InputStream is = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String strRead = null;
            StringBuffer buffer = new StringBuffer();
            while ((strRead = reader.readLine()) != null) {
                buffer.append(strRead);
                buffer.append("\r\n");
            }
            reader.close();
            return buffer.toString();
        } catch (Exception ex) {
            return "[]";
        }
    }
}
