package com.pinmi.react.printer.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.EscPosPrinterCommands;
import com.dantsu.escposprinter.EscPosPrinterSize;
import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UtilsImage {
    public static Bitmap getBitmapResized(Bitmap image, float decreaseSizeBy, int imageWidth, int imageHeight) {
        int imageWidthForResize = image.getWidth();
        int imageHeightForResize = image.getHeight();
        if (imageWidth > 0) {
            imageWidthForResize = imageWidth;
        }

        if (imageHeight > 0) {
            imageHeightForResize = imageHeight;
        }
        return Bitmap.createScaledBitmap(image, (int) (imageWidthForResize * decreaseSizeBy),
                (int) (imageHeightForResize * decreaseSizeBy), true);
    }

    public static int getRGB(Bitmap bmpOriginal, int col, int row) {
        // get one pixel color
        int pixel = bmpOriginal.getPixel(col, row);
        // retrieve color of all channels
        int R = Color.red(pixel);
        int G = Color.green(pixel);
        int B = Color.blue(pixel);
        return Color.rgb(R, G, B);
    }

    public static Bitmap resizeTheImageForPrinting(Bitmap image, int imageWidth, int imageHeight) {
        // making logo size 150 or less pixels
        int width = image.getWidth();
        int height = image.getHeight();
        if (Integer.toString(imageWidth) != null || Integer.toString(imageHeight) != null) {
            return getBitmapResized(image, 1, imageWidth, imageHeight);
        }
        if (width > 200 || height > 200) {
            float decreaseSizeBy;
            if (width > height) {
                decreaseSizeBy = (200.0f / width);
            } else {
                decreaseSizeBy = (200.0f / height);
            }
            return getBitmapResized(image, decreaseSizeBy, 0, 0);
        }
        return image;
    }

    public static boolean shouldPrintColor(int col) {
        final int threshold = 127;
        int a, r, g, b, luminance;
        a = (col >> 24) & 0xff;
        if (a != 0xff) {// Ignore transparencies
            return false;
        }
        r = (col >> 16) & 0xff;
        g = (col >> 8) & 0xff;
        b = col & 0xff;

        luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

        return luminance < threshold;
    }

    public static byte[] recollectSlice(int y, int x, int[][] img) {
        byte[] slices = new byte[]{0, 0, 0};
        for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
            byte slice = 0;
            for (int b = 0; b < 8; b++) {
                int yyy = yy + b;
                if (yyy >= img.length) {
                    continue;
                }
                int col = img[yyy][x];
                boolean v = shouldPrintColor(col);
                slice |= (byte) ((v ? 1 : 0) << (7 - b));
            }
            slices[i] = slice;
        }
        return slices;
    }

    public static int[][] getPixelsSlow(Bitmap image2, int imageWidth, int imageHeight) {

        Bitmap image = resizeTheImageForPrinting(image2, imageWidth, imageHeight);

        int width = image.getWidth();
        int height = image.getHeight();
        int[][] result = new int[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                result[row][col] = getRGB(image, col, row);
            }
        }
        return result;
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            //TODO use regex
            if (src.contains(".jpg")) {
                myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            } else {
                myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            }

            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    public static EscPosPrinterSize getEscPosPrinterSize() throws EscPosConnectionException {
        DeviceConnection deviceConnection = new DeviceConnection() {
            @Override
            public DeviceConnection connect() throws EscPosConnectionException {
                return null;
            }

            @Override
            public DeviceConnection disconnect() {
                return null;
            }
        };
        EscPosPrinterSize printer = new EscPosPrinter(deviceConnection, 203, 48f, 32);
        return printer;
    }
    public static byte[][] getEscPosImageBytes(Bitmap bitmapImage, int imageWidth, int imageHeight, boolean useEscAsteriskCommand) throws EscPosConnectionException {
        EscPosPrinterSize printer = getEscPosPrinterSize();
        // String hexaDecimalImage = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmapImage);
        // String hexaDecimalImage = PrinterTextParserImg.bytesToHexadecimalString(EscPosPrinterCommands.bitmapToBytes(bitmapImage, gradient));
        // byte[] byteImage = PrinterTextParserImg.hexadecimalStringToBytes(hexaDecimalImage);

        boolean isSizeEdit = false;
        int bitmapWidth = bitmapImage.getWidth(),
                bitmapHeight = bitmapImage.getHeight(),
                maxWidth = imageWidth > 0 ? imageWidth : printer.getPrinterWidthPx(),
                maxHeight = imageHeight;

        if (bitmapWidth > maxWidth) {
            bitmapHeight = Math.round(((float) bitmapHeight) * ((float) maxWidth) / ((float) bitmapWidth));
            bitmapWidth = maxWidth;
            isSizeEdit = true;
        }
        if (maxHeight > 0 && bitmapHeight > maxHeight) {
            bitmapWidth = Math.round(((float) bitmapWidth) * ((float) maxHeight) / ((float) bitmapHeight));
            bitmapHeight = maxHeight;
            isSizeEdit = true;
        }

        if (isSizeEdit) {
            bitmapImage = Bitmap.createScaledBitmap(bitmapImage, bitmapWidth, bitmapHeight, true);
        }        
        boolean gradient = true;
        byte[] byteImage = EscPosPrinterCommands.bitmapToBytes(bitmapImage, gradient);
        int
                byteWidth = ((int) byteImage[4] & 0xFF) + ((int) byteImage[5] & 0xFF) * 256,
                width = byteWidth * 8,
                height = ((int) byteImage[6] & 0xFF) + ((int) byteImage[7] & 0xFF) * 256,
                nbrByteDiff = (int) Math.floor(((float) (printer.getPrinterWidthPx() - width)) / 8f),
                nbrWhiteByteToInsert = 0;

        final int textAlign = 0; // -1 means left, 0 means center, 1 means right
        switch (textAlign) {
            case 0: //PrinterTextParser.TAGS_ALIGN_CENTER:
                nbrWhiteByteToInsert = Math.round(((float) nbrByteDiff) / 2f);
                break;
            case 1: //PrinterTextParser.TAGS_ALIGN_RIGHT:
                nbrWhiteByteToInsert = nbrByteDiff;
                break;
        }

        if (nbrWhiteByteToInsert > 0) {
            int newByteWidth = byteWidth + nbrWhiteByteToInsert;
            byte[] newImage = EscPosPrinterCommands.initGSv0Command(newByteWidth, height);
            for (int i = 0; i < height; i++) {
                System.arraycopy(byteImage, (byteWidth * i + 8), newImage, (newByteWidth * i + nbrWhiteByteToInsert + 8), byteWidth);
            }
            byteImage = newImage;
        }
        // this.length = (int) Math.ceil(((float) byteWidth * 8) / ((float) printer.getPrinterCharSizeWidthPx()));
                
        return useEscAsteriskCommand ? EscPosPrinterCommands.convertGSv0ToEscAsterisk(byteImage) : new byte[][]{byteImage};
    }
}
