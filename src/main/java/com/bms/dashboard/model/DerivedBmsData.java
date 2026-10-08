package com.bms.dashboard.model;

public class DerivedBmsData {
    public enum OperationalMode {
        CHARGING,
        DISCHARGING,
        IDLE,
        FAULT
    }

    private final double packVoltage;
    private final double power;
    private final double socPercent;
    private final double maxCellVoltage;
    private final double minCellVoltage;
    private final double cellDelta; // max - min in Volts
    private final OperationalMode mode;

    public DerivedBmsData(BmsSample sample, boolean hasActiveFault) {
        double[] voltages = sample.getCellVoltages();
        double sum = 0.0;
        double maxV = Double.MIN_VALUE;
        double minV = Double.MAX_VALUE;

        if (voltages != null && voltages.length > 0) {
            for (double v : voltages) {
                sum += v;
                if (v > maxV) maxV = v;
                if (v < minV) minV = v;
            }
        } else {
            sum = 14.8;
            maxV = 3.7;
            minV = 3.7;
        }

        this.packVoltage = sum;
        this.power = sum * sample.getCurrent();
        this.maxCellVoltage = maxV;
        this.minCellVoltage = minV;
        this.cellDelta = maxV - minV;

        double avgCellV = (voltages != null && voltages.length > 0) ? sum / voltages.length : 3.7;
        this.socPercent = calculateSoc(avgCellV);

        if (hasActiveFault) {
            this.mode = OperationalMode.FAULT;
        } else if (sample.getCurrent() > 0.05) {
            this.mode = OperationalMode.CHARGING;
        } else if (sample.getCurrent() < -0.05) {
            this.mode = OperationalMode.DISCHARGING;
        } else {
            this.mode = OperationalMode.IDLE;
        }
    }

    /**
     * SoC % calculation from average cell voltage via lookup table interpolation:
     * 3.0V = 0%, 3.7V = 50%, 4.2V = 100%
     */
    public static double calculateSoc(double cellVoltage) {
        if (cellVoltage <= 3.0) {
            return 0.0;
        } else if (cellVoltage <= 3.7) {
            return (cellVoltage - 3.0) / (3.7 - 3.0) * 50.0;
        } else if (cellVoltage <= 4.2) {
            return 50.0 + (cellVoltage - 3.7) / (4.2 - 3.7) * 50.0;
        } else {
            return 100.0;
        }
    }

    public double getPackVoltage() {
        return packVoltage;
    }

    public double getPower() {
        return power;
    }

    public double getSocPercent() {
        return socPercent;
    }

    public double getMaxCellVoltage() {
        return maxCellVoltage;
    }

    public double getMinCellVoltage() {
        return minCellVoltage;
    }

    public double getCellDelta() {
        return cellDelta;
    }

    public OperationalMode getMode() {
        return mode;
    }
}
