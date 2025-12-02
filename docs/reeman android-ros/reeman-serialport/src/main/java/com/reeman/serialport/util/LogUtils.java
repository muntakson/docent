package com.reeman.serialport.util;

import android.os.Environment;
import android.util.Log;

import com.reeman.serialport.BuildConfig;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import timber.log.Timber;

public class LogUtils {

    private static List<String> alreadyUploadFiles = new ArrayList<>();

    public static void uploadLogs(String ip, List<String> pathList) {
        Calendar now = Calendar.getInstance();
        int i = now.get(Calendar.HOUR_OF_DAY);
        for (String path : pathList) {
            File root = new File(Environment.getExternalStorageDirectory() + File.separator + path);
            if (!root.exists()) continue;
            File[] files = root.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (alreadyUploadFiles.contains(file.getAbsolutePath())) continue;
                File tempFile = new File(
                        file.getAbsolutePath().replace(
                                Environment.getExternalStorageDirectory().getAbsolutePath(),
                                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "temp"
                        )
                );
                String parent = tempFile.getParent();
                File parentPath = new File(parent);
                if (!parentPath.exists()) {
                    parentPath.mkdirs();
                }
                try {
                    copyFileUsingStreams(file, tempFile);
                } catch (IOException e) {
                    Timber.tag(BuildConfig.LOG_ROS).w(e, "日志复制失败");
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    continue;
                }
                try {
                    String boundary = "----" + UUID.randomUUID().toString().replaceAll("-", "");
                    URL url = new URL("http://" + ip + "/file_up/power_log");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("Charset", "UTF-8");
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    OutputStream outputStream = conn.getOutputStream();
                    DataOutputStream dos = new DataOutputStream(outputStream);
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + tempFile.getName() + "\"\r\n");
                    dos.writeBytes("Content-Type: file/file\r\n\r\n");

                    FileInputStream fis = new FileInputStream(tempFile);
                    byte[] buffer = new byte[1024];
                    int count = 0;
                    while ((count = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, count);
                    }
                    fis.close();
                    dos.writeBytes("\r\n");

                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"folder\"\r\n\r\n");
                    dos.writeBytes(path + "\r\n");
                    dos.writeBytes("--" + boundary + "--\r\n");
                    dos.flush();
                    dos.close();

                    int resCode = conn.getResponseCode();
                    if (resCode == HttpURLConnection.HTTP_OK) {
                        Log.w("日志", file.getAbsolutePath());
                        String formatDay = TimeUtil.formatDay(new Date());
                        if (file.getName().contains(" ")) {
                            String[] hs = file.getName().split(" ");
                            if (!(hs[0] + ".log").equals(formatDay + ".log")) {
                                file.delete();
                            } else {
                                int h = Integer.parseInt(file.getName().split(" ")[1].replace(".log", ""));
                                if (h != i) {
                                    file.delete();
                                }
                            }
                        } else {
                            if (!file.getName().startsWith(formatDay))
                                file.delete();
                            if (file.getName().startsWith(formatDay) && file.getName().contains(".bak") && !alreadyUploadFiles.contains(file.getAbsolutePath())) {
                                alreadyUploadFiles.add(file.getAbsolutePath());
                            }

                        }
                    } else {
                        Timber.tag(BuildConfig.LOG_ROS).w("日志上传失败 %s %s", tempFile.getAbsolutePath(), conn.getResponseMessage());
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Timber.tag(BuildConfig.LOG_ROS).w(e, "日志上传失败 %s", tempFile.getAbsolutePath());
                } finally {
                    tempFile.delete();
                }
            }
        }
    }

    public static void copyFileUsingStreams(File sourceFile, File targetFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }
}
