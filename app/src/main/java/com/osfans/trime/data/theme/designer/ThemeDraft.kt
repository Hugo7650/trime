/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.yaml.Node
import java.io.File

data class ThemeDraft(
    val theme: Theme,
    val sourceThemeId: String,
    val derivedThemeId: String,
    val file: File,
    val yamlNode: Node.Mapping,
    val yamlText: String,
    val dirty: Boolean,
    val validation: ThemeValidationResult,
)
