# RTP Audio Test (ESP32 -> RPi)

Goal: stream stereo mic audio from ESP32 to Raspberry Pi over UDP/RTP using PCM (no codec).

## Files

- ESP32_OpusRtpTest.ino
- WIFI_CONFIG.example.h
- rpi_receive_pcm.sh
- rtp_pcm.sdp

## Step-by-step setup

1) Create a local Wi-Fi config file
   - Copy WIFI_CONFIG.example.h to WIFI_CONFIG.h
   - Fill in WIFI_SSID, WIFI_PASS, and RPI_IP

2) Build/flash ESP32
   - Use the sketch ESP32_OpusRtpTest.ino

3) On Raspberry Pi, install ffmpeg if needed
   - sudo apt-get update
   - sudo apt-get install ffmpeg

4) Start receiver on the Pi
   - chmod +x rpi_receive_pcm.sh
   - ./rpi_receive_pcm.sh 5004 pcm_capture.wav

5) Power up ESP32 and listen
   - Play the WAV to verify audio:
     - aplay pcm_capture.wav

## Notes

- Audio format: 16 kHz, stereo, signed 16-bit PCM (L16).
- RTP payload type 96 is used for PCM (L16).
- Frame size is 10 ms (160 samples at 16 kHz).
- For a clean first pass, keep the Pi and ESP32 on the same Wi-Fi network.
