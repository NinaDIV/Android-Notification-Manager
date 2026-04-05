# NotificationManager (Senior Android Portfolio)

![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple?logo=kotlin)
![Hilt](https://img.shields.io/badge/Dagger--Hilt-Inject-green?logo=dagger)
![Jetpack Compose](https://img.shields.io/badge/Jetpack--Compose-UI-blue?logo=jetpackcompose)
![Robolectric](https://img.shields.io/badge/Robolectric-Test-brightgreen)
![MQTT](https://img.shields.io/badge/MQTT-IoT-orange)

A high-performance, industrial-grade Android application designed for reliable notification tracking and MQTT synchronization. This project showcases a robust transition from legacy architecture to a professional, dependency-injected system using **Dagger-Hilt** and the modern **Credential Manager API**.

## 🚀 Key Features

- **Modern Authentication**: Fully migrated to Android's **Credential Manager API**, supporting Google ID and Passkeys with a seamless user experience.
- **Resilient Background Processing**: Implements a dedicated **Service Guard (Watchdog)** strategy using `HiltWorker` to ensure maximum uptime for the notification listener.
- **Professional DI Architecture**: 100% Dependency Injection coverage with Dagger-Hilt across Services, Workers, and Receivers.
- **IoT Integration**: Secure MQTT synchronization with a sanitized payload format optimized for ESP32/IoT consumption.
- **Robust Testing Suite**: Comprehensive unit testing layer with **82+ tests** validated via Robolectric and MockK, achieving full decoupling from Android infrastructure.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Clean Architecture Patterns
- **DI**: Dagger-Hilt
- **Networking/Sync**: MQTT Client & Firebase Auth
- **Persistence**: SharedPreferences with Hilt Qualifiers
- **Testing**: Robolectric, JUnit 4, MockK, Coroutines Test

## 📂 Project Structure

```text
com.dynamictecnologies.notificationmanager/
├── data/               # Data layer (Remote/Local sources)
│   ├── datasource/     # Retrofit/MQTT Implementations
│   ├── repository/     # Data repository implementation
│   └── db/             # Persistence logic
├── domain/             # Business Logic (UseCases & Entities)
├── di/                 # Hilt Modules (App/Service scopes)
├── presentation/       # UI Layer (Compose Screens & ViewModels)
├── service/            # Core Notification Hub
│   ├── util/           # Reliability & Death Detection
│   └── strategy/       # Foreground management
├── receiver/           # System level BroadcastReceivers
├── worker/             # Background HealthChecks (WorkManager)
└── util/               # Cross-cutting concerns (Security/Logger)
```

## 🧪 Testing Coverage

The project uses a **Hybrid Testing strategy**:
- **Unit Tests**: Logic validation using MockK.
- **Robolectric integration**: Real SharedPreferences and System Service simulation to verify complex component interaction.
- **Manual Injection Fallback**: Used in `BroadcastReceivers` to isolate logic from Hilt infrastructure during unit tests without sacrificing production DI.

---
*Developed with a focus on Code Quality, Performance, and Senior-level Android practices.*
