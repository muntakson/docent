package com.reeman.log;

import android.text.TextUtils;

import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.ClassicFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy2;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public class FileLoggingTree extends Timber.Tree {

    private final String defaultTAG;

    private final int logLevel;

    private final boolean withAndroidPrinter;
    private final Map<String, Printer> printerMap = new HashMap<>();

    private final Printer androidPrinter = new AndroidPrinter(true);

    private Set<String> blockedTags;

    private Set<String> blackedMessages;


    public FileLoggingTree(int priority, boolean withAndroidPrinter, String rootPath, String tag, List<String> pathList) {
        this.logLevel = priority;
        this.defaultTAG = tag;
        this.withAndroidPrinter = withAndroidPrinter;
        for (String path : pathList) {
            printerMap.put(path, new FilePrinter
                    .Builder(rootPath + File.separator + path)
                    .fileNameGenerator(new LogFileName())
                    .backupStrategy(new FileSizeBackupStrategy2(1024 * 1024 * 5, 100))
                    .flattener(new ClassicFlattener())
                    .cleanStrategy(new FileLastModifiedCleanStrategy(7 * 24 * 60 * 60 * 1000))
                    .build());
        }
    }

    public FileLoggingTree(int priority, boolean withAndroidPrinter, String tag, Map<String, Printer> printerMap) {
        this.logLevel = priority;
        this.defaultTAG = tag;
        this.withAndroidPrinter = withAndroidPrinter;
        this.printerMap.putAll(printerMap);

    }

    public void setBlockedTags(Set<String> blockedTags) {
        this.blockedTags = blockedTags;
    }

    public void setBlackedMessages(Set<String> blackedMessages) {
        this.blackedMessages = blackedMessages;
    }

    @Override
    protected void log(int priority, String tag, @NotNull String message, Throwable t) {
        if (tag == null) {
            tag = defaultTAG;
        }
        if (priority < logLevel) {
            return;
        }
        if (!shouldLog(tag, message)) return;
        Printer finalFilePrinter = printerMap.get(printerMap.containsKey(tag) ? tag : defaultTAG);
        Printer[] printers;
        if (withAndroidPrinter) {
            printers = new Printer[]{androidPrinter, finalFilePrinter};
        } else {
            printers = new Printer[]{finalFilePrinter};
        }
        if (t == null) {
            XLog.tag(tag).printers(printers).log(priority, message);
        } else {
            XLog.tag(tag).printers(printers).log(priority, message, t);
        }
    }

    private boolean shouldLog(String tag, String message) {
        if (blockedTags != null && blockedTags.contains(tag)) return false;
        if (blackedMessages != null && !TextUtils.isEmpty(message)) {
            for (String blackedMessage : blackedMessages) {
                if (message.contains(blackedMessage)) return false;
            }
        }
        return true;
    }
}
