/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeColorFallbacks
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string

object ThemeDraftValidator {
    fun validate(node: Node.Mapping): ThemeValidationResult = runCatching {
        Theme.decode(node)
        val messages = mutableListOf<ThemeValidationResult.Message>()
        val keyboards = node["preset_keyboards"]?.mapping?.keys?.mapNotNull { it.string }?.toSet().orEmpty()
        val keyboardNodes = node["preset_keyboards"]?.mapping?.entries.orEmpty()
        val style = node["style"]?.mapping
        val styleKeyboards = keyboardRefs(style?.get("keyboards"))
        val colorKeys = colorKeys(node)
        val colorSchemeIds = node["preset_color_schemes"]?.mapping?.keys?.mapNotNull { it.string }?.toSet().orEmpty()

        if (colorSchemeIds.isEmpty()) {
            messages += error("preset_color_schemes must contain at least one color scheme")
        }

        styleKeyboards
            .filter { it.isNotBlank() && !ThemeKeyboardReferences.isKnown(it, keyboards) }
            .forEach {
                messages += error("style/keyboards references missing preset keyboard '$it'")
            }

        keyboardNodes.forEach { (key, value) ->
            val keyboardId = key.string ?: return@forEach
            val keyboard = value.mapping ?: return@forEach
            val keys = keyboard["keys"] as? Node.Sequence
            if (keys == null || keys.isEmpty()) {
                messages += warning("preset_keyboards/$keyboardId has no keys")
            }
            val keyboardHeight = keyboard["keyboard_height"]?.string?.toIntOrNull()
            val styleKeyboardHeight = style?.get("keyboard_height")?.string?.toIntOrNull()
            if ((keyboardHeight ?: styleKeyboardHeight ?: 0) == 0) {
                messages += warning("preset_keyboards/$keyboardId may have zero keyboard height")
            }
            validateKeyboardReferences(
                keyboardId = keyboardId,
                keyboard = keyboard,
                knownKeyboards = keyboards,
                messages = messages,
            )
            validateKeyboardKeys(
                keyboardId = keyboardId,
                keyboard = keyboard,
                keys = keys,
                style = style,
                knownKeyboards = keyboards,
                knownColors = colorKeys,
                messages = messages,
            )
        }

        validatePresetKeyReferences(node, keyboards, messages)
        validateColorReferences(node, colorKeys, colorSchemeIds, messages)
        validateFallbackColorCycles(node, messages)

        if (messages.isEmpty()) ThemeValidationResult.Ok else ThemeValidationResult(messages)
    }.getOrElse {
        ThemeValidationResult(
            listOf(
                error(it.message ?: "Theme draft is invalid"),
            ),
        )
    }

    private fun validateKeyboardReferences(
        keyboardId: String,
        keyboard: Node.Mapping,
        knownKeyboards: Set<String>,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        KEYBOARD_REFERENCE_FIELDS.forEach { field ->
            val target = keyboard[field]?.string.orEmpty()
            if (target.isNotEmpty() && target !in knownKeyboards) {
                messages += warning("preset_keyboards/$keyboardId $field target '$target' is not defined")
            }
        }
    }

    private fun validateKeyboardKeys(
        keyboardId: String,
        keyboard: Node.Mapping,
        keys: Node.Sequence?,
        style: Node.Mapping?,
        knownKeyboards: Set<String>,
        knownColors: Set<String>,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        if (keys == null) return
        val defaultWidth = keyboard["width"]?.float ?: style?.get("key_width")?.float ?: 0f
        val configuredColumns = keyboard["columns"]?.string?.toIntOrNull() ?: DEFAULT_MAX_COLUMNS
        if (configuredColumns < -1 || configuredColumns == 0) {
            messages += error("preset_keyboards/$keyboardId columns must be -1 or greater than 0")
        }
        val maxColumns = if (configuredColumns == -1) Int.MAX_VALUE else configuredColumns.coerceAtLeast(1)
        var rowWidth = 0f
        var rowIndex = 1
        var column = 0
        keys.mapNotNull { it.mapping }.forEachIndexed { index, keyNode ->
            val select = keyNode["select"]?.string.orEmpty()
            if (select.isNotEmpty() && !ThemeKeyboardReferences.isKnown(select, knownKeyboards)) {
                messages += warning("preset_keyboards/$keyboardId has select target '$select' that is not defined")
            }
            KEY_COLOR_FIELDS.forEach { field ->
                val value = keyNode[field]?.string.orEmpty()
                if (value.isColorReferenceMissing(knownColors)) {
                    messages += warning("preset_keyboards/$keyboardId key ${index + 1} references missing color '$value'")
                }
            }
            val width = keyNode["width"]?.float ?: defaultWidth
            if (width <= 0f) {
                messages += warning("preset_keyboards/$keyboardId key ${index + 1} has non-positive width")
                return@forEachIndexed
            }
            if (column >= maxColumns || (column > 0 && rowWidth + width > ROW_TARGET_WIDTH)) {
                validateRowWidth(keyboardId, rowIndex, rowWidth, messages)
                rowWidth = 0f
                rowIndex += 1
                column = 0
            }
            rowWidth += width
            if (keyNode["click"]?.string.orEmpty().isNotEmpty()) column += 1
        }
        validateRowWidth(keyboardId, rowIndex, rowWidth, messages)
    }

    private fun validateRowWidth(
        keyboardId: String,
        rowIndex: Int,
        rowWidth: Float,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        if (rowWidth <= 0f) return
        when {
            rowWidth < ROW_MIN_REASONABLE_WIDTH ->
                messages += warning("preset_keyboards/$keyboardId row $rowIndex width is unusually small: $rowWidth")
            rowWidth > ROW_MAX_REASONABLE_WIDTH ->
                messages += warning("preset_keyboards/$keyboardId row $rowIndex width is unusually large: $rowWidth")
        }
    }

    private fun validatePresetKeyReferences(
        node: Node.Mapping,
        knownKeyboards: Set<String>,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        node["preset_keys"]?.mapping?.forEach { (key, value) ->
            val presetKeyId = key.string ?: return@forEach
            val select = value.mapping?.get("select")?.string.orEmpty()
            if (select.isNotEmpty() && !ThemeKeyboardReferences.isKnown(select, knownKeyboards)) {
                messages += warning("preset_keys/$presetKeyId select target '$select' is not defined")
            }
        }
    }

    private fun validateColorReferences(
        node: Node.Mapping,
        knownColors: Set<String>,
        knownColorSchemes: Set<String>,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        val style = node["style"]?.mapping
        STYLE_COLOR_FIELDS.forEach { field ->
            val value = style?.get(field)?.string.orEmpty()
            if (value.isColorReferenceMissing(knownColors)) {
                messages += warning("style/$field references missing color '$value'")
            }
        }
        node["preset_color_schemes"]?.mapping?.forEach { (schemeKey, schemeNode) ->
            val schemeId = schemeKey.string ?: return@forEach
            schemeNode.mapping?.forEach { (colorKey, value) ->
                val key = colorKey.string ?: return@forEach
                if (key in COLOR_SCHEME_METADATA_FIELDS) return@forEach
                val colorValue = value.string.orEmpty()
                if (key in ThemeColorFieldRegistry.schemeReferenceKeys) {
                    if (colorValue.isNotBlank() && colorValue !in knownColorSchemes) {
                        messages += warning("preset_color_schemes/$schemeId/$key references missing color scheme '$colorValue'")
                    }
                    return@forEach
                }
                if (colorValue.isColorReferenceMissing(knownColors)) {
                    messages += warning("preset_color_schemes/$schemeId/$key references missing color '$colorValue'")
                }
            }
        }
        node["fallback_colors"]?.mapping?.forEach { (key, value) ->
            val fallback = value.string.orEmpty()
            if (fallback.isColorReferenceMissing(knownColors)) {
                messages += warning("fallback_colors/${key.string} references missing color '$fallback'")
            }
        }
    }

    private fun validateFallbackColorCycles(
        node: Node.Mapping,
        messages: MutableList<ThemeValidationResult.Message>,
    ) {
        val configured = node["fallback_colors"]?.mapping?.entries
            ?.mapNotNull { (key, value) ->
                val source = key.string ?: return@mapNotNull null
                val target = value.string ?: return@mapNotNull null
                source to target
            }
            ?.toMap()
            .orEmpty()
        val allKeys = configured.keys + ThemeColorFallbacks.values.keys
        val reportedCycles = mutableSetOf<Set<String>>()
        allKeys.forEach { start ->
            val path = mutableListOf<String>()
            val pathIndexes = mutableMapOf<String, Int>()
            var current: String? = start
            while (current != null) {
                val previousIndex = pathIndexes[current]
                if (previousIndex != null) {
                    val cyclePath = path.subList(previousIndex, path.size)
                    if (reportedCycles.add(cyclePath.toSet())) {
                        messages += error(
                            "fallback_colors contains cycle: ${cyclePath.joinToString(" -> ")} -> ${cyclePath.first()}",
                        )
                    }
                    break
                }
                pathIndexes[current] = path.size
                path += current
                current = configured[current] ?: ThemeColorFallbacks.values[current]
            }
        }
    }

    private fun keyboardRefs(node: Node?): List<String> = node?.sequence?.mapNotNull { it.string }
        ?: node?.string?.split(',')?.map { it.trim() }
        ?: node?.mapping?.keys?.mapNotNull(Node::string)
        ?: emptyList()

    private fun colorKeys(node: Node.Mapping): Set<String> {
        val schemeKeys = node["preset_color_schemes"]?.mapping?.values
            ?.flatMap { it.mapping?.keys?.mapNotNull(Node::string).orEmpty() }
            .orEmpty()
        val fallbackKeys = node["fallback_colors"]?.mapping?.keys?.mapNotNull { it.string }.orEmpty()
        return (schemeKeys + fallbackKeys + ThemeColorFieldRegistry.colorKeys).toSet()
    }

    private fun String.isColorReferenceMissing(knownColors: Set<String>): Boolean {
        if (isBlank()) return false
        if (startsWith("#") || startsWith("0x") || startsWith("0X")) return false
        if (startsWith("@") || contains("/") || contains(".")) return false
        return this !in knownColors
    }

    private fun error(message: String) = ThemeValidationResult.Message(ThemeValidationResult.Level.ERROR, message)

    private fun warning(message: String) = ThemeValidationResult.Message(ThemeValidationResult.Level.WARNING, message)

    private val KEYBOARD_REFERENCE_FIELDS = listOf(
        "ascii_keyboard",
        "landscape_keyboard",
    )

    private val KEY_COLOR_FIELDS = listOf(
        "key_text_color",
        "key_back_color",
        "key_symbol_color",
        "hilited_key_text_color",
        "hilited_key_back_color",
        "hilited_key_symbol_color",
    )

    private val STYLE_COLOR_FIELDS = listOf(
        "key_text_color",
        "key_back_color",
        "key_symbol_color",
        "hilited_key_text_color",
        "hilited_key_back_color",
        "hilited_key_symbol_color",
        "keyboard_back_color",
    )

    private val COLOR_SCHEME_METADATA_FIELDS = setOf(
        "name",
        "author",
    )

    private const val ROW_TARGET_WIDTH = 100f
    private const val ROW_MIN_REASONABLE_WIDTH = 40f
    private const val ROW_MAX_REASONABLE_WIDTH = 120f
    private const val DEFAULT_MAX_COLUMNS = 30
}
