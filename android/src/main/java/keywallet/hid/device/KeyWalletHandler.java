package keywallet.hid.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.util.ArrayList;
import java.util.HashMap;

public class KeyWalletHandler {

    Context mContext;
    public UsbManager mUsbManager;
    private static final String ACTION_USB_PERMISSION = "com.example.doye.usbtest.USB_PERMISSION";
    private OnDeviceConnected mOnDeviceConnected;
    private OnDeviceDetached mOnDeviceDetached;
    private OnDeviceAttached mOnDeviceAttached;
    ArrayList<KeyWalletHidDevice> keyWalletHidDeviceArray;

    public KeyWalletHandler(Context context, OnDeviceAttached mOnDeviceAttached, OnDeviceDetached mOnDeviceDetached,
            OnDeviceConnected mOnDeviceConnected) {
        this.mContext = context;
        this.mOnDeviceAttached = mOnDeviceAttached;
        this.mOnDeviceDetached = mOnDeviceDetached;
        this.mOnDeviceConnected = mOnDeviceConnected;
        BrodcastReceiver();
    }

    public ArrayList<KeyWalletHidDevice> getDeviceList() {
        try {
            keyWalletHidDeviceArray = new ArrayList<KeyWalletHidDevice>();
            mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

            if (mUsbManager != null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
                for (String key : deviceList.keySet()) {

                    KeyWalletHidDevice device = new KeyWalletHidDevice(deviceList.get(key), mUsbManager, mContext);
                    keyWalletHidDeviceArray.add(device);
                }
                return keyWalletHidDeviceArray;
            } else
                return null;
        } catch (NullPointerException e) {
            throw new NullPointerException();
        }
    }

    public void BrodcastReceiver() {
        try {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            mContext.registerReceiver(mUsbReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            mOnDeviceConnected.onConnectedEvent();
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                mOnDeviceDetached.onDetached();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mOnDeviceAttached.onAttachedEvent();
            }
        }
    };

    public interface OnDeviceConnected {
        void onConnectedEvent();
    }

    void setmOnDeviceConnected(OnDeviceConnected listener) {
        mOnDeviceConnected = listener;
    }

    public interface OnDeviceDetached {
        void onDetached();
    }

    void setmOnDeviceDetached(OnDeviceDetached listener) {
        mOnDeviceDetached = listener;
    }

    public interface OnDeviceAttached {
        void onAttachedEvent();
    }

    void setmOnDeviceAttached(OnDeviceAttached listener) {
        mOnDeviceAttached = listener;
    }

}
