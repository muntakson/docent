package com.reeman.serialport.controller;

import android.text.TextUtils;

import com.reeman.serialport.BuildConfig;
import com.reeman.serialport.util.Parser;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class RosCallbackParser {
    private final String port;
    private final int baudRate;
    private final RosCallback callback;
    private final Pattern pattern = Pattern.compile("AA(54|56)");
    private final StringBuilder sb = new StringBuilder();
    private SerialPortParser parser;
    private final ConcurrentLinkedQueue<String> receiveLinkedQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<String> sendLinkedQueue = new ConcurrentLinkedQueue<>();
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);

    public RosCallbackParser(String port, int baudRate, RosCallback callback) {
        this.port = port;
        this.baudRate = baudRate;
        this.callback = callback;
    }

    public void startListen() throws Exception {
        scheduledExecutorService.scheduleWithFixedDelay(resultRunnable, 10, 10, TimeUnit.MILLISECONDS);
        scheduledExecutorService.scheduleWithFixedDelay(sendRunnable, 50, 50, TimeUnit.MILLISECONDS);
        parser = new SerialPortParser(new File(this.port), this.baudRate, (bytes, len) -> {
            sb.append(Parser.byteArrToHex(bytes, len));
            while (sb.length() != 0) {
                if (sb.length() == 2 && "AA".equals(sb.toString())) break;
                if (sb.length() < 4) break;
                Matcher matcher = pattern.matcher(sb);
                if (matcher.find()) {
                    try {
                        int start = matcher.start();
                        int startIndex = start + 4;
                        if (startIndex + 2 >= sb.length()) break;
                        String dataSize = sb.substring(startIndex, startIndex + 2);
                        int intSize = Integer.parseInt(dataSize, 16);
                        int dataLastIndex = startIndex + intSize * 2 + 2;
                        if (dataLastIndex + 2 > sb.length())
                            break;
                        String dataHexSum = sb.substring(startIndex, dataLastIndex);
                        String checkSum = sb.substring(dataLastIndex, dataLastIndex + 2);
                        if (checkSum.equals(Parser.checkXor(dataHexSum))) {
                            String dataPackage = sb.substring(start, dataLastIndex) + checkSum;
                            if (dataPackage.startsWith("AA54")) {
                                if (callback != null)
                                    receiveLinkedQueue.offer(Parser.hexStringToString(sb.substring(startIndex + 2, dataLastIndex)));
                            }
                            sb.delete(0, dataLastIndex + 2);

                        } else if (matcher.find()) {
                            Timber.tag(BuildConfig.LOG_ROS).w("导航数据包校验不通过1%s", sb);
                            sb.delete(0, matcher.start());
                        } else {
                            Timber.tag(BuildConfig.LOG_ROS).w("导航数据包校验不通过2%s", sb);
                            sb.delete(0, sb.length());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Timber.tag(BuildConfig.LOG_ROS).w(e, "解析出错%s", sb);
                        sb.delete(0, sb.length());
                    }
                } else {
                    sb.delete(0, sb.length());
                }
            }
        });
        parser.start();
    }

    public void stopListen() {
        if (scheduledExecutorService != null) {
            try {
                scheduledExecutorService.shutdownNow();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            scheduledExecutorService = null;
        }
        if (parser != null) {
            parser.stop();
            parser = null;
        }
    }

    public void sendCommand(String cmd) {
        try {
            parser.sendCommand(Parser.string2BH(cmd));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendCommandToQueue(String cmd) {
        sendLinkedQueue.offer(cmd);
    }

    public static class Builder {
        private String port;
        private int baudRate;
        private RosCallback callback;

        public Builder port(String port) {
            this.port = port;
            return this;
        }

        public Builder baudRate(int baudRate) {
            this.baudRate = baudRate;
            return this;
        }

        public Builder callback(RosCallback callback) {
            this.callback = callback;
            return this;
        }

        public RosCallbackParser build() {
            return new RosCallbackParser(port, baudRate, callback);
        }
    }

    public interface RosCallback {
        void onResult(String result);
    }

    Runnable sendRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sendLinkedQueue.isEmpty()) {
                String poll = sendLinkedQueue.poll();
                if (poll != null && !TextUtils.isEmpty(poll)) {
                    sendCommand(poll);
                }
            }
        }
    };

    Runnable resultRunnable = new Runnable() {
        @Override
        public void run() {
            if (callback == null) return;
            try {
                String result = receiveLinkedQueue.poll();
                if (result != null && !TextUtils.isEmpty(result)) {
                    if (receiveLinkedQueue.size() > 10) {
                        Timber.tag(BuildConfig.LOG_ROS).e("队列数据量过大 : %s", receiveLinkedQueue);
                    }
                    callback.onResult(result);
                }
            } catch (Exception e) {

            }
        }
    };
}
