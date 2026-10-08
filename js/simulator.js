/**
 * Smart BMS Telemetry Simulator Engine with Fault Injections
 */

import { BmsSample } from './model.js';

export class BmsSimulator {
    constructor(onSampleCallback = null) {
        this.onSampleCallback = onSampleCallback;
        this.timerId = null;
        this.running = false;

        // Battery State Variables
        this.baseSoc = 65.0; // %
        this.currentAmps = 1.85; // positive = charge, negative = discharge
        this.temperature = 31.4; // °C
        this.cycleCounter = 0;

        // Fault Injection Switches
        this.forceOvercharge = false;
        this.forceOverdischarge = false;
        this.forceOvercurrent = false;
        this.forceOvertemp = false;

        // Dynamic Hardware Rating Payload simulation
        this.customHardwareConfig = null;
    }

    start(onSampleCallback = null) {
        this.stop();
        if (onSampleCallback) this.onSampleCallback = onSampleCallback;
        this.running = true;

        // 2 Hz interval = 500 ms
        this.timerId = setInterval(() => this.generateAndEmitSample(), 500);
    }

    stop() {
        this.running = false;
        if (this.timerId) {
            clearInterval(this.timerId);
            this.timerId = null;
        }
    }

    generateAndEmitSample() {
        if (!this.running) return;

        this.cycleCounter++;

        // Periodic current cycling if no fault forced
        if (!this.forceOvercurrent) {
            const phase = Math.floor(this.cycleCounter / 40) % 3; // Every ~20 seconds
            if (phase === 0) {
                this.currentAmps = 2.10 + (Math.random() * 0.2 - 0.1); // Charging
            } else if (phase === 1) {
                this.currentAmps = -2.40 + (Math.random() * 0.2 - 0.1); // Discharging
            } else {
                this.currentAmps = 0.0 + (Math.random() * 0.04 - 0.02); // Idle
            }
        }

        // SoC integration (2600 mAh capacity = 2.6 Ah)
        const deltaSoc = ((this.currentAmps * 0.5) / 3600.0) / 2.6 * 100.0;
        this.baseSoc = Math.max(0.0, Math.min(100.0, this.baseSoc + deltaSoc));

        // Temperature drift under current load
        if (!this.forceOvertemp) {
            const targetTemp = 28.0 + Math.abs(this.currentAmps) * 2.5;
            this.temperature += (targetTemp - this.temperature) * 0.05 + (Math.random() * 0.1 - 0.05);
        }

        // Nominal cell voltage curve from SoC
        let nominalCellV;
        if (this.baseSoc <= 50.0) {
            nominalCellV = 3.0 + (this.baseSoc / 50.0) * 0.7;
        } else {
            nominalCellV = 3.7 + ((this.baseSoc - 50.0) / 50.0) * 0.5;
        }

        const cellVoltages = [
            nominalCellV + 0.012 + (Math.random() * 0.006 - 0.003),
            nominalCellV - 0.008 + (Math.random() * 0.006 - 0.003),
            nominalCellV + 0.004 + (Math.random() * 0.006 - 0.003),
            nominalCellV - 0.005 + (Math.random() * 0.006 - 0.003)
        ];

        // Apply Fault Injections if active
        if (this.forceOvercharge) {
            cellVoltages[3] = 4.28 + (Math.random() * 0.02); // > 4.20V limit
        }
        if (this.forceOverdischarge) {
            cellVoltages[0] = 2.72 - (Math.random() * 0.02); // < 2.80V limit
        }
        if (this.forceOvercurrent) {
            this.currentAmps = -6.20 - (Math.random() * 0.3); // |I| > 5.0A limit
        }
        if (this.forceOvertemp) {
            this.temperature = 58.5 + (Math.random() * 0.5); // > 55.0°C limit
        }

        const sample = new BmsSample(
            cellVoltages.map(v => Math.round(v * 1000) / 1000),
            Math.round(this.currentAmps * 100) / 100,
            Math.round(this.temperature * 10) / 10,
            Math.floor(Date.now() / 1000),
            this.customHardwareConfig
        );

        if (this.onSampleCallback) {
            this.onSampleCallback(sample);
        }
    }
}
