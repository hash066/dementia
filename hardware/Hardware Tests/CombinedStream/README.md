# Combined Stream (ESP32 -> RPi)

Goal: run a single ESP32 firmware that streams PCM audio over RTP and IMU/button data over UART, with a single Pi listener script.

## Files

- ESP32_CombinedStream.ino
- WIFI_CONFIG.example.h
- imu_button.proto
- imu_button.options
- rtp_pcm.sdp
- rpi_listener.py

## Wiring

ESP32 (UART2) -> Raspberry Pi UART0

- ESP32 TX2 (GPIO18) -> Pi RXD (GPIO15)
- ESP32 RX2 (GPIO19) -> Pi TXD (GPIO14)
- GND -> GND

Button: GPIO4 to GND (INPUT_PULLUP)
MPU6050: SDA=GPIO21, SCL=GPIO22

## ESP32 setup (Arduino IDE)

1) Copy WIFI_CONFIG.example.h to WIFI_CONFIG.h and fill Wi-Fi + Pi IP.
2) Generate nanopb files from imu_button.proto:

   - Install nanopb generator (Python):
     - pip install nanopb

   - Generate:
     - python -m nanopb_generator imu_button.proto

   This produces imu_button.pb.h and imu_button.pb.c.

3) Place imu_button.pb.h and imu_button.pb.c next to the sketch.
4) Build and flash ESP32_CombinedStream.ino.

## Raspberry Pi setup

1) Install deps (venv recommended):
   - pip install protobuf pyserial

2) Generate Python protobuf:
   - python -m grpc_tools.protoc -I. --python_out=. imu_button.proto

3) Run listener:
   - python rpi_listener.py

## Notes

- UART frame format: 0xA5 0x5A + uint16 length (LE) + protobuf payload + uint16 CRC16-CCITT (LE).
- Audio format: 16 kHz, stereo, signed 16-bit PCM (L16) over RTP.
- The listener auto-restarts ffmpeg if it exits.
