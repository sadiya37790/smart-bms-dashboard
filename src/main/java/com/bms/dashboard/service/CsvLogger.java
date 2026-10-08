package com.bms.dashboard.service;

import com.bms.dashboard.model.BmsSample;
import com.bms.dashboard.model.DerivedBmsData;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class CsvLogger {
    private File logFile;
    private PrintWriter printWriter;
    private volatile boolean logging = false;
    private final AtomicLong recordCount = new AtomicLong(0);

    public synchronized void startLogging(File targetFile) throws Exception {
        stopLogging();
        this.logFile = targetFile;
        this.recordCount.set(0);

        boolean exists = targetFile.exists() && targetFile.length() > 0;
        this.printWriter = new PrintWriter(new FileWriter(targetFile, true));

        if (!exists) {
            printWriter.println("timestamp,cell1,cell2,cell3,cell4,pack_v,current,temp,soc,power,mode,active_faults");
            printWriter.flush();
        }

        this.logging = true;
    }

    public synchronized void logSample(BmsSample sample, DerivedBmsData derived, String activeFaults) {
        if (!logging || printWriter == null || sample == null || derived == null) return;

        double[] v = sample.getCellVoltages();
        double c1 = (v != null && v.length > 0) ? v[0] : 0.0;
        double c2 = (v != null && v.length > 1) ? v[1] : 0.0;
        double c3 = (v != null && v.length > 2) ? v[2] : 0.0;
        double c4 = (v != null && v.length > 3) ? v[3] : 0.0;

        String formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(sample.getTimestamp() * 1000L));

        String line = String.format("%s,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%.1f,%.1f,%.2f,%s,\"%s\"",
                formattedTime,
                c1, c2, c3, c4,
                derived.getPackVoltage(),
                sample.getCurrent(),
                sample.getTemperature(),
                derived.getSocPercent(),
                derived.getPower(),
                derived.getMode().name(),
                activeFaults != null ? activeFaults : "NONE"
        );

        printWriter.println(line);
        printWriter.flush();
        recordCount.incrementAndGet();
    }

    public synchronized void stopLogging() {
        logging = false;
        if (printWriter != null) {
            printWriter.close();
            printWriter = null;
        }
    }

    public boolean isLogging() {
        return logging;
    }

    public long getRecordCount() {
        return recordCount.get();
    }

    public File getLogFile() {
        return logFile;
    }
}
