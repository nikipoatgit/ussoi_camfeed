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
    private SaveInputFields saveInputFields;
    private BluetoothHandler(){
        this.context = context.getApplicationContext();
        this.connection = new Connection(this.context);
        this.saveInputFields = SaveInputFields.getInstance(context);

    };
    public static synchronized BluetoothHandler getInstance(){
        if (instance == null){
            instance = new BluetoothHandler();
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
                Log.d("Signaling", "Connected to signaling server");
            }

            @Override
            public void onPayloadReceived(String payload) {
                Log.d("Signaling", " Received signaling payload: " + payload);

                // Example: parse JSON
                try {
                    JSONObject json = new JSONObject(payload);
                    String type = json.optString("type");
                    if ("offer".equals(type)) {
                        // handle WebRTC offer
                    } else if ("answer".equals(type)) {
                        // handle WebRTC answer
                    } else if ("candidate".equals(type)) {
                        // handle ICE candidate
                    }
                } catch (Exception e) {
                    Log.e("Signaling", "Failed to parse signaling payload", e);
                }
            }

            @Override
            public void onClosed() {
                Log.d("Signaling", "Signaling connection closed: " );
            }

            @Override
            public void onError(String error) {
                Log.e("Signaling", "Error in signaling: " + error);
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
    //note u can edit SendReceive.java file of library then modify line 155 to 160 , so u can directly get bytedata
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
    public void sendData(byte[] mavlinkBytes) {
        boolean success = SendReceive.getInstance().send(mavlinkBytes);
        Log.d(TAG, success ? "[TX] " + mavlinkBytes.length : "[TX] Failed: " +  mavlinkBytes.length);
    }
    public void disconnect() {
        connection.disconnect();
    }
    public boolean isConnected() {
        return connection.isConnected();
    }
    public void stopAllServices() {
        disconnect();
    }
}
