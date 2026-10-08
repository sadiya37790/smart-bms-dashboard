package com.bms.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class ProtectionSettings {
    private double overchargeThreshold = 4.20;     // Volts per cell
    private double overdischargeThreshold = 2.80;  // Volts per cell
    private double overcurrentThreshold = 5.00;     // Amperes (absolute)
    private double overtempThreshold = 55.00;       // Celsius

    // Latched trip states
    private boolean overchargeTripped = false;
    private boolean overdischargeTripped = false;
    private boolean overcurrentTripped = false;
    private boolean overtempTripped = false;

    // Instantaneous trip states (for UI lamp display)
    private boolean instantOvercharge = false;
    private boolean instantOverdischarge = false;
    private boolean instantOvercurrent = false;
    private boolean instantOvertemp = false;

    public ProtectionSettings() {}

    public synchronized List<AlertEntry> evaluate(BmsSample sample) {
        List<AlertEntry> alertsGenerated = new ArrayList<>();
        if (sample == null || sample.getCellVoltages() == null) {
            return alertsGenerated;
        }

        // 1. Overcharge check
        double maxCell = Double.MIN_VALUE;
        double minCell = Double.MAX_VALUE;
        for (double v : sample.getCellVoltages()) {
            if (v > maxCell) maxCell = v;
            if (v < minCell) minCell = v;
        }

        instantOvercharge = maxCell > overchargeThreshold;
        if (instantOvercharge && !overchargeTripped) {
            overchargeTripped = true;
            alertsGenerated.add(new AlertEntry(
                    "Overcharge Protection",
                    String.format("%.2f V", maxCell),
                    String.format("> %.2f V", overchargeThreshold),
                    AlertEntry.Severity.CRITICAL
            ));
        }

        // 2. Over-discharge check
        instantOverdischarge = minCell < overdischargeThreshold;
        if (instantOverdischarge && !overdischargeTripped) {
            overdischargeTripped = true;
            alertsGenerated.add(new AlertEntry(
                    "Over-discharge Protection",
                    String.format("%.2f V", minCell),
                    String.format("< %.2f V", overdischargeThreshold),
                    AlertEntry.Severity.CRITICAL
            ));
        }

        // 3. Overcurrent check
        double absCurrent = Math.abs(sample.getCurrent());
        instantOvercurrent = absCurrent > overcurrentThreshold;
        if (instantOvercurrent && !overcurrentTripped) {
            overcurrentTripped = true;
            alertsGenerated.add(new AlertEntry(
                    "Overcurrent Protection",
                    String.format("%.2f A", sample.getCurrent()),
                    String.format("> %.2f A", overcurrentThreshold),
                    AlertEntry.Severity.CRITICAL
            ));
        }

        // 4. Over-temperature check
        instantOvertemp = sample.getTemperature() > overtempThreshold;
        if (instantOvertemp && !overtempTripped) {
            overtempTripped = true;
            alertsGenerated.add(new AlertEntry(
                    "Over-temperature Protection",
                    String.format("%.1f °C", sample.getTemperature()),
                    String.format("> %.1f °C", overtempThreshold),
                    AlertEntry.Severity.CRITICAL
            ));
        }

        return alertsGenerated;
    }

    /**
     * Clears latched faults ONLY if the corresponding parameter is currently back in safe range.
     * Returns list of cleared alert logs.
     */
    public synchronized List<AlertEntry> resetFaults(BmsSample currentSample) {
        List<AlertEntry> clearedAlerts = new ArrayList<>();
        if (currentSample == null) {
            return clearedAlerts;
        }

        double maxCell = Double.MIN_VALUE;
        double minCell = Double.MAX_VALUE;
        if (currentSample.getCellVoltages() != null) {
            for (double v : currentSample.getCellVoltages()) {
                if (v > maxCell) maxCell = v;
                if (v < minCell) minCell = v;
            }
        }

        if (overchargeTripped && maxCell <= overchargeThreshold) {
            overchargeTripped = false;
            clearedAlerts.add(new AlertEntry("Overcharge Protection", String.format("%.2f V", maxCell), "NORMAL", AlertEntry.Severity.INFO));
        }
        if (overdischargeTripped && minCell >= overdischargeThreshold) {
            overdischargeTripped = false;
            clearedAlerts.add(new AlertEntry("Over-discharge Protection", String.format("%.2f V", minCell), "NORMAL", AlertEntry.Severity.INFO));
        }
        if (overcurrentTripped && Math.abs(currentSample.getCurrent()) <= overcurrentThreshold) {
            overcurrentTripped = false;
            clearedAlerts.add(new AlertEntry("Overcurrent Protection", String.format("%.2f A", currentSample.getCurrent()), "NORMAL", AlertEntry.Severity.INFO));
        }
        if (overtempTripped && currentSample.getTemperature() <= overtempThreshold) {
            overtempTripped = false;
            clearedAlerts.add(new AlertEntry("Over-temperature Protection", String.format("%.1f °C", currentSample.getTemperature()), "NORMAL", AlertEntry.Severity.INFO));
        }

        return clearedAlerts;
    }

    public synchronized void restoreDefaults() {
        this.overchargeThreshold = 4.20;
        this.overdischargeThreshold = 2.80;
        this.overcurrentThreshold = 5.00;
        this.overtempThreshold = 55.00;
    }

    public synchronized boolean isChargeFetOn() {
        return !overchargeTripped && !overcurrentTripped && !overtempTripped;
    }

    public synchronized boolean isDischargeFetOn() {
        return !overdischargeTripped && !overcurrentTripped && !overtempTripped;
    }

    public synchronized boolean hasAnyFault() {
        return overchargeTripped || overdischargeTripped || overcurrentTripped || overtempTripped;
    }

    // Getters and Setters
    public synchronized double getOverchargeThreshold() { return overchargeThreshold; }
    public synchronized void setOverchargeThreshold(double overchargeThreshold) { this.overchargeThreshold = overchargeThreshold; }

    public synchronized double getOverdischargeThreshold() { return overdischargeThreshold; }
    public synchronized void setOverdischargeThreshold(double overdischargeThreshold) { this.overdischargeThreshold = overdischargeThreshold; }

    public synchronized double getOvercurrentThreshold() { return overcurrentThreshold; }
    public synchronized void setOvercurrentThreshold(double overcurrentThreshold) { this.overcurrentThreshold = overcurrentThreshold; }

    public synchronized double getOvertempThreshold() { return overtempThreshold; }
    public synchronized void setOvertempThreshold(double overtempThreshold) { this.overtempThreshold = overtempThreshold; }

    public synchronized boolean isOverchargeTripped() { return overchargeTripped; }
    public synchronized boolean isOverdischargeTripped() { return overdischargeTripped; }
    public synchronized boolean isOvercurrentTripped() { return overcurrentTripped; }
    public synchronized boolean isOvertempTripped() { return overtempTripped; }

    public synchronized boolean isInstantOvercharge() { return instantOvercharge; }
    public synchronized boolean isInstantOverdischarge() { return instantOverdischarge; }
    public synchronized boolean isInstantOvercurrent() { return instantOvercurrent; }
    public synchronized boolean isInstantOvertemp() { return instantOvertemp; }
}
