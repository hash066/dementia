#include <Wire.h>
#include <pb_encode.h>
#include "imu_button.pb.h"

#define MPU6050_ADDR 0x68

#define UART_PORT Serial2
#define UART_BAUD 115200
#define UART_RX 19
#define UART_TX 18

#define BUTTON_PIN 4
#define FRAME_MAGIC_0 0xA5
#define FRAME_MAGIC_1 0x5A

static uint32_t last_send_ms = 0;
static bool last_button_state = false;

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

void setup() {
  Serial.begin(115200);
  UART_PORT.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);

  pinMode(BUTTON_PIN, INPUT_PULLUP);

  Wire.begin();
  mpu6050Write(0x6B, 0x00); // wake up
  mpu6050Write(0x1C, 0x00); // accel +/-2g
  mpu6050Write(0x1B, 0x00); // gyro +/-250 dps
}

void loop() {
  uint32_t now = millis();
  if (now - last_send_ms < 20) {
    return; // 50 Hz
  }
  last_send_ms = now;

  uint8_t raw[14] = {0};
  mpu6050Read(0x3B, raw, sizeof(raw));

  int16_t accel_x = readI16(raw, 0);
  int16_t accel_y = readI16(raw, 2);
  int16_t accel_z = readI16(raw, 4);
  int16_t gyro_x = readI16(raw, 8);
  int16_t gyro_y = readI16(raw, 10);
  int16_t gyro_z = readI16(raw, 12);

  bool button_pressed = (digitalRead(BUTTON_PIN) == LOW);
  bool button_edge = (button_pressed && !last_button_state);
  last_button_state = button_pressed;

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
