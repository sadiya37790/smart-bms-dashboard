# Smart BMS Web Dashboard (Vercel Ready)

A modern, high-performance web dashboard for monitoring a Lithium-ion Smart Battery Management System (BMS). Features live **Web Serial API** support for physical ESP32 microcontrollers, **Dynamic Hardware Battery Rating & Cutoff Auto-Sync**, an integrated real-time simulator with fault injection controls, Chart.js rolling trend charts, alert logging, and CSV export. Ready for 1-click deployment on **Vercel**.

---

## 🚀 Live Demo & Deployment to Vercel

### Deploying via GitHub to Vercel (Recommended)
1. Push this repository to your GitHub account (`sadiya37790/smart-bms-dashboard`).
2. Go to [Vercel Dashboard](https://vercel.com/new).
3. Select **Import Project** and choose your GitHub repository `smart-bms-dashboard`.
4. Click **Deploy**. Vercel will automatically host your live web app with a custom URL (e.g., `smart-bms-dashboard.vercel.app`)!

---

## ⚡ Key Web Features

1. **Dynamic Hardware Rating & Cutoff Auto-Sync**:
   - When the hardware prototype updates its battery ratings or cutoffs, it sends a `"cfg"` JSON object in its serial payload:
     ```json
     {"v":[4.01,3.98,4.02,3.99],"i":-1.85,"t":31.4,"ts":1699999999,"cfg":{"max_v":4.25,"min_v":2.75,"max_i":6.0,"max_t":60.0}}
     ```
   - The dashboard **automatically detects the rating changes and updates protection thresholds, progress bar scales, and safe limits in real time!**
2. **🔌 Web Serial API Support**:
   - Connect physical ESP32 devices directly to Chrome or Edge via USB serial port without installing any local drivers or server software.
3. **🎮 Integrated Simulator & 4 Fault Injection Controls**:
   - Test protections live without hardware using the built-in simulator:
     - **Force Overcharge**: Spikes Cell 4 voltage to $> 4.20\,\text{V}$.
     - **Force Over-discharge**: Drops Cell 1 voltage to $< 2.80\,\text{V}$.
     - **Force Overcurrent**: Spikes pack current to $> 5.0\,\text{A}$.
     - **Force Over-temp**: Heats pack temperature to $> 55.0\,^\circ\text{C}$.
4. **🛡️ Protection Logic & Latched Trips**:
   - **Charge FET OFF**: Overcharge, Overcurrent, or Over-temp.
   - **Discharge FET OFF**: Over-discharge, Overcurrent, or Over-temp.
   - **Safety Reset**: Trips remain latched until clicking **"Reset Faults"** while pack parameters are within safe ranges.
5. **📈 Rolling 120s Line Charts & Memory Optimization**:
   - Chart.js line plots for Voltage, Current, and Temperature with automatic pruning of data points older than 120 seconds.
6. **📊 Alert Log Table & CSV Exporter**:
   - Capped at 200 rows with severity color tinting and instant browser CSV file download.

---

## 📡 ESP32 Data Payload Specification

### Hardware Config Auto-Sync Payload
```json
{"v":[4.01,3.98,4.02,3.99],"i":-1.85,"t":31.4,"ts":1699999999,"cfg":{"max_v":4.25,"min_v":2.75,"max_i":6.0,"max_t":60.0}}
```

- `"v"`: Array of 4 cell voltages in Volts (e.g. `[4.01, 3.98, 4.02, 3.99]`).
- `"i"`: Signed pack current in Amperes (`+` = charging, `-` = discharging).
- `"t"`: Pack temperature in °C.
- `"ts"`: Unix timestamp in seconds.
- `"cfg"` *(Optional)*: Hardware ratings sync (`max_v` overcharge cutoff, `min_v` over-discharge cutoff, `max_i` overcurrent cutoff, `max_t` over-temp cutoff).

---

## 🛠️ ESP32 Arduino C++ Code Example

Below is a complete, ready-to-flash Arduino C++ sketch for ESP32 emitting telemetry with dynamic hardware configuration sync:

```cpp
#include <Arduino.h>

// ESP32 Serial Emitter with Dynamic Battery Rating Sync
const float MAX_CELL_V = 4.25; // Overcharge cutoff
const float MIN_CELL_V = 2.75; // Over-discharge cutoff
const float MAX_CURRENT = 6.0;  // Overcurrent cutoff
const float MAX_TEMP = 60.0;   // Over-temp cutoff

void setup() {
    Serial.begin(115200);
    while (!Serial) { delay(10); }
}

void loop() {
    // Read cell voltages, current, and temperature
    float v1 = 3.95 + (random(-20, 20) / 1000.0);
    float v2 = 3.98 + (random(-20, 20) / 1000.0);
    float v3 = 3.96 + (random(-20, 20) / 1000.0);
    float v4 = 3.97 + (random(-20, 20) / 1000.0);

    float current = -1.85 + (random(-10, 10) / 100.0);
    float temp = 31.4 + (random(-5, 5) / 10.0);
    unsigned long timestamp = millis() / 1000;

    // Build JSON packet with hardware rating auto-sync
    char jsonBuffer[256];
    snprintf(jsonBuffer, sizeof(jsonBuffer),
             "{\"v\":[%.2f,%.2f,%.2f,%.2f],\"i\":%.2f,\"t\":%.1f,\"ts\":%lu,\"cfg\":{\"max_v\":%.2f,\"min_v\":%.2f,\"max_i\":%.1f,\"max_t\":%.1f}}",
             v1, v2, v3, v4, current, temp, timestamp,
             MAX_CELL_V, MIN_CELL_V, MAX_CURRENT, MAX_TEMP);

    // Emit line over UART Serial
    Serial.println(jsonBuffer);

    delay(500); // 2 Hz update rate
}
```

---

## 📂 Project Structure

```
bms-dashboard/
├── index.html                   # Main responsive HTML5 dashboard
├── vercel.json                  # Vercel deployment configuration
├── css/
│   └── styles.css               # Dark glassmorphism stylesheet
├── js/
│   ├── app.js                   # Main controller & Chart.js renderer
│   ├── model.js                 # BMS telemetry parser, SoC curve, Hardware Auto-Sync
│   ├── simulator.js             # 2 Hz telemetry simulator & fault injectors
│   ├── serial.js                # Web Serial API manager
│   └── logger.js                # Telemetry logger & CSV exporter
└── README.md
```
