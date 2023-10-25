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
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.util.HashMap;

public class UsbActivity extends Activity {
    private static final String TAG = "UsbEnumerator";

    private static final String ACTION_USB_PERMISSION = "com.domainbangla.mirrorme.USB_PERMISSION";


    /* USB system service */
    private UsbManager mUsbManager;

    /* UI elements */
    private TextView mStatusView, mResultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb);

        mStatusView = (TextView) findViewById(R.id.text_status);
        mResultView = (TextView) findViewById(R.id.text_result);

        mUsbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        handleIntent(getIntent());
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
            } if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted, you can now communicate with the USB device
                            communication(device);
                        }
                    } else {
                        Log.d(TAG, "Permission denied for USB device: " + device.getDeviceName());
                        showToast("Permission denied for USB device");
                    }
                }
            }
        }
    };

    public void showToast(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();

    }

    /**
     * Determine whether to list all devices or query a specific device from
     * the provided intent.
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
                communication(device);
            } else {
                // Request USB permission
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(device, pi);
            }
        }

    }


    public void communication(UsbDevice device) {
        // Open a connection to the USB device
        UsbDeviceConnection connection = mUsbManager.openDevice(device);

        if (connection == null) {
            showToast("Failed to open USB connection");
            return;
        }

        try {
            // Find the first OUT endpoint
            UsbEndpoint outEndpoint = null;
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                            endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint;
                        break;
                    }
                }
                if (outEndpoint != null) {
                    break;
                }
            }

            if (outEndpoint == null) {
                showToast("No suitable OUT endpoint found");
                return;
            }

            // Prepare the data to be sent
            byte[] dataToSend = "Hello, USB Device!".getBytes();

            // Send the data
            int result = connection.bulkTransfer(outEndpoint, dataToSend, dataToSend.length, 1000);

            if (result < 0) {
                showToast("Failed to send data");
            } else {
                showToast("Data sent successfully");
            }

            receiveData(device);

        } finally {
            // Close the USB connection when done
            connection.close();
        }
    }

    private class ReceiveDataTask extends AsyncTask<UsbDevice, Void, String> {
        @Override
        protected String doInBackground(UsbDevice... devices) {
            UsbDevice device = devices[0];

            UsbDeviceConnection connection = mUsbManager.openDevice(device);

            if (connection == null) {
                showToast("Failed to open USB connection");
                return null;
            }

            try {
                // Find the first IN endpoint
                UsbEndpoint inEndpoint = null;
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);
                    for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                        UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                        if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                                endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                            inEndpoint = endpoint;
                            break;
                        }
                    }
                    if (inEndpoint != null) {
                        break;
                    }
                }

                if (inEndpoint == null) {
                    showToast("No suitable IN endpoint found");
                    return null;
                }

                // Receive data
                byte[] buffer = new byte[1024];
                int bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.length, 1000);

                if (bytesRead < 0) {
                    showToast("Failed to receive data");
                } else {
                    showToast("Received data: " + new String(buffer, 0, bytesRead));
                    return new String(buffer, 0, bytesRead);
                }
            } finally {
                // Close the USB connection when done
                connection.close();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            // Handle the received data in the UI thread
            if (result != null) {
                // Do something with the received data
            }
        }
    }

    // ... (your existing code)

    // Method to initiate receiving data
    private void receiveData(UsbDevice device) {
        // Execute the AsyncTask to receive data in the background
        new ReceiveDataTask().execute(device);
    }


    /**
     * Print a basic description about a specific USB device.
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
