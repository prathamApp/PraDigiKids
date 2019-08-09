package com.pratham.pradigikids.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.pratham.pradigikids.models.StorageInfo;
import com.pratham.pradigikids.util.PD_Utility;

import java.util.List;

public class OTGListener extends BroadcastReceiver {
    private static final String TAG = OTGListener.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //Initilizing globel class to access USB ATTACH and DETACH state
        if (action.equalsIgnoreCase("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                int vendorID = device.getVendorId();
                int productID = device.getProductId();
                Toast.makeText(context, "onReceive: usb attached: vendorid= " + vendorID + "..productid=" + productID, Toast.LENGTH_SHORT).show();
                List<StorageInfo> info = PD_Utility.getStorageList();
                for (StorageInfo in : info) {
                    Toast.makeText(context, in.getDisplayName(), Toast.LENGTH_SHORT).show();
                }
            }
        } else if (action.equalsIgnoreCase("android.hardware.usb.action.USB_DEVICE_DETACHED")) {
            //When ever device Detach set your global variable to "false"
            Toast.makeText(context, "onReceive: usb detached", Toast.LENGTH_SHORT).show();
        }
    }
}