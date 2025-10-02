package com.example.ussoi_camfeed;


import static com.example.ussoi_camfeed.SaveInputFields.KEY_BAUD_RATE;
import static com.example.ussoi_camfeed.SaveInputFields.streaming;
import static com.example.ussoi_camfeed.SaveInputFields.telemetry;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;


import java.io.IOException;
import java.util.List;

public class UsbHandler {
    private static final String TAG = "UsbHandler";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";
    private static UsbHandler instance;
    private SharedPreferences prefs;
    private SaveInputFields saveInputFields;
    private static UsbManager usbManager;
    private WebSocketHandler webSocketHandler;
    private Thread readThread;
    private volatile boolean reading = false;
    private Context context;
    private UsbHandler(Context context){
        this.context = context.getApplicationContext();
    };
    public static synchronized UsbHandler getInstance(Context context){
        if (instance == null){
            instance = new UsbHandler(context);
        }
        return instance;
    }
    public void setupConnection(){
        saveInputFields = SaveInputFields.getInstance(context);
        prefs = saveInputFields.get_shared_pref();

        // Find all available drivers from attached devices.
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "No USB serial drivers available");
            Toast.makeText(context, "No USB serial drivers available", Toast.LENGTH_SHORT).show();
            return;
        }
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        // Check permission
        if (!usbManager.hasPermission(device)) {
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    0,
                    new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            usbManager.requestPermission(device, pi);
            Log.d(TAG, "Requested USB permission for device");
            return;
        }
        // Open a connection to the first available driver.
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device connection");
            Toast.makeText(context, "Failed to open USB device connection", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);

            // Read baud rate from prefs (default 115200)
            int baud = prefs.getInt(KEY_BAUD_RATE, 115200);
            port.setParameters(
                    baud,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
            );

            Log.d(TAG, "USB port opened at baud " + baud);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up USB port", e);
            try {
                port.close();
                return;
            } catch (IOException ignored) {
                return;
            }
        }
        webSocketHandler = new WebSocketHandler(new WebSocketHandler.MessageCallback() {
            public void onOpen() {
                Log.d(TAG, "Connected to WS");
            }

            @Override
            public void onPayloadReceivedtext(String payload) {
            }
            @Override
            public void onPayloadReceivedbyte(byte[] mavlinkBytes) {
                try {
                    synchronized (port) {
                        port.write(mavlinkBytes, 100);
                        Log.d(TAG, "TX " + mavlinkBytes.length );

                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to USB port from WebSocket", e);
                }
            }
            @Override
            public void onClosed() {
                Log.d(TAG, "WS connection closed: " );
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error : " + error);
            }
        });
        webSocketHandler.setupConnection(context,telemetry);
        startReading(port);

    }
    private void startReading(UsbSerialPort port) {
        stopReading();
        reading = true;

        readThread = new Thread(() -> {
            byte[] buffer = new byte[4096]; // allocate buffer
            final int READ_WAIT_MILLIS = 2000;

            while (reading) {
                try {
                    int len = port.read(buffer, READ_WAIT_MILLIS);
                    if (len > 0) {
                        byte[] mavlinkBytes = java.util.Arrays.copyOf(buffer, len);

                        Log.d(TAG, "RX (" + len + " bytes): " );
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Serial read error", e);
                    break; // exit on connection loss
                }
            }
            reading = false;
        }, "UsbReadLoop");
        readThread.start();
    }
    public void stopReading() {
        reading = false;
        if (readThread != null) {
            try { readThread.join(500); } catch (InterruptedException ignored) {}
            readThread = null;
        }
        Log.d(TAG,"reading stopped");
    }
    public void stopAllServices(){
        stopReading();
        //  Stop network handler ---
        if (webSocketHandler != null ) {
            webSocketHandler.closeConnection();
            webSocketHandler = null;
            Log.d(TAG,"socket closed");

        }
    }
}
