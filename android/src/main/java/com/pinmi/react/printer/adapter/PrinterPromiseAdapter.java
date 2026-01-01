package com.pinmi.react.printer.adapter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.telecom.Call;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;

import java.util.List;

/**
 * Created by xiesubin on 2017/9/21.
 */

public interface PrinterPromiseAdapter {

    public void init(ReactApplicationContext reactContext, Promise promise);

    public List<PrinterDevice> getDeviceList(Promise promise);

    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise);

    public void closeConnectionIfExists();

    public void printRawData(String rawBase64Data, Promise promise);

    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise);

    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Promise promise);

    public void getAllNetworkDevices(int timeout, Promise promise);
}
