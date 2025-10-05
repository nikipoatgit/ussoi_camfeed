package com.example.ussoi_camfeed;

import static com.example.ussoi_camfeed.SaveInputFields.KEY_BT_SWITCH;
import static com.example.ussoi_camfeed.SaveInputFields.KEY_MAVLINK;
import static com.example.ussoi_camfeed.SaveInputFields.KEY_VIDEO_SWITCH;
import static com.example.ussoi_camfeed.SaveInputFields.control_api;
import static com.example.ussoi_camfeed.SaveInputFields.streaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CpuUsageInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Switch;

import androidx.core.app.NotificationCompat;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceManager extends Service {
    private final String TAG = "ServiceManager";
    public static final String NOTIFICATION_CHANNEL_ID = "StreamingServiceChannel";
    public static final int NOTIFICATION_ID = 1;
    private ScheduledExecutorService scheduler;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs; // code related to handel preferences
    private SaveInputFields saveInputFields;
    public static boolean isRunning = false;
    private boolean bt_status;
    private boolean stream_status;
    private boolean mavlink_status;
    private BluetoothHandler bluetoothHandler;
    private UsbHandler usbHandler;
    private WebRtcHandler webRtcHandler;
    private WebSocketHandler webSocketHandler;
    private ClientInfoProvider clientInfoProvider;


    @Override
    public void onCreate() {
        super.onCreate();
        // Service is being created. Initialize components here if needed.
        isRunning = true;
        saveInputFields = SaveInputFields.getInstance(this);
        // --- Acquire partial WakeLock (NEW) ---
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyApp::StreamingWakeLock");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = saveInputFields.get_shared_pref();
        bt_status = prefs.getBoolean(KEY_BT_SWITCH,false);
        stream_status = prefs.getBoolean(KEY_VIDEO_SWITCH,false);
        mavlink_status = prefs.getBoolean(KEY_MAVLINK,true);
        // init ws and client info class
        clientInfoProvider = ClientInfoProvider.getInstance(this);
        clientInfoProvider.startMonitoring();
        webSocketHandler = new WebSocketHandler(new WebSocketHandler.MessageCallback() {
            @Override
            public void onOpen() {
                Log.d(TAG, "WebSocket connected");
            }
            @Override
            public void onPayloadReceivedtext(String payload) {
                try {
                    Log.d(TAG,payload);
                    JSONObject json = new JSONObject(payload);
                    String type = json.optString("sub_type");
                    switch (type) {
                        case "stream":
                            streamControl(json.optString("control"));
                            break;
                        case "mavlink":
                            mavlinkControl(json.optString("control"));
                            break;
                        case "client":
                            clientConfig();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse payload", e);
                }
            }
            @Override
            public void onPayloadReceivedbyte(byte[] payload) {}
            @Override
            public void onClosed() {
                Log.d(TAG, "WebSocket connection closed");
                }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebSocket error: " + error);
            }
        });
        webSocketHandler.setupConnection(this, control_api);
        Log.d(TAG, "Service starting...");
        //  Notification Channel (required for Android 8.0 and higher)
        createNotificationChannel();
        // Build the persistent notification for the user
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("UAV Stream Active")
                .setContentText("Broadcasting camera and telemetry.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // replace with your own icon
                .setPriority(NotificationCompat.PRIORITY_LOW) // ensures visibility
                .setOngoing(true) // cannot be swiped away
                .build();
        //  Start the service in the foreground.
        // This is the most important step. It tells Android not to kill the service.
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Foreground service is now running.");
        runServicesBasedOnPrefs();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // If the service is killed, it will not be automatically restarted.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (bluetoothHandler != null) {
            bluetoothHandler.stopAllServices();
            Log.d(TAG,"Bthandler Stopped");
        }
        if (usbHandler != null) {
            usbHandler.stopAllServices();
            Log.d(TAG,"usbHandler Stopped");
        }
//        if (webRtcHandler != null) webRtcHandler.stopAllServices();
        if (webRtcHandler != null) {
            Log.d(TAG, "Closing WebRTC stream...");
            webRtcHandler.stopAllServices();
            webRtcHandler = null;
        }
        if (clientInfoProvider != null){
            webSocketHandler.closeConnection();
            clientInfoProvider.stopMonitoring();
            Log.d(TAG, "Closing clientInfoProvider...");
        }

        if (scheduler != null) scheduler.shutdownNow();
        // --- Release WakeLock (NEW) ---
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Log.d(TAG, "Service is being destroyed.");
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so we return null
        // let an Activity communicate directly with a running background Service
        return null;
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Streaming Service Channel",
                    NotificationManager.IMPORTANCE_LOW // LOW = no sound
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
    private void runServicesBasedOnPrefs(){
        if (stream_status){
            webRtcHandler = WebRtcHandler.getInstance(this);
            webRtcHandler.init();
            int initialWidth = 1280;
            int initialHeight = 720;
            int initialFps = 30;
            webRtcHandler.startLocalStream(initialWidth, initialHeight, initialFps);
            webRtcHandler.createOffer();
        }
        if (bt_status && mavlink_status){
            bluetoothHandler = BluetoothHandler.getInstance(this);
            bluetoothHandler.setupConnection();
        }
        else if (!bt_status && mavlink_status) {
            usbHandler = UsbHandler.getInstance(this);
            usbHandler.setupConnection();
        }
    }
    private void mavlinkControl(String control){
        switch(control){
            case "restart":
                mavlink_status=true;
                if (bluetoothHandler != null) {
                    bluetoothHandler.stopAllServices();
                    Log.d(TAG,"Bthandler Stopped");
                }
                if (usbHandler != null) {
                    usbHandler.stopAllServices();
                    Log.d(TAG,"usbHandler Stopped");
                }
                stream_status=false;
                runServicesBasedOnPrefs();
                break;
            case "stop":
                mavlink_status=false;
                if (bluetoothHandler != null) {
                    bluetoothHandler.stopAllServices();
                    bluetoothHandler = null;
                    Log.d(TAG,"Bthandler Stopped");
                }
                if (usbHandler != null) {
                    usbHandler.stopAllServices();
                    bluetoothHandler = null;
                    Log.d(TAG,"usbHandler Stopped");
                }
        }
    }
    private void streamControl(String control){
        switch (control){
            case "restart":
                stream_status=true;
                if (webRtcHandler != null) {
                    Log.d("StreamingService", "Closing WebRTC stream...");
                    webRtcHandler.stopAllServices();
                }
                runServicesBasedOnPrefs();
                break;
            case "stop":
                stream_status=false;
                if (webRtcHandler != null) {
                    Log.d("StreamingService", "Closing WebRTC stream...");
                    webRtcHandler.stopAllServices();
                    webRtcHandler = null;
                }
        }
    }
    private void clientConfig() throws JSONException {
        webSocketHandler.connSendPayload(clientInfoProvider.clientInfoConstructor());
    }
}
