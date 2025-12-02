package com.reeman.serialport.controller;


import android.os.SystemClock;

import com.aill.androidserialport.SerialPort;
import com.reeman.serialport.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import timber.log.Timber;

public class SerialPortParser {

    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Thread thread;
    private byte[] bytes;
    private volatile boolean stopped = false;
    private final SerialPort serialPort;

    public SerialPortParser(File file, int baudRate, OnDataResultListener listener) throws Exception {
        serialPort = new SerialPort(file, baudRate, 0);
        inputStream = serialPort.getInputStream();
        outputStream = serialPort.getOutputStream();
        this.listener = listener;
        bytes = new byte[1024];
        thread = new Thread(new ReadRunnable(), "serial-port-read-thread1");
    }


    public void start() {
        thread.start();
    }


    public void stop() {
        stopped = true;
        listener = null;
        if (serialPort != null) {
            serialPort.tryClose();
        }
    }

    public void sendCommand(byte[] bytes) throws IOException {
        if (this.outputStream != null) {
            this.outputStream.write(bytes);
        }
    }

    private OnDataResultListener listener;


    public interface OnDataResultListener {
        void onDataResult(byte[] bytes, int len);

    }

    private class ReadRunnable implements Runnable {

        @Override
        public void run() {
            while (!stopped) {
                try {
                    if (inputStream.available() <= 0) {
                        SystemClock.sleep(10);
                        continue;
                    }
                    int len;
                    if ((len = inputStream.read(bytes)) > 0) {
                        if (listener != null) {
                            listener.onDataResult(bytes, len);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Timber.tag(BuildConfig.LOG_ROS).w("read thread finish");
            bytes = null;
        }
    }

}
