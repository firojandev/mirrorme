package com.domainbangla.mirrorme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class DeviceTwoActivity extends AppCompatActivity {

    private static final String TAG = "DeviceTwoActivity";
    private UsbManager usbManager;

    private TextView tvDevices;
    private String connectedDevStr = "";

    private static final String ACTION_USB_PERMISSION = "com.domainbangla.mirrorme.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG,"action:"+action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted, you can now communicate with the USB device
                            Log.d(TAG, "Permission granted for USB device: " + device.getDeviceName());
                            // Access the list of connected USB devices
                            listConnectedDevices();
                        }
                    } else {
                        Log.d(TAG, "Permission denied for USB device: " + device.getDeviceName());
                        showToast("Permission denied for USB device");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_two);

        tvDevices = findViewById(R.id.tvDevices);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register the broadcast receiver to listen for USB permission events
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        LocalBroadcastManager.getInstance(this).registerReceiver(usbReceiver, filter);

        // Request permission to access USB devices
        requestUSBPermission();
    }

    private void requestUSBPermission() {
        Log.d(TAG, "Requesting USB permission");

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "Requesting permission for USB device: " + device.getDeviceName());

            // Request permission from the user
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
            permissionIntent.putExtra(UsbManager.EXTRA_DEVICE, device);
            LocalBroadcastManager.getInstance(this).sendBroadcast(permissionIntent);
        }
    }


    private void listConnectedDevices() {
        // Access the list of connected USB devices and perform actions
        HashMap<String, UsbDevice> connectedDevices = usbManager.getDeviceList();

        // Log information about each connected device
        for (UsbDevice device : connectedDevices.values()) {
            connectedDevStr = connectedDevStr + "USB Device found: " +
                    "DeviceName: " + device.getDeviceName() + ", " +
                    "DeviceId: " + device.getDeviceId() + ", " +
                    "VendorId: " + device.getVendorId() + ", " +
                    "ProductId: " + device.getProductId();
        }

        if (!connectedDevStr.equals("")){
            tvDevices.setText(connectedDevStr);
        }else{
            tvDevices.setText("No USB device found");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(usbReceiver);
    }

    public void showToast(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();

    }

}
