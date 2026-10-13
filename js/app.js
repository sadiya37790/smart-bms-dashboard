/**
 * Main Web BMS Dashboard Controller & UI Orchestrator (3S Li-ion Battery Pack)
 */

import { BmsSample, DerivedBmsData, ProtectionSettings } from './model.js';
import { BmsSimulator } from './simulator.js';
import { WebSerialService } from './serial.js';
import { CsvLogger } from './logger.js';

class App {
    constructor() {
        this.protectionSettings = new ProtectionSettings((cfg) => this.onHardwareConfigSynced(cfg));
        this.simulator = new BmsSimulator((sample) => this.onSampleReceived(sample));
        this.serialService = new WebSerialService(
            (sample) => this.onSampleReceived(sample),
            (statusMsg) => this.updateSerialStatus(statusMsg)
        );
        this.csvLogger = new CsvLogger();

        this.alertLogList = [];
        this.lastReceivedSample = null;
        this.lastPacketTimeMs = 0;
        this.packetCount = 0;
        this.sampleRateHz = 0;
        this.hzPacketCounter = 0;
        this.lastHzCalcTimeMs = 0;
        this.startChartTimeSec = -1;

        // Chart.js Instances
        this.cellBarChart = null;
        this.vcChart = null;
        this.tempChart = null;

        this.initDOM();
        this.initCharts();
        this.startStaleDataTimer();
        this.updateProtectionThresholdLabels();
        this.showDisconnectedState(); // Start with safe "--" placeholders
    }

    initDOM() {
        // Top Controls
        this.modeBanner = document.getElementById('modeBanner');
        this.connectionPill = document.getElementById('connectionPill');
        this.pillStatusText = document.getElementById('pillStatusText');
        this.syncBadge = document.getElementById('syncBadge');
        this.syncBadgeText = document.getElementById('syncBadgeText');

        this.connectSerialBtn = document.getElementById('connectSerialBtn');
        this.baudRateSelect = document.getElementById('baudRateSelect');
        this.simulatorToggleBtn = document.getElementById('simulatorToggleBtn');
        this.settingsBtn = document.getElementById('settingsBtn');

        // Telemetry Readouts
        this.liveContainer = document.getElementById('liveContainer');
        this.packVVal = document.getElementById('packVVal');
        this.packVBar = document.getElementById('packVBar');
        this.currentVal = document.getElementById('currentVal');
        this.currentBar = document.getElementById('currentBar');
        this.tempVal = document.getElementById('tempVal');
        this.tempBar = document.getElementById('tempBar');
        this.powerVal = document.getElementById('powerVal');
        this.powerBar = document.getElementById('powerBar');

        // SoC Gauge
        this.socVal = document.getElementById('socVal');
        this.socBar = document.getElementById('socBar');

        // Cell Metrics
        this.maxCellVal = document.getElementById('maxCellVal');
        this.minCellVal = document.getElementById('minCellVal');
        this.cellDeltaVal = document.getElementById('cellDeltaVal');

        // Protection Lamps
        this.lampOvercharge = document.getElementById('lampOvercharge');
        this.valOvercharge = document.getElementById('valOvercharge');
        this.threshOvercharge = document.getElementById('threshOvercharge');

        this.lampOverdischarge = document.getElementById('lampOverdischarge');
        this.valOverdischarge = document.getElementById('valOverdischarge');
        this.threshOverdischarge = document.getElementById('threshOverdischarge');

        this.lampOvercurrent = document.getElementById('lampOvercurrent');
        this.valOvercurrent = document.getElementById('valOvercurrent');
        this.threshOvercurrent = document.getElementById('threshOvercurrent');

        this.lampOvertemp = document.getElementById('lampOvertemp');
        this.valOvertemp = document.getElementById('valOvertemp');
        this.threshOvertemp = document.getElementById('threshOvertemp');

        // FET Badges & Reset
        this.chargeFetBadge = document.getElementById('chargeFetBadge');
        this.dischargeFetBadge = document.getElementById('dischargeFetBadge');
        this.resetFaultsBtn = document.getElementById('resetFaultsBtn');

        // Alert Table
        this.alertTableBody = document.getElementById('alertTableBody');

        // Fault Injectors
        this.chkOvercharge = document.getElementById('chkOvercharge');
        this.chkOverdischarge = document.getElementById('chkOverdischarge');
        this.chkOvercurrent = document.getElementById('chkOvercurrent');
        this.chkOvertemp = document.getElementById('chkOvertemp');

        // Logging Toolbar
        this.loggingToggleBtn = document.getElementById('loggingToggleBtn');
        this.exportCsvBtn = document.getElementById('exportCsvBtn');
        this.logFileLabel = document.getElementById('logFileLabel');
        this.logRowsLabel = document.getElementById('logRowsLabel');

        // Settings Modal Elements
        this.settingsModal = document.getElementById('settingsModal');
        this.inputOvercharge = document.getElementById('inputOvercharge');
        this.inputOverdischarge = document.getElementById('inputOverdischarge');
        this.inputOvercurrent = document.getElementById('inputOvercurrent');
        this.inputOvertemp = document.getElementById('inputOvertemp');
        this.btnApplySettings = document.getElementById('btnApplySettings');
        this.btnRestoreDefaults = document.getElementById('btnRestoreDefaults');
        this.btnCancelSettings = document.getElementById('btnCancelSettings');

        // Event Listeners
        this.simulatorToggleBtn.addEventListener('click', () => this.toggleSimulator());
        this.connectSerialBtn.addEventListener('click', () => this.toggleSerialConnection());
        this.resetFaultsBtn.addEventListener('click', () => this.handleResetFaults());

        this.chkOvercharge.addEventListener('change', () => this.updateFaultInjections());
        this.chkOverdischarge.addEventListener('change', () => this.updateFaultInjections());
        this.chkOvercurrent.addEventListener('change', () => this.updateFaultInjections());
        this.chkOvertemp.addEventListener('change', () => this.updateFaultInjections());

        this.loggingToggleBtn.addEventListener('click', () => this.toggleLogging());
        this.exportCsvBtn.addEventListener('click', () => this.csvLogger.exportCsv(this.lastReceivedSample, this.lastReceivedSample ? new DerivedBmsData(this.lastReceivedSample, this.protectionSettings.hasAnyFault(), this.protectionSettings) : null));

        this.settingsBtn.addEventListener('click', () => this.openSettingsModal());
        this.btnCancelSettings.addEventListener('click', () => this.closeSettingsModal());
        this.btnApplySettings.addEventListener('click', () => this.applySettings());
        this.btnRestoreDefaults.addEventListener('click', () => this.restoreDefaultSettings());

        // Fault injection checkboxes: uncheck & disable when not in simulator mode
        [this.chkOvercharge, this.chkOverdischarge, this.chkOvercurrent, this.chkOvertemp]
            .forEach(chk => { chk.disabled = true; chk.checked = false; });
    }

    initCharts() {
        // 1. Cell Voltage Bar Chart (3S: Cell 1, Cell 2, Cell 3)
        const cellCtx = document.getElementById('cellBarChartCanvas').getContext('2d');
        this.cellBarChart = new Chart(cellCtx, {
            type: 'bar',
            data: {
                labels: ['Cell 1', 'Cell 2', 'Cell 3'],
                datasets: [{
                    label: 'Voltage (V)',
                    data: [3.70, 3.70, 3.70],
                    backgroundColor: ['#38bdf8', '#38bdf8', '#38bdf8'],
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                scales: {
                    y: {
                        min: 2.5,
                        max: 4.3,
                        ticks: { color: '#94a3b8', font: { family: 'Consolas, monospace' } },
                        grid: { color: 'rgba(51, 65, 85, 0.4)' }
                    },
                    x: {
                        ticks: { color: '#94a3b8' },
                        grid: { display: false }
                    }
                },
                plugins: { legend: { display: false } }
            }
        });

        // 2. Voltage & Current Shared Line Chart
        const vcCtx = document.getElementById('vcChartCanvas').getContext('2d');
        this.vcChart = new Chart(vcCtx, {
            type: 'line',
            data: {
                datasets: [
                    { label: 'Pack V', data: [], borderColor: '#38bdf8', borderWidth: 2, pointRadius: 0, tension: 0.1 },
                    { label: 'Current A', data: [], borderColor: '#f59e0b', borderWidth: 2, pointRadius: 0, tension: 0.1 }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                scales: {
                    x: { type: 'linear', title: { display: true, text: 'Time (s)', color: '#64748b' }, ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51, 65, 85, 0.3)' } },
                    y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51, 65, 85, 0.3)' } }
                },
                plugins: { legend: { labels: { color: '#94a3b8' } } }
            }
        });

        // 3. Temperature Line Chart
        const tempCtx = document.getElementById('tempChartCanvas').getContext('2d');
        this.tempChart = new Chart(tempCtx, {
            type: 'line',
            data: {
                datasets: [
                    { label: 'Temp °C', data: [], borderColor: '#10b981', borderWidth: 2, pointRadius: 0, tension: 0.1 }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                scales: {
                    x: { type: 'linear', title: { display: true, text: 'Time (s)', color: '#64748b' }, ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51, 65, 85, 0.3)' } },
                    y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(51, 65, 85, 0.3)' } }
                },
                plugins: { legend: { labels: { color: '#94a3b8' } } }
            }
        });
    }

    /**
     * Enable or disable fault-injection panel based on whether simulator is running.
     * When real hardware is connected the controls must be hidden so users cannot
     * accidentally send misleading fault signals.
     */
    setFaultInjectionGate(simulatorActive) {
        const panel = document.getElementById('faultInjectionPanel');
        const hint  = document.getElementById('faultPanelHint');
        [this.chkOvercharge, this.chkOverdischarge, this.chkOvercurrent, this.chkOvertemp]
            .forEach(chk => {
                chk.disabled = !simulatorActive;
                if (!simulatorActive) chk.checked = false;
            });
        if (panel) {
            panel.classList.toggle('fault-panel-disabled', !simulatorActive);
        }
        if (hint) {
            hint.style.display = simulatorActive ? 'none' : 'block';
        }
        if (!simulatorActive) this.updateFaultInjections(); // clear any active injection
    }

    /**
     * Render "--" placeholders for every metric tile and dim the live-readings
     * container.  Called on start-up and whenever no data source is active.
     */
    showDisconnectedState() {
        this.packVVal.innerText = '--';
        this.currentVal.innerText = '--';
        this.tempVal.innerText = '--';
        this.powerVal.innerText = '--';
        this.packVBar.style.width = '0%';
        this.currentBar.style.width = '0%';
        this.tempBar.style.width = '0%';
        this.powerBar.style.width = '0%';

        this.socVal.innerText = '-- %';
        this.socBar.style.width = '0%';
        this.socBar.className = 'soc-bar-fill';

        this.maxCellVal.innerText = '--';
        this.minCellVal.innerText = '--';
        this.cellDeltaVal.innerText = '--';
        this.cellDeltaVal.style.color = '';

        this.valOvercharge.innerText = '--';
        this.valOverdischarge.innerText = '--';
        this.valOvercurrent.innerText = '--';
        this.valOvertemp.innerText = '--';

        // Reset cell bar chart to zero
        if (this.cellBarChart) {
            this.cellBarChart.data.datasets[0].data = [0, 0, 0];
            this.cellBarChart.data.datasets[0].backgroundColor = ['#334155', '#334155', '#334155'];
            this.cellBarChart.update();
        }

        this.modeBanner.className = 'mode-banner mode-idle';
        this.modeBanner.innerText = '⏸ IDLE';
        this.liveContainer.classList.add('stale-readings');
    }

    toggleSimulator() {
        if (this.simulator.running) {
            this.simulator.stop();
            this.simulatorToggleBtn.classList.remove('btn-primary');
            this.simulatorToggleBtn.innerText = '🎮 Simulator';
            this.setFaultInjectionGate(false);
            this.showDisconnectedState();
            this.lastPacketTimeMs = 0; // Force pill back to disconnected
        } else {
            if (this.serialService.connected) {
                this.serialService.disconnect();
            }
            this.simulator.start();
            this.simulatorToggleBtn.classList.add('btn-primary');
            this.simulatorToggleBtn.innerText = '⏹ Stop Simulator';
            this.setFaultInjectionGate(true);
        }
    }

    async toggleSerialConnection() {
        if (this.serialService.connected) {
            await this.serialService.disconnect();
            this.connectSerialBtn.innerText = 'Connect USB';
            this.connectSerialBtn.classList.remove('btn-danger');
            this.setFaultInjectionGate(false);
            this.showDisconnectedState();
            this.lastPacketTimeMs = 0;
        } else {
            if (this.simulator.running) {
                this.toggleSimulator(); // stop simulator first
            }
            const baud = this.baudRateSelect.value || 115200;
            const ok = await this.serialService.connect(baud);
            if (ok) {
                this.connectSerialBtn.innerText = 'Disconnect';
                this.connectSerialBtn.classList.add('btn-danger');
                // Hardware connected → fault injection must stay DISABLED
                this.setFaultInjectionGate(false);
            }
        }
    }

    updateSerialStatus(msg) {
        this.pillStatusText.innerText = msg;
    }

    onHardwareConfigSynced(cfg) {
        this.syncBadge.style.display = 'inline-flex';
        this.syncBadgeText.innerText = `Auto-Synced Hardware Ratings (V: ${cfg.min_v || 2.8}V-${cfg.max_v || 4.2}V | I: ${cfg.max_i || 5}A | T: ${cfg.max_t || 55}°C)`;
        this.updateProtectionThresholdLabels();
    }

    onSampleReceived(sample) {
        this.lastReceivedSample = sample;
        const now = Date.now();
        this.lastPacketTimeMs = now;
        this.packetCount++;
        this.hzPacketCounter++;

        if (this.startChartTimeSec < 0) {
            this.startChartTimeSec = sample.ts;
        }
        const relTimeSec = sample.ts - this.startChartTimeSec;

        // 1. Evaluate Safety Protection Logic
        const newAlerts = this.protectionSettings.evaluate(sample);
        for (let alert of newAlerts) {
            this.addAlertLog(alert);
        }

        // 2. Compute Derived Metrics
        const derived = new DerivedBmsData(sample, this.protectionSettings.hasAnyFault(), this.protectionSettings);

        // 3. CSV Logger
        if (this.csvLogger.logging) {
            let activeFaults = [];
            if (this.protectionSettings.overchargeTripped) activeFaults.push('OVERCHARGE');
            if (this.protectionSettings.overdischargeTripped) activeFaults.push('OVERDISCHARGE');
            if (this.protectionSettings.overcurrentTripped) activeFaults.push('OVERCURRENT');
            if (this.protectionSettings.overtempTripped) activeFaults.push('OVERTEMP');
            this.csvLogger.logSample(sample, derived, activeFaults.join(' '));
            this.logRowsLabel.innerText = `${this.csvLogger.getRecordCount()} rows`;
        }

        // 4. Update UI
        this.updateModeBanner(derived.mode);
        this.updateMetricTiles(sample, derived);
        this.updateSocGauge(derived.socPercent);
        this.updateCellBarChart(sample.v, derived);
        this.updateProtectionPanel(sample);
        this.updateFetBadges();
        this.updateTrendCharts(relTimeSec, derived.packVoltage, sample.i, sample.t);
    }

    updateModeBanner(mode) {
        this.modeBanner.className = 'mode-banner';
        switch (mode) {
            case 'CHARGING':
                this.modeBanner.innerText = '⚡ CHARGING';
                this.modeBanner.classList.add('mode-charging');
                break;
            case 'DISCHARGING':
                this.modeBanner.innerText = '🔋 DISCHARGING';
                this.modeBanner.classList.add('mode-discharging');
                break;
            case 'IDLE':
                this.modeBanner.innerText = '⏸ IDLE';
                this.modeBanner.classList.add('mode-idle');
                break;
            case 'FAULT':
                this.modeBanner.innerText = '🚨 FAULT - TRIP LATCHED';
                this.modeBanner.classList.add('mode-fault');
                break;
        }
    }

    updateMetricTiles(sample, derived) {
        this.packVVal.innerText = derived.packVoltage.toFixed(2);
        this.currentVal.innerText = (sample.i >= 0 ? '+' : '') + sample.i.toFixed(2);
        this.tempVal.innerText = sample.t.toFixed(1);
        this.powerVal.innerText = derived.power.toFixed(1);

        // Dynamic progress bar scaled to 3S thresholds
        const minPackV = (this.protectionSettings.overdischargeThreshold * 3);
        const maxPackV = (this.protectionSettings.overchargeThreshold * 3);
        this.packVBar.style.width = `${Math.max(0, Math.min(100, ((derived.packVoltage - minPackV) / (maxPackV - minPackV)) * 100))}%`;

        const maxI = this.protectionSettings.overcurrentThreshold * 2;
        this.currentBar.style.width = `${Math.max(0, Math.min(100, ((sample.i + maxI / 2) / maxI) * 100))}%`;

        this.tempBar.style.width = `${Math.max(0, Math.min(100, (sample.t / this.protectionSettings.overtempThreshold) * 100))}%`;
        this.powerBar.style.width = `${Math.max(0, Math.min(100, (Math.abs(derived.power) / 100) * 100))}%`;
    }

    updateSocGauge(soc) {
        this.socVal.innerText = `${soc.toFixed(1)} %`;
        this.socBar.style.width = `${Math.max(0, Math.min(100, soc))}%`;
        this.socBar.className = 'soc-bar-fill';
        if (soc > 40) {
            this.socBar.classList.add('soc-high');
        } else if (soc >= 15) {
            this.socBar.classList.add('soc-med');
        } else {
            this.socBar.classList.add('soc-low');
        }
    }

    updateCellBarChart(voltages, derived) {
        if (!voltages || voltages.length < 3) return;

        const bgColors = voltages.map(v => (v > this.protectionSettings.overchargeThreshold || v < this.protectionSettings.overdischargeThreshold) ? '#ef4444' : '#38bdf8');
        this.cellBarChart.data.datasets[0].data = voltages.slice(0, 3);
        this.cellBarChart.data.datasets[0].backgroundColor = bgColors;
        this.cellBarChart.update();

        this.maxCellVal.innerText = `${derived.maxCellVoltage.toFixed(3)} V`;
        this.minCellVal.innerText = `${derived.minCellVoltage.toFixed(3)} V`;

        const deltamV = derived.cellDelta * 1000.0;
        this.cellDeltaVal.innerText = `${deltamV.toFixed(1)} mV`;
        this.cellDeltaVal.style.color = deltamV > 100 ? '#ef4444' : (deltamV > 50 ? '#f59e0b' : '#10b981');
    }

    updateProtectionPanel(sample) {
        let maxCell = Number.MIN_VALUE;
        let minCell = Number.MAX_VALUE;
        if (sample.v) {
            for (let v of sample.v) {
                if (v > maxCell) maxCell = v;
                if (v < minCell) minCell = v;
            }
        }

        this.valOvercharge.innerText = `${maxCell.toFixed(2)} V`;
        this.valOverdischarge.innerText = `${minCell.toFixed(2)} V`;
        this.valOvercurrent.innerText = `${sample.i.toFixed(2)} A`;
        this.valOvertemp.innerText = `${sample.t.toFixed(1)} °C`;

        this.setLamp(this.lampOvercharge, this.protectionSettings.overchargeTripped || this.protectionSettings.instantOvercharge);
        this.setLamp(this.lampOverdischarge, this.protectionSettings.overdischargeTripped || this.protectionSettings.instantOverdischarge);
        this.setLamp(this.lampOvercurrent, this.protectionSettings.overcurrentTripped || this.protectionSettings.instantOvercurrent);
        this.setLamp(this.lampOvertemp, this.protectionSettings.overtempTripped || this.protectionSettings.instantOvertemp);
    }

    updateProtectionThresholdLabels() {
        this.threshOvercharge.innerText = `> ${this.protectionSettings.overchargeThreshold.toFixed(2)} V`;
        this.threshOverdischarge.innerText = `< ${this.protectionSettings.overdischargeThreshold.toFixed(2)} V`;
        this.threshOvercurrent.innerText = `> ${this.protectionSettings.overcurrentThreshold.toFixed(2)} A`;
        this.threshOvertemp.innerText = `> ${this.protectionSettings.overtempThreshold.toFixed(1)} °C`;
    }

    setLamp(elem, isTripped) {
        elem.innerText = isTripped ? 'TRIPPED' : 'NORMAL';
        elem.className = 'lamp ' + (isTripped ? 'lamp-tripped' : 'lamp-normal');
    }

    updateFetBadges() {
        const chgOn = this.protectionSettings.isChargeFetOn();
        const dsgOn = this.protectionSettings.isDischargeFetOn();

        this.chargeFetBadge.innerText = chgOn ? 'CHARGE FET: ON' : 'CHARGE FET: OFF';
        this.chargeFetBadge.className = 'fet-badge ' + (chgOn ? 'fet-on' : 'fet-off');

        this.dischargeFetBadge.innerText = dsgOn ? 'DISCHARGE FET: ON' : 'DISCHARGE FET: OFF';
        this.dischargeFetBadge.className = 'fet-badge ' + (dsgOn ? 'fet-on' : 'fet-off');
    }

    updateTrendCharts(tSec, packV, current, temp) {
        // Add points
        this.vcChart.data.datasets[0].data.push({ x: tSec, y: packV });
        this.vcChart.data.datasets[1].data.push({ x: tSec, y: current });
        this.tempChart.data.datasets[0].data.push({ x: tSec, y: temp });

        // Memory leak prevention: Prune points older than 120s window
        const minTime = tSec - 120;
        while (this.vcChart.data.datasets[0].data.length > 0 && this.vcChart.data.datasets[0].data[0].x < minTime) {
            this.vcChart.data.datasets[0].data.shift();
            this.vcChart.data.datasets[1].data.shift();
        }
        while (this.tempChart.data.datasets[0].data.length > 0 && this.tempChart.data.datasets[0].data[0].x < minTime) {
            this.tempChart.data.datasets[0].data.shift();
        }

        this.vcChart.options.scales.x.min = Math.max(0, minTime);
        this.vcChart.options.scales.x.max = Math.max(120, tSec);
        this.tempChart.options.scales.x.min = Math.max(0, minTime);
        this.tempChart.options.scales.x.max = Math.max(120, tSec);

        this.vcChart.update();
        this.tempChart.update();
    }

    addAlertLog(alert) {
        const timeStr = new Date().toTimeString().split(' ')[0];
        const row = { ...alert, time: timeStr };
        this.alertLogList.unshift(row);
        if (this.alertLogList.length > 200) this.alertLogList.pop();

        this.renderAlertTable();
    }

    renderAlertTable() {
        this.alertTableBody.innerHTML = '';
        for (let entry of this.alertLogList) {
            const tr = document.createElement('tr');
            tr.className = 'row-' + entry.severity.toLowerCase();
            tr.innerHTML = `
                <td>${entry.time}</td>
                <td>${entry.parameter}</td>
                <td>${entry.value}</td>
                <td>${entry.threshold}</td>
                <td><strong>${entry.severity}</strong></td>
            `;
            this.alertTableBody.appendChild(tr);
        }
    }

    handleResetFaults() {
        if (this.lastReceivedSample) {
            const cleared = this.protectionSettings.resetFaults(this.lastReceivedSample);
            for (let c of cleared) {
                this.addAlertLog(c);
            }
            if (cleared.length === 0 && this.protectionSettings.hasAnyFault()) {
                alert('Cannot reset latched fault: Safety breach condition is still active on battery pack!');
            }
        } else {
            this.protectionSettings.restoreDefaults();
        }
        if (this.lastReceivedSample) this.updateProtectionPanel(this.lastReceivedSample);
        this.updateFetBadges();
    }

    updateFaultInjections() {
        this.simulator.forceOvercharge = this.chkOvercharge.checked;
        this.simulator.forceOverdischarge = this.chkOverdischarge.checked;
        this.simulator.forceOvercurrent = this.chkOvercurrent.checked;
        this.simulator.forceOvertemp = this.chkOvertemp.checked;
    }

    toggleLogging() {
        if (this.csvLogger.logging) {
            this.csvLogger.stopLogging();
            this.loggingToggleBtn.innerText = 'Start Logging';
            this.loggingToggleBtn.classList.remove('btn-primary');
        } else {
            this.csvLogger.startLogging();
            this.loggingToggleBtn.innerText = 'Stop Logging';
            this.loggingToggleBtn.classList.add('btn-primary');
            this.logFileLabel.innerText = this.csvLogger.filename;
            this.logRowsLabel.innerText = '0 rows';
        }
    }

    startStaleDataTimer() {
        setInterval(() => {
            const now = Date.now();
            if (now - this.lastHzCalcTimeMs >= 1000) {
                this.sampleRateHz = (this.hzPacketCounter * 1000.0) / (now - this.lastHzCalcTimeMs);
                this.hzPacketCounter = 0;
                this.lastHzCalcTimeMs = now;
            }

            if (this.lastPacketTimeMs === 0) {
                this.setConnectionPill('DISCONNECTED', 'pill-disconnected', 0, 0);
                return;
            }

            const elapsedSec = Math.floor((now - this.lastPacketTimeMs) / 1000);
            const sourceStr = this.serialService.connected ? 'USB SERIAL' : (this.simulator.running ? 'SIMULATOR' : 'N/A');

            if (elapsedSec > 10) {
                this.setConnectionPill(`DISCONNECTED (${sourceStr})`, 'pill-disconnected', elapsedSec, 0);
                this.liveContainer.classList.add('stale-readings');
            } else if (elapsedSec > 3) {
                this.setConnectionPill(`STALE DATA (${sourceStr})`, 'pill-stale', elapsedSec, this.sampleRateHz);
                this.liveContainer.classList.add('stale-readings');
            } else {
                this.setConnectionPill(`CONNECTED (${sourceStr})`, 'pill-connected', elapsedSec, this.sampleRateHz);
                this.liveContainer.classList.remove('stale-readings');
            }
        }, 100);
    }

    setConnectionPill(statusText, cssClass, ageSec, hz) {
        this.connectionPill.className = `connection-pill ${cssClass}`;
        this.pillStatusText.innerText = `${statusText} | ${ageSec}s ago | ${hz.toFixed(1)} Hz`;
    }

    // Modal Settings Dialog
    openSettingsModal() {
        this.inputOvercharge.value = this.protectionSettings.overchargeThreshold.toFixed(2);
        this.inputOverdischarge.value = this.protectionSettings.overdischargeThreshold.toFixed(2);
        this.inputOvercurrent.value = this.protectionSettings.overcurrentThreshold.toFixed(2);
        this.inputOvertemp.value = this.protectionSettings.overtempThreshold.toFixed(1);
        this.settingsModal.classList.add('active');
    }

    closeSettingsModal() {
        this.settingsModal.classList.remove('active');
    }

    applySettings() {
        const overcharge = parseFloat(this.inputOvercharge.value);
        const overdischarge = parseFloat(this.inputOverdischarge.value);
        const overcurrent = parseFloat(this.inputOvercurrent.value);
        const overtemp = parseFloat(this.inputOvertemp.value);

        if (isNaN(overcharge) || isNaN(overdischarge) || isNaN(overcurrent) || isNaN(overtemp)) {
            alert('Please enter valid numerical values.');
            return;
        }
        if (overcharge <= overdischarge) {
            alert('Overcharge limit must be strictly greater than Over-discharge limit.');
            return;
        }

        this.protectionSettings.overchargeThreshold = overcharge;
        this.protectionSettings.overdischargeThreshold = overdischarge;
        this.protectionSettings.overcurrentThreshold = overcurrent;
        this.protectionSettings.overtempThreshold = overtemp;

        this.updateProtectionThresholdLabels();
        if (this.lastReceivedSample) this.onSampleReceived(this.lastReceivedSample);
        this.closeSettingsModal();
    }

    restoreDefaultSettings() {
        this.protectionSettings.restoreDefaults();
        this.openSettingsModal();
    }
}

// Instantiate App when DOM is ready
window.addEventListener('DOMContentLoaded', () => {
    window.app = new App();
});
