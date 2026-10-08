package com.bms.dashboard.service;

import com.bms.dashboard.model.BmsSample;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BmsSimulator {
    private ScheduledExecutorService executor;
    private Consumer<BmsSample> sampleListener;
    private volatile boolean running = false;
    private final Random random = new Random();

    // Battery State variables
    private double baseSoc = 65.0; // %
    private double currentAmps = 1.85; // positive = charge, negative = discharge
    private double temperature = 31.4; // °C
    private int cycleCounter = 0;

    // Fault Injections
    private volatile boolean forceOvercharge = false;
    private volatile boolean forceOverdischarge = false;
    private volatile boolean forceOvercurrent = false;
    private volatile boolean forceOvertemp = false;

    public synchronized void start(Consumer<BmsSample> sampleListener) {
        stop();
        this.sampleListener = sampleListener;
        this.running = true;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BMS-SimulatorThread");
            t.setDaemon(true);
            return t;
        });

        // 2 Hz interval -> 500 ms
        executor.scheduleAtFixedRate(this::generateAndEmitSample, 0, 500, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void generateAndEmitSample() {
        if (!running) return;

        cycleCounter++;

        // Periodic current direction toggling if no fault forced
        if (!forceOvercurrent) {
            int phase = (cycleCounter / 40) % 3; // Every ~20 seconds
            if (phase == 0) {
                currentAmps = 2.10 + (random.nextDouble() * 0.2 - 0.1); // Charging
            } else if (phase == 1) {
                currentAmps = -2.40 + (random.nextDouble() * 0.2 - 0.1); // Discharging
            } else {
                currentAmps = 0.0 + (random.nextDouble() * 0.04 - 0.02); // Idle
            }
        }

        // SoC integration (2600 mAh capacity = 2.6 Ah = 9360 A*s)
        // dt = 0.5 sec -> dAh = current * 0.5 / 3600
        double deltaSoc = (currentAmps * 0.5 / 3600.0) / 2.6 * 100.0;
        baseSoc = Math.max(0.0, Math.min(100.0, baseSoc + deltaSoc));

        // Temperature drift based on current squared (I^2 * R heating)
        if (!forceOvertemp) {
            double targetTemp = 28.0 + Math.abs(currentAmps) * 2.5;
            temperature += (targetTemp - temperature) * 0.05 + (random.nextDouble() * 0.1 - 0.05);
        }

        // Cell voltages derived from SoC curve + cell variations
        // 0% = 3.0V, 50% = 3.7V, 100% = 4.2V
        double nominalCellV;
        if (baseSoc <= 50.0) {
            nominalCellV = 3.0 + (baseSoc / 50.0) * 0.7;
        } else {
            nominalCellV = 3.7 + ((baseSoc - 50.0) / 50.0) * 0.5;
        }

        double[] cellVoltages = new double[4];
        cellVoltages[0] = nominalCellV + 0.012 + (random.nextDouble() * 0.006 - 0.003);
        cellVoltages[1] = nominalCellV - 0.008 + (random.nextDouble() * 0.006 - 0.003);
        cellVoltages[2] = nominalCellV + 0.004 + (random.nextDouble() * 0.006 - 0.003);
        cellVoltages[3] = nominalCellV - 0.005 + (random.nextDouble() * 0.006 - 0.003);

        // Apply Fault Injections if active
        if (forceOvercharge) {
            cellVoltages[3] = 4.28 + (random.nextDouble() * 0.02); // > 4.20V limit
        }
        if (forceOverdischarge) {
            cellVoltages[0] = 2.72 - (random.nextDouble() * 0.02); // < 2.80V limit
        }
        if (forceOvercurrent) {
            currentAmps = -6.20 - (random.nextDouble() * 0.3); // |I| > 5.0A limit
        }
        if (forceOvertemp) {
            temperature = 58.5 + (random.nextDouble() * 0.5); // > 55.0°C limit
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        BmsSample sample = new BmsSample(cellVoltages, currentAmps, Math.round(temperature * 10.0) / 10.0, timestamp);

        if (sampleListener != null) {
            sampleListener.accept(sample);
        }
    }

    public boolean isRunning() {
        return running;
    }

    // Fault Injection Controls
    public boolean isForceOvercharge() { return forceOvercharge; }
    public void setForceOvercharge(boolean forceOvercharge) { this.forceOvercharge = forceOvercharge; }

    public boolean isForceOverdischarge() { return forceOverdischarge; }
    public void setForceOverdischarge(boolean forceOverdischarge) { this.forceOverdischarge = forceOverdischarge; }

    public boolean isForceOvercurrent() { return forceOvercurrent; }
    public void setForceOvercurrent(boolean forceOvercurrent) { this.forceOvercurrent = forceOvercurrent; }

    public boolean isForceOvertemp() { return forceOvertemp; }
    public void setForceOvertemp(boolean forceOvertemp) { this.forceOvertemp = forceOvertemp; }
}
