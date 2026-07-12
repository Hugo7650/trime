/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.designer

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string

object ThemeKeyboardRenamer {
    fun rename(
        node: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        if (oldId == newId) return node
        val presetKeyboards = node["preset_keyboards"]?.mapping ?: return node
        require(presetKeyboards[oldId] != null) { "Keyboard '$oldId' does not exist" }
        require(presetKeyboards[newId] == null) { "Keyboard '$newId' already exists" }
        return replaceKeyboardReferences(
            renamePresetKeyboard(node, oldId, newId),
            oldId,
            newId,
        )
    }

    private fun renamePresetKeyboard(
        node: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        val keyboards = node["preset_keyboards"]?.mapping ?: return node
        val renamed = Node.Mapping(
            keyboards.pairs.mapKeys { (key, _) ->
                if (key.string == oldId) Node.Scalar(newId) else key
            },
            keyboards.anchor,
        )
        return ThemeYamlEditor.updateNode(node, listOf("preset_keyboards"), renamed)
    }

    private fun replaceKeyboardReferences(
        node: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        val styleRefs = node["style"]?.mapping?.get("keyboards")?.replaceKeyboardRef(oldId, newId)
        val withStyleRefs = if (styleRefs == null) {
            node
        } else {
            ThemeYamlEditor.updateNode(node, listOf("style", "keyboards"), styleRefs)
        }
        val withKeyboardRefs = updateKeyboardReferenceFields(withStyleRefs, oldId, newId)
        return updatePresetKeySelectReferences(withKeyboardRefs, oldId, newId)
    }

    private fun updateKeyboardReferenceFields(
        node: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        val keyboards = node["preset_keyboards"]?.mapping ?: return node
        val updatedKeyboards = Node.Mapping(
            keyboards.pairs.mapValues { (_, value) ->
                val keyboard = value.mapping ?: return@mapValues value
                val withKeyboardRefs = keyboard.replaceScalarFields(
                    oldId = oldId,
                    newId = newId,
                    fields = listOf("ascii_keyboard", "landscape_keyboard"),
                )
                updateKeySelectReferences(withKeyboardRefs, oldId, newId)
            },
            keyboards.anchor,
        )
        return ThemeYamlEditor.updateNode(node, listOf("preset_keyboards"), updatedKeyboards)
    }

    private fun updateKeySelectReferences(
        keyboard: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        val keys = keyboard["keys"]?.sequence ?: return keyboard
        val updatedKeys = Node.Sequence(
            keys.map { keyNode ->
                val key = keyNode.mapping ?: return@map keyNode
                key.replaceScalarFields(oldId, newId, listOf("select"))
            },
            keys.anchor,
        )
        return ThemeYamlEditor.updateNode(keyboard, listOf("keys"), updatedKeys)
    }

    private fun updatePresetKeySelectReferences(
        node: Node.Mapping,
        oldId: String,
        newId: String,
    ): Node.Mapping {
        val presetKeys = node["preset_keys"]?.mapping ?: return node
        val updatedPresetKeys = Node.Mapping(
            presetKeys.pairs.mapValues { (_, value) ->
                val presetKey = value.mapping ?: return@mapValues value
                presetKey.replaceScalarFields(oldId, newId, listOf("select"))
            },
            presetKeys.anchor,
        )
        return ThemeYamlEditor.updateNode(node, listOf("preset_keys"), updatedPresetKeys)
    }

    private fun Node.replaceKeyboardRef(
        oldId: String,
        newId: String,
    ): Node = when (this) {
        is Node.Sequence -> Node.Sequence(
            map { if (it.string == oldId) Node.Scalar(newId) else it },
            anchor,
        )
        is Node.Scalar -> Node.Scalar(
            string.split(',')
                .joinToString(", ") { if (it.trim() == oldId) newId else it.trim() },
            anchor,
        )
        is Node.Mapping -> Node.Mapping(
            pairs.mapKeys { (key, _) -> if (key.string == oldId) Node.Scalar(newId) else key },
            anchor,
        )
        is Node.Alias -> this
    }

    private fun Node.Mapping.replaceScalarFields(
        oldId: String,
        newId: String,
        fields: List<String>,
    ): Node.Mapping {
        var current = this
        fields.forEach { field ->
            if (current[field]?.string == oldId) {
                current = ThemeYamlEditor.updateScalar(current, listOf(field), newId)
            }
        }
        return current
    }
}
