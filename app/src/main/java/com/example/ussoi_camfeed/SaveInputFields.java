package com.example.ussoi_camfeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;

public class SaveInputFields {
    private static SaveInputFields instance;

    private final SharedPreferences prefs;
    // key value pairs for shared preferences
    static final String PREFS_NAME   = "UsbAppPrefs";
    static final String KEY_BAUD_RATE = "baud_rate";
    static final String KEY_IP        = "ip";
    static final String KEY_HTTP      = "http";
    static final String KEY_WEBSOCKET = "websocket";
    static final String KEY_BT_SWITCH = "bt_enable";
    static final String KEY_VIDEO_SWITCH = "video_enable";
    static final String telemetry = "telemetry";
    static final String streaming = "streaming";
    static final String control_api = "control/api";
    static final String KEY_MAVLINK = "mavlink";


    // Private constructor for singleton
    private SaveInputFields(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    // makes sure class has only one active object
    public static SaveInputFields getInstance(Context context){
        if ( instance == null){
            instance = new SaveInputFields(context);
        }
        return instance;

    }
    public void restorePreferences(Switch btSwitch, Switch videoSwitch ,EditText baudrate, EditText ip, CheckBox http_checkbox, CheckBox websocket_checkbox, Switch mavlinkButton) {
        baudrate.setText(String.valueOf(prefs.getInt(KEY_BAUD_RATE, 115200)));
        ip.setText(prefs.getString(KEY_IP, ""));
        http_checkbox.setChecked(prefs.getBoolean(KEY_HTTP, false));
        websocket_checkbox.setChecked(prefs.getBoolean(KEY_WEBSOCKET, false));
        btSwitch.setChecked(prefs.getBoolean(KEY_BT_SWITCH, false));
        videoSwitch.setChecked(prefs.getBoolean(KEY_VIDEO_SWITCH, false));
        mavlinkButton.setChecked(prefs.getBoolean(KEY_MAVLINK,true));
    }
    public void savePreferences(boolean btOn, boolean videoOn, EditText baudrate, EditText ip, boolean http, boolean websocket, boolean mavlink){

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_BT_SWITCH,btOn);
        editor.putBoolean(KEY_VIDEO_SWITCH,videoOn);
        editor.putInt(KEY_BAUD_RATE,Integer.parseInt(baudrate.getText().toString().trim()));
        editor.putString(KEY_IP,ip.getText().toString().trim());
        editor.putBoolean(KEY_HTTP, http);
        editor.putBoolean(KEY_WEBSOCKET, websocket);
        editor.putString(telemetry,telemetry);
        editor.putString(streaming,streaming);
        editor.putBoolean(KEY_MAVLINK,mavlink);
        editor.apply();
    }
    public SharedPreferences get_shared_pref(){ // returns obj obj of shared prefs
        return prefs;
    }
}
