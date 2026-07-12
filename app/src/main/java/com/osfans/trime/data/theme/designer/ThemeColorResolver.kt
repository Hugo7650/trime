/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import androidx.annotation.ColorInt
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeColorFallbacks
import com.osfans.trime.util.ColorUtils

class ThemeColorResolver(
    private val theme: Theme,
    private val schemeId: String = "default",
) {
    private val scheme = theme.colorSchemes.firstOrNull { it.id == schemeId }
        ?: theme.colorSchemes.firstOrNull()

    @ColorInt
    fun color(
        key: String,
        @ColorInt default: Int,
    ): Int = runCatching {
        parseThemeColor(resolveValue(key))
    }.getOrDefault(default)

    @ColorInt
    fun colorValue(
        value: String,
        @ColorInt default: Int,
    ): Int {
        if (value.isBlank()) return default
        return runCatching {
            parseThemeColor(resolveValue(value))
        }.getOrDefault(default)
    }

    @ColorInt
    private fun parseThemeColor(value: String): Int {
        val normalized = value.trim()
        val hex = when {
            normalized.startsWith("#") -> normalized.drop(1)
            normalized.startsWith("0x", ignoreCase = true) -> normalized.drop(2)
            else -> return ColorUtils.parseColor(normalized)
        }
        val parsed = hex.toLongOrNull(16) ?: error("Invalid color '$value'")
        return when (hex.length) {
            1, 2 -> (parsed shl 24).toInt()
            in 3..6 -> (0xff000000L or parsed).toInt()
            7, 8 -> parsed.toInt()
            else -> error("Invalid color '$value'")
        }
    }

    private fun resolveValue(key: String): String {
        var current = key
        val seen = mutableSetOf<String>()
        while (seen.add(current)) {
            val value = scheme?.colors?.get(current)
            if (!value.isNullOrBlank()) return value
            val fallback = theme.fallbackColors[current] ?: ThemeColorFallbacks.values[current]
            if (fallback.isNullOrBlank()) break
            current = fallback
        }
        return key
    }

    companion object {
        @ColorInt val DEFAULT_KEY_BACK: Int = 0xfff5f7f9.toInt()

        @ColorInt val DEFAULT_KEY_TEXT: Int = 0xff20252a.toInt()

        @ColorInt val DEFAULT_KEY_SYMBOL: Int = 0xff59646e.toInt()

        @ColorInt val DEFAULT_BORDER: Int = 0xff9ea9b3.toInt()
    }
}
