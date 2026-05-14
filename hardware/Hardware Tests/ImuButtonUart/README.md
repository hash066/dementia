# IMU + Button UART (ESP32 -> RPi)

Goal: send MPU6050 + button events over UART using Protobuf (nanopb on ESP32, Python on RPi).

## Files

- imu_button.proto
- imu_button.options
- ImuButtonUart.ino
- rpi_uart_receiver.py

## Wiring

ESP32 (UART2) -> Raspberry Pi UART0

- ESP32 TX2 (GPIO18) -> Pi RXD (GPIO15)
- ESP32 RX2 (GPIO19) -> Pi TXD (GPIO14)
- GND -> GND

Important:
- The ESP32 side uses GPIO18/19 for UART2 as requested.
- The Pi side remains on GPIO14/15 for the hardware UART.

Button: GPIO4 to GND (INPUT_PULLUP)
MPU6050: SDA=GPIO21, SCL=GPIO22

## ESP32 setup (Arduino IDE)

1) Install nanopb from Arduino Library Manager.
2) Generate nanopb files from imu_button.proto:

   - Install nanopb generator (Python):
     - pip install nanopb

   - Generate:
     - python -m nanopb_generator imu_button.proto

   This produces imu_button.pb.h and imu_button.pb.c.

3) Place imu_button.pb.h and imu_button.pb.c in this folder.
4) Build and flash ImuButtonUart.ino.

## Raspberry Pi setup

1) Install deps:
   - pip install protobuf pyserial

2) Generate Python protobuf:
   - python -m grpc_tools.protoc -I. --python_out=. imu_button.proto

3) Run receiver:
   - python rpi_uart_receiver.py

## Notes

- Frame format: 0xA5 0x5A + uint16 length (LE) + protobuf payload + uint16 CRC16-CCITT (LE).
- Default UART is /dev/serial0; adjust PORT as needed.
- If Pi serial console is enabled, disable it to use UART.
