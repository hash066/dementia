# 06 - Event Contract + Storage Ownership

## EventEnvelope Lifecycle

```mermaid
sequenceDiagram
  participant ESP as ESP32
  participant RPI as RPi Runtime
  participant PH as Phone Core
  participant DB as Phone Memory DB
  participant CG as Caregiver App

  ESP->>RPI: Audio packets + control events
  RPI->>PH: EventEnvelope(event_id, ts, type, payload, priority)
  PH->>PH: Validate, order, dedupe, classify
  PH->>DB: Persist (single-writer upsert)
  DB-->>CG: Query/search/dashboard results
  CG->>PH: Ack/Clear/Settings updates
  PH->>RPI: Commands (speak, notify, FSM update)
```

## Storage Ownership Model

```mermaid
flowchart TD
  A["ESP32"] -->|No persistent memory| B["RPi"]
  B -->|No persistent memory (RAM only)| C["Phone"]
  C -->|ALL persistent memory writes| D["Encrypted SQLite + FTS5"]
```

