# 🔍 Auditoría de Diseño y Ciclos de Vida — NotificationManager

> **Fecha:** 2026-06-03
> **Alcance:** Auditoría de diseño (arquitectura, DI, SOLID, patrones) y de lógica de ciclos de vida (servicios, receivers, UI/ViewModels, conexiones).
> **Metodología:** 5 agentes de análisis en paralelo · 3 sobre ciclos de vida (servicios/receivers · UI/ViewModels · conexiones MQTT/BT/Firebase) y 2 sobre diseño (arquitectura limpia · DI/SOLID/patrones). ~220 archivos Kotlin analizados con verificación cruzada (grep + lectura de código).

---

## Índice

- [Veredicto general](#veredicto-general)
- [⭐ Hallazgos de consenso](#-hallazgos-de-consenso-detectados-por-varios-agentes--máxima-confianza)
- [A) Auditoría de ciclos de vida](#a-auditoría-de-ciclos-de-vida)
  - [Críticos](#-críticos)
  - [Altos](#-altos)
  - [Medios](#-medios)
- [B) Auditoría de diseño](#b-auditoría-de-diseño)
  - [Críticos](#-críticos-1)
  - [Altos](#-altos-1)
  - [Medios / Bajos](#-medios--bajos)
- [Diagrama de dependencias real](#diagrama-de-dependencias-real-observado)
- [🧭 Plan de remediación priorizado](#-plan-de-remediación-priorizado)
- [✅ Lo que está bien hecho](#-lo-que-está-bien-hecho)

---

## Veredicto general

El proyecto tiene un **esqueleto arquitectónico maduro** (Clean Architecture, Hilt, MVVM con `StateFlow`, use cases, repos tras interfaces) y **buena higiene de coroutines en servicios** (todos los scopes usan `SupervisorJob` + se cancelan en `onDestroy`). Pero la auditoría revela **tres problemas sistémicos**:

1. **🔴 Bugs de compatibilidad API 34 que rompen la app en runtime** (el target declarado). Dos crashes/fallos silenciosos en el subsistema de servicios, que es el núcleo de la app.
2. **🔴 "Arquitectura de adorno" / código muerto extenso**: una capa MQTT entera sin cablear, una interfaz de repositorio nunca implementada, un broadcast que nadie escucha, un receiver que no se dispara, un backoff manager sin usar. Da **falsa sensación de robustez**.
3. **🟠 La regla de dependencia de Clean Architecture está rota de forma sistemática**: el dominio depende de Firebase, Room, Compose y clases concretas de `data/`.

---

## ⭐ Hallazgos de consenso (detectados por varios agentes → máxima confianza)

| # | Hallazgo | Agentes | Severidad |
|---|----------|---------|-----------|
| A | Broadcast `MQTT_RECONNECT` se emite pero **nadie lo escucha** (no-op) | 3 | Alto |
| B | **Capa MQTT muerta**: `MqttRepository` + 4 use cases sin binding Hilt | 2 | Crítico |
| C | **`NotificationRepository` duplicado**: interfaz de dominio muerta vs clase `data` concreta inyectada en VM/Activity/Service | 2 | Crítico |
| D | Dominio depende de framework: `FirebaseUser`, `MqttConnectionManager`, `BluetoothDeviceScanner` concretos | 2 | Crítico |
| E | **Fuga de `BroadcastReceiver` Bluetooth** (scanner no-singleton + `onCleared` que no corre + `ACTION_DISCOVERY_FINISHED` no desregistra) | 2 | Crítico |
| F | `CONNECTIVITY_CHANGE` en manifest **no se dispara desde Android 7 (API 24)** — reconexión por red es código muerto | 2 | Medio |

---

# A) Auditoría de ciclos de vida

## 🔴 Críticos

### 1. `startForeground()` sin `foregroundServiceType` (crash en Android 14)
- **Ubicación:** `NotificationForegroundService.kt:132` · manifest `AndroidManifest.xml:82` (`foregroundServiceType="dataSync"`) · `build.gradle.kts:20` (`targetSdk = 34`)
- **Problema:** Con `targetSdk 34`, un FGS con tipo declarado **debe** arrancarse con la sobrecarga de 3 argumentos. Se usa la de 2 argumentos.
- **Impacto:** El sistema lanza `MissingForegroundServiceTypeException` → **el servicio de monitoreo no arranca en absoluto en API 34**.
- **Recomendación:** `ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`. Nota: `dataSync` tiene cuota ~6h/24h en Android 14; reevaluar si el FGS es necesario (ver decisión de arquitectura).

### 2. `registerReceiver` sin flag `RECEIVER_NOT_EXPORTED` (fallo silencioso en API 34)
- **Ubicación:** `NotificationForegroundService.kt:241` y `:298`
- **Problema:** Desde `targetSdk 33+` es obligatorio pasar el flag de exportación. Lanza `SecurityException`, **pero el try/catch se la traga** → los observers de energía/Doze y de red **nunca se registran silenciosamente**.
- **Impacto:** Se pierde la detección de modo ahorro y de cambios de red. Defecto enmascarado.
- **Recomendación:** `ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)`. `MainActivity.kt:320-324` ya lo hace bien — replicar.

### 3. Auto-reinicio del FGS desde background viola restricción API 31+
- **Ubicación:** `tryAutomaticRestart()` en `NotificationForegroundService.kt:697-724`
- **Problema:** `onDestroy` llama `startForegroundService()` desde background → `ForegroundServiceStartNotAllowedException` siempre en API 31+. Combinado con el bug #1, aunque arrancara volvería a crashear.
- **Recomendación:** Eliminarlo; confiar en `START_STICKY` + WorkManager.

### 4. `MqttClient` nunca se cierra + operaciones bloqueantes en el hilo del caller
- **Ubicación:** `MqttConnectionManager.kt:103-220`
- **Problema:** Se usa el cliente Paho **síncrono**; `connect/publish/subscribe` no envuelven en `withContext(Dispatchers.IO)` (y `subscribe()` ni siquiera es `suspend`). Además `disconnect()` nunca llama `close()` y ningún componente lo invoca en su teardown.
- **Impacto:** **ANR garantizado** si algún caller las invoca desde Main; **fuga de hilos/handles de persistencia Paho**.
- **Recomendación:** Envolver todo en `Dispatchers.IO` dentro del manager; añadir `close()` y llamarlo desde un punto con ciclo de vida.

### 5. Fuga de `BroadcastReceiver` Bluetooth *(consenso ×2)*
- **Ubicación:** `DevicePairingViewModel.kt:170` · `BluetoothMqttHiltModule.kt:38` · `BluetoothDeviceScanner.kt:90-93`
- **Problema:** El scanner se provee **sin `@Singleton`** y registra el receiver contra el `applicationContext`; `ACTION_DISCOVERY_FINISHED` pone `isScanning=false` pero **no desregistra**, y `onCleared()` solo corre al destruir la Activity (no al salir de Home).
- **Impacto:** Receivers BT fantasma a nivel de aplicación; consumo de batería en background.
- **Recomendación:** Desregistrar en `DISCOVERY_FINISHED`, marcar `@Singleton`, llamar `cleanup()` en `onCleared`.

### 6. `MainActivity`: fugas de Activity por `Handler.postDelayed` y ViewModels scope-Activity
- **Ubicación:** `MainActivity.kt:340-346` · ViewModels en `MainActivity.kt:70-73`
- **Problema:** `Handler.postDelayed(…, 2000)` captura `this` y no se cancela en `onDestroy` → rotar dentro de 2s filtra toda la Activity. Los 4 ViewModels se obtienen con `by viewModels()` (scope Activity) y se pasan por parámetro a todo el árbol de navegación → `onCleared` (que para el escaneo BT) solo corre al destruir la Activity.
- **Recomendación:** Usar `lifecycleScope.launch{delay()}`; migrar a `hiltViewModel()` por `composable` scopeado al `NavBackStackEntry`.

## 🟠 Altos

- **Recolección no lifecycle-aware en TODA la app:** `collectAsState()` ×19 en 5 archivos, `collectAsStateWithLifecycle` ×0 (ni la dependencia `lifecycle-runtime-compose` está declarada). Las pantallas siguen recolectando StateFlows con la app en background. → Añadir la dependencia y reemplazar las 19 ocurrencias.
- **`ExponentialBackoffManager` huérfano:** la pieza diseñada para evitar loops de reinicio no se usa en ningún flujo de recovery → todos los reintentos son inmediatos y sin tope.
- **NPE crash-loop en `NotificationListenerService.onListenerConnected`:** si la detección de fabricante falla, `deviceDetector`/`serviceStrategy` quedan `lateinit` sin inicializar y se usan fuera del `try` (`:126-127`) → `UninitializedPropertyAccessException` recurrente.
- **El watchdog se auto-desactiva tras detectar muerte:** `ServiceHealthCheckWorker` muestra notificación, pone `service_should_be_running=false` y devuelve `Result.success()` → **no recupera** y se silencia para siempre. → `Result.retry()`, no desarmar el flag.
- **Posible tormenta de reinicios:** `setComponentEnabledSetting` togglea el listener → disconnect (notif roja) → connect → arranca FGS de nuevo, sin backoff.
- **Doble navegación al Login:** `LaunchedEffect(authState)` en `AppNavigation.kt:55-72` usa el data class completo como key y colisiona con `startDestination` + el `onLogout`. → key solo `isAuthenticated`.
- **Eventos one-shot como estado:** el snackbar de pairing (`AppListScreen.kt:63-81`) no resetea en rama `Error` → se re-dispara al rotar. → `Channel`/`SharedFlow` de eventos.
- **`BootReceiver` con `delay(3s)+delay(10s)` dentro de `goAsync()`** antes de arrancar el FGS → excede la ventana ~10s del receiver (ANR/kill) justo en el caso de uso de arranque tras boot.

## 🟡 Medios

- `keepSynced(true)` nunca desactivado en `RemoteUserDataSource.kt:36,67` (tráfico/batería sostenidos).
- Escaneo BT sin timeout efectivo (la "optimización" solo se loguea, `BluetoothDeviceScanner.kt:144-145`).
- `NetworkStateReceiver.goAsync()` con ventana ~10s para un `connect()` de hasta 60s.
- Falta `enableEdgeToEdge()` (recomendado en `targetSdk 34`).
- `checkBatteryOptimization()` lanza diálogo del sistema desde `onCreate` del servicio en cada arranque (`NotificationForegroundService.kt:151`).
- `signInWithGoogle(context)` pasa Context de Activity al ViewModel → fuga temporal si se rota con el bottom-sheet abierto (`AuthViewModel.kt:174-195`).
- Estado de diálogo de permisos en campo de Activity en vez de `rememberSaveable` (`MainActivity.kt:65`).
- `RemoteUserDataSource.init` vuelve a llamar `setPersistenceEnabled(true)` (excepción esperada y silenciada).

---

# B) Auditoría de diseño

## 🔴 Críticos

### 1. Capa MQTT completamente muerta *(consenso ×2)*
- **Ubicación:** `domain/repositories/MqttRepository`, `data/repository/MqttRepositoryImpl.kt`, `domain/usecases/mqtt/{Connect,Disconnect,SearchDevices,SendNotificationViaMqtt}UseCase.kt`
- **Problema:** Ninguna tiene binding Hilt — inyectarlas **fallaría en compilación**; solo compila porque nadie las usa. `MqttRepositoryImpl.sendNotification()` devuelve siempre `failure` y `searchDevices()` retorna vacío con TODOs. Hay tests validando código que el grafo real no puede construir.
- **Recomendación:** **Borrar** la jerarquía (el flujo real usa `NotificationSender`), o cablearla y migrar.

### 2. `NotificationRepository` duplicado *(consenso ×2)*
- **Ubicación:** `domain/repositories/NotificationRepository.kt` (interfaz, **nunca implementada ni inyectada**) vs `data/repository/NotificationRepository.kt` (clase concreta Android con `Context`, `SharedPreferences`, `Log`)
- **Problema:** Hilt provee la **clase concreta**, inyectada directamente en ViewModel, Activity y Service. Riesgo real de import equivocado (mismo nombre simple).
- **Recomendación:** Renombrar la clase a `NotificationRepositoryImpl`, implementar la interfaz, inyectar la abstracción vía un use case.

### 3. El dominio depende del framework *(consenso ×2)*
- `AuthRepository` expone `Flow<FirebaseUser?>` y `awaitFirebaseUser(): FirebaseUser?` (`AuthRepository.kt:48`).
- Use cases de `device/` importan `MqttConnectionManager` y `BluetoothDeviceScanner` **concretos** de `data/`.
- Use cases usan `android.util.Log` y `BuildConfig.DEBUG` (`SendNotificationToDeviceUseCase.kt:36-66`).
- **Recomendación:** Devolver entidad `User`; extraer interfaces de dominio (`MqttConnection`, `BluetoothScanner`); inyectar el `Logger` que ya existe.

### 4. Las "entidades" que cruzan capas son modelos `data` contaminados
- **Ubicación:** `data/model/NotificationInfo.kt` (`@Entity` de Room) y `data/model/AppInfo.kt` (`ImageBitmap` de Compose), ambos en contratos de dominio.
- **Impacto:** El dominio depende transitivamente de Room y Compose.
- **Recomendación:** Crear entidades puras en `domain/entities/` con mappers en cada frontera.

### 5. Strategy OEM no inyectado, duplicado, con bug funcional Oppo/Vivo
- **Ubicación:** `when()` **duplicado** en `NotificationListenerService.kt:143-154` y `ServiceHealthCheckWorker.kt:54-66`, instanciando con `new`.
- **Problema:** `DeviceManufacturer.Oppo`/`Vivo` existen pero el `when` del listener no los mapea → caen en `GenericServiceStrategy`, mientras el worker sí les da timeouts agresivos → **inconsistencia real**: dispositivos con killing agresivo reciben config "Generic".
- **Recomendación:** Factory/`@IntoMap` Hilt, inyectar `BackgroundServiceStrategy`, añadir Oppo/Vivo.

## 🟠 Altos

- **God class `NotificationForegroundService` (731 líneas, ~8 responsabilidades):** FGS lifecycle + WakeLock + heartbeat + 3 observers + reinicio del listener + batería + notificaciones duplicadas. → Extraer `WakeLockManager`, `ServiceHeartbeat`, `PermissionObserver`, etc.
- **God class `NotificationListenerService` (376 líneas)** con service-locator implícito: mezcla field-injection Hilt con `new` manual de colaboradores → no testeable.
- **ViewModels saltan la capa de dominio:** `AppListViewModel` inyecta el repo de `data` directamente; `DevicePairingViewModel` inyecta `FirebaseAuth` y deriva el username leyendo Firebase (lógica de negocio en presentación).
- **Compose accede a servicios y modelos `data`:** `AppListScreen.kt:245-255` construye `Intent` + `startForegroundService`; varios componentes importan `data.model.*` y `data.bluetooth.*`.
- **Observer de red duplicado + reconexión competidora:** el del FGS manda el broadcast muerto `MQTT_RECONNECT`; `NetworkStateReceiver` llama `connect()` directo; `lastKnownConnected` es `companion object` mutable sin sincronización.

## 🟡 Medios / Bajos

- `@Provides` manual en todo el grafo donde `@Binds` sería idiomático (0 usos de `@Binds`).
- Use cases anémicos passthrough (varios del subsistema MQTT muerto).
- `NotificationRateLimiter` instanciado con `new` dentro del use case (`SendNotificationToDeviceUseCase.kt:27-30`) → no testeable.
- Paquetes incoherentes (`viewmodel/`, `service/`, `receiver/` fuera de las 3 capas).
- Validación de negocio repartida entre `domain/` y `data/validator/`.

---

## Diagrama de dependencias real observado

```
                         ┌─────────────────────────────────────────────┐
                         │  presentation/ (Compose)                     │
                         │  - importa data.model.AppInfo/NotificationInfo│
                         │  - importa data.bluetooth.BluetoothDeviceScanner│
                         │  - AppListScreen crea Intent + startService() │
                         └───────────────┬─────────────────────────────┘
                                         │
                         ┌───────────────▼─────────────────────────────┐
                         │  viewmodel/  (fuera de presentation/)         │
                         │  - AppListVM ── data.repository.Notification  │ ✗ salta domain
                         │  - DevicePairingVM ── FirebaseAuth (directo)  │ ✗
                         └───┬───────────────────────┬──────────────────┘
                             │ (use cases)           │ (acceso directo)
            ┌────────────────▼─────────┐             │
            │  domain/usecases         │             │
            │  - Auth/User: OK (→iface)│             │
            │  - device/*: ──────────────────────────┼──► data.datasource.mqtt.* (CONCRETO) ✗
            │  - importan BuildConfig, android.util.Log, Firebase ✗      │
            └───────┬──────────────────┘             │
                    │ (algunas ifaces)               │
       ┌────────────▼───────────┐                    │
       │ domain/repositories     │  ◄─ FirebaseUser, data.model.* (con @Entity Room) ✗
       │ - AuthRepo / UserProfileRepo / DevicePairingRepo : implementadas y usadas ✓
       │ - NotificationRepo / MqttRepo / NotificationSender : NUNCA implementadas/inyectadas ✗ (muertas)
       └────────────┬───────────┘                    │
                    ▼                                 ▼
            ┌──────────────────────────────────────────────────────┐
            │  data/ (repository impl, datasource, db, model, mapper)│
            │  - data.repository.NotificationRepository (clase Android, NO impl. de iface)│
            └──────────────────────────────────────────────────────┘

LEYENDA: ✓ correcto   ✗ violación de la regla de dependencia
```

---

## 🧭 Plan de remediación priorizado

| Prioridad | Acción | Esfuerzo | Por qué primero |
|-----------|--------|----------|-----------------|
| **P0** | Fix #1 y #2 de lifecycle (`startForeground` tipado + `registerReceiver` con flag) | ~5 líneas | **Desbloquean el funcionamiento en API 34** — hoy la app falla en su target |
| **P0** | Envolver MQTT en `Dispatchers.IO` + añadir `close()` | Bajo | Evita ANR y fuga de hilos |
| **P1** | Fuga receiver BT (#5) + scoping ViewModels a nav-graph (#6) | Medio | Fugas reales de memoria/batería |
| **P1** | Replantear recovery a un único owner (Worker) con backoff; quitar auto-restart de `onDestroy` y el watchdog que se auto-desactiva | Medio | El sistema de supervivencia hoy no recupera de verdad |
| **P1** | Migrar `collectAsState` → `collectAsStateWithLifecycle` (×19) | Bajo (mecánico) | Alto valor, cambio repetitivo |
| **P2** | **Borrar código muerto**: capa MQTT, broadcast `MQTT_RECONNECT`, `CONNECTIVITY_CHANGE` del manifest, `ExponentialBackoffManager` si no se integra | Bajo | Elimina "arquitectura de adorno" y confusión |
| **P2** | Resolver `NotificationRepository` duplicado (interfaz + Impl) | Medio | Restaura el DIP en el flujo principal |
| **P3** | Despurificar dominio (Firebase/Log/BuildConfig/data concretos → abstracciones) + entidades de dominio puras | Alto | Testabilidad y regla de dependencia |
| **P3** | Inyectar+unificar Strategy OEM (fix Oppo/Vivo); descomponer god-services; migrar a `@Binds` | Alto | Mantenibilidad |

**Decisión de arquitectura de fondo:** evaluar si `NotificationForegroundService` es necesario. Un `NotificationListenerService` ya es mantenido vivo por el sistema mientras el permiso esté concedido; eliminar el FGS resolvería de raíz los bugs #1/#3, el wakelock permanente de 10h (batería) y el acoplamiento de dos ciclos de vida.

---

## ✅ Lo que está bien hecho

- Higiene de coroutines en servicios (`SupervisorJob` + cancelación en `onDestroy`).
- `StateFlow` inmutables vía `asStateFlow()`.
- `SavedStateHandle` para formularios (sobrevive process death).
- Flujo de **Auth correctamente desacoplado** (mapea `FirebaseUser→User` con `UserMapper`).
- `DevicePairing` con invariantes validadas en `init` (DDD-lite).
- `ServiceActionReceiver` ↔ `ServiceActionHandler` (SRP limpio, testeable).
- Scoping `@Singleton` correcto para DB/MQTT/sesión; `@ApplicationContext` en todos lados (sin fugas de Activity).
- `PendingIntent` con `FLAG_IMMUTABLE` consistente.
- WorkManager bien configurado (`enqueueUniquePeriodicWork` + `KEEP`, 15 min, `HiltWorkerFactory`).
- Permisos BT runtime API 31+ bien diferenciados (`BLUETOOTH_SCAN`/`CONNECT` vs legacy).
- `MqttMessageHandler` con backpressure (`Channel` + `BufferOverflow.SUSPEND`); `MqttSubscriptionManager` thread-safe (aunque hoy infrautilizados).

---

*Generado mediante auditoría multi-agente. Cada hallazgo incluye ubicación `archivo:línea` verificada. Para profundizar en un subsistema o aplicar fixes, consultar el plan de remediación priorizado.*
