package com.bms.dashboard.service;

import com.bms.dashboard.model.BmsSample;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SerialService {
    private SerialPort activePort;
    private ExecutorService readExecutor;
    private volatile boolean running = false;
    private Consumer<BmsSample> sampleListener;
    private Consumer<String> statusListener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> getAvailablePorts() {
        List<String> ports = new ArrayList<>();
        SerialPort[] commPorts = SerialPort.getCommPorts();
        for (SerialPort port : commPorts) {
            ports.add(port.getSystemPortName());
        }
        return ports;
    }

    public synchronized boolean connect(String portName, int baudRate, Consumer<BmsSample> sampleListener, Consumer<String> statusListener) {
        disconnect();

        this.sampleListener = sampleListener;
        this.statusListener = statusListener;

        SerialPort targetPort = null;
        for (SerialPort port : SerialPort.getCommPorts()) {
            if (port.getSystemPortName().equalsIgnoreCase(portName) || port.getDescriptivePortName().contains(portName)) {
                targetPort = port;
                break;
            }
        }

        if (targetPort == null) {
            if (statusListener != null) {
                statusListener.accept("Port " + portName + " not found");
            }
            return false;
        }

        targetPort.setBaudRate(baudRate);
        targetPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

        if (!targetPort.openPort()) {
            if (statusListener != null) {
                statusListener.accept("Failed to open " + portName);
            }
            return false;
        }

        this.activePort = targetPort;
        this.running = true;
        this.readExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BMS-SerialReader");
            t.setDaemon(true);
            return t;
        });

        readExecutor.submit(this::readLoop);

        if (statusListener != null) {
            statusListener.accept("Connected to " + portName + " @ " + baudRate + " baud");
        }
        return true;
    }

    public synchronized void disconnect() {
        running = false;
        if (readExecutor != null && !readExecutor.isShutdown()) {
            readExecutor.shutdownNow();
            readExecutor = null;
        }
        if (activePort != null && activePort.isOpen()) {
            activePort.closePort();
            if (statusListener != null) {
                statusListener.accept("Disconnected from serial port");
            }
        }
        activePort = null;
    }

    private void readLoop() {
        if (activePort == null || !activePort.isOpen()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activePort.getInputStream(), StandardCharsets.UTF_8))) {
            while (running && activePort.isOpen()) {
                String line = reader.readLine();
                if (line == null) {
                    // Serial port disconnected or EOF
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    BmsSample sample = objectMapper.readValue(line, BmsSample.class);
                    if (sampleListener != null) {
                        sampleListener.accept(sample);
                    }
                } catch (Exception parseEx) {
                    System.err.println("[SerialService] Malformed JSON line skipped: '" + line + "' -> " + parseEx.getMessage());
                }
            }
        } catch (Exception ex) {
            System.err.println("[SerialService] Error reading from serial: " + ex.getMessage());
        } finally {
            if (running) {
                running = false;
                if (statusListener != null) {
                    statusListener.accept("Serial port connection lost");
                }
            }
        }
    }

    public boolean isConnected() {
        return activePort != null && activePort.isOpen() && running;
    }

    public String getPortName() {
        return activePort != null ? activePort.getSystemPortName() : "N/A";
    }
}
