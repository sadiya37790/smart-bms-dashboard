# Smart BMS Desktop Dashboard (JavaFX)

A high-performance JavaFX desktop dashboard for monitoring a 4-cell (4S) Lithium-ion Smart Battery Management System (BMS) over ESP32 serial communication (`jSerialComm`). Built with an integrated real-time simulator, instant fault injection controls, threshold settings dialog, alert logging, and CSV export.

---

## 🚀 Quick Start & Run Instructions

### Prerequisites
- **Java 17** or later (Java 21 supported)
- **Apache Maven 3.8+**

### How to Run
Navigate to the project root directory and execute:

```bash
mvn clean javafx:run
```

---

## ⚡ Key Features

1. **Hardware & Simulator Telemetry Modes**:
   - **Hardware Serial**: Connects to ESP32 microcontrollers via `jSerialComm` at customizable baud rates (default `115200`).
   - **Integrated Simulator**: Generates synthetic 4S pack telemetry (~2 Hz) with charge/discharge cycles, temperature heat-up, and cell voltage drift.
2. **Instant Fault Injection Controls**:
   - Live fault toggle buttons:
     - **Force Overcharge**: Drives Cell 4 voltage to `> 4.20 V`.
     - **Force Over-discharge**: Drops Cell 1 voltage to `< 2.80 V`.
     - **Force Overcurrent**: Spikes pack current to `> 5.0 A`.
     - **Force Over-temp**: Heats pack temperature to `> 55.0 °C`.
3. **Hardware Safety Protection Logic & Latched Trips**:
   - Automatically computes FET control signals:
     - **Charge FET OFF**: Triggered by Overcharge, Overcurrent, or Over-temperature.
     - **Discharge FET OFF**: Triggered by Over-discharge, Overcurrent, or Over-temperature.
   - Trips latch automatically until cleared by clicking **"Reset Faults"** while pack parameters are within safe ranges.
4. **Stale Data Monitoring & Safeguards**:
   - Connection status pill turns **amber** if no telemetry packet is received for `> 3s`, and **red** after `> 10s`.
   - Live metrics are greyed out to prevent stale readings from being mistaken for active telemetry.
5. **Real-Time Rolling Charts**:
   - 120-second rolling trend line charts for Voltage/Current and Temperature with automatic memory cleanup.
6. **Data Logging & Export**:
   - Real-time background logging to CSV files (`bms_telemetry_*.csv`) and one-click CSV export.

---

## 📡 ESP32 Data Protocol (JSON Format)

The dashboard expects JSON telemetry lines emitted over UART serial at ~2 Hz:

```json
{"v":[4.01,3.98,4.02,3.99],"i":-1.85,"t":31.4,"ts":1699999999}
```

### JSON Schema Breakdown
- `"v"`: Array of 4 cell voltages in Volts (e.g. `[4.01, 3.98, 4.02, 3.99]`).
- `"i"`: Signed pack current in Amperes (`+` = charging, `-` = discharging).
- `"t"`: Pack temperature in °C.
- `"ts"`: Unix epoch timestamp in seconds.

---

## 🛠️ ESP32 Arduino C++ Code Example

Below is a complete, ready-to-flash Arduino C++ sketch for ESP32 to read cell voltages via ADC / ADS1115 and emit the JSON telemetry:

```cpp
#include <Arduino.h>

// ESP32 Serial Telemetry Emitter for Smart BMS Dashboard
void setup() {
    Serial.begin(115200);
    while (!Serial) { delay(10); }
}

void loop() {
    // Read cell voltages (V), current (A), and temperature (°C)
    float v1 = 3.95 + (random(-20, 20) / 1000.0);
    float v2 = 3.98 + (random(-20, 20) / 1000.0);
    float v3 = 3.96 + (random(-20, 20) / 1000.0);
    float v4 = 3.97 + (random(-20, 20) / 1000.0);

    float current = -1.85 + (random(-10, 10) / 100.0); // Discharging
    float temp = 31.4 + (random(-5, 5) / 10.0);
    unsigned long timestamp = millis() / 1000;

    // Format single line JSON string
    char jsonBuffer[128];
    snprintf(jsonBuffer, sizeof(jsonBuffer),
             "{\"v\":[%.2f,%.2f,%.2f,%.2f],\"i\":%.2f,\"t\":%.1f,\"ts\":%lu}",
             v1, v2, v3, v4, current, temp, timestamp);

    // Emit JSON line over UART Serial
    Serial.println(jsonBuffer);

    delay(500); // ~2 Hz sampling rate
}
```

---

## 📂 Project Structure

```
bms-dashboard/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── bms/
        │           └── dashboard/
        │               ├── MainApp.java
        │               ├── model/
        │               │   ├── BmsSample.java
        │               │   ├── DerivedBmsData.java
        │               │   ├── ProtectionSettings.java
        │               │   └── AlertEntry.java
        │               ├── service/
        │               │   ├── SerialService.java
        │               │   ├── BmsSimulator.java
        │               │   └── CsvLogger.java
        │               └── controller/
        │                   ├── MainController.java
        │                   └── SettingsController.java
        └── resources/
            └── com/
                └── bms/
                    └── dashboard/
                        ├── view/
                        │   ├── MainView.fxml
                        │   └── SettingsView.fxml
                        └── styles/
                            └── dark-theme.css
```
