import serial
import struct
import time

import imu_button_pb2

PORT = "/dev/serial0"  # update if using USB-UART or a different serial device
BAUD = 115200

FRAME_MAGIC_0 = 0xA5
FRAME_MAGIC_1 = 0x5A


def crc16_ccitt(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= (b << 8) & 0xFFFF
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc


ser = serial.Serial(PORT, BAUD, timeout=1)

print("Listening on", PORT)

while True:
    b0 = ser.read(1)
    if not b0:
        continue
    if b0[0] != FRAME_MAGIC_0:
        continue

    b1 = ser.read(1)
    if len(b1) < 1 or b1[0] != FRAME_MAGIC_1:
        continue

    len_bytes = ser.read(2)
    if len(len_bytes) < 2:
        continue
    (length,) = struct.unpack("<H", len_bytes)

    data = ser.read(length)
    if len(data) < length:
        continue

    crc_bytes = ser.read(2)
    if len(crc_bytes) < 2:
        continue
    (crc_rx,) = struct.unpack("<H", crc_bytes)

    if crc16_ccitt(data) != crc_rx:
        continue

    msg = imu_button_pb2.ImuButtonFrame()
    msg.ParseFromString(data)

    print(
        f"t={msg.ts_ms} ms button={msg.button_pressed} edge={msg.button_edge} "
        f"acc=({msg.accel_x},{msg.accel_y},{msg.accel_z}) "
        f"gyro=({msg.gyro_x},{msg.gyro_y},{msg.gyro_z})"
    )
    time.sleep(0.001)
