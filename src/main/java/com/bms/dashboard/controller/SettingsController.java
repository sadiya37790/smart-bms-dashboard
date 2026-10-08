package com.bms.dashboard.controller;

import com.bms.dashboard.model.ProtectionSettings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SettingsController {
    @FXML private TextField overchargeField;
    @FXML private TextField overdischargeField;
    @FXML private TextField overcurrentField;
    @FXML private TextField overtempField;

    private ProtectionSettings protectionSettings;
    private Runnable onSettingsApplied;

    public void setProtectionSettings(ProtectionSettings settings, Runnable onApplied) {
        this.protectionSettings = settings;
        this.onSettingsApplied = onApplied;
        loadCurrentValues();
    }

    private void loadCurrentValues() {
        if (protectionSettings == null) return;
        overchargeField.setText(String.format("%.2f", protectionSettings.getOverchargeThreshold()));
        overdischargeField.setText(String.format("%.2f", protectionSettings.getOverdischargeThreshold()));
        overcurrentField.setText(String.format("%.2f", protectionSettings.getOvercurrentThreshold()));
        overtempField.setText(String.format("%.1f", protectionSettings.getOvertempThreshold()));
    }

    @FXML
    private void handleApply() {
        try {
            double overcharge = Double.parseDouble(overchargeField.getText().trim());
            double overdischarge = Double.parseDouble(overdischargeField.getText().trim());
            double overcurrent = Double.parseDouble(overcurrentField.getText().trim());
            double overtemp = Double.parseDouble(overtempField.getText().trim());

            if (overcharge <= overdischarge) {
                showError("Overcharge threshold must be strictly greater than over-discharge threshold.");
                return;
            }
            if (overcharge <= 0 || overdischarge <= 0 || overcurrent <= 0 || overtemp <= 0) {
                showError("Threshold values must be positive numbers.");
                return;
            }

            protectionSettings.setOverchargeThreshold(overcharge);
            protectionSettings.setOverdischargeThreshold(overdischarge);
            protectionSettings.setOvercurrentThreshold(overcurrent);
            protectionSettings.setOvertempThreshold(overtemp);

            if (onSettingsApplied != null) {
                onSettingsApplied.run();
            }

            closeWindow();
        } catch (NumberFormatException e) {
            showError("Invalid numeric input. Please enter valid numbers for all thresholds.");
        }
    }

    @FXML
    private void handleRestoreDefaults() {
        if (protectionSettings != null) {
            protectionSettings.restoreDefaults();
            loadCurrentValues();
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) overchargeField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Invalid Settings");
        alert.setHeaderText("Threshold Validation Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
