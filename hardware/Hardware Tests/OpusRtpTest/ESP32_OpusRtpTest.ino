#include <WiFi.h>
#include <WiFiUdp.h>
#include <driver/i2s.h>

#include "WIFI_CONFIG.h"

#define I2S_WS 25
#define I2S_SD 33
#define I2S_SCK 26

#define SAMPLE_RATE 16000
#define CHANNELS 2
#define I2S_PORT I2S_NUM_0

// 10 ms frame @ 16 kHz
#define FRAME_SAMPLES 160
#define I2S_READ_SAMPLES (FRAME_SAMPLES * CHANNELS)

const uint16_t RPI_PORT = 5004;

WiFiUDP udp;

static uint16_t rtp_seq = 0;
static uint32_t rtp_ts = 0;
static uint32_t rtp_ssrc = 0x12345678;
static uint32_t packet_count = 0;

void setupI2S() {
  i2s_config_t config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
    .sample_rate = SAMPLE_RATE,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT,
    .channel_format = I2S_CHANNEL_FMT_RIGHT_LEFT,
    .communication_format = I2S_COMM_FORMAT_I2S,
    .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
    .dma_buf_count = 8,
    .dma_buf_len = 256,
    .use_apll = false
  };

  i2s_pin_config_t pins = {
    .bck_io_num = I2S_SCK,
    .ws_io_num = I2S_WS,
    .data_out_num = I2S_PIN_NO_CHANGE,
    .data_in_num = I2S_SD
  };

  i2s_driver_install(I2S_PORT, &config, 0, NULL);
  i2s_set_pin(I2S_PORT, &pins);
  i2s_zero_dma_buffer(I2S_PORT);
}

void waitForWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  while (WiFi.status() != WL_CONNECTED) {
    delay(200);
  }
}

void buildRtpHeader(uint8_t* hdr, uint8_t payloadType, uint16_t seq, uint32_t ts, uint32_t ssrc) {
  hdr[0] = 0x80; // V=2, P=0, X=0, CC=0
  hdr[1] = payloadType & 0x7F;
  hdr[2] = (seq >> 8) & 0xFF;
  hdr[3] = seq & 0xFF;
  hdr[4] = (ts >> 24) & 0xFF;
  hdr[5] = (ts >> 16) & 0xFF;
  hdr[6] = (ts >> 8) & 0xFF;
  hdr[7] = ts & 0xFF;
  hdr[8] = (ssrc >> 24) & 0xFF;
  hdr[9] = (ssrc >> 16) & 0xFF;
  hdr[10] = (ssrc >> 8) & 0xFF;
  hdr[11] = ssrc & 0xFF;
}

void setup() {
  Serial.begin(115200);
  delay(500);

  setupI2S();
  waitForWiFi();
  udp.begin(RPI_PORT);
}

void loop() {
  int32_t i2s_raw[I2S_READ_SAMPLES];
  int16_t pcm16[I2S_READ_SAMPLES];
  size_t bytesRead = 0;

  i2s_read(I2S_PORT, &i2s_raw, sizeof(i2s_raw), &bytesRead, portMAX_DELAY);

  int count = bytesRead / 4;
  for (int i = 0; i < count; i++) {
    int32_t v = i2s_raw[i] >> 8; // drop low noise bits
    pcm16[i] = (int16_t)(v >> 8);
  }

  uint8_t rtp[12];
  const uint8_t payloadType = 96; // L16/16000/2 (matches rtp_pcm.sdp)
  buildRtpHeader(rtp, payloadType, rtp_seq++, rtp_ts, rtp_ssrc);

  udp.beginPacket(RPI_IP, RPI_PORT);
  udp.write(rtp, 12);
  udp.write((uint8_t*)pcm16, FRAME_SAMPLES * CHANNELS * 2);
  udp.endPacket();

  rtp_ts += FRAME_SAMPLES;
  packet_count++;
  if ((packet_count % 100) == 0) {
    Serial.printf("Sent RTP packets: %lu\n", (unsigned long)packet_count);
  }
}
