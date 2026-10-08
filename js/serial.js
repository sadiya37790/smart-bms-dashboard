/**
 * Web Serial API Manager for ESP32 USB Connection (Chrome & Edge)
 */

import { BmsSample } from './model.js';

export class WebSerialService {
    constructor(onSampleCallback = null, onStatusCallback = null) {
        this.onSampleCallback = onSampleCallback;
        this.onStatusCallback = onStatusCallback;

        this.port = null;
        this.reader = null;
        this.readableStreamClosed = null;
        this.connected = false;
        this.keepReading = false;
    }

    static isSupported() {
        return 'serial' in navigator;
    }

    async connect(baudRate = 115200) {
        if (!WebSerialService.isSupported()) {
            this.emitStatus('Web Serial API is not supported in this browser. Use Chrome or Edge.');
            return false;
        }

        try {
            this.port = await navigator.serial.requestPort();
            await this.port.open({ baudRate: parseInt(baudRate, 10) || 115200 });

            this.connected = true;
            this.keepReading = true;
            this.emitStatus('Connected to USB Serial Device');

            this.readLoop();
            return true;
        } catch (err) {
            console.error('[WebSerialService] Connection error:', err);
            this.emitStatus('Failed to connect: ' + err.message);
            this.connected = false;
            return false;
        }
    }

    async disconnect() {
        this.keepReading = false;
        if (this.reader) {
            try {
                await this.reader.cancel();
            } catch (e) { /* ignore */ }
        }
        if (this.port) {
            try {
                await this.port.close();
            } catch (e) { /* ignore */ }
        }
        this.port = null;
        this.reader = null;
        this.connected = false;
        this.emitStatus('Disconnected');
    }

    async readLoop() {
        const textDecoder = new TextDecoderStream();
        this.readableStreamClosed = this.port.readable.pipeTo(textDecoder.writable);
        const reader = textDecoder.readable.getReader();
        this.reader = reader;

        let buffer = '';

        try {
            while (this.keepReading) {
                const { value, done } = await reader.read();
                if (done) {
                    break;
                }
                if (value) {
                    buffer += value;
                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // Keep partial trailing line in buffer

                    for (let line of lines) {
                        line = line.trim();
                        if (line.length === 0) continue;

                        const sample = BmsSample.parseJson(line);
                        if (sample && this.onSampleCallback) {
                            this.onSampleCallback(sample);
                        }
                    }
                }
            }
        } catch (error) {
            console.error('[WebSerialService] Read loop error:', error);
        } finally {
            reader.releaseLock();
            this.connected = false;
            this.emitStatus('Serial connection closed');
        }
    }

    emitStatus(message) {
        if (this.onStatusCallback) {
            this.onStatusCallback(message);
        }
    }
}
