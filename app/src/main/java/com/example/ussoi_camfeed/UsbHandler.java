package com.example.ussoi_camfeed;


import android.content.Context;

public class UsbHandler {
    private static final String TAG = "BtHandel";
    private static UsbHandler instance;
    private Context context;
    private UsbHandler(){
        this.context = context.getApplicationContext();
    };
    public static synchronized UsbHandler getInstance(){
        if (instance == null){
            instance = new UsbHandler();
        }
        return instance;
    }
}
