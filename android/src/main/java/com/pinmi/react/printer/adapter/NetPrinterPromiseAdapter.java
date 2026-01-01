package com.pinmi.react.printer.adapter;
import static com.pinmi.react.printer.adapter.UtilsImage.getBitmapFromURL;
import static com.pinmi.react.printer.adapter.UtilsImage.getEscPosImageBytes;
import static com.pinmi.react.printer.adapter.UtilsImage.getPixelsSlow;
import static com.pinmi.react.printer.adapter.UtilsImage.recollectSlice;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.graphics.BitmapFactory;
import androidx.annotation.RequiresApi;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class NetPrinterPromiseAdapter implements PrinterPromiseAdapter {
    private static NetPrinterPromiseAdapter mInstance;
    private ReactApplicationContext mContext;
    private final String LOG_TAG = "RNNetPrinter";
    private NetPrinterDevice mNetDevice;

    // {TODO- support other ports later}

    private final int[] PRINTER_ON_PORTS = {9100};
    private static final String EVENT_SCANNER_RESOLVED = "scannerResolved";
    private static final String EVENT_SCANNER_RUNNING = "scannerRunning";

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
    private final static byte[] SET_LINE_SPACE_24 = new byte[]{ESC_CHAR, 0x33, 24};
    private final static byte[] SET_LINE_SPACE_32 = new byte[]{ESC_CHAR, 0x33, 32};
    private final static byte[] LINE_FEED = new byte[]{0x0A};
    private static final byte[] CENTER_ALIGN = {0x1B, 0X61, 0X31};

    private Socket mSocket;

    private boolean isRunning = false;

    private NetPrinterPromiseAdapter() {

    }

    public static NetPrinterPromiseAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new NetPrinterPromiseAdapter();

        }
        return mInstance;
    }

    @Override
    public void init(ReactApplicationContext reactContext, Promise promise) {
        this.mContext = reactContext;
        promise.resolve("done");
    }

    @Override
    public List<PrinterDevice> getDeviceList(Promise promise) {
        // promise.reject("do not need to invoke get device list for net
        // printer");
        // Use emitter instancee get devicelist to non block main thread
        this.scan(promise);
        return new ArrayList<>();
    }

    private void scan(Promise promise) {
        if (isRunning) {
            promise.reject("Already scaning");
            return;
        }
        Log.i(LOG_TAG, "Running scan");

        isRunning = true;
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    Log.i(LOG_TAG, "Inside Running scan");
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);

                    WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    String ipAddress = ipToString(wifiManager.getConnectionInfo().getIpAddress());
                    WritableArray arrayEvent = Arguments.createArray();
                    WritableArray arrayPromise = Arguments.createArray();

                    String prefix = ipAddress.substring(0, ipAddress.lastIndexOf('.') + 1);
                    int suffix = Integer
                            .parseInt(ipAddress.substring(ipAddress.lastIndexOf('.') + 1, ipAddress.length()));

                    for (int i = 0; i <= 255; i++) {
                        if (i == suffix)
                            continue;
                        Log.i(LOG_TAG, "Scanning address: " + prefix + i);
                        ArrayList<Integer> ports = getAvailablePorts(prefix + i);
                        if (!ports.isEmpty()) {
                            WritableMap payload = Arguments.createMap();

                            payload.putString("host", prefix + i);
                            payload.putInt("port", 9100);

                            arrayEvent.pushMap(payload);
                            arrayPromise.pushMap(payload);
                        }
                    }

                    Log.i(LOG_TAG, "Emitting scan resolved");
                    emitEvent(EVENT_SCANNER_RESOLVED, arrayEvent);
                    Log.i(LOG_TAG, "Resolving scan array");
                    promise.resolve(arrayPromise);
                } catch (NullPointerException ex) {
                    Log.i(LOG_TAG, "No connection");
                } finally {
                    isRunning = false;
                    Log.i(LOG_TAG, "Emitting scan isRunning false");
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);

                }
            }
        }).start();
    }

    private void emitEvent(String eventName, Object data) {
        if (mContext != null) {
            mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, data);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private ArrayList<Integer> getAvailablePorts(String address) {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : PRINTER_ON_PORTS) {
            if (crunchifyAddressReachable(address, port))
                ports.add(port);
        }
        return ports;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static boolean crunchifyAddressReachable(String address, int port) {
        try {

            try (Socket crunchifySocket = new Socket()) {
                // Connects this socket to the server with a specified timeout value.
                crunchifySocket.connect(new InetSocketAddress(address, port), 100);
            }
            // Return true if connection successful
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private String ipToString(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise) {
        NetPrinterDeviceId netPrinterDeviceId = (NetPrinterDeviceId) printerDeviceId;

        if (this.mSocket != null && !this.mSocket.isClosed()
                && mNetDevice.getPrinterDeviceId().equals(netPrinterDeviceId)) {
            Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
            promise.resolve(this.mNetDevice.toRNWritableMap());
            return;
        }

        try {
            Socket socket = new Socket(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
            if (socket.isConnected()) {
                closeConnectionIfExists();
                this.mSocket = socket;
                this.mNetDevice = new NetPrinterDevice(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
                promise.resolve(this.mNetDevice.toRNWritableMap());
            } else {
                promise.reject("unable to build connection with host: " + netPrinterDeviceId.getHost()
                        + ", port: " + netPrinterDeviceId.getPort());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            promise.reject("failed to connect printer: " + e.getMessage());
        }
    }

    @Override
    public void closeConnectionIfExists() {
        if (this.mSocket != null) {
            if (!this.mSocket.isClosed()) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            this.mSocket = null;

        }
    }

    @Override
    public void printRawData(String rawBase64Data, Promise promise) {
        if (this.mSocket == null) {
            promise.reject("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }
        final String rawData = rawBase64Data;
        final Socket socket = this.mSocket;
        Log.v(LOG_TAG, "start to print raw data " + rawBase64Data);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
                    OutputStream printerOutputStream = socket.getOutputStream();
                    printerOutputStream.write(bytes, 0, bytes.length);
                    printerOutputStream.flush();
                    promise.resolve("done");
                } catch (IOException e) {
                    Log.e(LOG_TAG, "failed to print data" + rawData);
                    e.printStackTrace();
                    promise.reject("failed to print data" + e.getMessage());
                }
            }
        }).start();

    }

    @Override
    public void printImageData(final String imageUrl, int imageWidth, int imageHeight, Promise promise) {
        Bitmap bitmapImage = null;
        if (imageUrl.contains("http")) {
            bitmapImage = getBitmapFromURL(imageUrl);
        } else {
            try {
                bitmapImage = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(imageUrl));
            }
            catch (IOException e){
                promise.reject("image not found");
                return;
            }
        }

        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mSocket == null) {
            promise.reject("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;
        try {
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            OutputStream printerOutputStream = socket.getOutputStream();

            printerOutputStream.write(SET_LINE_SPACE_24);
            printerOutputStream.write(CENTER_ALIGN);

            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printerOutputStream.write(new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)});
                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printerOutputStream.write(recollectSlice(y, x, pixels));
                }

                // Do a line feed, if not the printing will resume on the same line
                printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            printerOutputStream.write(LINE_FEED);

            printerOutputStream.flush();
            promise.resolve("done");
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
            promise.reject("failed to print data" + e.getMessage());
        }
    }

    @Override
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Promise promise) {
        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mSocket == null) {
            promise.reject("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;

        try {
            final boolean useEscAsteriskCommand = true;
            final boolean useGsv0 = true;

            OutputStream printerOutputStream = socket.getOutputStream();

            if(useGsv0 || useEscAsteriskCommand) {
                byte[][] pixels = getEscPosImageBytes(bitmapImage, imageWidth, imageHeight, useEscAsteriskCommand);
                printerOutputStream.write(SET_LINE_SPACE_24);
                printerOutputStream.write(CENTER_ALIGN);
                for (byte[] bytes : pixels) {
                    printerOutputStream.write(bytes);
                    printerOutputStream.flush();
                    // this.printerConnection.send();
                }
            } else {
              int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);
              printerOutputStream.write(SET_LINE_SPACE_24);
              printerOutputStream.write(CENTER_ALIGN);

              for (int y = 0; y < pixels.length; y += 24) {
                  // Like I said before, when done sending data,
                  // the printer will resume to normal text printing
                  printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                  // Set nL and nH based on the width of the image
                  printerOutputStream.write(new byte[]{(byte) (0x00ff & pixels[y].length)
                          , (byte) ((0xff00 & pixels[y].length) >> 8)});
                  for (int x = 0; x < pixels[y].length; x++) {
                      // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                      printerOutputStream.write(recollectSlice(y, x, pixels));
                  }

                  // Do a line feed, if not the printing will resume on the same line
                  printerOutputStream.write(LINE_FEED);
              }
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            // printerOutputStream.write(LINE_FEED);

            printerOutputStream.flush();
            promise.resolve("done");
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
            promise.reject("failed to print data" + e.getMessage());
        } catch (EscPosConnectionException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
            promise.reject("failed to print data" + e.getMessage());
        }
    }


    private boolean isPortOpen(String host, int port, int timeout) {
        return crunchifyAddressReachable(host, port);

        // try (Socket socket = new Socket()) {
        //     socket.connect(new InetSocketAddress(host, port), timeout);
        //     return true;
        // } catch (IOException e) {
        //     return false;
        // }
    }

    @Override 
    public void getAllNetworkDevices(int port, Promise promise) {
        if (isRunning) {
            promise.reject("Already scanning");
            return;
        }
        isRunning = true;
        Log.i(LOG_TAG, "Running All Network Devices scan");
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    Log.i(LOG_TAG, "Inside Running All Network Devices scan");
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);

                    WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    String ipAddress = ipToString(wifiManager.getConnectionInfo().getIpAddress());
                    WritableArray arrayEvent = Arguments.createArray();
                    WritableArray arrayPromise = Arguments.createArray();

                    String prefix = ipAddress.substring(0, ipAddress.lastIndexOf('.') + 1);
                    int suffix = Integer.parseInt(ipAddress.substring(ipAddress.lastIndexOf('.') + 1));

                    for (int i = 0; i <= 255; i++) {
                        if (i == suffix) continue;

                        String host = prefix + i;
                        Log.i(LOG_TAG, "Checking host: " + host + " on port: " + port);

                        if (isPortOpen(host, port, 200)) {
                            WritableMap payload = Arguments.createMap();
                            payload.putString("host", host);
                            payload.putInt("port", port);
                            arrayEvent.pushMap(payload);
                            arrayPromise.pushMap(payload);
                        }
                    }

                    Log.i(LOG_TAG, "Emitting All Network Devices scan");
                    emitEvent(EVENT_SCANNER_RESOLVED, arrayEvent);
                    Log.i(LOG_TAG, "Resolving All Network Devices scan");
                    promise.resolve(arrayPromise);
                } catch (NullPointerException ex) {
                    Log.i(LOG_TAG, "No connection");
                    promise.reject("No connection", ex);
                } finally {
                    isRunning = false;
                    Log.i(LOG_TAG, "Emitting All Network Devices scan isRunning false");
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);
                }
            }
        }).start();
    }
}



