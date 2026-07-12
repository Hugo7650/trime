/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

data class ThemeValidationResult(
    val messages: List<Message> = emptyList(),
) {
    val hasErrors: Boolean get() = messages.any { it.level == Level.ERROR }

    enum class Level {
        ERROR,
        WARNING,
        INFO,
    }

    data class Message(
        val level: Level,
        val message: String,
    )

    companion object {
        val Ok = ThemeValidationResult(listOf(Message(Level.INFO, "Theme draft is valid")))
    }
}
