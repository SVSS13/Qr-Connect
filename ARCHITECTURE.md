# QR Connect — Architecture Specification

> **Secure | Real-Time | Location Aware | Session Based | Scalable | Generic**

## 1. System Overview

QR Connect is a **generic QR-based contact platform** where a permanent physical QR code (printed on a vehicle, card, home, etc.) links to a dynamic, configuration-driven interaction page. When scanned, the visitor gets a **5-minute ephemeral session** to send alerts, messages, voice recordings, or share their location with the QR card owner. The owner receives **real-time push notifications** via an Android app.

### Core Invariants

1. **One generic QR platform** — not vehicle-specific, not business-card-specific.
2. **Permanent physical QR identity** — the printed QR code never changes; the `card_token` is immutable.
3. **Five-minute ephemeral scanner sessions** — each scan creates a new session that expires after 5 minutes.
4. **Dynamic actions stored as database rows** — the actions available on a QR card are rows in `qr_actions`, not hardcoded.
5. **Scanner UI rendered from database configuration** — the public scanner page reads action config from the DB and renders accordingly.
6. **Android app manages QR cards and actions through authenticated APIs** — never direct DB access.
7. **FastAPI serves the public scanner frontend** — templates served from the backend, no separate frontend deployment.
8. **PostgreSQL is the persistent database**.
9. **No separate frontend deployment required**.
10. **Configuration changes do not require redeployment** — only new backend *capabilities* may require deployment.
11. **Android app NEVER connects directly to PostgreSQL**.

---

## 2. Architecture Layers

### 2.1 Client Layer

| Client | Type | Protocol | Description |
|--------|------|----------|-------------|
| **Visitor (Scanner)** | Mobile Browser (iOS/Android) | HTTPS | Scans QR → opens web page → interacts (alert, message, voice, location) |
| **Owner (Authenticated)** | Android App (Kotlin Native) | HTTPS | Receives notifications, views messages/voice/location, manages QR cards & actions, manages settings |

### 2.2 Backend Layer — FastAPI (Deployed on Render)

- API Endpoints (public + authenticated)
- Session Management (5-minute tokens)
- Location Handling (geocoding)
- Notification Service (FCM)
- Security & Rate Limiting
- File Storage Service (voice recordings)

### 2.3 Database Layer — PostgreSQL (Managed by Render)

Stores: QR Cards, Sessions, Events, User Data, Settings, QR Actions, Messages/Voice

### 2.4 External Services

| Service | Purpose |
|---------|---------|
| **Firebase Cloud Messaging (FCM)** | Push notifications to Android app (new alert/message/voice, real-time delivery, topic/user based) |
| **Maps / Geocoding Service (Google Maps API)** | Convert lat/lng to address, show on map in Android app, optional reverse geocoding |
| **IP Geolocation Service (e.g. ipinfo.io or ipapi.co)** | Get approximate location from IP |
| **Object Storage (Render / S3)** | Store voice recordings, generate secure URLs, file metadata in PostgreSQL |

---

## 3. Database Schema

### 3.1 `owners` (User/Owner accounts)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `name` | VARCHAR | NOT NULL |
| `email` | VARCHAR | UNIQUE, NOT NULL |
| `password_hash` | VARCHAR | NOT NULL |
| `fcm_token` | VARCHAR | Nullable |
| `created_at` | TIMESTAMP | DEFAULT NOW() |

### 3.2 `qr_cards`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `owner_id` | UUID (FK → owners.id) | NOT NULL |
| `card_token` | VARCHAR | UNIQUE, NOT NULL, immutable |
| `name` | VARCHAR | NOT NULL |
| `type` | VARCHAR | e.g. 'car', 'home', 'other' |
| `status` | VARCHAR | 'active', 'inactive' |
| `created_at` | TIMESTAMP | DEFAULT NOW() |
| `rotated_at` | TIMESTAMP | Nullable (from diagram 2) |

### 3.3 `qr_actions` (Dynamic action configuration)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `qr_card_id` | UUID (FK → qr_cards.id) | NOT NULL |
| `label` | VARCHAR | Display label |
| `action_type` | VARCHAR | 'alert', 'message', 'voice', 'location' |
| `icon` | VARCHAR | Icon identifier |
| `sort_order` | INTEGER | Display ordering |
| `config` | JSON | Action-specific configuration |
| `enabled` | BOOLEAN | DEFAULT TRUE |
| `created_at` | TIMESTAMP | DEFAULT NOW() |

### 3.4 `sessions`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `qr_card_id` | UUID (FK → qr_cards.id) | NOT NULL |
| `session_token` | VARCHAR | UNIQUE, NOT NULL |
| `ip_address` | VARCHAR | Captured from request |
| `user_agent` | VARCHAR | Captured from request |
| `latitude` | DECIMAL | Nullable |
| `longitude` | DECIMAL | Nullable |
| `location_status` | VARCHAR | 'pending', 'granted', 'denied' |
| `created_at` | TIMESTAMP | DEFAULT NOW() |
| `expires_at` | TIMESTAMP | created_at + 5 minutes |

### 3.5 `events`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `session_id` | UUID (FK → sessions.id) | NOT NULL |
| `action_id` | UUID (FK → qr_actions.id) | Nullable |
| `type` | VARCHAR | 'alert', 'message', 'voice', 'location' |
| `content` | TEXT | Message text, alert text, etc. |
| `latitude` | DECIMAL | Nullable |
| `longitude` | DECIMAL | Nullable |
| `address` | VARCHAR | Reverse-geocoded address |
| `created_at` | TIMESTAMP | DEFAULT NOW() |

### 3.6 `messages_voice` (Voice message metadata)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `event_id` | UUID (FK → events.id) | NOT NULL |
| `content` | VARCHAR | Storage path/URL |
| `language` | VARCHAR | Nullable |
| `mime_type` | VARCHAR | e.g. 'audio/webm' |
| `duration` | INTEGER | Duration in seconds |
| `created_at` | TIMESTAMP | DEFAULT NOW() |

### 3.7 `settings` (Per-user preferences)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID (PK) | Primary Key |
| `user_id` | UUID (FK → owners.id) | UNIQUE, NOT NULL |
| `preferences` | JSON | User preferences |
| `location_enabled` | BOOLEAN | DEFAULT TRUE |
| `notifications_enabled` | BOOLEAN | DEFAULT TRUE |
| `created_at` | TIMESTAMP | DEFAULT NOW() |

### Entity Relationships

```
owners 1──∞ qr_cards
qr_cards 1──∞ qr_actions
qr_cards 1──∞ sessions
sessions 1──∞ events
events 1──1 messages_voice (optional)
owners 1──1 settings
```

---

## 4. API Contract

### 4.1 Public API (Scanner — No Auth Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/c/{token}` | Scanner page — validates card token, creates 5-min session, serves scanner HTML |
| `GET` | `/api/public/card/{token}` | Card details (name, type, active actions) |
| `POST` | `/api/public/action` | Trigger an action (alert) |
| `POST` | `/api/public/message` | Send a message |
| `POST` | `/api/public/voice` | Upload voice recording |
| `POST` | `/api/public/location` | Share location |

### 4.2 Owner API (Authenticated — JWT)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Login → returns JWT |
| `POST` | `/api/auth/register` | Register new owner |
| `POST` | `/api/auth/refresh` | Refresh JWT token |
| `GET` | `/api/owner/cards` | List owner's QR cards |
| `POST` | `/api/owner/cards` | Create new QR card |
| `PATCH` | `/api/owner/cards/{id}` | Update QR card |
| `DELETE` | `/api/owner/cards/{id}` | Delete QR card |
| `GET` | `/api/owner/cards/{id}/actions` | List actions for a card |
| `POST` | `/api/owner/cards/{id}/actions` | Add action to card |
| `PATCH` | `/api/owner/actions/{id}` | Update action |
| `DELETE` | `/api/owner/actions/{id}` | Delete action |
| `GET` | `/api/owner/events` | List events (all cards) |
| `GET` | `/api/owner/messages` | List messages |
| `GET` | `/api/owner/settings` | Get settings |
| `PATCH` | `/api/owner/settings` | Update settings |

### 4.3 Authentication

- **JWT-based** authentication for all owner endpoints
- Token refresh mechanism
- FCM token registration on login

---

## 5. Session Flow (5-Minute Token)

```
1. Scan QR        → Visitor scans the permanent QR code
2. Create Session → FastAPI validates card token → creates 5-min session token
3. User Interacts → Visitor sends alert, message, voice, or shares location
4. Events Stored  → All actions stored in PostgreSQL with IP, message, location, timestamp
5. Notify Owner   → Owner receives real-time notification in Android app (FCM)
6. Session Expires → After 5 minutes the session token expires; scan again for new session
```

**Token Lifecycle:**
- 10:00 — Scan QR → Create session
- 10:00–10:05 — **5 MINUTES (ACTIVE SESSION)** — Send Alert, Send Message, Send Voice, Share Location
- 10:05 — Session expires

---

## 6. Data Flow

1. Visitor scans QR → opens web page
2. FastAPI validates card token → creates 5-min session
3. Browser optionally requests location → gets lat/lng
4. Visitor sends alert/message/voice/location
5. Data stored in PostgreSQL (session + events)
6. Owner receives notification via FCM
7. Owner views details in Android app (map, message, etc.)
8. Session expires after 5 minutes → new scan required

---

## 7. Frontend Implementation (Scanner UI)

### Tech Stack
- **HTML5**
- **CSS3** (TailwindCSS)
- **JavaScript** (Vanilla/Alpine.js/HTMX)

### Pages / Components
1. **Scanner Home** — Shows card info + available actions (rendered from DB)
2. **Send Message** — Text input + send button
3. **Record Voice** — Audio recorder (00:00 timer) + send button
4. **Share Location** — Location permission request + share button
5. **Success Screen** — "Message Sent! Thank you! The owner has been notified."

### Serving
- Templates served by FastAPI (Jinja2)
- Static files: `scanner.css`, `scanner.js`
- No separate frontend deployment

---

## 8. Backend Implementation (FastAPI)

### Project Structure
```
qr-connect-backend/
├── app/
│   ├── core/
│   │   ├── config.py
│   │   ├── security.py
│   │   └── database.py
│   ├── api/
│   │   ├── auth.py
│   │   ├── public.py
│   │   ├── cards.py
│   │   ├── actions.py
│   │   ├── events.py
│   │   ├── messages.py
│   │   └── settings.py
│   ├── models/
│   │   └── schemas/
│   ├── services/
│   │   ├── session_service.py
│   │   ├── notification_service.py
│   │   ├── location_service.py
│   │   └── voice_service.py
│   ├── db/
│   │   └── migrations/
│   ├── templates/
│   │   └── scanner.html
│   ├── static/
│   │   ├── scanner.css
│   │   └── scanner.js
│   └── main.py
├── alembic.ini
├── requirements.txt
└── Dockerfile
```

### Core Services (Python)

| Service | Responsibilities |
|---------|-----------------|
| **SessionService** | Create 5-min session tokens, validate sessions, handle expiry |
| **NotificationService** | Send push notifications via FCM, topic/user based |
| **LocationService** | Reverse geocode lat/lng, detect IP location (fallback) |
| **VoiceService** | Store audio files (Render/S3), generate secure URLs, file metadata in PostgreSQL |
| **Security** | Rate limiting, token validation, input sanitization |

---

## 9. Android Owner App Implementation

### Tech Stack
- **Kotlin** (Native)
- **Android SDK** (Min 24+)
- **Retrofit** (HTTP Client)
- **FCM** (Notifications)
- **Maps SDK** (View Location)

### App Screens
1. **Login** — Email/password authentication
2. **Dashboard** — "My QR Cards" list (My Car, My Home, My Bike, etc.)
3. **Card Details** — Card info, actions list, edit card, view events, share QR
4. **Actions** — Manage dynamic actions for a card
5. **Messages Inbox** — List of messages with voice playback
6. **Map View** — Location shared by scanner, "Open in Maps" button

---

## 10. Deployment on Render

### 1. Web Service (FastAPI)
- Build from GitHub
- Docker / Python
- Environment Variables
- Auto Deploy (GitHub push)
- SSL (Let's Encrypt)
- Custom Domain: `yourdomain.com`

### 2. PostgreSQL Database
- Managed by Render
- Automatic Backups
- Connection via internal URL
- No public access required

---

## 11. Key Features Summary

| Feature | Description |
|---------|-------------|
| Generic QR | One platform, any use case |
| Secure 5-Min Session Tokens | Ephemeral sessions, no persistent scanner state |
| Token Rotation (Every Scan) | Each scan = new session token |
| IP Address Capture (Automatic) | Every scan captures IP |
| Geolocation (Optional via Browser) | Browser Geolocation API |
| Map Integration (Show on Map) | Google Maps in Android app |
| Address Detection (Reverse Geocoding) | lat/lng → human-readable address |
| Real-Time Notifications (FCM) | Instant push to owner |
| Voice Messages (Store & Play) | Record in browser, play in app |
| Audit Trail (All Events) | Complete event history per card |
| User Settings (Per QR Card) | Owner preferences in DB |
| Rate Limiting & Abuse Protection | Backend-enforced |
| Dynamic Actions | Configuration-driven, no redeployment |
| Scalable | Horizontal scaling on Render |
