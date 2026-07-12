/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string

object ThemeUnknownFieldScanner {
    fun scan(node: Node.Mapping): List<String> {
        val unknown = mutableListOf<String>()
        scanRoot(node, unknown)
        scanDynamicMappings(node, unknown)
        return unknown.distinct().sorted()
    }

    private fun scanRoot(
        node: Node.Mapping,
        unknown: MutableList<String>,
    ) {
        node.pairs.forEach { (keyNode, value) ->
            val key = keyNode.string ?: return@forEach
            when (key) {
                in ROOT_FIELDS -> Unit
                "style" -> scanKnownTree(value.mapping, "style", ROOT_STYLE_FIELD_PATHS, unknown)
                "preedit" -> scanKnownTree(value.mapping, "preedit", ROOT_PREEDIT_FIELD_PATHS, unknown)
                "window" -> scanKnownTree(value.mapping, "window", ROOT_WINDOW_FIELD_PATHS, unknown)
                "tool_bar" -> scanKnownTree(value.mapping, "tool_bar", ROOT_TOOLBAR_FIELD_PATHS, unknown)
                "liquid_keyboard" -> scanLiquidKeyboard(value.mapping, unknown)
                "fallback_colors", "preset_color_schemes", "preset_keys", "preset_keyboards" -> Unit
                else -> unknown += key
            }
        }
    }

    private fun scanDynamicMappings(
        node: Node.Mapping,
        unknown: MutableList<String>,
    ) {
        node["preset_keyboards"]?.mapping?.forEach { (keyboardKey, value) ->
            val keyboardId = keyboardKey.string ?: return@forEach
            val keyboard = value.mapping ?: return@forEach
            scanKnownTree(keyboard, "preset_keyboards/$keyboardId", KEYBOARD_FIELD_PATHS, unknown)
            keyboard["keys"]?.sequence?.forEachIndexed { index, keyNode ->
                scanKnownTree(keyNode.mapping, "preset_keyboards/$keyboardId/keys/${index + 1}", KEY_FIELD_PATHS, unknown)
            }
        }
        node["preset_keys"]?.mapping?.forEach { (presetKey, value) ->
            val id = presetKey.string ?: return@forEach
            scanKnownTree(value.mapping, "preset_keys/$id", PRESET_KEY_FIELD_PATHS, unknown)
        }
        node["tool_bar"]?.mapping?.get("primary_button")?.mapping?.let {
            scanToolbarButton(it, "tool_bar/primary_button", unknown)
        }
        node["tool_bar"]?.mapping?.get("buttons")?.sequence?.forEachIndexed { index, button ->
            scanToolbarButton(button.mapping, "tool_bar/buttons/${index + 1}", unknown)
        }
    }

    private fun scanToolbarButton(
        node: Node.Mapping?,
        prefix: String,
        unknown: MutableList<String>,
    ) {
        scanKnownTree(node, prefix, TOOLBAR_BUTTON_FIELD_PATHS, unknown)
    }

    private fun scanLiquidKeyboard(
        node: Node.Mapping?,
        unknown: MutableList<String>,
    ) {
        node?.pairs?.forEach { (keyNode, value) ->
            val key = keyNode.string ?: return@forEach
            when {
                key in ROOT_LIQUID_KEYBOARD_TOP_FIELDS -> scanKnownTree(
                    Node.Mapping(Node.Scalar(key) to value),
                    "liquid_keyboard",
                    ROOT_LIQUID_KEYBOARD_FIELD_PATHS,
                    unknown,
                )
                value is Node.Mapping -> scanKnownTree(value, "liquid_keyboard/$key", LIQUID_KEYBOARD_ENTRY_FIELD_PATHS, unknown)
                else -> unknown += "liquid_keyboard/$key"
            }
        }
    }

    private fun scanKnownTree(
        node: Node.Mapping?,
        prefix: String,
        knownPaths: Set<List<String>>,
        unknown: MutableList<String>,
        relativePath: List<String> = emptyList(),
    ) {
        node?.pairs?.forEach { (keyNode, _) ->
            val key = keyNode.string ?: return@forEach
            val path = relativePath + key
            val hasExact = path in knownPaths
            val hasDescendants = knownPaths.any { knownPath ->
                knownPath.size > path.size && knownPath.take(path.size) == path
            }
            if (!hasExact && !hasDescendants) {
                unknown += (listOf(prefix) + path).joinToString("/")
                return@forEach
            }
            if (hasDescendants) {
                scanKnownTree(node[key]?.mapping, prefix, knownPaths, unknown, path)
            }
        }
    }

    private val ROOT_FIELDS = setOf("name", "author", "config_version")

    private val ROOT_STYLE_FIELD_PATHS = ThemeFieldRegistry.fields
        .filter { it.path.firstOrNull() == "style" }
        .map { it.path.drop(1) }
        .toSet()

    private val ROOT_PREEDIT_FIELD_PATHS = ThemeFieldRegistry.fields
        .filter { it.path.firstOrNull() == "preedit" }
        .map { it.path.drop(1) }
        .toSet()

    private val ROOT_WINDOW_FIELD_PATHS = ThemeFieldRegistry.fields
        .filter { it.path.firstOrNull() == "window" }
        .map { it.path.drop(1) }
        .toSet()

    private val ROOT_TOOLBAR_FIELD_PATHS = setOf(
        listOf("button_spacing"),
        listOf("button_font"),
        listOf("back_style"),
        listOf("primary_button"),
        listOf("buttons"),
    )

    private val ROOT_LIQUID_KEYBOARD_FIELD_PATHS = ThemeFieldRegistry.fields
        .filter { it.path.firstOrNull() == "liquid_keyboard" }
        .map { it.path.drop(1) }
        .toSet()
    private val ROOT_LIQUID_KEYBOARD_TOP_FIELDS = ROOT_LIQUID_KEYBOARD_FIELD_PATHS.mapNotNull { it.firstOrNull() }.toSet()

    private val KEYBOARD_FIELD_PATHS = (ThemeFieldRegistry.keyboardFields.map { it.path } + listOf(listOf("keys"))).toSet()
    private val KEY_FIELD_PATHS = (
        ThemeFieldRegistry.keyFields.map { it.path } +
            KeyBehavior.entries.map { listOf(it.name.lowercase()) } +
            listOf(listOf("popup"))
        ).toSet()
    private val PRESET_KEY_FIELD_PATHS = ThemeFieldRegistry.presetKeyFields.map { it.path }.toSet()
    private val LIQUID_KEYBOARD_ENTRY_FIELD_PATHS = ThemeFieldRegistry.liquidKeyboardFields.map { it.path }.toSet()
    private val TOOLBAR_BUTTON_FIELD_PATHS = ThemeFieldRegistry.toolbarButtonFields.map { it.path }.toSet()
}
