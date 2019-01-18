package keywallet.hid.device;

import android.widget.Toast;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.lang.StringBuilder;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;

public class Module extends ReactContextBaseJavaModule {

  private static final String DURATION_SHORT_KEY = "SHORT";
  private static final String DURATION_LONG_KEY = "LONG";
  private static final String ACTION_USB_PERMISSION = "io.cmichel.boilerplate.USB_PERMISSION";
  public UsbManager mUsbManager;
  public UsbDevice device;
  public KeyWalletHidDevice mKeyWalletHidDevice;
  public KeyWalletHandler keyWalletHandler;
  Context mContext;
  ArrayList<KeyWalletHidDevice> keyWalletHidDeviceArray;

  public Module(ReactApplicationContext reactContext) {
    super(reactContext);
    mContext = reactContext;
  }

  @Override
  public String getName() {
    return "Boilerplate";
  }

  @ReactMethod
  public void getDeviceList(Promise p) {
    WritableArray deviceArray = Arguments.createArray();
    keyWalletHidDeviceArray = keyWalletHandler.getDeviceList();
    try {
      for (int i = 0; i < keyWalletHidDeviceArray.size(); i++) {
        WritableMap map = Arguments.createMap();
        map.putString("name", keyWalletHidDeviceArray.get(i).mDevicePName);
        map.putInt("index", keyWalletHidDeviceArray.size() - 1);
        deviceArray.pushMap(map);
      }
      p.resolve(deviceArray);
    } catch (Exception e) {
      e.printStackTrace();
      p.reject(e);
    }
  }

  @ReactMethod
  public void BroadcastReceiver() {

    keyWalletHandler = new KeyWalletHandler(getReactApplicationContext(), new KeyWalletHandler.OnDeviceAttached() {
      @Override
      public void onAttachedEvent() {
        Toast.makeText(getReactApplicationContext(), "장치가 휴대폰과 연결되었습니다.", Toast.LENGTH_SHORT).show();
      }
    }, new KeyWalletHandler.OnDeviceDetached() {
      @Override
      public void onDetached() {
        Toast.makeText(getReactApplicationContext(), "장치가 휴대폰과 분리되었습니다.", Toast.LENGTH_SHORT).show();
      }
    }, new KeyWalletHandler.OnDeviceConnected() {
      @Override
      public void onConnectedEvent() {
        Toast.makeText(getReactApplicationContext(), "장치와 연결되었습니다.", Toast.LENGTH_SHORT).show();
      }
    });

  }

  @ReactMethod
  public void Connection(int index) {
    mKeyWalletHidDevice = keyWalletHidDeviceArray.get(index);
    mKeyWalletHidDevice.Connection();

  }

  @ReactMethod
  public void IsCardPresent(Promise p) {
    WritableMap map = Arguments.createMap();
    try {
      boolean result = mKeyWalletHidDevice.IsCardPresent();
      if (result) {
        map.putInt("result", 1);
      } else {
        map.putInt("result", 0);
      }
      p.resolve(map);
    } catch (IOException e) {
      p.reject(e);
    }

  }

  @ReactMethod
  public void PowerOn(Promise p) {
    try {
      mKeyWalletHidDevice.PowerOn();
    } catch (IOException e) {
      p.reject(e);
    }
  }

  @ReactMethod
  public void PowerOff(Promise p) {
    try {
      mKeyWalletHidDevice.PowerOff();
    } catch (IOException e) {
      p.reject(e);
    }
  }

  @ReactMethod
  public void Transceive(ReadableArray apdu, Promise p) {

    try {
      byte[] abOutReport = rnArrayToBytes(apdu);
      byte[] asd = mKeyWalletHidDevice.Transceive(abOutReport);
      StringBuilder sb = new StringBuilder();
      for (byte b : asd) {
        sb.append(String.format("%02X", b & 0xff));
      }
      WritableMap map = Arguments.createMap();
      map.putString("name", sb.toString());
      p.resolve(map);
    } catch (IOException e) {
      p.reject(e);
    }
  }

  private static byte[] rnArrayToBytes(ReadableArray rArray) {
    byte[] bytes = new byte[rArray.size()];
    for (int i = 0; i < rArray.size(); i++) {
      bytes[i] = (byte) (rArray.getInt(i) & 0xff);
    }
    return bytes;
  }

  private UsbManager getUsbManager() {
    ReactApplicationContext rAppContext = getReactApplicationContext();
    UsbManager usbManager = (UsbManager) rAppContext.getSystemService(rAppContext.USB_SERVICE);
    return usbManager;
  }

}