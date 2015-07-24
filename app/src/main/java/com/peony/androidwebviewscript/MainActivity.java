package com.peony.androidwebviewscript;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import java.io.InputStream;

public class MainActivity extends ActionBarActivity {

    private WebView webView;
    private WebViewJavascriptBridge bridge;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        webView = (WebView) this.findViewById(R.id.webView);
        bridge = new WebViewJavascriptBridge(this, webView, new UserServerHandler());
        loadUserClient();
        registerHandle();
    }

    private void loadUserClient() {
        InputStream is = getResources().openRawResource(R.raw.user_client);
        String user_client_html = WebViewJavascriptBridge.convertStreamToString(is);
        webView.loadData(user_client_html, "text/html", "UTF-8");
    }

    class UserServerHandler implements WebViewJavascriptBridge.WVJBHandler {
        @Override
        public void handle(String data, WebViewJavascriptBridge.WVJBResponseCallback jsCallback) {
            Log.d("test", "Received message from javascript: " + data);
            if (null != jsCallback) {
                jsCallback.callback("Java said:Right back atcha");
            }
            bridge.send("I expect a response!", new WebViewJavascriptBridge.WVJBResponseCallback() {
                @Override
                public void callback(String responseData) {
                    Log.d("test", "Got response! " + responseData);
                }
            });
            //bridge.send("Hi");
        }
    }

    private void registerHandle() {
        bridge.registerHandler("handler1", new WebViewJavascriptBridge.WVJBHandler() {
            @Override
            public void handle(String data, WebViewJavascriptBridge.WVJBResponseCallback jsCallback) {
                Log.d("test", "handler1 got:" + data);
                if (null != jsCallback) {
                    jsCallback.callback("handler1 answer");
                }
                bridge.callHandler("showAlert", "42");
            }
        });
    }
}
