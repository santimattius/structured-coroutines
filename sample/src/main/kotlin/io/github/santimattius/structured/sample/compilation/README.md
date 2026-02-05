# Compilation check examples

This package contains **one package per compiler check** from the Structured Coroutines plugin. Each
subpackage has a single file that triggers that specific **error** or **warning** when the sample is
compiled with the plugin enabled.

Use these examples to:

- See exactly what code triggers each rule
- Test the gradle-plugin / compiler plugin in your environment
- Document expected diagnostics for CI or IDE

## Package layout

| Package       | Severity | Rule                            | Description                                             |
|---------------|----------|---------------------------------|---------------------------------------------------------|
| `compilation` | Error    | globalScopeUsage                | `GlobalScope.launch` / `GlobalScope.async`              |
| `compilation` | Error    | inlineCoroutineScope            | `CoroutineScope(Dispatchers.X).launch`                  |
| `compilation` | Error    | unstructuredLaunch              | `scope.launch` where scope is not `@StructuredScope`    |
| `compilation` | Error    | runBlockingInSuspend            | `runBlocking { }` inside a suspend function             |
| `compilation` | Error    | jobInBuilderContext             | `Job()` / `SupervisorJob()` in launch/async/withContext |
| `compilation` | Error    | cancellationExceptionSubclass   | Class extends `CancellationException`                   |
| `compilation` | Error    | unusedDeferred                  | `async { }` result never awaited                        |
| `compilation` | Warning  | dispatchersUnconfined           | `Dispatchers.Unconfined` usage                          |
| `compilation` | Warning  | suspendInFinally                | Suspend call in `finally` without `NonCancellable`      |
| `compilation` | Warning  | cancellationExceptionSwallowed  | `catch(Exception)` may swallow `CancellationException`  |
| `compilation` | Warning  | redundantLaunchInCoroutineScope | Single `launch` inside `coroutineScope { }`             |

## How to compile

From the project root, build the compiler plugin and then the sample:

```bash
./gradlew :compiler:jar
./gradlew :sample:compileKotlinJvm
```

With the plugin applied, the sample will **fail** compilation due to the error examples (and any
other error examples in the sample, e.g. `BasicPluginErrorExample.kt`). To get a successful build
you can temporarily exclude the `compilation` source set, remove the error example packages, or
configure the plugin (e.g. via the gradle-plugin) to downgrade those rules to `"warning"`.
