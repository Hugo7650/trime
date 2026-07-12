/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

object ThemeKeyboardReferences {
    val specialIds: List<String> = listOf(
        ".default",
        ".prior",
        ".next",
        ".last",
        ".last_lock",
        ".ascii",
    )

    fun isKnown(
        id: String,
        keyboardIds: Set<String>,
    ): Boolean = id in keyboardIds || id in specialIds
}
