package com.example.ussoi_camfeed;

import static com.example.ussoi_camfeed.SaveInputFields.KEY_IP;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;import okio.ByteString;


public class WebSocketHandler {
    private Context context;
    private static final String TAG = "ConnHandel";
    private OkHttpClient client;
    private WebSocket webSocket;
    private SaveInputFields saveInputFields;
    private SharedPreferences prefs;
    private final Gson gson;
    private MessageCallback callback;
    public interface MessageCallback {
        void onOpen();
        void onPayloadReceived(String payload);
        void onClosed();
        void onError(String error);
    }
    public WebSocketHandler(MessageCallback callback) {
        gson = new Gson();
        client = new OkHttpClient();
        this.callback = callback;

    }
    public void setupConnection(Context context,String urlPath){
        this.context=context;
        saveInputFields = SaveInputFields.getInstance( this.context);
        prefs = saveInputFields.get_shared_pref();
        String wsUrl = normalizeUrl(prefs.getString(KEY_IP,"10.0.0.1")) + urlPath.trim();;
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG,"WebSocket connected!");
                callback.onOpen();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG,"WebSocket Text Message Received!");
                callback.onPayloadReceived(text);
            }
            /**
             * This is the method you need for binary data.
             * It will be called automatically when the server sends a binary message.
             */
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG,"WebSocket Binary Message Received!");
                // To use the data, convert the ByteString to a standard byte array.
                byte[] byteArray = bytes.toByteArray();
                Log.d(TAG, "Received " + byteArray.length + " bytes.");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.d(TAG,"WebSocket Failure!"+ t.getMessage());
                callback.onError(t.getMessage());

                new Handler(Looper.getMainLooper()).postDelayed(() -> setupConnection(context,urlPath), 3000);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG,"WebSocket closed: " + reason);
                callback.onClosed();
            }
        });
    }
    public static String normalizeUrl(String inputUrl) {
        if (inputUrl == null || inputUrl.isEmpty()) return "ws://10.0.0.1";

        inputUrl = inputUrl.trim();

        // Add trailing slash if missing
        if (!inputUrl.endsWith("/")) {
            inputUrl += "/";
        }

        // Convert http/https to ws/wss
        if (inputUrl.startsWith("https://")) {
            inputUrl = "wss://" + inputUrl.substring(8); // remove "https://", prepend wss://
        } else if (inputUrl.startsWith("http://")) {
            inputUrl = "ws://" + inputUrl.substring(7); // remove "http://", prepend ws://
        } else if (inputUrl.startsWith("ws://") || inputUrl.startsWith("wss://")) {
            // already a websocket URL, do nothing
        } else {
            // If no schema provided, assume secure websocket
            inputUrl = "wss://" + inputUrl ;
        }
        return inputUrl;
    }
    public void connSendPayload(Object payload) {
            try {
                // convert obj to json
                String jsonString = gson.toJson(payload);
                webSocket.send(jsonString);
            }
            catch (Exception e) {
                Log.d(TAG, "Failed to send JSON payload: " + e.getMessage());
            }
    }
    public void connSendPayloadBytes(byte[] serialBytesReceived) {
            try {
                webSocket.send(okio.ByteString.of(serialBytesReceived));
            }
            catch (Exception e) {
                Log.d(TAG, "Failed to send BYTES: " + e.getMessage());
            }
    }
    public void closeConnection() {
        if (webSocket != null) {
            webSocket.close(1000, "Closing manually");
        }
    }
}
