package com.reeman.serialport.controller;

import android.os.Environment;

import com.reeman.serialport.BuildConfig;
import com.reeman.serialport.util.TimeUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class PowerBoardReceiver {
    private static PowerBoardReceiver INSTANCE;
    private SerialPortParser parser;

    public static PowerBoardReceiver getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PowerBoardReceiver();
        }
        return INSTANCE;
    }

    public PowerBoardReceiver() {
        File file = new File(Environment.getExternalStorageDirectory() + File.separator+BuildConfig.LOG_POWER_BOARD);
        if (!file.exists() || !file.isDirectory()) {
            file.mkdir();
        } else {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    if (shouldClean(files[i])) {
                        files[i].delete();
                    }
                }
            }
        }
    }

    public void start() throws Exception {
        parser = new SerialPortParser(new File("/dev/ttyS0"), 115200, this::writeToLocal);
        parser.start();
    }

    public void stop() {
        if (parser != null) {
            parser.stop();
            parser = null;
        }
        INSTANCE = null;
    }

    private void writeToLocal(byte[] data, int len) {
        FileOutputStream downloadFile = null;
        try {
            downloadFile = new FileOutputStream(new File(Environment.getExternalStorageDirectory() + File.separator+BuildConfig.LOG_POWER_BOARD+File.separator + TimeUtil.formatDay(new Date()) + ".log"), true);
            downloadFile.write(data, 0, len);
            downloadFile.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (downloadFile != null)
                    downloadFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
//            String strRead = new String(data, StandardCharsets.UTF_8);
//            strRead = String.copyValueOf(strRead.toCharArray(), 0, len);
//            Timber.tag(BuildConfig.LOG_ROS).e("power board : %s", strRead);

        }

    }

    private boolean shouldClean(File file) {
        long currentTimeMillis = System.currentTimeMillis();
        long lastModified = file.lastModified();
        return (currentTimeMillis - lastModified > 3 * 24 * 60 * 1000);
    }
}
