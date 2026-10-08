package com.bms.dashboard.controller;

import com.bms.dashboard.model.AlertEntry;
import com.bms.dashboard.model.BmsSample;
import com.bms.dashboard.model.DerivedBmsData;
import com.bms.dashboard.model.ProtectionSettings;
import com.bms.dashboard.service.BmsSimulator;
import com.bms.dashboard.service.CsvLogger;
import com.bms.dashboard.service.SerialService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainController {
    // Top Bar
    @FXML private Label modeBannerLabel;
    @FXML private HBox connectionPill;
    @FXML private Label pillStatusText;
    @FXML private ComboBox<String> portComboBox;
    @FXML private TextField baudRateField;
    @FXML private Button connectButton;
    @FXML private ToggleButton simulatorToggle;

    // Left Column - Live Readout Tiles
    @FXML private VBox liveReadingsContainer;
    @FXML private Label packVoltageLabel;
    @FXML private Label currentLabel;
    @FXML private Label tempLabel;
    @FXML private Label powerLabel;

    @FXML private ProgressBar packVoltageBar;
    @FXML private ProgressBar currentBar;
    @FXML private ProgressBar tempBar;
    @FXML private ProgressBar powerBar;

    // SoC Battery Gauge
    @FXML private Label socPercentLabel;
    @FXML private ProgressBar socProgressBar;

    // Cell Voltage Bar Chart
    @FXML private BarChart<String, Number> cellBarChart;
    @FXML private CategoryAxis cellXAxis;
    @FXML private NumberAxis cellYAxis;
    @FXML private Label maxCellLabel;
    @FXML private Label minCellLabel;
    @FXML private Label cellDeltaLabel;

    // Right Column - Protection Lamps & FET Status
    @FXML private Label overchargeLamp;
    @FXML private Label overchargeValueLabel;
    @FXML private Label overchargeThreshLabel;

    @FXML private Label overdischargeLamp;
    @FXML private Label overdischargeValueLabel;
    @FXML private Label overdischargeThreshLabel;

    @FXML private Label overcurrentLamp;
    @FXML private Label overcurrentValueLabel;
    @FXML private Label overcurrentThreshLabel;

    @FXML private Label overtempLamp;
    @FXML private Label overtempValueLabel;
    @FXML private Label overtempThreshLabel;

    @FXML private Label chargeFetStatusLabel;
    @FXML private Label dischargeFetStatusLabel;
    @FXML private Button resetFaultsButton;

    // Alert Log Table
    @FXML private TableView<AlertEntry> alertLogTable;
    @FXML private TableColumn<AlertEntry, String> colTime;
    @FXML private TableColumn<AlertEntry, String> colParameter;
    @FXML private TableColumn<AlertEntry, String> colValue;
    @FXML private TableColumn<AlertEntry, String> colThreshold;
    @FXML private TableColumn<AlertEntry, String> colSeverity;

    // Bottom Panel - Rolling Line Charts
    @FXML private LineChart<Number, Number> voltageCurrentChart;
    @FXML private NumberAxis vcTimeAxis;
    @FXML private NumberAxis voltageAxis;
    @FXML private NumberAxis currentAxis;

    @FXML private LineChart<Number, Number> tempChart;
    @FXML private NumberAxis tempTimeAxis;
    @FXML private NumberAxis tempYAxis;

    // Logging & Fault Injection Toolbar
    @FXML private ToggleButton loggingToggle;
    @FXML private Label logFilenameLabel;
    @FXML private Label logRowCountLabel;
    @FXML private Button exportCsvButton;

    @FXML private CheckBox forceOverchargeCheck;
    @FXML private CheckBox forceOverdischargeCheck;
    @FXML private CheckBox forceOvercurrentCheck;
    @FXML private CheckBox forceOvertempCheck;

    // Underlying Services & Models
    private final SerialService serialService = new SerialService();
    private final BmsSimulator simulator = new BmsSimulator();
    private final CsvLogger csvLogger = new CsvLogger();
    private final ProtectionSettings protectionSettings = new ProtectionSettings();

    private final ObservableList<AlertEntry> alertLogList = FXCollections.observableArrayList();

    // Chart Series
    private final XYChart.Series<Number, Number> packVoltageSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> currentSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> tempSeries = new XYChart.Series<>();

    private final XYChart.Series<String, Number> cellVoltageBarSeries = new XYChart.Series<>();

    // Timers & Stats
    private Timeline staleDataTimer;
    private long lastPacketTimeMs = 0;
    private long packetCount = 0;
    private double sampleRateHz = 0.0;
    private long lastHzCalcTimeMs = 0;
    private long hzPacketCounter = 0;
    private long startChartTimeSec = -1;

    private BmsSample lastReceivedSample;

    @FXML
    public void initialize() {
        setupAlertTable();
        setupCharts();
        refreshPortList();

        baudRateField.setText("115200");

        // Stale data monitoring timer running at 10 Hz
        staleDataTimer = new Timeline(new KeyFrame(Duration.millis(100), e -> checkStaleDataAndStats()));
        staleDataTimer.setCycleCount(Timeline.INDEFINITE);
        staleDataTimer.play();

        // Initial default state
        updateProtectionLabels();
    }

    private void setupAlertTable() {
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTime()));
        colParameter.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getParameter()));
        colValue.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue()));
        colThreshold.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getThreshold()));
        colSeverity.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSeverity().name()));

        alertLogTable.setItems(alertLogList);

        alertLogTable.setRowFactory(tv -> new TableRow<AlertEntry>() {
            @Override
            protected void updateItem(AlertEntry item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-critical", "row-warning", "row-info");
                if (empty || item == null) {
                    setStyle("");
                } else {
                    switch (item.getSeverity()) {
                        case CRITICAL -> getStyleClass().add("row-critical");
                        case WARNING -> getStyleClass().add("row-warning");
                        case INFO -> getStyleClass().add("row-info");
                    }
                }
            }
        });
    }

    private void setupCharts() {
        packVoltageSeries.setName("Pack Voltage (V)");
        currentSeries.setName("Current (A)");
        voltageCurrentChart.getData().addAll(packVoltageSeries, currentSeries);

        tempSeries.setName("Temperature (°C)");
        tempChart.getData().add(tempSeries);

        cellVoltageBarSeries.setName("Cell Voltage");
        for (int i = 1; i <= 4; i++) {
            cellVoltageBarSeries.getData().add(new XYChart.Data<>("Cell " + i, 3.7));
        }
        cellBarChart.getData().add(cellVoltageBarSeries);
        cellBarChart.setAnimated(false);
    }

    @FXML
    public void refreshPortList() {
        List<String> ports = serialService.getAvailablePorts();
        portComboBox.getItems().clear();
        portComboBox.getItems().addAll(ports);
        if (!ports.isEmpty()) {
            portComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleConnectDisconnect() {
        if (serialService.isConnected()) {
            serialService.disconnect();
            connectButton.setText("Connect");
            portComboBox.setDisable(false);
            baudRateField.setDisable(false);
            simulatorToggle.setDisable(false);
        } else {
            String port = portComboBox.getValue();
            if (port == null || port.isEmpty()) {
                showAlert("Error", "Please select a serial port.");
                return;
            }
            int baud;
            try {
                baud = Integer.parseInt(baudRateField.getText().trim());
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid baud rate.");
                return;
            }

            if (simulatorToggle.isSelected()) {
                simulatorToggle.setSelected(false);
                handleSimulatorToggle();
            }

            boolean ok = serialService.connect(port, baud, this::onSampleReceived, statusMsg -> Platform.runLater(() -> pillStatusText.setText(statusMsg)));
            if (ok) {
                connectButton.setText("Disconnect");
                portComboBox.setDisable(true);
                baudRateField.setDisable(true);
                simulatorToggle.setDisable(true);
            }
        }
    }

    @FXML
    public void handleSimulatorToggle() {
        if (simulatorToggle.isSelected()) {
            if (serialService.isConnected()) {
                handleConnectDisconnect(); // disconnect hardware serial
            }
            simulator.start(this::onSampleReceived);
            pillStatusText.setText("Simulator Active");
            connectButton.setDisable(true);
            portComboBox.setDisable(true);
            baudRateField.setDisable(true);
        } else {
            simulator.stop();
            pillStatusText.setText("Disconnected");
            connectButton.setDisable(false);
            portComboBox.setDisable(false);
            baudRateField.setDisable(false);
        }
    }

    // Called on background thread from SerialService or Simulator
    private void onSampleReceived(BmsSample sample) {
        Platform.runLater(() -> processSample(sample));
    }

    private void processSample(BmsSample sample) {
        this.lastReceivedSample = sample;
        long now = System.currentTimeMillis();
        this.lastPacketTimeMs = now;
        this.packetCount++;
        this.hzPacketCounter++;

        if (startChartTimeSec < 0) {
            startChartTimeSec = sample.getTimestamp();
        }
        double relativeTimeSec = sample.getTimestamp() - startChartTimeSec;

        // 1. Evaluate Protection Logic
        List<AlertEntry> newAlerts = protectionSettings.evaluate(sample);
        for (AlertEntry alert : newAlerts) {
            addAlertLog(alert);
        }

        // 2. Compute Derived Metrics
        DerivedBmsData derived = new DerivedBmsData(sample, protectionSettings.hasAnyFault());

        // 3. Log sample to CSV if enabled
        if (csvLogger.isLogging()) {
            StringBuilder activeFaults = new StringBuilder();
            if (protectionSettings.isOverchargeTripped()) activeFaults.append("OVERCHARGE ");
            if (protectionSettings.isOverdischargeTripped()) activeFaults.append("OVERDISCHARGE ");
            if (protectionSettings.isOvercurrentTripped()) activeFaults.append("OVERCURRENT ");
            if (protectionSettings.isOvertempTripped()) activeFaults.append("OVERTEMP ");
            csvLogger.logSample(sample, derived, activeFaults.length() > 0 ? activeFaults.toString().trim() : "NONE");
            logRowCountLabel.setText(csvLogger.getRecordCount() + " rows");
        }

        // 4. Update UI Components
        updateModeBanner(derived.getMode());
        updateMetricTiles(sample, derived);
        updateSocGauge(derived.getSocPercent());
        updateCellBarChart(sample.getCellVoltages(), derived);
        updateProtectionPanel(sample);
        updateFetIndicators();
        updateTrendCharts(relativeTimeSec, derived.getPackVoltage(), sample.getCurrent(), sample.getTemperature());
    }

    private void updateModeBanner(DerivedBmsData.OperationalMode mode) {
        modeBannerLabel.getStyleClass().removeAll("mode-charging", "mode-discharging", "mode-idle", "mode-fault");
        switch (mode) {
            case CHARGING -> {
                modeBannerLabel.setText("CHARGING");
                modeBannerLabel.getStyleClass().add("mode-charging");
            }
            case DISCHARGING -> {
                modeBannerLabel.setText("DISCHARGING");
                modeBannerLabel.getStyleClass().add("mode-discharging");
            }
            case IDLE -> {
                modeBannerLabel.setText("IDLE");
                modeBannerLabel.getStyleClass().add("mode-idle");
            }
            case FAULT -> {
                modeBannerLabel.setText("FAULT - TRIP LATCHED");
                modeBannerLabel.getStyleClass().add("mode-fault");
            }
        }
    }

    private void updateMetricTiles(BmsSample sample, DerivedBmsData derived) {
        packVoltageLabel.setText(String.format("%.2f", derived.getPackVoltage()));
        currentLabel.setText(String.format("%+.2f", sample.getCurrent()));
        tempLabel.setText(String.format("%.1f", sample.getTemperature()));
        powerLabel.setText(String.format("%.1f", derived.getPower()));

        // Progress bars showing position in safe range
        packVoltageBar.setProgress(Math.max(0.0, Math.min(1.0, (derived.getPackVoltage() - 11.2) / (16.8 - 11.2))));
        currentBar.setProgress(Math.max(0.0, Math.min(1.0, (sample.getCurrent() + 5.0) / 10.0)));
        tempBar.setProgress(Math.max(0.0, Math.min(1.0, sample.getTemperature() / 60.0)));
        powerBar.setProgress(Math.max(0.0, Math.min(1.0, Math.abs(derived.getPower()) / 100.0)));
    }

    private void updateSocGauge(double soc) {
        socPercentLabel.setText(String.format("%.1f %%", soc));
        socProgressBar.setProgress(soc / 100.0);
        socProgressBar.getStyleClass().removeAll("soc-high", "soc-med", "soc-low");

        if (soc > 40.0) {
            socProgressBar.getStyleClass().add("soc-high");
        } else if (soc >= 15.0) {
            socProgressBar.getStyleClass().add("soc-med");
        } else {
            socProgressBar.getStyleClass().add("soc-low");
        }
    }

    private void updateCellBarChart(double[] voltages, DerivedBmsData derived) {
        if (voltages == null || voltages.length < 4) return;

        for (int i = 0; i < 4; i++) {
            double v = voltages[i];
            XYChart.Data<String, Number> data = cellVoltageBarSeries.getData().get(i);
            data.setYValue(v);

            if (data.getNode() != null) {
                data.getNode().getStyleClass().removeAll("bar-normal", "bar-fault");
                if (v > protectionSettings.getOverchargeThreshold() || v < protectionSettings.getOverdischargeThreshold()) {
                    data.getNode().getStyleClass().add("bar-fault");
                } else {
                    data.getNode().getStyleClass().add("bar-normal");
                }
            }
        }

        maxCellLabel.setText(String.format("%.3f V", derived.getMaxCellVoltage()));
        minCellLabel.setText(String.format("%.3f V", derived.getMinCellVoltage()));

        double deltamV = derived.getCellDelta() * 1000.0;
        cellDeltaLabel.setText(String.format("%.1f mV", deltamV));

        cellDeltaLabel.getStyleClass().removeAll("delta-normal", "delta-warn", "delta-alarm");
        if (deltamV > 100.0) {
            cellDeltaLabel.getStyleClass().add("delta-alarm");
        } else if (deltamV > 50.0) {
            cellDeltaLabel.getStyleClass().add("delta-warn");
        } else {
            cellDeltaLabel.getStyleClass().add("delta-normal");
        }
    }

    private void updateProtectionPanel(BmsSample sample) {
        double maxCell = Double.MIN_VALUE;
        double minCell = Double.MAX_VALUE;
        if (sample.getCellVoltages() != null) {
            for (double v : sample.getCellVoltages()) {
                if (v > maxCell) maxCell = v;
                if (v < minCell) minCell = v;
            }
        }

        overchargeValueLabel.setText(String.format("%.2f V", maxCell));
        overdischargeValueLabel.setText(String.format("%.2f V", minCell));
        overcurrentValueLabel.setText(String.format("%.2f A", sample.getCurrent()));
        overtempValueLabel.setText(String.format("%.1f °C", sample.getTemperature()));

        setLampStatus(overchargeLamp, protectionSettings.isOverchargeTripped() || protectionSettings.isInstantOvercharge());
        setLampStatus(overdischargeLamp, protectionSettings.isOverdischargeTripped() || protectionSettings.isInstantOverdischarge());
        setLampStatus(overcurrentLamp, protectionSettings.isOvercurrentTripped() || protectionSettings.isInstantOvercurrent());
        setLampStatus(overtempLamp, protectionSettings.isOvertempTripped() || protectionSettings.isInstantOvertemp());
    }

    private void updateProtectionLabels() {
        overchargeThreshLabel.setText(String.format("> %.2f V", protectionSettings.getOverchargeThreshold()));
        overdischargeThreshLabel.setText(String.format("< %.2f V", protectionSettings.getOverdischargeThreshold()));
        overcurrentThreshLabel.setText(String.format("> %.2f A", protectionSettings.getOvercurrentThreshold()));
        overtempThreshLabel.setText(String.format("> %.1f °C", protectionSettings.getOvertempThreshold()));
    }

    private void setLampStatus(Label lamp, boolean isTripped) {
        lamp.getStyleClass().removeAll("lamp-normal", "lamp-tripped");
        if (isTripped) {
            lamp.setText("TRIPPED");
            lamp.getStyleClass().add("lamp-tripped");
        } else {
            lamp.setText("NORMAL");
            lamp.getStyleClass().add("lamp-normal");
        }
    }

    private void updateFetIndicators() {
        boolean chgOn = protectionSettings.isChargeFetOn();
        boolean dsgOn = protectionSettings.isDischargeFetOn();

        chargeFetStatusLabel.setText(chgOn ? "CHARGE FET: ON" : "CHARGE FET: OFF");
        chargeFetStatusLabel.getStyleClass().removeAll("fet-on", "fet-off");
        chargeFetStatusLabel.getStyleClass().add(chgOn ? "fet-on" : "fet-off");

        dischargeFetStatusLabel.setText(dsgOn ? "DISCHARGE FET: ON" : "DISCHARGE FET: OFF");
        dischargeFetStatusLabel.getStyleClass().removeAll("fet-on", "fet-off");
        dischargeFetStatusLabel.getStyleClass().add(dsgOn ? "fet-on" : "fet-off");
    }

    private void updateTrendCharts(double tSec, double packV, double current, double temp) {
        packVoltageSeries.getData().add(new XYChart.Data<>(tSec, packV));
        currentSeries.getData().add(new XYChart.Data<>(tSec, current));
        tempSeries.getData().add(new XYChart.Data<>(tSec, temp));

        // Memory leak prevention: Keep only rolling 120-second window
        double minTime = tSec - 120.0;
        removeOldChartPoints(packVoltageSeries, minTime);
        removeOldChartPoints(currentSeries, minTime);
        removeOldChartPoints(tempSeries, minTime);

        vcTimeAxis.setLowerBound(Math.max(0, minTime));
        vcTimeAxis.setUpperBound(Math.max(120, tSec));

        tempTimeAxis.setLowerBound(Math.max(0, minTime));
        tempTimeAxis.setUpperBound(Math.max(120, tSec));
    }

    private void removeOldChartPoints(XYChart.Series<Number, Number> series, double minTime) {
        while (!series.getData().isEmpty() && series.getData().get(0).getXValue().doubleValue() < minTime) {
            series.getData().remove(0);
        }
    }

    private void addAlertLog(AlertEntry entry) {
        alertLogList.add(0, entry); // Newest first
        if (alertLogList.size() > 200) {
            alertLogList.remove(alertLogList.size() - 1);
        }
    }

    @FXML
    public void handleResetFaults() {
        if (lastReceivedSample != null) {
            List<AlertEntry> cleared = protectionSettings.resetFaults(lastReceivedSample);
            for (AlertEntry entry : cleared) {
                addAlertLog(entry);
            }
            if (cleared.isEmpty() && protectionSettings.hasAnyFault()) {
                showAlert("Reset Faults", "Cannot reset latched fault: Condition is still active in live battery pack!");
            }
        } else {
            protectionSettings.resetFaults(new BmsSample());
        }
        updateProtectionPanel(lastReceivedSample != null ? lastReceivedSample : new BmsSample());
        updateFetIndicators();
    }

    // Stale Data & Hz Monitoring
    private void checkStaleDataAndStats() {
        long now = System.currentTimeMillis();

        // Calculate sample rate (Hz) every 1 second
        if (now - lastHzCalcTimeMs >= 1000) {
            sampleRateHz = hzPacketCounter * 1000.0 / (now - lastHzCalcTimeMs);
            hzPacketCounter = 0;
            lastHzCalcTimeMs = now;
        }

        if (lastPacketTimeMs == 0) {
            setConnectionPillState("DISCONNECTED", "pill-disconnected", 0, 0.0);
            return;
        }

        long elapsedSec = (now - lastPacketTimeMs) / 1000;
        String portStr = serialService.isConnected() ? serialService.getPortName() : (simulator.isRunning() ? "SIMULATOR" : "N/A");

        if (elapsedSec > 10) {
            setConnectionPillState("DISCONNECTED (" + portStr + ")", "pill-disconnected", elapsedSec, 0.0);
            setLiveReadingsStale(true);
        } else if (elapsedSec > 3) {
            setConnectionPillState("STALE DATA (" + portStr + ")", "pill-stale", elapsedSec, sampleRateHz);
            setLiveReadingsStale(true);
        } else {
            setConnectionPillState("CONNECTED (" + portStr + ")", "pill-connected", elapsedSec, sampleRateHz);
            setLiveReadingsStale(false);
        }
    }

    private void setConnectionPillState(String statusText, String cssClass, long ageSec, double hz) {
        connectionPill.getStyleClass().removeAll("pill-connected", "pill-stale", "pill-disconnected");
        connectionPill.getStyleClass().add(cssClass);
        pillStatusText.setText(String.format("%s | %ds ago | %.1f Hz", statusText, ageSec, hz));
    }

    private void setLiveReadingsStale(boolean stale) {
        if (stale) {
            if (!liveReadingsContainer.getStyleClass().contains("stale-readings")) {
                liveReadingsContainer.getStyleClass().add("stale-readings");
            }
        } else {
            liveReadingsContainer.getStyleClass().remove("stale-readings");
        }
    }

    // Fault Injection Checkbox Handlers
    @FXML
    public void handleFaultInjections() {
        simulator.setForceOvercharge(forceOverchargeCheck.isSelected());
        simulator.setForceOverdischarge(forceOverdischargeCheck.isSelected());
        simulator.setForceOvercurrent(forceOvercurrentCheck.isSelected());
        simulator.setForceOvertemp(forceOvertempCheck.isSelected());
    }

    // Logging controls
    @FXML
    public void handleLoggingToggle() {
        if (loggingToggle.isSelected()) {
            File logFile = new File("bms_telemetry_" + System.currentTimeMillis() / 1000 + ".csv");
            try {
                csvLogger.startLogging(logFile);
                logFilenameLabel.setText(logFile.getName());
                logRowCountLabel.setText("0 rows");
                loggingToggle.setText("Stop Logging");
            } catch (Exception e) {
                showAlert("Logging Error", "Failed to create log file: " + e.getMessage());
                loggingToggle.setSelected(false);
            }
        } else {
            csvLogger.stopLogging();
            loggingToggle.setText("Start Logging");
        }
    }

    @FXML
    public void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save BMS CSV Telemetry");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("bms_export_" + System.currentTimeMillis() / 1000 + ".csv");

        File file = chooser.showSaveDialog(resetFaultsButton.getScene().getWindow());
        if (file != null) {
            try {
                CsvLogger exportLogger = new CsvLogger();
                exportLogger.startLogging(file);
                if (lastReceivedSample != null) {
                    exportLogger.logSample(lastReceivedSample, new DerivedBmsData(lastReceivedSample, protectionSettings.hasAnyFault()), "EXPORT");
                }
                exportLogger.stopLogging();
                showAlert("Success", "CSV Exported successfully to " + file.getAbsolutePath());
            } catch (Exception e) {
                showAlert("Export Error", "Failed to export CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleOpenSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bms/dashboard/view/SettingsView.fxml"));
            Parent root = loader.load();

            SettingsController controller = loader.getController();
            controller.setProtectionSettings(protectionSettings, () -> {
                updateProtectionLabels();
                if (lastReceivedSample != null) {
                    processSample(lastReceivedSample);
                }
            });

            Stage stage = new Stage();
            stage.setTitle("BMS Protection Threshold Settings");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(resetFaultsButton.getScene().getWindow());
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/bms/dashboard/styles/dark-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            showAlert("Error", "Could not open settings dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (staleDataTimer != null) staleDataTimer.stop();
        serialService.disconnect();
        simulator.stop();
        csvLogger.stopLogging();
    }
}
