#include <WiFi.h>
#include <WiFiUdp.h>
#include <Wire.h>
#include <driver/i2s.h>
#include <pb_encode.h>
#include "imu_button.pb.h"
#include "WIFI_CONFIG.h"

#define MPU6050_ADDR 0x68

#define UART_PORT Serial2
#define UART_BAUD 115200
#define UART_RX 19
#define UART_TX 18

#define BUTTON_PIN 4
#define FRAME_MAGIC_0 0xA5
#define FRAME_MAGIC_1 0x5A

#define I2S_WS 25
#define I2S_SD 33
#define I2S_SCK 26

#define SAMPLE_RATE 16000
#define CHANNELS 2
#define I2S_PORT I2S_NUM_0

// 10 ms frame @ 16 kHz
#define FRAME_SAMPLES 160
#define I2S_READ_SAMPLES (FRAME_SAMPLES * CHANNELS)

const uint16_t RPI_AUDIO_PORT = 5004;

WiFiUDP udp;

static uint16_t rtp_seq = 0;
static uint32_t rtp_ts = 0;
static uint32_t rtp_ssrc = 0x12345678;
static uint32_t packet_count = 0;

static uint32_t last_imu_send_ms = 0;
static const uint32_t BUTTON_DEBOUNCE_MS = 60;
static bool stable_button_state = false;
static bool last_button_reading = false;
static uint32_t last_button_change_ms = 0;

uint16_t crc16_ccitt(const uint8_t* data, size_t len) {
  uint16_t crc = 0xFFFF;
  for (size_t i = 0; i < len; i++) {
    crc ^= (uint16_t)data[i] << 8;
    for (int b = 0; b < 8; b++) {
      if (crc & 0x8000) {
        crc = (crc << 1) ^ 0x1021;
      } else {
        crc = (crc << 1);
      }
    }
  }
  return crc;
}

void mpu6050Write(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(reg);
  Wire.write(val);
  Wire.endTransmission();
}

void mpu6050Read(uint8_t reg, uint8_t* buf, size_t len) {
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(reg);
  Wire.endTransmission(false);
  Wire.requestFrom(MPU6050_ADDR, (uint8_t)len);
  for (size_t i = 0; i < len && Wire.available(); i++) {
    buf[i] = Wire.read();
  }
}

int16_t readI16(uint8_t* b, int idx) {
  return (int16_t)((b[idx] << 8) | b[idx + 1]);
}

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
  if (WiFi.status() == WL_CONNECTED) {
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > 10000) {
      break;
    }
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

void writePcmBigEndian(const int16_t* pcm, size_t samples) {
  uint8_t be[ I2S_READ_SAMPLES * 2 ];
  for (size_t i = 0; i < samples; i++) {
    uint16_t v = (uint16_t)pcm[i];
    be[i * 2] = (uint8_t)(v >> 8);
    be[i * 2 + 1] = (uint8_t)(v & 0xFF);
  }
  udp.write(be, samples * 2);
}

void sendImuFrame(uint32_t now) {
  if (now - last_imu_send_ms < 20) {
    return; // 50 Hz
  }
  last_imu_send_ms = now;

  uint8_t raw[14] = {0};
  mpu6050Read(0x3B, raw, sizeof(raw));

  int16_t accel_x = readI16(raw, 0);
  int16_t accel_y = readI16(raw, 2);
  int16_t accel_z = readI16(raw, 4);
  int16_t gyro_x = readI16(raw, 8);
  int16_t gyro_y = readI16(raw, 10);
  int16_t gyro_z = readI16(raw, 12);

  bool button_reading = (digitalRead(BUTTON_PIN) == LOW);
  if (button_reading != last_button_reading) {
    last_button_reading = button_reading;
    last_button_change_ms = now;
  }

  bool button_edge = false;
  if ((now - last_button_change_ms) >= BUTTON_DEBOUNCE_MS && button_reading != stable_button_state) {
    bool previous_state = stable_button_state;
    stable_button_state = button_reading;
    button_edge = (stable_button_state && !previous_state);
  }

  bool button_pressed = stable_button_state;

  imu_button_ImuButtonFrame frame = imu_button_ImuButtonFrame_init_zero;
  frame.ts_ms = now;
  frame.button_pressed = button_pressed;
  frame.button_edge = button_edge;
  frame.accel_x = accel_x;
  frame.accel_y = accel_y;
  frame.accel_z = accel_z;
  frame.gyro_x = gyro_x;
  frame.gyro_y = gyro_y;
  frame.gyro_z = gyro_z;

  uint8_t payload[64];
  pb_ostream_t stream = pb_ostream_from_buffer(payload, sizeof(payload));
  if (!pb_encode(&stream, imu_button_ImuButtonFrame_fields, &frame)) {
    return;
  }

  uint16_t len = (uint16_t)stream.bytes_written;
  uint8_t header[4] = {FRAME_MAGIC_0, FRAME_MAGIC_1, (uint8_t)(len & 0xFF), (uint8_t)(len >> 8)};
  uint16_t crc = crc16_ccitt(payload, len);

  UART_PORT.write(header, sizeof(header));
  UART_PORT.write(payload, len);
  UART_PORT.write((uint8_t*)&crc, 2);
}

void setup() {
  Serial.begin(115200);
  UART_PORT.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  stable_button_state = (digitalRead(BUTTON_PIN) == LOW);
  last_button_reading = stable_button_state;
  last_button_change_ms = millis();

  Wire.begin();
  mpu6050Write(0x6B, 0x00); // wake up
  mpu6050Write(0x1C, 0x00); // accel +/-2g
  mpu6050Write(0x1B, 0x00); // gyro +/-250 dps

  setupI2S();
  waitForWiFi();
  udp.begin(RPI_AUDIO_PORT);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    waitForWiFi();
    delay(100);
    return;
  }

  uint32_t now = millis();
  sendImuFrame(now);

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

  udp.beginPacket(RPI_IP, RPI_AUDIO_PORT);
  udp.write(rtp, 12);
  writePcmBigEndian(pcm16, FRAME_SAMPLES * CHANNELS);
  udp.endPacket();

  rtp_ts += FRAME_SAMPLES;
  packet_count++;
  if ((packet_count % 100) == 0) {
    Serial.printf("Sent RTP packets: %lu\n", (unsigned long)packet_count);
  }
}
