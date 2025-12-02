package com.reeman.serialport.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {
    public static String formatDay(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return formatter.format(date);
    }

    public static String formatHour(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH", Locale.getDefault());
        return formatter.format(date);
    }
}
