from __future__ import annotations

import argparse
import math
import signal
import subprocess
import sys
import threading
import time
import serial
import struct
from pathlib import Path

import imu_button_pb2

CURRENT_DIR = Path(__file__).resolve().parent
PRODUCTION_DIR = CURRENT_DIR.parent
if str(PRODUCTION_DIR) not in sys.path:
    sys.path.insert(0, str(PRODUCTION_DIR))

from Shared.event_envelope_emitter import EventEnvelopeEmitter

PORT = "/dev/serial0"
BAUD = 115200

FRAME_MAGIC_0 = 0xA5
FRAME_MAGIC_1 = 0x5A

RTP_PORT = 5004
SDP_FILE = "rtp_pcm.sdp"
FFMPEG_BIN = "ffmpeg"

ACCEL_LSB_PER_G = 16384.0
GYRO_LSB_PER_DPS = 131.0
DEFAULT_FALL_ACCEL_G = 2.7
DEFAULT_FALL_GYRO_DPS = 180.0
DEFAULT_FALL_COOLDOWN_SECONDS = 8.0


class SensorEventInterpreter:
    def __init__(
        self,
        emitter: EventEnvelopeEmitter | None,
        fall_accel_g: float,
        fall_gyro_dps: float,
        fall_cooldown_seconds: float,
    ) -> None:
        self._emitter = emitter
        self._fall_accel_g = fall_accel_g
        self._fall_gyro_dps = fall_gyro_dps
        self._fall_cooldown_seconds = fall_cooldown_seconds
        self._last_fall_emit_monotonic = 0.0

    def handle_frame(self, msg: imu_button_pb2.ImuButtonFrame) -> None:
        accel_g = vector_magnitude(msg.accel_x, msg.accel_y, msg.accel_z) / ACCEL_LSB_PER_G
        gyro_dps = vector_magnitude(msg.gyro_x, msg.gyro_y, msg.gyro_z) / GYRO_LSB_PER_DPS

        print(
            f"t={msg.ts_ms} ms button={msg.button_pressed} edge={msg.button_edge} "
            f"acc=({msg.accel_x},{msg.accel_y},{msg.accel_z}) |a|={accel_g:.2f}g "
            f"gyro=({msg.gyro_x},{msg.gyro_y},{msg.gyro_z}) |w|={gyro_dps:.1f}dps"
        )

        if not self._emitter:
            return

        if msg.button_edge:
            ok = self._emitter.emit(
                "EMERGENCY",
                {"trigger_source": "wearable_button", "fsm_state": "EMERGENCY_REQUESTED"},
                priority="CRITICAL",
            )
            print("emitted EMERGENCY button event" if ok else "failed to emit EMERGENCY button event")

        if self._is_possible_fall(accel_g, gyro_dps):
            payload = {
                "accel_magnitude": round(accel_g, 3),
                "gyro_magnitude": round(gyro_dps, 3),
                "button_pressed": bool(msg.button_pressed),
            }
            self._last_fall_emit_monotonic = time.monotonic()
            ok = self._emitter.emit("FALL", payload, priority="HIGH")
            if ok:
                print("emitted FALL event", payload)
            else:
                print("failed to emit FALL event", payload)

    def _is_possible_fall(self, accel_g: float, gyro_dps: float) -> bool:
        if time.monotonic() - self._last_fall_emit_monotonic < self._fall_cooldown_seconds:
            return False
        return accel_g >= self._fall_accel_g or gyro_dps >= self._fall_gyro_dps


def vector_magnitude(x: float, y: float, z: float) -> float:
    return math.sqrt((x * x) + (y * y) + (z * z))


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


def start_ffmpeg(sdp_file: str, ffmpeg_bin: str) -> subprocess.Popen:
    cmd = [
        ffmpeg_bin,
        "-hide_banner",
        "-loglevel",
        "warning",
        "-protocol_whitelist",
        "file,udp,rtp",
        "-i",
        sdp_file,
        "-f",
        "null",
        "-",
    ]
    return subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def uart_loop(stop_event: threading.Event, port: str, baud: int, interpreter: SensorEventInterpreter) -> None:
    ser = serial.Serial(port, baud, timeout=1)
    print("UART listening on", port)

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

        interpreter.handle_frame(msg)
        time.sleep(0.001)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default=PORT, help="UART device, e.g. /dev/serial0 or /dev/ttyUSB0")
    parser.add_argument("--baud", type=int, default=BAUD, help="UART baud rate")
    parser.add_argument("--sdp", default=SDP_FILE, help="Path to RTP SDP file")
    parser.add_argument("--ffmpeg", default=FFMPEG_BIN, help="ffmpeg binary")
    parser.add_argument("--emit", default="", help="Phone intake base URL, e.g. http://192.168.1.42:8000")
    parser.add_argument("--fall-accel-g", type=float, default=DEFAULT_FALL_ACCEL_G)
    parser.add_argument("--fall-gyro-dps", type=float, default=DEFAULT_FALL_GYRO_DPS)
    parser.add_argument("--fall-cooldown-sec", type=float, default=DEFAULT_FALL_COOLDOWN_SECONDS)
    args = parser.parse_args()

    stop_event = threading.Event()
    emitter = EventEnvelopeEmitter(args.emit) if args.emit else None
    interpreter = SensorEventInterpreter(
        emitter=emitter,
        fall_accel_g=args.fall_accel_g,
        fall_gyro_dps=args.fall_gyro_dps,
        fall_cooldown_seconds=args.fall_cooldown_sec,
    )

    ffmpeg = start_ffmpeg(args.sdp, args.ffmpeg)
    print("Audio listener started on RTP port", RTP_PORT)

    t = threading.Thread(target=uart_loop, args=(stop_event, args.port, args.baud, interpreter), daemon=True)
    t.start()

    def handle_sig(_sig, _frame):
        stop_event.set()

    signal.signal(signal.SIGINT, handle_sig)
    signal.signal(signal.SIGTERM, handle_sig)

    try:
        while not stop_event.is_set():
            if ffmpeg.poll() is not None:
                ffmpeg = start_ffmpeg(args.sdp, args.ffmpeg)
            time.sleep(0.5)
    finally:
        stop_event.set()
        if ffmpeg and ffmpeg.poll() is None:
            ffmpeg.terminate()
        if emitter:
            emitter.close()


if __name__ == "__main__":
    main()
