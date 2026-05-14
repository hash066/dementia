import os
import signal
import subprocess
import threading
import time
import serial
import struct

import imu_button_pb2

PORT = "/dev/serial0"
BAUD = 115200

FRAME_MAGIC_0 = 0xA5
FRAME_MAGIC_1 = 0x5A

RTP_PORT = 5004
SDP_FILE = "rtp_pcm.sdp"
FFMPEG_BIN = "ffmpeg"


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


def start_ffmpeg() -> subprocess.Popen:
    cmd = [
        FFMPEG_BIN,
        "-hide_banner",
        "-loglevel",
        "warning",
        "-protocol_whitelist",
        "file,udp,rtp",
        "-i",
        SDP_FILE,
        "-f",
        "null",
        "-",
    ]
    return subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def uart_loop(stop_event: threading.Event):
    ser = serial.Serial(PORT, BAUD, timeout=1)
    print("UART listening on", PORT)

    while not stop_event.is_set():
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


def main():
    stop_event = threading.Event()

    ffmpeg = start_ffmpeg()
    print("Audio listener started on RTP port", RTP_PORT)

    t = threading.Thread(target=uart_loop, args=(stop_event,), daemon=True)
    t.start()

    def handle_sig(_sig, _frame):
        stop_event.set()

    signal.signal(signal.SIGINT, handle_sig)
    signal.signal(signal.SIGTERM, handle_sig)

    try:
        while not stop_event.is_set():
            if ffmpeg.poll() is not None:
                ffmpeg = start_ffmpeg()
            time.sleep(0.5)
    finally:
        stop_event.set()
        if ffmpeg and ffmpeg.poll() is None:
            ffmpeg.terminate()


if __name__ == "__main__":
    main()
