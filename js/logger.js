/**
 * Telemetry Logger & CSV Exporter Engine (3S Li-ion Battery Pack)
 */

export class CsvLogger {
    constructor() {
        this.logging = false;
        this.records = [];
        this.filename = 'bms_telemetry_3s.csv';
    }

    startLogging() {
        this.records = [];
        this.logging = true;
        this.filename = `bms_telemetry_3s_${Math.floor(Date.now() / 1000)}.csv`;
    }

    stopLogging() {
        this.logging = false;
    }

    logSample(sample, derived, activeFaults = 'NONE') {
        if (!this.logging || !sample || !derived) return;

        const v = sample.v || [0, 0, 0];
        const formattedTime = new Date(sample.ts * 1000).toISOString().replace('T', ' ').substring(0, 19);

        const row = [
            formattedTime,
            v[0] ? v[0].toFixed(3) : '0.000',
            v[1] ? v[1].toFixed(3) : '0.000',
            v[2] ? v[2].toFixed(3) : '0.000',
            derived.packVoltage.toFixed(2),
            sample.i.toFixed(2),
            sample.t.toFixed(1),
            derived.socPercent.toFixed(1),
            derived.power.toFixed(2),
            derived.mode,
            `"${activeFaults}"`
        ].join(',');

        this.records.push(row);
    }

    exportCsv(currentSample = null, derived = null) {
        let csvContent = "timestamp,cell1,cell2,cell3,pack_v,current,temp,soc,power,mode,active_faults\n";

        if (this.records.length > 0) {
            csvContent += this.records.join('\n');
        } else if (currentSample && derived) {
            const v = currentSample.v || [0, 0, 0];
            const formattedTime = new Date(currentSample.ts * 1000).toISOString().replace('T', ' ').substring(0, 19);
            csvContent += [
                formattedTime,
                v[0].toFixed(3), v[1].toFixed(3), v[2].toFixed(3),
                derived.packVoltage.toFixed(2), currentSample.i.toFixed(2), currentSample.t.toFixed(1),
                derived.socPercent.toFixed(1), derived.power.toFixed(2), derived.mode, '"MANUAL_EXPORT"'
            ].join(',');
        }

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.setAttribute('href', url);
        link.setAttribute('download', this.filename || `bms_export_3s_${Math.floor(Date.now() / 1000)}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    getRecordCount() {
        return this.records.length;
    }
}
