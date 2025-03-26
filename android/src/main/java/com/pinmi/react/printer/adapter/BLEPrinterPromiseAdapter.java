package com.pinmi.react.printer.adapter;
import static com.pinmi.react.printer.adapter.UtilsImage.getBitmapFromURL;
import static com.pinmi.react.printer.adapter.UtilsImage.getEscPosPrinterSize;
import static com.pinmi.react.printer.adapter.UtilsImage.getEscPosImageBytes;
import static com.pinmi.react.printer.adapter.UtilsImage.getPixelsSlow;
import static com.pinmi.react.printer.adapter.UtilsImage.recollectSlice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.EscPosPrinterCommands;
import com.dantsu.escposprinter.EscPosPrinterSize;
import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.ArrayList;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.graphics.BitmapFactory;
/**
 * Created by xiesubin on 2017/9/21.
 */

public class BLEPrinterPromiseAdapter implements PrinterPromiseAdapter{


    private static BLEPrinterPromiseAdapter mInstance;


    private final String LOG_TAG = "RNBLEPrinter";

    private BluetoothDevice mBluetoothDevice;
    private BluetoothSocket mBluetoothSocket;


    private ReactApplicationContext mContext;

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
    private final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 24 };
    private final static byte[] SET_LINE_SPACE_32 = new byte[] { ESC_CHAR, 0x33, 32 };
    private final static byte[] LINE_FEED = new byte[] { 0x0A };
    private static final byte[] CENTER_ALIGN = { 0x1B, 0X61, 0X31 };



    private BLEPrinterPromiseAdapter(){}

    public static BLEPrinterPromiseAdapter getInstance() {
        if(mInstance == null) {
            mInstance = new BLEPrinterPromiseAdapter();
        }
        return mInstance;
    }

    @Override
    public void init(ReactApplicationContext reactContext, Promise promise) {
        this.mContext = reactContext;
        BluetoothAdapter bluetoothAdapter = getBTAdapter();
        if(bluetoothAdapter == null) {
            promise.reject("No bluetooth adapter available");
            return;
        }
        if(!bluetoothAdapter.isEnabled()) {
            promise.reject("bluetooth adapter is not enabled");
            return;
        }else{
            promise.resolve("done");
        }

    }

    private static BluetoothAdapter getBTAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public List<PrinterDevice> getDeviceList(Promise promise) {
        BluetoothAdapter bluetoothAdapter = getBTAdapter();
        List<PrinterDevice> printerDevices = new ArrayList<>();
        if(bluetoothAdapter == null) {
            promise.reject("No bluetooth adapter available");
            return printerDevices;
        }
        if (!bluetoothAdapter.isEnabled()) {
            promise.reject("bluetooth is not enabled");
            return printerDevices;
        }
        Set<BluetoothDevice> pairedDevices = getBTAdapter().getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            printerDevices.add(new BLEPrinterDevice(device));
        }
        return printerDevices;
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Promise promise) {
        BluetoothAdapter bluetoothAdapter = getBTAdapter();
        if(bluetoothAdapter == null) {
            promise.reject("No bluetooth adapter available");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            promise.reject("bluetooth is not enabled");
            return;
        }
        BLEPrinterDeviceId blePrinterDeviceId = (BLEPrinterDeviceId)printerDeviceId;
        if(this.mBluetoothDevice != null){
            if(this.mBluetoothDevice.getAddress().equals(blePrinterDeviceId.getInnerMacAddress()) && this.mBluetoothSocket != null){
                Log.v(LOG_TAG, "do not need to reconnect");
                promise.resolve(new BLEPrinterDevice(this.mBluetoothDevice).toRNWritableMap());
                return;
            }else{
                closeConnectionIfExists();
            }
        }
        Set<BluetoothDevice> pairedDevices = getBTAdapter().getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            if(device.getAddress().equals(blePrinterDeviceId.getInnerMacAddress())){

                try{
                    connectBluetoothDevice(device);
                    promise.resolve(new BLEPrinterDevice(this.mBluetoothDevice).toRNWritableMap());
                    return;
                }catch (IOException e){
                    e.printStackTrace();
                    promise.reject(e.getMessage());
                    return;
                }
            }
        }
        String errorText = "Can not find the specified printing device, please perform Bluetooth pairing in the system settings first.";
        Toast.makeText(this.mContext, errorText, Toast.LENGTH_LONG).show();
        promise.reject(errorText);
        return;
    }

    private void connectBluetoothDevice(BluetoothDevice device) throws IOException{
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        this.mBluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
        this.mBluetoothSocket.connect();
        this.mBluetoothDevice = device;//最后一步执行

    }

    @Override
    public void closeConnectionIfExists() {
        try{
            if(this.mBluetoothSocket != null){
                this.mBluetoothSocket.close();
                this.mBluetoothSocket = null;
            }
        }catch(IOException e){
            e.printStackTrace();
        }

        if(this.mBluetoothDevice != null) {
            this.mBluetoothDevice = null;
        }
    }

    @Override
    public void printRawData(String rawBase64Data, Promise promise) {
        if(this.mBluetoothSocket == null){
            promise.reject("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }
        final String rawData = rawBase64Data;
        final BluetoothSocket socket = this.mBluetoothSocket;
        Log.v(LOG_TAG, "start to print raw data " + rawBase64Data);
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte [] bytes = Base64.decode(rawData, Base64.DEFAULT);
                try{
                    OutputStream printerOutputStream = socket.getOutputStream();
                    printerOutputStream.write(bytes, 0, bytes.length);
                    printerOutputStream.flush();
                    promise.resolve("done");
                }catch (IOException e){
                    Log.e(LOG_TAG, "failed to print data" + rawData);
                    e.printStackTrace();
                    promise.resolve("failed to print data" + e.getMessage());
                }

            }
        }).start();
    }

    @Override
    public void printImageData(String imageUrl, int  imageWidth, int imageHeight, Promise promise) {
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
//        final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

        if(bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mBluetoothSocket == null) {
            promise.reject("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final BluetoothSocket socket = this.mBluetoothSocket;

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
                printerOutputStream.write(CENTER_ALIGN);
                printerOutputStream.write(new byte[]{(byte)(0x00ff & pixels[y].length)
                        , (byte)((0xff00 & pixels[y].length) >> 8)});
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
        if(bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        if (this.mBluetoothSocket == null) {
            promise.reject("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final BluetoothSocket socket = this.mBluetoothSocket;
        try {
            final boolean useEscAsteriskCommand = false;
            final boolean useGsv0 = true;

            OutputStream printerOutputStream = socket.getOutputStream();

            if(useGsv0 || useEscAsteriskCommand) {
                byte[][] pixels = getEscPosImageBytes(bitmapImage, imageWidth, imageHeight, useEscAsteriskCommand);
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
                  printerOutputStream.write(new byte[]{(byte)(0x00ff & pixels[y].length)
                          , (byte)((0xff00 & pixels[y].length) >> 8)});
                  for (int x = 0; x < pixels[y].length; x++) {
                      // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                      printerOutputStream.write(recollectSlice(y, x, pixels));
                  }

                  // Do a line feed, if not the printing will resume on the same line
                  printerOutputStream.write(LINE_FEED);
              }
              // printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);

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
}
