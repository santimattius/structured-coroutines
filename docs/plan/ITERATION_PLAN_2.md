# Structured Coroutines — Plan de Iteraciones V2

> **Base:** v0.7.0 (Kotlin 2.3.20 / kotlinx-coroutines 1.10.2)
> **Propuesta de origen:** `docs/NEW_FEATURES_PROPOSAL.md`
> **Proceso de implementación:** `docs-local/ADDING_NEW_RULES.md`
> **Fecha:** 2026-04-09

---

## Contexto: kotlinx-coroutines 1.10.2

Cada regla nueva se ancla en APIs estables de la versión actualmente configurada. Referencia rápida
de APIs relevantes:

| API                                              | Módulo                    | Disponible desde   | Relevancia  |
|--------------------------------------------------|---------------------------|--------------------|-------------|
| `suspendCancellableCoroutine`                    | core                      | 1.0                | INTEROP_001 |
| `callbackFlow { awaitClose {} }`                 | core                      | 1.3 (estable 1.6)  | INTEROP_002 |
| `MutableStateFlow` / `asStateFlow()`             | core                      | 1.3.6              | FLOW_010    |
| `stateIn(SharingStarted.WhileSubscribed(5_000))` | core                      | 1.5                | FLOW_006    |
| `Flow.catch {}`                                  | core                      | 1.3                | FLOW_005    |
| `Flow.launchIn(scope)`                           | core                      | 1.3                | FLOW_007    |
| `Flow.onEach {}`                                 | core                      | 1.3                | FLOW_008    |
| `flatMapLatest / flatMapMerge / flatMapConcat`   | core                      | 1.3 (stable)       | FLOW_009    |
| `Mutex.withLock {}`                              | core                      | 1.0                | CONCUR_001  |
| `Semaphore(permits)`                             | core                      | 1.3                | CONCUR_002  |
| `runTest {}` / `TestScope`                       | test                      | 1.6 (stable 1.7)   | TEST_004    |
| `StandardTestDispatcher()`                       | test                      | 1.6 (stable 1.7)   | TEST_005    |
| `advanceUntilIdle()` / `advanceTimeBy()`         | test                      | 1.6                | TEST_006    |
| `Flow.chunked(size)`                             | core                      | **1.10.0** (nuevo) | — (doc)     |
| `Flow.timeout(duration)`                         | core                      | **1.9.0**          | — (doc)     |
| `MDCContext()`                                   | slf4j                     | 1.3                | BACKEND_002 |
| `ListenableFuture.await()`                       | guava                     | 1.3                | INTEROP_004 |
| `CompletableFuture.await()`                      | jdk8                      | 1.3                | INTEROP_004 |
| `collectAsStateWithLifecycle()`                  | lifecycle-runtime-compose | 2.6.0              | COMPOSE_001 |

### Cambios clave en 1.10.x relevantes para nuevas reglas

- **`callbackFlow` sin `awaitClose`** lanza `IllegalStateException` en runtime desde 1.6 — ahora lo
  podemos elevar a nivel de análisis estático como ERROR.
- **`StandardTestDispatcher`** reemplaza al deprecated `TestCoroutineDispatcher`; la regla TEST_005
  debe recomendar `StandardTestDispatcher` o `UnconfinedTestDispatcher`.
- **`runTest`** es el entry point estable para tests con virtual time (reemplaza `runBlockingTest`
  deprecated en 1.6, removido en 1.9).
- **`Flow.chunked()`** (nuevo en 1.10.0) — mencionar en BEST_PRACTICES §9 como alternativa a
  `buffer + windowed`.
- **`Flow.timeout()`** (nuevo en 1.9.0) — complementa `withTimeoutOrNull` en cadenas Flow.
- **`Mutex.holdsLock(owner)`** (nuevo en 1.9.0) — útil para debugging de deadlocks.

---

## Iteración 1 — v0.8.0 "Interop & Flow Safety"

**Objetivo:** Cubrir las brechas de mayor impacto: interoperabilidad con callbacks y los patrones de
Flow más problemáticos. Son anti-patrones que producen memory leaks o errores silenciosos en
prácticamente todos los proyectos reales.

**Reglas nuevas: 8**

| # | Código      | Nombre                                | Severidad | Capas                 |
|---|-------------|---------------------------------------|-----------|-----------------------|
| 1 | INTEROP_001 | `SuspendCoroutineWithoutCancellation` | Error     | Compiler, Detekt, IDE |
| 2 | INTEROP_002 | `CallbackFlowWithoutAwaitClose`       | Error     | Compiler, Detekt, IDE |
| 3 | FLOW_010    | `MutableFlowExposed`                  | Warning   | Detekt, IDE           |
| 4 | FLOW_005    | `MissingCatchInFlow`                  | Warning   | Detekt, Lint, IDE     |
| 5 | CONCUR_003  | `SequentialAsyncAwait`                | Warning   | Detekt, IDE           |
| 6 | TEST_004    | `RunBlockingInsteadOfRunTest`         | Warning   | Detekt, Lint, IDE     |
| 7 | COMPOSE_001 | `CollectAsStateWithoutLifecycle`      | Warning   | Lint, IDE             |
| 8 | KMP_001     | `DispatchersIOInCommonMain`           | Error     | Detekt, Lint          |

**Infraestructura:**

- Anotaciones `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher` en `:annotations`
- Perfiles Gradle: `android-compose`, `kmp-common`
- Dependencias opcionales en `libs.versions.toml`: `kotlinx-coroutines-test`,
  `lifecycle-runtime-compose`

---

### Regla INTEROP_001 — `SuspendCoroutineWithoutCancellation`

**Código:** `INTEROP_001`  **Sección BEST_PRACTICES:** Nueva §10.1  **Severidad:** Error

**Motivación:** `suspendCoroutine` no propaga la cancelación del coroutine padre. Cuando el parent
se cancela, la coroutine queda suspendida indefinidamente esperando un callback que puede nunca
llegar, produciendo memory leaks y callbacks "fantasma" que llaman `resume` sobre una continuation
ya descartada.

**API de referencia (kotlinx-coroutines 1.10.2):**

```kotlin
// ✅ suspendCancellableCoroutine — API correcta
suspend fun <T> wrapCallback(block: (CancellableContinuation<T>) -> Unit): T =
    suspendCancellableCoroutine { cont -> block(cont) }
```

**Anti-patrón detectado:**

```kotlin
// ❌ [INTEROP_001] suspendCoroutine sin soporte de cancelación
suspend fun fetchUser(id: String): User = suspendCoroutine { cont ->
    api.getUser(
        id,
        onSuccess = { cont.resume(it) },
        onError = { cont.resumeWithException(it) }
    )
    // si el coroutine padre se cancela, el listener sigue activo
}
```

**Corrección recomendada:**

```kotlin
// ✅ suspendCancellableCoroutine + invokeOnCancellation para cleanup
suspend fun fetchUser(id: String): User = suspendCancellableCoroutine { cont ->
    val call = api.getUser(
        id,
        onSuccess = { cont.resume(it) },
        onError = { cont.resumeWithException(it) }
    )
    cont.invokeOnCancellation { call.cancel() }
}
```

**Heurística de detección:**

- AST: llamada a `suspendCoroutine { }` en el cuerpo de una función `suspend`.
- Exclusión: código en `jvmMain` que ya tiene un `try/finally` con cleanup explícito.
- Falso positivo conocido: ninguno relevante; `suspendCoroutine` casi nunca es la opción correcta.

**Capas de implementación:**

| Capa               | Implementación                                                    | Quick Fix                                                                                              |
|--------------------|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| **Compiler (FIR)** | `FirCallChecker` sobre `suspendCoroutine` call-site               | —                                                                                                      |
| **Detekt**         | `SuspendCoroutineWithoutCancellationRule` — `visitCallExpression` | —                                                                                                      |
| **IDE**            | `SuspendCoroutineInspection` + `ReplaceSuspendCoroutineQuickFix`  | Reemplaza `suspendCoroutine` por `suspendCancellableCoroutine`; añade `invokeOnCancellation { }` vacío |

**Mensaje diagnóstico:**
`[INTEROP_001] 'suspendCoroutine' does not support cancellation. Use 'suspendCancellableCoroutine' and call 'invokeOnCancellation' to release resources. See BEST_PRACTICES §10.1`

**BEST_PRACTICES §10.1 — nueva sección a añadir:**

```
### 10.1 [INTEROP_001] Wrapping Callbacks Without Cancellation Support

| | Description |
|--|-------------|
| **Bad Practice** | Using `suspendCoroutine` to wrap async callbacks. When the parent coroutine is cancelled the callback remains registered, leaking memory and resuming a dead continuation. |
| **Recommended** | Use `suspendCancellableCoroutine` and register cleanup in `invokeOnCancellation`. |

Tool support: Compiler Plugin, Detekt, IntelliJ — rule `SuspendCoroutineWithoutCancellation`.
```

---

### Regla INTEROP_002 — `CallbackFlowWithoutAwaitClose`

**Código:** `INTEROP_002`  **Sección BEST_PRACTICES:** Nueva §10.2  **Severidad:** Error

**Motivación:** `callbackFlow { }` sin `awaitClose { }` emite un `IllegalStateException` en runtime
en kotlinx-coroutines ≥ 1.6 cuando el flow se cancela. La regla eleva esto a análisis estático para
detectarlo antes del runtime.

**API de referencia:**
`callbackFlow { ... awaitClose { /* cleanup */ } }` — `awaitClose` suspende hasta que el collector
cancela o completa; es el único hook de lifecycle para desregistrar listeners.

**Anti-patrón detectado:**

```kotlin
// ❌ [INTEROP_002] callbackFlow sin awaitClose
fun locationFlow(): Flow<Location> = callbackFlow {
    val cb = LocationCallback { trySend(it) }
    manager.register(cb)
    // sin awaitClose → IllegalStateException en runtime + listener leak
}
```

**Corrección recomendada:**

```kotlin
// ✅ callbackFlow con awaitClose para cleanup garantizado
fun locationFlow(): Flow<Location> = callbackFlow {
    val cb = LocationCallback { trySend(it) }
    manager.register(cb)
    awaitClose { manager.unregister(cb) }
}
```

**Heurística de detección:**

- Visitar bloques `callbackFlow { ... }` y verificar si contienen una llamada a `awaitClose(...)`
  dentro del bloque lambda.
- Exclusión: `channelFlow` no requiere `awaitClose` (distinto semántico).

**Capas de implementación:**

| Capa               | Implementación                                                                                     | Quick Fix                                    |
|--------------------|----------------------------------------------------------------------------------------------------|----------------------------------------------|
| **Compiler (FIR)** | `FirCallChecker` — visitar cuerpo del lambda de `callbackFlow`, verificar ausencia de `awaitClose` | —                                            |
| **Detekt**         | `CallbackFlowWithoutAwaitCloseRule`                                                                | —                                            |
| **IDE**            | `CallbackFlowInspection` + `AddAwaitCloseQuickFix`                                                 | Inserta `awaitClose { }` al final del lambda |

---

### Regla FLOW_010 — `MutableFlowExposed`

**Código:** `FLOW_010`  **Sección BEST_PRACTICES:** Nueva §9.5  **Severidad:** Warning

**Motivación:** Exponer `MutableStateFlow` o `MutableSharedFlow` como `public val` (sin el tipo
`StateFlow`/`SharedFlow`) permite que cualquier componente externo emita valores, rompiendo el
patrón UDF (Unidirectional Data Flow) que es el estándar en Android y KMP.

**API de referencia:**
`MutableStateFlow<T>.asStateFlow(): StateFlow<T>` — convierte en read-only; en kotlinx-coroutines
1.10.2 es la forma canónica.

**Anti-patrón detectado:**

```kotlin
// ❌ [FLOW_010] MutableStateFlow expuesto como public
class UserViewModel : ViewModel() {
    val uiState = MutableStateFlow<UiState>(UiState.Loading)   // mutable y público
    val events = MutableSharedFlow<UiEvent>()                  // mutable y público
}
```

**Corrección recomendada:**

```kotlin
// ✅ Backing property privada con tipo read-only expuesto
class UserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
}
```

**Heurística:**

- Declaración `public val` (sin `private`/`internal`/`protected`) cuyo tipo inferido sea
  `MutableStateFlow` o `MutableSharedFlow`.
- También aplica a propiedades con tipo explícito `MutableStateFlow<*>` o `MutableSharedFlow<*>`.
- No reportar dentro de `object` de test o `@Preview`.

**Quick Fix IDE:** Convertir a patrón backing property; ofrecer dos variantes: con prefijo `_` o
manteniendo nombre original con sufijo `Mutable`.

---

### Regla FLOW_005 — `MissingCatchInFlow`

**Código:** `FLOW_005`  **Sección BEST_PRACTICES:** Nueva §9.6  **Severidad:** Warning

**Motivación:** Una cadena Flow que termina en `.collect { }` o `.launchIn()` sin un operador
`.catch { }` upstream propaga excepciones al scope contenedor, potencialmente cancelando el
ViewModel/scope. En producción esto se manifiesta como crashes silenciosos difíciles de rastrear.

**API de referencia:**
`Flow.catch { cause -> ... }` — kotlinx-coroutines 1.10.2, maneja excepciones upstream sin
interrumpir el scope del colector.

**Anti-patrón detectado:**

```kotlin
// ❌ [FLOW_005] cadena Flow sin catch
viewModelScope.launch {
    repository.getItems()
        .map { it.toUiModel() }
        .collect { _state.value = it }  // excepción en map/getItems cancela el scope
}
```

**Corrección recomendada:**

```kotlin
// ✅ catch antes de collect
viewModelScope.launch {
    repository.getItems()
        .map { it.toUiModel() }
        .catch { e -> _error.value = e.message }
        .collect { _state.value = it }
}
```

**Heurística:**

- Llamada terminal `.collect {}`, `.collectLatest {}`, o `.launchIn(scope)` precedida de ≥1 operador
  intermedio (`.map`, `.filter`, `.flatMapLatest`, etc.) sin `.catch {}` en la cadena.
- **No** reportar si la cadena entera está dentro de un `try/catch` que captura `Throwable` o
  `Exception` explícitamente.
- Excluir cadenas que ya usan `.catch {}` o `.onEach { }` combinado con try/catch interno.
- No reportar en tests (archivos bajo `test/` o `androidTest/`).

**Quick Fix IDE:** Insertar `.catch { e -> /* TODO: handle $e */ }` antes del operador terminal.

---

### Regla CONCUR_003 — `SequentialAsyncAwait`

**Código:** `CONCUR_003`  **Sección BEST_PRACTICES:** Nueva §1.5  **Severidad:** Warning

**Motivación:** `async { }.await()` en líneas consecutivas es semánticamente equivalente a
`withContext { }` pero más costoso (crea un `Deferred` y Job extra). Peor aún, transmite la
intención de paralelismo sin conseguirlo.

**API de referencia:**
`coroutineScope { val a = async { }; val b = async { }; a.await() to b.await() }` — patrón correcto
para paralelismo con `async`.

**Anti-patrón detectado:**

```kotlin
// ❌ [CONCUR_003] async secuencial — sin beneficio de paralelismo
suspend fun loadDashboard(): Dashboard {
    val user = async { userRepo.getUser() }.await()    // espera A termina
    val metrics = async { metricsRepo.get() }.await()    // luego espera B — secuencial!
    return Dashboard(user, metrics)
}
```

**Corrección recomendada:**

```kotlin
// ✅ async paralelo — ambas requests corren concurrentemente
suspend fun loadDashboard(): Dashboard = coroutineScope {
    val userDeferred = async { userRepo.getUser() }
    val metricsDeferred = async { metricsRepo.get() }
    Dashboard(userDeferred.await(), metricsDeferred.await())
}
```

**Heurística:**

- Patrón `val result = async { ... }.await()` en la misma sentencia (inline await).
- O: `val d = async { ... }` seguido en la línea inmediatamente siguiente de `d.await()` sin ningún
  otro `async { }` en entre medio.
- Excluir cuando el `async` está dentro de un bucle o lambda que no es parte de un
  `coroutineScope/supervisorScope`.

**Quick Fix IDE:** Opción 1: reemplazar `async { }.await()` por `withContext(coroutineContext) { }`.
Opción 2: extraer a `coroutineScope { }` con múltiples async en paralelo (solo si hay ≥2 en el mismo
scope).

---

### Regla TEST_004 — `RunBlockingInsteadOfRunTest`

**Código:** `TEST_004`  **Sección BEST_PRACTICES:** §6 (actualizar)  **Severidad:** Warning

**Motivación:** `runBlocking` en tests con `delay()` o código suspendido espera tiempo real,
haciendo los tests lentos. `runTest` de `kotlinx-coroutines-test:1.10.2` usa virtual time:
`delay(5000)` se ejecuta en microsegundos, y `advanceTimeBy`/`advanceUntilIdle` permiten control
preciso.

**API de referencia (kotlinx-coroutines-test 1.10.2):**

```kotlin
// runTest — entry point estable para tests con virtual time
@Test
fun `should emit after delay`() = runTest {
    val result = async { repository.getWithRetry() }
    advanceTimeBy(3_000)   // avanza 3 s de tiempo virtual
    advanceUntilIdle()     // espera que todas las coroutines completen
    assertEquals(expected, result.await())
}
```

**Anti-patrón detectado:**

```kotlin
// ❌ [TEST_004] runBlocking con delay — test real de 5 segundos
@Test
fun `should retry after timeout`() = runBlocking {
    delay(5_000)  // espera 5 segundos reales
    assertEquals(expected, viewModel.state.value)
}
```

**Heurística:**

- Función anotada con `@Test` que llama a `runBlocking { }` Y contiene dentro: `delay(...)`, o llama
  código que llama `delay`.
- Extensión existente `RunBlockingWithDelayInTest` ya cubre el caso básico; esta regla la **extiende
  ** para también detectar `runBlocking` sin `delay` pero en contexto de test donde `runTest` sería
  más apropiado (virtual dispatcher).
- Excluir `runBlocking` en funciones `main()` no relacionadas con tests.

**Quick Fix IDE:** Reemplazar `runBlocking {` por `runTest {` con import automático de
`kotlinx.coroutines.test.runTest`.

---

### Regla COMPOSE_001 — `CollectAsStateWithoutLifecycle`

**Código:** `COMPOSE_001`  **Sección BEST_PRACTICES:** §8.3 (nueva subsección)  **Severidad:**
Warning

**Motivación:** `flow.collectAsState()` sigue recolectando aunque el Composable esté en background (
pantalla apagada, app minimizada). En Android, `collectAsStateWithLifecycle()` de
`lifecycle-runtime-compose:2.6+` detiene la recolección automáticamente cuando el lifecycle entra en
`STOPPED`.

**API de referencia:**
`Flow<T>.collectAsStateWithLifecycle(initialValue, lifecycle, minActiveState)` — disponible en
`androidx.lifecycle:lifecycle-runtime-compose:2.6.0+`.

**Anti-patrón detectado:**

```kotlin
// ❌ [COMPOSE_001] recolecta aunque la pantalla esté apagada
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()  // activo incluso en background
}
```

**Corrección recomendada:**

```kotlin
// ✅ para StateFlow desde ViewModel en Android
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
}
```

**Heurística:**

- `.collectAsState()` llamado en un Composable (archivo con `@Composable` functions) sobre un
  `StateFlow` o `Flow` que proviene de un `ViewModel`.
- Excluir tests y Previews (`@Preview`).
- Excluir si el archivo no importa `androidx.lifecycle` (puede ser KMP sin Android).

**Quick Fix IDE:** Reemplazar `.collectAsState()` por `.collectAsStateWithLifecycle()` con import de
`androidx.lifecycle.compose`.

---

### Regla KMP_001 — `DispatchersIOInCommonMain`

**Código:** `KMP_001`  **Sección BEST_PRACTICES:** Nueva §11.1  **Severidad:** Error

**Motivación:** `Dispatchers.IO` no existe en Kotlin/Native (iOS/macOS) ni en Kotlin/JS. Causa
`IllegalStateException: Dispatchers.IO is not supported` en runtime en iOS. Con KMP siendo
production-ready en 2026, esta es una fuente común de crashes post-merge.

**Anti-patrón detectado:**

```kotlin
// ❌ [KMP_001] Dispatchers.IO en commonMain — crash en iOS/JS
// archivo: commonMain/src/.../Repository.kt
suspend fun fetchData(): Data = withContext(Dispatchers.IO) {
    httpClient.get(url)
}
```

**Corrección recomendada:**

```kotlin
// Opción A: inyectar dispatcher (más testeable)
class Repository(private val ioDispatcher: CoroutineDispatcher) {
    suspend fun fetchData(): Data = withContext(ioDispatcher) {
        httpClient.get(url)
    }
}

// Opción B: expect/actual por plataforma
// commonMain:
expect val ioDispatcher: CoroutineDispatcher
// jvmMain/androidMain:
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
// iosMain:
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
```

**Heurística:**

- Uso de `Dispatchers.IO` (qualified call o import) en archivos bajo directorios `commonMain`,
  `commonTest`, o con target `kotlin-multiplatform` sin target específico JVM/Android.
- Detekt: verificar que el módulo tiene `kotlin-multiplatform` plugin y el archivo está en
  `src/commonMain/`.
- Lint: verificar que el source set es `commonMain`.

**Nota de implementación:** Requiere que Detekt/Lint tengan acceso al path del source set. En
Detekt, usar la configuración de `sourceSets` del módulo. En Lint, usar
`context.project.buildTarget`.

---

### Infraestructura v0.8.0

#### 1. Nuevas anotaciones en `:annotations`

```kotlin
// annotations/src/commonMain/kotlin/io/github/santimattius/structured/annotations/DispatcherQualifiers.kt

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DefaultDispatcher
```

Uso esperado:

```kotlin
class Repository(
    @IoDispatcher private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) { ... }
```

La regla `TEST_005` (v0.9.0) usará la presencia de `@IoDispatcher` para determinar si el dispatcher
está correctamente inyectado.

#### 2. Perfiles Gradle ampliados

```kotlin
// gradle-plugin: agregar en StructuredCoroutinesExtension.kt
fun useAndroidComposeProfile() {
    // activa: SCOPE_001-003, RUNBLOCK_001-002, DISPATCH_001,003,004
    // CANCEL_001,003-006, EXCEPT_002, TEST_001,004, FLOW_005,010
    // COMPOSE_001, ARCH_002
}

fun useKmpCommonProfile() {
    // activa: todo el perfil strict
    // + KMP_001-004
    // desactiva: ARCH_002 (Android-only), COMPOSE_001 (Android-only)
}
```

#### 3. Nuevas dependencias opcionales en `libs.versions.toml`

```toml
[versions]
kotlinx-coroutines-test = "1.10.2"     # mismo que core
lifecycle-runtime-compose = "2.8.0"    # para COMPOSE_001 quick fix import

[libraries]
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle-runtime-compose" }
```

#### 4. Secciones nuevas en `docs/BEST_PRACTICES_COROUTINES.md`

- **§9.5** — `MutableFlowExposed`: MutableStateFlow backing property
- **§9.6** — `MissingCatchInFlow`: error handling en cadenas Flow
- **§10** — Nueva sección: "Interoperabilidad (Callbacks → Coroutines)"
    - §10.1 — `SuspendCoroutineWithoutCancellation`
    - §10.2 — `CallbackFlowWithoutAwaitClose`
- **§11** — Nueva sección: "Kotlin Multiplatform"
    - §11.1 — `DispatchersIOInCommonMain`
- **§6** (Testing) — Actualizar §6.1 para incluir `runTest` vs `runBlocking`

#### 5. `rule-codes.yml` — entradas nuevas

```yaml
  - code: INTEROP_001
    section: "10.1"
    title: "Wrapping Callbacks Without Cancellation Support"
    doc_anchor: "101-interop_001--wrapping-callbacks-without-cancellation-support"
    compiler: [ SUSPEND_COROUTINE_WITHOUT_CANCELLATION ]
    detekt: [ SuspendCoroutineWithoutCancellation ]
    lint: [ ]
    intellij: [ SuspendCoroutineWithoutCancellation ]
    default_severity: error

  - code: INTEROP_002
    section: "10.2"
    title: "callbackFlow Without awaitClose"
    doc_anchor: "102-interop_002--callbackflow-without-awaitclose"
    compiler: [ CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE ]
    detekt: [ CallbackFlowWithoutAwaitClose ]
    lint: [ ]
    intellij: [ CallbackFlowWithoutAwaitClose ]
    default_severity: error

  - code: FLOW_005
    section: "9.6"
    title: "Missing catch in Flow Chain"
    doc_anchor: "96-flow_005--missing-catch-in-flow-chain"
    compiler: [ ]
    detekt: [ MissingCatchInFlow ]
    lint: [ MissingCatchInFlow ]
    intellij: [ MissingCatchInFlow ]
    default_severity: warning

  - code: FLOW_010
    section: "9.5"
    title: "MutableStateFlow/MutableSharedFlow Exposed as Public"
    doc_anchor: "95-flow_010--mutablestateflow-exposed"
    compiler: [ ]
    detekt: [ MutableFlowExposed ]
    lint: [ ]
    intellij: [ MutableFlowExposed ]
    default_severity: warning

  - code: CONCUR_003
    section: "1.5"
    title: "Sequential async/await (wasted parallelism)"
    doc_anchor: "15-concur_003--sequential-asyncawait"
    compiler: [ ]
    detekt: [ SequentialAsyncAwait ]
    lint: [ ]
    intellij: [ SequentialAsyncAwait ]
    default_severity: warning

  - code: TEST_004
    section: "6.4"
    title: "runBlocking Instead of runTest"
    doc_anchor: "64-test_004--runblocking-instead-of-runtest"
    compiler: [ ]
    detekt: [ RunBlockingInsteadOfRunTest ]
    lint: [ RunBlockingInsteadOfRunTest ]
    intellij: [ RunBlockingInsteadOfRunTest ]
    default_severity: warning

  - code: COMPOSE_001
    section: "8.3"
    title: "collectAsState Without Lifecycle Awareness"
    doc_anchor: "83-compose_001--collectasstate-without-lifecycle-awareness"
    compiler: [ ]
    detekt: [ ]
    lint: [ CollectAsStateWithoutLifecycle ]
    intellij: [ CollectAsStateWithoutLifecycle ]
    default_severity: warning

  - code: KMP_001
    section: "11.1"
    title: "Dispatchers.IO in commonMain"
    doc_anchor: "111-kmp_001--dispatchersio-in-commonmain"
    compiler: [ ]
    detekt: [ DispatchersIOInCommonMain ]
    lint: [ DispatchersIOInCommonMain ]
    intellij: [ ]
    default_severity: error
```

#### 6. Kotlin Coroutines Skill — actualizar

Añadir al `SYSTEM_PROMPT.md` y al índice de referencias:

- INTEROP_001/002: cuándo usar `suspendCancellableCoroutine` vs `callbackFlow`
- FLOW_010: backing property pattern para StateFlow
- TEST_004: `runTest` como standard para tests con coroutines
- KMP_001: dispatcher injection pattern para KMP

#### Definition of Done — v0.8.0

- [ ] 8 reglas implementadas en las capas especificadas
- [ ] Tests unitarios con ≥3 casos por regla (true positive, false positive guard, suppression)
- [ ] Samples en `:sample-detekt` para las 6 reglas Detekt
- [ ] BEST_PRACTICES actualizado (§§9.5, 9.6, 10.1, 10.2, 11.1)
- [ ] `rule-codes.yml` actualizado con 8 entradas nuevas
- [ ] `SUPPRESSING_RULES.md` actualizado
- [ ] README.md — tablas de reglas actualizadas
- [ ] Gradle plugin: perfiles `android-compose`, `kmp-common`
- [ ] Annotations module: `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`
- [ ] CHANGELOG entrada v0.8.0

---

## Iteración 2 — v0.9.0 "Concurrency, KMP & Backend"

**Objetivo:** Cubrir los patrones de concurrencia compartida que producen race conditions, expandir
soporte KMP completo, y añadir las primeras reglas específicas de backend (Ktor/Spring). Introducir
el baseline inteligente para adopción gradual en proyectos grandes.

**Reglas nuevas: 10**

| #  | Código      | Nombre                           | Severidad | Capas             |
|----|-------------|----------------------------------|-----------|-------------------|
| 1  | CONCUR_001  | `SynchronizedInCoroutine`        | Warning   | Detekt, Lint, IDE |
| 2  | CONCUR_002  | `SharedMutableStateInCoroutine`  | Warning   | Detekt            |
| 3  | CONCUR_004  | `RedundantWithContext`           | Warning   | Detekt, IDE       |
| 4  | FLOW_006    | `StateInWithEagerlyStrategy`     | Warning   | Detekt, Lint, IDE |
| 5  | FLOW_007    | `LaunchInWithUnstructuredScope`  | Warning   | Lint, IDE         |
| 6  | FLOW_008    | `SideEffectInMapOperator`        | Warning   | Detekt, IDE       |
| 7  | KMP_002     | `RunBlockingInCommonMain`        | Error     | Detekt, Lint      |
| 8  | KMP_003     | `MainScopeWithoutCancel`         | Warning   | Detekt            |
| 9  | BACKEND_001 | `BlockingCallInCoroutineBackend` | Warning   | Detekt            |
| 10 | BACKEND_002 | `ThreadLocalNotPropagated`       | Warning   | Detekt            |

**Infraestructura:**

- Baseline inteligente en Gradle plugin
- Perfil `ktor-backend`
- Reporte HTML v2 con Learning Path

---

### Regla CONCUR_001 — `SynchronizedInCoroutine`

**Código:** `CONCUR_001`  **Sección BEST_PRACTICES:** Nueva §12.1  **Severidad:** Warning

**Motivación:** `synchronized(lock) { }` bloquea el thread subyacente del dispatcher. En
`Dispatchers.Main` (single-thread) puede causar deadlock si el código dentro del bloque suspende. En
`limitedParallelism(n)` satura el pool. La alternativa coroutine-native es `Mutex.withLock { }`.

**API de referencia (kotlinx-coroutines 1.10.2):**

```kotlin
val mutex = Mutex()
mutex.withLock { /* coroutine-safe, no bloquea thread */ }
// Nuevo en 1.9.0: mutex.holdsLock(owner) para debugging
```

**Anti-patrón:**

```kotlin
// ❌ [CONCUR_001] synchronized bloquea el dispatcher thread
private val lock = Any()

suspend fun increment() {
    synchronized(lock) {  // bloquea el thread — deadlock risk en Main
        counter++
    }
}
```

**Corrección recomendada:**

```kotlin
// ✅ Mutex — suspende la coroutine, no bloquea el thread
private val mutex = Mutex()

suspend fun increment() {
    mutex.withLock { counter++ }
}
```

**Heurística:** `synchronized(...)` dentro de una función `suspend` o dentro de un builder (
`launch`, `async`, `withContext`).

**Quick Fix IDE:** Ofrecer:

1. Reemplazar `synchronized(lock) { body }` por `mutex.withLock { body }` + declarar
   `private val mutex = Mutex()` en la clase.
2. Para casos simples de contador: reemplazar `var counter` por `AtomicInteger` de
   `java.util.concurrent.atomic`.

---

### Regla CONCUR_002 — `SharedMutableStateInCoroutine`

**Código:** `CONCUR_002`  **Sección BEST_PRACTICES:** Nueva §12.2  **Severidad:** Warning

**Motivación:** Múltiples coroutines que leen/modifican la misma colección mutable (`ArrayList`,
`HashMap`) o variable `var` sin sincronización pueden producir race conditions aunque el código
parezca secuencial. La heurística detecta el patrón más común: `var` o colección mutable de stdlib
accedida desde múltiples `launch { }` en el mismo scope.

**Anti-patrón:**

```kotlin
// ❌ [CONCUR_002] ArrayList no es thread-safe
var results = mutableListOf<Result>()

coroutineScope {
    items.forEach { item ->
        launch {
            results.add(processItem(item))  // race condition
        }
    }
}
```

**Corrección recomendada:**

```kotlin
// ✅ Opción A: colectar con awaitAll (más idiomático)
val results = coroutineScope {
    items.map { item -> async { processItem(item) } }.awaitAll()
}

// ✅ Opción B: Channel para resultados concurrentes
val results = Channel<Result>(Channel.UNLIMITED)
coroutineScope {
    items.forEach { launch { results.send(processItem(it)) } }
}
results.close()
```

**Heurística (heurística pesada — alta tasa de FP posible):**

- `var mutableList` / `var mutableMapOf` a nivel de función o clase, accedida dentro de múltiples
  lambdas `launch { }` en el mismo scope.
- Configurar por defecto como `warning` con severity configurable hasta `info` para reducir ruido.
- Excluir cuando hay un `mutex.withLock { }` envolviendo el acceso.

---

### Regla CONCUR_004 — `RedundantWithContext`

**Código:** `CONCUR_004`  **Sección BEST_PRACTICES:** Nueva §3.6  **Severidad:** Warning (opt-in,
desactivada por defecto)

**Motivación:** `withContext(dispatcher)` anidado que usa el mismo dispatcher que el contexto actual
añade overhead de context-switch sin ningún beneficio funcional.

**Anti-patrón:**

```kotlin
// ❌ [CONCUR_004] doble switch al mismo dispatcher
class Repository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getData(): Data = withContext(ioDispatcher) {
        withContext(ioDispatcher) {  // redundante — ya estamos en ioDispatcher
            db.query()
        }
    }
}
```

**Heurística:** Anidamiento directo de `withContext(X) { withContext(X) { } }` donde `X` es el mismo
callee en ambos niveles. **Solo** cuando el dispatcher es una referencia a la misma
variable/expresión; no aplica a `Dispatchers.IO` vs `Dispatchers.IO` literal (podría ser override en
tests).

---

### Regla FLOW_006 — `StateInWithEagerlyStrategy`

**Código:** `FLOW_006`  **Sección BEST_PRACTICES:** Nueva §9.7  **Severidad:** Warning

**Motivación:** `SharingStarted.Eagerly` inicia la recolección inmediatamente al crear el StateFlow,
incluso si ningún suscriptor está activo. En ViewModels, esto significa trabajo de red/DB desde la
instanciación del ViewModel, desperdiciando recursos si la pantalla nunca se muestra.

**API de referencia (kotlinx-coroutines 1.10.2):**

```kotlin
// SharingStarted.WhileSubscribed(stopTimeoutMillis, replayExpirationMillis)
// stopTimeoutMillis = 5_000 → mantiene activo 5s tras el último suscriptor (rotation safety)
// replayExpirationMillis = Long.MAX_VALUE → mantiene el último valor indefinidamente
SharingStarted.WhileSubscribed(5_000)
```

**Anti-patrón:**

```kotlin
// ❌ [FLOW_006] Eagerly en ViewModel — trabajo innecesario en background
val items: StateFlow<List<Item>> = repository.getItems()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

**Corrección recomendada:**

```kotlin
// ✅ WhileSubscribed — activo solo mientras hay suscriptores + buffer 5s para rotación
val items: StateFlow<List<Item>> = repository.getItems()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

**Heurística:** `.stateIn(scope, SharingStarted.Eagerly, ...)` donde `scope` es `viewModelScope` o
`lifecycleScope`. Para otros scopes (tests, `GlobalScope` — ya cubierto) no reportar por defecto.

**Quick Fix IDE:** Reemplazar `SharingStarted.Eagerly` con `SharingStarted.WhileSubscribed(5_000)`.

---

### Regla FLOW_007 — `LaunchInWithUnstructuredScope`

**Código:** `FLOW_007`  **Sección BEST_PRACTICES:** Nueva §9.8  **Severidad:** Warning

**Motivación:** `.launchIn(GlobalScope)` o `.launchIn(CoroutineScope(Dispatchers.IO))` tienen el
mismo problema que `GlobalScope.launch`: la coroutine queda huérfana y nunca se cancela. Es el
equivalente de SCOPE_001 pero a través del Flow API.

**Anti-patrón:**

```kotlin
// ❌ [FLOW_007] launchIn con scope no estructurado
fun startObserving() {
    dataFlow
        .onEach { process(it) }
        .launchIn(GlobalScope)  // huérfano
}
```

**Heurística:** `.launchIn(GlobalScope)` o `.launchIn(CoroutineScope(...))` inline. Reusar la lógica
de detección de `GlobalScopeUsage` y `InlineCoroutineScope`.

---

### Regla FLOW_008 — `SideEffectInMapOperator`

**Código:** `FLOW_008`  **Sección BEST_PRACTICES:** Nueva §9.9  **Severidad:** Warning (opt-in)

**Motivación:** `map` debe ser una transformación pura. Efectos secundarios en `map` complican el
testing (hay que mockear el efecto para testear la transformación) y violan el principio de
responsabilidad única en cadenas Flow.

**API de referencia:** `Flow.onEach { }` — operador para efectos secundarios en kotlinx-coroutines
1.10.2.

**Anti-patrón:**

```kotlin
// ❌ [FLOW_008] efecto secundario en map
flow.map { item ->
    analytics.track("item_viewed", item.id)  // efecto secundario
    item.toUiModel()
}
```

**Corrección recomendada:**

```kotlin
// ✅ onEach para efectos, map para transformaciones
flow
    .onEach { item -> analytics.track("item_viewed", item.id) }
    .map { it.toUiModel() }
```

**Heurística (difícil — alta tasa de FP):**

- Llamadas dentro de `.map { }` que no retornan el item transformado en la primera línea, sino que
  tienen statements adicionales.
- Específicamente detectar: logging calls, analytics calls, DB writes, UI updates.
- Configurar como `info` por defecto o activar solo con perfil verbose.

---

### Regla KMP_002 — `RunBlockingInCommonMain`

**Código:** `KMP_002`  **Sección BEST_PRACTICES:** §11.2 (nueva)  **Severidad:** Error

**Motivación:** `runBlocking` no existe en Kotlin/JS y causa deadlock en iOS cuando se llama desde
el main thread (el runtime de Kotlin/Native no puede suspender el main thread con `runBlocking`).

**Anti-patrón:**

```kotlin
// ❌ [KMP_002] — en commonMain: crash en JS, deadlock en iOS main thread
fun loadSync(): Data = runBlocking {
    repository.fetchData()
}
```

**Heurística:** `runBlocking` en archivos bajo `commonMain` o `commonTest`.

---

### Regla KMP_003 — `MainScopeWithoutCancel`

**Código:** `KMP_003`  **Sección BEST_PRACTICES:** §11.3 (nueva)  **Severidad:** Warning

**Motivación:** En KMP con Swift interop, `MainScope()` no se cancela automáticamente por ARC de
iOS/Swift. Requiere `onDestroy()` / `dealloc` explícito. La regla detecta clases que crean
`MainScope()` sin una función de cleanup.

**Anti-patrón:**

```kotlin
// ❌ [KMP_003] — en commonMain: MainScope sin cancel
class SharedPresenter {
    private val scope = MainScope()
    fun onDestroy() { /* olvidó scope.cancel() */
    }
}
```

**Heurística:**

- Clase que declara `private val ... = MainScope()` Y tiene una función llamada `onDestroy`,
  `onCleared`, o `dispose` que **no** llama `scope.cancel()` (o el nombre de la variable que
  contiene el scope).
- Si no existe ninguna función de cleanup, también reportar.

---

### Regla BACKEND_001 — `BlockingCallInCoroutineBackend`

**Código:** `BACKEND_001`  **Sección BEST_PRACTICES:** §3.1 (extender)  **Severidad:** Warning

**Motivación:** Extiende `BlockingCallInCoroutine` con patrones específicos de backend JVM:

- Llamadas JDBC sin `Dispatchers.IO` (ya en Detekt básico)
- Llamadas bloqueantes en handlers de Ktor (`routing { get { ... } }`)
- `Thread.sleep()`, `.join()`, `CountDownLatch.await()` dentro de coroutines

**API de referencia:**

- Ktor: `routing { get { withContext(Dispatchers.IO) { } } }`
- Spring: `@Transactional` + `suspend fun` requiere driver no-bloqueante (R2DBC)

**Heurística adicional al Detekt existente:**

- Llamadas a métodos en clases que implementan `java.sql.*` interfaces (JDBC) sin
  `withContext(Dispatchers.IO)`.
- Llamadas a `CountDownLatch.await()`, `Semaphore.acquire()` (java.util), `Thread.sleep()` dentro de
  `suspend` fun.

---

### Regla BACKEND_002 — `ThreadLocalNotPropagated`

**Código:** `BACKEND_002`  **Sección BEST_PRACTICES:** Nueva §3.7  **Severidad:** Warning

**Motivación:** `ThreadLocal` y `MDC` (SLF4J Mapped Diagnostic Context) no se propagan
automáticamente entre threads. Al cambiar de dispatcher en backend, los datos de tracing/logging (
traceId, userId, sessionId) desaparecen de los logs.

**API de referencia:**

```kotlin
// kotlinx-coroutines-slf4j: MDCContext
withContext(Dispatchers.IO + MDCContext()) {
    // MDC propagado correctamente al nuevo thread
}

// Para ThreadLocal genérico (kotlinx-coroutines 1.10.2):
val threadLocalElement = threadLocal.asContextElement(value = "contextValue")
withContext(Dispatchers.IO + threadLocalElement) { ... }
```

**Heurística:**

- `MDC.put(...)` o `MDC.get(...)` en una función `suspend` que también contiene
  `withContext(Dispatchers.IO)` o `withContext(Dispatchers.Default)` sin `MDCContext()` en el
  contexto.
- Requiere que `kotlinx-coroutines-slf4j` esté en el classpath para activarse.

---

### Infraestructura v0.9.0

#### 1. Baseline inteligente en Gradle plugin

```kotlin
// build.gradle.kts
structuredCoroutines {
    baseline {
        file = rootProject.file("coroutines-baseline.xml")
        mode = BaselineMode.REPORT_NEW_ONLY  // INFO para existentes, WARNING/ERROR para nuevas
        autoUpdate = false  // requiere ./gradlew generateCoroutinesBaseline
    }
}
```

Tarea Gradle nueva: `generateCoroutinesBaseline` — genera/actualiza `coroutines-baseline.xml` con
todas las violaciones actuales.

#### 2. Perfil `ktor-backend`

```kotlin
fun useKtorBackendProfile() {
    // activa: SCOPE_*, RUNBLOCK_002, DISPATCH_001,003,004
    // CANCEL_*, EXCEPT_*, TEST_*, FLOW_005,010
    // BACKEND_001, BACKEND_002, CONCUR_001
    // desactiva: ARCH_002, COMPOSE_001 (no aplica en backend)
}
```

#### 3. Reporte HTML v2 — Learning Path

El `structuredCoroutinesReport` task genera una sección adicional:

```
## Suggested Learning Path
1. 🔴 Critical (fix now): INTEROP_001, INTEROP_002 — memory leaks
2. 🟠 High (fix this sprint): FLOW_005, FLOW_010, CONCUR_001
3. 🟡 Medium (backlog): FLOW_006, FLOW_007, TEST_004
4. 🟢 Low (when possible): FLOW_008, DEBUG_001
```

Ordenado por: `(violationCount × impactScore) / estimatedFixTime`

#### 4. Secciones en BEST_PRACTICES

- **§3.6** — `RedundantWithContext`
- **§3.7** — `ThreadLocalNotPropagated` (MDC)
- **§9.7** — `StateInWithEagerlyStrategy`
- **§9.8** — `LaunchInWithUnstructuredScope`
- **§9.9** — `SideEffectInMapOperator`
- **§11.2** — `RunBlockingInCommonMain`
- **§11.3** — `MainScopeWithoutCancel`
- **§12** — Nueva sección: "Concurrencia Compartida"
    - §12.1 — `SynchronizedInCoroutine`
    - §12.2 — `SharedMutableStateInCoroutine`
- **§13** — Nueva sección: "Backend (Ktor / Spring)"
    - §13.1 — `BlockingCallInCoroutineBackend`

#### Definition of Done — v0.9.0

- [x] 10 reglas implementadas en capas especificadas
- [x] Tests con ≥3 casos por regla (Detekt/Lint; IDE config smoke; verificar `:intellij-plugin:test` en CI)
- [x] Samples en `:sample-detekt` para reglas Detekt (8/9 archivos; KMP_002 cubierto por fixture Detekt en tests)
- [x] BEST_PRACTICES §§3.6, 3.7, 9.7–9.9, 11.2–11.3, 12.1–12.2, 13.1
- [x] `rule-codes.yml` con 10 entradas nuevas (+ `learning_path`)
- [x] Gradle plugin: baseline, perfil `ktor-backend`, `applyCoroutinesBaseline`
- [x] Reporte HTML v2 con Learning Path
- [x] CHANGELOG entrada v0.9.0

**SDD archived:** 2026-05-25 · ramas `feat/v090-phase1-core-rules` → `phase2` → `phase3` · verify PASS WITH WARNINGS

---

## Iteración 3 — v1.0.0 "Production Ready Complete"

**Objetivo:** Completar la cobertura de Compose, testing moderno, debugging y todos los patrones
Flow pendientes. Alcanzar el estado de toolkit completo para el ciclo completo de desarrollo con
Kotlin Coroutines en todos los targets (Android, iOS vía KMP, Backend JVM, JS).

**Reglas nuevas: 9**

| # | Código      | Nombre                        | Severidad | Capas             |
|---|-------------|-------------------------------|-----------|-------------------|
| 1 | COMPOSE_002 | `RememberScopeForInit`        | Warning   | Lint, IDE         |
| 2 | COMPOSE_003 | `SideEffectInComposable`      | Warning   | Lint              |
| 3 | TEST_005    | `HardcodedDispatcherInClass`  | Warning   | Detekt, IDE       |
| 4 | TEST_006    | `CoroutineNotCompletedInTest` | Warning   | IDE               |
| 5 | FLOW_009    | `FlatMapOperatorChoice`       | Info      | IDE (guía)        |
| 6 | FLOW_011    | `SharedFlowForOneshotEvents`  | Warning   | Detekt, IDE       |
| 7 | INTEROP_003 | `ChannelFlowVsCallbackFlow`   | Warning   | Detekt, IDE       |
| 8 | INTEROP_004 | `BlockingFutureGet`           | Warning   | Detekt, Lint, IDE |
| 9 | DEBUG_001   | `MissingCoroutineName`        | Info      | Detekt (opt-in)   |

**Infraestructura:**

- Flow Chain Analyzer IDE (MVP)
- Perfil `spring-backend`
- Kotlin Coroutines Skill v3.0.0
- Documentación v1.0 completa

---

### Regla COMPOSE_002 — `RememberScopeForInit`

**Código:** `COMPOSE_002`  **Sección BEST_PRACTICES:** §8.4 (nueva)  **Severidad:** Warning

**Motivación:** `rememberCoroutineScope` está diseñado para coroutines disparadas por **interacción
del usuario** (clicks, swipes). Usarlo para efectos de inicialización (cargar datos, observar,
registrar) provoca que `scope.launch { }` se ejecute en cada recomposición, multiplicando las
llamadas.

**API de referencia:**

```kotlin
// LaunchedEffect(key) — se ejecuta una vez por key; se cancela y relanza si key cambia
LaunchedEffect(userId) { viewModel.loadUser(userId) }

// rememberCoroutineScope — para eventos de usuario en handlers
val scope = rememberCoroutineScope()
Button(onClick = { scope.launch { viewModel.submitForm() } })
```

**Heurística:**

- `scope.launch { }` (donde `scope` proviene de `rememberCoroutineScope()`) en el cuerpo del
  Composable fuera de lambdas de handlers de eventos (`onClick`, `onValueChange`, etc.).

---

### Regla COMPOSE_003 — `SideEffectInComposable`

**Código:** `COMPOSE_003`  **Sección BEST_PRACTICES:** §8.5 (nueva)  **Severidad:** Warning

**Motivación:** Llamadas a funciones con efectos secundarios (analytics tracking, estado mutación,
DB writes) directamente en el cuerpo de un Composable se ejecutan en cada recomposición. Deben estar
en `SideEffect { }`, `LaunchedEffect { }`, o `DisposableEffect { }`.

**Heurística:**

- Detección de llamadas a funciones conocidas de analytics, logging, o mutación de estado (
  `viewModel.track(...)`, `analytics.log(...)`) directamente en el cuerpo de una función
  `@Composable`, fuera de efectos o handlers.

---

### Regla TEST_005 — `HardcodedDispatcherInClass`

**Código:** `TEST_005`  **Sección BEST_PRACTICES:** §6.5 (nueva)  **Severidad:** Warning

**Motivación:** Clases que usan `Dispatchers.IO` o `Dispatchers.Main` hardcodeados requieren
`Dispatchers.setMain()` / `resetMain()` en tests, que es frágil en tests paralelos y requiere un
`TestRule` o `@BeforeEach`/`@AfterEach`. Inyectar el dispatcher permite usar
`UnconfinedTestDispatcher()` directamente.

**API de referencia (kotlinx-coroutines-test 1.10.2):**

```kotlin
// UnconfinedTestDispatcher — ejecuta inmediatamente, sin virtual time
// StandardTestDispatcher — requiere advanceUntilIdle() / advanceTimeBy()
class RepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun test() = runTest(testDispatcher) {
        val repo = Repository(ioDispatcher = testDispatcher)
        // ...
    }
}
```

**Heurística:**

- Clase (no en `test/`) que contiene `Dispatchers.IO` o `Dispatchers.Main` literal en el cuerpo de
  una función `suspend` o builder (no en valor default de parámetro).
- No reportar si la clase tiene un constructor/init que acepta un `CoroutineDispatcher` parámetro
  anotado con `@IoDispatcher` o `@MainDispatcher`.
- No reportar en ViewModel que usa `viewModelScope` (el scope ya es testeable vía `setMain`).

---

### Regla TEST_006 — `CoroutineNotCompletedInTest`

**Código:** `TEST_006`  **Sección BEST_PRACTICES:** §6.6 (nueva)  **Severidad:** Warning

**Motivación:** Dentro de `runTest { }`, si la función bajo test lanza una coroutine internamente (
via `viewModelScope.launch`) y el test no llama `advanceUntilIdle()`, las aserciones pueden
ejecutarse antes de que el trabajo haya completado, produciendo flaky tests.

**API de referencia:**

```kotlin
runTest {
    viewModel.loadData()     // lanza internamente viewModelScope.launch { }
    advanceUntilIdle()       // espera que todas las coroutines completen
    assertEquals(expected, viewModel.state.value)
}
```

**Heurística:**

- Dentro de `runTest { }`: llamada a una función que no es `suspend` (o es `suspend` pero retorna
  `Unit`) seguida de una aserción (`assertEquals`, `assertThat`, etc.) sin `advanceUntilIdle()` o
  `advanceTimeBy(...)` entre medio.
- Alta probabilidad de FP → configurar como `warning` con descripción detallada.

---

### Regla FLOW_009 — `FlatMapOperatorChoice`

**Código:** `FLOW_009`  **Sección BEST_PRACTICES:** Nueva §9.10  **Severidad:** Info (guía
contextual)

**Motivación:** La elección entre `flatMapLatest`, `flatMapMerge` y `flatMapConcat` es uno de los
errores de diseño más frecuentes con Flow. La regla provee guía contextual en el IDE basada en el
nombre del Flow o el tipo de operación.

**Guía de decisión (para IDE tooltip/inspection description):**

| Operador          | Cuándo usar                                | Anti-patrón                                        |
|-------------------|--------------------------------------------|----------------------------------------------------|
| `flatMapLatest`   | Búsqueda en tiempo real, última query gana | Usado para downloads (cancela descarga en curso)   |
| `flatMapMerge(n)` | Procesamiento paralelo sin orden           | Usado para búsqueda (race condition de resultados) |
| `flatMapConcat`   | Procesamiento ordenado y secuencial        | Usado para búsqueda (acumula resultados viejos)    |

**Heurística de detección (reducida, solo casos claros):**

- `flatMapLatest` en un Flow cuya lambda devuelve una colección de items independientes (descarga,
  upload múltiple).
- `flatMapConcat` con un Flow de queries de búsqueda (detectado por nombre: `searchQuery`, `query`,
  `searchText`).

---

### Regla FLOW_011 — `SharedFlowForOneshotEvents`

**Código:** `FLOW_011`  **Sección BEST_PRACTICES:** Nueva §9.11  **Severidad:** Warning

**Motivación:** `MutableSharedFlow(replay=0)` para eventos one-shot (navegación, snackbars) puede
perder eventos emitidos antes de que el colector esté activo. `Channel(BUFFERED).receiveAsFlow()`
garantiza entrega para al menos un colector.

**API de referencia (kotlinx-coroutines 1.10.2):**

```kotlin
// Patrón recomendado por Google/Android team para eventos one-shot en ViewModel
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events: Flow<UiEvent> = _events.receiveAsFlow()

// Emitir:
viewModelScope.launch { _events.send(UiEvent.NavigateTo(screen)) }
```

**Heurística:**

- `MutableSharedFlow<T>()` sin argumentos (replay=0, extraBufferCapacity=0) cuyo nombre sugiere
  eventos one-shot (contiene "event", "Event", "command", "Command", "effect", "Effect").

---

### Regla INTEROP_003 — `ChannelFlowVsCallbackFlow`

**Código:** `INTEROP_003`  **Sección BEST_PRACTICES:** §10.3 (nueva)  **Severidad:** Warning

**Motivación:** `channelFlow { }` está diseñado para emisión desde múltiples coroutines internas.
`callbackFlow { }` está diseñado para wrapping de callbacks externos con cleanup via `awaitClose`.
Confundirlos lleva a flows sin cleanup o cleanup innecesario.

**Heurística:**

- `channelFlow { }` que registra un listener/callback externo sin `awaitClose` (mismo problema que
  INTEROP_002 pero con `channelFlow`).
- `callbackFlow { }` sin ningún callback externo (solo emite desde coroutines internas → debería ser
  `channelFlow`).

---

### Regla INTEROP_004 — `BlockingFutureGet`

**Código:** `INTEROP_004`  **Sección BEST_PRACTICES:** §10.4 (nueva)  **Severidad:** Warning

**Motivación:** `.get()` en `Future`, `ListenableFuture`, `CompletableFuture` dentro de una
coroutine bloquea el dispatcher thread. Las extensiones de `kotlinx-coroutines-guava` y
`kotlinx-coroutines-jdk8` proveen `.await()` no-bloqueante.

**API de referencia:**

```kotlin
// kotlinx-coroutines-jdk8 (1.10.2)
val result: T = completableFuture.await()  // no-bloqueante

// kotlinx-coroutines-guava (1.10.2)
val result: T = listenableFuture.await()   // no-bloqueante
```

**Heurística:** Llamadas a `.get()` o `.get(timeout, unit)` en tipos que implementan
`java.util.concurrent.Future` dentro de una función `suspend` o coroutine builder.

**Quick Fix IDE:** Reemplazar `.get()` por `.await()` con import condicional de
`kotlinx-coroutines-guava` o `kotlinx-coroutines-jdk8` según el tipo.

---

### Regla DEBUG_001 — `MissingCoroutineName`

**Código:** `DEBUG_001`  **Sección BEST_PRACTICES:** Nueva §14.1  **Severidad:** Info (opt-in,
desactivado por defecto)

**Motivación:** Las coroutines sin nombre aparecen como `"coroutine#123"` en stacktraces y en el
IntelliJ Coroutines Debugger. `CoroutineName("descriptor")` facilita enormemente el debugging en
producción y durante el desarrollo.

**API de referencia:**
`CoroutineName(name: String)` — kotlinx-coroutines 1.10.2 core.

**Anti-patrón:**

```kotlin
// ❌ [DEBUG_001] — nombre genérico dificulta debugging
viewModelScope.launch {
    loadUserData()
}

// ✅ — nombre descriptivo en logs y debugger
viewModelScope.launch(CoroutineName("load-user-${userId}")) {
    loadUserData()
}
```

**Heurística:** `launch { }` o `async { }` sin `CoroutineName` en el contexto. **Activar solo en
proyectos con perfil `verbose` o `debug`.**

---

### Infraestructura v1.0.0

#### 1. Flow Chain Analyzer IDE (MVP)

**Descripción:** Intention / quick inspection que al posicionar el cursor en cualquier operador de
una cadena Flow muestra en un popup:

```
Flow chain analysis:
  ✅ catch        — error handling present
  ✅ onEach       — side effects separated
  ⚠️ missing distinctUntilChanged — potential redundant emissions
  ℹ️  flatMapLatest — last-wins semantics (suitable for search, not for downloads)
  → Dispatcher: inherited (not pinned)
```

Implementación: análisis PSI de la cadena desde el operador terminal hacia arriba. MVP cubre
detección de `catch`, `distinctUntilChanged`, y tipo de `flatMap`.

#### 2. Perfil `spring-backend`

```kotlin
fun useSpringBackendProfile() {
    // activa: SCOPE_*, RUNBLOCK_002, DISPATCH_001,003,004
    // CANCEL_*, EXCEPT_*, FLOW_005,010
    // BACKEND_001, BACKEND_002
    // + nota especial: en Spring, @Transactional + suspend requiere R2DBC
}
```

#### 3. Kotlin Coroutines Skill v3.0.0

Actualizaciones:

- Nuevas secciones §§10-14 en referencias
- SYSTEM_PROMPT: reglas para INTEROP_001-004, KMP_001-003, COMPOSE_001-003
- Guía de decisión: `callbackFlow` vs `channelFlow`, `flatMapLatest` vs `flatMapMerge`
- Triage table extendida a 65+ entradas

#### 4. Documentación v1.0

- BEST_PRACTICES: §§8.3–8.5, 9.10–9.11, 10.3–10.4, 11–14
- DECISION_GUIDE: secciones 11–14 para los nuevos dominios
- `rule-codes.yml`: 9 entradas nuevas (total: 55+ reglas documentadas)
- Guía de migración desde v0.7.x
- Guías de integración por plataforma: Android Compose, KMP, Ktor, Spring

#### Definition of Done — v1.0.0

- [ ] 9 reglas implementadas en capas especificadas
- [ ] Tests con ≥3 casos por regla
- [ ] BEST_PRACTICES: secciones §§8.3–8.5, 9.10–9.11, 10.3–10.4, 14.1
- [ ] `rule-codes.yml` completo con todas las reglas
- [ ] Flow Chain Analyzer IDE (MVP funcional)
- [ ] Perfil `spring-backend` en Gradle plugin
- [ ] Kotlin Coroutines Skill v3.0.0
- [ ] Guías de integración por plataforma
- [ ] Guía de migración v0.7.x → v1.0.0
- [ ] CHANGELOG entrada v1.0.0

---

## Resumen: Conteo Total de Reglas por Versión

| Versión         | Reglas Nuevas | Acumulado              | Capas principales                     |
|-----------------|---------------|------------------------|---------------------------------------|
| v0.7.0 (actual) | —             | 24 reglas documentadas | Compiler, Detekt, Lint, IDE           |
| **v0.8.0**      | +8            | 32                     | Interop + Flow + Test + KMP básico    |
| **v0.9.0**      | +10           | 42                     | Concurrencia + KMP completo + Backend |
| **v1.0.0**      | +9            | 51                     | Compose + Testing moderno + Debug     |

**Total de implementaciones individuales (regla × capa):**

| Versión   | Compiler | Detekt  | Lint    | IDE     | Total nuevas impls |
|-----------|----------|---------|---------|---------|--------------------|
| v0.8.0    | +2       | +6      | +5      | +6      | +19                |
| v0.9.0    | 0        | +9      | +4      | +5      | +18                |
| v1.0.0    | 0        | +4      | +4      | +7      | +15                |
| **Total** | **+2**   | **+19** | **+13** | **+18** | **+52**            |

Al completar v1.0.0 el toolkit tendrá **~119 implementaciones** distribuidas en 4 capas cobriendo *
*51 patrones** documentados — frente a los 67 actuales en 24 patrones.

---

## Notas sobre kotlinx-coroutines 1.10.2 para los implementadores

### APIs nuevas en 1.9.x–1.10.x a mencionar en BEST_PRACTICES

| API                              | Versión | Dónde mencionar                           |
|----------------------------------|---------|-------------------------------------------|
| `Flow.timeout(duration)`         | 1.9.0   | §4.6 junto a `withTimeoutOrNull` en flows |
| `Flow.chunked(size)`             | 1.10.0  | §9 como alternativa a `buffer + windowed` |
| `Mutex.holdsLock(owner)`         | 1.9.0   | §12.1 para debugging de deadlocks         |
| `CoroutineScope.cancel(message)` | estable | §4.5 en ScopeReuseAfterCancel             |
| `TestScope.backgroundScope`      | 1.7     | §6.4 en RunBlockingInsteadOfRunTest       |
| `runTest(timeout = ...)`         | 1.7     | §6 para tests con timeout configurable    |

### Cambios de comportamiento en 1.10.x relevantes para tests

- `callbackFlow` sin `awaitClose` lanza `IllegalStateException` desde 1.6 → confirmar en tests de
  INTEROP_002
- `runTest` con `StandardTestDispatcher` requiere `advanceUntilIdle()` explícito → core de TEST_006
- `TestCoroutineScope` fue removido en 1.9.0 → samples deben usar `TestScope` + `runTest`

### Dispatchers en KMP (kotlinx-coroutines 1.10.2)

| Target                 | Dispatchers.Main           | Dispatchers.IO  | Dispatchers.Default |
|------------------------|----------------------------|-----------------|---------------------|
| JVM/Android            | ✅ (con coroutines-android) | ✅               | ✅                   |
| iOS/macOS (Native)     | ✅ (main thread)            | ❌ **no existe** | ✅                   |
| JS/WASM                | ✅ (microtask queue)        | ❌ **no existe** | ✅                   |
| Linux/Windows (Native) | ❌ requiere impl            | ❌ **no existe** | ✅                   |

Tabla a incluir en BEST_PRACTICES §11.

---

*Plan generado para structured-coroutines v0.7.0 — Roadmap hacia v1.0.0*
