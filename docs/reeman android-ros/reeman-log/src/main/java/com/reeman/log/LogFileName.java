package com.reeman.log;

import com.elvishew.xlog.printer.file.naming.FileNameGenerator;

import java.util.Date;

public class LogFileName implements FileNameGenerator {
    public LogFileName() {
    }

    @Override
    public boolean isFileNameChangeable() {
        return true;
    }

    @Override
    public String generateFileName(int logLevel, long timestamp) {
        return TimeUtil.formatDay(new Date()) + ".log";
    }
}
