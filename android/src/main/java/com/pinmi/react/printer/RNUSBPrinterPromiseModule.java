package com.pinmi.react.printer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.pinmi.react.printer.adapter.PrinterPromiseAdapter;
import com.pinmi.react.printer.adapter.PrinterDevice;
import com.pinmi.react.printer.adapter.USBPrinterPromiseAdapter;
import com.pinmi.react.printer.adapter.USBPrinterDeviceId;

import java.util.List;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNUSBPrinterPromiseModule extends ReactContextBaseJavaModule implements RNPrinterPromiseModule {

    protected ReactApplicationContext reactContext;

    protected PrinterPromiseAdapter adapter;

    public RNUSBPrinterPromiseModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Promise promise) {
        this.adapter = USBPrinterPromiseAdapter.getInstance();
        this.adapter.init(reactContext, promise);
    }

    @ReactMethod
    @Override
    public void closeConn(Promise promise)  {
        if(this.adapter != null) {
          adapter.closeConnectionIfExists();
        }
        promise.resolve("done");
    }

    @ReactMethod
    @Override
    public void getDeviceList(Promise promise)  {
        List<PrinterDevice> printerDevices = adapter.getDeviceList(promise);
        WritableArray pairedDeviceList = Arguments.createArray();
        if(printerDevices.size() > 0) {
            for (PrinterDevice printerDevice : printerDevices) {
                pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
            }
            promise.resolve(pairedDeviceList);
        }else{
            promise.reject("No Device Found");
        }
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Promise promise){
        adapter.printRawData(base64Data, promise);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise) {
        adapter.printImageData(imageUrl, imageWidth, imageHeight, promise);
    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Promise promise) {
        // String imageBase64 = "data:image/png;base64," + imageUrl;
        // String base64ImageProcessed = imageUrl.split(",")[1];
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        adapter.printImageBase64(decodedByte, imageWidth, imageHeight, promise);
    }

    @ReactMethod
    public void connectPrinter(Integer vendorId, Integer productId, Promise promise) {
        adapter.selectDevice(USBPrinterDeviceId.valueOf(vendorId, productId), promise);
    }

    @ReactMethod
    @Override
    public void getAllNetworkDevices(int port, Promise promise) {
        promise.reject("NOT_SUPPORTED", "Network device discovery is not supported for USB printers");
    }

    @Override
    public String getName() {
        return "RNUSBPromisePrinter";
    }
}
