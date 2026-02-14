/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

/** System property to force compiler message locale. If unset, messages use English by default. */
private const val LOCALE_PROPERTY = "structured.coroutines.compiler.locale"

/**
 * Loads compiler diagnostic messages from resource bundles for i18n.
 * Default is the root bundle (English) so builds and CI are predictable. To use another language, set
 * the system property [LOCALE_PROPERTY] (e.g. `-Dstructured.coroutines.compiler.locale=es` for Spanish,
 * or `=default` to use the JVM default locale).
 */
internal object CompilerMessages {

    private const val BUNDLE_NAME = "messages.CompilerBundle"

    private val bundleCache = ConcurrentHashMap<Locale, ResourceBundle>()

    private fun resolveLocale(): Locale {
        val value = System.getProperty(LOCALE_PROPERTY)?.lowercase() ?: return Locale.ROOT
        if (value == "default") return Locale.getDefault()
        return when (value) {
            "es", "es_es" -> Locale("es", "ES")
            "en", "en_us", "" -> Locale.ROOT
            else -> runCatching { Locale.forLanguageTag(value.replace('_', '-')) }.getOrDefault(Locale.ROOT)
        }
    }

    private fun getBundle(): ResourceBundle {
        val locale = resolveLocale()
        return bundleCache.computeIfAbsent(locale) { ResourceBundle.getBundle(BUNDLE_NAME, it) }
    }

    /**
     * Returns the localized message for the given diagnostic key.
     * The placeholder {0} is replaced with the documentation base URL.
     */
    fun message(key: String): String {
        val baseUrl = getBundle().getString("doc.base.url")
        return getBundle().getString(key).replace("{0}", baseUrl)
    }
}
