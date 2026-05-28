package io.github.santimattius.structured.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Baseline configuration for gradual Detekt adoption (MVP).
 *
 * ```kotlin
 * structuredCoroutines {
 *     baseline {
 *         file.set(rootProject.file("coroutines-baseline.xml"))
 *         mode.set(BaselineMode.REPORT_NEW_ONLY.name)
 *         enabled.set(true)
 *         autoUpdate.set(false)
 *     }
 * }
 * ```
 */
abstract class BaselineExtension {

    abstract val file: RegularFileProperty

    abstract val mode: Property<String>

    abstract val enabled: Property<Boolean>

    abstract val autoUpdate: Property<Boolean>
}
