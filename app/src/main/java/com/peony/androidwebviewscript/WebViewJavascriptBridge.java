package com.peony.androidwebviewscript;

import android.app.Activity;
import android.util.Log;
import android.webkit.*;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * implements Serializable in case of javascript interface will be removed in obfuscated code.
 */
public class WebViewJavascriptBridge implements Serializable {
    private final static String TAG = WebViewJavascriptBridge.class.getSimpleName();

    private WebView mWebView;
    private Activity mContext;
    private WVJBHandler mMessageHandler;
    private Map<String, WVJBHandler> mMessageHandlers;
    private Map<String, WVJBResponseCallback> mResponseCallbacks;
    long mUniqueId;

    public WebViewJavascriptBridge(Activity context, WebView webview, WVJBHandler handler) {
        mContext = context;
        mWebView = webview;
        mMessageHandler = handler;
        mMessageHandlers = new HashMap<>();
        mResponseCallbacks = new HashMap<>();
        mUniqueId = 0;
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mWebView.addJavascriptInterface(this, "_WebViewJavascriptBridge");
        mWebView.setWebViewClient(new PeonyWebViewClient());
        //optional, for show console and alert
        mWebView.setWebChromeClient(new PeonyWebChromeClient());
    }

    private void loadWebViewJavascriptBridgeJs(WebView webView) {
        InputStream is = mContext.getResources().openRawResource(R.raw.webviewjavascriptbridge);
        String script = convertStreamToString(is);
        webView.loadUrl("javascript:" + script);
    }

    public static String convertStreamToString(InputStream is) {
        String s = "";
        try {
            Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
            if (scanner.hasNext()) s = scanner.next();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    private class PeonyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView webView, String url) {
            Log.d(TAG, "onPageFinished");
            loadWebViewJavascriptBridgeJs(webView);
        }
    }

    private class PeonyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(TAG, cm.message() + " line:" + cm.lineNumber());
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            // if don't cancel the alert, webview after onJsAlert not responding taps
            // you can check this :
            // http://stackoverflow.com/questions/15892644/android-webview-after-onjsalert-not-responding-taps
            result.cancel();
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    public interface WVJBHandler {
        public void handle(String data, WVJBResponseCallback jsCallback);
    }

    public interface WVJBResponseCallback {
        public void callback(String data);
    }

    public void registerHandler(String handlerName, WVJBHandler handler) {
        mMessageHandlers.put(handlerName, handler);
    }

    private class CallbackJs implements WVJBResponseCallback {
        private final String callbackIdJs;

        public CallbackJs(String callbackIdJs) {
            this.callbackIdJs = callbackIdJs;
        }

        @Override
        public void callback(String data) {
            _callbackJs(callbackIdJs, data);
        }
    }

    private void _callbackJs(String callbackIdJs, String data) {
        //TODO: CALL js to call back;
        Map<String, String> message = new HashMap<String, String>();
        message.put("responseId", callbackIdJs);
        message.put("responseData", data);
        _dispatchMessage(message);
    }

    @JavascriptInterface
    public void _handleMessageFromJs(final String data, String responseId,
                                     String responseData, String callbackId, String handlerName) {
        if (null != responseId) {
            WVJBResponseCallback responseCallback = mResponseCallbacks.get(responseId);
            responseCallback.callback(responseData);
            mResponseCallbacks.remove(responseId);
        } else {
            WVJBResponseCallback responseCallback = null;
            if (null != callbackId) {
                responseCallback = new CallbackJs(callbackId);
            }
            final WVJBHandler handler;
            if (null != handlerName) {
                handler = mMessageHandlers.get(handlerName);
                if (null == handler) {
                    Log.e(TAG, "WVJB Warning: No handler for " + handlerName);
                    return;
                }
            } else {
                handler = mMessageHandler;
            }
            try {
                final WVJBResponseCallback callback = responseCallback;
                mContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handler.handle(data, callback);
                    }
                });
            } catch (Exception exception) {
                Log.e(TAG, "WebViewJavascriptBridge: WARNING: java handler threw. " + exception.getMessage());
            }
        }
    }

    public void send(String data) {
        send(data, null);
    }

    public void send(String data, WVJBResponseCallback responseCallback) {
        _sendData(data, responseCallback, null);
    }

    private void _sendData(String data, WVJBResponseCallback responseCallback, String handlerName) {
        Map<String, String> message = new HashMap<String, String>();
        message.put("data", data);
        if (null != responseCallback) {
            String callbackId = "java_cb_" + (++mUniqueId);
            mResponseCallbacks.put(callbackId, responseCallback);
            message.put("callbackId", callbackId);
        }
        if (null != handlerName) {
            message.put("handlerName", handlerName);
        }
        _dispatchMessage(message);
    }

    private void _dispatchMessage(Map<String, String> message) {
        String messageJSON = new JSONObject(message).toString();
        final String javascriptCommand =
                String.format("javascript:WebViewJavascriptBridge._handleMessageFromJava('%s');", doubleEscapeString(messageJSON));
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.loadUrl(javascriptCommand);
            }
        });
    }


    public void callHandler(String handlerName) {
        callHandler(handlerName, null, null);
    }

    public void callHandler(String handlerName, String data) {
        callHandler(handlerName, data, null);
    }

    public void callHandler(String handlerName, String data, WVJBResponseCallback responseCallback) {
        _sendData(data, responseCallback, handlerName);
    }

    /*
      * you must escape the char \ and  char ", or you will not recevie a correct json object in 
      * your javascript which will cause a exception in chrome.
      *
      * please check this and you will know why.
      * http://stackoverflow.com/questions/5569794/escape-nsstring-for-javascript-input
      * http://www.json.org/
    */
    private String doubleEscapeString(String javascript) {
        String result;
        result = javascript.replace("\\", "\\\\");
        result = result.replace("\"", "\\\"");
        result = result.replace("\'", "\\\'");
        result = result.replace("\n", "\\n");
        result = result.replace("\r", "\\r");
        result = result.replace("\f", "\\f");
        return result;
    }
}
