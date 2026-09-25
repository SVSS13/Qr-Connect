# QR Connect 📱🔗

> **Generic | Secure | Ephemeral Sessions | Real-Time | Dynamic Actions | Location Aware | Scalable**

QR Connect is an end-to-end, configuration-driven QR-based contact platform. Permanent physical QR codes (printed on vehicles, homes, businesses, or assets) route visitors to an ephemeral, 5-minute interactive scanner session. Visitors can trigger alerts, send messages, record audio, or share geolocation without exposing the owner's phone number or personal identity. The owner manages cards and dynamic actions through an authenticated API and native Android app, receiving instant push notifications.

---

## 🏗️ Repository Architecture & Layout

```
qr-connect/
├── ARCHITECTURE.md                  # Canonical machine-readable architecture specification
├── README.md                        # Quickstart, Docker, Render, and Android build instructions
├── render.yaml                      # Render Blueprint (Infrastructure-as-Code for one-click deployment)
├── docker-compose.yml               # Local containerized stack with PostgreSQL 16
├── docs/                            # High-resolution architectural blueprints
│   ├── qr-connect-architecture-overview.jpg
│   └── qr-connect-architecture-complete.jpg
├── backend/                         # FastAPI Backend Service
│   ├── app/
│   │   ├── main.py                  # App entry point, CORS, Rate Limiter, Static Mounts
│   │   ├── core/                    # Config, Async DB Engine, Security/JWT, Slowapi Limiter
│   │   ├── models/                  # 7 SQLAlchemy 2.0 Async Models
│   │   ├── schemas/                 # Pydantic v2 data contracts
│   │   ├── api/                     # REST API Routers:
│   │   │   ├── auth.py              # Register, login, refresh JWT
│   │   │   ├── cards.py             # QR Card CRUD
│   │   │   ├── actions.py           # Dynamic Actions configuration
│   │   │   ├── settings_router.py   # User preferences
│   │   │   ├── events.py            # Activity log & FCM device token registration
│   │   │   ├── messages.py          # Owner messages inbox
│   │   │   └── public.py            # Public scanner /c/{token}, alerts, messages, voice, location
│   │   ├── services/
│   │   │   ├── session_service.py   # 5-minute ephemeral tokens & expiry
│   │   │   ├── notification_service.py # Firebase Admin SDK push alerts
│   │   │   ├── voice_service.py     # Local / S3 audio storage abstraction
│   │   │   └── location_service.py  # Reverse geocoding (Google Maps + OSM Nominatim fallback)
│   │   ├── templates/               # Jinja2 templates (dynamic TailwindCSS scanner UI)
│   │   └── static/                  # Vanilla JS + CSS (MediaRecorder audio, GPS geolocation)
│   ├── alembic/                     # Database migrations
│   ├── tests/                       # Pytest test suite (50 tests passing)
│   ├── seed.py                      # Local development database seeder
│   ├── Dockerfile                   # Multi-stage production container build
│   └── requirements.txt             # Python dependencies
└── android/                         # Android Owner App (Kotlin Native, Android 16 / API 36)
    ├── app/                         # MVVM, Retrofit, Coroutines, ViewBinding, FCM Service
    └── gradle/                      # Gradle 8.12 wrapper & version catalog
```

---

## 🚀 Deployment Options

### Option A: Local Development (SQLite)
```bash
cd backend
source /home/svss/.qrconnect-venv/bin/activate

# Seed demo owner and card
python seed.py

# Launch server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
- **Scanner UI:** [http://localhost:8000/c/demo-car-123](http://localhost:8000/c/demo-car-123)
- **Interactive Swagger Docs:** [http://localhost:8000/docs](http://localhost:8000/docs)
- **Health Check:** [http://localhost:8000/health](http://localhost:8000/health)

---

### Option B: Local Docker Stack (PostgreSQL 16)
```bash
# From repository root
docker compose up -d --build
```
This boots up:
1. `qrconnect-postgres`: Managed PostgreSQL 16 database with persistent volume.
2. `qrconnect-backend`: Production FastAPI container with automated Alembic migrations.

---

### Option C: Cloud Deployment on Render (1-Click Blueprint)

1. Push this repository to GitHub or GitLab.
2. Log into [Render Dashboard](https://dashboard.render.com).
3. Click **New +** → **Blueprint**.
4. Select your `qr-connect` repository.
5. Render automatically creates:
   - **`qrconnect-db`**: Managed PostgreSQL database.
   - **`qrconnect-api`**: Docker web service with automatic migrations and SSL.

---

## 📱 Android Owner App (Android 16 / API 36)

- **Target SDK**: Android 16 (API 36), Minimum SDK: Android 7.0 (API 24).
- **SDK Path**: `/mnt/geforce/AndroidDev/android-sdk`
- **Build Directory**: `/mnt/geforce/AndroidDev/projects/qrconnect-android/`

### Building the APK:
```bash
cd /mnt/geforce/AndroidDev/projects/qrconnect-android
./gradlew assembleDebug
```
The APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing to Device/Emulator:
```bash
/mnt/geforce/AndroidDev/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Automated Test Suite

```bash
cd backend
source /home/svss/.qrconnect-venv/bin/activate
pytest tests/ -v
```
**Status:** `50 passed` across all 7 stages:
- Authentication & JWT token security (8 tests)
- QR Card CRUD and default actions (10 tests)
- Dynamic action configuration and sorting (8 tests)
- Ephemeral 5-minute session lifecycle & scanner page (12 tests)
- Events, messages inbox, and FCM token registration (3 tests)
- Multipart voice upload & storage (2 tests)
- Geolocation sharing & reverse geocoding (3 tests)
- Slowapi rate limiting security enforcement (1 test)

---

## 📋 Complete Stage Checklist

| Stage | Feature Area | Status |
|---|---|---|
| **Stage 1** | FastAPI Foundation, PostgreSQL/SQLite SQLAlchemy models, Alembic, JWT Auth, QR Cards & Actions CRUD | ✅ **Complete** |
| **Stage 2** | Dynamic Scanner `/c/{token}`, 5-minute ephemeral sessions, TailwindCSS frontend, public endpoints | ✅ **Complete** |
| **Stage 3** | Android Owner App (Kotlin Native, MVVM, Retrofit, Material 3, Android 16) | ✅ **Complete** |
| **Stage 4** | Messages, Events, Firebase Cloud Messaging (FCM) push alerts, deep-linking | ✅ **Complete** |
| **Stage 5** | Voice recording (`MediaRecorder`), multipart audio upload, audio streaming player | ✅ **Complete** |
| **Stage 6** | Geolocation capture (`navigator.geolocation`), reverse geocoding, native Android map launcher | ✅ **Complete** |
| **Stage 7** | Security hardening (Slowapi rate limiting), production Dockerfile, `render.yaml` Blueprint, Docker Compose | ✅ **Complete** |
