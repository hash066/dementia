#!/usr/bin/env bash

set -euo pipefail

PORT="${1:-5004}"
OUT="${2:-pcm_capture.wav}"
SDP_FILE="${3:-rtp_pcm.sdp}"

# Receives RTP/PCM on UDP and decodes to WAV using ffmpeg.
ffmpeg -hide_banner -loglevel info \
  -protocol_whitelist file,udp,rtp \
  -i "${SDP_FILE}" \
  -acodec pcm_s16le -ar 16000 -ac 2 \
  -y "${OUT}"
