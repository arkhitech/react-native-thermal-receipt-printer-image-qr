package com.pinmi.react.printer;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;

/**
 * Created by xiesubin on 2017/9/21.
 */

public interface RNPrinterPromiseModule {

    @ReactMethod
    public void init(Promise promise);

    @ReactMethod
    public void closeConn(Promise promise);

    @ReactMethod
    public void getDeviceList(Promise promise);

    @ReactMethod
    public void printRawData(String base64Data, Promise promise) ;

    @ReactMethod
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise);

    @ReactMethod
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Promise promise) throws EscPosConnectionException;

    @ReactMethod
    public void getAllNetworkDevices(int port, Promise promise);
}

