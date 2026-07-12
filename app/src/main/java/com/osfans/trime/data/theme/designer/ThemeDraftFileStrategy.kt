/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import java.io.File

object ThemeDraftFileStrategy {
    fun derivedThemeId(baseThemeId: String): String = "$baseThemeId.designer.trime"

    fun isDerivedThemeId(themeId: String): Boolean = DERIVED_THEME_PATTERN.matches(themeId)

    fun sourceThemeId(themeId: String): String = DERIVED_THEME_PATTERN.matchEntire(themeId)?.groupValues?.get(1) ?: themeId

    fun uniqueDerivedThemeId(
        baseThemeId: String,
        userDataDir: File,
    ): String {
        val preferred = derivedThemeId(baseThemeId)
        if (!userDataDir.resolve("$preferred.yaml").exists()) return preferred
        return generateSequence(2) { it + 1 }
            .map { "$baseThemeId.designer$it.trime" }
            .first { !userDataDir.resolve("$it.yaml").exists() }
    }

    fun backupFile(file: File): File = File(file.parentFile, "${file.name}.bak")

    private val DERIVED_THEME_PATTERN = Regex("""(.+)\.designer\d*\.trime""")
}
