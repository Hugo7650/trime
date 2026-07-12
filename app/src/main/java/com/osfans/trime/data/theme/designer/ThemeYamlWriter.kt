/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node

object ThemeYamlWriter {
    fun write(node: Node): String = buildString {
        writeNode(node, 0, emptyList())
        if (!endsWith('\n')) append('\n')
    }

    private fun StringBuilder.writeNode(
        node: Node,
        indent: Int,
        path: List<String>,
    ) {
        when (node) {
            is Node.Mapping -> writeMapping(node, indent, path)
            is Node.Sequence -> writeSequence(node, indent, path)
            is Node.Scalar -> append(formatScalar(node.string))
            is Node.Alias -> append(formatScalar(node.anchor))
        }
    }

    private fun StringBuilder.writeMapping(
        node: Node.Mapping,
        indent: Int,
        path: List<String>,
    ) {
        sortedEntries(node, path).forEachIndexed { index, (key, value) ->
            if (index > 0) append('\n')
            append(" ".repeat(indent))
            val keyString = (key as? Node.Scalar)?.string ?: key.toString()
            append(formatScalar(keyString))
            append(":")
            val childPath = path + keyString
            when (value) {
                is Node.Mapping -> {
                    if (value.isEmpty()) {
                        append(" {}")
                    } else {
                        append('\n')
                        writeMapping(value, indent + INDENT, childPath)
                    }
                }
                is Node.Sequence -> {
                    if (value.isEmpty()) {
                        append(" []")
                    } else {
                        append('\n')
                        writeSequence(value, indent + INDENT, childPath)
                    }
                }
                else -> {
                    append(' ')
                    writeNode(value, indent + INDENT, childPath)
                }
            }
        }
    }

    private fun StringBuilder.writeSequence(
        node: Node.Sequence,
        indent: Int,
        path: List<String>,
    ) {
        node.forEachIndexed { index, value ->
            if (index > 0) append('\n')
            append(" ".repeat(indent)).append("-")
            when (value) {
                is Node.Mapping -> {
                    if (value.isEmpty()) {
                        append(" {}")
                    } else {
                        append('\n')
                        writeMapping(value, indent + INDENT, path + index.toString())
                    }
                }
                is Node.Sequence -> {
                    if (value.isEmpty()) {
                        append(" []")
                    } else {
                        append('\n')
                        writeSequence(value, indent + INDENT, path + index.toString())
                    }
                }
                else -> {
                    append(' ')
                    writeNode(value, indent + INDENT, path + index.toString())
                }
            }
        }
    }

    private fun sortedEntries(
        node: Node.Mapping,
        path: List<String>,
    ): List<Map.Entry<Node, Node>> {
        val order = orderFor(path)
        return node.entries
            .filterNot { (it.key as? Node.Scalar)?.string in OMITTED_DIRECTIVE_KEYS }
            .sortedWith(
                compareBy<Map.Entry<Node, Node>>(
                    { entry ->
                        order[(entry.key as? Node.Scalar)?.string] ?: Int.MAX_VALUE
                    },
                    { entry ->
                        (entry.key as? Node.Scalar)?.string ?: entry.key.toString()
                    },
                ),
            )
    }

    private fun orderFor(path: List<String>): Map<String, Int> = when {
        path.isEmpty() -> ROOT_ORDER
        path == listOf("style") -> STYLE_ORDER
        path == listOf("preedit") -> PREEDIT_ORDER
        path == listOf("preedit", "foreground") -> PREEDIT_FOREGROUND_ORDER
        path == listOf("window") -> WINDOW_ORDER
        path == listOf("window", "insets") || path == listOf("window", "item_padding") -> PADDING_ORDER
        path == listOf("window", "foreground") -> WINDOW_FOREGROUND_ORDER
        path == listOf("tool_bar") -> TOOLBAR_ORDER
        path == listOf("tool_bar", "primary_button") || path.takeLast(1).singleOrNull()?.toIntOrNull() != null && path.dropLast(1) == listOf("tool_bar", "buttons") -> TOOLBAR_BUTTON_ORDER
        path.takeLast(1).singleOrNull()?.toIntOrNull() != null && path.dropLast(1).lastOrNull() == "keys" -> KEY_ORDER
        path.firstOrNull() == "preset_keyboards" && path.size == 2 -> KEYBOARD_ORDER
        path.firstOrNull() == "preset_keys" && path.size == 2 -> PRESET_KEY_ORDER
        path.firstOrNull() == "liquid_keyboard" && path.size == 2 -> LIQUID_KEYBOARD_ENTRY_ORDER
        path.firstOrNull() == "liquid_keyboard" -> LIQUID_KEYBOARD_ORDER
        else -> emptyMap()
    }

    private fun formatScalar(value: String): String {
        if (value.isEmpty()) return "\"\""
        val plain = value.matches(PLAIN_SCALAR) &&
            value !in RESERVED_VALUES &&
            !value.first().let { it in RESERVED_PLAIN_SCALAR_PREFIXES }
        return if (plain) {
            value
        } else {
            "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\""
        }
    }

    private val PLAIN_SCALAR = Regex("[A-Za-z0-9_./+%()#:-]+")
    private val RESERVED_PLAIN_SCALAR_PREFIXES = setOf('-', '?', '@', '`', '&', '*', '!', '#', '%', ':')
    private val RESERVED_VALUES = setOf("true", "false", "True", "False", "TRUE", "FALSE", "null", "Null", "NULL", "~")
    private val OMITTED_DIRECTIVE_KEYS = setOf("__include", "__patch")
    private val ROOT_ORDER = order(
        listOf("config_version", "name", "author", "style", "preedit", "window", "tool_bar", "fallback_colors", "preset_color_schemes", "liquid_keyboard", "preset_keys", "preset_keyboards"),
    )
    private val STYLE_ORDER = order(ThemeFieldRegistry.fields.filter { it.path.firstOrNull() == "style" }.map { it.path.drop(1).first() })
    private val PREEDIT_ORDER = order(listOf("horizontal_padding", "top_end_radius", "alpha", "foreground"))
    private val PREEDIT_FOREGROUND_ORDER = order(listOf("font_size"))
    private val WINDOW_ORDER = order(listOf("insets", "item_padding", "min_width", "corner_radius", "border", "shadow", "alpha", "foreground"))
    private val WINDOW_FOREGROUND_ORDER = order(listOf("label_font_size", "text_font_size", "comment_font_size"))
    private val PADDING_ORDER = order(listOf("vertical", "horizontal"))
    private val TOOLBAR_ORDER = order(listOf("button_spacing", "button_font", "back_style", "primary_button", "buttons"))
    private val TOOLBAR_BUTTON_ORDER = order(ThemeFieldRegistry.toolbarButtonFields.map { it.path.first() }.distinct())
    private val LIQUID_KEYBOARD_ORDER = order(ThemeFieldRegistry.fields.filter { it.path.firstOrNull() == "liquid_keyboard" }.map { it.path.drop(1).first() }.distinct())
    private val LIQUID_KEYBOARD_ENTRY_ORDER = order(ThemeFieldRegistry.liquidKeyboardFields.map { it.path.first() })
    private val KEY_BEHAVIOR_NAMES = ThemeFieldRegistry.keyActionBehaviors.map { it.name.lowercase() } + "popup"
    private val KEYBOARD_ORDER = order(ThemeFieldRegistry.keyboardFields.map { it.path.first() } + "keys")
    private val PRESET_KEY_ORDER = order(ThemeFieldRegistry.presetKeyFields.map { it.path.first() })
    private val KEY_ORDER = order(ThemeFieldRegistry.keyFields.map { it.path.first() } + KEY_BEHAVIOR_NAMES)

    private fun order(keys: List<String>): Map<String, Int> = keys.distinct().mapIndexed { index, key -> key to index }.toMap()

    private const val INDENT = 2
}
