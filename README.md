# Notification Manager (Senior Android Portfolio) 🔔
[English](#english) | [Español](#español)

## English
A high-performance, industrial-grade Android application designed for reliable notification tracking and MQTT synchronization. This project showcases a robust transition from legacy architecture to a professional, dependency-injected system using **Dagger-Hilt** and the modern **Credential Manager API**.

### 🚀 Key Features
- **Modern Authentication**: Fully migrated to Android's **Credential Manager API**, supporting Google ID and Passkeys with a seamless user experience.
- **Resilient Background Processing**: Implements a dedicated **Service Guard (Watchdog)** strategy using `HiltWorker` to ensure maximum uptime for the notification listener.
- **Professional DI Architecture**: 100% Dependency Injection coverage with Dagger-Hilt across Services, Workers, and Receivers.
- **IoT Integration**: Secure MQTT synchronization with a sanitized payload format optimized for ESP32/IoT consumption.
- **Robust Testing Suite**: Comprehensive unit testing layer with **82+ tests** validated via Robolectric and MockK, achieving full decoupling from Android infrastructure.

### 🛠 Tech Stack
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Clean Architecture Patterns
- **DI**: Dagger-Hilt
- **Networking/Sync**: MQTT Client & Firebase Auth
- **Persistence**: SharedPreferences with Hilt Qualifiers
- **Testing**: Robolectric, JUnit 4, MockK, Coroutines Test

### 📂 Project Structure
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

### 🧪 Testing Coverage
The project uses a **Hybrid Testing strategy**:
- **Unit Tests**: Logic validation using MockK.
- **Robolectric integration**: Real SharedPreferences and System Service simulation to verify complex component interaction.
- **Manual Injection Fallback**: Used in `BroadcastReceivers` to isolate logic from Hilt infrastructure during unit tests without sacrificing production DI.

---

## Español
Una aplicación Android de alto rendimiento y grado industrial, diseñada para el seguimiento confiable de notificaciones y sincronización MQTT. Este proyecto muestra una transición robusta de una arquitectura legacy a un sistema profesional con inyección de dependencias usando **Dagger-Hilt** y la moderna **Credential Manager API**.

### 🚀 Características Principales
- **Autenticación Moderna**: Migración completa a la **Credential Manager API** de Android, soportando Google ID y Passkeys con una experiencia de usuario fluida.
- **Procesamiento en Segundo Plano Resiliente**: Implementa una estrategia de **Guardia de Servicio (Watchdog)** dedicada usando `HiltWorker` para asegurar el máximo tiempo de actividad del suscriptor de notificaciones.
- **Arquitectura DI Profesional**: Cobertura del 100% de Inyección de Dependencias con Dagger-Hilt en Servicios, Workers y Receivers.
- **Integración IoT**: Sincronización MQTT segura con un formato de carga útil (payload) sanitizado y optimizado para el consumo de dispositivos ESP32/IoT.
- **Suite de Pruebas Robusta**: Capa integral de pruebas unitarias con más de **82 pruebas** validadas mediante Robolectric y MockK, logrando un desacoplamiento total de la infraestructura de Android.

### 🛠 Stack Tecnológico
- **UI**: Jetpack Compose (Material 3)
- **Arquitectura**: Patrones MVVM + Clean Architecture
- **DI**: Dagger-Hilt
- **Networking/Sync**: Cliente MQTT y Firebase Auth
- **Persistencia**: SharedPreferences con Cualificadores de Hilt
- **Pruebas**: Robolectric, JUnit 4, MockK, Coroutines Test

### 📂 Estructura del Proyecto
```text
com.dynamictecnologies.notificationmanager/
├── data/               # Capa de datos (Fuentes Remotas/Locales)
│   ├── datasource/     # Implementaciones de Retrofit/MQTT
│   ├── repository/     # Implementación del repositorio de datos
│   └── db/             # Lógica de persistencia
├── domain/             # Lógica de Negocio (UseCases y Entidades)
├── di/                 # Módulos de Hilt (Scopes de App/Service)
├── presentation/       # Capa de UI (Pantallas Compose y ViewModels)
├── service/            # Hub central de notificaciones
│   ├── util/           # Confiabilidad y detección de finalización de procesos
│   └── strategy/       # Gestión de primer plano (Foreground)
├── receiver/           # BroadcastReceivers a nivel de sistema
├── worker/             # Verificaciones de salud en segundo plano (WorkManager)
└── util/               # Aspectos transversales (Seguridad/Logger)
```

### 🧪 Cobertura de Pruebas
El proyecto utiliza una **estrategia de pruebas híbrida**:
- **Pruebas Unitarias**: Validación de lógica usando MockK.
- **Integración con Robolectric**: Simulación real de SharedPreferences y servicios del sistema para verificar interacciones complejas de componentes.
- **Fallback de Inyección Manual**: Utilizado en `BroadcastReceivers` para aislar la lógica de la infraestructura de Hilt durante las pruebas unitarias sin sacrificar la DI en producción.

---
*Desarrollado con un enfoque en la calidad del código, el rendimiento y prácticas de nivel Senior en Android.*
