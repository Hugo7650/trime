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

object ThemeReferenceScanner {
    fun keyboardReferences(
        node: Node.Mapping,
        keyboardId: String,
    ): List<String> = buildList {
        keyboardRefs(node["style"]?.mapping?.get("keyboards"))
            .forEachIndexed { index, value ->
                if (value == keyboardId) add("style/keyboards[$index]")
            }
        node["preset_keyboards"]?.mapping?.forEach { (keyboardKey, value) ->
            val sourceKeyboard = keyboardKey.string ?: return@forEach
            val keyboard = value.mapping ?: return@forEach
            listOf("ascii_keyboard", "landscape_keyboard").forEach { field ->
                if (keyboard[field]?.string == keyboardId) {
                    add("preset_keyboards/$sourceKeyboard/$field")
                }
            }
            keyboard["keys"]?.sequence?.forEachIndexed { index, keyNode ->
                val key = keyNode.mapping ?: return@forEachIndexed
                if (key["select"]?.string == keyboardId) {
                    add("preset_keyboards/$sourceKeyboard/keys[${index + 1}]/select")
                }
            }
        }
        node["preset_keys"]?.mapping?.forEach { (presetKey, value) ->
            if (value.mapping?.get("select")?.string == keyboardId) {
                add("preset_keys/${presetKey.string}/select")
            }
        }
    }.distinct().sorted()

    fun presetKeyReferences(
        node: Node.Mapping,
        presetKeyId: String,
    ): List<String> = buildList {
        node["preset_keyboards"]?.mapping?.forEach { (keyboardKey, value) ->
            val keyboardId = keyboardKey.string ?: return@forEach
            val keys = value.mapping?.get("keys")?.sequence ?: return@forEach
            keys.forEachIndexed { index, keyNode ->
                val key = keyNode.mapping ?: return@forEachIndexed
                PRESET_KEY_ACTION_FIELDS.forEach { field ->
                    if (key[field]?.string == presetKeyId) {
                        add("preset_keyboards/$keyboardId/keys[${index + 1}]/$field")
                    }
                }
                key["popup"]?.sequence?.forEachIndexed { popupIndex, popupKey ->
                    if (popupKey.string == presetKeyId) {
                        add("preset_keyboards/$keyboardId/keys[${index + 1}]/popup[${popupIndex + 1}]")
                    }
                }
            }
        }
        scanToolbarPresetKeyReferences(node, presetKeyId, this)
        scanLiquidKeyboardPresetKeyReferences(node, presetKeyId, this)
    }.distinct().sorted()

    private fun scanToolbarPresetKeyReferences(
        node: Node.Mapping,
        presetKeyId: String,
        references: MutableList<String>,
    ) {
        val toolbar = node["tool_bar"]?.mapping ?: return
        toolbar["primary_button"]?.mapping?.let { button ->
            scanToolbarButtonActions(button, "tool_bar/primary_button", presetKeyId, references)
        }
        toolbar["buttons"]?.sequence?.forEachIndexed { index, buttonNode ->
            buttonNode.mapping?.let { button ->
                scanToolbarButtonActions(button, "tool_bar/buttons[${index + 1}]", presetKeyId, references)
            }
        }
    }

    private fun scanToolbarButtonActions(
        button: Node.Mapping,
        path: String,
        presetKeyId: String,
        references: MutableList<String>,
    ) {
        TOOLBAR_ACTION_FIELDS.forEach { field ->
            if (button[field]?.string == presetKeyId) references += "$path/$field"
        }
    }

    private fun scanLiquidKeyboardPresetKeyReferences(
        node: Node.Mapping,
        presetKeyId: String,
        references: MutableList<String>,
    ) {
        val liquid = node["liquid_keyboard"]?.mapping ?: return
        liquid["fixed_key_bar"]?.mapping?.get("keys")?.sequence?.forEachIndexed { index, key ->
            if (key.string == presetKeyId) {
                references += "liquid_keyboard/fixed_key_bar/keys[${index + 1}]"
            }
        }
        liquid["keyboards"]?.sequence?.mapNotNull { it.string }?.forEach { keyboardId ->
            val keys = liquid[keyboardId]?.mapping?.get("keys") ?: return@forEach
            keys.sequence?.forEachIndexed { index, keyNode ->
                val path = "liquid_keyboard/$keyboardId/keys[${index + 1}]"
                when (keyNode) {
                    is Node.Scalar -> if (keyNode.string == presetKeyId) references += path
                    is Node.Mapping -> {
                        if (keyNode["click"]?.string == presetKeyId) references += "$path/click"
                        if (keyNode["click"] == null && keyNode.keys.any { it.string == presetKeyId }) {
                            references += "$path/$presetKeyId"
                        }
                    }
                    else -> Unit
                }
            }
            keys.string?.lines()?.forEachIndexed { index, action ->
                if (action == presetKeyId) references += "liquid_keyboard/$keyboardId/keys[${index + 1}]"
            }
        }
    }

    private fun keyboardRefs(node: Node?): List<String> = node?.sequence?.mapNotNull { it.string }
        ?: node?.string?.split(',')?.map { it.trim() }
        ?: node?.mapping?.keys?.mapNotNull(Node::string)
        ?: emptyList()

    private val PRESET_KEY_ACTION_FIELDS = KeyBehavior.entries.map { it.name.lowercase() }.distinct()
    private val TOOLBAR_ACTION_FIELDS = listOf("action", "long_press_action")
}
