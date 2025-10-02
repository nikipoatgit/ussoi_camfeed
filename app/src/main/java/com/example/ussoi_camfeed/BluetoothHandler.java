package com.example.ussoi_camfeed;

import static com.example.ussoi_camfeed.SaveInputFields.telemetry;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.psp.bluetoothlibrary.BluetoothListener;
import com.psp.bluetoothlibrary.Connection;
import com.psp.bluetoothlibrary.SendReceive;

import org.json.JSONObject;

public class BluetoothHandler {
    public static final String ACTION_BT_FAILED = "com.example.ussoi.BT_CONNECTION_FAILED";
    private static final String TAG = "BtHandel";
    private static BluetoothHandler instance;
    private Context context;
    private BluetoothDevice device;
    private Connection connection;
    private WebSocketHandler webSocketHandler;
    private BluetoothHandler(Context context){
        this.context = context.getApplicationContext();
        this.connection = new Connection(this.context);
    };
    public static synchronized BluetoothHandler getInstance(Context context){
        if (instance == null){
            instance = new BluetoothHandler(context);
        }
        return instance;
    }
    public void setDevice(BluetoothDevice device){
        this.device = device;
    }
    public void setupConnection(){
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter == null){
            Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(context, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }
        // Android 12+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "BLUETOOTH_CONNECT permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        // Immediately connect to the supplied device
        connectToDevice(device);

        webSocketHandler = new WebSocketHandler(new WebSocketHandler.MessageCallback() {
            @Override
            public void onOpen() {
                Log.d(TAG, "Connected to WS");
            }

            @Override
            public void onPayloadReceivedtext(String payload) {
            }
            @Override
            public void onPayloadReceivedbyte(byte[] mavlinkBytes) {
                boolean success = SendReceive.getInstance().send(mavlinkBytes);
                Log.d(TAG, success ? "[TX] " + mavlinkBytes.length : "[TX] Failed: " +  mavlinkBytes.length);

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
    }
    private void connectToDevice(BluetoothDevice device){
        BluetoothListener.onConnectionListener connectionListener =
                new BluetoothListener.onConnectionListener() {
                    @Override
                    public void onConnectionStateChanged(android.bluetooth.BluetoothSocket socket, int state) {
                        switch (state) {
                            case Connection.CONNECTING:
                                Log.d(TAG, "Connecting…");
                                Toast.makeText(context, "Connecting…", Toast.LENGTH_SHORT).show();
                                break;
                            case Connection.CONNECTED:
                                Log.d(TAG, "Connected successfully");
                                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                        != PackageManager.PERMISSION_GRANTED) return;
                                Toast.makeText(context, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                                setupBluetoothListener();
                                break;
                            case Connection.DISCONNECTED:
                                Log.d(TAG, "Disconnected");
                                Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show();
                                connection.disconnect();
                                break;
                        }
                    }

                    @Override
                    public void onConnectionFailed(int errorCode) {
                        Log.d(TAG, "Connection failed, code " + errorCode);
                        Toast.makeText(context, "Connection failed", Toast.LENGTH_SHORT).show();
                        //  Create and send the broadcast to main activity to stop bg thread
                        Intent intent = new Intent(ACTION_BT_FAILED);
                        context.sendBroadcast(intent);

                        connection.disconnect();
                        stopAllServices();
                    }
                };

        connection.connect(device, true, connectionListener, null);
    }
    private void setupBluetoothListener() {
        // Create the listener object
        BluetoothListener.onReceiveListener myListener = new BluetoothListener.onReceiveListener() {
            @Override
            public void onReceived(String data) {
            }
            @Override
            public void onReceived(String data, byte[] mavlinkBytes) {
                Log.d(TAG, "RX :" + mavlinkBytes.length);
                webSocketHandler.connSendPayloadBytes(mavlinkBytes);
            }
        };
        //set listener
        SendReceive.getInstance().setOnReceiveListener(myListener);

    }
    public void stopAllServices() {
        if(connection.isConnected()) {
            connection.disconnect();
        }
        webSocketHandler.closeConnection();
    }
}
