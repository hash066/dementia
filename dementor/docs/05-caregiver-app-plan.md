# Implementation Plan: Person 3 (Caregiver App)

This plan outlines the full development cycle for the native Android Caregiver App using Kotlin, Jetpack Compose, and Ktor.

## Phase 1: Foundation & Infrastructure (Completed)
- [x] Project Structure (Clean Architecture)
- [x] Material 3 Theme (Premium/Calming)
- [x] Navigation Graph (Bottom Nav)
- [x] Timeline Screen (Vertical Event Feed)
- [x] Chat Screen (Gemma RAG Interface)
- [x] Mock Data Injection Service

## Phase 2: Core Feature Completion (IN PROGRESS)
- [ ] **Auth & Device Pairing**
    - [ ] PIN entry screen + mDNS discovery.
    - [ ] Secure Storage (EncryptedSharedPreferences) for Pairing Token.
- [ ] **Medical Dashboard**
    - [ ] Medication List (read from `/query/medical`).
    - [ ] Vico-based Health Charts (Heart rate, activity).
    - [ ] Appointment Log.
- [ ] **Real-time Connectivity**
    - [ ] Ktor SSE client for Chat and Emergency updates.
    - [ ] NsdManager implementation for auto-discovering the Phone IP.

## Phase 3: Background & Reliability
- [ ] **WorkManager Emergency Polling**
    - [ ] Background job to check emergency status every 15 mins.
    - [ ] Local Notification triggers for critical events.
- [ ] **Room Database Integration**
    - [ ] Local caching of events for offline/spotty LAN access.

## Phase 4: Polish & Performance
- [ ] Shared Element Transitions (Hero animations for event cards).
- [ ] Bionic Haptics integration for critical alerts.
- [ ] Pull-to-refresh custom animation.

## Self-Testing Strategy
1. **Unit Tests**:
    - `EventMapperTest`: Verify JSON -> Domain models.
    - `TimelineViewModelTest`: Verify StateFlow updates from Mock Service.
2. **Integration Tests**:
    - `KtorMockEngine` tests: Verify query API handling.
3. **UI Tests**:
    - Compose UI tests for "Acknowledge" button flow.
