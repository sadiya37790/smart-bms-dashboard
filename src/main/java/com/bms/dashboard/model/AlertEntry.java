package com.bms.dashboard.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AlertEntry {
    public enum Severity {
        CRITICAL,
        WARNING,
        INFO
    }

    private final String time;
    private final String parameter;
    private final String value;
    private final String threshold;
    private final Severity severity;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public AlertEntry(String parameter, String value, String threshold, Severity severity) {
        this.time = LocalTime.now().format(TIME_FORMATTER);
        this.parameter = parameter;
        this.value = value;
        this.threshold = threshold;
        this.severity = severity;
    }

    public AlertEntry(String time, String parameter, String value, String threshold, Severity severity) {
        this.time = time;
        this.parameter = parameter;
        this.value = value;
        this.threshold = threshold;
        this.severity = severity;
    }

    public String getTime() {
        return time;
    }

    public String getParameter() {
        return parameter;
    }

    public String getValue() {
        return value;
    }

    public String getThreshold() {
        return threshold;
    }

    public Severity getSeverity() {
        return severity;
    }
}
