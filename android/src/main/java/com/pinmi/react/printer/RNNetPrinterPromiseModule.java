package com.pinmi.react.printer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.pinmi.react.printer.adapter.NetPrinterPromiseAdapter;
import com.pinmi.react.printer.adapter.NetPrinterDeviceId;
import com.pinmi.react.printer.adapter.PrinterPromiseAdapter;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNNetPrinterPromiseModule extends ReactContextBaseJavaModule implements RNPrinterPromiseModule {

    private PrinterPromiseAdapter adapter;
    private ReactApplicationContext reactContext;

    public RNNetPrinterPromiseModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Promise promise) {
        this.adapter = NetPrinterPromiseAdapter.getInstance();
        this.adapter.init(reactContext, promise);
    }

    @ReactMethod
    @Override
    public void closeConn(Promise promise) {
        if(this.adapter != null) {
          this.adapter.closeConnectionIfExists();
        }
        promise.resolve("done");
    }
    
    @ReactMethod
    @Override
    public void getDeviceList(Promise promise) {
        try {
            this.adapter.getDeviceList(promise);
        } catch (Exception ex) {
            promise.reject(ex.getMessage());
        }
        // this.adapter.getDeviceList(errorCallback);
    }

    @ReactMethod
    public void connectPrinter(String host, Integer port, Promise promise) {
        adapter.selectDevice(NetPrinterDeviceId.valueOf(host, port), promise);
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Promise promise) {
        adapter.printRawData(base64Data, promise);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise) {
        Log.v("imageUrl", imageUrl);
        adapter.printImageData(imageUrl, imageWidth, imageHeight, promise);
    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Promise promise) throws EscPosConnectionException {
        // String imageBase64 = "data:image/png;base64," + imageUrl;
        // String base64ImageProcessed = imageUrl.split(",")[1];
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        adapter.printImageBase64(decodedByte, imageWidth, imageHeight, promise);
    }

    @Override
    public String getName() {
        return "RNNetPromisePrinter";
    }

    @ReactMethod
    @Override
    public void getAllNetworkDevices(int port, Promise promise) {
        try {
            this.adapter.getAllNetworkDevices(port , promise);
        } catch (Exception ex) {
            promise.reject(ex.getMessage());
        }
        // this.adapter.getDeviceList(errorCallback);
    }
}
