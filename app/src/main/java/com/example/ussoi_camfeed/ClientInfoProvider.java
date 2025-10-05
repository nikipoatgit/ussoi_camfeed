package com.example.ussoi_camfeed;

import android.content.Intent;
import android.content.IntentFilter;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientInfoProvider {
    private final String TAG = "ClientInfoProvider";
    private static ClientInfoProvider instance;
    private final Context context;
    // System Service Managers
    private final BatteryManager batteryManager;
    private final LocationManager locationManager;
    private final TelephonyManager telephonyManager;
    private final ConnectivityManager connectivityManager;
    private final PowerManager powerManager;
    private final int myUid;

    // For Network Speed Calculation
    private volatile long lastTxBytes = 0;
    private volatile long lastRxBytes = 0;
    private volatile long lastTimestamp = 0;
    private volatile double uploadSpeedBps = 0.0; // Bytes per second
    private volatile double downloadSpeedBps = 0.0; //Bytes per second
    private volatile JSONObject basicInfo = new JSONObject ();
    private String provider = null;

    // Listeners and Schedulers
    private volatile Location lastKnownLocation;
    private volatile SignalStrength lastSignalStrength;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MySignalStrengthListener modernListener;
    private PhoneStateListener legacyListener;
    private ClientInfoProvider(Context context){
        this.context = context.getApplicationContext();

        // Initialize Managers
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // Get the app's UID for data consumption tracking
        this.myUid = android.os.Process.myUid();
    };
    public static synchronized ClientInfoProvider getInstance(Context context){
        if (instance == null){
            instance = new ClientInfoProvider(context);
        }
        return instance;
    }
    public void startMonitoring(){
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        startLocationUpdates();
        startSignalStrengthListener();
        startNetworkSpeedSampler();
    }
    public void stopMonitoring() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (telephonyManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernListener != null) {
                telephonyManager.unregisterTelephonyCallback(modernListener);
            } else if (legacyListener != null) {
                telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_NONE);
            }
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
    private void startLocationUpdates(){
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted.");
            return;
        }
        if (isGpsEnabled) {
            provider = LocationManager.GPS_PROVIDER; // more accurate
        } else if (isNetworkEnabled) {
            provider = LocationManager.NETWORK_PROVIDER; // faster indoors
        }
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 10000, 50, locationListener);
        }
        else {
            Log.e(TAG, "No provider enabled (GPS or Network).");
        }
    }
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            lastKnownLocation = location;
        }
        @Override
        public void onProviderDisabled(@NonNull String provider) {}
        @Override
        public void onProviderEnabled(@NonNull String provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    // Add this annotation
    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MySignalStrengthListener extends TelephonyCallback implements TelephonyCallback.SignalStrengthsListener {
        @Override
        public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
            lastSignalStrength = signalStrength;
        }
    }
    private void startSignalStrengthListener() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Phone state permission not granted.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Modern method for API 31+
            modernListener = new MySignalStrengthListener();
            telephonyManager.registerTelephonyCallback(context.getMainExecutor(), modernListener);
        } else {
            // Legacy method for older APIs
            legacyListener = new PhoneStateListener() {
                @Override
                @SuppressWarnings("deprecation") // Suppress warning for onSignalStrengthsChanged
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    super.onSignalStrengthsChanged(signalStrength);
                    lastSignalStrength = signalStrength;
                }
            };
            telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
    }

    private void startNetworkSpeedSampler() {
        lastTxBytes = TrafficStats.getUidTxBytes(myUid);
        lastRxBytes = TrafficStats.getUidRxBytes(myUid);
        lastTimestamp = System.currentTimeMillis();

        scheduler.scheduleWithFixedDelay(() -> {
            long currentTxBytes = TrafficStats.getUidTxBytes(myUid);
            long currentRxBytes = TrafficStats.getUidRxBytes(myUid);
            long currentTimestamp = System.currentTimeMillis();

            long timeDiffMillis = currentTimestamp - lastTimestamp;
            if (timeDiffMillis > 0) {
                uploadSpeedBps = (currentTxBytes - lastTxBytes) * 1000.0 / timeDiffMillis;
                downloadSpeedBps = (currentRxBytes - lastRxBytes) * 1000.0 / timeDiffMillis;
            }

            lastTxBytes = currentTxBytes;
            lastRxBytes = currentRxBytes;
            lastTimestamp = currentTimestamp;

        }, 1, 2, TimeUnit.SECONDS);

    }
    public JSONObject clientInfoConstructor() throws JSONException {
        try {
            // bat info
            basicInfo.put("bat%", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            basicInfo.put("batAmp", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            basicInfo.put("batTemp", getBatteryTemperatureLegacy());
            basicInfo.put("thermal", getThermalStatus());

            // net info
            basicInfo.put("signalDbm", getCellularSignalStrengthDbm());
            basicInfo.put("upMBps", (uploadSpeedBps ) / (1024.0 * 1024.0));
            basicInfo.put("dwnMBps", (downloadSpeedBps ) / (1024.0 * 1024.0));
            basicInfo.put("totalMb", getAppDataConsumptionMb());

            // location info
            if (lastKnownLocation != null) {
                basicInfo.put("latitude", lastKnownLocation.getLatitude());
                basicInfo.put("longitude", lastKnownLocation.getLongitude());
                basicInfo.put("accuracy", lastKnownLocation.hasAccuracy() ? lastKnownLocation.getAccuracy() : JSONObject.NULL);
                basicInfo.put("altitude", lastKnownLocation.hasAltitude() ? lastKnownLocation.getAltitude() : JSONObject.NULL);
                basicInfo.put("speed", lastKnownLocation.hasSpeed() ? lastKnownLocation.getSpeed() : JSONObject.NULL);
            }

            // net info
            basicInfo.put("netTyp",telephonyManager.getNetworkType());
            basicInfo.put("sim",telephonyManager.getNetworkOperatorName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                basicInfo.put("dataTyp",telephonyManager.getDataNetworkType());
            }

            return basicInfo;

        } catch (JSONException e) {
            Log.e(TAG, "Error constructing JSON", e);
            try {
                return new JSONObject().put("error", "Failed to construct client info");
            } catch (JSONException jsonException) {
                return basicInfo;
            }
        }
    }
    public int getCellularSignalStrengthDbm() {
        if (lastSignalStrength == null) return -120; // Return a realistic "no signal" value instead of -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!lastSignalStrength.getCellSignalStrengths().isEmpty()) {
                return lastSignalStrength.getCellSignalStrengths().get(0).getDbm();
            }
        } else {
            int gsmSignal = lastSignalStrength.getGsmSignalStrength();
            if (gsmSignal != 99) { // 99 means 'unknown' or 'not detectable'
                return -113 + 2 * gsmSignal;
            }
        }
        return -120; // Default fallback
    }
    public double getAppDataConsumptionMb() {
        long totalBytes = TrafficStats.getUidTxBytes(myUid) + TrafficStats.getUidRxBytes(myUid);
        if (totalBytes == TrafficStats.UNSUPPORTED) {
            return -1.0;
        }
        return totalBytes / (1024.0 * 1024.0);
    }
    public String getThermalStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int status = powerManager.getCurrentThermalStatus();
            switch (status) {
                case PowerManager.THERMAL_STATUS_NONE: return "None";
                case PowerManager.THERMAL_STATUS_LIGHT: return "Light";
                case PowerManager.THERMAL_STATUS_MODERATE: return "Moderate";
                case PowerManager.THERMAL_STATUS_SEVERE: return "Severe";
                case PowerManager.THERMAL_STATUS_CRITICAL: return "Critical";
                default: return "?";
            }
        }
        return "-1";
    }
    public float getBatteryTemperatureLegacy() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (temp != -1) {
                return temp / 10.0f;
            }
        }
        return -1f;
    }


}
