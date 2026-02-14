# Compilation check examples

This package contains **one package per compiler check** from the Structured Coroutines plugin. Each
subpackage has a single file that triggers that specific **error** or **warning** when the sample is
compiled with the plugin enabled.

Use these examples to:

- See exactly what code triggers each rule
- Test the gradle-plugin / compiler plugin in your environment
- Document expected diagnostics for CI or IDE

## Package layout

Each rule has its own subpackage under `io.github.santimattius.structured.sample.compilation`:

| Subpackage                              | Severity | Rule                            | Description                                             |
|-----------------------------------------|----------|---------------------------------|---------------------------------------------------------|
| `compilation.globalscope`               | Error    | globalScopeUsage                | `GlobalScope.launch` / `GlobalScope.async`              |
| `compilation.inlinecoroutinescope`      | Error    | inlineCoroutineScope            | `CoroutineScope(Dispatchers.X).launch`                  |
| `compilation.unstructuredlaunch`       | Error    | unstructuredLaunch              | `scope.launch` where scope is not `@StructuredScope`    |
| `compilation.runblockinginsuspend`      | Error    | runBlockingInSuspend            | `runBlocking { }` inside a suspend function             |
| `compilation.jobinbuildercontext`       | Error    | jobInBuilderContext             | `Job()` / `SupervisorJob()` in launch/async/withContext |
| `compilation.cancellationexceptionsubclass` | Error | cancellationExceptionSubclass   | Class extends `CancellationException`                   |
| `compilation.unuseddeferred`            | Error    | unusedDeferred                  | `async { }` result never awaited                        |
| `compilation.dispatchersunconfined`     | Warning  | dispatchersUnconfined           | `Dispatchers.Unconfined` usage                          |
| `compilation.suspendinfinally`          | Warning  | suspendInFinally                | Suspend call in `finally` without `NonCancellable`      |
| `compilation.cancellationexceptionswallowed` | Warning | cancellationExceptionSwallowed  | `catch(Exception)` may swallow `CancellationException`  |
| `compilation.redundantlaunchincoroutinescope` | Warning | redundantLaunchInCoroutineScope | Single `launch` inside `coroutineScope { }`             |

## How to compile

From the project root, build the compiler plugin and then the sample:

```bash
./gradlew :compiler:jar
./gradlew :sample:compileKotlin
```

With the plugin applied, the sample will **fail** compilation due to the error examples (and any
other error examples in the sample, e.g. `BasicPluginErrorExample.kt`). To get a successful build
you can temporarily exclude the `compilation` source set, remove the error example packages, or
configure the plugin (e.g. via the gradle-plugin) to downgrade those rules to `"warning"`.

## Automated validation

The compiler’s functional tests include:

1. **Rule codes:** Compile the **sample** (expecting failure) and assert the output contains
   `[SCOPE_001]`, `[SCOPE_003]`, `[DISPATCH_004]`.
2. **Locales:** Run the sample compile with `JAVA_TOOL_OPTIONS=-Dstructured.coroutines.compiler.locale=en`
   and with `locale=es`, and assert the output contains a **localized** SCOPE_001 message (English or
   Spanish). This checks that i18n bundles are used; the exact language can depend on whether the
   Gradle worker receives the JVM options.

Together this validates that the sample and the compiler (rule codes and i18n) stay in sync.

From the project root:

```bash
./gradlew validateSample
```

or run all compiler tests:

```bash
./gradlew :compiler:test
```
