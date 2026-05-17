# Combined Stream (ESP32 -> RPi)

This folder is the production-ready combined hardware path.

It does two things at once:

1. Streams microphone audio from the ESP32 to the Raspberry Pi over RTP.
2. Sends IMU and button events from the ESP32 to the Raspberry Pi over UART.

The Pi listener is the bridge that feeds those signals into the phone pipeline.

## Files

- ESP32_CombinedStream.ino
- WIFI_CONFIG.example.h
- imu_button.proto
- imu_button.options
- rtp_pcm.sdp
- rpi_listener.py
- requirements.txt

## What the transport looks like

- Audio: RTP over Wi-Fi to the Pi on UDP port 5004.
- Sensor events: framed protobuf messages over UART.
- Phone forwarding: the Pi uses the shared EventEnvelope emitter in `hardware/Production/Shared`.

## Sensor event logic

The ESP32 sends raw MPU6050 readings. The Pi listener converts them into phone-safe EventEnvelopes instead of forwarding every raw frame:

- Button rising edge -> `EMERGENCY`, priority `CRITICAL`, payload `{"trigger_source":"wearable_button","fsm_state":"EMERGENCY_REQUESTED"}`.
- Possible fall spike -> `FALL`, priority `HIGH`, payload with `accel_magnitude`, `gyro_magnitude`, and `button_pressed`.
- Normal 50 Hz IMU frames are printed locally for debugging but are not POSTed to the phone.

Default thresholds:

- Acceleration magnitude: `2.7g`
- Gyro magnitude: `180 dps`
- Fall emit cooldown: `8 seconds`

Tune them with `--fall-accel-g`, `--fall-gyro-dps`, and `--fall-cooldown-sec`.

## Wiring

ESP32 (UART2) -> Raspberry Pi UART0

- ESP32 TX2 (GPIO18) -> Pi RXD (GPIO15)
- ESP32 RX2 (GPIO19) -> Pi TXD (GPIO14)
- GND -> GND

Button: GPIO4 to GND (INPUT_PULLUP)
MPU6050: SDA=GPIO21, SCL=GPIO22

The firmware enables `INPUT_PULLUP` for the button, so the normal unpressed state is HIGH and a press pulls GPIO4 to GND. Button readings are debounced in firmware for 60 ms before a rising press edge is sent to the Pi.

## ESP32 setup (Arduino IDE)

1) Copy WIFI_CONFIG.example.h to WIFI_CONFIG.h and fill Wi-Fi + Pi IP.
2) Generate nanopb files from imu_button.proto:

   - Install nanopb generator (Python):
     - pip install nanopb

   - Generate:
     - py -m nanopb.generator.nanopb_generator imu_button.proto

   This produces imu_button.pb.h and imu_button.pb.c.

3) Place imu_button.pb.h and imu_button.pb.c next to the sketch.
4) Build and flash ESP32_CombinedStream.ino.

## Beginner device test checklist

Follow this order if you are testing with real hardware for the first time.

1. Prepare the phone server.
   - Start the FastAPI phone core on a machine that the Pi can reach.
   - Confirm `/health` works before doing anything with hardware.

2. Prepare the Pi.
   - Install Python dependencies in a venv.
   - Make sure `ffmpeg` is installed.
   - Open the serial port permissions if needed.

3. Prepare the ESP32.
   - Copy `WIFI_CONFIG.example.h` to `WIFI_CONFIG.h`.
   - Set the Wi-Fi credentials and Pi IP address.
   - Flash the combined stream sketch.

4. Wire the board correctly.
   - UART pins must match the diagram above.
   - Button should go to ground with `INPUT_PULLUP`.
   - MPU6050 should be on the expected I2C pins.

5. Start the Pi listener without phone forwarding first.
   - Run `python rpi_listener.py`.
   - You should see the UART listener and RTP listener start.

6. Verify audio.
   - Speak near the microphones.
   - Confirm the Pi is receiving the RTP stream.

7. Verify IMU and button.
   - Move the device.
   - Press the button.
   - Confirm the Pi prints the sensor frame values.

8. Connect to the phone.
   - Run `python rpi_listener.py --emit http://<phone-ip>:8000`.
   - Press the wearable button and confirm the phone stores an `EMERGENCY` event.
   - Move the device sharply enough to cross the fall threshold and confirm the phone stores a `FALL` event.

## Raspberry Pi setup

1) Create and activate a venv:

   ```
   python3 -m venv .venv
   source .venv/bin/activate
   ```

2) Install deps:

   ```
   pip install -r requirements.txt
   ```

3) Generate Python protobuf:

   ```
   python -m grpc_tools.protoc -I. --python_out=. imu_button.proto
   ls imu_button_pb2.py
   ```

   `rpi_listener.py` imports `imu_button_pb2.py`, so this generation step is required after a fresh clone. If you see `ModuleNotFoundError: No module named 'imu_button_pb2'`, run this command again from this folder.

4) Run listener:

   ```
   python rpi_listener.py
   ```

5) Run listener with phone forwarding:

   ```
   python rpi_listener.py --emit http://<phone-ip>:8000
   ```

Useful options:

- `--port /dev/ttyUSB0` if you are using a USB-UART adapter instead of Pi UART0.
- `--fall-accel-g 3.0` to make fall detection less sensitive.
- `--fall-gyro-dps 220` to make rotation spikes less sensitive.
- `--button-cooldown-sec 3.0` to ignore repeated button edges for longer after one emergency emit.

## Notes

- UART frame format: 0xA5 0x5A + uint16 length (LE) + protobuf payload + uint16 CRC16-CCITT (LE).
- Audio format: 16 kHz, stereo, signed 16-bit PCM (L16) over RTP.
- The listener auto-restarts ffmpeg if it exits.
- For phone integration, keep the phone core on the network and point the Pi emitter at `http://<phone-ip>:8000`.
- If the button emits without being pressed, confirm the switch is wired between GPIO4 and GND, not GPIO4 and 3.3V. Keep the wire short or add an external 10k pull-up to 3.3V if the enclosure wiring is noisy.
