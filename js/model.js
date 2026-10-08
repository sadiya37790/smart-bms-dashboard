/**
 * Smart BMS Model & Protection Logic Engine
 * Includes Dynamic Hardware Battery Rating & Cutoff Auto-Sync
 */

export class BmsSample {
    constructor(cellVoltages = [3.70, 3.70, 3.70, 3.70], current = 0.0, temperature = 25.0, timestamp = null, hardwareConfig = null) {
        this.v = cellVoltages && cellVoltages.length > 0 ? cellVoltages : [3.70, 3.70, 3.70, 3.70];
        this.i = current;
        this.t = temperature;
        this.ts = timestamp || Math.floor(Date.now() / 1000);
        this.cfg = hardwareConfig || null; // Optional dynamic rating sync payload from ESP32
    }

    static parseJson(jsonString) {
        try {
            const data = JSON.parse(jsonString);
            if (!data || !Array.isArray(data.v)) {
                return null;
            }
            return new BmsSample(data.v, data.i || 0, data.t || 25.0, data.ts, data.cfg || null);
        } catch (e) {
            console.warn('[BMS Data Model] Invalid JSON packet skipped:', jsonString, e);
            return null;
        }
    }
}

export class DerivedBmsData {
    constructor(sample, hasActiveFault, settings) {
        const voltages = sample.v || [3.70, 3.70, 3.70, 3.70];
        let sum = 0;
        let maxV = Number.MIN_VALUE;
        let minV = Number.MAX_VALUE;

        for (let v of voltages) {
            sum += v;
            if (v > maxV) maxV = v;
            if (v < minV) minV = v;
        }

        this.packVoltage = sum;
        this.power = sum * sample.i;
        this.maxCellVoltage = maxV;
        this.minCellVoltage = minV;
        this.cellDelta = maxV - minV; // in Volts

        const avgCellV = voltages.length > 0 ? sum / voltages.length : 3.7;
        this.socPercent = DerivedBmsData.calculateSoc(avgCellV, settings ? settings.overdischargeThreshold : 2.80, settings ? settings.overchargeThreshold : 4.20);

        if (hasActiveFault) {
            this.mode = 'FAULT';
        } else if (sample.i > 0.05) {
            this.mode = 'CHARGING';
        } else if (sample.i < -0.05) {
            this.mode = 'DISCHARGING';
        } else {
            this.mode = 'IDLE';
        }
    }

    /**
     * SoC % Interpolation Lookup Curve:
     * Min Limit (2.8V-3.0V) = 0%, Nominal (3.7V) = 50%, Max Limit (4.2V-4.25V) = 100%
     */
    static calculateSoc(cellV, minCutoff = 2.80, maxCutoff = 4.20) {
        const minV = Math.max(2.5, minCutoff);
        const midV = (minV + maxCutoff) / 2.0;

        if (cellV <= minV) {
            return 0.0;
        } else if (cellV <= midV) {
            return ((cellV - minV) / (midV - minV)) * 50.0;
        } else if (cellV <= maxCutoff) {
            return 50.0 + ((cellV - midV) / (maxCutoff - midV)) * 50.0;
        } else {
            return 100.0;
        }
    }
}

export class ProtectionSettings {
    constructor(onConfigAutoSynced = null) {
        this.overchargeThreshold = 4.20;     // V/cell
        this.overdischargeThreshold = 2.80;  // V/cell
        this.overcurrentThreshold = 5.00;     // A
        this.overtempThreshold = 55.0;        // °C

        // Latched fault states
        this.overchargeTripped = false;
        this.overdischargeTripped = false;
        this.overcurrentTripped = false;
        this.overtempTripped = false;

        // Instantaneous conditions
        this.instantOvercharge = false;
        this.instantOverdischarge = false;
        this.instantOvercurrent = false;
        this.instantOvertemp = false;

        this.onConfigAutoSynced = onConfigAutoSynced;
        this.lastSyncedCfgStr = '';
    }

    /**
     * Auto-syncs battery ratings and protection cutoffs from incoming ESP32 payload
     * payload format: {"max_v": 4.25, "min_v": 2.75, "max_i": 6.0, "max_t": 60.0}
     */
    syncHardwareConfig(cfgPayload) {
        if (!cfgPayload) return false;

        const cfgStr = JSON.stringify(cfgPayload);
        if (cfgStr === this.lastSyncedCfgStr) return false;

        let changed = false;
        if (typeof cfgPayload.max_v === 'number' && cfgPayload.max_v !== this.overchargeThreshold) {
            this.overchargeThreshold = cfgPayload.max_v;
            changed = true;
        }
        if (typeof cfgPayload.min_v === 'number' && cfgPayload.min_v !== this.overdischargeThreshold) {
            this.overdischargeThreshold = cfgPayload.min_v;
            changed = true;
        }
        if (typeof cfgPayload.max_i === 'number' && cfgPayload.max_i !== this.overcurrentThreshold) {
            this.overcurrentThreshold = cfgPayload.max_i;
            changed = true;
        }
        if (typeof cfgPayload.max_t === 'number' && cfgPayload.max_t !== this.overtempThreshold) {
            this.overtempThreshold = cfgPayload.max_t;
            changed = true;
        }

        this.lastSyncedCfgStr = cfgStr;

        if (changed && this.onConfigAutoSynced) {
            this.onConfigAutoSynced(cfgPayload);
        }
        return changed;
    }

    evaluate(sample) {
        const alerts = [];
        if (!sample || !sample.v) return alerts;

        // Check dynamic payload sync first
        if (sample.cfg) {
            this.syncHardwareConfig(sample.cfg);
        }

        let maxCell = Number.MIN_VALUE;
        let minCell = Number.MAX_VALUE;
        for (let v of sample.v) {
            if (v > maxCell) maxCell = v;
            if (v < minCell) minCell = v;
        }

        // 1. Overcharge check
        this.instantOvercharge = maxCell > this.overchargeThreshold;
        if (this.instantOvercharge && !this.overchargeTripped) {
            this.overchargeTripped = true;
            alerts.push({
                parameter: 'Overcharge Protection',
                value: `${maxCell.toFixed(2)} V`,
                threshold: `> ${this.overchargeThreshold.toFixed(2)} V`,
                severity: 'CRITICAL'
            });
        }

        // 2. Over-discharge check
        this.instantOverdischarge = minCell < this.overdischargeThreshold;
        if (this.instantOverdischarge && !this.overdischargeTripped) {
            this.overdischargeTripped = true;
            alerts.push({
                parameter: 'Over-discharge Protection',
                value: `${minCell.toFixed(2)} V`,
                threshold: `< ${this.overdischargeThreshold.toFixed(2)} V`,
                severity: 'CRITICAL'
            });
        }

        // 3. Overcurrent check
        const absI = Math.abs(sample.i);
        this.instantOvercurrent = absI > this.overcurrentThreshold;
        if (this.instantOvercurrent && !this.overcurrentTripped) {
            this.overcurrentTripped = true;
            alerts.push({
                parameter: 'Overcurrent Protection',
                value: `${sample.i.toFixed(2)} A`,
                threshold: `> ${this.overcurrentThreshold.toFixed(2)} A`,
                severity: 'CRITICAL'
            });
        }

        // 4. Over-temperature check
        this.instantOvertemp = sample.t > this.overtempThreshold;
        if (this.instantOvertemp && !this.overtempTripped) {
            this.overtempTripped = true;
            alerts.push({
                parameter: 'Over-temperature Protection',
                value: `${sample.t.toFixed(1)} °C`,
                threshold: `> ${this.overtempThreshold.toFixed(1)} °C`,
                severity: 'CRITICAL'
            });
        }

        return alerts;
    }

    resetFaults(sample) {
        const cleared = [];
        if (!sample || !sample.v) return cleared;

        let maxCell = Number.MIN_VALUE;
        let minCell = Number.MAX_VALUE;
        for (let v of sample.v) {
            if (v > maxCell) maxCell = v;
            if (v < minCell) minCell = v;
        }

        if (this.overchargeTripped && maxCell <= this.overchargeThreshold) {
            this.overchargeTripped = false;
            cleared.push({ parameter: 'Overcharge Protection', value: `${maxCell.toFixed(2)} V`, threshold: 'NORMAL', severity: 'INFO' });
        }
        if (this.overdischargeTripped && minCell >= this.overdischargeThreshold) {
            this.overdischargeTripped = false;
            cleared.push({ parameter: 'Over-discharge Protection', value: `${minCell.toFixed(2)} V`, threshold: 'NORMAL', severity: 'INFO' });
        }
        if (this.overcurrentTripped && Math.abs(sample.i) <= this.overcurrentThreshold) {
            this.overcurrentTripped = false;
            cleared.push({ parameter: 'Overcurrent Protection', value: `${sample.i.toFixed(2)} A`, threshold: 'NORMAL', severity: 'INFO' });
        }
        if (this.overtempTripped && sample.t <= this.overtempThreshold) {
            this.overtempTripped = false;
            cleared.push({ parameter: 'Over-temperature Protection', value: `${sample.t.toFixed(1)} °C`, threshold: 'NORMAL', severity: 'INFO' });
        }

        return cleared;
    }

    restoreDefaults() {
        this.overchargeThreshold = 4.20;
        this.overdischargeThreshold = 2.80;
        this.overcurrentThreshold = 5.00;
        this.overtempThreshold = 55.0;
        this.lastSyncedCfgStr = '';
    }

    isChargeFetOn() {
        return !this.overchargeTripped && !this.overcurrentTripped && !this.overtempTripped;
    }

    isDischargeFetOn() {
        return !this.overdischargeTripped && !this.overcurrentTripped && !this.overtempTripped;
    }

    hasAnyFault() {
        return this.overchargeTripped || this.overdischargeTripped || this.overcurrentTripped || this.overtempTripped;
    }
}
