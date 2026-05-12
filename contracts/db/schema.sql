-- Single writer: phone/memory/upsert.py only
-- Read-only consumers: phone/query/api.py, caregiver-app via HTTP query API

CREATE TABLE IF NOT EXISTS events (
  event_id   TEXT PRIMARY KEY,
  ts         INTEGER NOT NULL,
  type       TEXT NOT NULL,
  priority   TEXT NOT NULL,
  raw_json   TEXT NOT NULL,
  transcript TEXT,
  summary    TEXT,
  entities   TEXT,
  created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX IF NOT EXISTS idx_events_ts   ON events(ts DESC);
CREATE INDEX IF NOT EXISTS idx_events_type ON events(type, ts DESC);

-- FTS5: maintained by upsert.py (no external content= to avoid rowid sync issues)
CREATE VIRTUAL TABLE IF NOT EXISTS events_fts USING fts5(
  event_id UNINDEXED,
  transcript,
  summary,
  tokenize = 'porter unicode61'
);

CREATE TABLE IF NOT EXISTS medical (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id    TEXT REFERENCES events(event_id),
  category    TEXT NOT NULL,
  label       TEXT NOT NULL,
  value       TEXT,
  ts          INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS reminders (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  label       TEXT NOT NULL,
  cron        TEXT,
  next_fire   INTEGER,
  active      INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS caregiver_settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);

INSERT OR IGNORE INTO caregiver_settings (key, value) VALUES
  ('retention_days', '90'),
  ('alert_threshold_priority', 'HIGH'),
  ('tts_voice', 'en_GB-alan-medium'),
  ('active_emergency', 'false'),
  ('fsm_state', 'IDLE');
