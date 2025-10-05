package com.example.ussoi_camfeed;
import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity; // provides backward compatibility
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class MainActivity  extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";
    private UsbManager usbManager; // framework that enables usb communications
    private SaveInputFields saveInputFields; // initlise savefrefclass obj
    private BluetoothHandler bluetoothHandler;
    private TextView usbInfoText;
    private EditText baudrate;
    private EditText url_ip;
    private CheckBox http;
    private CheckBox websocket;
    private Switch btswitch;
    private Switch videoswitch;
    private Switch mavlinkButton;
    private Button serviceButton;
    private boolean serviceRunning = false;
    private boolean requestedAny;
    private BluetoothDevice selectedBtDevice; // Member variable to hold the chosen device
    private static final int PERMISSION_REQUEST_CODE = 1001;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Bind UI elements
        usbInfoText = findViewById(R.id.usbInfoText);
        baudrate = findViewById(R.id.baudrateInfoText);
        url_ip = findViewById(R.id.url_ip);
        http = findViewById(R.id.http_checkbox);
        websocket = findViewById(R.id.websocket_checkbox);
        btswitch = findViewById(R.id.btswitch1);
        videoswitch = findViewById(R.id.videoswitch1);
        serviceButton = findViewById(R.id.serviceButton);
        mavlinkButton = findViewById(R.id.mavswitch1);

        // initialise obj instance for saved prefs
        saveInputFields = saveInputFields.getInstance(this);

        saveInputFields.restorePreferences(btswitch,videoswitch,baudrate,url_ip,http,websocket,mavlinkButton);

        // Request Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            ActivityCompat.requestPermissions(this, perms, 1);
        }

        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, usbFilter);

        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, permissionReceiver, permissionFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(permissionReceiver, permissionFilter);
        }
        // Check connected USB devices at startup
        checkAndRequestPermissions();
        checkExistingDevices();
        requestNotificationPermission();
        serviceButton.setOnClickListener(v -> {
            if (http.isChecked() ^ websocket.isChecked() ) {
                if (!serviceRunning) {
                    saveInputFields.savePreferences(btswitch.isChecked(), videoswitch.isChecked(), baudrate, url_ip, http.isChecked(), websocket.isChecked(),mavlinkButton.isChecked());
                    if (btswitch.isChecked() && mavlinkButton.isChecked()) {
                        pickBluetoothDeviceThenStart();
                        }
                    else {
                        startMainService();
                    }
                }
                else {
                    stopMainService();
                }
            }
            else {
                Toast.makeText(this,"Select one check box",Toast.LENGTH_SHORT).show();
            }

        });
    }
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            Log.d("PermissionCheck", "All permissions granted!");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d("PermissionCheck", "All permissions granted!");
            } else {
                Log.w("PermissionCheck", "User denied one or more permissions.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register your receiver with the NOT_EXPORTED flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 (Tiramisu) and above
            registerReceiver(btFailReceiver, new IntentFilter(ACTION_BT_FAILED), RECEIVER_NOT_EXPORTED);
        } else {
            // For older versions
            registerReceiver(btFailReceiver, new IntentFilter(ACTION_BT_FAILED));
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        // And unregister it to prevent leaks
        unregisterReceiver(btFailReceiver);
    }
    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(usbReceiver); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(permissionReceiver); } catch (IllegalArgumentException ignored) {}
    }
    void startMainService(){
        //initialise Bt obj
        if (btswitch.isChecked()) {
            bluetoothHandler = BluetoothHandler.getInstance(this);
            bluetoothHandler.setDevice(selectedBtDevice);
        }
        serviceButton.setText("Stop Service");
        serviceRunning = true;
        //initialise service intent
        Intent serviceIntent = new Intent(this,ServiceManager.class);
        // to pass data to serviceIntent : serviceIntent.putExtra("IP ADDER",45);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

    }
    void stopMainService(){
        serviceButton.setText("Start Service");
        serviceRunning = false;
        Intent serviceIntent = new Intent(this, ServiceManager.class);
        stopService(serviceIntent);

    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }
    public static final String ACTION_BT_FAILED = "com.example.ussoi.ACTION_BT_FAILED";
    private final BroadcastReceiver btFailReceiver = new BroadcastReceiver() {  // this list to broadcast when bt conn  failed
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BT_FAILED.equals(intent.getAction())) {
                Toast.makeText(MainActivity.this, "Bluetooth connection failed", Toast.LENGTH_SHORT).show();
                Log.d(TAG,"bt failed service stopped");
                selectedBtDevice = null;
//                stopMainService();   // stop the service & reset UI
            }
        }
    };
    private void pickBluetoothDeviceThenStart() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkBtPermission()) return;

        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired.isEmpty()) {
            Toast.makeText(this, "No paired devices", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<BluetoothDevice> devices = new ArrayList<>(paired);
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            names[i] = devices.get(i).getName() + "\n" + devices.get(i).getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Device")
                .setItems(names, (dialog, which) -> {
                    selectedBtDevice = devices.get(which);
                    startMainService();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    serviceButton.setText("Start Service");
                    serviceRunning = false;
                    Toast.makeText(this, "No BT device selected", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
    private boolean checkBtPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth connect permission is needed.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    requestUsbPermission(device);
                    usbInfoText.setText("USB device attached, requesting permission...");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                usbInfoText.setText("USB device disconnected");
            }
        }
    };
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) {
                    usbInfoText.setText(buildDeviceInfo());
                    Log.d(TAG, "USB permission granted – info updated");
                } else {
                    usbInfoText.setText("USB device detected but permission denied");
                    Log.d(TAG, "USB permission denied");
                }
            }
        }
    };
    private void checkExistingDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            usbInfoText.setText("No USB device connected");
            return;
        }

        requestedAny = false;
        for (UsbDevice device : deviceList.values()) {
            requestUsbPermission(device);
            requestedAny = true;
        }

        usbInfoText.setText(requestedAny
                ? "USB device(s) detected - requesting permission..."
                : buildDeviceInfo());
    }
    private void requestUsbPermission(UsbDevice device) {
        Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        usbManager.requestPermission(device, pi);
        Log.d(TAG, "Requesting permission for device: " + device.getDeviceName());
    }
    private String buildDeviceInfo() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) return "No USB device connected";

        StringBuilder sb = new StringBuilder();
        for (UsbDevice device : deviceList.values()) {
            sb.append("Device: ").append(device.getDeviceName()).append("\n")
                    .append("VID: 0x").append(String.format("%04X", device.getVendorId()))
                    .append("   PID: 0x").append(String.format("%04X", device.getProductId())).append("\n")
                    .append("Class: ").append(device.getDeviceClass())
                    .append("  Subclass: ").append(device.getDeviceSubclass())
                    .append("  Protocol: ").append(device.getDeviceProtocol()).append("\n");
            sb.append("Manufacturer: ").append(device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown")
                    .append("\nProduct: ").append(device.getProductName() != null ? device.getProductName() : "Unknown")
                    .append("\nSerial#: ").append(device.getSerialNumber() != null ? device.getSerialNumber() : "Unknown")
                    .append("\n");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

}
