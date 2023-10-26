/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.domainbangla.mirrorme;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.util.HashMap;

public class UsbSenderActivity extends Activity {
    private static final String TAG = "UsbEnumerator";

    private static final String ACTION_USB_PERMISSION = "com.domainbangla.mirrorme.USB_PERMISSION";


    /* USB system service */
    private UsbManager mUsbManager;

    /* UI elements */
    private TextView mStatusView, mResultView;
    private Button btnSend;

    UsbDevice usbDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb);

        btnSend = (Button) findViewById(R.id.btnSend);
        mStatusView = (TextView) findViewById(R.id.text_status);
        mResultView = (TextView) findViewById(R.id.text_result);

        mUsbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        handleIntent(getIntent());

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                send();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
    }

    /**
     * Broadcast receiver to handle USB disconnect events.
     */
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    printStatus(getString(R.string.status_removed));
                    printDeviceDescription(device);
                }
            }
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted, you can now communicate with the USB device
                            usbDevice = device;
                            send();
                        }
                    } else {
                        Log.d(TAG, "Permission denied for USB device: " + device.getDeviceName());
                        showToast("Permission denied for USB device");
                    }
                }
            }
        }
    };

    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    /**
     * Determine whether to list all devices or query a specific device from
     * the provided intent.
     *
     * @param intent Intent to query.
     */
    private void handleIntent(Intent intent) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            printStatus(getString(R.string.status_added));
            printDeviceDetails(device);
        } else {
            // List all devices connected to USB host on startup
            printStatus(getString(R.string.status_list));
            printDeviceList();
        }
    }

    /**
     * Print the list of currently visible USB devices.
     */
    private void printDeviceList() {
        HashMap<String, UsbDevice> connectedDevices = mUsbManager.getDeviceList();

//        if (connectedDevices.isEmpty()) {
//            printResult("No Devices Currently Connected");
//        } else {
////            StringBuilder builder = new StringBuilder();
////            builder.append("Connected Device Count: ");
////            builder.append(connectedDevices.size());
////            builder.append("\n\n");
////            for (UsbDevice device : connectedDevices.values()) {
////                //Use the last device detected (if multiple) to open
////                builder.append(UsbHelper.readDevice(device));
////                builder.append("\n\n");
////            }
////            printResult(builder.toString());
//
//            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
//            mUsbManager.requestPermission(device, pi);
//
//        }

        for (UsbDevice device : connectedDevices.values()) {
            if (mUsbManager.hasPermission(device)) {
                // Permission already granted, open the device and start communication
                // Implement device communication here
                usbDevice = device;
                send();
            } else {
                // Request USB permission
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(device, pi);
            }
        }

    }


    public void send() {
        if (usbDevice == null) {
            showToast("No USB device is found!");
            return;
        }
        UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
        try {
            UsbInterface usbInterface = usbDevice.getInterface(0); // You may need to adjust the interface and endpoint indices.
            UsbEndpoint endpoint = usbInterface.getEndpoint(0);

            byte[] dataToSend = "Hello, Device 2!".getBytes();
            int bytesSent = connection.bulkTransfer(endpoint, dataToSend, dataToSend.length, 1000);

            if (bytesSent > 0) {
                showToast("Data sent!");
            } else {
                showToast("Failed to send data");
            }

            connection.releaseInterface(usbInterface);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connection.close();
        }

    }

    /**
     * Print a basic description about a specific USB device.
     *
     * @param device USB device to query.
     */
    private void printDeviceDescription(UsbDevice device) {
        String result = UsbHelper.readDevice(device) + "\n\n";
        printResult(result);
    }

    /**
     * Initiate a control transfer to request the device information
     * from its descriptors.
     *
     * @param device USB device to query.
     */
    private void printDeviceDetails(UsbDevice device) {
        UsbDeviceConnection connection = mUsbManager.openDevice(device);

        String deviceString = "";
        try {
            //Parse the raw device descriptor
            deviceString = DeviceDescriptor.fromDeviceConnection(connection)
                    .toString();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid device descriptor", e);
        }

        String configString = "";
        try {
            //Parse the raw configuration descriptor
            configString = ConfigurationDescriptor.fromDeviceConnection(connection)
                    .toString();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid config descriptor", e);
        } catch (ParseException e) {
            Log.w(TAG, "Unable to parse config descriptor", e);
        }

        printResult(deviceString + "\n\n" + configString);
        connection.close();
    }

    /* Helpers to display user content */

    private void printStatus(String status) {
        mStatusView.setText(status);
        Log.i(TAG, status);
    }

    private void printResult(String result) {
        mResultView.setText(result);
        Log.i(TAG, result);
    }
}
