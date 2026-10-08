package com.bms.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BmsSample {
    @JsonProperty("v")
    private double[] cellVoltages;

    @JsonProperty("i")
    private double current;

    @JsonProperty("t")
    private double temperature;

    @JsonProperty("ts")
    private long timestamp;

    public BmsSample() {
        this.cellVoltages = new double[]{3.70, 3.70, 3.70, 3.70};
        this.current = 0.0;
        this.temperature = 25.0;
        this.timestamp = System.currentTimeMillis() / 1000L;
    }

    public BmsSample(double[] cellVoltages, double current, double temperature, long timestamp) {
        this.cellVoltages = cellVoltages != null ? cellVoltages : new double[]{3.70, 3.70, 3.70, 3.70};
        this.current = current;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public double[] getCellVoltages() {
        return cellVoltages;
    }

    public void setCellVoltages(double[] cellVoltages) {
        this.cellVoltages = cellVoltages;
    }

    public double getCurrent() {
        return current;
    }

    public void setCurrent(double current) {
        this.current = current;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "BmsSample{" +
                "v=" + Arrays.toString(cellVoltages) +
                ", i=" + current +
                ", t=" + temperature +
                ", ts=" + timestamp +
                '}';
    }
}
